// summary/SummaryViewModel.kt
package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import android.util.Log
import android.util.Xml
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val preferencesManager: UserPreferencesManager = UserPreferencesManager.getInstance(application)

    private val _quote = MutableLiveData<String>()
    val quote: LiveData<String> = _quote
    private var allQuotes: List<String> = emptyList()

    private val _holyPlacesStats = MutableLiveData<List<HolyPlaceStat>>()
    val holyPlacesStats: LiveData<List<HolyPlaceStat>> = _holyPlacesStats

    // --- START: Year Navigation Enhancements ---
    // These internal vars will track the actual integer years for the columns
    private var internalCurrentActualYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var internalLeftColumnYear: Int = internalCurrentActualYear - 1 // Default: year before current
    private var internalRightColumnYear: Int = internalCurrentActualYear   // Default: current year

    // NEW LiveData for the header text labels "<YYYY" or "YYYY>"
    private val _leftYearHeaderUiLabel = MutableLiveData<String>()
    val leftYearHeaderUiLabel: LiveData<String> = _leftYearHeaderUiLabel

    private val _rightYearHeaderUiLabel = MutableLiveData<String>()
    val rightYearHeaderUiLabel: LiveData<String> = _rightYearHeaderUiLabel

    // Your EXISTING LiveData for stats will be REPURPOSED:
    // _templeVisitPreviousYearStats will show data for internalLeftColumnYear
    // _templeVisitCurrentYearStats will show data for internalRightColumnYear
    private val _templeVisitPreviousYearStats = MutableLiveData<TempleVisitYearStats>()
    val templeVisitPreviousYearStats: LiveData<TempleVisitYearStats> = _templeVisitPreviousYearStats

    private val _templeVisitCurrentYearStats = MutableLiveData<TempleVisitYearStats>()
    val templeVisitCurrentYearStats: LiveData<TempleVisitYearStats> = _templeVisitCurrentYearStats

    // Your existing _currentYearLabel and _previousYearLabel are no longer directly used for header text
    // but we can leave them if other parts of your app might use them.
    // For this change, the fragment will observe the new UiLabel LiveData.
    private val _currentYearLabel = MutableLiveData<String>() // Keep if needed elsewhere
    val currentYearLabel: LiveData<String> = _currentYearLabel // Keep if needed elsewhere
    private val _previousYearLabel = MutableLiveData<String>() // Keep if needed elsewhere
    val previousYearLabel: LiveData<String> = _previousYearLabel // Keep if needed elsewhere
    // --- END: Year Navigation Enhancements ---
    private val _templeVisitTotalStats = MutableLiveData<TempleVisitYearStats>()
    val templeVisitTotalStats: LiveData<TempleVisitYearStats> = _templeVisitTotalStats

    private val _mostVisitedPlaces = MutableLiveData<List<MostVisitedPlaceItem>>()
    val mostVisitedPlaces: LiveData<List<MostVisitedPlaceItem>> = _mostVisitedPlaces

    private val _shouldShowRatingPrompt = MutableLiveData<Boolean>()
    val shouldShowRatingPrompt: LiveData<Boolean> = _shouldShowRatingPrompt
    private var ratingPromptShownThisSession = false

    // Define place type constants as they appear in Visit.type and Temple.type
    companion object {
        const val TYPE_TEMPLE = "T" // Example from your TempleContract
        const val TYPE_HISTORICAL_SITE = "H" // Assuming "H" for Historical Sites
        const val TYPE_VISITORS_CENTER = "V" // Assuming "V" for Visitors' Centers
        const val TYPE_ANNOUNCED_TEMPLES = "A" // From your colors.xml (t2_announced_temples)
        const val TYPE_UNDER_CONSTRUCTION = "C" // From your colors.xml (t2_under_construction)
        // Add any other types you use and have colors for
        // Define min year for navigation - adjust as needed based on your data
        private const val MIN_NAVIGATION_YEAR = 1920 // Example: First year of potential data

    }

    init {
        Log.d("SummaryVM", "ViewModel initialized.")
        val systemYear = Calendar.getInstance().get(Calendar.YEAR)
        internalLeftColumnYear = systemYear // Set initial state: Left column is current year
        internalRightColumnYear = systemYear - 1 // Set initial state: Right column is year before current
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
        Log.d("SummaryVM", "loadSummaryData: Recalculating all summary data.")
        // Set default years for the columns on a full refresh
        internalCurrentActualYear = Calendar.getInstance().get(Calendar.YEAR)
        // This call will set internal years, update UI labels, and trigger stat loading for these years
        updateDynamicYearDisplayAndLoadStats(internalCurrentActualYear, internalCurrentActualYear - 1)

        viewModelScope.launch {
            // Check if we should show the rating prompt
            checkAndUpdateRatingPromptVisibility()
            

            // --- START MODIFICATION 1: Fetch and Validate Visits ---
            val allVisitsFromDb: List<Visit>
            try {
                allVisitsFromDb = withContext(Dispatchers.IO) { visitDao.getAllVisitsListForExport() }
            } catch (e: Exception) {
                Log.e("SummaryVM_CrashPrevent", "Critical error fetching visits from DB. Aborting summary load.", e)
                // Optionally, post an error state to UI if you have one
                // _errorState.postValue("Failed to load visit data. Please try again.")
                return@launch // Stop further processing
            }

            val validVisits = allVisitsFromDb.filter { visit ->
                var isValid = true
                if (visit.holyPlaceName == null) {
                    Log.w("SummaryVM_CrashPrevent", "Visit (id: ${visit.id ?: "unknown"}) has NULL holyPlaceName. Excluding from summary aggregations.")
                    isValid = false
                }
                if (visit.type == null) {
                    Log.w("SummaryVM_CrashPrevent", "Visit (id: ${visit.id ?: "unknown"}) has NULL type. Excluding from summary aggregations.")
                    isValid = false
                }
                // For year-based stats, a null date makes the visit unusable FOR THOSE STATS.
                // But it might still be valid for "most visited" list if name exists.
                // The filtering for actualTempleVisits and in calculateYearStatsForTemples already checks dateVisited.
                isValid
            }

            if (validVisits.size < allVisitsFromDb.size) {
                Log.w("SummaryVM_CrashPrevent", "${allVisitsFromDb.size - validVisits.size} visits were filtered out from main summary processing due to missing critical data (name or type).")
            }
            // --- END MODIFICATION 1 ---
            // Fetch total counts for each place type concurrently
            val totalTemplesCountDeferred = async(Dispatchers.IO) { templeDao.getCountByType(TYPE_TEMPLE) }
            val totalHistoricalCountDeferred = async(Dispatchers.IO) { templeDao.getCountByType(TYPE_HISTORICAL_SITE) }
            val totalVCCountDeferred = async(Dispatchers.IO) { templeDao.getCountByType(TYPE_VISITORS_CENTER) }

            // --- Holy Places Section ---
            val visitedPlaceNamesByType = mutableMapOf<String, MutableSet<String>>()

            for (visit in validVisits) {
                visitedPlaceNamesByType.getOrPut(visit.type!!) { mutableSetOf() }.add(visit.holyPlaceName!!)
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

            val actualTempleVisits = validVisits.filter {
                it.type == TYPE_TEMPLE && it.dateVisited != null
            }
            if (actualTempleVisits.size < validVisits.filter{it.type == TYPE_TEMPLE}.size) {
                Log.w("SummaryVM_CrashPrevent", "${validVisits.filter{it.type == TYPE_TEMPLE}.size - actualTempleVisits.size} temple visits were excluded from year stats due to missing dateVisited.")
            }
            _templeVisitTotalStats.postValue(calculateYearStatsForTemples(actualTempleVisits, null))

            // --- Most Visited Section (All place types) ---
            val mostVisitedItems = validVisits
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
    // NEW function to manage dynamic year display and trigger stat loading
    private fun updateDynamicYearDisplayAndLoadStats(leftYearToDisplay: Int, rightYearToDisplay: Int) {
        internalLeftColumnYear = leftYearToDisplay     // Example: 2025 (initially)
        internalRightColumnYear = rightYearToDisplay   // Example: 2024 (initially)
        val systemYear = Calendar.getInstance().get(Calendar.YEAR)

        Log.d("SummaryVM_Direct", "DATA: LeftUI_Year=$internalLeftColumnYear, RightUI_Year=$internalRightColumnYear")

        var finalLeftText: String
        var finalRightText: String

        // Case 1: Initial State (e.g., UI should be "2025" and "2024>")
        if (internalLeftColumnYear == systemYear && internalRightColumnYear == (systemYear - 1)) {
            finalLeftText = "$internalLeftColumnYear"
            // Right gets ">" unless it's the absolute min year and can't be part of an older pair by shifting
            if (internalRightColumnYear == MIN_NAVIGATION_YEAR && internalLeftColumnYear == (MIN_NAVIGATION_YEAR + 1)) {
                finalRightText = "$internalRightColumnYear" // e.g. Left="1921", Right="1920"
            } else {
                finalRightText = "$internalRightColumnYear>" // e.g. Left="2025", Right="2024>"
            }
        }
        // Case 2: Navigated to the oldest possible state (e.g., UI should be "1921" and "1920")
        else if (internalRightColumnYear == MIN_NAVIGATION_YEAR && internalLeftColumnYear == (MIN_NAVIGATION_YEAR + 1)) {
            finalLeftText = "$internalLeftColumnYear"
            finalRightText = "$internalRightColumnYear"
        }
        // Case 3: Any other navigated state (e.g., UI should be "<2024" and "2023>")
        else {
            finalLeftText = "<$internalLeftColumnYear"
            finalRightText = "$internalRightColumnYear>"
        }

        _leftYearHeaderUiLabel.value = finalLeftText
        _rightYearHeaderUiLabel.value = finalRightText
        Log.d("SummaryVM_Direct", "LABELS: LeftText='${finalLeftText}', RightText='${finalRightText}'")

        loadStatsForCurrentlyDisplayedYears()
    }

    // NEW function to load stats for the two columns
    private fun loadStatsForCurrentlyDisplayedYears() {
        viewModelScope.launch {
            val allVisitsFromDbForYearStats: List<Visit>
            try {
                allVisitsFromDbForYearStats = withContext(Dispatchers.IO) { visitDao.getAllVisitsListForExport() }
            } catch (e: Exception) {
                Log.e("SummaryVM_CrashPrevent", "Error fetching visits for year stats. Aborting.", e)
                _templeVisitCurrentYearStats.postValue(calculateYearStatsForTemples(emptyList(), internalLeftColumnYear))
                _templeVisitPreviousYearStats.postValue(calculateYearStatsForTemples(emptyList(), internalRightColumnYear))
                return@launch
            }

            // Filter for valid temple visits with dates
            val validTempleVisitsWithDates = allVisitsFromDbForYearStats.filter { visit ->
                visit.type == TYPE_TEMPLE &&
                        visit.holyPlaceName != null && // Essential for identifying the temple
                        visit.dateVisited != null   // Essential for year calculation
            }

            if (validTempleVisitsWithDates.size < allVisitsFromDbForYearStats.filter { it.type == TYPE_TEMPLE }.size) {
                Log.w("SummaryVM_CrashPrevent", "${allVisitsFromDbForYearStats.filter { it.type == TYPE_TEMPLE }.size - validTempleVisitsWithDates.size} temple visits excluded from year-specific stats due to missing name or date.")
            }

            Log.d("SummaryVM", "Loading stats for LEFT column (Year: $internalLeftColumnYear)")
            _templeVisitCurrentYearStats.postValue( // This LiveData now serves the left dynamic column
                calculateYearStatsForTemples(validTempleVisitsWithDates, internalLeftColumnYear)
            )

            Log.d("SummaryVM", "Loading stats for RIGHT column (Year: $internalRightColumnYear)")
            _templeVisitPreviousYearStats.postValue( // This LiveData now serves the right dynamic column
                calculateYearStatsForTemples(validTempleVisitsWithDates, internalRightColumnYear)
            )
        }
    }

    private suspend fun checkAndUpdateRatingPromptVisibility() {
        // Don't show if already shown this session
        if (ratingPromptShownThisSession) {
            _shouldShowRatingPrompt.postValue(false)
            return
        }
        
        val visitCount = withContext(Dispatchers.IO) { visitDao.getVisitCount() }
        val ratingStatus = preferencesManager.ratingPromptStatusFlow.map { it }.first()
        
        // Show if: has 4+ visits AND hasn't permanently dismissed AND hasn't completed rating
        // "maybe_later" is session-based now, so we ignore it in persistent checks
        val shouldShow = visitCount >= 4 && 
                        ratingStatus != UserPreferencesManager.RATING_STATUS_DONT_ASK_AGAIN &&
                        ratingStatus != UserPreferencesManager.RATING_STATUS_COMPLETED
        
        _shouldShowRatingPrompt.postValue(shouldShow)
        Log.d("SummaryVM", "Rating prompt check: visitCount=$visitCount, status=$ratingStatus, shouldShow=$shouldShow")
    }

    fun onRateNowClicked() {
        viewModelScope.launch {
            preferencesManager.saveRatingPromptStatus(UserPreferencesManager.RATING_STATUS_COMPLETED)
            ratingPromptShownThisSession = true
            _shouldShowRatingPrompt.postValue(false)
        }
    }

    fun onMaybeLaterClicked() {
        // Only dismiss for this session, will show again on next app launch
        ratingPromptShownThisSession = true
        _shouldShowRatingPrompt.value = false
    }

    fun onDontAskAgainClicked() {
        viewModelScope.launch {
            preferencesManager.saveRatingPromptStatus(UserPreferencesManager.RATING_STATUS_DONT_ASK_AGAIN)
            ratingPromptShownThisSession = true
            _shouldShowRatingPrompt.postValue(false)
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

        if (visitsForYear.isEmpty() && targetYear != null) { // If specific year has no visits
            return TempleVisitYearStats(year = targetYear.toString(), attended = 0, uniqueTemples = 0, hoursWorked = 0.0, sealings = 0, endowments = 0, initiatories = 0, confirmations = 0, baptisms = 0)
        }
        if (visitsForYear.isEmpty() && targetYear == null) { // If "Total" has no visits (empty DB)
            return TempleVisitYearStats(year = "Total", attended = 0, uniqueTemples = 0, hoursWorked = 0.0, sealings = 0, endowments = 0, initiatories = 0, confirmations = 0, baptisms = 0)
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
    // NEW Navigation Methods
    fun onNavigateRightClicked() {
        Log.d("SummaryVM_Navigation", "onNavigateLeftClicked CALLED") // Log method call
        if (internalRightColumnYear > MIN_NAVIGATION_YEAR) {
            val newRight = internalRightColumnYear - 1
            val newLeft = internalRightColumnYear
            Log.d("SummaryVM", "Navigating right. New years: L=$newLeft, R=$newRight")
            updateDynamicYearDisplayAndLoadStats(newLeft, newRight)
        } else {
            Log.d("SummaryVM", "Cannot navigate further right. At MIN_NAVIGATION_YEAR: $internalRightColumnYear")
        }
    }
    fun onNavigateLeftClicked() {
        Log.d("SummaryVM_Navigation", "onNavigateRightClicked CALLED") // Log method call
        val maxNavigableYear = Calendar.getInstance().get(Calendar.YEAR)
        if (internalLeftColumnYear < maxNavigableYear) { // Ensure left column doesn't exceed current year
            val newLeft = internalLeftColumnYear + 1
            val newRight = internalLeftColumnYear
            Log.d("SummaryVM", "Navigating left. New years: L=$newLeft, R=$newRight")
            updateDynamicYearDisplayAndLoadStats(newLeft, newRight)
        } else {
            Log.d("SummaryVM", "Cannot navigate further left. At max navigable year: $internalLeftColumnYear")
        }
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
