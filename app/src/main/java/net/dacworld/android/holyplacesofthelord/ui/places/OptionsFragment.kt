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
                Log.d("OptionsFragment_LocPerm", "Permission GRANTED, but not specifically for NEAREST sort.")
            }
            // sharedOptionsViewModel.updateHasLocationPermission(true) // Optional: If you track this separately
        } else {
            if (isAwaitingPermissionForNearestSort) {
                Log.w("OptionsFragment_LocPerm", "Permission DENIED for NEAREST sort. Reverting.")
                Toast.makeText(requireContext(), "Location permission denied. Cannot sort by nearest.", Toast.LENGTH_LONG).show()
                val fallbackSort = sortToRevertToIfNearestFails ?: PlaceSort.ALPHABETICAL // Use the stored fallback
                sharedOptionsViewModel.setSort(fallbackSort)
                // Reset is now handled after fetch/failure or here if permission denied
            } else {
                Log.w("OptionsFragment_LocPerm", "Permission DENIED, not for NEAREST sort.")
            }
            // sharedOptionsViewModel.updateHasLocationPermission(false) // Optional
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
                if (isInitialFilterSetup) {
                    isInitialFilterSetup = false // Don't trigger update on initial ViewModel load
                    return
                }
                val selectedFilter = parent?.getItemAtPosition(position) as PlaceFilter
                Log.d("OptionsFragment", "Filter Spinner selected: ${selectedFilter.displayName}")
                sharedOptionsViewModel.setFilter(selectedFilter)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
                if (isInitialSortSetup) {
                    isInitialSortSetup = false
                    Log.d("OptionsFragment_Sort", "Initial sort setup, spinner listener returning. Current VM sort: ${sharedOptionsViewModel.uiState.value.currentSort.displayName}")
                    return
                }

                val selectedSort = parent?.getItemAtPosition(position) as PlaceSort
                Log.d("OptionsFragment_Sort", "User selected Sort from spinner: ${selectedSort.displayName}")

                if (selectedSort == sharedOptionsViewModel.uiState.value.currentSort) {
                    Log.d("OptionsFragment_Sort", "Spinner selection matches ViewModel's current sort. Ignoring.")
                    return
                }

                if (selectedSort == PlaceSort.NEAREST) {
                    // Store the sort we were on *before* attempting NEAREST
                    sortToRevertToIfNearestFails = sharedOptionsViewModel.uiState.value.currentSort.takeIf { it != PlaceSort.NEAREST } ?: PlaceSort.ALPHABETICAL
                    Log.d("OptionsFragment_Loc", "User wants NEAREST from spinner. Storing previous sort: ${sortToRevertToIfNearestFails?.displayName}. Setting isAwaitingPermission.")
                    isAwaitingPermissionForNearestSort = true // Signal that upcoming permission/fetch is for this user action
                    requestLocationPermissionOrFetchForNearest()
                } else {
                    Log.d("OptionsFragment_Sort", "Non-Nearest sort selected: ${selectedSort.displayName}. Updating ViewModel.")
                    sharedOptionsViewModel.setSort(selectedSort)
                    // If user explicitly moves away from NEAREST, clear flags related to a pending NEAREST operation.
                    isAwaitingPermissionForNearestSort = false
                    sortToRevertToIfNearestFails = null
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
        Log.e("!!!!_OptionsFragment_FLAUVFN", "ENTERED fetchLocationAndUpdateViewModelForNearest") // High visibility log

        // Defensive check
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("OptionsFragment_Loc", "fetchLocationAndUpdateViewModelForNearest called without permission! THIS IS A BUG.")
            if (isAwaitingPermissionForNearestSort) { // Check if this was part of a permission flow that somehow failed at the check
                val fallbackSort = sortToRevertToIfNearestFails ?: PlaceSort.ALPHABETICAL
                sharedOptionsViewModel.setSort(fallbackSort)
            }
            // Reset flags after handling the failure
            isAwaitingPermissionForNearestSort = false
            sortToRevertToIfNearestFails = null
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                Log.d("!!!!_OptionsFragment_FLAUVFN", "Location fetch success. Location: ${location?.latitude}")
                if (location != null) {
                    Log.d("!!!!_OptionsFragment_FLAUVFN", "Location is NOT NULL. Calling VM.setDeviceLocation.")
                    sharedOptionsViewModel.setDeviceLocation(location)
                    // Crucially, set the sort to NEAREST in the VM.
                    // This ensures that if the location fetch was triggered by user selecting NEAREST (or onStart check),
                    // the VM's state reflects NEAREST as the active sort, utilizing the new location.
                    if (sharedOptionsViewModel.uiState.value.currentSort != PlaceSort.NEAREST) {
                        Log.d("OptionsFragment_Loc", "Setting sort in ViewModel to NEAREST after successful location.")
                        sharedOptionsViewModel.setSort(PlaceSort.NEAREST) // This will trigger combine in VM
                    } else {
                        // If already NEAREST, the location update alone will trigger combine.
                        Log.d("OptionsFragment_Loc", "ViewModel sort is already NEAREST. Location update should suffice.")
                    }
                } else {
                    Log.w("OptionsFragment_Loc", "Fetched location is null. Cannot apply NEAREST.")
                    Toast.makeText(requireContext(), "Could not retrieve current location.", Toast.LENGTH_SHORT).show()
                    val fallbackSort = sortToRevertToIfNearestFails ?: PlaceSort.ALPHABETICAL
                    Log.d("OptionsFragment_Loc", "Reverting to sort: ${fallbackSort.displayName} due to null location.")
                    sharedOptionsViewModel.setSort(fallbackSort)
                }
                // Reset flags after success or handled failure (null location)
                isAwaitingPermissionForNearestSort = false
                sortToRevertToIfNearestFails = null
                Log.d("!!!!_OptionsFragment_FLAUVFN", "Reset isAwaiting and sortToRevertToIfNearestFails after fetch success/handled fail.")
            }
            .addOnFailureListener { e ->
                Log.e("OptionsFragment_Loc", "Failed to fetch location", e)
                Toast.makeText(requireContext(), "Failed to get location.", Toast.LENGTH_SHORT).show()
                val fallbackSort = sortToRevertToIfNearestFails ?: PlaceSort.ALPHABETICAL
                Log.d("OptionsFragment_Loc", "Reverting to sort: ${fallbackSort.displayName} due to location fetch failure.")
                sharedOptionsViewModel.setSort(fallbackSort)
                // Reset flags after failure
                isAwaitingPermissionForNearestSort = false
                sortToRevertToIfNearestFails = null
                Log.d("!!!!_OptionsFragment_FLAUVFN", "Reset isAwaiting and sortToRevertToIfNearestFails after fetch failure listener.")
            }
    }
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedOptionsViewModel.uiState.collect { uiState ->
                    // CHANGE 4: Adjust log and remove triggerLocationSetup block
                    Log.d("OptionsFragment_Observer", "Observed UI State: Filter=${uiState.currentFilter.displayName}, Sort=${uiState.currentSort.displayName}, Location: ${uiState.currentDeviceLocation != null}")

                    // CHANGE 4: REMOVE THIS ENTIRE BLOCK
                    /*
                    if (uiState.triggerLocationSetup && uiState.currentSort == PlaceSort.NEAREST) {
                        // This logic is now handled by onStart() or direct user interaction
                        Log.e("!!!!_OptionsFragment_TRIGGER", "Old TRIGGER DETECTED for NEAREST - THIS SHOULD NOT HAPPEN.")
                        // sortToRevertToIfNearestFails = uiState.availableSortOptions.firstOrNull { it != PlaceSort.NEAREST } ?: PlaceSort.ALPHABETICAL
                        // requestLocationPermissionOrFetchForNearest()
                        // sharedOptionsViewModel.locationSetupTriggerConsumed()
                    } else if (uiState.triggerLocationSetup) {
                        Log.w("!!!!_OptionsFragment_TRIGGER", "Old TriggerLoc was true, but currentSort is ${uiState.currentSort.displayName}. Consuming. THIS SHOULD NOT HAPPEN")
                        // sharedOptionsViewModel.locationSetupTriggerConsumed() // Consume even if not NEAREST to prevent stale trigger
                    }
                    */

                    // --- Update Filter Spinner Selection ---
                    val filterPosition = filterAdapter.getPosition(uiState.currentFilter)
                    if (binding.spinnerFilter.selectedItemPosition != filterPosition && filterPosition != -1) {
                        Log.d("OptionsFragment_Observer", "Updating Filter spinner to: ${uiState.currentFilter.displayName} at pos $filterPosition")
                        isInitialFilterSetup = true // Prevent listener fire for this programmatic change
                        binding.spinnerFilter.setSelection(filterPosition, false)
                    }

                    // --- Update Sort Spinner Adapter Content (Available Sort Options) ---
                    val currentAdapterSortItems = (0 until sortAdapter.count).mapNotNull { sortAdapter.getItem(it) }
                    if (currentAdapterSortItems.toSet() != uiState.availableSortOptions.toSet()) {
                        Log.i("OptionsFragment_Observer", "Updating sort adapter content. New options: ${uiState.availableSortOptions.joinToString { it.displayName }}")
                        isInitialSortSetup = true // Prevent listener fire for selection change after adapter update
                        sortAdapter.clear()
                        sortAdapter.addAll(uiState.availableSortOptions)
                        // sortAdapter.notifyDataSetChanged() // Not needed with addAll if adapter handles it

                        // After adapter content changes, re-apply the current sort selection from ViewModel
                        val sortPositionInNewAdapter = sortAdapter.getPosition(uiState.currentSort)
                        if (sortPositionInNewAdapter != -1) {
                            Log.d("OptionsFragment_Observer", "Restoring Sort spinner selection to: ${uiState.currentSort.displayName} at pos $sortPositionInNewAdapter after adapter update.")
                            // isInitialSortSetup = true; // Already set above
                            binding.spinnerSort.setSelection(sortPositionInNewAdapter, false)
                        } else {
                            Log.w("OptionsFragment_Observer", "Could not find current sort ${uiState.currentSort.displayName} in new adapter options.")
                        }
                    } else {
                        // --- Update Sort Spinner Selection (if adapter content didn't change but selection might need to) ---
                        val sortPositionInAdapter = sortAdapter.getPosition(uiState.currentSort)
                        if (binding.spinnerSort.selectedItemPosition != sortPositionInAdapter && sortPositionInAdapter != -1) {
                            Log.d("OptionsFragment_Observer", "Updating Sort spinner selection to: ${uiState.currentSort.displayName} at pos $sortPositionInAdapter")
                            isInitialSortSetup = true // Prevent listener fire for this programmatic change
                            binding.spinnerSort.setSelection(sortPositionInAdapter, false)
                        }
                    }
                    // --- Update Sort Spinner Adapter Content (Available Sort Options) ---
                    // This ensures the spinner shows the correct list of sort options for the current filter.
//                    val currentAdapterSortItems = (0 until sortAdapter.count).mapNotNull { sortAdapter.getItem(it) }
//                    if (currentAdapterSortItems.toSet() != uiState.availableSortOptions.toSet()) { // Compare sets for order independence
//                        Log.i("OptionsFragment_Observer", "Updating sort adapter data. ViewModel has: ${uiState.availableSortOptions.map { it.displayName }}")
//                        val currentSortBeforeAdapterUpdate = binding.spinnerSort.selectedItem as? PlaceSort
//
//                        isInitialSortSetup = true // Temporarily set to true to prevent listener firing during adapter data change
//                        sortAdapter.clear()
//                        sortAdapter.addAll(uiState.availableSortOptions)
//                        // sortAdapter.notifyDataSetChanged() // Not always needed with clear/addAll but good practice
//
//                        // Attempt to restore selection if the previously selected sort is still available
//                        // This is important because changing adapter data can reset selection.
//                        val newPositionOfOldSort = if (currentSortBeforeAdapterUpdate != null) {
//                            sortAdapter.getPosition(currentSortBeforeAdapterUpdate)
//                        } else {
//                            -1
//                        }
//
//                        if (newPositionOfOldSort != -1) {
//                            binding.spinnerSort.setSelection(newPositionOfOldSort, false)
//                        } else if (uiState.availableSortOptions.isNotEmpty()) {
//                            // If old sort not available, select the current sort from ViewModel (should be valid)
//                            val sortPositionInNewAdapter = sortAdapter.getPosition(uiState.currentSort)
//                            if (sortPositionInNewAdapter != -1) {
//                                binding.spinnerSort.setSelection(sortPositionInNewAdapter, false)
//                            } else if (sortAdapter.count > 0) {
//                                binding.spinnerSort.setSelection(0, false) // Fallback to first item
//                            }
//                        }
//                        // isInitialSortSetup should be reset by the listener itself,
//                        // but we set it true here to ensure this programmatic change is ignored.
//                        // The spinner's onItemSelected listener will set it to false on its first actual run.
//                    }
//
//
//                    // --- Update Sort Spinner Selection (Based on ViewModel's currentSort) ---
//                    // This ensures the spinner's selected item matches the ViewModel's currentSort,
//                    // especially after adapter data might have changed or if VM state changed for other reasons.
//                    val sortPositionInAdapter = sortAdapter.getPosition(uiState.currentSort)
//                    if (binding.spinnerSort.selectedItemPosition != sortPositionInAdapter && sortPositionInAdapter != -1) {
//                        Log.d("OptionsFragment_Observer", "Updating Sort spinner to: ${uiState.currentSort.displayName} (pos: $sortPositionInAdapter)")
//                        isInitialSortSetup = true // Crucial before programmatic selection
//                        binding.spinnerSort.setSelection(sortPositionInAdapter, false)
//                    } else if (sortPositionInAdapter == -1 && uiState.availableSortOptions.isNotEmpty() && sortAdapter.count > 0) {
//                        // This case might happen if currentSort from VM is somehow not in the (newly updated) adapter.
//                        // Should be rare if availableSortOptions is always kept in sync.
//                        Log.w("OptionsFragment_Observer", "Could not find currentSort ${uiState.currentSort.displayName} in adapter. Defaulting to first option in adapter.")
//                        isInitialSortSetup = true
//                        binding.spinnerSort.setSelection(0, false)
//                    }
                    // The spinner's onItemSelected listener should set isInitialSortSetup = false.
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
