package net.dacworld.android.holyplacesofthelord.data // Or your preferred package

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.dao.TempleDao // Correct import for your TempleDao
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager // Correct import

class DataViewModelFactory(
    private val templeDao: TempleDao,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DataViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DataViewModel(templeDao, userPreferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}