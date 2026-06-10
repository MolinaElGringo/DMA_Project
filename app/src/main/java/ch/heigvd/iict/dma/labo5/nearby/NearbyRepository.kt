package ch.heigvd.iict.dma.labo5.nearby

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import ch.heigvd.iict.dma.labo5.model.Peer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

class NearbyRepository(private val context: Context) {

    companion object {
        private const val SERVICE_ID = "ch.heigvd.iict.dma.labo5"
        private const val TAG = "NearbyRepository"
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)

    // Peers actuellement connectés
    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    val connectedPeers: StateFlow<List<Peer>> = _connectedPeers

    // Payloads reçus. /!\ On utilise un SharedFlow (pas un StateFlow) :
    //  - un StateFlow "conflate" : deux payloads reçus coup sur coup => un seul gardé (perte de message)
    //  - un StateFlow rejoue sa dernière valeur aux nouveaux collecteurs => message traité 2x
    // Un SharedFlow avec un buffer correspond à la sémantique "flux d'évènements".
    private val _incomingPayload = MutableSharedFlow<Pair<String, ByteArray>>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingPayload: SharedFlow<Pair<String, ByteArray>> = _incomingPayload.asSharedFlow()

    // Notre propre nom (défini au démarrage)
    var localUserName: String = "Unknown"

    // Noms découverts (côté discovery) et noms reçus à l'initiation de connexion (côté advertiser)
    private val discoveredEndpoints = mutableMapOf<String, String>()
    private val pendingNames = mutableMapOf<String, String>()
    // endpoints pour lesquels on a déjà demandé une connexion (évite les requêtes en double)
    private val requestedEndpoints = mutableSetOf<String>()

    // CallBacks

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with ${connectionInfo.endpointName}")
            // On mémorise le nom annoncé par le pair : c'est la seule source fiable côté advertiser.
            pendingNames[endpointId] = connectionInfo.endpointName
            // On accepte automatiquement toutes les connexions
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d(TAG, "Connected to $endpointId")
                // Nom : on prend ce qu'on a découvert, sinon ce qui a été annoncé à l'initiation.
                val name = discoveredEndpoints[endpointId]
                    ?: pendingNames[endpointId]
                    ?: "Unknown"
                // Déduplication : on n'ajoute pas deux fois le même endpoint.
                if (_connectedPeers.value.none { it.endpointId == endpointId }) {
                    _connectedPeers.value = _connectedPeers.value + Peer(endpointId, name)
                }
            } else {
                Log.w(TAG, "Connection failed to $endpointId : ${result.status}")
                requestedEndpoints.remove(endpointId)
            }
            pendingNames.remove(endpointId)
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            _connectedPeers.value = _connectedPeers.value.filter { it.endpointId != endpointId }
            requestedEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {

        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                // tryEmit : non bloquant, adapté à un callback ; le buffer évite la perte.
                _incomingPayload.tryEmit(endpointId to bytes)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // On pourra afficher une progress bar ici plus tard si besoin
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            discoveredEndpoints[endpointId] = info.endpointName
            // onEndpointFound peut se redéclencher : on ne demande la connexion qu'une fois.
            if (requestedEndpoints.add(endpointId)) {
                connectionsClient.requestConnection(
                    localUserName,
                    endpointId,
                    connectionLifecycleCallback
                ).addOnFailureListener {
                    Log.w(TAG, "requestConnection failed for $endpointId", it)
                    requestedEndpoints.remove(endpointId)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            discoveredEndpoints.remove(endpointId)
            requestedEndpoints.remove(endpointId)
        }
    }

    // API

    fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        connectionsClient.startAdvertising(
            localUserName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
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
        val endpointIds = _connectedPeers.value.map { it.endpointId }
        if (endpointIds.isEmpty()) return

        val payload = Payload.fromBytes(bytes)
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
        pendingNames.clear()
        requestedEndpoints.clear()
    }
}
