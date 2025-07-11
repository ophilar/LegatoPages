package com.fluxzen.legatopages

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.charset.StandardCharsets
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

private const val SERVICE_ID = "com.fluxzen.legatopages.SERVICE_ID"
private const val DISCOVERY_TIMEOUT_MS = 10000L

data class PageTurn(val bookPage: Int)
data class Device(
    val endpointId: String,
    val deviceName: String,
    val isLeader: Boolean = false,
    val isThisDevice: Boolean = false,
)

private sealed class SyncMessage {
    abstract val type: String

    data class FileInfo(
        val fileName: String,
        val fileSize: Long,
        val fileHash: String,
        override val type: String = "file_info"
    ) : SyncMessage()

    object FileRequest : SyncMessage() {
        override val type: String = "file_request"
    }

    data class PageChanged(
        val bookPage: Int,
        override val type: String = "page_changed"
    ) : SyncMessage()

    data class ArrangementUpdate(
        val devices: List<Device>,
        override val type: String = "arrangement_update"
    ) : SyncMessage()

    object PageTurnNextRequest : SyncMessage() {
        override val type: String = "page_turn_next"
    }

    object PageTurnPrevRequest : SyncMessage() {
        override val type: String = "page_turn_prev"
    }
}

private class SyncMessageDeserializer : JsonDeserializer<SyncMessage> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SyncMessage {
        val jsonObject = json?.asJsonObject ?: throw JsonParseException("Null JSON object")
        val type = jsonObject.get("type")?.asString ?: throw JsonParseException("SyncMessage type not found")

        return when (type) {
            "file_info" ->          context!!.deserialize(jsonObject, SyncMessage.FileInfo::class.java)
            "file_request" ->       context!!.deserialize(jsonObject, SyncMessage.FileRequest::class.java)
            "page_changed" ->       context!!.deserialize(jsonObject, SyncMessage.PageChanged::class.java)
            "arrangement_update" -> context!!.deserialize(jsonObject, SyncMessage.ArrangementUpdate::class.java)
            "page_turn_next" ->     context!!.deserialize(jsonObject, SyncMessage.PageTurnNextRequest::class.java)
            "page_turn_prev" ->     context!!.deserialize(jsonObject, SyncMessage.PageTurnPrevRequest::class.java)
            else -> throw JsonParseException("Unknown SyncMessage type: $type")
        }
    }
}
private class SyncMessageSerializer : JsonSerializer<SyncMessage> {
    override fun serialize(
        src: SyncMessage,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val jsonObject = when (src) {
            is SyncMessage.FileRequest,
            is SyncMessage.PageTurnNextRequest,
            is SyncMessage.PageTurnPrevRequest -> {
                com.google.gson.JsonObject()
            }
            else -> context.serialize(src, src.javaClass).asJsonObject
        }
        jsonObject.addProperty("type", src.type)
        return jsonObject
    }
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
    var onTurnRequestReceived: ((PageTurnDirection) -> Unit)? = null

    private val myDeviceName = Build.MODEL

    private val connectionsClient = Nearby.getConnectionsClient(context)

    private val strategy = Strategy.P2P_CLUSTER

    private val cacheManager = CacheManager(context)
    private val pdfPreferences = PdfPreferences(context)

    private var isLeader = false
    private var leaderEndpointId: String? = null
    private var currentFileUri: Uri? = null
    private var currentFileInfo: SyncMessage.FileInfo? = null

    private val connectedEndpoints = mutableListOf<Device>()
   
    private val outgoingFilePayloads = mutableMapOf<Long, String>()
    private val incomingFilePayloads = mutableMapOf<Long, Payload>()
    private val completedFilePayloads = mutableMapOf<Long, File>()
    private val pendingConnections = mutableMapOf<String, String>()

    private val gson = GsonBuilder()
        .registerTypeAdapter(SyncMessage::class.java, SyncMessageDeserializer())
        .registerTypeAdapter(SyncMessage::class.java, SyncMessageSerializer())
        .registerTypeAdapter(SyncMessage.FileRequest::class.java, SyncMessageSerializer())
        .registerTypeAdapter(SyncMessage.PageTurnNextRequest::class.java, SyncMessageSerializer())
        .registerTypeAdapter(SyncMessage.PageTurnPrevRequest::class.java, SyncMessageSerializer())
        .create()

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

