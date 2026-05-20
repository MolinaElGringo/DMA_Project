package ch.heigvd.iict.dma.labo5.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import ch.heigvd.iict.dma.labo5.databinding.FragmentDrawBinding
import ch.heigvd.iict.dma.labo5.viewmodel.ChatViewModel
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

        binding.btnSendDrawing.setOnClickListener {
            val bitmap = binding.drawView.getBitmap()
            val stream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()

            viewModel.sendDrawing(bytes)
            findNavController().navigateUp()
        }

        binding.btnClearDrawing.setOnClickListener {
            binding.drawView.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}