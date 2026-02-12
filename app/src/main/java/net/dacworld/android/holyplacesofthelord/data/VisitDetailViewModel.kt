package net.dacworld.android.holyplacesofthelord.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.model.Visit

class VisitDetailViewModel(
    visitDao: VisitDao,
    templeDao: TempleDao,
    visitId: Long
) : ViewModel() {

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
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val temple: StateFlow<Temple?> = visit
        .flatMapLatest { v ->
            flow {
                emit(if (v != null) templeDao.getTempleWithPictureById(v.placeID) else null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}