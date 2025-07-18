package net.dacworld.android.holyplacesofthelord.ui.places

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
//import androidx.compose.ui.semantics.dismiss
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Keep this
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
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
import android.graphics.Color
import android.widget.EdgeEffect
import androidx.core.content.ContextCompat // Import ContextCompat for getColor
import androidx.core.text.color
import androidx.navigation.NavController
import androidx.navigation.findNavController
import net.dacworld.android.holyplacesofthelord.R // Import R for R.color.BaptismBlue
import androidx.fragment.app.activityViewModels // For NavigationViewModel
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.ui.NavigationViewModel // Import your ViewModel


class PlacesFragment : Fragment() {

    private var _binding: FragmentPlacesBinding? = null
    private val binding get() = _binding!!

    // Provide the factory to activityViewModels
    private val dataViewModel: DataViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication
        DataViewModelFactory(application,application.templeDao, application.userPreferencesManager)
    }

    // ViewModel for Toolbar communication (title, count, search query)
    private val sharedToolbarViewModel: SharedToolbarViewModel by activityViewModels()

    // Get the NavigationViewModel, scoped to the Activity
    private val navigationViewModel: NavigationViewModel by activityViewModels()

    private lateinit var templeAdapter: TempleAdapter

    // Keep track of active dialogs to prevent overlap if needed, though clearing the Flow source is primary
    private var isDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PlacesFragment", "LIFECYCLE: onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("PlacesFragment", "LIFECYCLE: onCreateView")
        _binding = FragmentPlacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        Log.d("PlacesFragment", "LIFECYCLE: onStart") // Add if not present
    }

    override fun onResume() {
        super.onResume()
        Log.d("PlacesFragment", "LIFECYCLE: onResume") // Add if not present
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("PlacesFragment", "LIFECYCLE: onViewCreated")
        // --- Call methods to setup Toolbar and SearchView ---
        setupToolbar()
        setupSearchViewListeners()
        setupRecyclerView()
        Log.d("PlacesFragment", "OBSERVER_SETUP: Starting main UI content observer setup (combine).") // <<<

        // Main observer for UI content (temples, loading state, search filtering)
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d("PlacesFragment", "OBSERVER_SETUP: Main UI content observer coroutine LAUNCHED.") // <<<

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d("PlacesFragment", "OBSERVER_SETUP: Main UI content observer REPEATING on STARTED.") // <<<
                // Combine relevant flows:
                combine(
                    dataViewModel.isLoading,  // StateFlow<Boolean>
                    dataViewModel.allTemples,   // StateFlow<List<Temple>>
                    sharedToolbarViewModel.uiState.map { it.searchQuery }.distinctUntilChanged() // Flow<String>
                ) { isLoading: Boolean, temples: List<Temple>, searchQuery: String -> // Explicit types HERE
                    Log.d("PlacesFragment", "Combine Triggered: isLoading=$isLoading, templesCount=${temples.size}, searchQuery='$searchQuery', templesInstance=${System.identityHashCode(temples)}")
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
                    Log.i("PlacesFragment_COMBINE", "FILTERED_RESULT: filteredCount=${filteredTemples.size} (ID: ${System.identityHashCode(filteredTemples)})")
                    // --- MODIFIED PART within the combine block ---
                    // Determine the title based on search state
                    val currentScreenTitle = if (searchQuery.isBlank()) {
                        // Assuming you have a string resource like <string name="title_places">Places</string>
                        getString(R.string.tab_label_places) // Or your preferred default title
                    } else {
                        getString(R.string.tab_label_places) // e.g., "Search Results"
                    }
                    // Update the SharedToolbarViewModel with this title and count
                    // This call was already here, ensure it uses the determined title
                    Log.i("PlacesFragment_COMBINE", "FILTERED: filteredCount=${filteredTemples.size} (ID: ${System.identityHashCode(filteredTemples)})")
                    sharedToolbarViewModel.updateToolbarInfo(currentScreenTitle, filteredTemples.size)
                    // --- END MODIFIED PART ---

                    Triple(isLoading, filteredTemples, searchQuery)
                }.collectLatest { (isLoading: Boolean, filteredTemples: List<Temple>, searchQuery: String) -> // Explicit types also good here
                    Log.w("PlacesFragment_COLLECT", "RECEIVED_FOR_UI: listCount=${filteredTemples.size} (ID: ${System.identityHashCode(filteredTemples)}), isLoading=$isLoading, searchQuery='$searchQuery'")
                    val currentFilterName = if (searchQuery.isBlank()) "All Places" else "Search Results"
                    Log.d(
                        "PlacesFragment",
                        "Combined UI State: isLoading=$isLoading, filteredTemplesCount=${filteredTemples.size}, searchQuery='$searchQuery'"
                    )

                    //sharedToolbarViewModel.updateToolbarInfo(currentFilterName, filteredTemples.size)

                    // Simplified progress bar logic - show if loading AND list is currently empty
                    binding.progressBar.visibility = if (isLoading && templeAdapter.itemCount == 0) View.VISIBLE else View.GONE

                    val listToSubmit = filteredTemples.toList() // Your existing .toList() call
                    Log.w("PlacesFragment_SUBMIT", "PREP_FOR_ADAPTER: listToSubmitCount=${listToSubmit.size} (ID: ${System.identityHashCode(listToSubmit)})")
                    Log.w("PlacesFragment_SUBMIT", "CALLING submitList with ${listToSubmit.size} items.")

                    templeAdapter.submitList(listToSubmit)
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
        // --- NEW: Observer for SharedToolbarViewModel's title and initial SearchView query ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedToolbarViewModel.uiState.collectLatest { toolbarState ->
                    // Update the centered title
                    val titleText = if (toolbarState.searchQuery.isBlank()) {
                        // Example: "Places (150)"
                        // Ensure you have R.string.toolbar_title_format = "%1$s (%2$d)"
                        getString(R.string.toolbar_title_format, toolbarState.title, toolbarState.count)
                    } else {
                        // Example: "Search Results (10)"
                        // Ensure you have R.string.toolbar_search_results_format = "Search Results (%1$d)"
                        getString(R.string.toolbar_search_results_format, toolbarState.count)
                    }
                    binding.placesToolbarTitleCentered?.text = titleText // Use safe call as binding might be null during quick exit

                    // Initialize SearchView text if it hasn't been set by user interaction yet
                    // and if it differs from the ViewModel's state (e.g., on configuration change)
                    if (binding.placesSearchView?.query?.toString() != toolbarState.searchQuery) {
                        binding.placesSearchView?.setQuery(toolbarState.searchQuery, false)
                    }
                }
            }
        }

        // --- NEW: Observer for NavigationViewModel to handle navigation to PlaceDetail ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigationViewModel.navigateToPlaceDetail.collectLatest { placeId: String? -> // Added : String?
                    placeId?.let { nonNullPlaceId -> // Using a different name for clarity after null check
                        Log.d("PlacesFragmentNav", "Observed navigation request for place ID: $nonNullPlaceId. Navigating.")
                        try {
                            // Ensure PlacesFragmentDirections is correctly generated and imported
                            val action = PlacesFragmentDirections.actionPlacesFragmentToPlaceDetailFragment(nonNullPlaceId)
                            findNavController().navigate(action)
                        } catch (e: IllegalStateException) { // More specific exception
                            Log.e("PlacesFragmentNav", "Navigation failed (IllegalStateException): ${e.message}. Current destination: ${findNavController().currentDestination?.label}", e)
                        } catch (e: IllegalArgumentException) { // More specific exception
                            Log.e("PlacesFragmentNav", "Navigation failed (IllegalArgumentException): ${e.message}. Check arguments or action ID.", e)
                        } catch (e: Exception) { // Generic fallback
                            Log.e("PlacesFragmentNav", "Navigation failed (Exception): ${e.message}", e)
                        }
                        navigationViewModel.onPlaceDetailNavigated()
                    }
                }
            }
        }

    }

    private fun setupToolbar() {
        // Set the new toolbar as the support action bar
        val appCompatActivity = (activity as? AppCompatActivity)
        appCompatActivity?.setSupportActionBar(binding.placesToolbar)
        // The title is handled by the placesToolbarTitleCentered TextView,
        // which is updated by observing sharedToolbarViewModel.uiState
        appCompatActivity?.supportActionBar?.title = "" // Clear title on the new action bar
        appCompatActivity?.supportActionBar?.subtitle = "" // Clear subtitle too, just in case
    }

    private fun setupSearchViewListeners() {
        binding.placesSearchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                sharedToolbarViewModel.setSearchQuery(query.orEmpty().trim())
                binding.placesSearchView.clearFocus() // Optional: dismiss keyboard
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                sharedToolbarViewModel.setSearchQuery(newText.orEmpty().trim())
                return true
            }
        })
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
        templeAdapter = TempleAdapter { temple ->
            Log.d("PlacesFragmentNav", "Item clicked: ${temple.name}, ID: ${temple.id}. Requesting navigation via ViewModel.")
            navigationViewModel.requestNavigationToPlaceDetail(temple.id) // <<<< CHANGE HERE
        }
        binding.placesRecyclerView.apply {
            Log.d("PlacesFragment", "setupRecyclerView: Configuring RecyclerView.") // <<< ADDED
            adapter = templeAdapter
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null

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

    override fun onPause() { // Added onPause
        super.onPause()
        Log.d("PlacesFragment", "LIFECYCLE: onPause")
    }

    override fun onStop() { // Added onStop
        super.onStop()
        Log.d("PlacesFragment", "LIFECYCLE: onStop")
    }

    override fun onDestroyView() {
        Log.d("PlacesFragment", "LIFECYCLE: onDestroyView") // Added log
        super.onDestroyView()
        binding.placesRecyclerView.adapter = null // Recommended to clear adapter
        _binding = null
        Log.d("PlacesFragment", "LIFECYCLE: _binding and RecyclerView adapter set to null.") // Added log
        isDialogShowing = false // Reset flag
    }
}