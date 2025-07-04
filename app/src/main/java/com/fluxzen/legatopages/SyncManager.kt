package com.fluxzen.legatopages

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.nio.charset.StandardCharsets

class SyncManager(
    private val context: Context,
    private val onStatusUpdate: (String) -> Unit,
    private val onPageReceived: (Int) -> Unit
) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.fluxzen.legatopages.SERVICE_ID"
    private val strategy = Strategy.P2P_STAR

    private var connectedEndpointId: String? = null

    

    fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(
            "Leader Device", 
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            onStatusUpdate("Advertising started. Waiting for a follower.")
        }.addOnFailureListener { e ->
            onStatusUpdate("Error starting advertising: ${e.message}")
        }
    }

    fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            onStatusUpdate("Discovery started. Looking for a leader.")
        }.addOnFailureListener { e ->
            onStatusUpdate("Error starting discovery: ${e.message}")
        }
    }

    fun sendPage(page: Int) {
        connectedEndpointId?.let { endpointId ->
            val payload = Payload.fromBytes(page.toString().toByteArray(StandardCharsets.UTF_8))
            connectionsClient.sendPayload(endpointId, payload)
            onStatusUpdate("Sent page: $page")
        }
    }

    fun disconnect() {
        connectedEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
        connectionsClient.stopAllEndpoints()
        onStatusUpdate("Disconnected.")
        connectedEndpointId = null
    }

    

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            onStatusUpdate("Accepting connection to ${connectionInfo.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    onStatusUpdate("Connected to device!")
                    connectedEndpointId = endpointId
                    connectionsClient.stopDiscovery()
                    connectionsClient.stopAdvertising()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    onStatusUpdate("Connection rejected.")
                    connectedEndpointId = null
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    onStatusUpdate("Connection error.")
                    connectedEndpointId = null
                }
                else -> {
                    onStatusUpdate("Unknown connection result.")
                    connectedEndpointId = null
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            onStatusUpdate("Disconnected from device.")
            connectedEndpointId = null
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val pageStr = payload.asBytes()?.toString(StandardCharsets.UTF_8)
                val page = pageStr?.toIntOrNull()
                if (page != null) {
                    onPageReceived(page)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, discoveredEndpointInfo: DiscoveredEndpointInfo) {
            onStatusUpdate("Found leader: ${discoveredEndpointInfo.endpointName}. Connecting...")
            connectionsClient.requestConnection(
                "Follower Device",
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener { e ->
                onStatusUpdate("Failed to request connection: ${e.message}")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            onStatusUpdate("Leader device is no longer available.")
        }
    }
}