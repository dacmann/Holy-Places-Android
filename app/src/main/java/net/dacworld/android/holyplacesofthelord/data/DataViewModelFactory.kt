package net.dacworld.android.holyplacesofthelord.data // Or your preferred package

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao

class DataViewModelFactory(
    private val application: Application,
    private val templeDao: TempleDao,
    private val visitDao: VisitDao,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DataViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DataViewModel(application, templeDao, visitDao, userPreferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}