package net.dacworld.android.holyplacesofthelord.ui.placedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.data.ProfileRepository
import net.dacworld.android.holyplacesofthelord.model.VisitPhoto

/**
 * Totals across every visit to a place, scoped to the active profile.
 */
data class PlaceVisitSummary(
    val visitCount: Int = 0,
    val baptisms: Int = 0,
    val confirmations: Int = 0,
    val initiatories: Int = 0,
    val endowments: Int = 0,
    val sealings: Int = 0,
    val hoursWorked: Double = 0.0
) {
    val hasAnyOrdinances: Boolean
        get() = baptisms > 0 || confirmations > 0 || initiatories > 0 ||
            endowments > 0 || sealings > 0
}

/**
 * Backs the visit count, ordinance breakdown, and visit photos on place details.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceDetailViewModel(
    private val visitDao: VisitDao,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val templeId = MutableStateFlow<String?>(null)

    val visitSummary: StateFlow<PlaceVisitSummary> =
        combine(templeId, profileRepository.scopedProfileId) { id, profileId -> id to profileId }
            .flatMapLatest { (id, profileId) ->
                if (id.isNullOrEmpty()) {
                    flowOf(emptyList())
                } else {
                    visitDao.getVisitsForTempleByProfile(id, profileId)
                }
            }
            .map { visits ->
                PlaceVisitSummary(
                    visitCount = visits.size,
                    baptisms = visits.sumOf { it.baptisms?.toInt() ?: 0 },
                    confirmations = visits.sumOf { it.confirmations?.toInt() ?: 0 },
                    initiatories = visits.sumOf { it.initiatories?.toInt() ?: 0 },
                    endowments = visits.sumOf { it.endowments?.toInt() ?: 0 },
                    sealings = visits.sumOf { it.sealings?.toInt() ?: 0 },
                    hoursWorked = visits.sumOf { it.shiftHrs ?: 0.0 }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaceVisitSummary())

    fun setTempleId(id: String) {
        if (templeId.value != id) {
            selectedPhotoIndex = 0
        }
        templeId.value = id
    }

    /** Restored after the photo pager is rebuilt (for example, returning from the image viewer). */
    var selectedPhotoIndex: Int = 0

    /**
     * The newest visit photos for the given place, capped at [MAX_VISIT_PHOTOS] as on iOS.
     */
    suspend fun loadVisitPhotos(id: String): List<VisitPhoto> {
        if (id.isEmpty()) return emptyList()
        val profileId = profileRepository.scopedProfileId.first()
        return visitDao.getVisitPhotosForTemple(id, profileId, MAX_VISIT_PHOTOS)
    }

    companion object {
        const val MAX_VISIT_PHOTOS = 20
    }
}

class PlaceDetailViewModelFactory(
    private val visitDao: VisitDao,
    private val profileRepository: ProfileRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaceDetailViewModel::class.java)) {
            return PlaceDetailViewModel(visitDao, profileRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
