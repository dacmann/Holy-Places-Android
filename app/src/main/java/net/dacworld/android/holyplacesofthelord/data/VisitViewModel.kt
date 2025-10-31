// data/VisitViewModel.kt
package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
//import android.icu.util.Calendar
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter
import net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder
import androidx.lifecycle.MediatorLiveData
import java.util.Calendar
import kotlin.text.contains
import net.dacworld.android.holyplacesofthelord.util.SearchHelper

class VisitViewModel(application: Application) : AndroidViewModel(application) {

    private val visitDao: VisitDao
    private val preferencesManager: UserPreferencesManager = UserPreferencesManager.getInstance(application)
    private val rawVisitsFromDB: LiveData<List<Visit>>
    val allVisits: MediatorLiveData<List<VisitDisplayListItem>> = MediatorLiveData()
    private var currentSortOrder: VisitSortOrder = VisitSortOrder.BY_DATE_DESC
    private var currentPlaceTypeFilter: VisitPlaceTypeFilter = VisitPlaceTypeFilter.ALL // Default to ALL
    private var currentSearchQuery: String = ""

    // Backup reminder state
    private val _shouldShowBackupReminder = MutableLiveData<Boolean>()
    val shouldShowBackupReminder: LiveData<Boolean> = _shouldShowBackupReminder
    private var backupReminderShownThisSession = false

    init {
        val database = AppDatabase.getDatabase(application)
        visitDao = database.visitDao() // visitDao is initialized FIRST

        // NOW rawVisitsFromDB can be initialized, as visitDao is ready.
        rawVisitsFromDB = visitDao.getVisitsForListAdapter().asLiveData()

        allVisits.addSource(rawVisitsFromDB) { visitsList ->
            // Pass the currentSearchQuery to the transformation function
            allVisits.value = transformToDisplayListWithHeaders(
                visitsList,
                currentPlaceTypeFilter,
                currentSortOrder,
                currentSearchQuery // Pass current search query
            )
        }
    }

    // --- NEW: Method to update search query ---
    fun setSearchQuery(query: String?) {
        val newQuery = query?.trim() ?: "" // Normalize: trim and default null to empty
        if (newQuery != currentSearchQuery) {
            currentSearchQuery = newQuery
            // Re-apply filter, sort, and search to the current raw data
            rawVisitsFromDB.value?.let { visitsList ->
                allVisits.value = transformToDisplayListWithHeaders(
                    visitsList,
                    currentPlaceTypeFilter,
                    currentSortOrder,
                    currentSearchQuery // Pass current search query
                )
            }
        }
    }
    fun updateSortOrder(newSortOrder: VisitSortOrder) {
        if (newSortOrder != currentSortOrder) {
            currentSortOrder = newSortOrder
            rawVisitsFromDB.value?.let { visitsList ->
                allVisits.value = transformToDisplayListWithHeaders(
                    visitsList,
                    currentPlaceTypeFilter,
                    currentSortOrder,
                    currentSearchQuery // ADD currentSearchQuery HERE
                )}
        }
    }

    // --- NEW: Method to be called by VisitsFragment when the filter changes ---
    fun updatePlaceTypeFilter(newFilter: VisitPlaceTypeFilter) {
        if (newFilter != currentPlaceTypeFilter) {
            currentPlaceTypeFilter = newFilter
            // Re-apply filter and sort to the current raw data
            rawVisitsFromDB.value?.let { visitsList ->
                allVisits.value = transformToDisplayListWithHeaders(
                    visitsList,
                    currentPlaceTypeFilter,
                    currentSortOrder,
                    currentSearchQuery // ADD currentSearchQuery HERE
                )
            }
        }
    }

