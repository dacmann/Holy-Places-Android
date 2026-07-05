package net.dacworld.android.holyplacesofthelord.data

import android.util.Log // Added import for Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.dao.NameChangeDao
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.model.TempleNameChange
import net.dacworld.android.holyplacesofthelord.model.effectiveName
import org.maplibre.android.camera.CameraPosition
import java.time.LocalDate

// MapPlace data class remains the same (as it was in your provided code)
data class MapPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val isVisited: Boolean,
    val address: String? = null
)

data class MapCameraState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double? = null, // Optional
    val tilt: Double? = null      // Optional
)

enum class TempleFilterType(val typeKey: String?) {
    ALL(null), // null typeKey means no type-specific filtering
    ACTIVE_TEMPLES("T"),
    HISTORICAL_SITES("H"),
    VISITORS_CENTERS("V"),
    UNDER_CONSTRUCTION("C"),
    ANNOUNCED("A");
}

/** One dedication event shown on the Map Timeline. */
private data class TimelineEntry(
    val id: String,
    val currentName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val dedicatedDate: LocalDate,
    val nameChanges: List<TempleNameChange> = emptyList(),
    /** Fixed display name overriding effectiveName (used by injected pre-1877 pins). */
    val fixedName: String? = null,
    /** True for the injected original (1846) Nauvoo pin — hidden once the modern temple appears. */
    val isOriginalNauvoo: Boolean = false
)

