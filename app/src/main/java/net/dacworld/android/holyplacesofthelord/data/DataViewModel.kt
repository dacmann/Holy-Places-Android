package net.dacworld.android.holyplacesofthelord.data

import android.util.Log
import androidx.lifecycle.ViewModel // Reverted from AndroidViewModel if Application context is not directly needed here
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
import net.dacworld.android.holyplacesofthelord.data.UpdateDetails
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.util.HolyPlacesXmlParser // Ensure this is imported
import org.xmlpull.v1.XmlPullParser // Keep for fetchRemoteVersion
import org.xmlpull.v1.XmlPullParserFactory // Keep for fetchRemoteVersion
import java.io.InputStream // Keep, though direct use in this file might be gone
import java.net.HttpURLConnection
import java.net.URL

// Assuming HolyPlacesData class is defined (as used by HolyPlacesXmlParser)

class DataViewModel(
    // Application context is removed if ViewModel doesn't directly access assets for initial load
    private val templeDao: TempleDao,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() { // Reverted to ViewModel

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastUpdateMessage = MutableStateFlow<String?>(null)
    val lastUpdateMessage: StateFlow<String?> = _lastUpdateMessage.asStateFlow()

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
    fun clearLastUpdateMessage() {
        _lastUpdateMessage.value = null
    }
    fun checkForUpdates(forceNetworkFetch: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            var localDataExists = allTemples.value.isNotEmpty()

            // Simplified initial message
            if (localDataExists && !forceNetworkFetch) {
                _lastUpdateMessage.value = "Using cached data. Checking remote version..."
            } else if (!localDataExists) {
                _lastUpdateMessage.value = "No local data. Checking for updates..."
            } else {
                _lastUpdateMessage.value = "Checking for updates..."
            }

            // Short-circuit if not forcing network and data exists (quick version check only)
            if (!forceNetworkFetch && localDataExists) {
                try {
                    val remoteVersionCheck = fetchRemoteVersion()
                    val localVersionCheck = userPreferencesManager.xmlVersionFlow.firstOrNull()
                    if (remoteVersionCheck != null && remoteVersionCheck == localVersionCheck) {
                        _lastUpdateMessage.value = "Data is up to date (Version: $localVersionCheck)."
                    } else if (remoteVersionCheck != null && remoteVersionCheck != localVersionCheck) {
                        _lastUpdateMessage.value = "Using cached data. A new version ($remoteVersionCheck) is available."
                    } else if (remoteVersionCheck == null) {
                        _lastUpdateMessage.value = "Using cached data. Could not verify remote version."
                    }
                } catch (e: Exception) {
                    _lastUpdateMessage.value = "Error checking remote version: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
                return@launch
            }

            try {
                val remoteVersion = fetchRemoteVersion()
                val localVersion = userPreferencesManager.xmlVersionFlow.firstOrNull()
                Log.d("DataViewModel", "Remote version: $remoteVersion, Local version: $localVersion")


                if (remoteVersion != null && remoteVersion != localVersion) {
                    _lastUpdateMessage.value = "New version found: $remoteVersion. Downloading..."
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

                        if (holyPlacesData.temples.isNotEmpty()) {
                            _lastUpdateMessage.value = "Data updated to version: $remoteVersion. ${holyPlacesData.temples.size} places loaded."
                            if (dialogMessages.isEmpty()) { // Add a default message if specific changes aren't listed but temples updated
                                dialogMessages.add("Place data has been updated to version $remoteVersion.")
                            }
                        } else { // No temples in the update, but maybe message changes
                            _lastUpdateMessage.value = "Data file processed for version $remoteVersion. Update messages applied."
                            if (dialogMessages.isEmpty()) { // Add a default message if specific changes aren't listed
                                dialogMessages.add("Update messages for version $remoteVersion have been applied.")
                            }
                        }

                        // Post details for the dialog
                        if (dialogMessages.isNotEmpty()) {
                            _remoteUpdateDetails.value = UpdateDetails(
                                updateTitle = "${holyPlacesData.changesDate ?: "Data"} Update (v$remoteVersion)",
                                messages = dialogMessages
                            )
                        }

                    } else { // holyPlacesData is null (download or parsing failed)
                        _lastUpdateMessage.value = "Failed to download or process update for version $remoteVersion."
                    }
                } else if (remoteVersion == null) {
                    _lastUpdateMessage.value = "Could not check for updates (network or parsing error for version file)."
                } else { // remoteVersion == localVersion
                    _lastUpdateMessage.value = "Data is up to date (Version: $localVersion)."
                    loadCurrentChangeSummary() // Ensure summary is current
                }
            } catch (e: Exception) {
                _lastUpdateMessage.value = "Error during update: ${e.message}"
                Log.e("DataViewModel", "Error during checkForUpdates: ${e.message}", e)
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
                withContext(Dispatchers.Main) {
                    _lastUpdateMessage.value = "Version check failed: HTTP ${connection.responseCode}"
                }
            }
        } catch (e: Exception) {
            Log.e("DataViewModel", "Error fetching remote version: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _lastUpdateMessage.value = "Error fetching remote version: ${e.message}"
            }
        }
        version
    }

    private suspend fun downloadAndProcessPlaces(): HolyPlacesData? = withContext(Dispatchers.IO) {
        // ... (implementation as before, using HolyPlacesXmlParser.parse and .use for inputStream)
        try {
            val url = URL("https://dacworld.net/holyplaces/HolyPlaces.xml")
            val connection = url.openConnection() as HttpURLConnection
            // ... (timeouts, connect)
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { inputStream ->
                    val parsedData = HolyPlacesXmlParser.parse(inputStream) // Uses the utility

                    if (parsedData.temples.isNotEmpty()) {
                        templeDao.clearAndInsertAll(parsedData.temples)
                    } else if (parsedData.version != null) {
                        Log.w("DataViewModel", "Downloaded HolyPlaces.xml (Version: ${parsedData.version}), but no <Place> elements were found.")
                        // If an empty but valid XML is received, you might want to clear the local DB.
                        // For example: templeDao.clearAllTemples()
                        // This behavior needs to be defined based on product requirements.
                        // For now, it only inserts if temples are present.
                    }
                    return@withContext parsedData
                }
            } else {
                Log.w("DataViewModel", "Download failed: HTTP ${connection.responseCode}")
                withContext(Dispatchers.Main) {
                    _lastUpdateMessage.value = "Download failed: HTTP ${connection.responseCode}"
                }
            }
        } catch (e: Exception) {
            Log.e("DataViewModel", "Error downloading/processing places: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _lastUpdateMessage.value = "Error downloading/processing places: ${e.message}"
            }
        }
        return@withContext null
    }

    // parseHolyPlacesXml, extractAnnouncedDateObject, extractOrderFromSnippet are REMOVED.
}