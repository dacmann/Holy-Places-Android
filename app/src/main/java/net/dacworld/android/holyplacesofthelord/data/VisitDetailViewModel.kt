package net.dacworld.android.holyplacesofthelord.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Visit

class VisitDetailViewModel(
    visitDao: VisitDao, // Inject VisitDao
    visitId: Long
) : ViewModel() {

    // Expose the visit as a StateFlow
    // The DAO's getVisitById(visitId) returns a Flow<Visit?>,
    // which we convert to a StateFlow.
    val visit: StateFlow<Visit?> = visitDao.getVisitById(visitId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep active for 5s after last observer stops
            initialValue = null // Initial value before the database query completes
        )

    // If you need to perform any other operations related to this specific visit,
    // they can be added here. For example, triggering an update or delete
    // would likely be handled by a different ViewModel (like your main VisitViewModel)
    // or a shared repository, depending on your architecture.
    // For now, this ViewModel's primary role is to provide the visit details.
}