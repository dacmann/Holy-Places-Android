package net.dacworld.android.holyplacesofthelord // Assuming this is your package

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ProcessLifecycleOwner // For observing app lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.database.AppDatabase // Your AppDatabase
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager // Your DataStore manager
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.util.HolyPlacesXmlParser // Your XML Parser utility
import java.io.InputStream

class MyApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    // Make these accessible to the Factory, 'internal' is fine if factory is in same module.
    internal val templeDao: TempleDao by lazy { database.templeDao() }
    internal val visitDao: VisitDao by lazy { database.visitDao() }
    internal val userPreferencesManager: UserPreferencesManager by lazy {
        UserPreferencesManager.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            val isDataSeeded = userPreferencesManager.isInitialDataSeededFlow.first()
            if (!isDataSeeded) {
                Log.i("MyApplication", "First launch: Seeding database from local XML.")
                val success = seedDatabaseFromLocalXml() // Capture success
                userPreferencesManager.setInitialDataSeeded(true)
                if (success) { // Only set seeded flag and dialog details if seeding was successful
                    userPreferencesManager.setInitialDataSeeded(true)
                    Log.i("MyApplication", "Initial data seeding process complete and main seeded flag set.")
                } else {
                    Log.e("MyApplication", "Initial data seeding FAILED. Dialog details not set. Seeded flag not set.")
                }
            } else {
                Log.i("MyApplication", "Not first launch: Database already seeded or flag set.")
            }
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