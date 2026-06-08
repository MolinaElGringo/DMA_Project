package ch.heigvd.iict.dma.labo5.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.heigvd.iict.dma.labo5.model.DrawAction
import ch.heigvd.iict.dma.labo5.model.DrawEvent
import ch.heigvd.iict.dma.labo5.model.Message
import ch.heigvd.iict.dma.labo5.model.MessageType
import ch.heigvd.iict.dma.labo5.model.Peer
import ch.heigvd.iict.dma.labo5.nearby.NearbyRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val nearbyRepository = NearbyRepository(application)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    val connectedPeers: StateFlow<List<Peer>> = nearbyRepository.connectedPeers

    // Évènements de dessin collaboratif reçus des pairs (flux d'évènements => SharedFlow).
    private val _drawEvents = MutableSharedFlow<DrawEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val drawEvents: SharedFlow<DrawEvent> = _drawEvents.asSharedFlow()

    init {
        // Écoute les payloads entrants et les route selon leur "kind".
        viewModelScope.launch {
            nearbyRepository.incomingPayload.collect { (endpointId, bytes) ->
                routeIncoming(endpointId, bytes)
            }
        }
    }

    fun setUserName(name: String) {
        nearbyRepository.localUserName = name
    }

    fun startChat() {
        nearbyRepository.startAdvertising()
        nearbyRepository.startDiscovery()
    }

    fun sendTextMessage(text: String) {
        val message = Message(
            senderId = "me",
            senderName = nearbyRepository.localUserName,
            type = MessageType.TEXT,
            text = text
        )
        addMessage(message)
        nearbyRepository.sendPayload(serializeMessage(message))
    }

    fun sendDrawing(imageData: ByteArray) {
        val message = Message(
            senderId = "me",
            senderName = nearbyRepository.localUserName,
            type = MessageType.DRAWING,
            imageData = imageData
        )
        addMessage(message)
        nearbyRepository.sendPayload(serializeMessage(message))
    }

    fun sendAudio(audioData: ByteArray, amplitudes: List<Int>, durationMs: Long) {
        val message = Message(
            senderId = "me",
            senderName = nearbyRepository.localUserName,
            type = MessageType.AUDIO,
            audioData = audioData,
            amplitudes = amplitudes,
            audioDurationMs = durationMs
        )
        addMessage(message)
        nearbyRepository.sendPayload(serializeMessage(message))
    }

    /** Diffuse un évènement de dessin en direct. N'ajoute rien localement : le DrawView local dessine déjà. */
    fun sendDrawEvent(action: DrawAction, x: Float, y: Float, color: Int, width: Float) {
        val json = JSONObject().apply {
            put("kind", "draw")
            put("action", action.name)
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("color", color)
            put("width", width.toDouble())
        }
        nearbyRepository.sendPayload(json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun addMessage(message: Message) {
        _messages.value = _messages.value + message
    }

    // --- Routage / (dé)sérialisation ---

    private fun routeIncoming(endpointId: String, bytes: ByteArray) {
        val json = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            return
        }
        when (json.optString("kind", "message")) {
            "draw" -> parseDrawEvent(endpointId, json)?.let { _drawEvents.tryEmit(it) }
            else -> deserializeMessage(endpointId, json)?.let { addMessage(it) }
        }
    }

    // Sérialisation simple en JSON + données binaires encodées en Base64
// Sérialisation simple en JSON + données binaires encodées en Base64
    private fun serializeMessage(message: Message): ByteArray {
        val json = JSONObject().apply {
            put("kind", "message")
            put("id", message.id)
            put("senderId", message.senderId)
            put("senderName", message.senderName)
            put("type", message.type.name)
            put("timestamp", message.timestamp)
            put("audioDurationMs", message.audioDurationMs)
            message.text?.let { put("text", it) }
            message.audioData?.let { put("audioData", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
            message.imageData?.let { put("imageData", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
            message.amplitudes?.let { amps ->
                put("amplitudes", org.json.JSONArray().apply { amps.forEach { put(it) } })
            }
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun deserializeMessage(endpointId: String, json: JSONObject): Message? {
        return try {
            val amps = json.optJSONArray("amplitudes")?.let { arr ->
                List(arr.length()) { arr.getInt(it) }
            }
            Message(
                id = json.getString("id"),
                senderId = endpointId,
                senderName = json.getString("senderName"),
                type = MessageType.valueOf(json.getString("type")),
                timestamp = json.getLong("timestamp"),
                audioDurationMs = json.optLong("audioDurationMs", 0L),
                text = json.optString("text").ifEmpty { null },
                audioData = json.optString("audioData").ifEmpty { null }
                    ?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) },
                imageData = json.optString("imageData").ifEmpty { null }
                    ?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) },
                amplitudes = amps
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDrawEvent(endpointId: String, json: JSONObject): DrawEvent? {
        return try {
            DrawEvent(
                sender = endpointId,
                action = DrawAction.valueOf(json.getString("action")),
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                color = json.optInt("color", 0xFF000000.toInt()),
                width = json.optDouble("width", 8.0).toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        nearbyRepository.stop()
    }
}
