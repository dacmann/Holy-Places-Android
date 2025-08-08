package net.dacworld.android.holyplacesofthelord.data // Or your UI package where it resides

import android.content.Context // Import if not already there
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager // Your manager

class SettingsViewModelFactory(
    private val applicationContext: Context // Pass context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Use the getInstance method with the applicationContext
            val userPreferencesManager = UserPreferencesManager.getInstance(applicationContext)
            return SettingsViewModel(userPreferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
