package ch.heigvd.iict.dma.labo5.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import ch.heigvd.iict.dma.labo5.databinding.FragmentDrawBinding
import ch.heigvd.iict.dma.labo5.model.DrawAction
import ch.heigvd.iict.dma.labo5.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class DrawFragment : Fragment() {

    private var _binding: FragmentDrawBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrawBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Diffusion en direct de chaque trait dessiné localement
        binding.drawView.onLocalDraw = { action, x, y, color, width ->
            viewModel.sendDrawEvent(action, x, y, color, width)
        }

        // 2) Application en direct des traits reçus des pairs
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.drawEvents.collect { event ->
                binding.drawView.applyRemoteEvent(event)
            }
        }

        // 3) Sélection de couleur
        binding.colorBlack.setOnClickListener { binding.drawView.currentColor = Color.BLACK }
        binding.colorRed.setOnClickListener { binding.drawView.currentColor = Color.RED }
        binding.colorBlue.setOnClickListener { binding.drawView.currentColor = Color.BLUE }
        binding.colorGreen.setOnClickListener { binding.drawView.currentColor = Color.rgb(0, 150, 0) }

        // Effacer : on vide le tableau local ET on prévient les pairs
        binding.btnClearDrawing.setOnClickListener {
            binding.drawView.clear()
            viewModel.sendDrawEvent(DrawAction.CLEAR, 0f, 0f, binding.drawView.currentColor, 0f)
        }

        // Envoyer : on poste un instantané du tableau dans le fil de discussion
        binding.btnSendDrawing.setOnClickListener {
            val bitmap = binding.drawView.getBitmap()
            val stream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            viewModel.sendDrawing(stream.toByteArray())
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.drawView.onLocalDraw = null
        _binding = null
    }
}
