package com.fluxzen.legatopages

import android.content.Context
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
import java.nio.charset.StandardCharsets

data class PageTurn(val bookPage: Int)

class SyncManager(
    context: Context,
    private val onPageTurnReceived: (PageTurn) -> Unit,
    private val onDeviceCountChanged: (Int) -> Unit,
    private val onStatusUpdate: (String) -> Unit,
) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.fluxzen.legatopages.SERVICE_ID"

    private val strategy = Strategy.P2P_CLUSTER

    private val connectedEndpoints = mutableMapOf<String, String>()

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            onStatusUpdate("Accepting connection to ${connectionInfo.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                onStatusUpdate("Connected to $endpointId")
                connectedEndpoints[endpointId] = "Connected"
            } else {
                onStatusUpdate("Connection failed to $endpointId")
                connectedEndpoints.remove(endpointId)
            }

            onDeviceCountChanged(connectedEndpoints.size + 1)
        }

        override fun onDisconnected(endpointId: String) {
            onStatusUpdate("Disconnected from $endpointId.")
            connectedEndpoints.remove(endpointId)

            onDeviceCountChanged(connectedEndpoints.size + 1)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let {
                    val pageStr = it.toString(StandardCharsets.UTF_8)
                    val page = pageStr.toIntOrNull()
                    if (page != null) {
                        onPageTurnReceived(PageTurn(bookPage = page))
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            onStatusUpdate("Found device: ${info.endpointName}. Connecting...")
            connectionsClient.requestConnection(
                "LegatoPages Device",
                endpointId,
                connectionLifecycleCallback
            )
                .addOnFailureListener { e -> onStatusUpdate("Connection request failed: ${e.message}") }
        }

        override fun onEndpointLost(endpointId: String) {
            onStatusUpdate("Device lost: $endpointId")
        }
    }

    fun start() {
        onStatusUpdate("Starting sync...")
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(
            "LegatoPages Device",
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        )
            .addOnSuccessListener { onStatusUpdate("Advertising...") }
            .addOnFailureListener { e -> onStatusUpdate("Advertising failed: ${e.message}") }

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { onStatusUpdate("Discovering...") }
            .addOnFailureListener { e -> onStatusUpdate("Discovery failed: ${e.message}") }
    }

    fun broadcastPageTurn(pageTurn: PageTurn) {
        if (connectedEndpoints.isEmpty()) return

        val payload =
            Payload.fromBytes(pageTurn.bookPage.toString().toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(connectedEndpoints.keys.toList(), payload)
        onStatusUpdate("Broadcast page ${pageTurn.bookPage}")
    }

    fun stop() {
        onStatusUpdate("Stopping sync.")
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        onDeviceCountChanged(1)
    }
}