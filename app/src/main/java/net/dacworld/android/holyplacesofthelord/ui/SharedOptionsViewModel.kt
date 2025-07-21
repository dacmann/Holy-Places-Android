// SharedOptionsViewModel.kt
package net.dacworld.android.holyplacesofthelord.ui

import android.location.Location // You have this
import android.util.Log         // For logging, uncomment or add if you use Log.d/e
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.update
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope // *** ADD THIS (or uncomment) ***
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch              // *** ADD THIS ***
import kotlinx.coroutines.flow.combine             // *** ADD THIS ***
import kotlinx.coroutines.flow.collect            // *** ADD THIS ***
import kotlinx.coroutines.flow.distinctUntilChanged // *** ADD THIS ***
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map                // *** ADD THIS ***
import kotlinx.coroutines.launch       // *** ADD THIS (or uncomment) ***
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.model.PlaceFilter
import net.dacworld.android.holyplacesofthelord.model.PlaceSort
import net.dacworld.android.holyplacesofthelord.model.Temple // Assuming OptionsUiState needs it for displayedTemples
import net.dacworld.android.holyplacesofthelord.model.getSortOptionsForFilter
import androidx.datastore.preferences.core.edit
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager

class SharedOptionsViewModelFactory(
    private val dataViewModel: DataViewModel,
    private val userPreferencesManager: UserPreferencesManager // INJECT UserPreferencesManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SharedOptionsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SharedOptionsViewModel(dataViewModel, userPreferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class OptionsUiState(
    val currentFilter: PlaceFilter = PlaceFilter.HOLY_PLACES,
    val currentSort: PlaceSort = PlaceSort.ALPHABETICAL,
    val availableSortOptions: List<PlaceSort> = getSortOptionsForFilter(PlaceFilter.HOLY_PLACES),
    //val triggerLocationSetup: Boolean = false, // For "Nearest"
    val hasLocationPermission: Boolean = false, // Added for completeness based on logic
    val currentDeviceLocation: Location? = null,
    val displayedTemples: List<Temple> = emptyList() // Make sure Temple is imported
)

