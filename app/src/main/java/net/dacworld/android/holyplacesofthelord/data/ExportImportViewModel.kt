package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt
import androidx.fragment.app.add
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.util.XmlHelper // We will create this helper next
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Data class to represent the outcome of an operation
sealed class OperationStatus {
    object Idle : OperationStatus()
    object InProgress : OperationStatus()
    data class Success(val message: String) : OperationStatus()
    data class Error(val message: String) : OperationStatus()
}

    data class ImportResults(
        val successfullyImported: Int,
        val updatedExisting: Int,
        val photosImported: Int,
        val skippedDuplicates: Int,
        val skippedMissingTemples: Int,
        val parsingErrors: Int,
        val templeNameCorrections: Int
    )

class ExportImportViewModel(application: Application) : AndroidViewModel(application) {

    private val visitDao = AppDatabase.getDatabase(application).visitDao()
    private val templeDao = AppDatabase.getDatabase(application).templeDao()

    private val _operationStatus = MutableLiveData<OperationStatus>(OperationStatus.Idle)
    val operationStatus: LiveData<OperationStatus> = _operationStatus

    private val _importResults = MutableLiveData<ImportResults?>()
    val importResults: LiveData<ImportResults?> = _importResults
    
    // Photo export state
    private var _includePhotos = false
    val includePhotos: Boolean get() = _includePhotos
    
    // Operation type tracking
    private var _isExporting = false
    val isExporting: Boolean get() = _isExporting

