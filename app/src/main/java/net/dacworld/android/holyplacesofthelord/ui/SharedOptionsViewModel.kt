// SharedOptionsViewModel.kt
package net.dacworld.android.holyplacesofthelord.ui

import android.location.Location // You have this
import android.util.Log         // For logging, uncomment or add if you use Log.d/e
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map                // *** ADD THIS ***
import kotlinx.coroutines.launch       // *** ADD THIS (or uncomment) ***
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.model.PlaceFilter
import net.dacworld.android.holyplacesofthelord.model.PlaceSort
import net.dacworld.android.holyplacesofthelord.model.Temple // Assuming OptionsUiState needs it for displayedTemples
import net.dacworld.android.holyplacesofthelord.model.getSortOptionsForFilter

class SharedOptionsViewModelFactory(
    private val dataViewModel: DataViewModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SharedOptionsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SharedOptionsViewModel(dataViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
data class OptionsUiState(
    val currentFilter: PlaceFilter = PlaceFilter.HOLY_PLACES,
    val currentSort: PlaceSort = PlaceSort.ALPHABETICAL,
    val availableSortOptions: List<PlaceSort> = getSortOptionsForFilter(PlaceFilter.HOLY_PLACES),
    val triggerLocationSetup: Boolean = false, // For "Nearest"
    val hasLocationPermission: Boolean = false, // Added for completeness based on logic
    val currentDeviceLocation: Location? = null,
    val displayedTemples: List<Temple> = emptyList() // Make sure Temple is imported
)

class SharedOptionsViewModel(
    private val dataViewModel: DataViewModel // Inject DataViewModel
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

        // Ensure initial sort option is valid for initial filter
        val initialFilter = _uiState.value.currentFilter
        val initialAvailableSorts = getSortOptionsForFilter(initialFilter)
        _uiState.update { currentState ->
            // Ensure we don't accidentally overwrite displayedTemples if the launch block above
            // hasn't run yet or is in the process of running.
            // Best practice is to only update specific fields if the update is targeted.
            currentState.copy(
                availableSortOptions = initialAvailableSorts,
                currentSort = if (initialAvailableSorts.contains(currentState.currentSort)) {
                    currentState.currentSort
                } else {
                    initialAvailableSorts.firstOrNull() ?: PlaceSort.ALPHABETICAL // Handle empty list
                }
            )
        }
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
            PlaceFilter.ACTIVE_TEMPLES -> allTemples.filter { it.type == SharedOptionsViewModel.TYPE_ACTIVE_TEMPLE }
            PlaceFilter.HISTORICAL_SITES -> allTemples.filter { it.type == SharedOptionsViewModel.TYPE_HISTORICAL_SITE }
            PlaceFilter.VISITORS_CENTERS -> allTemples.filter { it.type == SharedOptionsViewModel.TYPE_VISITORS_CENTER }
            PlaceFilter.TEMPLES_UNDER_CONSTRUCTION -> allTemples.filter { it.type == SharedOptionsViewModel.TYPE_CONSTRUCTION_TEMPLE }
            PlaceFilter.ANNOUNCED_TEMPLES -> allTemples.filter { it.type == SharedOptionsViewModel.TYPE_ANNOUNCED_TEMPLE }
            PlaceFilter.ALL_TEMPLES -> {
                // Assuming ALL_TEMPLES means only actual temple structures (T, A, C)
                allTemples.filter {
                    it.type == SharedOptionsViewModel.TYPE_ACTIVE_TEMPLE ||
                            it.type == SharedOptionsViewModel.TYPE_CONSTRUCTION_TEMPLE ||
                            it.type == SharedOptionsViewModel.TYPE_ANNOUNCED_TEMPLE
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
        Log.d("SharedVM", "setFilter called with: ${PlaceFilter.values().toList()}")
        val newAvailableSorts = getSortOptionsForFilter(filterType)
        _uiState.update {
            it.copy(
                currentFilter = filterType,
                availableSortOptions = newAvailableSorts,
                // Reset sort if current sort is no longer valid, or keep if still valid
                currentSort = if (newAvailableSorts.contains(it.currentSort)) it.currentSort else newAvailableSorts.first(),
                triggerLocationSetup = false // Reset location trigger
            )
        }
    }

    fun setSort(sortType: PlaceSort) {
        _uiState.update {
            it.copy(
                currentSort = sortType,
                triggerLocationSetup = (sortType == PlaceSort.NEAREST && !it.triggerLocationSetup) // Trigger only once per selection
            )
        }
    }

    fun locationSetupTriggerConsumed() {
        _uiState.update { it.copy(triggerLocationSetup = false) }
    }
}
