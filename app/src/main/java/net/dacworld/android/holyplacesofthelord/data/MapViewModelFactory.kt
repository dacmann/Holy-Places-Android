package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.database.AppDatabase

class MapViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            val database = AppDatabase.getDatabase(application) // Get your database instance
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(database.templeDao(), database.visitDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class for MapViewModelFactory (DAO version)")
    }
}
