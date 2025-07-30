package net.dacworld.android.holyplacesofthelord.data // Or your preferred package for ViewModels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.database.AppDatabase // For getting DAO instance
import net.dacworld.android.holyplacesofthelord.model.Visit
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class RecordVisitViewModel(
    private val application: Application, // For ContentResolver if converting Uri to ByteArray
    private val visitDao: VisitDao,
    private val currentVisitId: Long?, // Null if creating a new visit
    private val placeIdArg: String,    // The ID of the Temple/Place
    private val placeNameArg: String,  // The name of the Temple/Place
    private val placeTypeArg: String   // The type of the Temple/Place (e.g., "T")
) : AndroidViewModel(application) {

    private val _isEditing = currentVisitId != null && currentVisitId != 0L

    // LiveData to hold the current state of the visit being edited or created
    private val _uiState = MutableLiveData<VisitUiState>()
    val uiState: LiveData<VisitUiState> = _uiState

    // LiveData to signal the result of the save operation (true for success, false for failure)
    // Wrapped in an Event to prevent multiple consumptions on config change
    private val _saveResultEvent = MutableLiveData<Event<Boolean>>()
    val saveResultEvent: LiveData<Event<Boolean>> = _saveResultEvent

    // Example: LiveData for ordinance worker status (you'd load this from prefs/datastore)
    private val _isOrdinanceWorker = MutableLiveData<Boolean>(true) // Default, adjust as needed
    val isOrdinanceWorker: LiveData<Boolean> = _isOrdinanceWorker

    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

    init {
        loadInitialData()
        // TODO: Load isOrdinanceWorker from actual preferences/DataStore if needed
        // viewModelScope.launch {
        //     _isOrdinanceWorker.value = settingsRepository.isOrdinanceWorker().first()
        // }
    }

    private fun loadInitialData() {
        if (_isEditing && currentVisitId != null) {
            viewModelScope.launch {
                // Collect the first emission from the Flow
                val visit = visitDao.getVisitById(currentVisitId).firstOrNull() // Corrected line
                if (visit != null) {
                    _uiState.value = VisitUiState.fromVisitEntity(visit)
                } else {
                    // Visit not found for editing, or Flow emitted null first.
                    // Fallback to new visit state with passed args.
                    _uiState.value = createNewVisitState(placeIdArg, placeNameArg, placeTypeArg)
                    // Optionally, signal an error or log this situation
                    // e.g., Log.e("RecordVisitVM", "Visit with ID $currentVisitId not found for editing.")
                }
            }
        } else {
            // Creating a new visit
            _uiState.value = createNewVisitState(placeIdArg, placeNameArg, placeTypeArg)
        }
    }


    private fun createNewVisitState(placeId: String, placeName: String, placeType: String): VisitUiState {
        // Determine a default visit type based on the place type, e.g., Temple visit is "Ordinance Work"
        val defaultVisitType = if (placeType == "T") "Ordinance Work" else "Personal Visit"
        return VisitUiState(
            placeID = placeId,
            holyPlaceName = placeName,
            visitType = defaultVisitType, // Set the 'type' field of the Visit
            dateVisited = Date(), // Default to current date/time
            isFavorite = false // Default favorite status
            // Other fields default to null or 0 as per VisitUiState definition
        )
    }

    // --- UI Event Handlers ---

    fun onDateChanged(newDate: Date) {
        _uiState.value = _uiState.value?.copy(dateVisited = newDate)
    }

    fun onOrdinanceCountChanged(type: OrdinanceType, countString: String) {
        val count = countString.trim().toShortOrNull() ?: 0 // Convert to Short, default to 0
        val current = _uiState.value ?: return // Should not happen if UI is bound
        _uiState.value = when (type) {
            OrdinanceType.BAPTISMS -> current.copy(baptisms = count)
            OrdinanceType.CONFIRMATIONS -> current.copy(confirmations = count)
            OrdinanceType.INITIATORIES -> current.copy(initiatories = count)
            OrdinanceType.ENDOWMENTS -> current.copy(endowments = count)
            OrdinanceType.SEALINGS -> current.copy(sealings = count)
        }
    }

    fun onHoursWorkedChanged(hoursString: String) {
        val hours = hoursString.trim().toDoubleOrNull() ?: 0.0
        _uiState.value = _uiState.value?.copy(shiftHrs = hours)
    }

    fun onCommentsChanged(comments: String) {
        _uiState.value = _uiState.value?.copy(comments = comments.trim().ifBlank { null })
    }

    fun onImageSelected(imageUri: Uri?) {
        if (imageUri == null) {
            // Image removed by user or no image selected
            _uiState.value = _uiState.value?.copy(
                selectedImageUri = null,
                pictureByteArray = null
            )
        } else {
            // New image selected, convert it to ByteArray
            _uiState.value = _uiState.value?.copy(selectedImageUri = imageUri) // Show preview immediately
            viewModelScope.launch {
                val byteArray = convertUriToByteArray(imageUri)
                // Update only if the selected URI hasn't changed (user didn't pick another one quickly)
                if (_uiState.value?.selectedImageUri == imageUri) {
                    _uiState.value = _uiState.value?.copy(pictureByteArray = byteArray)
                }
            }
        }
    }

    private suspend fun convertUriToByteArray(uri: Uri): ByteArray? {
        return withContext(Dispatchers.IO) { // Perform heavy operation on IO thread
            try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                    ByteArrayOutputStream().use { byteStream ->
                        inputStream.copyTo(byteStream)
                        byteStream.toByteArray()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace() // Log the error
                // Consider showing an error message to the user via a LiveData event
                null
            }
        }
    }

    fun onToggleFavorite() {
        val current = _uiState.value ?: return
        _uiState.value = current.copy(isFavorite = !current.isFavorite)
    }

    fun onVisitTypeChanged(newVisitType: String) {
        _uiState.value = _uiState.value?.copy(visitType = newVisitType.ifBlank { null })
    }

    fun saveVisit() {
        val currentUiState = _uiState.value ?: run {
            _saveResultEvent.value = Event(false) // Should not happen
            return
        }

        // Basic validation (example)
        if (currentUiState.holyPlaceName.isBlank() || currentUiState.placeID.isBlank()) {
            _saveResultEvent.value = Event(false)
            // TODO: Expose more specific error messages to the UI if needed
            return
        }
        if (currentUiState.dateVisited == null) {
            _saveResultEvent.value = Event(false)
            // TODO: Expose more specific error messages (e.g., "Date cannot be empty")
            return
        }

        viewModelScope.launch {
            try {
                val visitToSave = Visit(
                    id = if (_isEditing) currentVisitId!! else 0L, // Use currentVisitId for updates, 0 for new
                    placeID = currentUiState.placeID,
                    holyPlaceName = currentUiState.holyPlaceName.ifBlank { null }, // Ensure not just whitespace
                    type = currentUiState.visitType, // The type of visit (e.g., "Ordinance Work")
                    dateVisited = currentUiState.dateVisited, // This is already a Date object
                    year = currentUiState.dateVisited?.let { yearFormat.format(it) },
                    baptisms = currentUiState.baptisms,
                    confirmations = currentUiState.confirmations,
                    initiatories = currentUiState.initiatories,
                    endowments = currentUiState.endowments,
                    sealings = currentUiState.sealings,
                    shiftHrs = currentUiState.shiftHrs,
                    comments = currentUiState.comments,
                    picture = currentUiState.pictureByteArray, // The ByteArray for the image
                    isFavorite = currentUiState.isFavorite
                )

                if (_isEditing) {
                    visitDao.updateVisit(visitToSave)
                } else {
                    visitDao.insertVisit(visitToSave)
                }
                _saveResultEvent.value = Event(true) // Signal success
            } catch (e: Exception) {
                e.printStackTrace() // Log the exception
                _saveResultEvent.value = Event(false) // Signal failure
            }
        }
    }
}