class SharedOptionsViewModel(
    private val dataViewModel: DataViewModel,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OptionsUiState())
    val uiState: StateFlow<OptionsUiState> = _uiState.asStateFlow()

    // Companion object with TYPE_* constants (as defined before)
    companion object {
        const val TYPE_ACTIVE_TEMPLE = "T"
        const val TYPE_HISTORICAL_SITE = "H"
        const val TYPE_ANNOUNCED_TEMPLE = "A"
        const val TYPE_CONSTRUCTION_TEMPLE = "C"
        const val TYPE_VISITORS_CENTER = "V"
    }

    init {

        // --- NEW: Reactive logic for displayedTemples ---
        viewModelScope.launch {
            // Load initial filter and sort preferences using UserPreferencesManager
            val initialFilterName = userPreferencesManager.selectedFilterFlow.first()
                ?: PlaceFilter.HOLY_PLACES.name // Default if flow emits null or is empty

            val loadedFilter = try {
                PlaceFilter.valueOf(initialFilterName)
            } catch (e: IllegalArgumentException) {
                Log.w("SharedVM_Init", "Failed to parse stored filter: '$initialFilterName'. Defaulting.")
                PlaceFilter.HOLY_PLACES
            }

            val defaultSortForLoadedFilter = getSortOptionsForFilter(loadedFilter).firstOrNull() ?: PlaceSort.ALPHABETICAL
            val initialSortName = userPreferencesManager.selectedSortFlow.first()
                ?: defaultSortForLoadedFilter.name // Default

            var loadedSort = try {
                PlaceSort.valueOf(initialSortName)
            } catch (e: IllegalArgumentException) {
                Log.w("SharedVM_Init", "Failed to parse stored sort: '$initialSortName'. Defaulting.")
                defaultSortForLoadedFilter
            }

            val availableSortsForLoadedFilter = getSortOptionsForFilter(loadedFilter)
            if (!availableSortsForLoadedFilter.contains(loadedSort)) {
                Log.w("SharedVM_Init", "Loaded sort ${loadedSort.displayName} is not valid for loaded filter ${loadedFilter.displayName}. Resetting sort.")
                loadedSort = availableSortsForLoadedFilter.firstOrNull() ?: PlaceSort.ALPHABETICAL
            }

            //val shouldTriggerLocationSetup = (loadedSort == PlaceSort.NEAREST)

            _uiState.update { currentState ->
                currentState.copy(
                    currentFilter = loadedFilter,
                    currentSort = loadedSort,
                    availableSortOptions = availableSortsForLoadedFilter,
                    //triggerLocationSetup = if (shouldTriggerLocationSetup) true else currentState.triggerLocationSetup
                )
            }
            Log.d("SharedVM_Init", "Initial state from UPM: Filter=${loadedFilter.displayName}, Sort=${loadedSort.displayName}")

            combine<Triple<PlaceFilter, PlaceSort, Location?>, List<Temple>, List<Temple>>(
                _uiState.map { state ->
                    Triple(state.currentFilter, state.currentSort, state.currentDeviceLocation)
                }.distinctUntilChanged(),
                dataViewModel.allTemples
            ) {
                // Explicitly typed parameters (already correct from previous steps):
                    (filter: PlaceFilter, sort: PlaceSort, location: Location?),
                    allTemplesFromDataVM: List<Temple>
                ->

                Log.d("SharedVM", "Combining: Filter=${filter.displayName}, Sort=${sort.displayName}, AllTemplesCount=${allTemplesFromDataVM.size}")
                applyFilterAndSort(allTemplesFromDataVM, filter, sort, location)
            }
                .distinctUntilChanged()
                .catch { e ->
                    Log.e("SharedVM", "Error combining flows for displayedTemples: ${e.message}", e)
                    _uiState.update { it.copy(displayedTemples = emptyList()) }
                }
                .collect { resultList: List<Temple> ->
                    Log.d("SharedVM_Reverted", "Collected List<Temple>: Size=${resultList.size}")
                    _uiState.update { currentUiState ->
                        currentUiState.copy(displayedTemples = resultList)
                    }
                }
        }

        // This block for ensuring initial sort is valid might run before the launch block above fully updates state from preferences.
        // It's generally better to handle this within the same launch block that loads preferences.
        // However, if kept, ensure it doesn't prematurely overwrite things.
        // The launch block above is now the primary source for initial filter/sort state.
        // We can simplify or remove this if the launch block correctly sets availableSortOptions based on loadedFilter.

        // Consider if this is still needed or if the launch block above handles it sufficiently.
        // If the launch block sets currentFilter, currentSort, and availableSortOptions based on loaded preferences,
        // this might be redundant or could even cause a brief incorrect state.
        // For now, let's assume the launch block correctly initializes everything.
        // If issues arise, we might need to ensure this logic is correctly sequenced or integrated.
        /*
        val initialFilter = _uiState.value.currentFilter // This will be the default PlaceFilter.HOLY_PLACES initially
        val initialAvailableSorts = getSortOptionsForFilter(initialFilter)
        _uiState.update { currentState ->
            currentState.copy(
                availableSortOptions = initialAvailableSorts,
                currentSort = if (initialAvailableSorts.contains(currentState.currentSort)) {
                    currentState.currentSort
                } else {
                    initialAvailableSorts.firstOrNull() ?: PlaceSort.ALPHABETICAL
                }
            )
        }
        */
        // The primary initialization of currentFilter, currentSort, and availableSortOptions
        // now happens within the viewModelScope.launch block using loaded preferences.
    }

    private fun applyFilterAndSort(
        allTemples: List<Temple>,
        filter: PlaceFilter,
        sort: PlaceSort,
        currentDeviceLocation: Location? // This is the Location from _uiState.currentDeviceLocation
    ): List<Temple> {
        Log.d("SharedVM_FilterSort", "Applying Filter: ${filter.displayName}, Sort: ${sort.displayName}")

        // 1. Apply Filter
        val filteredList = when (filter) {
            PlaceFilter.HOLY_PLACES -> {
                // Assuming HOLY_PLACES means all types T, H, A, C, V (adjust if different)
                allTemples // Or further filter if HOLY_PLACES is a specific subset
            }
            PlaceFilter.ACTIVE_TEMPLES -> allTemples.filter { it.type == TYPE_ACTIVE_TEMPLE }
            PlaceFilter.HISTORICAL_SITES -> allTemples.filter { it.type == TYPE_HISTORICAL_SITE }
            PlaceFilter.VISITORS_CENTERS -> allTemples.filter { it.type == TYPE_VISITORS_CENTER }
            PlaceFilter.TEMPLES_UNDER_CONSTRUCTION -> allTemples.filter { it.type == TYPE_CONSTRUCTION_TEMPLE }
            PlaceFilter.ANNOUNCED_TEMPLES -> allTemples.filter { it.type == TYPE_ANNOUNCED_TEMPLE }
            PlaceFilter.ALL_TEMPLES -> {
                // Assuming ALL_TEMPLES means only actual temple structures (T, A, C)
                allTemples.filter {
                    it.type == TYPE_ACTIVE_TEMPLE ||
                            it.type == TYPE_CONSTRUCTION_TEMPLE ||
                            it.type == TYPE_ANNOUNCED_TEMPLE
                }
            }
            // Add other PlaceFilter cases if any
        }
        Log.d("SharedVM_FilterSort", "After filtering (${filter.displayName}): Count=${filteredList.size}")

        // 2. Apply Sort
        val sortedList = when (sort) {
            PlaceSort.ALPHABETICAL -> filteredList.sortedBy { it.name }
            PlaceSort.COUNTRY -> filteredList.sortedWith(compareBy<Temple> { it.country }.thenBy { it.name })
            PlaceSort.DEDICATION_DATE -> {
                // Ensure temples without dedication dates are handled (e.g., sorted last or filtered out if not applicable)
                filteredList.filter { it.order != null } // Example: only sort those with an order/dedication
                    .sortedBy { it.order }
            }
            PlaceSort.SIZE -> {
                // Handle null sqFt values (e.g. sort them last or give a default smallest value)
                filteredList.sortedWith(compareByDescending { it.sqFt ?: Int.MIN_VALUE })
            }
            PlaceSort.ANNOUNCED_DATE -> {
                // Ensure temples without announced dates are handled
                filteredList.filter { it.announcedDate != null }
                    .sortedBy { it.announcedDate }
            }
            PlaceSort.NEAREST -> {
                if (currentDeviceLocation != null) {
                    filteredList.map { temple ->
                        temple.setDistanceInMeters(currentDeviceLocation) // Sets temple.distance in meters
                        temple
                    }.sortedWith(compareBy(nullsLast()) { it.distance }) // Sort by meters, nulls (no location/calc error) last
                } else {
                    Log.w("SharedVM_FilterSort", "NEAREST sort: device location not available. Defaulting to alphabetical.")
                    filteredList.sortedBy { it.name } // Fallback sort
                }
            }
            // Add other PlaceSort cases if any
        }
        Log.d("SharedVM_FilterSort", "After sorting (${sort.displayName}): Count=${sortedList.size}")

        return sortedList
    }
    fun setFilter(filterType: PlaceFilter) {
        Log.d("SharedVM", "setFilter called with: ${filterType.displayName}")
        val newAvailableSorts = getSortOptionsForFilter(filterType)
        _uiState.update {
            it.copy(
                currentFilter = filterType,
                availableSortOptions = newAvailableSorts,
                // Reset sort if current sort is no longer valid, or keep if still valid
                currentSort = if (newAvailableSorts.contains(it.currentSort)) it.currentSort else newAvailableSorts.first(),
                // triggerLocationSetup = false // Reset location trigger
            )
        }
        // --- ADD THIS BLOCK TO SAVE THE FILTER ---
        viewModelScope.launch {
            userPreferencesManager.saveSelectedFilter(filterType.name) // Use UPM
            Log.d("SharedVM", "Saved filter preference via UPM: ${filterType.name}")
        }
    }

    fun setSort(sortType: PlaceSort) {
        _uiState.update {
            it.copy(
                currentSort = sortType,
                // triggerLocationSetup = (sortType == PlaceSort.NEAREST && !it.triggerLocationSetup) // Trigger only once per selection
            )
        }
        // Save to DataStore
        viewModelScope.launch {
            userPreferencesManager.saveSelectedSort(sortType.name) // Use UPM
            Log.d("SharedVM", "Saved sort preference via UPM: ${sortType.name}")
        }
    }

    fun setDeviceLocation(location: Location?) {
        Log.e("!!!!_SharedVM_SETLOC", "ENTERED setDeviceLocation. Location: ${location?.latitude}") // High visibility log
        _uiState.update { currentState ->
            currentState.copy(currentDeviceLocation = location)
        }
        Log.d("SharedVM_SetDeviceLoc", "_uiState.currentDeviceLocation is now: ${_uiState.value.currentDeviceLocation?.latitude}") // Log exit
    }

//    fun locationSetupTriggerConsumed() {
//        _uiState.update { currentState ->
//            currentState.copy(triggerLocationSetup = false)
//        }
//        Log.d("SharedVM", "Location setup trigger consumed.") // Optional: for debugging
//    }
}
