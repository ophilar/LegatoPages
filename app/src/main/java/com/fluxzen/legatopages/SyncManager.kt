package com.fluxzen.legatopages

import android.content.Context
import android.os.Build

import android.net.Uri
import android.util.Log

import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import java.io.File
import java.nio.charset.StandardCharsets

data class PageTurn(val bookPage: Int)
data class Device(val endpointId: String, val deviceName: String, val isLeader: Boolean = false, val isThisDevice: Boolean = false)

private sealed class SyncMessage {
    data class FileInfo(val fileName: String, val fileSize: Long, val fileHash: String) : SyncMessage()
    object FileRequest : SyncMessage()
    data class PageChanged(val bookPage: Int) : SyncMessage()
    data class ArrangementUpdate(val devices: List<Device>) : SyncMessage()
}

class SyncManager(private val context: Context) {
    var onPageTurnReceived: ((PageTurn) -> Unit)? = null
    var onDeviceArrangementChanged: ((List<Device>) -> Unit)? = null
    var onStatusUpdate: ((String) -> Unit)? = null
    var onFileReceived: ((File) -> Unit)? = null
    var onFollowerJoined: ((Device) -> Unit)? = null
    var onLeaderFound: ((String) -> Unit)? = null
    var onNoLeaderFound: (() -> Unit)? = null
    var onDisconnectedFromLeader: (() -> Unit)? = null
    var onAdvertisingStarted: (() -> Unit)? = null
    var onAdvertisingFailed: (() -> Unit)? = null
    var onLeadingStopped: (() -> Unit)? = null

    private val myDeviceName = Build.MODEL

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.fluxzen.legatopages.SERVICE_ID"
    private val strategy = Strategy.P2P_CLUSTER
    private val gson = Gson()
    private val cacheManager = CacheManager(context)
    private val pdfPreferences = PdfPreferences(context) 

    private var isLeader = false
    private var leaderEndpointId: String? = null
    private var currentFileUri: Uri? = null
    private var currentFileInfo: SyncMessage.FileInfo? = null

    private val connectedEndpoints = mutableListOf<Device>()
    private val incomingFilePayloads = mutableMapOf<Long, Payload>()
    private val completedFilePayloads = mutableMapOf<Long, File>()
    private val pendingConnections = mutableMapOf<String, String>()

    fun loadFile(uri: Uri): Boolean {
        return try {
            this.currentFileUri = uri
            val (fileName, size) = getFileDetails(uri)
            val hash = context.contentResolver.openInputStream(uri)?.use {
                cacheManager.getFileHash(it)
            } ?: run {
                onStatusUpdate?.invoke("Failed to generate file hash.")
                return false
            }
            currentFileInfo = SyncMessage.FileInfo(fileName, size, hash)
            onStatusUpdate?.invoke("File loaded: $fileName")
            true
        } catch (e: Exception) {
            Log.e("SyncManager", "Error loading file: $uri", e)
            onStatusUpdate?.invoke("Error loading file: ${e.message}")
            false
        }
    }

    fun startLeadingSession() {
        if (currentFileUri == null || currentFileInfo == null) {
            onStatusUpdate?.invoke("No file loaded. Cannot start leading session.")
            onAdvertisingFailed?.invoke()
            return
        }
        startAdvertising()
    }

    fun stopLeading() {
        if (isLeader) {
            connectionsClient.stopAdvertising()
            isLeader = false
            onStatusUpdate?.invoke("Stopped leading session.")
            onLeadingStopped?.invoke()
            connectedEndpoints.forEach { connectionsClient.disconnectFromEndpoint(it.endpointId) }
            connectedEndpoints.clear()
            broadcastDeviceArrangement()
        }
    }

