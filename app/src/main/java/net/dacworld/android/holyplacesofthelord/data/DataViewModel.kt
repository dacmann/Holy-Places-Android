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
            }
        } catch (e: Exception) {
            Log.e("DataViewModel", "Error downloading/processing places: ${e.message}", e)
        }
        return@withContext null
    }

    // parseHolyPlacesXml, extractAnnouncedDateObject, extractOrderFromSnippet are REMOVED.
}