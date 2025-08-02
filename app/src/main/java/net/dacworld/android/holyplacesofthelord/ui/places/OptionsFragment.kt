// OptionsFragment.kt
package net.dacworld.android.holyplacesofthelord.ui.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity // For toolbar title
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.MyApplication
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.databinding.FragmentOptionsBinding
import net.dacworld.android.holyplacesofthelord.model.PlaceFilter
import net.dacworld.android.holyplacesofthelord.model.PlaceSort
import net.dacworld.android.holyplacesofthelord.ui.SharedOptionsViewModel
import net.dacworld.android.holyplacesofthelord.ui.SharedOptionsViewModelFactory
import net.dacworld.android.holyplacesofthelord.data.dataStore
// Add a constant for the tag key
private const val SPINNER_TAG_PROGRAMMATIC_SELECTION = "programmatic_selection"

class OptionsFragment : Fragment() {

    private var _binding: FragmentOptionsBinding? = null
    private val binding get() = _binding!!

    private val dataViewModel: DataViewModel by activityViewModels()
    private val sharedOptionsViewModel: SharedOptionsViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication // Get UPM from Application
        SharedOptionsViewModelFactory(
            dataViewModel, // Assuming dataViewModel is already available here
            application.userPreferencesManager // Pass UserPreferencesManager instance
        )
    }

    private lateinit var filterAdapter: CustomSpinnerAdapter<PlaceFilter>
    private lateinit var sortAdapter: CustomSpinnerAdapter<PlaceSort>

    private var isInitialFilterSetup = true
    private var isInitialSortSetup = true

    // Simpler state for permission request flow for NEAREST
    private var isAwaitingPermissionForNearestSort = false
    private var sortToRevertToIfNearestFails: PlaceSort? = null // To store the sort before NEAREST was attempted

    // CHANGE 2: Add this flag
    private var initialCheckDoneInOnStart = false

    // --- Location Specific Variables ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // Stores the sort option the user *intended* to select if permission prompt is shown
    private var intendedSortAfterPermission: PlaceSort? = null
    // Stores the sort that was active *before* user selected NEAREST and triggered permission flow
    private var sortBeforeNearestAttempt: PlaceSort? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        Log.d("OptionsFragment_LocPerm", "Permission result: Fine=$fineLocationGranted, Coarse=$coarseLocationGranted, Awaiting=$isAwaitingPermissionForNearestSort")

        if (fineLocationGranted || coarseLocationGranted) {
            if (isAwaitingPermissionForNearestSort) {
                Log.d("OptionsFragment_LocPerm", "Permission GRANTED for NEAREST sort. Fetching location.")
                fetchLocationAndUpdateViewModelForNearest()
            } else {
                sharedOptionsViewModel.deviceLocationUpdated(sharedOptionsViewModel.uiState.value.currentDeviceLocation, true)
                Log.d("OptionsFragment_LocPerm", "Permission GRANTED (not for NEAREST). VM updated with permission status.")
            }
        } else {
            // ***** Permission DENIED *****
            sharedOptionsViewModel.deviceLocationUpdated(null, false)
            if (isAwaitingPermissionForNearestSort) {
                Log.w("OptionsFragment_LocPerm", "Permission DENIED for NEAREST sort. Reverting.")
                Toast.makeText(requireContext(), "Location permission denied. Cannot sort by nearest.", Toast.LENGTH_LONG).show()
                val fallbackSort = sortToRevertToIfNearestFails ?: PlaceSort.ALPHABETICAL // Use the stored fallback
                sharedOptionsViewModel.setSort(fallbackSort)
                // Reset is now handled after fetch/failure or here if permission denied
            } else {
                Log.w("OptionsFragment_LocPerm", "Permission DENIED, not for NEAREST sort.")
            }
        }
        // Always reset after a permission request cycle completes, whether it was for NEAREST or not
        if (isAwaitingPermissionForNearestSort) { // Only reset if the permission request was for the "nearest" flow
            isAwaitingPermissionForNearestSort = false
            sortToRevertToIfNearestFails = null
            Log.d("OptionsFragment_LocPerm", "Reset isAwaiting and sortToRevertToIfNearestFails after permission result.")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOptionsBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOptionsBinding.bind(view)

        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.button_text_options)
        }

        // Reset flags for this view instance
        isInitialFilterSetup = true
        isInitialSortSetup = true
        initialCheckDoneInOnStart = false

        setupSpinners()
        observeViewModel()
        setupDoneButton()

        binding.buttonDoneOptions.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("OptionsFragment_Lifecycle", "onStart called. initialCheckDoneInOnStart: $initialCheckDoneInOnStart")
        if (!initialCheckDoneInOnStart) {
            val currentSortFromVM = sharedOptionsViewModel.uiState.value.currentSort
            val currentLocationFromVM = sharedOptionsViewModel.uiState.value.currentDeviceLocation

            Log.d("OptionsFragment_OnStart", "Checking for NEAREST: CurrentSort=${currentSortFromVM.displayName}, LocationAvailable=${currentLocationFromVM != null}")

            if (currentSortFromVM == PlaceSort.NEAREST && currentLocationFromVM == null) {
                Log.w("!!!!_OptionsFragment_ONSTART_CHECK", "NEAREST sort is active but location is MISSING. Initiating fetch.")

                // Set fallback *before* requesting.
                // It should be a sort that IS NOT NEAREST from the available options, or a hardcoded default.
                sortToRevertToIfNearestFails = sharedOptionsViewModel.uiState.value.availableSortOptions
                    .firstOrNull { it != PlaceSort.NEAREST } ?: PlaceSort.ALPHABETICAL
                Log.d("!!!!_OptionsFragment_ONSTART_CHECK", "Fallback for onStart NEAREST set to: ${sortToRevertToIfNearestFails?.displayName}")

                isAwaitingPermissionForNearestSort = true // Set flag as this check will lead to a permission request/fetch
                requestLocationPermissionOrFetchForNearest()
            }
            initialCheckDoneInOnStart = true
        }
    }

    private fun setupSpinners() {
        // --- Filter Spinner ---

        filterAdapter = CustomSpinnerAdapter(
            requireContext(),
            R.layout.spinner_item_custom, // Layout for selected item
            R.layout.spinner_dropdown_item_custom, // Layout for dropdown items
            PlaceFilter.values().toList(),
            displayMapper = { it.displayName },
            colorMapper = { it.customColorRes } // Pass the color resource from the enum
        )
        binding.spinnerFilter.adapter = filterAdapter

        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedItem = parent?.getItemAtPosition(position)
                val selectedFilter = selectedItem as? PlaceFilter

                Log.d("OptionsFragment_Filter", ">>>> Filter Spinner: onItemSelected ENTERED. Position: $position, Selected: ${selectedFilter?.displayName ?: selectedItem?.toString() ?: "NULL"}. isInitialFilterSetup_WAS: $isInitialFilterSetup.")

                if (isInitialFilterSetup) {
                    isInitialFilterSetup = false
                    Log.d("OptionsFragment_Filter", "Filter Spinner: isInitialFilterSetup was true. Now false. Listener is returning (programmatic/initial setup). <<<<")
                    return
                }

                if (selectedFilter == null) {
                    Log.e("OptionsFragment_Filter", "Filter Spinner: Selected item is null or not PlaceFilter after initial setup check.")
                    return
                }

                // Original Log: Log.d("OptionsFragment", "Filter Spinner selected: ${selectedFilter.displayName}")
                Log.d("OptionsFragment_Filter", "Filter Spinner: User selected: ${selectedFilter.displayName}. Calling VM.setFilter.")
                sharedOptionsViewModel.setFilter(selectedFilter)
                Log.d("OptionsFragment_Filter", "Filter Spinner: onItemSelected EXITED NORMALLY for ${selectedFilter.displayName}. <<<<")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                Log.d("OptionsFragment_Filter", "Filter Spinner: onNothingSelected triggered.")
            }
        }

        // --- Sort Spinner ---
        // Initial sort options will be set by observing ViewModel
        sortAdapter = CustomSpinnerAdapter(
            requireContext(),
            R.layout.spinner_item_custom,
            R.layout.spinner_dropdown_item_custom,
            mutableListOf(), // Initially empty, will be populated by ViewModel
            displayMapper = { it.displayName },
            colorMapper = { _ -> R.color.grey_text }
        )
        binding.spinnerSort.adapter = sortAdapter

        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                //val selectedItem = parent?.getItemAtPosition(position)
                val selectedSort = parent?.getItemAtPosition(position) as? PlaceSort
                if (selectedSort == null) {
                    Log.w("OptionsFragment_Sort", "Sort Spinner: selectedItem is null or not PlaceSort at position $position. This is unexpected.")
                    return
                }

                // More detailed initial log for onItemSelected
                Log.d("OptionsFragment_Sort", ">>>> Sort Spinner: ENTERED onItemSelected. Selected: ${selectedSort.displayName}, Pos: $position. TAG_WAS: ${binding.spinnerSort.tag}, isInitialSortSetup_WAS: $isInitialSortSetup")

                if (binding.spinnerSort.tag == SPINNER_TAG_PROGRAMMATIC_SELECTION) {
                    // Log before clearing tag and changing isInitialSortSetup
                    Log.d("OptionsFragment_Sort", "Sort Spinner: TAGGED path detected. isInitialSortSetup_BEFORE_CHANGE_IN_TAGGED_PATH: $isInitialSortSetup. Clearing tag.")
                    binding.spinnerSort.tag = null // Clear the tag
                    if (isInitialSortSetup) {
                        Log.d("OptionsFragment_Sort", "Sort Spinner: TAGGED path and isInitialSortSetup was true. Now setting isInitialSortSetup = false.")
                        isInitialSortSetup = false
                    }
                    // Log after potential change to isInitialSortSetup and before returning
                    Log.d("OptionsFragment_Sort", "Sort Spinner: TAGGED path FINISHED. isInitialSortSetup_NOW: $isInitialSortSetup. Returning. Current VM sort: ${sharedOptionsViewModel.uiState.value.currentSort.displayName}")
                    return
                }

                if (isInitialSortSetup) {
                    // This is the path where a user click might be mistakenly consumed
                    Log.d("OptionsFragment_Sort", "Sort Spinner: UNTAGGED path, but isInitialSortSetup_WAS_TRUE. Setting isInitialSortSetup = false and returning. This 'eats' user click. Selected: ${selectedSort.displayName}. VM.currentSort: ${sharedOptionsViewModel.uiState.value.currentSort.displayName}")
                    isInitialSortSetup = false
                    return
                }

                // --- If we reach here, it's a genuine user interaction after initial setup has been handled ---
                Log.d("OptionsFragment_Sort", "Sort Spinner: User interaction presumed (passed tag and initial setup checks). Selected: ${selectedSort.displayName}. VM.currentSort_BEFORE_ACTION: ${sharedOptionsViewModel.uiState.value.currentSort.displayName}")


                if (binding.root.isLayoutRequested || !binding.root.isAttachedToWindow) { // This check might be too aggressive or need refinement
                    Log.w("OptionsFragment_Sort", "Sort Spinner: View is not ready (layout requested or not attached). Deferring/ignoring action for ${selectedSort.displayName}.")
                    // return@setOnItemSelectedListener // original code did not return here, be cautious if re-adding
                }

                if (selectedSort == sharedOptionsViewModel.uiState.value.currentSort) {
                    Log.d("OptionsFragment_Sort", "Sort Spinner: User selected sort (${selectedSort.displayName}) matches ViewModel's current sort. IGNORING.")
                    return
                }

                if (selectedSort == PlaceSort.NEAREST) {
                    sortToRevertToIfNearestFails = sharedOptionsViewModel.uiState.value.currentSort.takeIf { it != PlaceSort.NEAREST } ?: PlaceSort.ALPHABETICAL
                    Log.d("OptionsFragment_Loc", "Sort Spinner: User wants NEAREST. Storing previous sort: ${sortToRevertToIfNearestFails?.displayName}. Setting isAwaitingPermission.")
                    isAwaitingPermissionForNearestSort = true
                    requestLocationPermissionOrFetchForNearest()
                } else {
                    Log.d("OptionsFragment_Sort", "Sort Spinner: Non-Nearest sort selected: ${selectedSort.displayName}. Calling VM.setSort.")
                    sharedOptionsViewModel.setSort(selectedSort)
                    isAwaitingPermissionForNearestSort = false
                    sortToRevertToIfNearestFails = null
                }
                Log.d("OptionsFragment_Sort", "Sort Spinner: onItemSelected EXITED NORMALLY for ${selectedSort.displayName}. <<<<")
            } // End of onItemSelected
            override fun onNothingSelected(parent: AdapterView<*>?) {
                Log.d("OptionsFragment_Sort", "Sort Spinner: onNothingSelected triggered.")
            }
        }
    }

    private fun requestLocationPermissionOrFetchForNearest() {
        Log.e("!!!!_OptionsFragment_RLPOFFN", "ENTERED requestLocationPermissionOrFetchForNearest. isAwaiting: $isAwaitingPermissionForNearestSort, revertTo: ${sortToRevertToIfNearestFails?.displayName}")
        // Pre-condition: sortToRevertToIfNearestFails SHOULD be set by the caller if this is a NEAREST attempt
        // Pre-condition: isAwaitingPermissionForNearestSort SHOULD be true if this is a NEAREST attempt

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d("!!!!_OptionsFragment_RLPOFFN", "Permission GRANTED. Calling fetchLocationAndUpdateViewModelForNearest().")
            fetchLocationAndUpdateViewModelForNearest() // This will now handle its own reset of isAwaiting/sortToRevert
        } else {
            Log.d("OptionsFragment_Loc", "NEAREST selected/needed, permission needed. Requesting...")
            // isAwaitingPermissionForNearestSort = true; // This should be set by the CALLER (onStart or spinner listener)
            // sortToRevertToIfNearestFails is also set by the CALLER

            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                Toast.makeText(requireContext(), "Location permission is needed to sort by nearest places.", Toast.LENGTH_LONG).show()
            }
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    @SuppressLint("MissingPermission") // We check permissions before calling this
    private fun fetchLocationAndUpdateViewModelForNearest() {
        Log.d("OptionsFragment_Loc", "ENTERED fetchLocationAndUpdateViewModelForNearest. isAwaiting: $isAwaitingPermissionForNearestSort, revertTo: ${sortToRevertToIfNearestFails?.displayName}")

        val hasFinePermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val permissionGranted = hasFinePermission || hasCoarsePermission

        if (!permissionGranted) {
            Log.e("OptionsFragment_Loc", "fetchLocationAndUpdateViewModelForNearest called without permission! THIS IS A BUG. Reverting sort and updating VM.")
            if (isAwaitingPermissionForNearestSort) {
                Toast.makeText(requireContext(), "Location permission error. Cannot sort by nearest.", Toast.LENGTH_LONG).show()
                val fallbackSort = sortToRevertToIfNearestFails ?: PlaceSort.ALPHABETICAL
                sharedOptionsViewModel.setSort(fallbackSort) // Revert sort
            }
            // ***** CORRECTED: Call deviceLocationUpdated *****
            sharedOptionsViewModel.deviceLocationUpdated(null, false) // Inform VM: no location, no permission

            if (isAwaitingPermissionForNearestSort) { // Reset flags only if this flow was for NEAREST
                isAwaitingPermissionForNearestSort = false
                sortToRevertToIfNearestFails = null
            }
            return
        }

        // If we are here, permission IS granted.
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                Log.d("OptionsFragment_Loc", "Location fetch success. Location: ${location?.latitude}")
                if (location != null) {
                    Log.d("OptionsFragment_Loc", "Location is NOT NULL. Updating ViewModel.")
                    // ***** CORRECTED: Call deviceLocationUpdated *****
                    sharedOptionsViewModel.deviceLocationUpdated(location, true) // Location found, permission granted

                    if (isAwaitingPermissionForNearestSort) {
                        // Ensure NEAREST sort is set in the ViewModel if this fetch was triggered for it.
                        // deviceLocationUpdated will set nearestSortDataRefreshed if sort is NEAREST.
                        if (sharedOptionsViewModel.uiState.value.currentSort != PlaceSort.NEAREST) {
                            Log.d("OptionsFragment_Loc", "Setting sort in ViewModel to NEAREST after successful location.")
                            sharedOptionsViewModel.setSort(PlaceSort.NEAREST)
                        }
                    }
                } else {
                    Log.w("OptionsFragment_Loc", "Fetched location is null (but permission was granted).")
                    Toast.makeText(requireContext(), "Could not retrieve current location for NEAREST sort.", Toast.LENGTH_SHORT).show()
                    // ***** CORRECTED: Call deviceLocationUpdated *****
                    sharedOptionsViewModel.deviceLocationUpdated(null, true) // No location, but permission was granted

                    if (isAwaitingPermissionForNearestSort) {
                        val fallbackSort = sortToRevertToIfNearestFails ?: PlaceSort.ALPHABETICAL
                        Log.d("OptionsFragment_Loc", "Reverting to sort: ${fallbackSort.displayName} due to null location.")
                        sharedOptionsViewModel.setSort(fallbackSort)
                    }
                }
                // Reset flags specific to the NEAREST sort attempt in OptionsFragment AFTER success or failure to get location
                if (isAwaitingPermissionForNearestSort) {
                    isAwaitingPermissionForNearestSort = false
                    sortToRevertToIfNearestFails = null
                    Log.d("OptionsFragment_Loc", "Reset isAwaiting and sortToRevertToIfNearestFails after location fetch attempt (success/null location).")
                }
            }
            .addOnFailureListener { e ->
                Log.e("OptionsFragment_Loc", "Failed to fetch location for NEAREST sort.", e)
                Toast.makeText(requireContext(), "Failed to get location.", Toast.LENGTH_SHORT).show()
                // ***** CORRECTED: Call deviceLocationUpdated *****
                sharedOptionsViewModel.deviceLocationUpdated(null, true) // No location, but permission was granted (as we attempted fetch)

                if (isAwaitingPermissionForNearestSort) {
                    val fallbackSort = sortToRevertToIfNearestFails ?: PlaceSort.ALPHABETICAL
                    Log.d("OptionsFragment_Loc", "Reverting to sort: ${fallbackSort.displayName} due to location fetch failure.")
                    sharedOptionsViewModel.setSort(fallbackSort)
                    isAwaitingPermissionForNearestSort = false // Ensure flags are reset on failure too
                    sortToRevertToIfNearestFails = null
                    Log.d("OptionsFragment_Loc", "Reset isAwaiting and sortToRevertToIfNearestFails after location fetch failure listener.")
                }
            }
    }


    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedOptionsViewModel.uiState.collect { uiState ->
                    Log.d("OptionsFragment_Observer", ">>>> Observer START. Filter=${uiState.currentFilter.displayName}, Sort=${uiState.currentSort.displayName}. Flags BEFORE: isInitialSort=$isInitialSortSetup, isInitialFilter=$isInitialFilterSetup. Spinner Tag: ${binding.spinnerSort.tag}")

                    // --- Filter Spinner ---
                    val filterPosition = filterAdapter.getPosition(uiState.currentFilter)
                    if (binding.spinnerFilter.selectedItemPosition != filterPosition && filterPosition != -1) {
                        // Log.d("OptionsFragment_Observer", "Observer: Filter spinner update. Setting isInitialFilterSetup = true.")
                        isInitialFilterSetup = true // This flag seems to work well for the filter spinner
                        binding.spinnerFilter.setSelection(filterPosition, false)
                    }

                    // --- Sort Spinner ---
                    var needsAdapterUpdate = false
                    val currentAdapterSortItems = (0 until sortAdapter.count).mapNotNull { sortAdapter.getItem(it) }
                    if (currentAdapterSortItems.toSet() != uiState.availableSortOptions.toSet()) {
                        Log.i("OptionsFragment_Observer", "Observer: Sort adapter content changing.")
                        sortAdapter.clear()
                        sortAdapter.addAll(uiState.availableSortOptions)
                        needsAdapterUpdate = true // If adapter changes, we'll likely need to set selection
                    }

                    val targetSortPositionInAdapter = sortAdapter.getPosition(uiState.currentSort)
                    val currentSortSpinnerPosition = binding.spinnerSort.selectedItemPosition
                    var sortNeedsProgrammaticSetSelectionCall = false

                    if (needsAdapterUpdate) {
                        // If adapter was updated, we always need to ensure selection is correct.
                        // Target could be -1 if currentSort is not in new adapter.
                        sortNeedsProgrammaticSetSelectionCall = true
                        Log.d("OptionsFragment_Observer", "Observer: Sort adapter changed. Will ensure selection.")
                    } else if (targetSortPositionInAdapter != -1 && currentSortSpinnerPosition != targetSortPositionInAdapter) {
                        // Adapter same, but selection in UI doesn't match VM state.
                        sortNeedsProgrammaticSetSelectionCall = true
                        Log.d("OptionsFragment_Observer", "Observer: Sort selection differs from VM. Will set selection.")
                    } else if (targetSortPositionInAdapter == -1 && uiState.availableSortOptions.isNotEmpty()) {
                        // Adapter same, VM sort not in adapter (shouldn't happen if VM state is consistent).
                        // Default to 0 if possible.
                        sortNeedsProgrammaticSetSelectionCall = true
                        Log.w("OptionsFragment_Observer", "Observer: Current VM sort not in adapter. Will try to select 0.")
                    }


                    if (sortNeedsProgrammaticSetSelectionCall) {
                        val finalTargetPosition = if (targetSortPositionInAdapter != -1) {
                            targetSortPositionInAdapter
                        } else if (sortAdapter.count > 0) {
                            0 // Default to first item if current VM sort not found
                        } else {
                            -1 // No items to select
                        }

                        if (finalTargetPosition != -1) {
                            Log.d("OptionsFragment_Observer", "Observer: Preparing to set sort spinner to pos $finalTargetPosition (${sortAdapter.getItem(finalTargetPosition)?.displayName}). Current spinner pos: $currentSortSpinnerPosition.")
                            if (currentSortSpinnerPosition != finalTargetPosition) {
                                Log.d("OptionsFragment_Observer", "Observer: Position differs. TAGGING and calling setSelection.")
                                binding.spinnerSort.tag = SPINNER_TAG_PROGRAMMATIC_SELECTION
                                binding.spinnerSort.setSelection(finalTargetPosition, false)
                                // isInitialSortSetup will be handled by the listener via the tag path or its own check
                            } else {
                                // Target position is already selected. Listener won't fire.
                                Log.d("OptionsFragment_Observer", "Observer: Target position $finalTargetPosition is already selected. Listener likely won't fire.")
                                if (binding.spinnerSort.tag == SPINNER_TAG_PROGRAMMATIC_SELECTION) {
                                    Log.w("OptionsFragment_Observer", "Observer: Clearing STALE tag because target item already selected and listener won't fire to clear it.")
                                    binding.spinnerSort.tag = null
                                }
                                // If this observer pass was meant to satisfy the initial setup condition
                                // (e.g., on fragment start, or after a significant state change like adapter reload),
                                // and the spinner is already in the correct state, then the "initial setup"
                                // for the sort spinner can be considered done.
                                if (isInitialSortSetup) { // <--- NEW CONDITION CHECK
                                    Log.w("OptionsFragment_Observer", "Observer: Spinner already matches VM for initial setup. Resetting isInitialSortSetup to false.")
                                    isInitialSortSetup = false
                                }
                            }
                        } else {
                            Log.w("OptionsFragment_Observer", "Observer: No valid final target position for sort spinner.")
                            if (isInitialSortSetup) { // Also handle if adapter is empty but was initial setup
                                Log.w("OptionsFragment_Observer", "Observer: No target pos, but was initial setup. Resetting isInitialSortSetup to false.")
                                isInitialSortSetup = false
                            }
                            if (binding.spinnerSort.tag == SPINNER_TAG_PROGRAMMATIC_SELECTION) { // Clear tag if adapter empty
                                Log.w("OptionsFragment_Observer", "Observer: Clearing STALE tag, no target pos.")
                                binding.spinnerSort.tag = null
                            }
                        }
                    } else {
                        Log.d("OptionsFragment_Observer", "Observer: No programmatic sort selection change needed for ${uiState.currentSort.displayName}.")
                        if (binding.spinnerSort.tag == SPINNER_TAG_PROGRAMMATIC_SELECTION) {
                            Log.w("OptionsFragment_Observer", "Observer: Clearing STALE tag because no programmatic update was needed in this pass.")
                            binding.spinnerSort.tag = null
                        }
                        // If no programmatic update was needed at all, and isInitialSortSetup is still true,
                        // it means the initial state from the VM matches the default spinner state.
                        // Consider the initial setup satisfied.
                        if (isInitialSortSetup && binding.spinnerSort.selectedItemPosition == targetSortPositionInAdapter && targetSortPositionInAdapter != -1) { // Check that it matches VM
                            Log.w("OptionsFragment_Observer", "Observer: No programmatic update needed, current UI matches VM for initial state. Resetting isInitialSortSetup.")
                            isInitialSortSetup = false
                        }
                    }
                }
            }
        }
    }


    private fun setupDoneButton() {
        binding.buttonDoneOptions.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("OptionsFragment_Lifecycle", "onDestroyView called. Resetting flags.")
        // Reset flags to defaults
        isInitialFilterSetup = true
        isInitialSortSetup = true
        // isAwaitingPermissionForNearestSort = false // These are reset more locally now after each flow
        // sortToRevertToIfNearestFails = null
        _binding = null
    }
}
