package net.dacworld.android.holyplacesofthelord.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "holy_places_settings")

class UserPreferencesManager(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        val XML_VERSION_KEY = stringPreferencesKey("xml_data_version")
        val CHANGES_DATE_KEY = stringPreferencesKey("changes_date")
        val CHANGES_MSG1_KEY = stringPreferencesKey("changes_msg1")
        val CHANGES_MSG2_KEY = stringPreferencesKey("changes_msg2")
        val CHANGES_MSG3_KEY = stringPreferencesKey("changes_msg3")
    }

    val xmlVersionFlow: Flow<String?> = appContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[XML_VERSION_KEY]
        }

    suspend fun saveXmlVersion(version: String) {
        appContext.dataStore.edit { preferences ->
            preferences[XML_VERSION_KEY] = version
        }
    }

    // --- Flows for reading change messages ---
    val changesDateFlow: Flow<String?> = appContext.dataStore.data.mapToPreference(CHANGES_DATE_KEY)
    val changesMsg1Flow: Flow<String?> = appContext.dataStore.data.mapToPreference(CHANGES_MSG1_KEY)
    val changesMsg2Flow: Flow<String?> = appContext.dataStore.data.mapToPreference(CHANGES_MSG2_KEY)
    val changesMsg3Flow: Flow<String?> = appContext.dataStore.data.mapToPreference(CHANGES_MSG3_KEY)

    // Helper extension function to reduce boilerplate for mapping preferences
    private fun Flow<Preferences>.mapToPreference(key: Preferences.Key<String>): Flow<String?> {
        return this.catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            preferences[key]
        }
    }

    // --- Suspend functions for saving change messages ---
    suspend fun saveChangeMessages(
        date: String?,
        msg1: String?,
        msg2: String?,
        msg3: String?
    ) {
        appContext.dataStore.edit { preferences ->
            date?.let { preferences[CHANGES_DATE_KEY] = it } ?: preferences.remove(CHANGES_DATE_KEY)
            msg1?.let { preferences[CHANGES_MSG1_KEY] = it } ?: preferences.remove(CHANGES_MSG1_KEY)
            msg2?.let { preferences[CHANGES_MSG2_KEY] = it } ?: preferences.remove(CHANGES_MSG2_KEY)
            msg3?.let { preferences[CHANGES_MSG3_KEY] = it } ?: preferences.remove(CHANGES_MSG3_KEY)
        }
    }

    // Optional: Clear functions
    suspend fun clearXmlVersion() {
        appContext.dataStore.edit { preferences ->
            preferences.remove(XML_VERSION_KEY)
        }
    }

    suspend fun clearAllChangeMessages() {
        appContext.dataStore.edit { preferences ->
            preferences.remove(CHANGES_DATE_KEY)
            preferences.remove(CHANGES_MSG1_KEY)
            preferences.remove(CHANGES_MSG2_KEY)
            preferences.remove(CHANGES_MSG3_KEY)
        }
    }

    suspend fun clearAllPreferences() {
        appContext.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}