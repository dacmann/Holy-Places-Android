package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.model.Temple
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale // Still needed for parsing input string if it has month names

/**
 * Extracts the announced date from a snippet string.
 * Expected format in snippet: "Announced DD Month YYYY"
 * Returns a LocalDate object, or null if not found/parsed.
 */
private fun extractAnnouncedDateObject(snippet: String): LocalDate? {
    val snippetLower = snippet.lowercase(Locale.ROOT)
    val regex = """announced\s+(\d{1,2}\s+[a-zA-Z]+\s+\d{4})""".toRegex(RegexOption.IGNORE_CASE)
    val matchResult = regex.find(snippetLower)

    if (matchResult != null && matchResult.groupValues.size > 1) {
        val dateStrComponent = matchResult.groupValues[1].trim()
        try {
            // Input Formatter expects something like "1 january 2023"
            // DateTimeFormatter is thread-safe
            val inputFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
            return LocalDate.parse(dateStrComponent, inputFormatter)
        } catch (e: DateTimeParseException) {
            // Log parsing failure for the date string component
            println("⚠️ ViewModel: Failed to parse date string component '$dateStrComponent' from snippet. Error: ${e.message}")
            return null
        }
    }
    return null
}

/**
 * Extracts the leading number from the snippet string to be used as 'order'.
 * If no number is found at the beginning, defaults to 200.
 */
private fun extractOrderFromSnippet(snippet: String): Short {
    val numberString = StringBuilder()
    for (char in snippet) {
        if (char.isDigit()) {
            numberString.append(char)
        } else {
            break // Stop at the first non-digit
        }
    }
    return if (numberString.isNotEmpty()) {
        numberString.toString().toShortOrNull() ?: 300 // Convert to Short, default if too long or invalid
    } else {
        300 // Default if no leading digits
    }
}

// Data class to hold results from parsing HolyPlaces.xml
data class HolyPlacesData(
    val temples: List<Temple>,
    val version: String? = null, // Version from within HolyPlaces.xml
    val changesDate: String? = null,
    val changesMsg1: String? = null,
    val changesMsg2: String? = null,
    val changesMsg3: String? = null,
    val changesMsgQuiz: String? = null // If you also need this
)

class DataViewModel(application: Application) : AndroidViewModel(application) {

    private val templeDao = AppDatabase.getDatabase(application).templeDao()
    private val userPreferencesManager = UserPreferencesManager(application)

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _lastUpdateMessage = MutableLiveData<String>()
    val lastUpdateMessage: LiveData<String> = _lastUpdateMessage

