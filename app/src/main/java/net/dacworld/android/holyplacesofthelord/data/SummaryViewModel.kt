// summary/SummaryViewModel.kt
package net.dacworld.android.holyplacesofthelord.summary

import android.app.Application
import android.util.Xml
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.R // For strings like default_quote
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.model.Visit // Your Visit model from context
// No need to import Temple model here if we only use TempleDao for counts
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Calendar
import java.util.Date // Using java.util.Date from Visit.kt
import kotlin.random.Random

// Data classes for structured summary data
data class HolyPlaceStat(
    val typeName: String,
    val visitedCount: Int,
    val totalCount: Int,
    val colorRes: Int // Color resource ID from colors.xml
)

data class TempleVisitYearStats(
    val year: String, // "2023", "2024", "Total"
    val attended: Int = 0,
    val uniqueTemples: Int = 0,
    val hoursWorked: Double = 0.0,
    val sealings: Int = 0,
    val endowments: Int = 0,
    val initiatories: Int = 0,
    val confirmations: Int = 0,
    val baptisms: Int = 0
) {
    val totalOrdinances: Int
        get() = sealings + endowments + initiatories + confirmations + baptisms
}

data class MostVisitedPlaceItem(
    val placeName: String,
    val visitCount: Int,
    val typeColorRes: Int // Color resource from colors.xml for the place name
)

class SummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val visitDao: VisitDao = AppDatabase.getDatabase(application).visitDao()
    private val templeDao: TempleDao = AppDatabase.getDatabase(application).templeDao()

    private val _quote = MutableLiveData<String>()
    val quote: LiveData<String> = _quote
    private var allQuotes: List<String> = emptyList()

    private val _holyPlacesStats = MutableLiveData<List<HolyPlaceStat>>()
    val holyPlacesStats: LiveData<List<HolyPlaceStat>> = _holyPlacesStats

    private val _currentYearLabel = MutableLiveData<String>()
    val currentYearLabel: LiveData<String> = _currentYearLabel

    private val _previousYearLabel = MutableLiveData<String>()
    val previousYearLabel: LiveData<String> = _previousYearLabel

    private val _templeVisitCurrentYearStats = MutableLiveData<TempleVisitYearStats>()
    val templeVisitCurrentYearStats: LiveData<TempleVisitYearStats> = _templeVisitCurrentYearStats

    private val _templeVisitPreviousYearStats = MutableLiveData<TempleVisitYearStats>()
    val templeVisitPreviousYearStats: LiveData<TempleVisitYearStats> = _templeVisitPreviousYearStats

    private val _templeVisitTotalStats = MutableLiveData<TempleVisitYearStats>()
    val templeVisitTotalStats: LiveData<TempleVisitYearStats> = _templeVisitTotalStats

    private val _mostVisitedPlaces = MutableLiveData<List<MostVisitedPlaceItem>>()
    val mostVisitedPlaces: LiveData<List<MostVisitedPlaceItem>> = _mostVisitedPlaces

    // Define place type constants as they appear in Visit.type and Temple.type
    companion object {
        const val TYPE_TEMPLE = "T" // Example from your TempleContract
        const val TYPE_HISTORICAL_SITE = "H" // Assuming "H" for Historical Sites
        const val TYPE_VISITORS_CENTER = "V" // Assuming "V" for Visitors' Centers
        const val TYPE_ANNOUNCED_TEMPLES = "A" // From your colors.xml (t2_announced_temples)
        const val TYPE_UNDER_CONSTRUCTION = "C" // From your colors.xml (t2_under_construction)
        // Add any other types you use and have colors for
    }

    init {
        loadQuotes()
        loadSummaryData()
    }

    private fun loadQuotes() {
        viewModelScope.launch {
            allQuotes = parseQuotesFromXml()
            selectRandomQuote()
        }
    }

    fun selectRandomQuote() {
        if (allQuotes.isNotEmpty()) {
            _quote.value = allQuotes[Random.nextInt(allQuotes.size)]
        } else {
            // Ensure you have this string resource
            _quote.value = getApplication<Application>().getString(R.string.default_quote_summary)
        }
    }

    fun loadSummaryData() {
        viewModelScope.launch {
            // Fetch all necessary data
            val allVisits = withContext(Dispatchers.IO) { visitDao.getAllVisitsListForExport() }

            // Fetch total counts for each place type concurrently
            val totalTemplesCountDeferred = async(Dispatchers.IO) { templeDao.getCountByType(TYPE_TEMPLE) }
            val totalHistoricalCountDeferred = async(Dispatchers.IO) { templeDao.getCountByType(TYPE_HISTORICAL_SITE) }
            val totalVCCountDeferred = async(Dispatchers.IO) { templeDao.getCountByType(TYPE_VISITORS_CENTER) }
            // Add deferred calls for other types if they are part of the "Holy Places" summary section
            // For example, if "Announced" or "Under Construction" are also listed there with totals.
            // If not, these specific totals aren't strictly needed here unless displayed in that section.

            // --- Holy Places Section ---
            val visitedPlaceNamesByType = mutableMapOf<String, MutableSet<String>>()

            for (visit in allVisits) {
                if (visit.type != null && visit.holyPlaceName != null) {
                    visitedPlaceNamesByType.getOrPut(visit.type) { mutableSetOf() }.add(visit.holyPlaceName)
                }
            }

            _holyPlacesStats.postValue(listOfNotNull(
                HolyPlaceStat(
                    "Temples",
                    visitedPlaceNamesByType[TYPE_TEMPLE]?.size ?: 0,
                    totalTemplesCountDeferred.await(),
                    net.dacworld.android.holyplacesofthelord.R.color.t2_temples // Direct R reference
                ),
                HolyPlaceStat(
                    "Historic Sites", // Display name
                    visitedPlaceNamesByType[TYPE_HISTORICAL_SITE]?.size ?: 0,
                    totalHistoricalCountDeferred.await(),
                    net.dacworld.android.holyplacesofthelord.R.color.t2_historic_site
                ),
                HolyPlaceStat(
                    "Visitors' Centers", // Display name
                    visitedPlaceNamesByType[TYPE_VISITORS_CENTER]?.size ?: 0,
                    totalVCCountDeferred.await(),
                    net.dacworld.android.holyplacesofthelord.R.color.t2_visitors_centers
                )
                // Add other HolyPlaceStat objects here if "Announced", "Under Construction"
                // are part of this summary table with their own visited/total counts.
            ))

            // --- Temple Visits Section (Focuses only on visits where Visit.type == "T") ---
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val previousYear = currentYear - 1

            _currentYearLabel.postValue(currentYear.toString())
            _previousYearLabel.postValue(previousYear.toString())

            val actualTempleVisits = allVisits.filter { it.type == TYPE_TEMPLE }

            _templeVisitCurrentYearStats.postValue(calculateYearStatsForTemples(actualTempleVisits, currentYear))
            _templeVisitPreviousYearStats.postValue(calculateYearStatsForTemples(actualTempleVisits, previousYear))
            _templeVisitTotalStats.postValue(calculateYearStatsForTemples(actualTempleVisits, null)) // Overall

            // --- Most Visited Section (All place types) ---
            val mostVisitedItems = allVisits
                .filter { it.holyPlaceName != null } // Ensure name exists
                .groupBy { it.holyPlaceName!! } // Group by non-null place name
                .map { entry ->
                    val firstVisitInGroup = entry.value.first() // Get type from the first visit
                    Triple(entry.key, entry.value.size, firstVisitInGroup.type)
                }
                .sortedByDescending { it.second } // Sort by count
                .take(12)
                .map { (name, count, type) ->
                    val colorRes = when (type) {
                        TYPE_TEMPLE -> net.dacworld.android.holyplacesofthelord.R.color.t2_temples
                        TYPE_HISTORICAL_SITE -> net.dacworld.android.holyplacesofthelord.R.color.t2_historic_site
                        TYPE_VISITORS_CENTER -> net.dacworld.android.holyplacesofthelord.R.color.t2_visitors_centers
                        TYPE_ANNOUNCED_TEMPLES -> net.dacworld.android.holyplacesofthelord.R.color.t2_announced_temples
                        TYPE_UNDER_CONSTRUCTION -> net.dacworld.android.holyplacesofthelord.R.color.t2_under_construction
                        else -> net.dacworld.android.holyplacesofthelord.R.color.alt_grey_text // Fallback color
                    }
                    MostVisitedPlaceItem(name, count, colorRes)
                }
            _mostVisitedPlaces.postValue(mostVisitedItems)
        }
    }

    private fun calculateYearStatsForTemples(
        templeVisits: List<Visit>, // These are already filtered to be Visit.type == "T"
        targetYear: Int?
    ): TempleVisitYearStats {
        val visitsForYear = templeVisits.filter { visit ->
            visit.dateVisited != null && (targetYear == null || {
                val calendar = Calendar.getInstance()
                calendar.time = visit.dateVisited // java.util.Date from Visit.kt
                calendar.get(Calendar.YEAR) == targetYear
            }())
        }

        if (visitsForYear.isEmpty()) {
            return TempleVisitYearStats(year = targetYear?.toString() ?: "Total")
        }

        val attended = visitsForYear.count() // All visits in this list are to Temples
        val uniqueTemples = visitsForYear.distinctBy { it.holyPlaceName }.size
        val hoursWorked = visitsForYear.sumOf { it.shiftHrs ?: 0.0 }

        val sealings = visitsForYear.sumOf { (it.sealings ?: 0).toInt() }
        val endowments = visitsForYear.sumOf { (it.endowments ?: 0).toInt() }
        val initiatories = visitsForYear.sumOf { (it.initiatories ?: 0).toInt() }
        val confirmations = visitsForYear.sumOf { (it.confirmations ?: 0).toInt() }
        val baptisms = visitsForYear.sumOf { (it.baptisms ?: 0).toInt() }

        return TempleVisitYearStats(
            year = targetYear?.toString() ?: "Total",
            attended = attended,
            uniqueTemples = uniqueTemples,
            hoursWorked = hoursWorked,
            sealings = sealings,
            endowments = endowments,
            initiatories = initiatories,
            confirmations = confirmations,
            baptisms = baptisms
        )
    }

    private suspend fun parseQuotesFromXml(): List<String> = withContext(Dispatchers.IO) {
        val quotesList = mutableListOf<String>()
        try {
            val inputStream: InputStream = getApplication<Application>().resources.openRawResource(R.raw.summary_quotes)
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            var eventType = parser.eventType
            var currentText = ""
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> if (parser.name == "quote") currentText = ""
                    XmlPullParser.TEXT -> currentText += parser.text
                    XmlPullParser.END_TAG -> if (parser.name == "quote") {
                        quotesList.add(currentText.trim().replace("\\r\\n", "\n"))
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()
        } catch (e: Exception) { // Broader catch for simplicity in example
            e.printStackTrace()
            // In a real app, consider more specific exception handling and logging
        }
        quotesList
    }
}
