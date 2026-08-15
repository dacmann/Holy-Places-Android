package net.dacworld.android.holyplacesofthelord.ui.visits

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.util.OtherPlaceHelper

enum class PlacePickerCategory {
    TEMPLE, HISTORIC, VISITORS, OTHER
}

data class SelectedPlace(
    val placeId: String,
    val placeName: String,
    val placeType: String
)

data class PlacePickerUiState(
    val category: PlacePickerCategory = PlacePickerCategory.TEMPLE,
    val searchQuery: String = "",
    val closestEnabled: Boolean = false,
    val places: List<Temple> = emptyList(),
    val selectedPlaceId: String? = null,
    val otherName: String = "",
    val isChangingPlace: Boolean = false
) {
    val isOther: Boolean get() = category == PlacePickerCategory.OTHER
    val canConfirm: Boolean
        get() = if (isOther) otherName.trim().isNotEmpty() else selectedPlaceId != null
}

class AddVisitPlacePickerViewModel(
    application: Application,
    private val isChangingPlace: Boolean,
    private val currentPlaceId: String,
    private val currentPlaceName: String,
    private val currentPlaceType: String,
    private val userPreferencesManager: UserPreferencesManager
) : AndroidViewModel(application) {

    private val templeDao = AppDatabase.getDatabase(application).templeDao()
    private val nameChangeDao = AppDatabase.getDatabase(application).nameChangeDao()

    private val _uiState = MutableStateFlow(PlacePickerUiState(isChangingPlace = isChangingPlace))
    val uiState: StateFlow<PlacePickerUiState> = _uiState.asStateFlow()

    private var allPlaces: List<Temple> = emptyList()
    private var deviceLocation: Location? = null

    init {
        viewModelScope.launch {
            val closest = userPreferencesManager.addVisitClosestPlaceFlow.first()
            allPlaces = templeDao.getAllTemplesForSyncOrList()
                .filter { !OtherPlaceHelper.isOtherType(it.type) && !OtherPlaceHelper.isOtherId(it.id) }
            val initialCategory = categoryForType(currentPlaceType)
            _uiState.update {
                it.copy(
                    closestEnabled = closest,
                    category = if (isChangingPlace) initialCategory else PlacePickerCategory.TEMPLE,
                    otherName = if (isChangingPlace && initialCategory == PlacePickerCategory.OTHER) currentPlaceName else ""
                )
            }
            if (isChangingPlace) {
                resolveCurrentSelection()
            }
            applyFilters()
        }
    }

    fun setCategory(category: PlacePickerCategory) {
        _uiState.update { it.copy(category = category, searchQuery = "") }
        applyFilters()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setClosestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesManager.saveAddVisitClosestPlace(enabled)
        }
        _uiState.update { it.copy(closestEnabled = enabled) }
        applyFilters()
    }

    fun setDeviceLocation(location: Location?) {
        val previous = deviceLocation
        if (previous == null && location == null) return
        if (previous != null && location != null && previous.distanceTo(location) < 25f) {
            deviceLocation = location
            return
        }
        deviceLocation = location
        if (_uiState.value.closestEnabled) {
            applyFilters()
        }
    }

    fun selectPlace(placeId: String) {
        _uiState.update { it.copy(selectedPlaceId = placeId) }
    }

    fun setOtherName(name: String) {
        _uiState.update { it.copy(otherName = name) }
    }

    fun confirmedSelection(): SelectedPlace? {
        val state = _uiState.value
        return if (state.isOther) {
            val name = state.otherName.trim()
            if (name.isEmpty()) null
            else SelectedPlace(
                placeId = OtherPlaceHelper.idForName(name),
                placeName = name,
                placeType = OtherPlaceHelper.TYPE
            )
        } else {
            val selected = state.places.firstOrNull { it.id == state.selectedPlaceId } ?: return null
            SelectedPlace(
                placeId = selected.id,
                placeName = selected.name,
                placeType = selected.type
            )
        }
    }

    private suspend fun resolveCurrentSelection() {
        if (currentPlaceId.isNotBlank()) {
            val match = allPlaces.firstOrNull { it.id == currentPlaceId }
            if (match != null) {
                _uiState.update { it.copy(selectedPlaceId = match.id, category = categoryForType(match.type)) }
                return
            }
            if (OtherPlaceHelper.isOtherId(currentPlaceId) || OtherPlaceHelper.isOtherType(currentPlaceType)) {
                _uiState.update {
                    it.copy(
                        category = PlacePickerCategory.OTHER,
                        otherName = currentPlaceName
                    )
                }
                return
            }
        }
        if (currentPlaceName.isNotBlank()) {
            val byName = allPlaces.firstOrNull { it.name.equals(currentPlaceName, ignoreCase = true) }
            if (byName != null) {
                _uiState.update { it.copy(selectedPlaceId = byName.id, category = categoryForType(byName.type)) }
                return
            }
            val oldName = nameChangeDao.getByOldName(currentPlaceName)
            if (oldName != null) {
                _uiState.update {
                    it.copy(
                        selectedPlaceId = oldName.templeId,
                        category = categoryForType(currentPlaceType)
                    )
                }
            }
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        if (state.category == PlacePickerCategory.OTHER) {
            _uiState.update { it.copy(places = emptyList()) }
            return
        }
        val types = typesFor(state.category)
        var filtered = allPlaces.filter { it.type in types }
        val query = state.searchQuery.trim()
        if (query.isNotEmpty()) {
            filtered = filtered.filter { it.name.contains(query, ignoreCase = true) }
        }
        filtered = if (state.closestEnabled && deviceLocation != null) {
            filtered.map { temple ->
                temple.setDistanceInMeters(deviceLocation)
                temple
            }.sortedWith(compareBy(nullsLast()) { it.distance })
        } else {
            filtered.sortedBy { it.name }
        }
        val selectedId = when {
            state.selectedPlaceId != null && filtered.any { it.id == state.selectedPlaceId } -> state.selectedPlaceId
            filtered.isNotEmpty() -> filtered.first().id
            else -> null
        }
        _uiState.update { it.copy(places = filtered, selectedPlaceId = selectedId) }
    }

    private fun typesFor(category: PlacePickerCategory): Set<String> = when (category) {
        PlacePickerCategory.TEMPLE -> setOf("T", "A", "C")
        PlacePickerCategory.HISTORIC -> setOf("H")
        PlacePickerCategory.VISITORS -> setOf("V")
        PlacePickerCategory.OTHER -> emptySet()
    }

    private fun categoryForType(type: String): PlacePickerCategory = when (type) {
        "H" -> PlacePickerCategory.HISTORIC
        "V" -> PlacePickerCategory.VISITORS
        OtherPlaceHelper.TYPE -> PlacePickerCategory.OTHER
        else -> PlacePickerCategory.TEMPLE
    }
}

class AddVisitPlacePickerViewModelFactory(
    private val application: Application,
    private val isChangingPlace: Boolean,
    private val currentPlaceId: String,
    private val currentPlaceName: String,
    private val currentPlaceType: String,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddVisitPlacePickerViewModel::class.java)) {
            return AddVisitPlacePickerViewModel(
                application,
                isChangingPlace,
                currentPlaceId,
                currentPlaceName,
                currentPlaceType,
                userPreferencesManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
