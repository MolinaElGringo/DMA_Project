package ch.heigvd.iict.dma.labo5.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import ch.heigvd.iict.dma.labo5.R
import ch.heigvd.iict.dma.labo5.databinding.FragmentPeersBinding
import ch.heigvd.iict.dma.labo5.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Écran listant les pairs actuellement connectés. Se met à jour en direct
 * (connexions / déconnexions) en observant connectedPeers du ViewModel.
 */
class PeersFragment : Fragment() {

    private var _binding: FragmentPeersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPeersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectedPeers.collect { peers ->
                if (peers.isEmpty()) {
                    binding.textPeers.text = getString(R.string.peers_empty)
                } else {
                    binding.textPeers.text = peers.joinToString("\n") { "• ${it.name}" }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
