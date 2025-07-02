package net.dacworld.android.holyplacesofthelord.ui.places

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.databinding.FragmentPlacesBinding

class PlacesFragment : Fragment() {

    private var _binding: FragmentPlacesBinding? = null
    private val binding get() = _binding!!

    private val dataViewModel: DataViewModel by activityViewModels()
    private lateinit var templeAdapter: TempleAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataViewModel.allTemples.collect { temples ->
                    // temples from StateFlow is non-null (List<Temple>)
                    // isLoading.value can be null initially, handle with care
                    val currentIsLoading = dataViewModel.isLoading.value ?: true // Default to loading if null

                    if (!currentIsLoading) { // Only update list/empty view if not actively loading
                        if (temples.isEmpty()) {
                            binding.emptyViewTextView.visibility = View.VISIBLE
                            binding.placesRecyclerView.visibility = View.GONE
                            templeAdapter.submitList(emptyList())
                        } else {
                            binding.emptyViewTextView.visibility = View.GONE
                            binding.placesRecyclerView.visibility = View.VISIBLE
                            templeAdapter.submitList(temples)
                        }
                    }
                    // If currentIsLoading is true, the progressBar observer will handle UI
                }
            }
        }

        dataViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                binding.placesRecyclerView.visibility = View.GONE
                binding.emptyViewTextView.visibility = View.GONE
            } else {
                // When loading finishes, the allTemples collector will re-evaluate
                // and set the correct visibility for RecyclerView or emptyView.
                // You might trigger a re-check if needed, but collect should handle it.
                // For example, if allTemples already has data, make RecyclerView visible:
                if (dataViewModel.allTemples.value.isNotEmpty()) {
                    binding.placesRecyclerView.visibility = View.VISIBLE
                    binding.emptyViewTextView.visibility = View.GONE
                } else {
                    // If temples are empty, the collector will show emptyViewTextView
                }
            }
        }

        dataViewModel.lastUpdateMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                // Display this message (e.g., Snackbar, Toast, or a TextView in your layout)
                // binding.statusTextView.text = message // Example
            }
        }

        // Example: Trigger data load if not handled elsewhere
        // if (dataViewModel.allTemples.value.isEmpty()) {
        //     dataViewModel.checkForUpdates(forceNetworkFetch = false) // Prioritize cache first
        // }
    }

    private fun setupRecyclerView() {
        templeAdapter = TempleAdapter() // Initialize your adapter
        binding.placesRecyclerView.apply {
            adapter = templeAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}