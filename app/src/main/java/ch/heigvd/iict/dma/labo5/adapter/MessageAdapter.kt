package ch.heigvd.iict.dma.labo5.adapter

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ch.heigvd.iict.dma.labo5.R
import ch.heigvd.iict.dma.labo5.databinding.ItemMessageBinding
import ch.heigvd.iict.dma.labo5.model.Message
import ch.heigvd.iict.dma.labo5.model.MessageType
import java.io.File
import java.util.Locale

class MessageAdapter(private val localUserName: String) :
    ListAdapter<Message, MessageAdapter.MessageViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(o: Message, n: Message) = o.id == n.id
        override fun areContentsTheSame(o: Message, n: Message) = o == n
    }

    // --- lecture audio : un seul lecteur à la fois ---
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var playingId: String? = null
    private var playingHolder: MessageViewHolder? = null
    private var progressTick: Runnable? = null

    inner class MessageViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            val isMe = message.senderId == "me"
            val ctx = binding.root.context

            binding.textSenderName.text = if (isMe) "Moi" else message.senderName

            // Alignement de la colonne (bulle) à droite si c'est nous, à gauche sinon
            (binding.bubbleColumn.layoutParams as FrameLayout.LayoutParams).gravity =
                if (isMe) Gravity.END else Gravity.START
            (binding.textSenderName.layoutParams as LinearLayout.LayoutParams).gravity =
                if (isMe) Gravity.END else Gravity.START

            // Couleurs de la bulle
            binding.bubble.setBackgroundResource(
                if (isMe) R.drawable.bg_bubble_sent else R.drawable.bg_bubble_received
            )

            binding.textMessage.visibility = View.GONE
            binding.imageMessage.visibility = View.GONE
            binding.audioContainer.visibility = View.GONE

            when (message.type) {
                MessageType.TEXT -> {
                    binding.textMessage.visibility = View.VISIBLE
                    binding.textMessage.text = message.text
                    binding.textMessage.setTextColor(
                        ContextCompat.getColor(
                            ctx,
                            if (isMe) R.color.bubble_sent_text else R.color.bubble_received_text
                        )
                    )
                }

                MessageType.DRAWING -> {
                    binding.imageMessage.visibility = View.VISIBLE
                    message.imageData?.let {
                        binding.imageMessage.setImageBitmap(
                            BitmapFactory.decodeByteArray(it, 0, it.size)
                        )
                    }
                }

                MessageType.AUDIO -> bindAudio(message, isMe, ctx)
            }
        }

        private fun bindAudio(message: Message, isMe: Boolean, ctx: android.content.Context) {
            binding.audioContainer.visibility = View.VISIBLE

            // Couleurs adaptées au fond de la bulle
            val wavePlayed = ContextCompat.getColor(
                ctx, if (isMe) R.color.wave_played_on_primary else R.color.wave_played
            )
            val waveUnplayed = ContextCompat.getColor(
                ctx, if (isMe) R.color.wave_unplayed_on_primary else R.color.wave_unplayed
            )
            binding.waveform.setColors(wavePlayed, waveUnplayed)
            binding.waveform.setAmplitudes(message.amplitudes ?: defaultWave())

            binding.btnPlayAudio.setBackgroundResource(
                if (isMe) R.drawable.bg_play_circle_sent else R.drawable.bg_play_circle_received
            )
            val iconTint = if (isMe) ContextCompat.getColor(ctx, R.color.brand_primary)
            else android.graphics.Color.WHITE
            binding.btnPlayAudio.imageTintList = ColorStateList.valueOf(iconTint)
            binding.textDuration.setTextColor(
                if (isMe) android.graphics.Color.WHITE
                else ContextCompat.getColor(ctx, R.color.time_text)
            )

            val playing = playingId == message.id
            if (playing) {
                playingHolder = this
                binding.btnPlayAudio.setImageResource(R.drawable.ic_pause)
            } else {
                binding.btnPlayAudio.setImageResource(R.drawable.ic_play_arrow)
                binding.waveform.setProgress(0f)
                binding.textDuration.text = formatMs(message.audioDurationMs)
            }

            binding.btnPlayAudio.setOnClickListener { togglePlay(this, message) }
        }
    }

    private fun togglePlay(holder: MessageViewHolder, message: Message) {
        if (playingId == message.id) { stopPlayback(); return }
        stopPlayback()

        val data = message.audioData ?: return
        val ctx = holder.binding.root.context
        val tmp = File.createTempFile("play_", ".3gp", ctx.cacheDir)
        tmp.writeBytes(data)

        val mp = MediaPlayer()
        try {
            mp.setDataSource(tmp.absolutePath)
            mp.prepare()
        } catch (e: Exception) {
            mp.release(); tmp.delete(); return
        }

        player = mp
        playingId = message.id
        playingHolder = holder
        holder.binding.btnPlayAudio.setImageResource(R.drawable.ic_pause)

        mp.setOnCompletionListener { stopPlayback() }
        mp.start()

        val total = mp.duration.coerceAtLeast(1)
        progressTick = object : Runnable {
            override fun run() {
                val p = player ?: return
                try {
                    val frac = p.currentPosition.toFloat() / total
                    playingHolder?.let { h ->
                        if (playingId == message.id) {
                            h.binding.waveform.setProgress(frac)
                            h.binding.textDuration.text = formatMs(p.currentPosition.toLong())
                        }
                    }
                } catch (_: Exception) {}
                handler.postDelayed(this, 50)
            }
        }
        handler.post(progressTick!!)

        mp.setOnErrorListener { _, _, _ -> stopPlayback(); true }
    }

    private fun stopPlayback() {
        progressTick?.let { handler.removeCallbacks(it) }
        progressTick = null
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        playingHolder?.let {
            it.binding.btnPlayAudio.setImageResource(R.drawable.ic_play_arrow)
            it.binding.waveform.setProgress(0f)
        }
        playingId = null
        playingHolder = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) =
        holder.bind(getItem(position))

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        return String.format(Locale.getDefault(), "%d:%02d", totalSec / 60, totalSec % 60)
    }

    // Repli si un message audio arrive sans forme d'onde (ancienne version, etc.)
    private fun defaultWave(): List<Int> = List(40) { (20..70).random() }
}