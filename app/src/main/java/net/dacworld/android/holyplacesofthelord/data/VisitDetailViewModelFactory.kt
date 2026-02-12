package net.dacworld.android.holyplacesofthelord.data

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.database.AppDatabase // Ensure this is the correct path

class VisitDetailViewModelFactory(
    private val application: Application,
    private val visitId: Long
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VisitDetailViewModel::class.java)) {
            val database = AppDatabase.getDatabase(application)
            return VisitDetailViewModel(database.visitDao(), database.templeDao(), visitId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}
