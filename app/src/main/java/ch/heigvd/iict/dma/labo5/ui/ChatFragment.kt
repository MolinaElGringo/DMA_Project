package ch.heigvd.iict.dma.labo5.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import ch.heigvd.iict.dma.labo5.R
import ch.heigvd.iict.dma.labo5.adapter.MessageAdapter
import ch.heigvd.iict.dma.labo5.databinding.FragmentChatBinding
import ch.heigvd.iict.dma.labo5.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var adapter: MessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(viewModel.nearbyRepository.localUserName)
        binding.recyclerMessages.adapter = adapter
        binding.recyclerMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
    }

    private fun setupListeners() {
        // Envoyer un message texte
        binding.btnSend.setOnClickListener {
            val text = binding.editMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendTextMessage(text)
                binding.editMessage.text?.clear()
            }
        }

        // Ouvrir le canvas de dessin
        binding.btnDraw.setOnClickListener {
            findNavController().navigate(R.id.action_chat_to_draw)
        }

        // Enregistrer un vocal (appui long = enregistre, relâche = envoie)
        binding.btnAudio.setOnLongClickListener {
            startRecording()
            true
        }
        binding.btnAudio.setOnClickListener {
            stopRecordingAndSend()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding.recyclerMessages.scrollToPosition(messages.size - 1)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectedPeers.collect { peers ->
                val count = peers.size
                activity?.title = if (count == 0) "PictoChat — personne connecté"
                else "PictoChat — $count connecté(s)"
            }
        }
    }

    // --- Audio ---

    private var mediaRecorder: android.media.MediaRecorder? = null
    private var audioFile: java.io.File? = null

    private fun startRecording() {
        audioFile = java.io.File.createTempFile("audio_", ".3gp", requireContext().cacheDir)
        mediaRecorder = android.media.MediaRecorder().apply {
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFile!!.absolutePath)
            prepare()
            start()
        }
        binding.btnAudio.text = "⏹"
    }

    private fun stopRecordingAndSend() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        binding.btnAudio.text = "🎤"

        audioFile?.let { file ->
            val bytes = file.readBytes()
            viewModel.sendAudio(bytes)
            file.delete()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaRecorder?.release()
        mediaRecorder = null
        _binding = null
    }
}