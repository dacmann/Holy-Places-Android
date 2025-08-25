package net.dacworld.android.holyplacesofthelord.data // Or your preferred package for ViewModels

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.flow.first
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager

class RecordVisitViewModel(
    private val application: Application, // For ContentResolver if converting Uri to ByteArray
    private val visitDao: VisitDao,
    private val currentVisitId: Long?, // Null if creating a new visit
    private val placeIdArg: String,    // The ID of the Temple/Place
    private val placeNameArg: String,  // The name of the Temple/Place
    private val placeTypeArg: String,   // The type of the Temple/Place (e.g., "T")
    private val userPreferencesManager: UserPreferencesManager
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
        // Observe the preference from UserPreferencesManager
        viewModelScope.launch {
            userPreferencesManager.enableHoursWorkedFlow.collect { isEnabled ->
                // Update the UI state with the new preference value
                _uiState.value?.let { currentUiState ->
                    _uiState.postValue(currentUiState.copy(isHoursWorkedEntryEnabled = isEnabled))
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val initialHoursPreference = userPreferencesManager.enableHoursWorkedFlow.first()

            if (_isEditing && currentVisitId != null) {
                val visit = visitDao.getVisitById(currentVisitId).firstOrNull()
                if (visit != null) {
                    _uiState.postValue(
                        VisitUiState.fromVisitEntity(visit).copy(
                            isHoursWorkedEntryEnabled = initialHoursPreference
                        )
                    )
                } else {
                    _uiState.postValue(
                        createNewVisitState(
                            placeIdArg,
                            placeNameArg,
                            placeTypeArg,
                            initialHoursPreference // Pass preference
                        )
                    )
                }
            } else {
                _uiState.postValue(
                    createNewVisitState(
                        placeIdArg,
                        placeNameArg,
                        placeTypeArg,
                        initialHoursPreference // Pass preference
                    )
                )
            }
        }
    }


    private fun createNewVisitState(
        placeId: String,
        placeName: String,
        placeType: String,
        isHoursEnabled: Boolean // Accept preference value
    ): VisitUiState {
        return VisitUiState(
            placeID = placeId,
            holyPlaceName = placeName,
            visitType = placeType,
            dateVisited = Date(),
            isFavorite = false,
            // --- SET BASED ON PREFERENCE ---
            isHoursWorkedEntryEnabled = isHoursEnabled,
            // If hours are not enabled, ensure shiftHrs is 0 or null from the start
            shiftHrs = if (isHoursEnabled) 0.0 else null // Or always 0.0 and UI hides it
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
        // Only update if the entry is actually enabled based on current state
        if (_uiState.value?.isHoursWorkedEntryEnabled == true) {
            val hours = hoursString.trim().toDoubleOrNull() ?: 0.0
            _uiState.value = _uiState.value?.copy(shiftHrs = hours)
        }
    }

    fun onCommentsChanged(comments: String) {
        _uiState.value = _uiState.value?.copy(comments = comments.ifBlank { null })
    }

    fun onImageSelectionCleared() {
        _uiState.value = _uiState.value?.copy(
            selectedImageUri = null, // Clear original URI if it was stored for temporary preview
            pictureByteArray = null  // Clear the (potentially processed) byte array
        )
    }

    // NEW: Function called by the Fragment when a new image URI is picked
    fun processImageUri(imageUri: Uri?) {
        if (imageUri == null) {
            // User might have cancelled picker or it's a call to clear
            onImageSelectionCleared()
            return
        }

        // Show some immediate feedback if desired, e.g., by setting selectedImageUri for a temporary preview
        // _uiState.value = _uiState.value?.copy(selectedImageUri = imageUri, pictureByteArray = null) // Optional: show original while processing

        viewModelScope.launch {
            // Perform image processing on a background thread
            val processedByteArray = resizeAndCompressImage(getApplication<Application>().contentResolver, imageUri)

            // Update UI state on the main thread
            withContext(Dispatchers.Main) {
                if (processedByteArray != null) {
                    _uiState.value = _uiState.value?.copy(
                        // selectedImageUri = imageUri, // Keep if you want to show original URI as preview source
                        // OR set to null if preview should only use byteArray
                        selectedImageUri = null, // Let's clear it and rely on byteArray for preview source
                        pictureByteArray = processedByteArray
                    )
                } else {
                    // Processing failed, ensure image is cleared
                    onImageSelectionCleared()
                    // TODO: Optionally notify user of processing failure via a LiveData event
                }
            }
        }
    }

    // NEW: Image processing logic
    private suspend fun resizeAndCompressImage(contentResolver: ContentResolver, imageUri: Uri): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                // More robust loading with inSampleSize to prevent OOM on very large images
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true // First, check dimensions without loading into memory
                var inputStream = contentResolver.openInputStream(imageUri)
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                if (options.outWidth == -1 || options.outHeight == -1) {
                    Log.e("RecordVisitVM", "Failed to decode image bounds. URI: $imageUri")
                    return@withContext null
                }

                val maxDimension = 1024 // Target max width or height in pixels

                options.inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension)
                options.inJustDecodeBounds = false // Now load the subsampled image into memory

                inputStream = contentResolver.openInputStream(imageUri)
                var bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                if (bitmap == null) { // Check if decoding failed even with inSampleSize
                    Log.e("RecordVisitVM", "BitmapFactory failed to decode stream after subsampling. URI: $imageUri")
                    return@withContext null
                }

                // --- Further Resize if needed after inSampleSize (more precise scaling) ---
                val currentWidth = bitmap.width
                val currentHeight = bitmap.height
                var finalBitmap = bitmap

                if (currentWidth > maxDimension || currentHeight > maxDimension) {
                    val ratio: Float = if (currentWidth > currentHeight) {
                        maxDimension.toFloat() / currentWidth
                    } else {
                        maxDimension.toFloat() / currentHeight
                    }
                    val newWidth = (currentWidth * ratio).toInt()
                    val newHeight = (currentHeight * ratio).toInt()
                    if (newWidth > 0 && newHeight > 0) { // Ensure valid dimensions
                        finalBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                        if (finalBitmap != bitmap) { // Only recycle if a new bitmap was created
                            bitmap.recycle() // Recycle the intermediate bitmap if scaled
                        }
                    } else {
                        Log.w("RecordVisitVM","Calculated new dimensions are zero or negative. Using bitmap from inSampleSize.")
                    }
                }


                // --- Compress Logic ---
                ByteArrayOutputStream().use { outputStream ->
                    // Adjust quality (0-100). JPEG is lossy.
                    val success = finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream) // 80% quality JPEG
                    if (finalBitmap != bitmap && !finalBitmap.isRecycled) { // If a new bitmap was created and not yet recycled
                        finalBitmap.recycle()
                    } else if (finalBitmap == bitmap && !bitmap.isRecycled) { // If original bitmap was used and not yet recycled
                        bitmap.recycle() // Recycle the original if it wasn't scaled further or if scaling failed
                    }

                    if (!success) {
                        Log.e("RecordVisitVM", "Bitmap compression failed for URI: $imageUri")
                        return@withContext null
                    }
                    outputStream.toByteArray()
                }
            } catch (e: IOException) {
                Log.e("RecordVisitVM", "IOException processing image URI: $imageUri", e)
                null
            } catch (oom: OutOfMemoryError) {
                Log.e("RecordVisitVM", "OutOfMemoryError processing image URI: $imageUri", oom)
                null
            } catch (e: Exception) { // Catch any other unexpected errors during processing
                Log.e("RecordVisitVM", "Unexpected error processing image URI: $imageUri", e)
                null
            }
        }
    }

    // NEW: Helper function to calculate inSampleSize
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