    // --- RENAMED & MODIFIED: This function now handles both filtering and sorting ---
    private fun transformToDisplayListWithHeaders(
        visits: List<Visit>?,
        filterType: VisitPlaceTypeFilter,
        sortOrder: VisitSortOrder,
        searchQuery: String
    ): List<VisitDisplayListItem> {
        val currentVisits = visits ?: return emptyList()

        // 1. Apply Search Query Filter FIRST (if query is not blank)
        val searchedVisits = if (searchQuery.isNotBlank()) {
            currentVisits.filter { visit ->
                SearchHelper.matchesAllTerms(
                    searchQuery,
                    listOf(visit.holyPlaceName, visit.comments)
                )
            }
        } else {
            currentVisits
        }

        // 2. Apply Place Type Filtering (on the result of the search filter)
        val filteredVisits = if (filterType == VisitPlaceTypeFilter.ALL || filterType.typeCode == null) {
            searchedVisits // Apply to already search-filtered list
        } else {
            searchedVisits.filter { visit -> // Apply to already search-filtered list
                visit.type == filterType.typeCode
            }
        }

        if (filteredVisits.isEmpty()) {
            return emptyList()
        }

        val displayListItems = mutableListOf<VisitDisplayListItem>()
        val calendar = Calendar.getInstance()

        // --- Conditional Grouping and Sorting ---
        when (sortOrder) {
            VisitSortOrder.BY_DATE_DESC, VisitSortOrder.BY_DATE_ASC -> {
                // Group by Year
                val visitsSortedByDate = filteredVisits.sortedWith(
                    if (sortOrder == VisitSortOrder.BY_DATE_DESC) {
                        compareByDescending<Visit> { it.dateVisited }
                            .thenByDescending { it.id }
                    } else { // BY_DATE_ASC
                        compareBy<Visit> { it.dateVisited }
                            .thenBy { it.id }
                    }
                )

                val groupedByYear = visitsSortedByDate
                    .filter { it.dateVisited != null }
                    .groupBy { visit ->
                        calendar.time = visit.dateVisited!!
                        calendar.get(Calendar.YEAR)
                    }

                val yearKeys = if (sortOrder == VisitSortOrder.BY_DATE_DESC) {
                    groupedByYear.keys.sortedDescending()
                } else { // BY_DATE_ASC
                    groupedByYear.keys.sorted()
                }

                for (year in yearKeys) {
                    val visitsInYear = groupedByYear[year] ?: continue
                    displayListItems.add(
                        VisitDisplayListItem.HeaderItem(
                            title = year.toString(),
                            count = visitsInYear.size
                        )
                    )
                    visitsInYear.forEach { visit -> // Already sorted correctly by date within the year
                        displayListItems.add(VisitDisplayListItem.VisitRowItem(visit))
                    }
                }

                // Handle visits with null dates for date-sorted lists
                val visitsWithNullDate = filteredVisits.filter { it.dateVisited == null }
                    .sortedByDescending { it.id } // Consistent order
                if (visitsWithNullDate.isNotEmpty()) {
                    if (displayListItems.isNotEmpty() || groupedByYear.isNotEmpty()) {
                        displayListItems.add(
                            VisitDisplayListItem.HeaderItem(
                                title = "Undated",
                                count = visitsWithNullDate.size
                            )
                        )
                    }
                    visitsWithNullDate.forEach { visit ->
                        displayListItems.add(VisitDisplayListItem.VisitRowItem(visit))
                    }
                }
            }

            VisitSortOrder.BY_PLACE_NAME_ASC, VisitSortOrder.BY_PLACE_NAME_DESC -> {
                // Group by Holy Place Name
                // Normalize empty/null place names to a consistent string for grouping
                val placeholderForNullOrEmptyName = "Unnamed Place"

                val visitsSortedByName = filteredVisits.sortedWith(
                    if (sortOrder == VisitSortOrder.BY_PLACE_NAME_ASC) {
                        compareBy(String.CASE_INSENSITIVE_ORDER) { visit: Visit -> visit.holyPlaceName ?: placeholderForNullOrEmptyName }
                            .thenByDescending { visit: Visit -> visit.dateVisited } // Secondary sort: newest visit first for that place
                            .thenByDescending { visit: Visit -> visit.id }
                    } else { // BY_PLACE_NAME_DESC
                        compareByDescending(String.CASE_INSENSITIVE_ORDER) { visit: Visit -> visit.holyPlaceName ?: placeholderForNullOrEmptyName }
                            .thenByDescending { visit: Visit -> visit.dateVisited } // Secondary sort: newest visit first for that place
                            .thenByDescending { visit: Visit -> visit.id }
                    }
                )

                // Group by the (potentially normalized) place name
                val groupedByPlaceName = visitsSortedByName.groupBy {
                    it.holyPlaceName?.takeIf { name -> name.isNotBlank() } ?: placeholderForNullOrEmptyName
                }

                // The keys of groupedByPlaceName will already be in the desired sort order (ASC/DESC)
                // because visitsSortedByName was sorted that way.
                // However, groupBy does not guarantee key order from the original list order,
                // so we must sort the keys based on the sortOrder.
                val placeNameKeys = if (sortOrder == VisitSortOrder.BY_PLACE_NAME_ASC) {
                    groupedByPlaceName.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
                } else { // BY_PLACE_NAME_DESC
                    groupedByPlaceName.keys.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it })
                }


                for (placeName in placeNameKeys) {
                    val visitsForPlace = groupedByPlaceName[placeName] ?: continue
                    displayListItems.add(
                        VisitDisplayListItem.HeaderItem(
                            title = placeName, // Use the actual place name (or placeholder) as header
                            count = visitsForPlace.size
                        )
                    )
                    // Visits within this group are already sorted by name then date by visitsSortedByName
                    visitsForPlace.forEach { visit ->
                        displayListItems.add(VisitDisplayListItem.VisitRowItem(visit))
                    }
                }
                // No special handling for "null date" needed here as grouping is by name.
                // Null dates are handled by the secondary sort (dateVisited descending).
            }
        }
        return displayListItems
    }

    fun insert(visit: Visit) = viewModelScope.launch {
        visitDao.insertVisit(visit)
    }

    fun update(visit: Visit) = viewModelScope.launch {
        visitDao.updateVisit(visit)
    }

    fun delete(visit: Visit) = viewModelScope.launch {
        visitDao.deleteVisit(visit)
    }

    fun deleteVisitById(visitId: Long) = viewModelScope.launch {
        visitDao.deleteVisitById(visitId)
    }

    // Add other business logic related to visits here if necessary

    // --- Backup Reminder Methods ---
    fun checkBackupReminderStatus() {
        // Don't show if already shown this session
        if (backupReminderShownThisSession) {
            _shouldShowBackupReminder.value = false
            return
        }

        viewModelScope.launch {
            // Check if user has at least 1 visit
            val visitCount = visitDao.getVisitCount()
            if (visitCount == 0) {
                _shouldShowBackupReminder.postValue(false)
                return@launch
            }

            val lastExportDate = preferencesManager.lastExportDateFlow.first()

            val shouldShow = if (lastExportDate == 0L) {
                // Never exported before
                true
            } else {
                // Check if more than 3 months have passed
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastExportDate
                timeDiff > UserPreferencesManager.THREE_MONTHS_MILLIS
            }

            _shouldShowBackupReminder.postValue(shouldShow)
        }
    }

    fun onBackupReminderShown() {
        backupReminderShownThisSession = true
        _shouldShowBackupReminder.value = false
    }

    fun onBackupReminderDismissed() {
        // Only dismiss for this session, will show again on next app launch
        backupReminderShownThisSession = true
        _shouldShowBackupReminder.value = false
    }
}

sealed interface VisitDisplayListItem {
    // A stable ID is good for DiffUtil, especially for items that might change position or content
    // while representing the "same" conceptual item (like a header for a specific year).
    val stableId: String

    data class HeaderItem(
        val title: String,
        val count: Int,
        // Generate a stable ID, e.g., based on the title.
        // Important if headers can be added/removed/reordered.
        override val stableId: String = "Header_$title"
    ) : VisitDisplayListItem

    data class VisitRowItem(
        val visit: Visit,
        // Use the visit's own ID if it's unique and stable.
        // Ensure visit.id is a String or convert it. If it's Long, make stableId Long.
        // For simplicity with the HeaderItem's stableId being String, let's assume conversion.
        override val stableId: String = "Visit_${visit.id}" // Make sure visit.id is accessible and suitable
    ) : VisitDisplayListItem
}