package ch.heigvd.iict.dma.labo5.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.heigvd.iict.dma.labo5.model.Message
import ch.heigvd.iict.dma.labo5.model.MessageType
import ch.heigvd.iict.dma.labo5.model.Peer
import ch.heigvd.iict.dma.labo5.nearby.NearbyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val nearbyRepository = NearbyRepository(application)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    val connectedPeers: StateFlow<List<Peer>> = nearbyRepository.connectedPeers

    init {
        // Écoute les payloads entrants et les convertit en Message
        viewModelScope.launch {
            nearbyRepository.incomingPayload.collect { pair ->
                pair?.let { (endpointId, bytes) ->
                    val message = deserializeMessage(endpointId, bytes)
                    message?.let { addMessage(it) }
                }
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

    fun sendAudio(audioData: ByteArray) {
        val message = Message(
            senderId = "me",
            senderName = nearbyRepository.localUserName,
            type = MessageType.AUDIO,
            audioData = audioData
        )
        addMessage(message)
        nearbyRepository.sendPayload(serializeMessage(message))
    }

    private fun addMessage(message: Message) {
        _messages.value = _messages.value + message
    }

    // Sérialisation simple en JSON + données binaires encodées en Base64
    private fun serializeMessage(message: Message): ByteArray {
        val json = JSONObject().apply {
            put("id", message.id)
            put("senderId", message.senderId)
            put("senderName", message.senderName)
            put("type", message.type.name)
            put("timestamp", message.timestamp)
            message.text?.let { put("text", it) }
            message.audioData?.let { put("audioData", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
            message.imageData?.let { put("imageData", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun deserializeMessage(endpointId: String, bytes: ByteArray): Message? {
        return try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            Message(
                id = json.getString("id"),
                senderId = endpointId,
                senderName = json.getString("senderName"),
                type = MessageType.valueOf(json.getString("type")),
                timestamp = json.getLong("timestamp"),
                text = json.optString("text").ifEmpty { null },
                audioData = json.optString("audioData").ifEmpty { null }
                    ?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) },
                imageData = json.optString("imageData").ifEmpty { null }
                    ?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
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