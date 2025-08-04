package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.util.XmlHelper // We will create this helper next
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
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
                    val dateVisited = XmlHelper.parseDateString(dto.dateVisitedString)
                    if (dateVisited == null) {
                        Log.w("Import", "Skipping visit due to unparseable date: ${dto.dateVisitedString} for ${dto.holyPlaceName}")
                        parsingErrorCount++
                        continue
                    }

                    // 2. Get placeID from holyPlaceName
                    val placeId = dto.holyPlaceName?.let { templeDao.getTempleIdByName(it) }
                    if (placeId == null) {
                        Log.w("Import", "Skipping visit, Temple not found for name: ${dto.holyPlaceName}")
                        skippedMissingTempleCount++
                        continue
                    }

                    // 3. Check for duplicates in DB
                    val dateMillis = dateVisited.time
                    if (dto.holyPlaceName != null && visitDao.visitExistsByNameAndDate(dto.holyPlaceName, dateMillis)) {
                        skippedDuplicateCount++
                        continue
                    }

                    // 4. Construct Visit entity
                    val visitToImport = Visit(
                        id = 0, // Room will auto-generate
                        placeID = placeId,
                        holyPlaceName = dto.holyPlaceName, // Store the name as it came from XML too
                        dateVisited = dateVisited,
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
                        year = SimpleDateFormat("yyyy", Locale.getDefault()).format(dateVisited)
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