//    fun onImageSelected(imageUri: Uri?) {
//        if (imageUri == null) {
//            // Image removed by user or no image selected
//            _uiState.value = _uiState.value?.copy(
//                selectedImageUri = null,
//                pictureByteArray = null
//            )
//        } else {
//            // New image selected, convert it to ByteArray
//            _uiState.value = _uiState.value?.copy(selectedImageUri = imageUri) // Show preview immediately
//            viewModelScope.launch {
//                val byteArray = convertUriToByteArray(imageUri)
//                // Update only if the selected URI hasn't changed (user didn't pick another one quickly)
//                if (_uiState.value?.selectedImageUri == imageUri) {
//                    _uiState.value = _uiState.value?.copy(pictureByteArray = byteArray)
//                }
//            }
//        }
//    }

//    private suspend fun convertUriToByteArray(uri: Uri): ByteArray? {
//        return withContext(Dispatchers.IO) { // Perform heavy operation on IO thread
//            try {
//                getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
//                    ByteArrayOutputStream().use { byteStream ->
//                        inputStream.copyTo(byteStream)
//                        byteStream.toByteArray()
//                    }
//                }
//            } catch (e: IOException) {
//                e.printStackTrace() // Log the error
//                // Consider showing an error message to the user via a LiveData event
//                null
//            }
//        }
//    }

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
            return
        }
        if (currentUiState.dateVisited == null) {
            _saveResultEvent.value = Event(false)
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
                    comments = currentUiState.comments?.trim()?.ifBlank { null },
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
    val isFavorite: Boolean = false,
    val isHoursWorkedEntryEnabled: Boolean = false // Default to false, will be updated from preference

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
        if (isHoursWorkedEntryEnabled != other.isHoursWorkedEntryEnabled) return false

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
        result = 31 * result + isHoursWorkedEntryEnabled.hashCode()
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
                isFavorite = visit.isFavorite,
                isHoursWorkedEntryEnabled = false
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
    private val placeType: String,
    private val userPreferencesManager: UserPreferencesManager
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
                placeType,
                userPreferencesManager
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