    fun leaveSession() {
        if (!isLeader && leaderEndpointId != null) {
            connectionsClient.disconnectFromEndpoint(leaderEndpointId!!)
            leaderEndpointId = null 
            onStatusUpdate?.invoke("Leaving session...")
        } else {
            onStatusUpdate?.invoke("Not in a session or already leader.")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> handleBytePayload(endpointId, payload.asBytes()!!)
                Payload.Type.STREAM -> {
                    onStatusUpdate?.invoke("Receiving file...")
                    incomingFilePayloads[payload.id] = payload
                }
                else -> Log.w("SyncManager", "Unhandled payload type: ${payload.type}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payloadId = update.payloadId
                val payload = incomingFilePayloads.remove(payloadId) ?: return

                val receivedFile = cacheManager.saveReceivedFile(payload)
                if (receivedFile != null) {
                    completedFilePayloads[payloadId] = receivedFile
                    onStatusUpdate?.invoke("File received successfully.")
                    onFileReceived?.invoke(receivedFile)
                } else {
                    Log.e("SyncManager", "Failed to save received file for payload ID: $payloadId")
                    onStatusUpdate?.invoke("File reception failed.")
                }
            } else if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS) {
                val percent = (update.bytesTransferred * 100) / update.totalBytes
                onStatusUpdate?.invoke("Receiving file... $percent%")
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE || update.status == PayloadTransferUpdate.Status.CANCELED) {
                Log.w("SyncManager", "Payload transfer not SUCCESS: ${update.status} for payload ID: ${update.payloadId}")
                incomingFilePayloads.remove(update.payloadId)
                completedFilePayloads.remove(update.payloadId)
                onStatusUpdate?.invoke("File transfer failed.")
            } else {
                Log.w("SyncManager", "Unhandled payload transfer update: ${update.status}")
            }
        }
    }

    private fun handleBytePayload(endpointId: String, bytes: ByteArray) {
        val json = bytes.toString(StandardCharsets.UTF_8)
        val message = try {
            gson.fromJson(json, SyncMessage::class.java)
        } catch (e: Exception) {
            Log.e("SyncManager", "Error parsing SyncMessage JSON: $json", e)
            return
        }
        when (message) {
            is SyncMessage.FileInfo -> {
                onStatusUpdate?.invoke("Received file info: ${message.fileName}")
                this.currentFileInfo = message
                val cachedFile = cacheManager.getCachedFile(message.fileHash) 
                if (cachedFile != null) {
                    onStatusUpdate?.invoke("File found in cache.")
                    onFileReceived?.invoke(cachedFile)
                } else {
                    onStatusUpdate?.invoke("File not in cache. Requesting...")
                    sendMessage(endpointId, SyncMessage.FileRequest)
                }
            }
            is SyncMessage.FileRequest -> {
                if (!isLeader) return
                val fileUri = currentFileUri ?: return
                try {
                    context.contentResolver.openFileDescriptor(fileUri, "r")?.use { pfd ->
                        val filePayload = Payload.fromStream(pfd)
                        connectionsClient.sendPayload(endpointId, filePayload)
                        onStatusUpdate?.invoke("Sending file to $endpointId...")
                    } ?: onStatusUpdate?.invoke("Failed to open file for sending.")
                } catch (e: Exception) {
                    Log.e("SyncManager", "Error sending file", e)
                    onStatusUpdate?.invoke("Error sending file: ${e.message}")
                }
            }
            is SyncMessage.PageChanged -> onPageTurnReceived?.invoke(PageTurn(message.bookPage))
            is SyncMessage.ArrangementUpdate -> {
                val processedList = message.devices.map { device ->
                    device.copy(isThisDevice = device.deviceName == myDeviceName)
                }
                onDeviceArrangementChanged?.invoke(processedList)
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            onStatusUpdate?.invoke("Accepting connection to ${connectionInfo.endpointName}")
            pendingConnections[endpointId] = connectionInfo.endpointName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val deviceName = pendingConnections.remove(endpointId) ?: "Unknown Device"
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                onStatusUpdate?.invoke("Connected to $deviceName.")
                if (isLeader) {
                    val newDevice = Device(endpointId, deviceName)
                    if (!connectedEndpoints.any {it.endpointId == newDevice.endpointId}) {
                        connectedEndpoints.add(newDevice)
                    }
                    onFollowerJoined?.invoke(newDevice)
                    broadcastDeviceArrangement()
                    currentFileInfo?.let { info ->
                        sendMessage(endpointId, info)
                        
                        val currentPageForLeader = pdfPreferences.getPageForFile(info.fileHash)
                        sendMessage(endpointId, SyncMessage.PageChanged(currentPageForLeader))
                    }
                } else {
                    connectionsClient.stopDiscovery()
                    leaderEndpointId = endpointId
                    onLeaderFound?.invoke(endpointId)
                }
            } else {
                onStatusUpdate?.invoke("Connection to $deviceName failed: ${result.status.statusMessage}")
                connectedEndpoints.removeAll { it.endpointId == endpointId }
                if (isLeader) {
                    broadcastDeviceArrangement()
                } else if (endpointId == leaderEndpointId) {
                    leaderEndpointId = null
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val deviceName = connectedEndpoints.find { it.endpointId == endpointId }?.deviceName ?: pendingConnections[endpointId] ?: "a device"
            onStatusUpdate?.invoke("$deviceName disconnected.")
            connectedEndpoints.removeAll { it.endpointId == endpointId }
            pendingConnections.remove(endpointId)

            if (isLeader) {
                broadcastDeviceArrangement()
            } else if (endpointId == leaderEndpointId) {
                leaderEndpointId = null
                onStatusUpdate?.invoke("Leader disconnected. Searching for new leader...")
                onDisconnectedFromLeader?.invoke()
                startDiscovery()
            }
        }
    }

    private fun startAdvertising() {
        connectionsClient.stopDiscovery()
        isLeader = true
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(
            myDeviceName, serviceId, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener {
            onStatusUpdate?.invoke("Advertising started. Waiting for followers.")
            onAdvertisingStarted?.invoke()
            broadcastDeviceArrangement()
        }.addOnFailureListener { e ->
            onStatusUpdate?.invoke("Advertising failed: ${e.message}")
            isLeader = false
            onAdvertisingFailed?.invoke()
        }
    }

    fun startDiscovery() {
        connectionsClient.stopAdvertising()
        isLeader = false
        leaderEndpointId = null
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(
            serviceId, object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    onStatusUpdate?.invoke("Found leader: ${info.endpointName}. Connecting...")
                    connectionsClient.requestConnection(myDeviceName, endpointId, connectionLifecycleCallback)
                        .addOnFailureListener { e ->
                            onStatusUpdate?.invoke("Connection request to ${info.endpointName} failed: ${e.message}. Restarting discovery might be needed if no other leaders are found.")
                        }
                }
                override fun onEndpointLost(endpointId: String) {
                    onStatusUpdate?.invoke("Leader $endpointId lost during discovery.")
                }
            }, discoveryOptions
        ).addOnSuccessListener {
            onStatusUpdate?.invoke("Searching for a leader...")
            android.os.Handler(context.mainLooper).postDelayed({
                if (leaderEndpointId == null && !isLeader) {
                    onNoLeaderFound?.invoke()
                }
            }, 10000) 
        }.addOnFailureListener { e ->
            onStatusUpdate?.invoke("Discovery failed to start: ${e.message}")
            onNoLeaderFound?.invoke()
        }
    }

     fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        onStatusUpdate?.invoke("Stopped discovery.")
    }

    fun broadcastPageTurn(pageTurn: PageTurn) {
        if (isLeader) {
            currentFileInfo?.let {
                
                pdfPreferences.savePageForFile(it.fileHash, pageTurn.bookPage)
            }
            sendMessageToAll(SyncMessage.PageChanged(pageTurn.bookPage))
        } else {
            leaderEndpointId?.let { sendMessage(it, SyncMessage.PageChanged(pageTurn.bookPage)) }
        }
    }

    private fun broadcastDeviceArrangement() {
        if (!isLeader) return

        val selfDevice = Device("leader_self_id", myDeviceName, isLeader = true, isThisDevice = true)

        val allDevices = mutableListOf(selfDevice)
        allDevices.addAll(connectedEndpoints)

        val message = SyncMessage.ArrangementUpdate(allDevices)
        sendMessageToAll(message)

        onDeviceArrangementChanged?.invoke(allDevices)
    }

    private fun sendMessageToAll(message: SyncMessage) {
        if (connectedEndpoints.isNotEmpty()) {
            val payload = Payload.fromBytes(gson.toJson(message).toByteArray(StandardCharsets.UTF_8))
            connectionsClient.sendPayload(connectedEndpoints.map { it.endpointId }, payload)
        }
    }

    private fun sendMessage(endpointId: String, message: SyncMessage) {
        val payload = Payload.fromBytes(gson.toJson(message).toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
    }

    fun shutdown() {
        onStatusUpdate?.invoke("Shutting down sync.")
        if (isLeader) {
            connectionsClient.stopAdvertising()
        }
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        isLeader = false
        leaderEndpointId = null
        pendingConnections.clear()
        currentFileUri = null
        currentFileInfo = null
        incomingFilePayloads.clear()
        completedFilePayloads.clear()
    }

    private fun getFileDetails(uri: Uri): Pair<String, Long> {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return "unknown.pdf" to 0L
        cursor.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                val name = if (nameIndex != -1) it.getString(nameIndex) else "unknown.pdf"
                val size = if (sizeIndex != -1) it.getLong(sizeIndex) else 0L
                return name to size
            }
        }
        return "unknown.pdf" to 0L
    }
}
