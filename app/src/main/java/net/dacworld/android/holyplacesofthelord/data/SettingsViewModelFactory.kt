package net.dacworld.android.holyplacesofthelord.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.MyApplication

class SettingsViewModelFactory(
    private val applicationContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val userPreferencesManager = UserPreferencesManager.getInstance(applicationContext)
            val profileRepository = (applicationContext as? MyApplication)?.profileRepository
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(userPreferencesManager, profileRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
