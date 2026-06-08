package ch.heigvd.iict.dma.labo5.model

import java.util.UUID

enum class MessageType {
    TEXT, AUDIO, DRAWING
}

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val type: MessageType,
    val text: String? = null,            // si TEXT
    val audioData: ByteArray? = null,    // si AUDIO
    val imageData: ByteArray? = null,    // si DRAWING
    val amplitudes: List<Int>? = null,   // si AUDIO : forme d'onde (valeurs 0..100)
    val audioDurationMs: Long = 0L,      // si AUDIO : durée en ms
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}