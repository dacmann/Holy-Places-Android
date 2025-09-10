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
import net.dacworld.android.holyplacesofthelord.ui.places.DisplayListItem
import net.dacworld.android.holyplacesofthelord.model.PlaceVisitedScope
import net.dacworld.android.holyplacesofthelord.ui.SharedToolbarViewModel


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
    val hasLocationPermission: Boolean = false, // Added for completeness based on logic
    val currentDeviceLocation: Location? = null,
    val displayedListItems: List<DisplayListItem> = emptyList(),
    val nearestSortDataRefreshed: Boolean = false
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
        var previousRelevantLocationForNearestSort: Location? = null
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

            combine(
                // Existing flows:
                _uiState.map { state ->
                    Triple(state.currentFilter, state.currentSort, state.currentDeviceLocation)
                }.distinctUntilChanged(), // #1
                dataViewModel.allTemples, // #2
                // New flows ADDED here for VISITED SCOPE ONLY:
                dataViewModel.currentPlaceVisitedScope, // #3
                dataViewModel.visitedTemplePlaceIdsFlow    // #4
            ) { params ->
                // Destructure parameters based on the order above:
                val (currentFilter, currentSort, deviceLocation) = params[0] as Triple<PlaceFilter, PlaceSort, Location?>
                val allTemplesFromDataVM = params[1] as List<Temple>
                val visitedScope = params[2] as PlaceVisitedScope      // <<< NEW
                val visitedIds = params[3] as Set<String>              // <<< NEW

                Log.d("SharedVM_Combine", "F=$currentFilter, S=$currentSort, Scope=$visitedScope, Temples=${allTemplesFromDataVM.size}")

                // Step 1: Your existing primary filter
                val primaryFilteredTemples = applyFilter(allTemplesFromDataVM, currentFilter)
                Log.d("SharedVM_Step1", "Primary filter ($currentFilter): ${primaryFilteredTemples.size}")

                // Step 2: NEW Visited Scope Filter (applied to primaryFilteredTemples)
                val scopeFilteredTemples = when (visitedScope) {
                    PlaceVisitedScope.ALL -> primaryFilteredTemples
                    PlaceVisitedScope.VISITED -> primaryFilteredTemples.filter { temple -> temple.id in visitedIds }
                    PlaceVisitedScope.NOT_VISITED -> primaryFilteredTemples.filter { temple -> temple.id !in visitedIds }
                }
                Log.d("SharedVM_Step2", "Visited scope ($visitedScope): ${scopeFilteredTemples.size}")

                // Step 3: Your existing Sort (operates on `scopeFilteredTemples`)
                val sortedTemples = applySort(scopeFilteredTemples, currentSort, deviceLocation)
                Log.d("SharedVM_Step3", "Sort ($currentSort): ${sortedTemples.size}")

                // Step 4: Your existing Insert Headers
                val listWithHeaders = insertHeadersIntoSortedList(sortedTemples, currentSort)
                Log.d("SharedVM_Step4", "Headers: ${listWithHeaders.size}")

                listWithHeaders // Return List<DisplayListItem>
            }
                .distinctUntilChanged() // On List<DisplayListItem>
                .catch { e ->
                    Log.e("SharedVM", "Error combining flows for displayedListItems: ${e.message}", e)
                    _uiState.update { it.copy(displayedListItems = emptyList()) } // Update correct field
                }
                .collect { resultListWithHeaders: List<DisplayListItem> -> // Type changed
                    Log.d("SharedVM_Collect", "Collected List<DisplayListItem>: Size=${resultListWithHeaders.size}")
                    _uiState.update { currentUiState ->
                        currentUiState.copy(displayedListItems = resultListWithHeaders) // Update correct field
                    }
                }
        }
    }

    // --- START: ADD THESE TWO NEW FUNCTIONS HERE ---

    /**
     * Called from the Fragment/Activity when the device's location is updated
     * or location permission status changes.
     * This function will update the UI state with the new location and,
     * if conditions are met (Nearest sort active & location changed),
     * set the nearestSortDataRefreshed flag to true.
     */
    fun deviceLocationUpdated(newLocation: Location?, permissionGranted: Boolean) {
        _uiState.update { currentState ->
            var shouldSetRefreshFlag = false
            if (permissionGranted && newLocation != null && currentState.currentSort == PlaceSort.NEAREST) {
                if (currentState.currentDeviceLocation == null ||
                    currentState.currentDeviceLocation?.latitude != newLocation.latitude ||
                    currentState.currentDeviceLocation?.longitude != newLocation.longitude) {
                    shouldSetRefreshFlag = true
                    Log.d("SharedVM_Flag", "deviceLocationUpdated: Setting nearestSortDataRefreshed = true.")
                }
            }

            // If permission was lost, currentDeviceLocation should reflect that (become null)
            val finalLocation = if (permissionGranted) newLocation else null

            currentState.copy(
                currentDeviceLocation = finalLocation,
                hasLocationPermission = permissionGranted,
                // Set the flag if conditions met, otherwise keep its current state.
                // It will be reset by acknowledgeNearestSortDataRefreshed().
                nearestSortDataRefreshed = if (shouldSetRefreshFlag) true else currentState.nearestSortDataRefreshed
            )
        }
    }

    /**
     * Called by the UI (Fragment/Activity) after it has observed
     * nearestSortDataRefreshed = true and taken appropriate action (e.g., scrolling).
     * This resets the flag to false.
     */
    fun acknowledgeNearestSortDataRefreshed() {
        // Only update if the flag is currently true, to avoid unnecessary state emissions
        if (_uiState.value.nearestSortDataRefreshed) {
            _uiState.update { currentState ->
                Log.d("SharedVM_Flag", "acknowledgeNearestSortDataRefreshed: Resetting flag to false.")
                currentState.copy(nearestSortDataRefreshed = false)
            }
        }
    }

    // This function applies ONLY the filter, returns List<Temple>
    private fun applyFilter(
        allTemples: List<Temple>,
        filter: PlaceFilter
    ): List<Temple> {
        return when (filter) {
            PlaceFilter.HOLY_PLACES -> allTemples // Or apply specific logic for HOLY_PLACES
            PlaceFilter.ACTIVE_TEMPLES -> allTemples.filter { it.type == TYPE_ACTIVE_TEMPLE }
            PlaceFilter.HISTORICAL_SITES -> allTemples.filter { it.type == TYPE_HISTORICAL_SITE }
            PlaceFilter.VISITORS_CENTERS -> allTemples.filter { it.type == TYPE_VISITORS_CENTER }
            PlaceFilter.TEMPLES_UNDER_CONSTRUCTION -> allTemples.filter { it.type == TYPE_CONSTRUCTION_TEMPLE }
            PlaceFilter.ANNOUNCED_TEMPLES -> allTemples.filter { it.type == TYPE_ANNOUNCED_TEMPLE }
            PlaceFilter.ALL_TEMPLES -> allTemples.filter {
                it.type == TYPE_ACTIVE_TEMPLE ||
                        it.type == TYPE_CONSTRUCTION_TEMPLE ||
                        it.type == TYPE_ANNOUNCED_TEMPLE
            }
        }
    }

    // This function applies ONLY the sort, returns List<Temple>
    // Make sure it returns a NEW list and handles distance calculation for NEAREST appropriately.
    private fun applySort(
        filteredTemples: List<Temple>,
        sort: PlaceSort,
        currentDeviceLocation: Location?
    ): List<Temple> {
        return when (sort) {
            PlaceSort.ALPHABETICAL -> filteredTemples.sortedBy { it.name }
            PlaceSort.COUNTRY -> filteredTemples.sortedWith(compareBy<Temple> { it.country }.thenBy { it.name })
            PlaceSort.DEDICATION_DATE -> {
                filteredTemples.filter { it.order != null }.sortedBy { it.order }
            }
            PlaceSort.SIZE -> {
                filteredTemples.sortedWith(compareByDescending { it.sqFt ?: Int.MIN_VALUE })
            }
            PlaceSort.ANNOUNCED_DATE -> {
                filteredTemples.filter { it.announcedDate != null }.sortedByDescending { it.announcedDate }
            }
            PlaceSort.NEAREST -> {
                if (currentDeviceLocation != null) {
                    // Important: map to a new list if setDistanceInMeters modifies the Temple object,
                    // or ensure Temple is a data class and setDistanceInMeters returns a new Temple instance.
                    // For simplicity, let's assume setDistanceInMeters is okay or we handle immutability.
                    filteredTemples.map { temple ->
                        // If Temple is a data class, it's better to create a new instance with distance
                        // e.g., temple.copy(distance = calculatedDistance)
                        // For now, assuming current implementation is acceptable for distance calculation.
                        temple.setDistanceInMeters(currentDeviceLocation) // This mutates temple.distance
                        temple
                    }.sortedWith(compareBy(nullsLast()) { it.distance })
                } else {
                    Log.w("SharedVM_Sort", "NEAREST sort: device location not available. Defaulting to alphabetical.")
                    filteredTemples.sortedBy { it.name } // Fallback
                }
            }
        }
    }