    fun requestPageTurn(direction: PageTurnDirection) {
        if (isLeader) return
        val message = when (direction) {
            PageTurnDirection.NEXT -> SyncMessage.PageTurnNextRequest
            PageTurnDirection.PREVIOUS -> SyncMessage.PageTurnPrevRequest
        }
        leaderEndpointId?.let { sendMessage(it, message) }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> handleBytePayload(endpointId, payload.asBytes()!!)
                Payload.Type.STREAM -> {
                    onStatusUpdate?.invoke("Receiving file...")
                    incomingFilePayloads[payload.id] = payload
                }
                Payload.Type.FILE -> {
                    onStatusUpdate?.invoke("Receiving file...")
                    incomingFilePayloads[payload.id] = payload
                }
                else -> Log.w("SyncManager", "Unhandled payload type: ${payload.type}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.d("SyncManager_Transfer", "Endpoint: $endpointId, Payload ID: ${update.payloadId}, Status: ${update.status}, Bytes: ${update.bytesTransferred}/${update.totalBytes}")
            val percent = if (update.totalBytes > 0) (update.bytesTransferred * 100 / update.totalBytes) else 0

            if (outgoingFilePayloads.containsKey(update.payloadId)) {
               
                when (update.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS -> {
                        onStatusUpdate?.invoke("Sending file... $percent%")
                    }
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        onStatusUpdate?.invoke("File sent successfully.")
                        outgoingFilePayloads.remove(update.payloadId)
                    }
                    PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                        onStatusUpdate?.invoke("File send failed.")
                        outgoingFilePayloads.remove(update.payloadId)
                    }
                }
            } else if (incomingFilePayloads.containsKey(update.payloadId)) {
               
                when (update.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS -> {
                        onStatusUpdate?.invoke("Receiving file... $percent%")
                    }
                    PayloadTransferUpdate.Status.SUCCESS -> {
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
                    }
                    PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                        Log.e("SyncManager_Transfer", "Transfer failed/canceled. Endpoint: $endpointId, Payload ID: ${update.payloadId}")
                        incomingFilePayloads.remove(update.payloadId)
                        completedFilePayloads.remove(update.payloadId)
                        onStatusUpdate?.invoke("File receive failed.")
                    }
                }
            }
        }
    }

    private fun handleBytePayload(endpointId: String, bytes: ByteArray) {
        val json = bytes.toString(StandardCharsets.UTF_8)

        Log.d("SyncManager_Debug", "RECEIVED_JSON from $endpointId: $json")

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
                val file = File(fileUri.path ?: "")
                if (!file.exists() || file.length() == 0L) {
                    Log.e("SyncManager_FileSend", "File does not exist or is empty: ${file.absolutePath}")
                    onStatusUpdate?.invoke("Cannot send file: file missing or empty.")
                    return
                }
                Log.d("SyncManager_FileSend", "File ready to send: ${file.absolutePath}, size: ${file.length()}")
                try {
                    val filePayload = Payload.fromFile(file)
                    outgoingFilePayloads[filePayload.id] = endpointId
                    Log.d("SyncManager_FileSend", "Sending file: $fileUri, Payload ID: ${filePayload.id}")
                    connectionsClient.sendPayload(endpointId, filePayload)
                    onStatusUpdate?.invoke("Sending file to $endpointId... 0%")
                } catch (e: Exception) {
                    Log.e("SyncManager_FileSend", "Error sending file: $fileUri", e)

                    Log.e("SyncManager", "Error sending file", e)
                    onStatusUpdate?.invoke("Error sending file: ${e.message}")
                }
            }

            is SyncMessage.PageChanged -> {
                if (!isLeader) {
                    onPageTurnReceived?.invoke(PageTurn(message.bookPage))
                }
            }

            is SyncMessage.ArrangementUpdate -> {
                val processedList = message.devices.map { device ->
                    device.copy(isThisDevice = device.deviceName == myDeviceName)
                }
                onDeviceArrangementChanged?.invoke(processedList)
            }

            is SyncMessage.PageTurnNextRequest -> {
                if (isLeader) {
                    onTurnRequestReceived?.invoke(PageTurnDirection.NEXT)
                }

            }

            is SyncMessage.PageTurnPrevRequest -> {
                if (isLeader) {
                    onTurnRequestReceived?.invoke(PageTurnDirection.PREVIOUS)
                }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d("SyncManager", "onConnectionInitiated - Endpoint ID: $endpointId, Name: ${connectionInfo.endpointName}")
            onStatusUpdate?.invoke("Accepting connection to ${connectionInfo.endpointName}")
            pendingConnections[endpointId] = connectionInfo.endpointName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val deviceName = pendingConnections.remove(endpointId) ?: "Unknown Device"
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                Log.d("SyncManager", "onConnectionResult SUCCESS - Connected to $deviceName ($endpointId)")
                onStatusUpdate?.invoke("Connected to $deviceName.")
                if (isLeader) {
                    val newDevice = Device(endpointId, deviceName)
                    if (!connectedEndpoints.any { it.endpointId == newDevice.endpointId }) {
                        connectedEndpoints.add(newDevice)
                    }
                    onFollowerJoined?.invoke(newDevice)
                    broadcastDeviceArrangement()
                    currentFileInfo?.let { info -> sendMessage(endpointId, info)
//                        val currentPageForLeader = pdfPreferences.getPageForFile(info.fileHash)
//                        sendMessage(endpointId, SyncMessage.PageChanged(currentPageForLeader))
                    }
                } else {
                    connectionsClient.stopDiscovery()
                    leaderEndpointId = endpointId
                    onLeaderFound?.invoke(endpointId)
                }
            } else {
                Log.e("SyncManager", "onConnectionResult FAILURE - $deviceName: ${result.status.statusMessage}")
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
            val deviceName = connectedEndpoints.find { it.endpointId == endpointId }?.deviceName
                ?: pendingConnections[endpointId] ?: "a device"
            Log.d("SyncManager", "onDisconnected - $deviceName ($endpointId) disconnected")

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
            myDeviceName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
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
        Log.d("SyncManager", "Starting discovery with SERVICE_ID: $SERVICE_ID")

        connectionsClient.startDiscovery(
            SERVICE_ID, object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("SyncManager", "Discovered endpoint: $endpointId with name: ${info.endpointName}")

                    onStatusUpdate?.invoke("Found leader: ${info.endpointName}. Connecting...")
                    connectionsClient.requestConnection(
                        myDeviceName,
                        endpointId,
                        connectionLifecycleCallback
                    )
                        .addOnFailureListener { e ->
                            Log.e("SyncManager", "Connection request failed for ${info.endpointName}: ${e.message}")

                            onStatusUpdate?.invoke("Connection request to ${info.endpointName} failed: ${e.message}. Restarting discovery might be needed if no other leaders are found.")
                        }
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.w("SyncManager", "Endpoint lost: $endpointId")

                    onStatusUpdate?.invoke("Leader $endpointId lost during discovery.")
                }
            }, discoveryOptions
        ).addOnSuccessListener {
            Log.d("SyncManager", "Discovery started successfully")

            onStatusUpdate?.invoke("Searching for a leader...")
            android.os.Handler(context.mainLooper).postDelayed({
                if (leaderEndpointId == null && !isLeader) {
                    Log.d("SyncManager", "No leader found within timeout")

                    onNoLeaderFound?.invoke()
                }
            }, DISCOVERY_TIMEOUT_MS)
        }.addOnFailureListener { e ->
            Log.e("SyncManager", "Failed to start discovery: ${e.message}")

            onStatusUpdate?.invoke("Discovery failed to start: ${e.message}")
            onNoLeaderFound?.invoke()
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        onStatusUpdate?.invoke("Stopped discovery.")
    }

    fun broadcastPageTurn(pageTurn: PageTurn) {
        if (!isLeader) return
        sendMessageToAll(SyncMessage.PageChanged(pageTurn.bookPage))
    }

    private fun broadcastDeviceArrangement() {
        if (!isLeader) return

        val selfDevice =
            Device("leader_self_id", myDeviceName, isLeader = true, isThisDevice = true)

        val allDevices = mutableListOf(selfDevice)
        allDevices.addAll(connectedEndpoints)

        val message = SyncMessage.ArrangementUpdate(allDevices)
        sendMessageToAll(message)

        onDeviceArrangementChanged?.invoke(allDevices)
    }

    private fun sendMessageToAll(message: SyncMessage) {

        if (connectedEndpoints.isNotEmpty()) {

            Log.d("SyncManager_Debug", "SENDING_TO_ALL: ${message::class.java.simpleName}")
            val payload =
                Payload.fromBytes(gson.toJson(message).toByteArray(StandardCharsets.UTF_8))
            connectionsClient.sendPayload(connectedEndpoints.map { it.endpointId }, payload)
        }
    }

    private fun sendMessage(endpointId: String, message: SyncMessage) {
        Log.d("SyncManager_Debug", "SENDING_TO_ONE ($endpointId): ${message::class.java.simpleName}")

        val json = gson.toJson(message)

        Log.d("SyncManager_Message", "Sending message to $endpointId: ${message::class.java.simpleName}, JSON: $json")

        Log.d("SyncManager_Debug", "Serialized message: $json")
        val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
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
        val cursor =
            context.contentResolver.query(uri, null, null, null, null) ?: return "unknown.pdf" to 0L
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
