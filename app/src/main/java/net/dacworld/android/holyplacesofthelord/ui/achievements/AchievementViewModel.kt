package net.dacworld.android.holyplacesofthelord.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.dacworld.android.holyplacesofthelord.data.AchievementRepository
import net.dacworld.android.holyplacesofthelord.model.Achievement

class AchievementViewModel(
    private val achievementRepository: AchievementRepository
) : ViewModel() {

    val achievements: StateFlow<List<Achievement>> = achievementRepository.achievements
    val completedAchievements: StateFlow<List<Achievement>> = achievementRepository.completedAchievements
    val incompleteAchievements: StateFlow<List<Achievement>> = achievementRepository.incompleteAchievements

    private val _selectedTab = MutableStateFlow(AchievementTab.COMPLETED)
    val selectedTab: StateFlow<AchievementTab> = _selectedTab.asStateFlow()

    fun setTab(tab: AchievementTab) {
        _selectedTab.value = tab
    }

    enum class AchievementTab {
        COMPLETED,
        NOT_COMPLETED
    }
}

class AchievementViewModelFactory(
    private val achievementRepository: AchievementRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AchievementViewModel::class.java)) {
            return AchievementViewModel(achievementRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