/**
 * Data class representing the UI state for the RecordVisit screen.
 * It mirrors the structure of your `Visit` entity but is tailored for UI interaction.
 */
data class VisitUiState(
    val placeID: String = "",           // Foreign key to Temple/Place
    val holyPlaceName: String = "",
    val visitType: String? = null,      // Type of visit activity (e.g., "Ordinance Work", "Personal Visit")
    val dateVisited: Date? = null,
    val baptisms: Short? = 0,
    val confirmations: Short? = 0,
    val initiatories: Short? = 0,
    val endowments: Short? = 0,
    val sealings: Short? = 0,
    val shiftHrs: Double? = 0.0,
    val comments: String? = null,
    val selectedImageUri: Uri? = null,      // Temporary URI from image picker for preview
    val pictureByteArray: ByteArray? = null,// Image data as ByteArray for saving to DB
    val isFavorite: Boolean = false
) {
    // Overriding equals and hashCode is important if this state is used in ways
    // that rely on object equality (e.g., in some LiveData transformations or tests),
    // especially due to the presence of ByteArray.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VisitUiState

        if (placeID != other.placeID) return false
        if (holyPlaceName != other.holyPlaceName) return false
        if (visitType != other.visitType) return false
        if (dateVisited != other.dateVisited) return false // Date comparison is fine
        if (baptisms != other.baptisms) return false
        if (confirmations != other.confirmations) return false
        if (initiatories != other.initiatories) return false
        if (endowments != other.endowments) return false
        if (sealings != other.sealings) return false
        if (shiftHrs != other.shiftHrs) return false
        if (comments != other.comments) return false
        if (selectedImageUri != other.selectedImageUri) return false // Uri comparison
        if (pictureByteArray != null) {
            if (other.pictureByteArray == null) return false
            if (!pictureByteArray.contentEquals(other.pictureByteArray)) return false
        } else if (other.pictureByteArray != null) return false
        if (isFavorite != other.isFavorite) return false

        return true
    }

    override fun hashCode(): Int {
        var result = placeID.hashCode()
        result = 31 * result + holyPlaceName.hashCode()
        result = 31 * result + (visitType?.hashCode() ?: 0)
        result = 31 * result + (dateVisited?.hashCode() ?: 0)
        result = 31 * result + (baptisms ?: 0).toInt()
        result = 31 * result + (confirmations ?: 0).toInt()
        result = 31 * result + (initiatories ?: 0).toInt()
        result = 31 * result + (endowments ?: 0).toInt()
        result = 31 * result + (sealings ?: 0).toInt()
        result = 31 * result + (shiftHrs?.hashCode() ?: 0)
        result = 31 * result + (comments?.hashCode() ?: 0)
        result = 31 * result + (selectedImageUri?.hashCode() ?: 0)
        result = 31 * result + (pictureByteArray?.contentHashCode() ?: 0)
        result = 31 * result + isFavorite.hashCode()
        return result
    }

    companion object {
        fun fromVisitEntity(visit: Visit): VisitUiState {
            return VisitUiState(
                placeID = visit.placeID,
                holyPlaceName = visit.holyPlaceName ?: "",
                visitType = visit.type,
                dateVisited = visit.dateVisited,
                baptisms = visit.baptisms ?: 0,
                confirmations = visit.confirmations ?: 0,
                initiatories = visit.initiatories ?: 0,
                endowments = visit.endowments ?: 0,
                sealings = visit.sealings ?: 0,
                shiftHrs = visit.shiftHrs ?: 0.0,
                comments = visit.comments,
                selectedImageUri = null, // No initial URI when loading from DB
                pictureByteArray = visit.picture, // Load byte array from DB
                isFavorite = visit.isFavorite
            )
        }
    }
}

// Enum to help manage ordinance type updates
enum class OrdinanceType {
    BAPTISMS, CONFIRMATIONS, INITIATORIES, ENDOWMENTS, SEALINGS
}

/**
 * ViewModelProvider.Factory to construct RecordVisitViewModel with arguments.
 */
class RecordVisitViewModelFactory(
    private val application: Application,
    private val visitDao: VisitDao, // Pass the DAO
    private val visitId: Long?,      // Use Long? to indicate optionality
    private val placeId: String,
    private val placeName: String,
    private val placeType: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordVisitViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecordVisitViewModel(
                application,
                visitDao,
                visitId,
                placeId,
                placeName,
                placeType
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/**
 * Used as a wrapper for data that is exposed via LiveData that represents an event.
 * Prevents issues with configuration changes (e.g. screen rotation) consuming an event multiple times.
 */
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    fun peekContent(): T = content
}