class MapViewModel(
    private val templeDao: TempleDao,
    private val visitDao: VisitDao,
    private val nameChangeDao: NameChangeDao,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _mapPlaces = MutableLiveData<List<MapPlace>>()
    val mapPlaces: LiveData<List<MapPlace>> get() = _mapPlaces

    // LiveData to hold the last known camera state
    private val _lastCameraState = MutableLiveData<MapCameraState?>()
    val lastCameraState: LiveData<MapCameraState?> = _lastCameraState

    private val _currentFilterType = MutableLiveData<TempleFilterType>(TempleFilterType.ALL)
    fun saveCameraState(cameraPosition: CameraPosition) {
        val newState = MapCameraState(
            latitude = cameraPosition.target?.latitude ?:40.770468,
            longitude = cameraPosition.target?.longitude ?:-111.891958,
            zoom = cameraPosition.zoom,
            bearing = cameraPosition.bearing,
            tilt = cameraPosition.tilt
        )
        _lastCameraState.value = newState
        Log.d("MapViewModel", "Saved camera state: $newState")
    }

    fun clearLastCameraState() {
        _lastCameraState.value = null
        Log.d("MapViewModel", "Cleared last camera state.")
    }
    // Companion object for TAG
    companion object {
        private const val TAG = "MapViewModel"
    }

    init {
        Log.d(TAG, "MapViewModel instance created.")
        viewModelScope.launch {
            userPreferencesManager.activeProfileIdFlow.collectLatest {
                loadAndFilterPlaces()
            }
        }
    }

    fun setFilter(filterType: TempleFilterType) {
        // Only reload if the filter has actually changed
        if (_currentFilterType.value != filterType) {
            _currentFilterType.value = filterType
            Log.d(TAG, "Filter changed to: $filterType. Reloading places.")
            loadAndFilterPlaces() // Trigger data reload and filtering
        } else {
            Log.d(TAG, "Filter is already $filterType. No change needed.")
        }
    }

    // ===================== Map Timeline (iOS 5.6/5.7 parity) =====================

    private val _isTimelineActive = MutableLiveData(false)
    val isTimelineActive: LiveData<Boolean> = _isTimelineActive

    private val _timelineYear = MutableLiveData<Int>()
    val timelineYear: LiveData<Int> = _timelineYear

    private val _timelineYearRange = MutableLiveData<Pair<Int, Int>>() // min to max
    val timelineYearRange: LiveData<Pair<Int, Int>> = _timelineYearRange

    private val _timelineVisibleCount = MutableLiveData(0)
    val timelineVisibleCount: LiveData<Int> = _timelineVisibleCount

    private val _isTimelinePlaying = MutableLiveData(false)
    val isTimelinePlaying: LiveData<Boolean> = _isTimelinePlaying

    private var timelineEntries: List<TimelineEntry> = emptyList()
    private var sortedDedicationYears: List<Int> = emptyList()
    private var playbackJob: Job? = null

    /** Modern Nauvoo dedication year — original Nauvoo pin is hidden from then on. */
    private var modernNauvooYear: Int? = null

    fun startTimeline() {
        if (_isTimelineActive.value == true) return
        viewModelScope.launch {
            try {
                buildTimelineEntries()
                if (timelineEntries.isEmpty()) {
                    Log.w(TAG, "Timeline has no dedication data; not activating.")
                    return@launch
                }
                val years = timelineEntries.map { it.dedicatedDate.year }.distinct().sorted()
                sortedDedicationYears = years
                _timelineYearRange.value = Pair(years.first(), years.last())
                _isTimelineActive.value = true
                // Start at the beginning of the timeline (Kirtland, 1836)
                setTimelineYear(years.first(), force = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting timeline: ${e.message}", e)
            }
        }
    }

    fun stopTimeline() {
        if (_isTimelineActive.value != true) return
        pauseTimeline()
        _isTimelineActive.value = false
        timelineEntries = emptyList()
        sortedDedicationYears = emptyList()
        // Restore the regular filtered pins
        loadAndFilterPlaces()
    }

    fun setTimelineYear(year: Int, force: Boolean = false) {
        if (_isTimelineActive.value != true) return
        val clamped = _timelineYearRange.value?.let { (min, max) -> year.coerceIn(min, max) } ?: year
        if (!force && _timelineYear.value == clamped) return
        _timelineYear.value = clamped
        rebuildTimelinePins(clamped)
    }

    fun nextTimelineYear() {
        val current = _timelineYear.value ?: return
        sortedDedicationYears.firstOrNull { it > current }?.let { setTimelineYear(it) }
    }

    fun previousTimelineYear() {
        val current = _timelineYear.value ?: return
        sortedDedicationYears.lastOrNull { it < current }?.let { setTimelineYear(it) }
    }

    /**
     * Animates the slider from its current position (restarting from the
     * beginning when already at the end) to the last year over ~20 seconds.
     */
    fun playTimeline() {
        if (_isTimelineActive.value != true || _isTimelinePlaying.value == true) return
        val range = _timelineYearRange.value ?: return
        val (minYear, maxYear) = range
        val startYear = _timelineYear.value?.takeIf { it < maxYear } ?: minYear
        _isTimelinePlaying.value = true
        playbackJob = viewModelScope.launch {
            val totalTicks = 400
            val tickMs = 50L // 400 x 50ms = ~20 seconds
            val span = (maxYear - minYear).coerceAtLeast(1)
            val startFraction = (startYear - minYear).toFloat() / span
            val startTick = (startFraction * totalTicks).toInt()
            try {
                for (tick in startTick..totalTicks) {
                    if (!isActive) break
                    val year = minYear + ((tick.toFloat() / totalTicks) * span).toInt()
                    if (year != _timelineYear.value) {
                        setTimelineYear(year)
                    }
                    delay(tickMs)
                }
            } finally {
                _isTimelinePlaying.postValue(false)
            }
        }
    }

    fun pauseTimeline() {
        playbackJob?.cancel()
        playbackJob = null
        _isTimelinePlaying.value = false
    }

    private suspend fun buildTimelineEntries() {
        val allTemples = templeDao.getAllTemplesForSyncOrList()
        val changesByTemple = nameChangeDao.getAllNameChanges().groupBy { it.templeId }
        val entries = mutableListOf<TimelineEntry>()

        // Active temples with a parsed dedication date
        for (temple in allTemples) {
            if (temple.type != "T") continue
            val dedicated = temple.dedicatedDate ?: continue
            entries.add(
                TimelineEntry(
                    id = temple.id,
                    currentName = temple.name,
                    latitude = temple.latitude,
                    longitude = temple.longitude,
                    type = temple.type,
                    dedicatedDate = dedicated,
                    nameChanges = changesByTemple[temple.id] ?: emptyList()
                )
            )
        }

        // Kirtland Temple (historical, type H) — dedicated 27 March 1836
        allTemples.firstOrNull { it.type == "H" && it.name.contains("Kirtland Temple", ignoreCase = true) }
            ?.let { kirtland ->
                entries.add(
                    TimelineEntry(
                        id = kirtland.id,
                        currentName = kirtland.name,
                        latitude = kirtland.latitude,
                        longitude = kirtland.longitude,
                        type = "H",
                        dedicatedDate = LocalDate.of(1836, 3, 27),
                        fixedName = kirtland.name
                    )
                )
            }

        // Original Nauvoo Temple — dedicated 1 May 1846, at the modern temple's site
        val modernNauvoo = allTemples.firstOrNull { it.type == "T" && it.name.contains("Nauvoo", ignoreCase = true) }
        if (modernNauvoo != null) {
            modernNauvooYear = modernNauvoo.dedicatedDate?.year
            entries.add(
                TimelineEntry(
                    id = modernNauvoo.id,
                    currentName = "Nauvoo Temple",
                    latitude = modernNauvoo.latitude,
                    longitude = modernNauvoo.longitude,
                    type = "H",
                    dedicatedDate = LocalDate.of(1846, 5, 1),
                    fixedName = "Nauvoo Temple",
                    isOriginalNauvoo = true
                )
            )
        }

        timelineEntries = entries
        Log.d(TAG, "Timeline built with ${entries.size} dedication entries.")
    }

    private fun rebuildTimelinePins(cutoffYear: Int) {
        // Names reflect what the place was called at the end of the slider year
        val sliderDate = LocalDate.of(cutoffYear, 12, 31)
        val modernNauvooVisible = modernNauvooYear?.let { cutoffYear >= it } ?: false

        val visible = timelineEntries.filter { entry ->
            if (entry.dedicatedDate.year > cutoffYear) return@filter false
            // Hide the injected original Nauvoo pin once the modern temple appears
            if (entry.isOriginalNauvoo && modernNauvooVisible) return@filter false
            true
        }

        val pins = visible.map { entry ->
            MapPlace(
                id = entry.id,
                name = entry.fixedName ?: entry.nameChanges.effectiveName(entry.currentName, sliderDate),
                latitude = entry.latitude,
                longitude = entry.longitude,
                type = entry.type,
                isVisited = false
            )
        }
        _timelineVisibleCount.value = pins.size
        _mapPlaces.value = pins
    }

    // ===================== End Map Timeline =====================

    private fun loadAndFilterPlaces() {
        if (_isTimelineActive.value == true) {
            Log.d(TAG, "loadAndFilterPlaces skipped: timeline is active.")
            return
        }
        val currentFilter = _currentFilterType.value ?: TempleFilterType.ALL // Get current filter, default to ALL
        Log.d(TAG, "loadAndFilterPlaces called. Current filter: ${currentFilter.name}")

        viewModelScope.launch {
            try {
                // Fetch all temples. The filtering will happen in Kotlin.
                val allTemplesList: List<Temple> = templeDao.getAllTemplesForSyncOrList()
                Log.d(TAG, "Fetched ${allTemplesList.size} total temples from DAO.")

                // Apply filtering based on currentFilter.typeKey
                val templesToProcess = if (currentFilter == TempleFilterType.ALL || currentFilter.typeKey == null) {
                    allTemplesList // No type-specific filtering
                } else {
                    allTemplesList.filter { temple -> temple.type == currentFilter.typeKey }
                }
                Log.d(TAG, "Processing ${templesToProcess.size} temples after applying filter '${currentFilter.name}'.")

                val profileId = userPreferencesManager.activeProfileIdFlow.first()
                val visitedTempleIds = visitDao.getVisitedTemplePlaceIdsByProfile(profileId).first().toSet()
                Log.d(TAG, "Fetched ${visitedTempleIds.size} visited place IDs for profile $profileId.")

                val resultingMapPlaces = templesToProcess.mapNotNull { temple ->
                    if (temple.latitude == null || temple.longitude == null) {
                        Log.w(TAG, "Skipping temple (ID: ${temple.id}, Name: ${temple.name}) due to null coordinates.")
                        null // Skip if coordinates are null
                    } else {
                        MapPlace(
                            id = temple.id,
                            name = temple.name, // temple.name is not nullable as per Temple.kt
                            latitude = temple.latitude, // Not nullable
                            longitude = temple.longitude, // Not nullable
                            type = temple.type, // This comes directly from Temple.type
                            isVisited = visitedTempleIds.contains(temple.id),
                            address = temple.address
                        )
                    }
                }
                Log.d(TAG, "Mapped to ${resultingMapPlaces.size} MapPlace objects.")

                _mapPlaces.postValue(resultingMapPlaces)
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadAndFilterPlaces: ${e.message}", e)
                _mapPlaces.postValue(emptyList()) // Post empty list in case of an error
            }
        }
    }
//    private fun loadAllPlaces() {
//        Log.d(TAG, "loadAllPlaces() called.") // Log: Method invocation
//        viewModelScope.launch {
//            try { // It's good practice to wrap db/network calls
//                val temples = templeDao.getAllTemplesForSyncOrList()
//                Log.d(TAG, "Temples fetched. Count: ${temples.size}") // Log: Temples count
//                if (temples.isNotEmpty()) {
//                    // Corrected to use temple.id as per your file context
//                    Log.d(TAG, "First temple raw data - ID: ${temples.first().id}, Name: ${temples.first().name}, Lat: ${temples.first().latitude}, Lon: ${temples.first().longitude}, Type: ${temples.first().type}")
//                }
//
//                val visits = visitDao.getAllVisits().first()
//                Log.d(TAG, "Visits fetched. Count: ${visits.size}") // Log: Visits count
//                if (visits.isNotEmpty()) {
//                    Log.d(TAG, "First visit raw data - PlaceID: ${visits.first().placeID}")
//                }
//
//                val visitedTempleIds = visits.map { it.placeID }.toSet()
//                Log.d(TAG, "Constructed visitedTempleIds set: $visitedTempleIds") // Log: Visited IDs set
//
//                val allMapPlaces = temples.mapNotNull { temple ->
//                    if (temple.latitude == null || temple.longitude == null) {
//                        // Corrected to use temple.id as per your file context
//                        Log.w(TAG, "Skipping temple (ID: ${temple.id}, Name: ${temple.name}) due to null coordinates.") // Log: Skipped temple
//                        null
//                    } else {
//                        MapPlace(
//                            // Corrected to use temple.id as per your file context
//                            id = temple.id,
//                            name = temple.name ?: "Unknown Place",
//                            latitude = temple.latitude,
//                            longitude = temple.longitude,
//                            type = temple.type ?: "U",
//                            // Corrected to use temple.id for the visited check
//                            isVisited = visitedTempleIds.contains(temple.id),
//                            address = temple.address
//                        )
//                    }
//                }
//                Log.d(TAG, "Finished mapping to MapPlace objects. Count: ${allMapPlaces.size}") // Log: MapPlace objects count
//                if (allMapPlaces.isNotEmpty()) {
//                    Log.d(TAG, "First MapPlace object created: ${allMapPlaces.first()}")
//                } else if (temples.isNotEmpty()) {
//                    Log.d(TAG, "No MapPlace objects created, but temples were present. Check coordinate nullability or mapping logic.")
//                }
//
//                _mapPlaces.postValue(allMapPlaces)
//                Log.d(TAG, "_mapPlaces LiveData posted. Count: ${allMapPlaces.size}") // Log: LiveData post
//            } catch (e: Exception) {
//                Log.e(TAG, "Exception in loadAllPlaces: ${e.message}", e) // Log: Catching any errors
//            }
//        }
//    }
}
