package net.dacworld.android.holyplacesofthelord.ui.places

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Keep this
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.MyApplication // Import MyApplication
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.data.DataViewModelFactory // Import your factory
import net.dacworld.android.holyplacesofthelord.databinding.FragmentPlacesBinding
import kotlinx.coroutines.flow.combine

class PlacesFragment : Fragment() {

    private var _binding: FragmentPlacesBinding? = null
    private val binding get() = _binding!!

    // Provide the factory to activityViewModels
    private val dataViewModel: DataViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication
        DataViewModelFactory(application.templeDao, application.userPreferencesManager)
    }

    private lateinit var templeAdapter: TempleAdapter

    // ... rest of your PlacesFragment.kt code remains the same ...
    // onCreateView, onViewCreated, setupRecyclerView, onDestroyView
    // The observers inside onViewCreated should work as before.
    // Make sure TempleAdapter is imported or defined.

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    // In PlacesFragment.kt

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Combine isLoading and allTemples to make decisions
                dataViewModel.isLoading.combine(dataViewModel.allTemples) { isLoading, temples ->
                    Pair(isLoading, temples) // Emit a pair of the latest values
                }.collect { (isLoading, temples) ->
                    Log.d("PlacesFragment", "Combined state: isLoading=$isLoading, templesCount=${temples.size}")

                    binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

                    if (isLoading) {
                        binding.placesRecyclerView.visibility = View.GONE
                        binding.emptyViewTextView.visibility = View.GONE
                        Log.d("PlacesFragment", "LOADING: RV GONE, EmptyView GONE")
                    } else {
                        templeAdapter.submitList(temples) // Submit list when not loading
                        if (temples.isEmpty()) {
                            binding.placesRecyclerView.visibility = View.GONE
                            binding.emptyViewTextView.visibility = View.VISIBLE
                            Log.d("PlacesFragment", "NOT LOADING - EMPTY: RV GONE, EmptyView VISIBLE")
                        } else {
                            binding.placesRecyclerView.visibility = View.VISIBLE
                            binding.emptyViewTextView.visibility = View.GONE
                            Log.d("PlacesFragment", "NOT LOADING - DATA: RV VISIBLE, EmptyView GONE")
                        }
                    }
                }
            }
        }

        // Keep SnackBar observer separate if needed
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataViewModel.lastUpdateMessage.collect { message ->
                    if (!message.isNullOrEmpty()) {
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        Log.i("PlacesFragment", "Update Message: $message")
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        Log.d("PlacesFragment", "setupRecyclerView called")
        templeAdapter = TempleAdapter() // Initialize your adapter
        binding.placesRecyclerView.apply {
            adapter = templeAdapter
            layoutManager = LinearLayoutManager(context)
            Log.d("PlacesFragment", "RecyclerView adapter and layoutManager set.")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.placesRecyclerView.adapter = null // Recommended to clear adapter
        _binding = null
    }
}