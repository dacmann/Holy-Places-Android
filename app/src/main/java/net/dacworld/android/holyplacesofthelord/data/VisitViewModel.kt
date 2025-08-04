// data/VisitViewModel.kt
package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter
import net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder
import androidx.lifecycle.MediatorLiveData

class VisitViewModel(application: Application) : AndroidViewModel(application) {

    private val visitDao: VisitDao

    private val rawVisitsFromDB: LiveData<List<Visit>>

    val allVisits: MediatorLiveData<List<Visit>> = MediatorLiveData()
    private var currentSortOrder: VisitSortOrder = VisitSortOrder.BY_DATE_DESC

    private var currentPlaceTypeFilter: VisitPlaceTypeFilter = VisitPlaceTypeFilter.ALL // Default to ALL


    init {
        val database = AppDatabase.getDatabase(application)
        visitDao = database.visitDao() // visitDao is initialized FIRST

        // NOW rawVisitsFromDB can be initialized, as visitDao is ready.
        rawVisitsFromDB = visitDao.getAllVisits().asLiveData()

        allVisits.addSource(rawVisitsFromDB) { visitsList ->
            allVisits.value = applyFilterAndSort(visitsList, currentPlaceTypeFilter, currentSortOrder)
        }
    }

    fun updateSortOrder(newSortOrder: VisitSortOrder) {
        if (newSortOrder != currentSortOrder) {
            currentSortOrder = newSortOrder
            rawVisitsFromDB.value?.let { visitsList ->
                allVisits.value = applyFilterAndSort(visitsList, currentPlaceTypeFilter, currentSortOrder)
            }
        }
    }

    // --- NEW: Method to be called by VisitsFragment when the filter changes ---
    fun updatePlaceTypeFilter(newFilter: VisitPlaceTypeFilter) {
        if (newFilter != currentPlaceTypeFilter) {
            currentPlaceTypeFilter = newFilter
            // Re-apply filter and sort to the current raw data
            rawVisitsFromDB.value?.let { visitsList ->
                allVisits.value = applyFilterAndSort(visitsList, currentPlaceTypeFilter, currentSortOrder)
            }
        }
    }

    // --- RENAMED & MODIFIED: This function now handles both filtering and sorting ---
    private fun applyFilterAndSort(
        visits: List<Visit>?,
        filterType: VisitPlaceTypeFilter,
        sortOrder: VisitSortOrder
    ): List<Visit> {
        val currentVisits = visits ?: return emptyList()

        // 1. Apply Filtering
        val filteredVisits = if (filterType == VisitPlaceTypeFilter.ALL || filterType.typeCode == null) {
            currentVisits // No type filter applied
        } else {
            currentVisits.filter { visit ->
                visit.type == filterType.typeCode
            }
        }

        // 2. Apply Sorting (to the filtered list)
        return when (sortOrder) {
            VisitSortOrder.BY_DATE_DESC ->
                filteredVisits.sortedWith(compareByDescending { visit: Visit -> visit.dateVisited }
                    .thenByDescending { visit: Visit -> visit.id })
            VisitSortOrder.BY_DATE_ASC ->
                filteredVisits.sortedWith(compareBy { visit: Visit -> visit.dateVisited }
                    .thenBy { visit: Visit -> visit.id })
            VisitSortOrder.BY_PLACE_NAME_ASC ->
                filteredVisits.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { visit: Visit -> visit.holyPlaceName ?: "" }
                    .thenByDescending { visit: Visit -> visit.dateVisited })
            VisitSortOrder.BY_PLACE_NAME_DESC ->
                filteredVisits.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { visit: Visit -> visit.holyPlaceName ?: "" }
                    .thenByDescending { visit: Visit -> visit.dateVisited })
        }
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
}
