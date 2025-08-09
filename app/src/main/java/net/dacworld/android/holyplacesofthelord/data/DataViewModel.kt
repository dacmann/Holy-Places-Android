package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.lifecycle.AndroidViewModel // Change to AndroidViewModel
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import coil.Coil.imageLoader
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.data.UpdateDetails
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.util.HolyPlacesXmlParser // Ensure this is imported
import org.xmlpull.v1.XmlPullParser // Keep for fetchRemoteVersion
import org.xmlpull.v1.XmlPullParserFactory // Keep for fetchRemoteVersion
import java.io.ByteArrayOutputStream
import java.io.InputStream // Keep, though direct use in this file might be gone
import java.net.HttpURLConnection
import java.net.URL


class DataViewModel(
    application: Application,
    private val templeDao: TempleDao,
    private val visitDao: VisitDao,
    private val userPreferencesManager: UserPreferencesManager
) : AndroidViewModel(application) { // Inherit from AndroidViewModel

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _updateChangesSummary = MutableStateFlow<String>("")
    val updateChangesSummary: StateFlow<String> = _updateChangesSummary.asStateFlow()

    val allTemples: StateFlow<List<Temple>> = templeDao.getAllTemples()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList() // DB provides initial state (empty or populated by MyApplication)
        )

    // --- For Initial Seed Dialog (from MyApplication via UserPreferencesManager) ---
    val initialSeedUpdateDetails: StateFlow<UpdateDetails?> =
        userPreferencesManager.initialSeedDialogDetailsFlow // This flow comes from UserPreferencesManager
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    // For the version from the last successfully processed XML/DB state
    private val _currentDataVersion = MutableStateFlow<String?>(null)
    val currentDataVersion: StateFlow<String?> = _currentDataVersion.asStateFlow()

    // For the "changes date" from the last successfully processed XML/DB state
    private val _currentDataChangesDate = MutableStateFlow<String?>(null)
    val currentDataChangesDate: StateFlow<String?> = _currentDataChangesDate.asStateFlow()

    fun initialSeedDialogShown() {
        viewModelScope.launch {
            userPreferencesManager.clearShowInitialSeedDialogFlag()
        }
    }

    // --- For Remote XML Update Dialog ---
    private val _remoteUpdateDetails = MutableStateFlow<UpdateDetails?>(null)
    val remoteUpdateDetails: StateFlow<UpdateDetails?> = _remoteUpdateDetails.asStateFlow()

    fun remoteUpdateDialogShown() { // Renamed for clarity from clearRemoteUpdateDetails
        _remoteUpdateDetails.value = null
    }

    init {
        // Load initial change summary messages when ViewModel is created
        loadCurrentChangeSummary()
        viewModelScope.launch {
            userPreferencesManager.xmlVersionFlow.firstOrNull()?.let { persistedVersion -> // This uses your UserPreferencesManager
                _currentDataVersion.value = persistedVersion
                Log.d("DataViewModelInit", "Loaded currentDataVersion from Prefs: $persistedVersion")
            } ?: run {
                Log.d("DataViewModelInit", "No persisted version found in Prefs for currentDataVersion.")
            }
            // ...
        }
    }
    private fun loadCurrentChangeSummary() {
        viewModelScope.launch {
            val date = userPreferencesManager.changesDateFlow.firstOrNull()
            val msg1 = userPreferencesManager.changesMsg1Flow.firstOrNull()
            val msg2 = userPreferencesManager.changesMsg2Flow.firstOrNull()
            val msg3 = userPreferencesManager.changesMsg3Flow.firstOrNull()

            if (!date.isNullOrBlank() || !msg1.isNullOrBlank() || !msg2.isNullOrBlank() || !msg3.isNullOrBlank()) {
                _updateChangesSummary.value = formatChangesMessage(date, msg1, msg2, msg3)
            } else {
                _updateChangesSummary.value = "No update information available." // Or empty string
            }
        }
    }

    fun checkForUpdates(forceNetworkFetch: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val remoteVersion = fetchRemoteVersion()
                val localVersion = userPreferencesManager.xmlVersionFlow.firstOrNull()
                Log.d("DataViewModel", "Remote version: $remoteVersion, Local version: $localVersion")


                if (remoteVersion != null && remoteVersion != localVersion) {
                    //New version found. Downloading...
                    val holyPlacesData = downloadAndProcessPlaces() // Uses HolyPlacesXmlParser

                    if (holyPlacesData != null && holyPlacesData.temples.isNotEmpty()) {
                        userPreferencesManager.saveXmlVersion(remoteVersion)
                        userPreferencesManager.saveChangeMessages(
                            holyPlacesData.changesDate,
                            holyPlacesData.changesMsg1,
                            holyPlacesData.changesMsg2,
                            holyPlacesData.changesMsg3
                            // holyPlacesData.changesMsgQuiz // Persist if needed
                        )
                        loadCurrentChangeSummary() // Reload the summary text view

                        val dialogMessages = mutableListOf<String>()
                        holyPlacesData.changesMsg1?.takeIf { it.isNotBlank() }?.let { dialogMessages.add(it) }
                        holyPlacesData.changesMsg2?.takeIf { it.isNotBlank() }?.let { dialogMessages.add(it) }
                        holyPlacesData.changesMsg3?.takeIf { it.isNotBlank() }?.let { dialogMessages.add(it) }

                        val dialogTitle: String
                        if (holyPlacesData.temples.isNotEmpty()) {
                            // REMOVED: _lastUpdateMessage for successful update with temples
                            if (dialogMessages.isEmpty()) {
                                dialogMessages.add("Place data has been updated to version $remoteVersion. ${holyPlacesData.temples.size} places loaded.")
                            }
                            dialogTitle = "${holyPlacesData.changesDate ?: "Data"} Update"
                        } else {
                            // REMOVED: _lastUpdateMessage for successful update without temples
                            if (dialogMessages.isEmpty()) {
                                dialogMessages.add("Update messages for version $remoteVersion have been applied.")
                            }
                            dialogTitle = "${holyPlacesData.changesDate ?: "Messages"} Updated (v$remoteVersion)"
                        }

                        if (dialogMessages.isNotEmpty()) {
                            _remoteUpdateDetails.value = UpdateDetails(
                                updateTitle = dialogTitle,
                                messages = dialogMessages
                            )
                        }

                    } else { // holyPlacesData is null (download or parsing failed)
                        _remoteUpdateDetails.value = UpdateDetails(
                            updateTitle = "Update Failed",
                            messages = listOf("Failed to download or process update for version $remoteVersion. Please try again later.")
                        )
                    }
                } else if (remoteVersion == null) {
                    _remoteUpdateDetails.value = UpdateDetails(
                        updateTitle = "Update Check Failed",
                        messages = listOf("Could not check for updates. Please check your network connection and try again.")
                    )
                }
            } catch (e: Exception) {
                Log.e("DataViewModel", "Error during checkForUpdates: ${e.message}", e)
                _remoteUpdateDetails.value = UpdateDetails(
                    updateTitle = "Update Error",
                    messages = listOf("An unexpected error occurred during the update: ${e.message}")
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun formatChangesMessage(date: String?, msg1: String?, msg2: String?, msg3: String? /*, msgQuiz: String? */): String {
        // ... (implementation as before)
        val builder = StringBuilder()
        if (!date.isNullOrBlank()) { // Use isNullOrBlank for better checking
            builder.append("Changes as of: $date\n\n")
        }
        if (!msg1.isNullOrBlank()) {
            builder.append("$msg1\n\n")
        }
        if (!msg2.isNullOrBlank()) {
            builder.append("$msg2\n\n")
        }
        if (!msg3.isNullOrBlank()) {
            builder.append("$msg3\n")
        }
        return builder.toString().trim().ifEmpty { "No update information available." }
    }

    private suspend fun fetchRemoteVersion(): String? = withContext(Dispatchers.IO) {
        // ... (implementation as before, using .use for inputStream)
        var version: String? = null
        try {
            val url = URL("https://dacworld.net/holyplaces/hpVersion.xml")
            val connection = url.openConnection() as HttpURLConnection
            // ... (timeouts, connect)
            connection.connectTimeout = 15000
            connection.readTimeout = 10000
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { inputStream ->
                    val factory = XmlPullParserFactory.newInstance()
                    factory.isNamespaceAware = true
                    val parser = factory.newPullParser()
                    parser.setInput(inputStream, null)
                    var eventType = parser.eventType
                    var text: String? = null
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        val tagName = parser.name
                        when (eventType) {
                            XmlPullParser.TEXT -> text = parser.text
                            XmlPullParser.END_TAG -> if (tagName.equals("Version", ignoreCase = true)) {
                                version = text?.trim()
                            }
                        }
                        if (version != null) break
                        eventType = parser.next()
                    }
                }
            } else {
                Log.w("DataViewModel", "Version check failed: HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("DataViewModel", "Error fetching remote version: ${e.message}", e)
        }
        version
    }

    // DataViewModel.kt

// ... (other imports, imageLoader, fetchImageWithCoil as previously discussed) ...

    // Helper data class for picture update tasks

    // Correct way to initialize a shared ImageLoader instance for this ViewModel
    private val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(getApplication<Application>().applicationContext)
            .build()
    }

    private data class PictureUpdateTask(
        val templeId: String,
        val pictureUrlToFetch: String
    )

    private suspend fun downloadAndProcessPlaces(): HolyPlacesData? = withContext(Dispatchers.IO) {
        _isLoading.value = true
        var processingSuccess = false // Renamed for clarity
        try {
            val url = URL("https://dacworld.net/holyplaces/HolyPlaces.xml")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("DataViewModel", "Failed to download HolyPlaces.xml. HTTP Code: ${connection.responseCode}")
                return@withContext null
            }

            val parsedData = connection.inputStream.use { inputStream ->
                HolyPlacesXmlParser.parse(inputStream)
            }

            if (parsedData == null) {
                Log.e("DataViewModel", "Failed to parse HolyPlaces.xml. Parsed data is null.")
                return@withContext null
            }
            Log.d("DataViewModel", "Parsed ${parsedData.temples.size} temples from XML (Version: ${parsedData.version}).")

            val existingDbTemplesMap = templeDao.getAllTemplesForSyncOrList().associateBy { it.id }
            Log.d("DataViewModel", "Fetched ${existingDbTemplesMap.size} existing temple metadata from DB.")

            val xmlTempleIds = parsedData.temples.map { it.id }.toSet()
            val pictureUpdateTasks = mutableListOf<PictureUpdateTask>()

            val visitsToUpdate = mutableListOf<Visit>()

            // --- Metadata Pass ---
            for (xmlTempleFromParser in parsedData.temples) {
                Log.d("DataViewModel", "Starting metadata pass...")
                val xmlTemple = xmlTempleFromParser.copy(pictureData = null) // Ensure pictureData is null
                val existingDbTempleMeta = existingDbTemplesMap[xmlTemple.id]

                if (existingDbTempleMeta != null) { // Temple EXISTS in DB
                    // +++ START: Visit Name Update - Check and Prepare +++
                    if (existingDbTempleMeta.name != xmlTemple.name) {
                        Log.i("DataViewModel", "Temple name change DETECTED for ID ${xmlTemple.id}: From '${existingDbTempleMeta.name}' to '${xmlTemple.name}'")
                        val affectedVisits = visitDao.getVisitsListForTempleId(xmlTemple.id)
                        if (affectedVisits.isNotEmpty()) {
                            Log.d("DataViewModel", "Found ${affectedVisits.size} visits for temple ID ${xmlTemple.id} to update name.")
                            affectedVisits.forEach { visit ->
                                val updatedVisit = visit.copy(holyPlaceName = xmlTemple.name)
                                visitsToUpdate.add(updatedVisit)
                            }
                        }
                    }
                    // +++ END: Visit Name Update - Check and Prepare +++
                    // Compare metadata (Temple.equals() ignores pictureData).
                    // Also check if pictureUrl itself changed.
                    val metadataFieldsChanged = xmlTemple.copy(pictureUrl = existingDbTempleMeta.pictureUrl) != existingDbTempleMeta
                    val pictureUrlChanged = xmlTemple.pictureUrl != existingDbTempleMeta.pictureUrl

                    if (metadataFieldsChanged || pictureUrlChanged) {
                        Log.d("DataViewModel", "Updating metadata/URL for existing temple: ID ${xmlTemple.id}, Name: ${xmlTemple.name}")
                        // xmlTemple contains new metadata and new pictureUrl, with pictureData = null.
                        templeDao.update(xmlTemple)
                    }

                    if (xmlTemple.pictureUrl.isNotBlank()) {
                        if (pictureUrlChanged) { // Only flag if URL actually changed
                            Log.d("DataViewModel", "Flagging PIC UPDATE for existing ${xmlTemple.id}: URL changed from '${existingDbTempleMeta.pictureUrl}' to '${xmlTemple.pictureUrl}'.")
                            pictureUpdateTasks.add(PictureUpdateTask(xmlTemple.id, xmlTemple.pictureUrl))
                        } else {
                            if (!existingDbTempleMeta.hasLocalPictureData) { // Check if data is missing
                                Log.d("DataViewModel", "PictureURL same for ${xmlTemple.id}, but local picture_data missing. Flagging for download.")
                                pictureUpdateTasks.add(PictureUpdateTask(xmlTemple.id, xmlTemple.pictureUrl))
                            } else {
                                Log.d("DataViewModel", "No pic update needed for existing ${xmlTemple.id}, PictureURL same and local data likely present.")
                            }
                        }
                    } else { // XML pictureUrl is blank
                        if (existingDbTempleMeta.pictureUrl.isNotBlank()) {
                            Log.d("DataViewModel", "XML PictureURL blank for ${xmlTemple.id}. Clearing DB picture URL and data.")
                            templeDao.updatePicture(xmlTemple.id, null, null)
                        }
                    }
                } else { // Temple is NEW -> INSERT
                    Log.d("DataViewModel", "Inserting new temple: ID ${xmlTemple.id}, Name: ${xmlTemple.name}")
                    templeDao.insert(xmlTemple) // xmlTemple has pictureData = null
                    if (xmlTemple.pictureUrl.isNotBlank()) {
                        Log.d("DataViewModel", "Flagging PIC UPDATE for new temple ${xmlTemple.id} (URL: '${xmlTemple.pictureUrl}').")
                        pictureUpdateTasks.add(PictureUpdateTask(xmlTemple.id, xmlTemple.pictureUrl))
                    }
                }
            }

            // +++ START: Visit Name Update - Execution +++
            if (visitsToUpdate.isNotEmpty()) {
                val updatedRowCount = visitDao.updateVisits(visitsToUpdate)
                Log.i("DataViewModel", "Successfully updated $updatedRowCount visit records with new temple names.")
            }
            // +++ END: Visit Name Update - Execution +++

            // --- Picture Update Pass ---
            if (pictureUpdateTasks.isNotEmpty()) {
                Log.d("DataViewModel", "Starting picture update pass for ${pictureUpdateTasks.size} temples.")
                pictureUpdateTasks.map { task ->
                    async(Dispatchers.IO) {
                        Log.d("DataViewModel", "Fetching image for ${task.templeId} from ${task.pictureUrlToFetch}")
                        val imageData = fetchImageWithCoil(task.pictureUrlToFetch)
                        if (imageData != null) {
                            Log.d("DataViewModel", "Successfully fetched image for ${task.templeId}. Updating DB with picture data and URL: ${task.pictureUrlToFetch}.")
                            templeDao.updatePicture(task.templeId, task.pictureUrlToFetch, imageData)
                        } else {
                            Log.w("DataViewModel", "Failed to fetch image for ${task.templeId} (URL: ${task.pictureUrlToFetch}). Setting DB picture_data to null, URL to ${task.pictureUrlToFetch}.")
                            templeDao.updatePicture(task.templeId, task.pictureUrlToFetch, null)
                        }
                    }
                }.awaitAll()
                Log.d("DataViewModel", "Finished picture update pass.")
            } else {
                Log.d("DataViewModel", "No picture updates needed based on URL changes or new temples.")
            }

            // --- Delete Orphans ---
            val currentDbTempleIds = templeDao.getAllTempleIds().toSet()
            val orphanIds = currentDbTempleIds.filter { it !in xmlTempleIds }
            if (orphanIds.isNotEmpty()) {
                Log.d("DataViewModel", "Deleting ${orphanIds.size} orphan temples: $orphanIds")
                templeDao.deleteTemplesByIds(orphanIds)
            } else {
                Log.d("DataViewModel", "No orphan temples to delete.")
            }

            _currentDataVersion.value = parsedData.version
            _currentDataChangesDate.value = parsedData.changesDate ?: "Unknown"

            processingSuccess = true
            return@withContext parsedData

        } catch (e: Exception) {
            Log.e("DataViewModel", "Error in downloadAndProcessPlaces: ${e.message}", e)
            return@withContext null
        } finally {
            _isLoading.value = false
            if (processingSuccess) {
                Log.d("DataViewModel", "downloadAndProcessPlaces completed successfully.")
            } else {
                Log.e("DataViewModel", "downloadAndProcessPlaces completed with errors or was aborted.")
            }
        }
    }

    // Function to be called by UI (e.g., from a Fragment/ViewModel for the detail screen)
    // when a specific temple detail is needed
    suspend fun getTempleDetailsWithPicture(templeId: String): Temple? {
        // This ensures the BLOB is loaded only when this function is explicitly called.
        return templeDao.getTempleWithPictureById(templeId)
    }
    // fetchImageWithCoil using shared imageLoader, as discussed
    private suspend fun fetchImageWithCoil(imageUrl: String): ByteArray? {
        // ... (implementation using ViewModel's imageLoader, Dispatchers.IO, etc.)
        // Ensure this method is robust
        try {
            val request = ImageRequest.Builder(getApplication())
                .data(imageUrl)
                .allowHardware(false)
                .build()
            Log.d("ImageFetchCoil", "Requesting image via Coil from: $imageUrl")
            val result = imageLoader.execute(request)

            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                ByteArrayOutputStream().use { stream -> // Use .use for auto-closing
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val byteArray = stream.toByteArray()
                    Log.d("ImageFetchCoil", "Successfully decoded image from $imageUrl, size: ${byteArray.size} bytes")
                    return byteArray
                }
            } else {
                Log.w("ImageFetchCoil", "Coil result not SuccessResult for $imageUrl. Result: ${result::class.java.simpleName}")
                return null
            }
        } catch (e: Exception) {
            Log.e("ImageFetchCoil", "Error fetching/processing image from $imageUrl", e)
            return null
        }
    }

    // parseHolyPlacesXml, extractAnnouncedDateObject, extractOrderFromSnippet are REMOVED.
}