package net.dacworld.android.holyplacesofthelord.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.MyApplication
import net.dacworld.android.holyplacesofthelord.util.HomeBackgroundStore

class SettingsViewModelFactory(
    private val applicationContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val app = applicationContext.applicationContext
            val userPreferencesManager = UserPreferencesManager.getInstance(app)
            val myApp = app as? MyApplication
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                userPreferencesManager,
                myApp?.profileRepository,
                myApp?.visitDao,
                HomeBackgroundStore.getInstance(app),
                app
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
