package net.dacworld.android.holyplacesofthelord // Assuming this is your package

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner // For observing app lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.database.AppDatabase // Your AppDatabase
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager // Your DataStore manager
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.util.HolyPlacesXmlParser // Your XML Parser utility
import java.io.InputStream

class MyApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    // Make these accessible to the Factory, 'internal' is fine if factory is in same module.
    internal val templeDao: TempleDao by lazy { database.templeDao() }
    internal val userPreferencesManager: UserPreferencesManager by lazy {
        UserPreferencesManager.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            val isDataSeeded = userPreferencesManager.isInitialDataSeededFlow.first()
            if (!isDataSeeded) {
                Log.i("MyApplication", "First launch: Seeding database from local XML.")
                seedDatabaseFromLocalXml()
                userPreferencesManager.setInitialDataSeeded(true)
                Log.i("MyApplication", "Initial data seeding complete and flag set.")
            } else {
                Log.i("MyApplication", "Not first launch: Database already seeded or flag set.")
            }
        }
    }

    private suspend fun seedDatabaseFromLocalXml() = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = assets.open("initial_holy_places.xml")
            val holyPlacesData = HolyPlacesXmlParser.parse(inputStream)

            if (holyPlacesData.temples.isNotEmpty()) {
                templeDao.clearAndInsertAll(holyPlacesData.temples) // Ensure this method exists
                Log.i(
                    "MyApplication",
                    "Successfully seeded ${holyPlacesData.temples.size} temples from local XML."
                )
                holyPlacesData.version?.let {
                    userPreferencesManager.saveXmlVersion(it)
                    Log.i("MyApplication", "Saved version '$it' from local XML to preferences.")
                }
                userPreferencesManager.saveChangeMessages(
                    holyPlacesData.changesDate,
                    holyPlacesData.changesMsg1,
                    holyPlacesData.changesMsg2,
                    holyPlacesData.changesMsg3
                )
                Log.i("MyApplication", "Saved change messages from local XML to preferences.")
            } else {
                Log.w(
                    "MyApplication",
                    "No temples found in initial_holy_places.xml or parsing yielded no temples."
                )
            }
        } catch (e: Exception) {
            Log.e("MyApplication", "Error seeding database from local XML", e)
        }
    }
}