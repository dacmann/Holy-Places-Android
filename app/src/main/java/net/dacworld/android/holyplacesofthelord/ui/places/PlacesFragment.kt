package net.dacworld.android.holyplacesofthelord.ui.places

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
//import androidx.compose.ui.semantics.dismiss
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Keep this
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.MyApplication // Import MyApplication
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.data.DataViewModelFactory // Import your factory
import net.dacworld.android.holyplacesofthelord.databinding.FragmentPlacesBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest // For observing search query
import net.dacworld.android.holyplacesofthelord.model.Temple
// Import your SharedToolbarViewModel
import net.dacworld.android.holyplacesofthelord.ui.SharedToolbarViewModel
import net.dacworld.android.holyplacesofthelord.data.UpdateDetails
import kotlin.text.contains
import android.app.AlertDialog // Import AlertDialog for getButton
import androidx.core.content.ContextCompat // Import ContextCompat for getColor
import net.dacworld.android.holyplacesofthelord.R // Import R for R.color.BaptismBlue

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

    // Keep track of active dialogs to prevent overlap if needed, though clearing the Flow source is primary
    private var isDialogShowing = false

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

                    // Simplified progress bar logic - show if loading AND list is currently empty
                    binding.progressBar.visibility = if (isLoading && templeAdapter.itemCount == 0) View.VISIBLE else View.GONE

                    templeAdapter.submitList(filteredTemples.toList())
                    if (filteredTemples.isEmpty() && !isLoading) { // Only show empty view if not loading and list is empty
                        binding.placesRecyclerView.visibility = View.GONE
                        binding.emptyViewTextView.visibility = View.VISIBLE
                        binding.emptyViewTextView.text = if (searchQuery.isNotBlank()) {
                            "No places match your search."
                        } else {
                            "No places available."
                        }
                    } else if (filteredTemples.isNotEmpty()) {
                        binding.placesRecyclerView.visibility = View.VISIBLE
                        binding.emptyViewTextView.visibility = View.GONE
                    } else if (isLoading && filteredTemples.isEmpty()){ // still loading and no data yet
                        binding.placesRecyclerView.visibility = View.GONE
                        binding.emptyViewTextView.visibility = View.GONE // Hide empty text while loading
                    }
                }
            }
        }

        // Observer for Remote XML Update Dialog
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataViewModel.remoteUpdateDetails.collectLatest { details: UpdateDetails? ->
                    details?.let {
                        if (!isDialogShowing) { // Basic concurrency check
                            isDialogShowing = true
                            showUpdateDialog(
                                details = it,
                                onDismiss = {
                                    dataViewModel.remoteUpdateDialogShown()
                                    isDialogShowing = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(details: UpdateDetails, onDismiss: () -> Unit) {
        if (!isAdded || context == null) { // Good practice to check if fragment is still added
            Log.w("PlacesFragment", "Dialog not shown for update, fragment not attached or context null.")
            onDismiss() // Call dismiss to clear flags in ViewModel if necessary
            isDialogShowing = false
            return
        }

        val formattedMessage = details.messages.joinToString(separator = "\n\n")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(details.updateTitle)
            .setMessage(formattedMessage)
            .setCancelable(false)
            // Positive button text is set here, action will be set later
            .setPositiveButton(android.R.string.ok, null) // Set text, listener is overridden by setOnShowListener or set later
            .create() // Create the dialog instance

        // Set the OnShowListener to modify the button color after the dialog is shown
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.BaptismBlue))
        }

        // Set the click listener for the positive button
        // This ensures your onDismiss logic is correctly tied
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok)) { d, _ ->
            d.dismiss() // Dismiss the dialog
            onDismiss()   // Call your original onDismiss logic
        }

        dialog.setOnDismissListener {
            isDialogShowing = false
            // Log.d("PlacesFragment", "Update dialog dismissed.") // Optional: more specific log
        }

        dialog.show()
        Log.d("PlacesFragment", "Showing update dialog: ${details.updateTitle}")
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
            addItemDecoration(dividerItemDecoration)
            // --- Add Divider Item Decoration END ---
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.placesRecyclerView.adapter = null // Recommended to clear adapter
        _binding = null
        isDialogShowing = false // Reset flag
    }
}