//
// NEW FUNCTION to insert headers
private fun insertHeadersIntoSortedList(
    sortedTemples: List<Temple>,
    sortType: PlaceSort // Using your existing PlaceSort enum
): List<DisplayListItem> {
    if (sortedTemples.isEmpty()) return emptyList()

    val displayItems = mutableListOf<DisplayListItem>()

    when (sortType) {
        PlaceSort.ALPHABETICAL -> {
            Log.d("SharedVM_Headers", "Alphabetical sort selected. Skipping header insertion.")
            sortedTemples.forEach { temple ->
                displayItems.add(DisplayListItem.TempleRowItem(temple))
            }
            return displayItems
        }
        PlaceSort.COUNTRY -> {
            var currentCountry = ""
            var countryStartIndex = 0
            for (i in sortedTemples.indices) { // sortedTemples is already sorted by country then name by applySort
                val temple = sortedTemples[i]
                if (temple.country != currentCountry) {
                    if (currentCountry.isNotEmpty()) {
                        val itemsInCountry = sortedTemples.subList(countryStartIndex, i)
                        if (itemsInCountry.isNotEmpty()){
                            displayItems.add(DisplayListItem.HeaderItem(currentCountry, itemsInCountry.size))
                            itemsInCountry.forEach { displayItems.add(DisplayListItem.TempleRowItem(it)) }
                        }
                    }
                    currentCountry = temple.country
                    countryStartIndex = i
                }
            }
            if (currentCountry.isNotEmpty() && countryStartIndex < sortedTemples.size) {
                val itemsInLastCountry = sortedTemples.subList(countryStartIndex, sortedTemples.size)
                if (itemsInLastCountry.isNotEmpty()){
                    displayItems.add(DisplayListItem.HeaderItem(currentCountry, itemsInLastCountry.size))
                    itemsInLastCountry.forEach { displayItems.add(DisplayListItem.TempleRowItem(it)) }
                }
            }
        }
        PlaceSort.DEDICATION_DATE -> {
            // Your iOS code for "Eras" based on `templeOrder`
            // sortedTemples is already sorted by 'order' from applySort
            if (sortedTemples.isEmpty()) return emptyList()
            var currentEra = ""
            var eraStartIndex = 0

            val getEraForOrder: (Short) -> String = { order ->
                when (order) {
                    in 1..4 -> "Pioneer Era ~ 1877-1893"
                    in 5..12 -> "Expansion Era ~ 1919-1958"
                    in 13..20 -> "Strengthening Era ~ 1964-1981"
                    in 21..53 -> "Growth Era ~ 1983-1998"
                    in 54..114 -> "Explosive Era ~ 1999-2002"
                    in 115..161 -> "Hastening Era ~ 2003-2018"
                    // else -> "Unparalleled Era ~ 2019-Present" // Handle current year if needed
                    else -> {
                        val currentYear = java.time.Year.now().value // Requires API 26 for java.time.Year
                        "Unparalleled Era ~ 2019-$currentYear"
                    }
                }
            }

            for (i in sortedTemples.indices) {
                val temple = sortedTemples[i]
                val era = getEraForOrder(temple.order ?: 0) // Handle null order safely

                if (era != currentEra) {
                    if (currentEra.isNotEmpty()) {
                        val itemsInEra = sortedTemples.subList(eraStartIndex, i)
                        if (itemsInEra.isNotEmpty()) {
                            displayItems.add(DisplayListItem.HeaderItem(currentEra, itemsInEra.size))
                            itemsInEra.forEach { displayItems.add(DisplayListItem.TempleRowItem(it)) }
                        }
                    }
                    currentEra = era
                    eraStartIndex = i
                }
            }
            // Add the last era's group
            if (currentEra.isNotEmpty() && eraStartIndex < sortedTemples.size) {
                val itemsInLastEra = sortedTemples.subList(eraStartIndex, sortedTemples.size)
                if (itemsInLastEra.isNotEmpty()) {
                    displayItems.add(DisplayListItem.HeaderItem(currentEra, itemsInLastEra.size))
                    itemsInLastEra.forEach { displayItems.add(DisplayListItem.TempleRowItem(it)) }
                }
            }
        }
        PlaceSort.SIZE -> {
            // Your iOS code for size categories based on `templeSqFt`
            // sortedTemples is already sorted by sqFt (desc) from applySort
            if (sortedTemples.isEmpty()) return emptyList()
            Log.d("SharedVM_Headers", "Size sort selected. Preparing items with size prefix in snippet.")
            var currentSizeCategory = ""
            var categoryStartIndex = 0

            val getSizeCategory: (Int?) -> String = { sqFt ->
                when (sqFt) {
                    null -> "Unknown Size" // Or handle as smallest/largest
                    in 100000..Int.MAX_VALUE -> "Over 100K sqft"
                    in 60000..99999 -> "60K - 100K sqft"
                    in 30000..59999 -> "30K - 60K sqft"
                    in 12000..29999 -> "12K - 30K sqft"
                    else -> "Under 12K sqft"
                }
            }

            // Helper function for creating the modified TempleRowItem
            fun createModifiedTempleRowItem(temple: Temple): DisplayListItem.TempleRowItem {
                val formattedSqFt = temple.sqFt?.let { String.format("%,d sq ft", it) } ?: "Unknown size"
                // temple.snippet will be an empty string if null originally, thanks to your default value.
                val newSnippet = "$formattedSqFt - ${temple.snippet}".trimEnd(' ', '-')
                val templeForDisplay = temple.copy(snippet = newSnippet)
                return DisplayListItem.TempleRowItem(templeForDisplay)
            }

            for (i in sortedTemples.indices) {
                val temple = sortedTemples[i]
                val category = getSizeCategory(temple.sqFt)

                if (category != currentSizeCategory) {
                    if (currentSizeCategory.isNotEmpty()) {
                        val itemsInCategory = sortedTemples.subList(categoryStartIndex, i)
                        if (itemsInCategory.isNotEmpty()) {
                            displayItems.add(DisplayListItem.HeaderItem(currentSizeCategory, itemsInCategory.size))
                            itemsInCategory.forEach { item -> displayItems.add(createModifiedTempleRowItem(item)) }
                        }
                    }
                    currentSizeCategory = category
                    categoryStartIndex = i
                }
            }
            if (currentSizeCategory.isNotEmpty() && categoryStartIndex < sortedTemples.size) {
                val itemsInLastCategory = sortedTemples.subList(categoryStartIndex, sortedTemples.size)
                if (itemsInLastCategory.isNotEmpty()) {
                    displayItems.add(DisplayListItem.HeaderItem(currentSizeCategory, itemsInLastCategory.size))
                    itemsInLastCategory.forEach { displayItems.add(DisplayListItem.TempleRowItem(it)) }
                }
            }
        }
        PlaceSort.ANNOUNCED_DATE -> {
            // Your iOS code for grouping by formatted announced date string
            // sortedTemples is already sorted by announcedDate (desc) from applySort
            if (sortedTemples.isEmpty()) return emptyList()
            var currentDateString = ""
            var dateStartIndex = 0
            // Requires API 26. If lower, use java.text.SimpleDateFormat
            val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.getDefault())

            for (i in sortedTemples.indices) {
                val temple = sortedTemples[i]
                val dateString = temple.announcedDate?.format(formatter) ?: "Date Unknown"

                if (dateString != currentDateString) {
                    if (currentDateString.isNotEmpty() && !currentDateString.equals("Date Unknown", ignoreCase = true)) { // Don't group "Date Unknown" items under a single header unless intended
                        val itemsForDate = sortedTemples.subList(dateStartIndex, i)
                        if (itemsForDate.isNotEmpty()) {
                            displayItems.add(DisplayListItem.HeaderItem(currentDateString, itemsForDate.size))
                            itemsForDate.forEach { displayItems.add(DisplayListItem.TempleRowItem(it)) }
                        }
                    }
                    currentDateString = dateString
                    dateStartIndex = i
                }
            }
            if (currentDateString.isNotEmpty() && dateStartIndex < sortedTemples.size && !currentDateString.equals("Date Unknown", ignoreCase = true)) {
                val itemsForLastDate = sortedTemples.subList(dateStartIndex, sortedTemples.size)
                if (itemsForLastDate.isNotEmpty()){
                    displayItems.add(DisplayListItem.HeaderItem(currentDateString, itemsForLastDate.size))
                    itemsForLastDate.forEach { displayItems.add(DisplayListItem.TempleRowItem(it)) }
                }
            } else if (currentDateString.equals("Date Unknown", ignoreCase = true) && dateStartIndex < sortedTemples.size) {
                // Handle all "Date Unknown" items - perhaps add them without a header, or a specific "Unknown Date" header
                sortedTemples.subList(dateStartIndex, sortedTemples.size).forEach {
                    displayItems.add(DisplayListItem.TempleRowItem(it))
                }
            }
        }
        PlaceSort.NEAREST -> {
            // Typically, "Nearest" sort in your iOS app did not have internal section headers.
            // So, we just convert the sortedTemples to TempleRowItems without headers.
            sortedTemples.forEach { temple ->
                displayItems.add(DisplayListItem.TempleRowItem(temple))
            }
        }
    }
    return displayItems
}
    fun setFilter(filterType: PlaceFilter) {
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
