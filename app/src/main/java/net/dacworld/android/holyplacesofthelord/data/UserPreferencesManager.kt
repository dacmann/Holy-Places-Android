package net.dacworld.android.holyplacesofthelord.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore // Ensure this import is present
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// This top-level property delegate is what makes `context.dataStore` work within this file.
// It needs to be at the top level, or accessible in the scope where it's used.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "holy_places_settings")

class UserPreferencesManager private constructor(private val dataStoreInstance: DataStore<Preferences>) {

    // Encapsulate PreferenceKeys within a private object
    private object PreferencesKeys {
        val XML_VERSION_KEY = stringPreferencesKey("xml_data_version")
        val CHANGES_DATE_KEY = stringPreferencesKey("changes_date")
        val CHANGES_MSG1_KEY = stringPreferencesKey("changes_msg1")
        val CHANGES_MSG2_KEY = stringPreferencesKey("changes_msg2")
        val CHANGES_MSG3_KEY = stringPreferencesKey("changes_msg3")
        val IS_INITIAL_DATA_SEEDED = booleanPreferencesKey("is_initial_data_seeded")
    }

    // Flow for XML version
    val xmlVersionFlow: Flow<String?> = dataStoreInstance.data // Use the constructor param
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.XML_VERSION_KEY]
        }

    suspend fun saveXmlVersion(version: String) {
        dataStoreInstance.edit { preferences -> // Use the constructor param
            preferences[PreferencesKeys.XML_VERSION_KEY] = version
        }
    }

    // --- Flows for reading change messages ---
    val changesDateFlow: Flow<String?> = dataStoreInstance.data.mapToPreference(PreferencesKeys.CHANGES_DATE_KEY)
    val changesMsg1Flow: Flow<String?> = dataStoreInstance.data.mapToPreference(PreferencesKeys.CHANGES_MSG1_KEY)
    val changesMsg2Flow: Flow<String?> = dataStoreInstance.data.mapToPreference(PreferencesKeys.CHANGES_MSG2_KEY)
    val changesMsg3Flow: Flow<String?> = dataStoreInstance.data.mapToPreference(PreferencesKeys.CHANGES_MSG3_KEY)

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

    suspend fun saveChangeMessages(
        date: String?,
        msg1: String?,
        msg2: String?,
        msg3: String?
    ) {
        dataStoreInstance.edit { preferences -> // Use the constructor param
            date?.let { preferences[PreferencesKeys.CHANGES_DATE_KEY] = it } ?: preferences.remove(PreferencesKeys.CHANGES_DATE_KEY)
            msg1?.let { preferences[PreferencesKeys.CHANGES_MSG1_KEY] = it } ?: preferences.remove(PreferencesKeys.CHANGES_MSG1_KEY)
            msg2?.let { preferences[PreferencesKeys.CHANGES_MSG2_KEY] = it } ?: preferences.remove(PreferencesKeys.CHANGES_MSG2_KEY)
            msg3?.let { preferences[PreferencesKeys.CHANGES_MSG3_KEY] = it } ?: preferences.remove(PreferencesKeys.CHANGES_MSG3_KEY)
        }
    }

    val isInitialDataSeededFlow: Flow<Boolean> = dataStoreInstance.data // Use the constructor param
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.IS_INITIAL_DATA_SEEDED] ?: false
        }

    suspend fun setInitialDataSeeded(seeded: Boolean) {
        dataStoreInstance.edit { preferences -> // Use the constructor param
            preferences[PreferencesKeys.IS_INITIAL_DATA_SEEDED] = seeded
        }
    }

    suspend fun clearXmlVersion() {
        dataStoreInstance.edit { preferences -> // Use the constructor param
            preferences.remove(PreferencesKeys.XML_VERSION_KEY)
        }
    }

    suspend fun clearAllChangeMessages() {
        dataStoreInstance.edit { preferences -> // Use the constructor param
            preferences.remove(PreferencesKeys.CHANGES_DATE_KEY)
            preferences.remove(PreferencesKeys.CHANGES_MSG1_KEY)
            preferences.remove(PreferencesKeys.CHANGES_MSG2_KEY)
            preferences.remove(PreferencesKeys.CHANGES_MSG3_KEY)
        }
    }

    suspend fun clearAllPreferences() {
        dataStoreInstance.edit { preferences -> // Use the constructor param
            preferences.clear()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        // This getInstance now takes a DataStore<Preferences> directly
        fun getInstance(dataStore: DataStore<Preferences>): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                // Check again in case INSTANCE was initialized while this thread was waiting
                INSTANCE ?: UserPreferencesManager(dataStore).also {
                    INSTANCE = it
                }
            }
        }

        // Convenience overload to get instance directly with Context
        // This version explicitly uses the top-level 'Context.dataStore' extension property
        fun getInstance(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                // Check again inside synchronized block
                // It's important to use context.applicationContext to avoid leaks
                // and to ensure the DataStore is a singleton for the app.
                // The `context.dataStore` here refers to the top-level extension property.
                INSTANCE ?: UserPreferencesManager(context.applicationContext.dataStore).also {
                    INSTANCE = it
                }
            }
        }
    }
}