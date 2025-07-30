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

class VisitViewModel(application: Application) : AndroidViewModel(application) {

    private val visitDao: VisitDao

    // LiveData to hold all visits for now.
    // Later, this will be combined with filters and sorting from SharedVisitsViewModel.
    val allVisits: LiveData<List<Visit>>

    // TODO: Expose LiveData for loading state and empty state if needed.

    init {
        val database = AppDatabase.getDatabase(application)
        visitDao = database.visitDao()
        allVisits = visitDao.getAllVisits().asLiveData() // Assuming getAllVisits() returns Flow<List<Visit>>
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