    // LiveData to hold the combined changes message for the UI
    private val _updateChangesSummary = MutableLiveData<String?>()
    val updateChangesSummary: LiveData<String?> = _updateChangesSummary
    val allTemples: StateFlow<List<Temple>> = templeDao.getAllTemples()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep active for 5s after last subscriber gone
            initialValue = emptyList() // Initial value while data is loading
        )


    init {
        // Optionally load last saved changes messages on init
        viewModelScope.launch {
            val date = userPreferencesManager.changesDateFlow.firstOrNull()
            val msg1 = userPreferencesManager.changesMsg1Flow.firstOrNull()
            val msg2 = userPreferencesManager.changesMsg2Flow.firstOrNull()
            val msg3 = userPreferencesManager.changesMsg3Flow.firstOrNull()
            if (date != null || msg1 != null || msg2 != null || msg3 != null) {
                _updateChangesSummary.value = formatChangesMessage(date, msg1, msg2, msg3)
            }
        }
    }

    fun checkForUpdates(forceNetworkFetch: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            var localDataLoadedSuccessfully = false
            // Attempt to load from local DB first if not forcing network (or as a fallback)
            // The StateFlow `allTemples` is already doing this reactively.

            if (allTemples.value.isNotEmpty()) {
                _lastUpdateMessage.value = "Displaying cached data. Checking for updates..."
                localDataLoadedSuccessfully = true
            } else {
                _lastUpdateMessage.value = "No cached data. Checking for updates..."
            }

            if (!forceNetworkFetch && localDataLoadedSuccessfully) {
                _isLoading.value = false
                _lastUpdateMessage.value = "Using cached data. Data is up to date based on last check."

                return@launch // Don't proceed to network if not forced and local data exists
            }
            try {
                val remoteVersion = fetchRemoteVersion() // This is from hpVersion.xml
                val localVersion = userPreferencesManager.xmlVersionFlow.firstOrNull()

                if (remoteVersion != null && remoteVersion != localVersion) {
                    _lastUpdateMessage.value = "New version found: $remoteVersion. Downloading..."
                    val holyPlacesData = downloadAndProcessPlaces() // Now returns HolyPlacesData?
                    if (holyPlacesData != null && holyPlacesData.temples.isNotEmpty()) {
                        // Save the new version (from hpVersion.xml) to DataStore
                        userPreferencesManager.saveXmlVersion(remoteVersion)

                        // Save the extracted messages
                        userPreferencesManager.saveChangeMessages(
                            holyPlacesData.changesDate,
                            holyPlacesData.changesMsg1,
                            holyPlacesData.changesMsg2,
                            holyPlacesData.changesMsg3
                        )

                        _lastUpdateMessage.value = "Data updated to version: $remoteVersion."
                        _updateChangesSummary.value = formatChangesMessage(
                            holyPlacesData.changesDate,
                            holyPlacesData.changesMsg1,
                            holyPlacesData.changesMsg2,
                            holyPlacesData.changesMsg3
                        )
                    } else if (holyPlacesData != null && holyPlacesData.temples.isEmpty() && holyPlacesData.version != null) {
                        // This case implies the XML was parsed but contained no places,
                        // which might be an error in the XML or parsing.
                        // Still, save the version and messages if they were parsed.
                        userPreferencesManager.saveXmlVersion(remoteVersion)
                        userPreferencesManager.saveChangeMessages(
                            holyPlacesData.changesDate,
                            holyPlacesData.changesMsg1,
                            holyPlacesData.changesMsg2,
                            holyPlacesData.changesMsg3
                        )
                        _lastUpdateMessage.value =
                            "Data file processed for version $remoteVersion, but no places were found. Update messages applied."
                        _updateChangesSummary.value = formatChangesMessage(
                            holyPlacesData.changesDate,
                            holyPlacesData.changesMsg1,
                            holyPlacesData.changesMsg2,
                            holyPlacesData.changesMsg3
                        )
                    } else {
                        _lastUpdateMessage.value = "Failed to update data or parse places."
                    }
                } else if (remoteVersion == null) {
                    _lastUpdateMessage.value =
                        "Could not check for updates (network or parsing error for version file)."
                } else {
                    _lastUpdateMessage.value = "Data is up to date (Version: $localVersion)."
                    // Optionally, reload and show existing messages if needed, though init block handles this
                }
            } catch (e: Exception) {
                _lastUpdateMessage.value = "Error during update: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun formatChangesMessage(date: String?, msg1: String?, msg2: String?, msg3: String?): String {
        val builder = StringBuilder()
        if (!date.isNullOrEmpty()) {
            builder.append("Changes as of: $date\n\n")
        }
        if (!msg1.isNullOrEmpty()) {
            builder.append("$msg1\n\n")
        }
        if (!msg2.isNullOrEmpty()) {
            builder.append("$msg2\n\n")
        }
        if (!msg3.isNullOrEmpty()) {
            builder.append("$msg3\n")
        }
        return builder.toString().trim()
    }

    private suspend fun fetchRemoteVersion(): String? = withContext(Dispatchers.IO) {
        var version: String? = null
        try {
            val url = URL("https://dacworld.net/holyplaces/hpVersion.xml") // Actual URL
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 10000
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
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
                inputStream.close()
            } else {
                _lastUpdateMessage.postValue("Version check failed: HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _lastUpdateMessage.postValue("Error fetching remote version: ${e.message}")
        }
        version
    }

    // Now returns HolyPlacesData? to include messages and allow for partial success
    private suspend fun downloadAndProcessPlaces(): HolyPlacesData? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://dacworld.net/holyplaces/HolyPlaces.xml") // Actual URL
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val parsedData = parseHolyPlacesXml(inputStream) // Renamed for clarity

                if (parsedData.temples.isNotEmpty()) {
                    templeDao.clearAndInsertAll(parsedData.temples)
                    // Messages are already in parsedData
                } else if (parsedData.version != null) {
                    // Log or indicate that places were empty but header was parsed
                    _lastUpdateMessage.postValue("Parsed HolyPlaces.xml (Version: ${parsedData.version}), but no <Place> elements were found.")
                }
                return@withContext parsedData // Return all parsed data
            } else {
                _lastUpdateMessage.postValue("Download failed: HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _lastUpdateMessage.postValue("Error downloading/processing places: ${e.message}")
        }
        return@withContext null // Indicate failure
    }

    // Renamed for clarity and now returns HolyPlacesData
    private fun parseHolyPlacesXml(inputStream: InputStream): HolyPlacesData {
        val temples = mutableListOf<Temple>()
        var currentTemple: Temple? = null
        var text: String? = null

        var xmlVersion: String? = null
        var changesDate: String? = null
        var changesMsg1: String? = null
        var changesMsg2: String? = null
        var changesMsg3: String? = null
        var changesMsgQuiz: String? = null

        var inDocumentRoot = true

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.trim()

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("Place", ignoreCase = true)) {
                            // CORRECTED INSTANTIATION
                            // Assuming 'id' and 'name' are the required fields for Temple constructor
                            currentTemple = Temple()
                            inDocumentRoot = false
                        }
                    }

                    XmlPullParser.TEXT -> {
                        text = parser.text?.trim()
                    }

                    XmlPullParser.END_TAG -> {
                        if (inDocumentRoot) {
                            when {
                                tagName.equals("Version", ignoreCase = true) -> xmlVersion = text
                                tagName.equals("ChangesDate", ignoreCase = true) -> changesDate = text
                                tagName.equals("ChangesMsg1", ignoreCase = true) -> changesMsg1 = text
                                tagName.equals("ChangesMsg2", ignoreCase = true) -> changesMsg2 = text
                                tagName.equals("ChangesMsg3", ignoreCase = true) -> changesMsg3 = text
                                tagName.equals("ChangesMsgQuiz", ignoreCase = true) -> changesMsgQuiz = text
                                tagName.equals("Document", ignoreCase = true) -> { /* End of document */ }
                            }
                        } else currentTemple?.let { temple -> // Parsing elements inside <Place>
                            var processSnippetForExtras = false
                            when {
                                tagName.equals("ID", ignoreCase = true) -> temple.id = text ?: ""
                                tagName.equals("name", ignoreCase = true) -> temple.name = text ?: ""
                                tagName.equals("fhc", ignoreCase = true) -> temple.fhCode = text
                                tagName.equals("Snippet", ignoreCase = true) -> {
                                    temple.snippet = text ?: ""
                                    processSnippetForExtras = true // Flag to process after snippet is set
                                }
                                tagName.equals("site_url", ignoreCase = true) -> temple.siteUrl = text ?: ""
                                tagName.equals("infoURL", ignoreCase = true) -> temple.infoUrl = text //
                                tagName.equals("image", ignoreCase = true) -> temple.pictureUrl = text ?: ""
                                tagName.equals("SqFt", ignoreCase = true) -> temple.sqFt = text?.toIntOrNull()
                                tagName.equals("Address", ignoreCase = true) -> temple.address = text ?: ""
                                tagName.equals("CityState", ignoreCase = true) -> temple.cityState = text ?: ""
                                tagName.equals("Country", ignoreCase = true) -> temple.country = text ?: ""
                                tagName.equals("Phone", ignoreCase = true) -> temple.phone = text ?: ""
                                tagName.equals("longitude", ignoreCase = true) -> temple.longitude = text?.toDoubleOrNull() ?: 0.0
                                tagName.equals("latitude", ignoreCase = true) -> temple.latitude = text?.toDoubleOrNull() ?: 0.0
                                tagName.equals("type", ignoreCase = true) -> temple.type = text ?: ""

                                tagName.equals("Place", ignoreCase = true) -> {
                                    // **Process snippet for Order and AnnouncedDate HERE, before adding temple**
                                    // This ensures snippet is fully populated before extraction attempts.
                                    if (temple.snippet.isNotBlank()) {
                                        temple.announcedDate = extractAnnouncedDateObject(temple.snippet)
                                        temple.order = extractOrderFromSnippet(temple.snippet)

                                        // Logging similar to Swift (optional)
                                        if ((temple.type == "T" || temple.type == "C" || temple.type == "A") && temple.announcedDate == null) {
                                            // Consider a more robust logging mechanism if this is critical
                                            println("⚠️ ViewModel: Could not parse announced date for: ${temple.name} — Snippet: ${temple.snippet}")
                                        }
                                    } else {
                                        // If snippet is blank, set defaults or handle as error
                                        temple.order = 300 // Default order if no snippet
                                        temple.announcedDate = null
                                    }
                                    if (temple.id.isNotBlank()) { // Ensure ID is present if it's your primary key
                                        temples.add(temple)
                                    } else {
                                        _lastUpdateMessage.postValue("Skipped a place due to missing ID during parsing.")
                                    }
                                    currentTemple = null
                                    inDocumentRoot = true
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _lastUpdateMessage.postValue("Error parsing XML data: ${e.message}")
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return HolyPlacesData(temples, xmlVersion, changesDate, changesMsg1, changesMsg2, changesMsg3, changesMsgQuiz)
    }
}