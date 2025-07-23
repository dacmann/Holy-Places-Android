package net.dacworld.android.holyplacesofthelord.ui.places

import android.Manifest // <<< ADD
import android.annotation.SuppressLint // <<< ADD if not present
import android.content.pm.PackageManager // <<< ADD
import android.location.Location // <<< ADD
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
import com.google.android.gms.location.FusedLocationProviderClient // <<< ADD
import com.google.android.gms.location.LocationServices // <<< ADD
import com.google.android.gms.location.Priority // <<< ADD
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
import android.widget.Toast
import net.dacworld.android.holyplacesofthelord.model.PlaceFilter
import net.dacworld.android.holyplacesofthelord.model.PlaceSort
import net.dacworld.android.holyplacesofthelord.ui.SharedOptionsViewModel
import net.dacworld.android.holyplacesofthelord.ui.SharedOptionsViewModelFactory
import net.dacworld.android.holyplacesofthelord.data.dataStore

class PlacesFragment : Fragment() {

    private var _binding: FragmentPlacesBinding? = null
    private val binding get() = _binding!!

    // Provide the factory to activityViewModels
    private val dataViewModel: DataViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication
        DataViewModelFactory(application,application.templeDao, application.userPreferencesManager)
    }
    private val sharedOptionsViewModel: SharedOptionsViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication // Get UPM from Application
        SharedOptionsViewModelFactory(
            dataViewModel,
            application.userPreferencesManager // Pass UserPreferencesManager instance
        )
    }

    // ViewModel for Toolbar communication (title, count, search query)
    private val sharedToolbarViewModel: SharedToolbarViewModel by activityViewModels()

    // Get the NavigationViewModel, scoped to the Activity
    private val navigationViewModel: NavigationViewModel by activityViewModels()

    private lateinit var templeAdapter: TempleAdapter

    // Keep track of active dialogs to prevent overlap if needed, though clearing the Flow source is primary
    private var isDialogShowing = false

    // --- NEW: For passive location check ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var initialPassiveLocationCheckDone = false
    // --- END NEW ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PlacesFragment", "LIFECYCLE: onCreate")
        // --- NEW: Initialize FusedLocationProviderClient ---
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        // --- END NEW ---
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
        // --- NEW: Reset passive location check flag ---
        initialPassiveLocationCheckDone = false
        // --- END NEW ---
        // --- Call methods to setup Toolbar and SearchView ---
        setupToolbar()
        setupSearchViewListeners()
        setupRecyclerView()
        Log.d("PlacesFragment", "OBSERVER_SETUP: Starting main UI content observer setup (combine).") // <<<

        // --- SETUP FOR THE NEW TEXTVIEW OPTIONS "BUTTON" ---
        binding.textViewOptions.setOnClickListener { // <<--- IMPORTANT: Use the new ID
            Log.d("PlacesFragment", "Options TextView (styled as button) clicked!")
            findNavController().navigate(R.id.action_placesFragment_to_optionsFragment)
        }

        // Main observer for UI content (temples, loading state, search filtering)
        // In PlacesFragment.kt - modify the main observer
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    dataViewModel.isLoading,
                    sharedOptionsViewModel.uiState.map { it.displayedTemples }.distinctUntilChanged(),
                    sharedOptionsViewModel.uiState.map { it.currentSort }.distinctUntilChanged(),     // <<< ADD THIS
                    sharedOptionsViewModel.uiState.map { it.currentFilter }.distinctUntilChanged(),   // <<< ADD THIS
                    sharedToolbarViewModel.uiState.map { it.searchQuery }.distinctUntilChanged()
                ) { isLoading, templesFromOptionsVM, currentSort, currentFilter, searchQuery ->        // <<< RECEIVE THEM HERE
                    Log.d("PlacesFragment", "Combine Triggered: isLoading=$isLoading, templesCount=${templesFromOptionsVM.size}, sort=$currentSort, filter=$currentFilter, query='$searchQuery'")

                    val searchFilteredTemples = if (searchQuery.isBlank()) {
                        templesFromOptionsVM
                    } else {
                        templesFromOptionsVM.filter { temple ->
                            (temple.name.contains(searchQuery, ignoreCase = true)) ||
                                    (temple.snippet.contains(searchQuery, ignoreCase = true)) ||
                                    (temple.cityState.contains(searchQuery, ignoreCase = true))
                        }
                    }
                    Log.i("PlacesFragment_COMBINE", "SEARCH_FILTERED_RESULT: count=${searchFilteredTemples.size}")

                    val currentScreenTitle = if (searchQuery.isBlank()) {
                        //currentFilter.displayName // Now you can use the 'currentFilter' parameter
                        getDisplayTitleForFilter(currentFilter, resources)
                    } else {
                        getString(R.string.search_results_title)
                    }

                    // Determine subtitle based on currentSort
                    val sortSubtitle = getSortOrderLabel(currentSort) // Now 'currentSort' parameter is used

                    // Update sharedToolbarViewModel
                    sharedToolbarViewModel.updateToolbarInfo(
                        title = currentScreenTitle,
                        count = searchFilteredTemples.size,
                        subtitle = sortSubtitle,
                        currentSearchQuery = searchQuery // Pass the current search query
                    )

                    // Pass isLoading, the searchFilteredTemples, and the original searchQuery
                    Triple(isLoading, searchFilteredTemples, searchQuery) // Note: isLoading might need rethinking if sharedOptionsViewModel also handles it
                }.collectLatest { (isLoading, finalTemplesToShow, originalSearchQuery) ->
                    Log.w("PlacesFragment_COLLECT", "RECEIVED_FOR_UI: listCount=${finalTemplesToShow.size}, isLoading=$isLoading, searchQuery='$originalSearchQuery'")

                    binding.progressBar.visibility = if (isLoading && templeAdapter.itemCount == 0) View.VISIBLE else View.GONE

                    // Submit the list
                    templeAdapter.submitList(finalTemplesToShow.toList()) {
                        // This callback is executed when the PagedList (if you were using Paging)
                        // has completed differencing and the UI has been updated.
                        // For ListAdapter, this lambda is invoked after the diffing is complete and
                        // the list is displayed. This is a good place to scroll.
                        if (finalTemplesToShow.isNotEmpty()) { // Only scroll if there are items
                            binding.placesRecyclerView.scrollToPosition(0)
                            Log.d("PlacesFragment_Scroll", "Scrolled to top after list submission (via submitList callback).")
                        }
                    }

                    if (finalTemplesToShow.isEmpty() && !isLoading) {
                        binding.placesRecyclerView.visibility = View.GONE
                        binding.emptyViewTextView.visibility = View.VISIBLE
                        binding.emptyViewTextView.text = if (originalSearchQuery.isNotBlank()) {
                            "No places match your search."
                        } else if (sharedOptionsViewModel.uiState.value.currentFilter != PlaceFilter.HOLY_PLACES) { // Example: be more specific based on filter
                            "No places match the current filter '${sharedOptionsViewModel.uiState.value.currentFilter.displayName}'."
                        } else {
                            "No places available."
                        }
                    } else if (finalTemplesToShow.isNotEmpty()) {
                        binding.placesRecyclerView.visibility = View.VISIBLE
                        binding.emptyViewTextView.visibility = View.GONE
                    } else if (isLoading && finalTemplesToShow.isEmpty()) {
                        binding.placesRecyclerView.visibility = View.GONE
                        binding.emptyViewTextView.visibility = View.GONE
                    }
                }
            }
        }

        // --- NEW: Observer for Passive Location Check ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe changes specifically relevant to this logic
                sharedOptionsViewModel.uiState
                    .map { Triple(it.currentSort, it.currentDeviceLocation, it.hasLocationPermission) } // Assuming hasLocationPermission is in your OptionsUiState
                    .distinctUntilChanged()
                    .collect { (currentSort, currentDeviceLocation, _) -> // hasLocationPermission from VM is not directly used here, we check fresh
                        Log.d("PlacesFragment_PassiveObs", "Passive Check State: Sort=${currentSort.displayName}, VM.LocationSet=${currentDeviceLocation != null}, CheckDone=$initialPassiveLocationCheckDone")

                        if (currentSort == PlaceSort.NEAREST &&
                            currentDeviceLocation == null &&
                            !initialPassiveLocationCheckDone) {

                            initialPassiveLocationCheckDone = true // Mark that we're attempting this check once per fragment instance
                            Log.i("PlacesFragment_PassiveLogic", "NEAREST sort is active, location missing in VM. Checking permissions passively.")

                            if (hasLocationPermission()) {
                                Log.i("PlacesFragment_PassiveLogic", "Location permission IS ALREADY GRANTED. Fetching location for NEAREST sort.")
                                fetchLocationForNearestSortPassively() // New method name for clarity
                            } else {
                                Log.i("PlacesFragment_PassiveLogic", "Location permission NOT granted. ViewModel will use fallback sort.")
                                // No action needed here; ViewModel should already be providing a fallback-sorted list.
                            }
                        }
                    }
            }
        }
        // --- END NEW ---

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
        // Observer for SharedToolbarViewModel's UI state AND SharedOptionsViewModel's currentFilter (for color)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Combine toolbarState with the currentFilter to get color information
                sharedToolbarViewModel.uiState.combine(sharedOptionsViewModel.uiState.map { it.currentFilter }.distinctUntilChanged()) { toolbarState, currentFilter ->
                    Pair(toolbarState, currentFilter) // Pass them together
                }.collectLatest { (toolbarState, currentFilter) -> // Destructure the pair
                    val titleTextView = binding.placesToolbarTitleCentered ?: return@collectLatest

                    // --- APPLYING COLOR TO TITLE BASED ON currentFilter.customColorRes ---
                    if (toolbarState.searchQuery.isBlank()) { // Only apply custom color if not searching
                        val colorRes = currentFilter.customColorRes
                        if (colorRes != null) {
                            titleTextView.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
                        } else {
                            // Fallback to a default theme color if customColorRes is null for the current filter
                            val typedValue = android.util.TypedValue()
                            requireContext().theme.resolveAttribute(R.attr.appBarTextColor, typedValue, true) // Or your default title color attribute
                            if (typedValue.resourceId != 0) {
                                titleTextView.setTextColor(ContextCompat.getColor(requireContext(), typedValue.resourceId))
                            } else {
                                titleTextView.setTextColor(typedValue.data)
                            }
                        }
                    } else {
                        // Default color for search results title (e.g., from theme attribute)
                        val typedValue = android.util.TypedValue()
                        requireContext().theme.resolveAttribute(R.attr.appBarTextColor, typedValue, true) // Or your default title color attribute
                        if (typedValue.resourceId != 0) {
                            titleTextView.setTextColor(ContextCompat.getColor(requireContext(), typedValue.resourceId))
                        } else {
                            titleTextView.setTextColor(typedValue.data)
                        }
                    }
                    // --- END APPLYING COLOR ---

                    // Update the centered title text
                    val titleText = if (toolbarState.searchQuery.isBlank()) {
                        getString(R.string.toolbar_title_format, toolbarState.title, toolbarState.count)
                    } else {
                        getString(R.string.toolbar_search_results_format, toolbarState.count)
                    }
                    titleTextView.text = titleText

                    // Update the subtitle view
                    if (toolbarState.subtitle.isNotBlank() && toolbarState.searchQuery.isBlank()) {
                        binding.placesToolbarSubtitle?.text = toolbarState.subtitle
                        binding.placesToolbarSubtitle?.visibility = View.VISIBLE
                    } else {
                        binding.placesToolbarSubtitle?.visibility = View.GONE
                    }

                    // Update SearchView query if necessary
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

    // --- NEW: Add this helper function within PlacesFragment ---
    private fun getSortOrderLabel(sortOrder: PlaceSort): String {
        return when (sortOrder) {
            PlaceSort.ALPHABETICAL -> getString(R.string.sort_label_alphabetical)
            PlaceSort.NEAREST -> getString(R.string.sort_label_nearest)
            PlaceSort.COUNTRY -> getString(R.string.sort_label_country) // Assuming COUNTRY_THEN_NAME maps to "By Country"
            PlaceSort.DEDICATION_DATE -> getString(R.string.sort_label_dedication_date)
            PlaceSort.SIZE -> getString(R.string.sort_label_size)
            PlaceSort.ANNOUNCED_DATE -> getString(R.string.sort_label_announced_date)
            // Add other cases if your PlaceSort enum has more options
            else -> {
                Log.w("PlacesFragment", "Unknown sort order encountered: $sortOrder")
                getString(R.string.sort_label_unknown) // Fallback for any unhandled or new sort orders
            }
        }
    }

    // Helper function to get the display title (could be in PlacesFragment or a utility file)
    private fun getDisplayTitleForFilter(filter: PlaceFilter, resources: android.content.res.Resources): String {
        return when (filter) {
            PlaceFilter.TEMPLES_UNDER_CONSTRUCTION -> "Construction" // Shorter title
            PlaceFilter.ANNOUNCED_TEMPLES -> "Announced"       // Shorter title
            // Add other specific short titles if needed
            // PlaceFilter.OPERATING_TEMPLES -> "Operating" // Example
            else -> filter.displayName // Default to the existing displayName for other filters
        }
    }

    // --- NEW: Helper method to check for location permission ---
    private fun hasLocationPermission(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        Log.d("PlacesFragment_PermCheck", "Passive Check - FineGranted: $fineLocationGranted, CoarseGranted: $coarseLocationGranted")
        return fineLocationGranted || coarseLocationGranted
    }
    // --- END NEW ---
    // --- NEW: Helper method to fetch location if permission is already granted ---
    @SuppressLint("MissingPermission") // We call this only after hasLocationPermission() returns true
    private fun fetchLocationForNearestSortPassively() {
        Log.d("PlacesFragment_FetchLocP", "Attempting to fetch current location (passively).")
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.i("PlacesFragment_FetchLocP", "Location fetched successfully (passively): ${location.latitude}, ${location.longitude}")
                    sharedOptionsViewModel.setDeviceLocation(location)
                    // The sharedOptionsViewModel.setDeviceLocation call should trigger
                    // its internal combine logic to re-sort the list if currentSort is NEAREST.
                } else {
                    Log.w("PlacesFragment_FetchLocP", "Fetched location is null (passively). ViewModel will use fallback sort.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("PlacesFragment_FetchLocP", "Failed to fetch location (passively).", e)
                // ViewModel will use fallback sort.
            }
    }
    // --- END NEW ---
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