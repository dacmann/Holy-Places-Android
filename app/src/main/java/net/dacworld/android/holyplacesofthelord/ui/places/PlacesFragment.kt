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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomnavigation.BottomNavigationView
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
import android.os.Parcelable
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
import androidx.room.Update
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
        DataViewModelFactory(application,application.templeDao, application.visitDao,application.userPreferencesManager)
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

    private lateinit var placeAdapter: PlaceDisplayAdapter

    // Keep track of active dialogs to prevent overlap if needed, though clearing the Flow source is primary
    private var isDialogShowing = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var initialPassiveLocationCheckDone = false

    private var savedRecyclerLayoutState: Parcelable? = null // For LayoutManager state

    // Add a flag to indicate if a scroll restoration is pending after returning to the fragment
    private var pendingScrollRestore = false

    private var previousSort: PlaceSort? = null
    private var previousFilter: PlaceFilter? = null
    private var isInitialLoad = true // To prevent scrolling to top on the very first load

    private var stableRestingBottomPadding: Int? = null
    private var isInitialInsetApplication = true // Helper to capture initial stable padding

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
        // The logic to set pendingScrollRestore is now in onViewCreated.
        Log.d("PlacesFragment_Scroll", "onResume: Current state: pendingScrollRestore=$pendingScrollRestore, savedStateIsNull=${savedRecyclerLayoutState == null}")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("PlacesFragment", "LIFECYCLE: onViewCreated")
        initialPassiveLocationCheckDone = false
        isInitialLoad = true
        if (savedRecyclerLayoutState != null) {
            pendingScrollRestore = true
            isInitialLoad = false
            Log.d("PlacesFragment_Scroll", "onViewCreated: Found savedRecyclerLayoutState. Set pendingScrollRestore = true.")
        } else {
            // Ensure it's false if no state; it should be initialized to false as a class member.
            pendingScrollRestore = false
            Log.d("PlacesFragment_Scroll", "onViewCreated: No savedRecyclerLayoutState or it was null. pendingScrollRestore is now $pendingScrollRestore.")
        }

        setupToolbar()
        setupSearchViewListeners()

        placeAdapter = PlaceDisplayAdapter { temple -> // Or templeAdapter if that's the variable name
            // Your existing click logic
            Log.d("PlacesFragment", "Temple clicked: ${temple.name}, ID: ${temple.id}")
            navigationViewModel.requestNavigationToPlaceDetail(temple.id) // Example from your code
        }
        binding.placesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = placeAdapter // Assign the correct adapter instance
            // Add DividerItemDecoration if needed, but be mindful of headers.
            // You might want a custom decoration that skips drawing dividers for header items.
            if (itemDecorationCount == 0) { // Add decoration only once
                addItemDecoration(DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL))
            }
        }

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
                    // IMPORTANT CHANGE HERE: Use displayedListItems from SharedOptionsViewModel's UiState
                    sharedOptionsViewModel.uiState.map { it.displayedListItems }.distinctUntilChanged(),
                    sharedOptionsViewModel.uiState.map { it.currentSort }.distinctUntilChanged(),
                    sharedOptionsViewModel.uiState.map { it.currentFilter }.distinctUntilChanged(),
                    sharedToolbarViewModel.uiState.map { it.searchQuery }.distinctUntilChanged()
                ) { isLoading,                                 // Boolean
                    displayItemsFromOptionsVM,               // THIS IS NOW List<DisplayListItem>
                    currentSort,                             // PlaceSort
                    currentFilter,                           // PlaceFilter
                    searchQuery ->                          // String

                    Log.d("PlacesFragment_Scroll", "Combine triggered. PrevSort: $previousSort, PrevFilter: $previousFilter, isInitialLoad: $isInitialLoad, query: '$searchQuery'")
                    Log.d("PlacesFragment_Combine", "Combine: isLoading=$isLoading, displayItemsCount=${displayItemsFromOptionsVM.size}, sort=$currentSort, filter=$currentFilter, query='$searchQuery'")

                    val searchFilteredDisplayItems = if (searchQuery.isBlank()) {
                        displayItemsFromOptionsVM
                    } else {
                        val filteredItems = mutableListOf<DisplayListItem>()
                        var currentHeader: DisplayListItem.HeaderItem? = null
                        val itemsUnderCurrentHeader = mutableListOf<DisplayListItem.TempleRowItem>()

                        for (item in displayItemsFromOptionsVM) {
                            if (item is DisplayListItem.HeaderItem) {
                                if (currentHeader != null && itemsUnderCurrentHeader.isNotEmpty()) {
                                    filteredItems.add(currentHeader.copy(count = itemsUnderCurrentHeader.size)) // Update count
                                    filteredItems.addAll(itemsUnderCurrentHeader)
                                }
                                currentHeader = item // Store the new header
                                itemsUnderCurrentHeader.clear()
                            } else if (item is DisplayListItem.TempleRowItem) {
                                if (item.temple.name.contains(searchQuery, ignoreCase = true) ||
                                    item.temple.snippet.contains(searchQuery, ignoreCase = true) ||
                                    item.temple.cityState.contains(searchQuery, ignoreCase = true)
                                ) {
                                    itemsUnderCurrentHeader.add(item)
                                }
                            }
                        }
                        // Add any remaining items from the last header group
                        if (currentHeader != null && itemsUnderCurrentHeader.isNotEmpty()) {
                            filteredItems.add(currentHeader.copy(count = itemsUnderCurrentHeader.size))
                            filteredItems.addAll(itemsUnderCurrentHeader)
                        }
                        // If no headers were involved (e.g. NEAREST sort), simple filter:
                        if (displayItemsFromOptionsVM.all { it is DisplayListItem.TempleRowItem } && filteredItems.isEmpty() && displayItemsFromOptionsVM.isNotEmpty()){
                            displayItemsFromOptionsVM.filter { listItem ->
                                (listItem as? DisplayListItem.TempleRowItem)?.temple?.let { temple ->
                                    temple.name.contains(searchQuery, ignoreCase = true) ||
                                            temple.snippet.contains(searchQuery, ignoreCase = true) ||
                                            temple.cityState.contains(searchQuery, ignoreCase = true)
                                } ?: false
                            }
                        } else {
                            filteredItems
                        }
                    }
                    Log.i("PlacesFragment_Combine", "SEARCH_FILTERED_DISPLAY_ITEMS: count=${searchFilteredDisplayItems.size}")

                    // Count for the toolbar should now be the number of actual temple items, not total display items
                    val templeItemCount = searchFilteredDisplayItems.count { it is DisplayListItem.TempleRowItem }
                    val currentScreenTitle = getDisplayTitleForFilter(currentFilter, resources)
                    val sortSubtitle = getSortOrderLabel(currentSort)

                    sharedToolbarViewModel.updateToolbarInfo(
                        title = currentScreenTitle,
                        count = templeItemCount, // Use temple item count
                        subtitle = sortSubtitle,
                        currentSearchQuery = searchQuery
                    )

                    // Pass isLoading, the searchFilteredDisplayItems, and the original searchQuery
                    object {
                        val isLoadingVal = isLoading
                        val itemsVal = searchFilteredDisplayItems // Assuming this is calculated in your combine block
                        val queryVal = searchQuery
                        val sortVal = currentSort     // Add currentSort from combine's scope
                        val filterVal = currentFilter   // Add currentFilter from combine's scope
                    }
                    // --- END CHANGE 1 ---

                }.collectLatest { collectedData -> // finalDisplayItemsToShow is List<DisplayListItem>
                    // New: Unpack the properties from 'collectedData'
                    val isLoading = collectedData.isLoadingVal
                    val finalDisplayItemsToShow = collectedData.itemsVal
                    val originalSearchQuery = collectedData.queryVal
                    val currentSort = collectedData.sortVal     // <<< Now available
                    val currentFilter = collectedData.filterVal
                    Log.w("PlacesFragment_Collect", "RECEIVED_FOR_UI: listCount=${finalDisplayItemsToShow.size}, isLoading=$isLoading, searchQuery='$originalSearchQuery'")

                    // ProgressBar logic: Show if loading AND there are no items yet (adapter might be empty initially)
                    binding.progressBar.visibility = if (isLoading && placeAdapter.itemCount == 0 && finalDisplayItemsToShow.isEmpty()) View.VISIBLE else View.GONE

                    placeAdapter.submitList(finalDisplayItemsToShow.toList()) {
                        Log.d("PlacesFragment_Scroll", "submitList START_CALLBACK: pendingScrollRestore=$pendingScrollRestore, itemCount=${placeAdapter.itemCount}, listToSubmitSize=${finalDisplayItemsToShow.size}")
                        Log.d("PlacesFragment_Scroll", "Callback values: currentSort=$currentSort, currentFilter=$currentFilter, prevSort=$previousSort, prevFilter=$previousFilter, isInitialLoad=$isInitialLoad, query='$originalSearchQuery'")

                        val nearestDataRefreshed = sharedOptionsViewModel.uiState.value.nearestSortDataRefreshed
                        Log.d("PlacesFragment_Scroll", "NEAREST_DATA_REFRESHED_FLAG: $nearestDataRefreshed, IS_INITIAL_LOAD: $isInitialLoad")

                        val sortDidChange = previousSort != null && previousSort != currentSort
                        val filterDidChange = previousFilter != null && previousFilter != currentFilter

                        if (pendingScrollRestore && savedRecyclerLayoutState != null && placeAdapter.itemCount > 0 && finalDisplayItemsToShow.isNotEmpty()) {
                            Log.d("PlacesFragment_Scroll", "submitList: ATTEMPTING RESTORE.")
                            binding.placesRecyclerView.layoutManager?.onRestoreInstanceState(savedRecyclerLayoutState)
                            Log.d("PlacesFragment_Scroll", "Scroll state restored. Item count: ${placeAdapter.itemCount}")
                            savedRecyclerLayoutState = null
                            pendingScrollRestore = false
                            isInitialLoad = false // If we restored, subsequent emissions in this lifecycle aren't "initial" for scrolling
                        } else if (isInitialLoad && !pendingScrollRestore) {
                            // Handles the very first load/data emission OR when view is recreated and not restoring.
                            if (finalDisplayItemsToShow.isNotEmpty()) {
                                binding.placesRecyclerView.scrollToPosition(0)
                                Log.d("PlacesFragment_Scroll", "submitList: Scrolled to top on INITIAL display (isInitialLoad=true).")
                            }
                        } else if (nearestDataRefreshed && !isInitialLoad) {
                            if (placeAdapter.itemCount > 0) {
                                binding.placesRecyclerView.scrollToPosition(0)
                                Log.d("PlacesFragment_Scroll", "submitList: Scrolled to top because NEAREST sort data was refreshed.")
                            }
                            sharedOptionsViewModel.acknowledgeNearestSortDataRefreshed() // <<< --- ADD THIS LINE ---
                            pendingScrollRestore = false // Reset, as list content has significantly changed
                            savedRecyclerLayoutState = null // Invalidate saved state
                        }else if ((sortDidChange || filterDidChange) /* && !isInitialLoad can be implied if isInitialLoad block is first */ ) {
                            // Handles sort/filter changes AFTER the initial load has been processed.
                            if (finalDisplayItemsToShow.isNotEmpty()) {
                                binding.placesRecyclerView.scrollToPosition(0)
                                Log.d("PlacesFragment_Scroll", "submitList: Scrolled to top due to SUBSEQUENT sort/filter change.")
                            }
                            // If sort/filter changed, any pending scroll from a previous state is likely irrelevant
                            pendingScrollRestore = false
                            savedRecyclerLayoutState = null
                        } else if (pendingScrollRestore) {
                            // Catch-all for pendingRestore if list became empty or other conditions weren't met
                            Log.d("PlacesFragment_Scroll", "Scroll restore was pending but other conditions not met. Resetting flags.")
                            if (finalDisplayItemsToShow.isEmpty()) {
                                binding.placesRecyclerView.scrollToPosition(0) // Scroll to top if list became empty
                                Log.d("PlacesFragment_Scroll", "Scrolled to top as list became empty during pending restore.")
                            }
                            savedRecyclerLayoutState = null
                            pendingScrollRestore = false
                            isInitialLoad = false // Also mark as not initial if we were trying to restore
                        }
                        // --- MODIFIED LOGIC END ---

                        // --- YOUR EXISTING LOGIC FOR UPDATING isInitialLoad, previousSort, previousFilter ---
                        // This remains from your code. It's important.
                        if (isInitialLoad && finalDisplayItemsToShow.isNotEmpty()) { // This ensures isInitialLoad is set to false after the first populated list
                            isInitialLoad = false
                            Log.d("PlacesFragment_Scroll", "Marked isInitialLoad = false as list is now populated (original logic).")
                        }
                        previousSort = currentSort
                        previousFilter = currentFilter
                        // --- END OF YOUR EXISTING LOGIC ---

                        Log.d("PlacesFragment_Scroll", "submitList END_CALLBACK: pendingScrollRestore=$pendingScrollRestore, savedStateIsNull=${savedRecyclerLayoutState == null}, isInitialLoad=$isInitialLoad")
                    }


                    val templeItemCountInFinalList = finalDisplayItemsToShow.count { it is DisplayListItem.TempleRowItem }

                    if (templeItemCountInFinalList == 0 && !isLoading) {
                        binding.placesRecyclerView.visibility = View.GONE
                        binding.emptyViewTextView.visibility = View.VISIBLE
                        binding.emptyViewTextView.text = if (originalSearchQuery.isNotBlank()) {
                            "No places match your search."
                        } else { // Using currentFilter from uiState as it's more robust
                            val currentFilterFromState = sharedOptionsViewModel.uiState.value.currentFilter
                            "No places match the current filter '${getDisplayTitleForFilter(currentFilterFromState, resources)}'."
                        }
                    } else if (templeItemCountInFinalList > 0) {
                        binding.placesRecyclerView.visibility = View.VISIBLE
                        binding.emptyViewTextView.visibility = View.GONE
                    } else if (isLoading && templeItemCountInFinalList == 0) {
                        // Still loading and list is empty (or only headers)
                        binding.placesRecyclerView.visibility = View.GONE
                        binding.emptyViewTextView.visibility = View.GONE // Hide empty text while loading
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
                    // --- END APPLYING COLOR ---

                    // Update the centered title text
                    titleTextView.text = getString(R.string.toolbar_title_format, toolbarState.title, toolbarState.count)

                    // Update the subtitle view
                    if (toolbarState.subtitle.isNotBlank()) {
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
// <<<<<<<<<<<< START: ADD THIS CODE AT THE VERY END OF onViewCreated >>>>>>>>>>>>>>>>
        Log.d("PlacesFragmentInsets", "Setting up inset handling for RecyclerView at the end of onViewCreated.")
        val recyclerViewToPad = binding.placesRecyclerView

        ViewCompat.setOnApplyWindowInsetsListener(recyclerViewToPad) { view, windowInsets ->
            Log.d("PlacesFragmentInsets", "--- Inset Listener Triggered ---") // Marker for when it's called

            val systemNavigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            // val systemBarsForSides = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()) // Optional
            Log.d("PlacesFragmentInsets", "Reported SystemNav.bottom: ${systemNavigationBars.bottom}")
            Log.d("PlacesFragmentInsets", "Reported IME.bottom: ${imeInsets.bottom}")

            var effectiveNavHeight = systemNavigationBars.bottom
            // Minimal logging for brevity in this specific example, you can add more if needed
            Log.d("PlacesFragmentInsets", "Initial systemNavigationBars.bottom: $effectiveNavHeight")

            val activityRootView = requireActivity().window.decorView
            val appBottomNavView = activityRootView.findViewById<BottomNavigationView>(net.dacworld.android.holyplacesofthelord.R.id.main_bottom_navigation) // Ensure this ID is correct

            if (appBottomNavView != null && appBottomNavView.visibility == View.VISIBLE) {
                Log.d("PlacesFragmentInsets", "App's BottomNavView found: Height=${appBottomNavView.height}, Visible=true")
                if (appBottomNavView.height > 0) {
                    effectiveNavHeight = appBottomNavView.height
                    Log.d("PlacesFragmentInsets", "Using App's BottomNavView height ($effectiveNavHeight) as effectiveNavHeight")
                } else {
                    Log.d("PlacesFragmentInsets", "Not using AppBottomNavView height. Current effectiveNavHeight (from system): $effectiveNavHeight")
                }
            } else {
                Log.d("PlacesFragmentInsets", "App's BottomNavView not found or not visible.")
            }

            // Capture the stable resting bottom padding ONCE when IME is not visible
            // and we haven't captured it yet, or if we want to allow it to update if nav bar itself changes (less common)
            if (imeInsets.bottom == 0 && (stableRestingBottomPadding == null || isInitialInsetApplication)) {
                stableRestingBottomPadding = effectiveNavHeight
                isInitialInsetApplication = false // Set to false after first capture
                Log.d("PlacesFragmentInsets", "CAPTURED/UPDATED Stable Resting Bottom Padding: $stableRestingBottomPadding")
            }

            //val desiredBottomPadding = kotlin.math.max(effectiveNavHeight, imeInsets.bottom)
            val desiredBottomPadding: Int
            if (stableRestingBottomPadding != null) {
                if (imeInsets.bottom > 0) {
                    // Keyboard is visible, use the greater of its height or our stable resting padding
                    // (Usually IME is taller, but this handles cases where resting padding might be unusually large)
                    desiredBottomPadding = kotlin.math.max(stableRestingBottomPadding!!, imeInsets.bottom)
                    Log.d("PlacesFragmentInsets", "Keyboard visible. DesiredPadding = max(stable: $stableRestingBottomPadding, IME: ${imeInsets.bottom}) = $desiredBottomPadding")
                } else {
                    // Keyboard is not visible, revert to the stable resting padding
                    desiredBottomPadding = stableRestingBottomPadding!!
                    Log.d("PlacesFragmentInsets", "Keyboard NOT visible. Reverting to Stable Resting Padding: $desiredBottomPadding")
                }
            } else {
                // Fallback if stableRestingBottomPadding hasn't been captured yet (should be rare after first few calls)
                desiredBottomPadding = kotlin.math.max(effectiveNavHeight, imeInsets.bottom)
                Log.d("PlacesFragmentInsets", "Stable resting padding not yet captured. Using fallback calculation. DesiredPadding = $desiredBottomPadding")
            }
            Log.d("PlacesFragmentInsets", "Final Calculation: effectiveNavHeight=$effectiveNavHeight, imeInsets.bottom=${imeInsets.bottom} => desiredBottomPadding=$desiredBottomPadding")

            view.updatePadding(
                // left = systemBarsForSides.left, // Uncomment for side padding
                // right = systemBarsForSides.right, // Uncomment for side padding
                bottom = desiredBottomPadding
            )
            Log.d("PlacesFragmentInsets", "View's actual paddingBottom after update: ${view.paddingBottom}")
            Log.d("PlacesFragmentInsets", "--- Inset Listener End ---")

            windowInsets
        }

        // Request insets to be applied initially
        if (recyclerViewToPad.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(recyclerViewToPad)
        } else {
            recyclerViewToPad.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
        Log.d("PlacesFragmentInsets", "Finished setting up inset handling for RecyclerView.")
        // <<<<<<<<<<<< END: ADD THIS CODE AT THE VERY END OF onViewCreated >>>>>>>>>>>>>>>>

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
                    sharedOptionsViewModel.deviceLocationUpdated(location, true) // CORRECTED CALL
                } else {
                    Log.w("PlacesFragment_FetchLocP", "Fetched location is null (passively). ViewModel will use fallback sort.")
                    sharedOptionsViewModel.deviceLocationUpdated(null, true) // RECOMMENDED ADDITION
                }
            }
            .addOnFailureListener { e ->
                Log.e("PlacesFragment_FetchLocP", "Failed to fetch location (passively).", e)
                sharedOptionsViewModel.deviceLocationUpdated(null, true) // RECOMMENDED ADDITION
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

    override fun onPause() {
        super.onPause()
        Log.d("PlacesFragment", "LIFECYCLE: onPause")
        // Save RecyclerView layout state
        _binding?.let { binding -> // Check if binding is not null
            binding.placesRecyclerView.layoutManager?.let { layoutManager ->
                savedRecyclerLayoutState = layoutManager.onSaveInstanceState()
                Log.d("PlacesFragment_Scroll", "onPause: Scroll state ${if (savedRecyclerLayoutState != null) "SAVED" else "NOT SAVED (null)"}. PendingRestore was: $pendingScrollRestore")
            }
        }
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