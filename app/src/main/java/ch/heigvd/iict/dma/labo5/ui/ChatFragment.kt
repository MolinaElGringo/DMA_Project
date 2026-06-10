package ch.heigvd.iict.dma.labo5.ui

import android.annotation.SuppressLint
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import ch.heigvd.iict.dma.labo5.R
import ch.heigvd.iict.dma.labo5.adapter.MessageAdapter
import ch.heigvd.iict.dma.labo5.databinding.FragmentChatBinding
import ch.heigvd.iict.dma.labo5.viewmodel.ChatViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.max

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var adapter: MessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        setupMenu()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(viewModel.nearbyRepository.localUserName)
        binding.recyclerMessages.adapter = adapter
        binding.recyclerMessages.layoutManager =
            LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            val text = binding.editMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendTextMessage(text)
                binding.editMessage.text?.clear()
            }
        }

        binding.btnDraw.setOnClickListener {
            findNavController().navigate(R.id.action_chat_to_draw)
        }

        // Maintenir pour enregistrer, relâcher pour envoyer (cf. note d'origine : OnTouch obligatoire).
        binding.btnAudio.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startRecording(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecordingAndSend(); v.performClick(); true
                }
                else -> false
            }
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) =
                menuInflater.inflate(R.menu.menu_chat, menu)

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                if (menuItem.itemId == R.id.action_peers) {
                    findNavController().navigate(R.id.action_chat_to_peers); true
                } else false
        }, viewLifecycleOwner)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages)
                if (messages.isNotEmpty())
                    binding.recyclerMessages.scrollToPosition(messages.size - 1)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectedPeers.collect { peers ->
                val count = peers.size
                activity?.title =
                    if (count == 0) "ProxyChat — personne connecté"
                    else "ProxyChat — $count connecté(s)"
            }
        }
    }

    // --- Audio ---

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var recordStartMs = 0L
    private var meterJob: Job? = null
    private val rawAmplitudes = ArrayList<Int>()

    private fun startRecording() {
        if (isRecording) return
        try {
            audioFile = File.createTempFile("audio_", ".3gp", requireContext().cacheDir)
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(requireContext())
            else @Suppress("DEPRECATION") MediaRecorder()

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            recordStartMs = SystemClock.elapsedRealtime()
            rawAmplitudes.clear()
            showRecordingUi(true)
            startMeter()
        } catch (e: Exception) {
            cleanupRecorder()
            audioFile?.delete(); audioFile = null
            showRecordingUi(false)
        }
    }

    /** Boucle qui lit l'amplitude du micro et alimente la forme d'onde + le chrono. */
    private fun startMeter() {
        meterJob = viewLifecycleOwner.lifecycleScope.launch {
            // premier appel souvent à 0, on l'ignore
            try { mediaRecorder?.maxAmplitude } catch (_: Exception) {}
            while (isActive && isRecording) {
                val amp = try { mediaRecorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                rawAmplitudes.add(amp)
                binding.waveformLive.pushAmplitude(amp)
                val elapsed = SystemClock.elapsedRealtime() - recordStartMs
                binding.recTimer.text = formatMs(elapsed)
                delay(50)
            }
        }
    }

    private fun stopRecordingAndSend() {
        if (!isRecording) return
        isRecording = false
        meterJob?.cancel(); meterJob = null
        showRecordingUi(false)

        val durationMs = SystemClock.elapsedRealtime() - recordStartMs
        var success = true
        try { mediaRecorder?.stop() } catch (e: Exception) { success = false }
        finally { cleanupRecorder() }

        val file = audioFile; audioFile = null
        if (success && file != null && file.length() > 0 && durationMs > 300) {
            viewModel.sendAudio(file.readBytes(), downsample(rawAmplitudes, 44), durationMs)
        }
        file?.delete()
        binding.waveformLive.clear()
    }

    private fun showRecordingUi(recording: Boolean) {
        binding.recDot.visibility = if (recording) View.VISIBLE else View.GONE
        binding.recTimer.visibility = if (recording) View.VISIBLE else View.GONE
        binding.waveformLive.visibility = if (recording) View.VISIBLE else View.GONE
        binding.btnDraw.visibility = if (recording) View.GONE else View.VISIBLE
        binding.editMessage.visibility = if (recording) View.GONE else View.VISIBLE
        binding.btnSend.visibility = if (recording) View.GONE else View.VISIBLE
        if (recording) binding.recTimer.text = "0:00"
    }

    /** Réduit la suite d'amplitudes brutes à [bars] valeurs normalisées 0..100. */
    private fun downsample(raw: List<Int>, bars: Int): List<Int> {
        if (raw.isEmpty()) return List(bars) { 30 }
        val peak = max(1, raw.maxOrNull() ?: 1)
        val out = ArrayList<Int>(bars)
        val bucket = max(1, raw.size / bars)
        var i = 0
        while (i < raw.size && out.size < bars) {
            var localMax = 0
            for (j in i until minOf(i + bucket, raw.size)) localMax = max(localMax, raw[j])
            out.add((localMax * 100 / peak).coerceIn(4, 100))
            i += bucket
        }
        while (out.size < bars) out.add(out.lastOrNull() ?: 30)
        return out
    }

    private fun cleanupRecorder() {
        try { mediaRecorder?.reset(); mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
    }

    private fun formatMs(ms: Long): String {
        val s = (ms / 1000).toInt()
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        meterJob?.cancel(); meterJob = null
        cleanupRecorder()
        isRecording = false
        _binding = null
    }
}