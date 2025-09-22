package net.dacworld.android.holyplacesofthelord.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
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
        .map { visit ->
            if (visit != null) {
                Log.d("VisitDetailViewModel", "Retrieved visit from DB: ${visit.holyPlaceName}")
                Log.d("VisitDetailViewModel", "Photo data present: ${visit.picture != null}")
                Log.d("VisitDetailViewModel", "Photo data size: ${visit.picture?.size ?: 0} bytes")
                Log.d("VisitDetailViewModel", "hasPicture flag: ${visit.hasPicture}")
                
                if (visit.picture != null && visit.picture.isNotEmpty()) {
                    val firstBytes = visit.picture.take(10).joinToString(" ") { "%02X".format(it) }
                    Log.d("VisitDetailViewModel", "Photo data header (first 10 bytes): $firstBytes")
                }
            }
            visit
        }
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