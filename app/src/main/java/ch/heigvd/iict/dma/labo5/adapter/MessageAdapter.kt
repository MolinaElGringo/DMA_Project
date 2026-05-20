package ch.heigvd.iict.dma.labo5.adapter

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ch.heigvd.iict.dma.labo5.databinding.ItemMessageBinding
import ch.heigvd.iict.dma.labo5.model.Message
import ch.heigvd.iict.dma.labo5.model.MessageType
import java.io.File

class MessageAdapter(private val localUserName: String) :
    ListAdapter<Message, MessageAdapter.MessageViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
    }

    inner class MessageViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            val isMe = message.senderId == "me"

            // Nom de l'expéditeur
            binding.textSenderName.text = if (isMe) "Moi" else message.senderName

            // Aligner à droite si c'est nous, à gauche sinon
            binding.root.layoutDirection =
                if (isMe) android.view.View.LAYOUT_DIRECTION_RTL
                else android.view.View.LAYOUT_DIRECTION_LTR

            // Cacher tout par défaut
            binding.textMessage.visibility = android.view.View.GONE
            binding.imageMessage.visibility = android.view.View.GONE
            binding.btnPlayAudio.visibility = android.view.View.GONE

            when (message.type) {
                MessageType.TEXT -> {
                    binding.textMessage.visibility = android.view.View.VISIBLE
                    binding.textMessage.text = message.text
                }

                MessageType.DRAWING -> {
                    binding.imageMessage.visibility = android.view.View.VISIBLE
                    message.imageData?.let { bytes ->
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                            bytes, 0, bytes.size
                        )
                        binding.imageMessage.setImageBitmap(bitmap)
                    }
                }

                MessageType.AUDIO -> {
                    binding.btnPlayAudio.visibility = android.view.View.VISIBLE
                    binding.btnPlayAudio.setOnClickListener {
                        playAudio(message.audioData)
                    }
                }
            }
        }

        private fun playAudio(audioData: ByteArray?) {
            audioData ?: return
            val context = binding.root.context

            // On écrit les bytes dans un fichier temporaire pour MediaPlayer
            val tempFile = File.createTempFile("play_", ".3gp", context.cacheDir)
            tempFile.writeBytes(audioData)

            MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    tempFile.delete()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}