    // --- EXPORT ---
    fun exportVisitsToXml(uri: Uri) {
        _isExporting = true
        _operationStatus.value = OperationStatus.InProgress
        viewModelScope.launch {
            try {
                val visits = visitDao.getAllVisitsListForExport()
                if (visits.isEmpty()) {
                    _operationStatus.postValue(OperationStatus.Success(getApplication<Application>().getString(R.string.export_no_visits)))
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openFileDescriptor(uri, "w")?.use { parcelFileDescriptor ->
                        FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                            if (_includePhotos) {
                                // Use streaming approach for photos to avoid memory issues
                                XmlHelper.generateVisitsXmlStreaming(visits, outputStream, visitDao)
                            } else {
                                // No photos, use regular export
                                XmlHelper.generateVisitsXml(visits, outputStream, false)
                            }
                        }
                    } ?: throw Exception("Failed to open file descriptor for writing.")
                }
                // Count photos if includePhotos is enabled
                val photoCount = if (_includePhotos) {
                    visits.count { it.hasPicture }
                } else {
                    0
                }
                
                val successMessage = if (_includePhotos && photoCount > 0) {
                    getApplication<Application>().getString(R.string.export_successful_with_photos, visits.size, photoCount)
                } else {
                    getApplication<Application>().getString(R.string.export_successful, visits.size)
                }
                
                _operationStatus.postValue(OperationStatus.Success(successMessage))
            } catch (e: Exception) {
                Log.e("ExportImportVM", "Export failed", e)
                _operationStatus.postValue(OperationStatus.Error(getApplication<Application>().getString(R.string.export_failed, e.localizedMessage ?: "Unknown error")))
            } finally {
                _isExporting = false
            }
        }
    }

    /**
     * Resets the operation status back to Idle.
     * Should be called from the UI after a success or error message has been handled.
     */
    fun resetOperationStatus() {
        _operationStatus.postValue(OperationStatus.Idle) // Use postValue if called from a background thread,
        // or setValue if always from the main thread.
        // postValue is safer here.
        _importResults.value = null
    }
    
    /**
     * Sets whether to include photos in export
     */
    fun setIncludePhotos(include: Boolean) {
        _includePhotos = include
    }
    
    /**
     * Calculates estimated file size for export
     */
    fun calculateEstimatedFileSize(callback: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val visits = visitDao.getAllVisitsListForExport()
                var estimatedSize: Long = 1000 // Base XML structure
                var photoCount = 0
                
                // Average photo size after compression (based on actual data from logs)
                val averagePhotoSizeBytes = 2_200_000L // 2.2MB average (more accurate based on actual sizes)
                
                Log.d("ExportImportVM", "Calculating file size for ${visits.size} visits, includePhotos: $_includePhotos")
                
                for (visit in visits) {
                    // Add visit data size
                    estimatedSize += (visit.holyPlaceName?.length ?: 0) * 2L
                    estimatedSize += (visit.comments?.length ?: 0) * 2L
                    estimatedSize += 200 // Other fields
                    
                    // Add estimated photo size if includePhotos is enabled and visit has a photo
                    if (_includePhotos && visit.hasPicture) {
                        // Base64 encoding increases size by ~33%
                        val estimatedPhotoSize = (averagePhotoSizeBytes * 133) / 100
                        estimatedSize += estimatedPhotoSize
                        photoCount++
                    }
                }
                
                Log.d("ExportImportVM", "Estimated size: $estimatedSize bytes, Photos included: $photoCount, Average photo size: $averagePhotoSizeBytes bytes")
                callback(estimatedSize)
            } catch (e: Exception) {
                Log.e("ExportImportVM", "Error calculating file size", e)
                callback(0)
            }
        }
    }
    
    /**
     * Validates if the decoded photo data looks like valid image data
     */
    private fun isValidImageData(data: ByteArray): Boolean {
        if (data.isEmpty() || data.size < 4) return false
        
        // Check for common image file signatures
        val header = data.take(4)
        return when {
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> true // JPEG
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && 
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> true // PNG
            header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && 
            header[2] == 0x46.toByte() && header[3] == 0x38.toByte() -> true // GIF
            else -> false
        }
    }
    
    // --- IMPORT ---
    fun importVisitsFromXml(uri: Uri) {
        _isExporting = false
        _operationStatus.value = OperationStatus.InProgress
        viewModelScope.launch {
            var successfullyImportedCount = 0
            var skippedDuplicateCount = 0
            var skippedMissingTempleCount = 0
            var parsingErrorCount = 0
            var photoImportCount = 0
            // +++ NEW: Add a counter for corrected temple names +++
            var correctedTempleNameCount = 0

            try {
                // Create a processor class to hold counters and process visits like iOS
                val processor = VisitProcessor(
                    visitDao, templeDao, getApplication<Application>()
                )
                
                // Use streaming approach like iOS - process visits one at a time
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
                        FileInputStream(parcelFileDescriptor.fileDescriptor).use { inputStream ->
                            XmlHelper.parseVisitsXmlStreaming(
                                inputStream = inputStream,
                                onVisitProcessed = { dto ->
                                    // Process each visit immediately like iOS does
                                    processor.processVisit(dto)
                                },
                                shouldSkipPhoto = { holyPlaceName, dateVisitedString ->
                                    // Check if this visit already has a photo (duplicate check)
                                    try {
                                        // Try multiple date formats to handle different XML formats
                                        val dateFormats = listOf(
                                            SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US), // "Saturday, August 30, 2025"
                                            SimpleDateFormat("yyyy-MM-dd", Locale.US), // "2025-08-30"
                                            SimpleDateFormat("MMM d, yyyy", Locale.US), // "Aug 30, 2025"
                                            SimpleDateFormat("M/d/yyyy", Locale.US) // "8/30/2025"
                                        )
                                        
                                        var dateVisitedFromXml: Date? = null
                                        for (format in dateFormats) {
                                            try {
                                                dateVisitedFromXml = format.parse(dateVisitedString)
                                                if (dateVisitedFromXml != null) break
                                            } catch (e: Exception) {
                                                // Try next format
                                            }
                                        }
                                        
                                        if (dateVisitedFromXml != null) {
                                            val calendar = Calendar.getInstance().apply {
                                                time = dateVisitedFromXml
                                                set(Calendar.HOUR_OF_DAY, 0)
                                                set(Calendar.MINUTE, 0)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }
                                            val startOfDayMillis = calendar.timeInMillis
                                            calendar.add(Calendar.DAY_OF_MONTH, 1)
                                            val endOfDayMillis = calendar.timeInMillis
                                            
                                            val existingVisit = visitDao.getVisitByNameAndDayRange(holyPlaceName, startOfDayMillis, endOfDayMillis)
                                            val shouldSkip = existingVisit?.picture?.isNotEmpty() == true
                                            if (shouldSkip) {
                                                Log.d("ImportVM", "Skipping photo processing for duplicate visit: $holyPlaceName")
                                            }
                                            return@parseVisitsXmlStreaming shouldSkip
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ImportVM", "Error checking for duplicate photo: ${e.message}")
                                    }
                                    false
                                }
                            )
                        }
                    } ?: throw Exception("Failed to open file descriptor for reading.")
                }

                // Get the final counts from the processor
                successfullyImportedCount = processor.successfullyImportedCount
                val updatedExistingCount = processor.updatedExistingCount
                skippedDuplicateCount = processor.skippedDuplicateCount
                skippedMissingTempleCount = processor.skippedMissingTempleCount
                parsingErrorCount = processor.parsingErrorCount
                photoImportCount = processor.photoImportCount
                correctedTempleNameCount = processor.correctedTempleNameCount

                // Set the import results for the dialog
                _importResults.value = ImportResults(
                    successfullyImported = successfullyImportedCount,
                    updatedExisting = updatedExistingCount,
                    photosImported = photoImportCount,
                    skippedDuplicates = skippedDuplicateCount,
                    skippedMissingTemples = skippedMissingTempleCount,
                    parsingErrors = parsingErrorCount,
                    templeNameCorrections = correctedTempleNameCount
                )
                val successMessage = if (photoImportCount > 0) {
                    getApplication<Application>().getString(R.string.import_report_successful_with_photos,
                        successfullyImportedCount, photoImportCount, skippedDuplicateCount, skippedMissingTempleCount, parsingErrorCount)
                } else {
                    getApplication<Application>().getString(R.string.import_report_successful_skipped_errors,
                        successfullyImportedCount, skippedDuplicateCount, skippedMissingTempleCount, parsingErrorCount)
                }
                
                _operationStatus.postValue(OperationStatus.Success(successMessage))

            } catch (e: XmlHelper.XmlParseException) {
                Log.e("ExportImportVM", "XML Parsing failed during import", e)
                _operationStatus.postValue(OperationStatus.Error(
                    getApplication<Application>().getString(R.string.import_failed_parsing, e.localizedMessage ?: "Invalid XML format")
                ))
            }
            catch (e: Exception) {
                Log.e("ExportImportVM", "Import failed", e)
                _operationStatus.postValue(OperationStatus.Error(
                    getApplication<Application>().getString(R.string.import_failed_general, e.localizedMessage ?: "Unknown error")
                ))
            } finally {
                XmlHelper.lastParseError = null // Reset last parse error
            }
        }
    }
}

