package net.dacworld.android.holyplacesofthelord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.data.AchievementRepository
import net.dacworld.android.holyplacesofthelord.data.ProfileRepository
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager

class HomeViewModelFactory(
    private val userPreferencesManager: UserPreferencesManager,
    private val visitDao: VisitDao,
    private val achievementRepository: AchievementRepository,
    private val profileRepository: ProfileRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(userPreferencesManager, visitDao, achievementRepository, profileRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}