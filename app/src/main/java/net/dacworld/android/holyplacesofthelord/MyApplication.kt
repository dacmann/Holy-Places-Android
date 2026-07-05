package net.dacworld.android.holyplacesofthelord // Assuming this is your package

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ProcessLifecycleOwner // For observing app lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.dao.NameChangeDao
import net.dacworld.android.holyplacesofthelord.dao.ProfileDao
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.data.AchievementRepository
import net.dacworld.android.holyplacesofthelord.data.ProfileRepository
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.util.HistoricalNamesHelper
import net.dacworld.android.holyplacesofthelord.util.HolyPlacesXmlParser
import java.io.InputStream

class MyApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    internal val templeDao: TempleDao by lazy { database.templeDao() }
    internal val visitDao: VisitDao by lazy { database.visitDao() }
    internal val profileDao: ProfileDao by lazy { database.profileDao() }
    internal val nameChangeDao: NameChangeDao by lazy { database.nameChangeDao() }
    internal val userPreferencesManager: UserPreferencesManager by lazy {
        UserPreferencesManager.getInstance(this)
    }

    internal val profileRepository: ProfileRepository by lazy {
        ProfileRepository(profileDao, visitDao, userPreferencesManager)
    }

    internal val achievementRepository: AchievementRepository by lazy {
        AchievementRepository(
            applicationContext,
            visitDao,
            userPreferencesManager,
            profileRepository,
            ProcessLifecycleOwner.get().lifecycleScope
        )
    }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(
            AppGlobalExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        )
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // Open DB (runs migration) and repair profile state before any UI queries the database.
        runBlocking(Dispatchers.IO) {
            database.openHelper.writableDatabase
            profileRepository.repairProfileState()
        }

        ProcessLifecycleOwner.get().lifecycleScope.launch {
            val isDataSeeded = userPreferencesManager.isInitialDataSeededFlow.first()
            if (!isDataSeeded) {
                Log.i("MyApplication", "First launch: Seeding database from local XML.")
                val success = seedDatabaseFromLocalXml()
                if (success) {
                    userPreferencesManager.setInitialDataSeeded(true)
                    Log.i("MyApplication", "Initial data seeding process complete and main seeded flag set.")
                } else {
                    Log.e("MyApplication", "Initial data seeding FAILED. Dialog details not set. Seeded flag not set.")
                }
            } else {
                Log.i("MyApplication", "Not first launch: Database already seeded or flag set.")
                // One-time backfill for the Historical Names feature (schema v4):
                // existing installs never re-parse the XML unless the server version
                // changes, so populate name changes / dedication dates from the
                // bundled XML and repair visit names once after upgrading.
                val backfillDone = userPreferencesManager.historicalDataBackfillDoneFlow.first()
                if (!backfillDone) {
                    runHistoricalDataBackfill()
                }
            }
            
            // Initialize default comments text from string resource if not already set
            userPreferencesManager.initializeDefaultCommentsTextIfNeeded(this@MyApplication)
        }
    }

    class AppGlobalExceptionHandler(
        private val applicationContext: Context,
        private val defaultUEH: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, exception: Throwable) {
            Log.e("AppCrash_Global", "Unhandled exception caught: ${exception.message}", exception)

            // Here you could:
            // 1. Log to a file on the device
            // 2. Log to a remote crash reporting service (Firebase Crashlytics, Sentry ([2]), etc.)
            // 3. Attempt a graceful shutdown or notify the user (tricky with a crash)

            // IMPORTANT: Let the default handler do its job too (like showing "App has stopped")
            defaultUEH?.uncaughtException(thread, exception)
        }
    }


    /**
     * One-time upgrade backfill (schema v4 / Historical Names): re-parses the
     * bundled XML to populate temple_name_changes and temples.dedicated_date for
     * existing installs, then repairs visit names that were bulk-renamed by the
     * old (date-unaware) sync logic. Historical images download on the next sync.
     */
    private suspend fun runHistoricalDataBackfill() = withContext(Dispatchers.IO) {
        try {
            Log.i("MyApplication", "Running one-time historical data backfill.")
            val holyPlacesData = assets.open("initial_holy_places.xml").use { input ->
                HolyPlacesXmlParser.parse(input)
            }
            if (holyPlacesData.temples.isEmpty()) {
                Log.w("MyApplication", "Backfill aborted: bundled XML yielded no temples.")
                return@withContext
            }
            val existingIds = templeDao.getAllTempleIds().toSet()
            var dedicatedCount = 0
            for (temple in holyPlacesData.temples) {
                if (temple.id !in existingIds) continue
                nameChangeDao.replaceForTemple(temple.id, temple.nameChanges)
                if (temple.dedicatedDate != null) {
                    templeDao.updateDedicatedDate(temple.id, temple.dedicatedDate)
                    dedicatedCount++
                }
            }
            val repaired = HistoricalNamesHelper.reconcileAllVisitNames(templeDao, visitDao, nameChangeDao)
            userPreferencesManager.setHistoricalDataBackfillDone(true)
            Log.i(
                "MyApplication",
                "Historical backfill complete: $dedicatedCount dedication dates, $repaired visit name(s) repaired."
            )
        } catch (e: Exception) {
            // Flag intentionally not set so the backfill retries on next launch.
            Log.e("MyApplication", "Historical data backfill failed.", e)
        }
    }

    // Modify to return Boolean indicating success
    private suspend fun seedDatabaseFromLocalXml(): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = assets.open("initial_holy_places.xml")
            val holyPlacesData = HolyPlacesXmlParser.parse(inputStream) // This should be your data class instance

            if (holyPlacesData.temples.isNotEmpty()) {
                templeDao.clearAndInsertAll(holyPlacesData.temples)
                Log.i(
                    "MyApplication",
                    "Successfully seeded ${holyPlacesData.temples.size} temples from local XML."
                )

                // Persist historical names (fresh install: nothing to reconcile/backfill)
                HistoricalNamesHelper.persistNameChanges(nameChangeDao, holyPlacesData.temples)
                userPreferencesManager.setHistoricalDataBackfillDone(true)
                Log.i("MyApplication", "Persisted name changes for seeded temples.")

                holyPlacesData.version?.let {
                    userPreferencesManager.saveXmlVersion(it)
                    Log.i("MyApplication", "Saved version '$it' from local XML to preferences.")
                }

                // Save the general change messages (for persistent display if needed)
                userPreferencesManager.saveChangeMessages(
                    holyPlacesData.changesDate,
                    holyPlacesData.changesMsg1,
                    holyPlacesData.changesMsg2,
                    holyPlacesData.changesMsg3
                )
                Log.i("MyApplication", "Saved general change messages from local XML to preferences.")

                // ---- ADDED: Prepare details for the initial seed dialog ----
                val dialogTitle = "${holyPlacesData.changesDate ?: "Initial Data"} Message"
                val dialogMessages = mutableListOf<String>()
                holyPlacesData.changesMsg1?.takeIf { it.isNotBlank() }?.let { dialogMessages.add(it) }
                holyPlacesData.changesMsg2?.takeIf { it.isNotBlank() }?.let { dialogMessages.add(it) }
                holyPlacesData.changesMsg3?.takeIf { it.isNotBlank() }?.let { dialogMessages.add(it) }

                if (dialogMessages.isEmpty()) {
                    dialogMessages.add("The initial set of Holy Places data has been successfully loaded into the app.")
                    dialogMessages.add("${holyPlacesData.temples.size} places are now available.")
                } else {
                    // Prepend a general success message if specific change messages exist
                    //dialogMessages.add(0, "The initial set of Holy Places data has been successfully loaded:")
                    //dialogMessages.add("Version: ${holyPlacesData.version ?: "N/A"}")
                    dialogMessages.add("${holyPlacesData.temples.size} places are now available.")
                }

                userPreferencesManager.setInitialSeedDetails(dialogTitle, dialogMessages)
                Log.i("MyApplication", "Initial seed dialog details set in UserPreferencesManager.")
                // ---- END ADDED ----

                return@withContext true // Indicate success

            } else {
                Log.w(
                    "MyApplication",
                    "No temples found in initial_holy_places.xml or parsing yielded no temples. Seeding considered failed for dialog."
                )
                return@withContext false // Indicate failure
            }
        } catch (e: Exception) {
            Log.e("MyApplication", "Error seeding database from local XML", e)
            return@withContext false // Indicate failure
        }
    }
}