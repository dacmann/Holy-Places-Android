package net.dacworld.android.holyplacesofthelord.ui.places

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Keep this
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.MyApplication // Import MyApplication
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.data.DataViewModelFactory // Import your factory
import net.dacworld.android.holyplacesofthelord.databinding.FragmentPlacesBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.recyclerview.widget.DividerItemDecoration
import kotlinx.coroutines.flow.collectLatest // For observing search query
import net.dacworld.android.holyplacesofthelord.model.Temple
// Import your SharedToolbarViewModel
import net.dacworld.android.holyplacesofthelord.ui.SharedToolbarViewModel
import kotlin.text.contains
import kotlin.text.filter
import kotlin.text.isEmpty
import kotlin.text.toList

class PlacesFragment : Fragment() {

    private var _binding: FragmentPlacesBinding? = null
    private val binding get() = _binding!!

    // Provide the factory to activityViewModels
    private val dataViewModel: DataViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication
        DataViewModelFactory(application.templeDao, application.userPreferencesManager)
    }

    // ViewModel for Toolbar communication (title, count, search query)
    private val sharedToolbarViewModel: SharedToolbarViewModel by activityViewModels()

    private lateinit var templeAdapter: TempleAdapter

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

        // Main observer for UI content (temples, loading state, search filtering)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Combine relevant flows:
                combine(
                    dataViewModel.isLoading,  // StateFlow<Boolean>
                    dataViewModel.allTemples,   // StateFlow<List<Temple>>
                    sharedToolbarViewModel.uiState.map { it.searchQuery }.distinctUntilChanged() // Flow<String>
                ) { isLoading: Boolean, temples: List<Temple>, searchQuery: String -> // Explicit types HERE
                    val filteredTemples = if (searchQuery.isBlank()) {
                        temples
                    } else {
                        temples.filter { temple ->
                            // Use properties from Temple.kt
                            (temple.name.contains(searchQuery, ignoreCase = true)) ||
                                    (temple.snippet.contains(searchQuery, ignoreCase = true)) ||
                                    (temple.cityState.contains(searchQuery, ignoreCase = true))
                            // Add more fields as needed for your search logic
                            // If these fields can be null, use safe calls: temple.name?.contains(...) == true
                        }
                    }
                    Triple(isLoading, filteredTemples, searchQuery)
                }.collectLatest { (isLoading: Boolean, filteredTemples: List<Temple>, searchQuery: String) -> // Explicit types also good here
                    val currentFilterName = if (searchQuery.isBlank()) "All Places" else "Search Results"
                    Log.d(
                        "PlacesFragment",
                        "Combined UI State: isLoading=$isLoading, filteredTemplesCount=${filteredTemples.size}, searchQuery='$searchQuery'"
                    )

                    sharedToolbarViewModel.updateToolbarInfo(currentFilterName, filteredTemples.size)

                    binding.progressBar.visibility = if (isLoading && templeAdapter.itemCount == 0 && filteredTemples.isEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                    if (isLoading && templeAdapter.itemCount == 0 && filteredTemples.isEmpty()) {
                        binding.placesRecyclerView.visibility = View.GONE
                        binding.emptyViewTextView.visibility = View.GONE
                        Log.d("PlacesFragment", "INITIAL LOADING: RV GONE, EmptyView GONE")
                    } else {
                        templeAdapter.submitList(filteredTemples.toList())
                        if (filteredTemples.isEmpty()) {
                            binding.placesRecyclerView.visibility = View.GONE
                            binding.emptyViewTextView.visibility = View.VISIBLE
                            binding.emptyViewTextView.text = if (searchQuery.isNotBlank() && !isLoading) {
                                "No places match your search."
                            } else if (!isLoading) {
                                "No places available."
                            } else {
                                ""
                            }
                            Log.d("PlacesFragment", "EMPTY LIST (after submit): RV GONE, EmptyView VISIBLE")
                        } else {
                            binding.placesRecyclerView.visibility = View.VISIBLE
                            binding.emptyViewTextView.visibility = View.GONE
                            Log.d("PlacesFragment", "DATA PRESENT (after submit): RV VISIBLE, EmptyView GONE")
                        }
                    }
                }
            }
        }

        // Observer for SnackBar messages
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataViewModel.lastUpdateMessage.collectLatest { message: String? -> // Explicit type for message
                    if (!message.isNullOrEmpty()) {
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        Log.i("PlacesFragment", "Update Message: $message")
                        dataViewModel.clearLastUpdateMessage()
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
            // --- Add Divider Item Decoration START ---
            val dividerItemDecoration = DividerItemDecoration(
                context, // Use requireContext() if context might be null, but here it should be fine
                (layoutManager as LinearLayoutManager).orientation
            )
            // Optionally, you can set a custom drawable for the divider:
            // ContextCompat.getDrawable(requireContext(), R.drawable.your_custom_divider)?.let {
            //    dividerItemDecoration.setDrawable(it)
            // }
            addItemDecoration(dividerItemDecoration)
            // --- Add Divider Item Decoration END ---
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.placesRecyclerView.adapter = null // Recommended to clear adapter
        _binding = null
    }
}