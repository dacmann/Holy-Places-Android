package net.dacworld.android.holyplacesofthelord.data

import android.util.Log // Added import for Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Temple
import org.maplibre.android.camera.CameraPosition

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

class MapViewModel(
    private val templeDao: TempleDao,
    private val visitDao: VisitDao
) : ViewModel() {

    private val _mapPlaces = MutableLiveData<List<MapPlace>>()
    val mapPlaces: LiveData<List<MapPlace>> get() = _mapPlaces

    // LiveData to hold the last known camera state
    private val _lastCameraState = MutableLiveData<MapCameraState?>()
    val lastCameraState: LiveData<MapCameraState?> = _lastCameraState

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
        Log.d(TAG, "MapViewModel instance created.") // Log: ViewModel initialization
        loadAllPlaces()
    }

    private fun loadAllPlaces() {
        Log.d(TAG, "loadAllPlaces() called.") // Log: Method invocation
        viewModelScope.launch {
            try { // It's good practice to wrap db/network calls
                val temples = templeDao.getAllTemplesForSyncOrList()
                Log.d(TAG, "Temples fetched. Count: ${temples.size}") // Log: Temples count
                if (temples.isNotEmpty()) {
                    // Corrected to use temple.id as per your file context
                    Log.d(TAG, "First temple raw data - ID: ${temples.first().id}, Name: ${temples.first().name}, Lat: ${temples.first().latitude}, Lon: ${temples.first().longitude}, Type: ${temples.first().type}")
                }

                val visits = visitDao.getAllVisits().first()
                Log.d(TAG, "Visits fetched. Count: ${visits.size}") // Log: Visits count
                if (visits.isNotEmpty()) {
                    Log.d(TAG, "First visit raw data - PlaceID: ${visits.first().placeID}")
                }

                val visitedTempleIds = visits.map { it.placeID }.toSet()
                Log.d(TAG, "Constructed visitedTempleIds set: $visitedTempleIds") // Log: Visited IDs set

                val allMapPlaces = temples.mapNotNull { temple ->
                    if (temple.latitude == null || temple.longitude == null) {
                        // Corrected to use temple.id as per your file context
                        Log.w(TAG, "Skipping temple (ID: ${temple.id}, Name: ${temple.name}) due to null coordinates.") // Log: Skipped temple
                        null
                    } else {
                        MapPlace(
                            // Corrected to use temple.id as per your file context
                            id = temple.id,
                            name = temple.name ?: "Unknown Place",
                            latitude = temple.latitude,
                            longitude = temple.longitude,
                            type = temple.type ?: "U",
                            // Corrected to use temple.id for the visited check
                            isVisited = visitedTempleIds.contains(temple.id),
                            address = temple.address
                        )
                    }
                }
                Log.d(TAG, "Finished mapping to MapPlace objects. Count: ${allMapPlaces.size}") // Log: MapPlace objects count
                if (allMapPlaces.isNotEmpty()) {
                    Log.d(TAG, "First MapPlace object created: ${allMapPlaces.first()}")
                } else if (temples.isNotEmpty()) {
                    Log.d(TAG, "No MapPlace objects created, but temples were present. Check coordinate nullability or mapping logic.")
                }

                _mapPlaces.postValue(allMapPlaces)
                Log.d(TAG, "_mapPlaces LiveData posted. Count: ${allMapPlaces.size}") // Log: LiveData post
            } catch (e: Exception) {
                Log.e(TAG, "Exception in loadAllPlaces: ${e.message}", e) // Log: Catching any errors
            }
        }
    }
    // TODO: Add filter logic here later
}
