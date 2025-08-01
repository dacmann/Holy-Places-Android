package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.dao.VisitDao // Assuming your VisitDao is here or accessible
import net.dacworld.android.holyplacesofthelord.database.AppDatabase // Ensure this is the correct path

class VisitDetailViewModelFactory(
    private val application: Application,
    private val visitId: Long
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VisitDetailViewModel::class.java)) {
            // Get DAO instance from your AppDatabase
            val visitDao = AppDatabase.getDatabase(application).visitDao()
            return VisitDetailViewModel(visitDao, visitId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}