/**
 * Processor class that handles individual visit processing like iOS does
 */
private class VisitProcessor(
    private val visitDao: net.dacworld.android.holyplacesofthelord.dao.VisitDao,
    private val templeDao: net.dacworld.android.holyplacesofthelord.dao.TempleDao,
    private val application: Application
) {
    var successfullyImportedCount = 0
    var updatedExistingCount = 0
    var skippedDuplicateCount = 0
    var skippedMissingTempleCount = 0
    var parsingErrorCount = 0
    var photoImportCount = 0
    var correctedTempleNameCount = 0

    suspend fun processVisit(dto: XmlHelper.VisitDto) {
                    // 1. Convert DTO date string to Date object
                    val dateVisitedFromXml = XmlHelper.parseDateString(dto.dateVisitedString)
                    if (dateVisitedFromXml == null) {
                        Log.w("Import", "Skipping visit due to unparseable date: ${dto.dateVisitedString} for ${dto.holyPlaceName}")
                        parsingErrorCount++
            return
                    }

                    val placeIdFromXml = dto.placeID
                    val nameFromXml = dto.holyPlaceName // Name from XML, for logging/comparison/fallback

                    val currentTempleData: Temple?
                    val definitivePlaceId: String? // Will hold the ID used for DB operations
                    val currentCorrectTempleName: String? // Will hold the name to be saved with the Visit

                    if (!placeIdFromXml.isNullOrBlank()) {
                        // --- Path 1: placeID IS AVAILABLE in XML (Newer XML Format) ---
                        Log.d("ImportVM", "Processing visit with placeID from XML: '$placeIdFromXml'")
                        currentTempleData = templeDao.getTempleByIdForSync(placeIdFromXml)
                        definitivePlaceId = placeIdFromXml

                        if (currentTempleData == null) {
                            Log.w("ImportVM", "Skipping visit. Temple with ID '$placeIdFromXml' (from XML) not found in current database. XML Name: '$nameFromXml', Date: ${dto.dateVisitedString}")
                            skippedMissingTempleCount++
                return
                        }
                        currentCorrectTempleName = currentTempleData.name

                        // Check if the name in XML (if present) was different from the current correct name
                        if (!nameFromXml.isNullOrBlank() && nameFromXml != currentCorrectTempleName) {
                            Log.i("ImportVM", "Temple name difference (ID-based import). XML had name '$nameFromXml' for ID '$definitivePlaceId', but current DB name is '$currentCorrectTempleName'.")
                            correctedTempleNameCount++
                        }
                    } else {
                        // --- Path 2: placeID IS NOT AVAILABLE in XML (Older XML Format - Fallback) ---
                        Log.d("ImportVM", "Processing visit without placeID from XML (fallback to name lookup). XML Name: '$nameFromXml'")
                        if (nameFromXml.isNullOrBlank()) {
                            Log.w("ImportVM", "Skipping visit (fallback mode). holyPlaceName is null or blank in XML DTO and no placeID available. Date: ${dto.dateVisitedString}")
                            parsingErrorCount++ // Or a "missing_identifier" count
                return
                        }

                        currentTempleData = templeDao.getTempleByNameForSync(nameFromXml)

                        if (currentTempleData == null) {
                            Log.w("ImportVM", "Skipping visit (fallback mode). Temple not found in DB by XML name: '$nameFromXml'. Date: ${dto.dateVisitedString}")
                            skippedMissingTempleCount++
                return
                        }
                        definitivePlaceId = currentTempleData.id // ID obtained via name lookup
                        currentCorrectTempleName = currentTempleData.name // This IS the name from DB

                        // In this fallback path, if we found a temple by 'nameFromXml', its 'currentCorrectTempleName'
                        if (nameFromXml != currentCorrectTempleName) {
                            Log.i("ImportVM", "Temple name difference (Name-based fallback import). XML had '${nameFromXml}', which resolved to Temple ID '${definitivePlaceId}' with current name '${currentCorrectTempleName}'.")
                            correctedTempleNameCount++
                        }
                    }

        // --- START: DUPLICATE CHECK (now handled in XML parser for photo optimization) ---
                    val calendar = Calendar.getInstance().apply {
                        time = dateVisitedFromXml // Use the date parsed from XML
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val startOfDayMillis = calendar.timeInMillis

                    calendar.add(Calendar.DAY_OF_MONTH, 1) // Moves to start of the next day
                    val endOfDayMillis = calendar.timeInMillis // This is the exclusive upper bound for the query

                    val nameForDuplicateCheck = currentCorrectTempleName

                    if (nameForDuplicateCheck.isBlank()){
                        Log.w("ImportVM_DUPE_CHECK", "Skipping duplicate check as nameForDuplicateCheck is null or blank for date ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dateVisitedFromXml)}")
        } else {
            // Check if a visit exists for this temple and date
            val existingVisit = visitDao.getVisitByNameAndDayRange(nameForDuplicateCheck, startOfDayMillis, endOfDayMillis)
            
            if (existingVisit != null) {
                Log.d("ImportVM", "Found existing visit for Temple Name '$nameForDuplicateCheck' on date ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dateVisitedFromXml)}")
                
                // Check if the existing visit has photo data
                if (existingVisit.picture == null || existingVisit.picture.isEmpty()) {
                    Log.d("ImportVM", "Existing visit has no photo data, will update with new photo data")
                    // Continue processing to update the existing visit with photo data
                } else {
                    Log.d("ImportVM", "Existing visit already has photo data, skipping duplicate")
                        skippedDuplicateCount++
                    return
                }
            }
                    }
        // --- END: DUPLICATE CHECK ---

                    // 4. Process photo data if present with memory management
                    var pictureData: ByteArray? = null
                    var hasPicture = false
        
        Log.d("VisitProcessor", "Processing photo for visit: ${dto.holyPlaceName}")
        Log.d("VisitProcessor", "Base64 string present: ${!dto.picture.isNullOrBlank()}")
        Log.d("VisitProcessor", "Base64 string length: ${dto.picture?.length ?: 0} chars")
                    
                    if (!dto.picture.isNullOrBlank()) {
                        try {
                            // Check photo size before decoding
                            val estimatedSize = (dto.picture.length * 3) / 4 // Base64 to binary size estimate
                Log.d("VisitProcessor", "Estimated photo size: ${estimatedSize} bytes for visit: ${dto.holyPlaceName}")
                
                            if (estimatedSize > 25 * 1024 * 1024) { // 25MB limit
                    Log.w("VisitProcessor", "Photo too large (${estimatedSize} bytes), skipping for visit: ${dto.holyPlaceName}")
                            } else {
                                pictureData = Base64.decode(dto.picture, Base64.DEFAULT)
                    Log.d("VisitProcessor", "Decoded photo to ${pictureData.size} bytes for visit: ${dto.holyPlaceName}")
                    
                    // Log first few bytes for debugging
                    val firstBytes = pictureData.take(10).joinToString(" ") { "%02X".format(it) }
                    Log.d("VisitProcessor", "Photo data header (first 10 bytes): $firstBytes for visit: ${dto.holyPlaceName}")
                                
                                // Validate that the decoded data looks like valid image data
                    val isValidImage = isValidImageData(pictureData)
                    Log.d("VisitProcessor", "Image validation result: $isValidImage for visit: ${dto.holyPlaceName}")
                    
                    if (pictureData.isNotEmpty() && isValidImage) {
                        // Compress photo if it's too large for SQLite storage
                        val compressedPictureData = compressPhotoIfNeeded(pictureData)
                        pictureData = compressedPictureData
                        
                                    hasPicture = true
                                    photoImportCount++
                        Log.d("VisitProcessor", "Successfully processed photo for visit: ${dto.holyPlaceName}")
                                } else {
                        Log.w("VisitProcessor", "Invalid or empty photo data for visit: ${dto.holyPlaceName}")
                                    pictureData = null
                                }
                            }
                        } catch (e: Exception) {
                Log.w("VisitProcessor", "Failed to decode Base64 photo data for visit: ${dto.holyPlaceName}", e)
                        }
        } else {
            Log.d("VisitProcessor", "No photo data for visit: ${dto.holyPlaceName}")
                    }
        
        // Store variables for later use
        val finalNameForDuplicateCheck = nameForDuplicateCheck
        val finalStartOfDayMillis = startOfDayMillis
        val finalEndOfDayMillis = endOfDayMillis
                    
                    // 5. Construct Visit entity
        Log.d("VisitProcessor", "Creating Visit entity for: ${dto.holyPlaceName}")
        Log.d("VisitProcessor", "Final picture data size: ${pictureData?.size ?: 0} bytes")
        Log.d("VisitProcessor", "Final hasPicture flag: $hasPicture")
        
                    val visitToImport = Visit(
                        id = 0, // Room will auto-generate
                        placeID = definitivePlaceId,
                        holyPlaceName = dto.holyPlaceName, // Store the name as it came from XML too
                        dateVisited = dateVisitedFromXml,
                        comments = dto.comments,
                        type = dto.type,
                        shiftHrs = dto.hoursWorked,
                        sealings = dto.sealings,
                        endowments = dto.endowments,
                        initiatories = dto.initiatories,
                        confirmations = dto.confirmations,
                        baptisms = dto.baptisms,
                        isFavorite = dto.isFavorite ?: false,
                        picture = pictureData,
                        hasPicture = hasPicture,
                        // Derive year from dateVisited if needed by your Visit model, or ensure it's nullable
                        // For Visit.kt, 'year' is nullable String. We can set it or leave it null.
                        year = SimpleDateFormat("yyyy", Locale.getDefault()).format(dateVisitedFromXml)
                    )

        // 5. Insert or update in DB immediately like iOS
        val existingVisit = visitDao.getVisitByNameAndDayRange(finalNameForDuplicateCheck, finalStartOfDayMillis, finalEndOfDayMillis)
        
        if (existingVisit != null) {
            // Only update existing visit if there's new photo data to add
            if (hasPicture && (existingVisit.picture == null || existingVisit.picture.isEmpty())) {
                val updatedVisit = existingVisit.copy(
                    picture = pictureData,
                    hasPicture = hasPicture
                )
                Log.d("VisitProcessor", "About to update visit ID: ${existingVisit.id} with ${pictureData?.size ?: 0} bytes of photo data")
                visitDao.updateVisit(updatedVisit)
                Log.d("VisitProcessor", "Updated existing visit with ID: ${existingVisit.id} for: ${dto.holyPlaceName}")
                updatedExistingCount++
            } else {
                // No new photo data to add, skip this visit
                Log.d("VisitProcessor", "Existing visit already has photo data or no new photo data, skipping: ${dto.holyPlaceName}")
                skippedDuplicateCount++
            }
        } else {
            // Insert new visit
            val insertedId = visitDao.insertVisit(visitToImport)
            Log.d("VisitProcessor", "Inserted new visit with ID: $insertedId for: ${dto.holyPlaceName}")
                    successfullyImportedCount++
        }
                    
                    // Force garbage collection periodically to free memory
                    if (successfullyImportedCount % 5 == 0) {
                        System.gc()
                    }
                }

    /**
     * Validates if the decoded photo data looks like valid image data
     */
    private fun isValidImageData(data: ByteArray): Boolean {
        if (data.isEmpty() || data.size < 4) return false
        
        // Check for common image file signatures
        val header = data.take(4)
        return when {
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> true // JPEG
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && 
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> true // PNG
            header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && 
            header[2] == 0x46.toByte() && header[3] == 0x38.toByte() -> true // GIF
            else -> false
        }
    }

    /**
     * Compresses photo data if it's too large for SQLite storage
     * @param originalData The original photo data
     * @param maxSizeBytes Maximum size in bytes (default: 2MB)
     * @return Compressed photo data or original if compression not needed
     */
    private fun compressPhotoIfNeeded(originalData: ByteArray, maxSizeBytes: Int = 2_000_000): ByteArray {
        if (originalData.size <= maxSizeBytes) {
            Log.d("VisitProcessor", "Photo size ${originalData.size} bytes is within limit, no compression needed")
            return originalData
        }

        Log.d("VisitProcessor", "Photo size ${originalData.size} bytes exceeds limit, compressing...")
        
        try {
            // Decode the original image
            val originalBitmap = BitmapFactory.decodeByteArray(originalData, 0, originalData.size)
            if (originalBitmap == null) {
                Log.w("VisitProcessor", "Failed to decode original image for compression")
                return originalData
            }

            // Ultra conservative compression - aim for 1.95MB target (97.5% of max)
            val targetSizeBytes = (maxSizeBytes * 0.975).toInt() // 1.95MB target
            val compressionRatio = sqrt(targetSizeBytes.toFloat() / originalData.size)
            
            // Ensure minimum reasonable size (at least 1800px on the longer side)
            val maxDimension = maxOf(originalBitmap.width, originalBitmap.height)
            val minDimension = 1800
            val sizeRatio = if (maxDimension > minDimension) compressionRatio else 1.0f
            
            val targetWidth = (originalBitmap.width * sizeRatio).toInt().coerceAtLeast(1000)
            val targetHeight = (originalBitmap.height * sizeRatio).toInt().coerceAtLeast(1000)
            
            Log.d("VisitProcessor", "Compressing from ${originalBitmap.width}x${originalBitmap.height} to ${targetWidth}x${targetHeight}")

            // Create compressed bitmap
            val compressedBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            originalBitmap.recycle() // Free memory

            // Compress to JPEG with quality adjustment
            val outputStream = ByteArrayOutputStream()
            var quality = 100 // Start with maximum quality
            var compressedData: ByteArray

            do {
                outputStream.reset()
                compressedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                compressedData = outputStream.toByteArray()
                quality -= 1 // Minimal quality steps
                Log.d("VisitProcessor", "Compression attempt: quality=$quality, size=${compressedData.size} bytes")
            } while (compressedData.size > maxSizeBytes && quality > 70) // Very high minimum quality

            compressedBitmap.recycle() // Free memory
            outputStream.close()

            Log.d("VisitProcessor", "Compression complete: ${originalData.size} -> ${compressedData.size} bytes")
            return compressedData

        } catch (e: Exception) {
            Log.w("VisitProcessor", "Failed to compress photo, using original", e)
            return originalData
        }
    }
}
