package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
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

class ExportImportViewModel(application: Application) : AndroidViewModel(application) {

    private val visitDao = AppDatabase.getDatabase(application).visitDao()
    private val templeDao = AppDatabase.getDatabase(application).templeDao()

    private val _operationStatus = MutableLiveData<OperationStatus>(OperationStatus.Idle)
    val operationStatus: LiveData<OperationStatus> = _operationStatus

    // --- EXPORT ---
    fun exportVisitsToXml(uri: Uri) {
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
                            XmlHelper.generateVisitsXml(visits, outputStream)
                        }
                    } ?: throw Exception("Failed to open file descriptor for writing.")
                }
                // Attempt to get the actual filename (might not always work perfectly across all UIs/providers)
                val filename = uri.lastPathSegment ?: getApplication<Application>().getString(R.string.default_export_filename_template,
                    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()))
                _operationStatus.postValue(OperationStatus.Success(getApplication<Application>().getString(R.string.export_successful, visits.size, filename)))
            } catch (e: Exception) {
                Log.e("ExportImportVM", "Export failed", e)
                _operationStatus.postValue(OperationStatus.Error(getApplication<Application>().getString(R.string.export_failed, e.localizedMessage ?: "Unknown error")))
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
    }
    // --- IMPORT ---
    fun importVisitsFromXml(uri: Uri) {
        _operationStatus.value = OperationStatus.InProgress
        viewModelScope.launch {
            var successfullyImportedCount = 0
            var skippedDuplicateCount = 0
            var skippedMissingTempleCount = 0
            var parsingErrorCount = 0
            // +++ NEW: Add a counter for corrected temple names +++
            var correctedTempleNameCount = 0

            try {
                val parsedVisitsDto: List<XmlHelper.VisitDto> = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
                        FileInputStream(parcelFileDescriptor.fileDescriptor).use { inputStream ->
                            XmlHelper.parseVisitsXml(inputStream)
                        }
                    } ?: throw Exception("Failed to open file descriptor for reading.")
                }

                if (parsedVisitsDto.isEmpty() && XmlHelper.lastParseError != null){
                    throw XmlHelper.lastParseError ?: Exception("Parsed DTO list is empty and no specific error was found.")
                }


                for (dto in parsedVisitsDto) {
                    // 1. Convert DTO date string to Date object
                    val dateVisitedFromXml = XmlHelper.parseDateString(dto.dateVisitedString)
                    if (dateVisitedFromXml == null) {
                        Log.w("Import", "Skipping visit due to unparseable date: ${dto.dateVisitedString} for ${dto.holyPlaceName}")
                        parsingErrorCount++
                        continue
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
                            continue
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
                            continue
                        }

                        currentTempleData = templeDao.getTempleByNameForSync(nameFromXml)

                        if (currentTempleData == null) {
                            Log.w("ImportVM", "Skipping visit (fallback mode). Temple not found in DB by XML name: '$nameFromXml'. Date: ${dto.dateVisitedString}")
                            skippedMissingTempleCount++
                            continue
                        }
                        definitivePlaceId = currentTempleData.id // ID obtained via name lookup
                        currentCorrectTempleName = currentTempleData.name // This IS the name from DB

                        // In this fallback path, if we found a temple by 'nameFromXml', its 'currentCorrectTempleName'
                        if (nameFromXml != currentCorrectTempleName) {
                            Log.i("ImportVM", "Temple name difference (Name-based fallback import). XML had '${nameFromXml}', which resolved to Temple ID '${definitivePlaceId}' with current name '${currentCorrectTempleName}'.")
                            correctedTempleNameCount++
                        }
                    }

                    // --- START: MODIFIED DUPLICATE CHECK ---
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
                    } else if (visitDao.visitExistsByNameAndDayRange(nameForDuplicateCheck, startOfDayMillis, endOfDayMillis)) {
                        Log.d("ImportVM", "Skipping duplicate visit (day range check) for Temple Name (from DTO) '$nameForDuplicateCheck' on date ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dateVisitedFromXml)}")
                        skippedDuplicateCount++
                        continue
                    }
                    // --- END: MODIFIED DUPLICATE CHECK ---

                    // 4. Construct Visit entity
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
                        picture = null, // Not importing pictures
                        // Derive year from dateVisited if needed by your Visit model, or ensure it's nullable
                        // For Visit.kt, 'year' is nullable String. We can set it or leave it null.
                        year = SimpleDateFormat("yyyy", Locale.getDefault()).format(dateVisitedFromXml)
                    )

                    // 5. Insert into DB
                    visitDao.insertVisit(visitToImport)
                    successfullyImportedCount++
                }
                _operationStatus.postValue(OperationStatus.Success(
                    getApplication<Application>().getString(R.string.import_report_successful_skipped_errors,
                        successfullyImportedCount, skippedDuplicateCount, skippedMissingTempleCount, parsingErrorCount)
                ))

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
