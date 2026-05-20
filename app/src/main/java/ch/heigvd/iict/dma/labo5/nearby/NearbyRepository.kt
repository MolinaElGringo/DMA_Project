package ch.heigvd.iict.dma.labo5.nearby

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import ch.heigvd.iict.dma.labo5.model.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NearbyRepository(private val context: Context) {

    companion object {
        private const val SERVICE_ID = "ch.heigvd.iict.dma.labo5"
        private const val TAG = "NearbyRepository"
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)

    // Peers actuellement connectés
    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    val connectedPeers: StateFlow<List<Peer>> = _connectedPeers

    // Payload reçus — le ViewModel écoute ça et construit les messages
    private val _incomingPayload = MutableStateFlow<Pair<String, ByteArray>?>(null)
    val incomingPayload: StateFlow<Pair<String, ByteArray>?> = _incomingPayload

    // Notre propre nom (défini au démarrage)
    var localUserName: String = "Unknown"

    // CallBacks

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "... ${connectionInfo.endpointName}")
            // On accepte automatiquement toutes les connexions
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d(TAG, "Connected to $endpointId")
                // On cherche le nom dans les peers découverts, sinon "Unknown"
                val name = discoveredEndpoints[endpointId] ?: "Unknown"
                val newPeer = Peer(endpointId, name)
                _connectedPeers.value = _connectedPeers.value + newPeer
            } else {
                Log.w(TAG, "Connection failed to $endpointId : ${result.status}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            _connectedPeers.value = _connectedPeers.value.filter { it.endpointId != endpointId }
        }
    }

    private val payloadCallback = object : PayloadCallback() {

        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                _incomingPayload.value = Pair(endpointId, bytes)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // On pourra afficher une progress bar ici plus tard si besoin
        }
    }


    private val discoveredEndpoints = mutableMapOf<String, String>()

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            discoveredEndpoints[endpointId] = info.endpointName
            // On demande la connexion automatiquement
            connectionsClient.requestConnection(
                localUserName,
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            discoveredEndpoints.remove(endpointId)
        }
    }

    // API

    fun startAdvertising() {
        val optons = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        connectionsClient.startAdvertising(
            localUserName,
            SERVICE_ID,
            connectionLifecycleCallback,
            optons
        ).addOnSuccessListener {
            Log.d(TAG, "Started advertising")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start advertising", e)
        }
    }

    fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Started discovery")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start discovery", e)
        }
    }

    fun sendPayload(bytes: ByteArray) {
        val peers = _connectedPeers.value
        if (peers.isEmpty()) return

        val payload = Payload.fromBytes(bytes)
        val endpointIds = _connectedPeers.value.map { it.endpointId }

        connectionsClient.sendPayload(endpointIds, payload)
            .addOnSuccessListener { Log.d(TAG, "Payload sent") }
            .addOnFailureListener { Log.e(TAG, "Payload send failed: $it") }
    }

    fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _connectedPeers.value = emptyList()
        discoveredEndpoints.clear()
    }
}
