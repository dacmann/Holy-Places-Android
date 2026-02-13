package net.dacworld.android.holyplacesofthelord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.data.AchievementRepository
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Achievement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data class to hold combined info for display (can be outside or a nested class)
data class GoalDisplayItem(
    val id: String, // e.g., "visits", "baptConf", "initiatories", "endowments", "sealings"
    val target: Int,
    val current: Int,
    val displayText: String,
    val hasActiveGoal: Boolean // True if target > 0
)

class HomeViewModel(
    private val userPreferencesManager: UserPreferencesManager,
    private val visitDao: VisitDao,
    private val achievementRepository: AchievementRepository
) : ViewModel() {

    private val _text = MutableStateFlow("This is home Fragment (from HomeViewModel)") // Changed to StateFlow
    val text: StateFlow<String> = _text.asStateFlow()

    val completedAchievements: StateFlow<List<Achievement>> = achievementRepository.completedAchievements

    // --- Goal Progress ---
    private val _goalProgressTitle = MutableStateFlow<String?>(null)
    val goalProgressTitle: StateFlow<String?> = _goalProgressTitle.asStateFlow()

    private val _goalDisplayItems = MutableStateFlow<List<GoalDisplayItem>>(emptyList())
    val goalDisplayItems: StateFlow<List<GoalDisplayItem>> = _goalDisplayItems.asStateFlow()

    init {
        loadGoalProgress()
    }

    private fun getCurrentYearString(): String {
        return SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
    }

    fun loadGoalProgress() { // Keep it public if you might want to refresh manually
        viewModelScope.launch {
            val currentYear = getCurrentYearString()
            _goalProgressTitle.value = "$currentYear Goal Progress"

            val visitsTargetFlow = userPreferencesManager.templeVisitsGoalFlow
            val baptConfTargetFlow = userPreferencesManager.baptismsGoalFlow
            val initiatoriesTargetFlow = userPreferencesManager.initiatoriesGoalFlow
            val endowmentsTargetFlow = userPreferencesManager.endowmentsGoalFlow
            val sealingsTargetFlow = userPreferencesManager.sealingsGoalFlow
            val excludeNoOrdinancesFlow = userPreferencesManager.excludeVisitsNoOrdinancesFlow

            val currentVisitsFlow = excludeNoOrdinancesFlow.flatMapLatest { exclude ->
                visitDao.getTempleVisitsCountForYear(currentYear, exclude)
            }
            val currentBaptismsFlow = visitDao.getTotalBaptismsForYear(currentYear).map { it ?: 0 }
            val currentConfirmationsFlow = visitDao.getTotalConfirmationsForYear(currentYear).map { it ?: 0 }
            val currentInitiatoriesFlow = visitDao.getTotalInitiatoriesForYear(currentYear).map { it ?: 0 }
            val currentEndowmentsFlow = visitDao.getTotalEndowmentsForYear(currentYear).map { it ?: 0 }
            val currentSealingsFlow = visitDao.getTotalSealingsForYear(currentYear).map { it ?: 0 }

            val currentBaptConfFlow = currentBaptismsFlow.combine(currentConfirmationsFlow) { baptisms, confirmations ->
                baptisms + confirmations
            }

            combine(
                visitsTargetFlow, currentVisitsFlow,
                baptConfTargetFlow, currentBaptConfFlow,
                initiatoriesTargetFlow, currentInitiatoriesFlow,
                endowmentsTargetFlow, currentEndowmentsFlow,
                sealingsTargetFlow, currentSealingsFlow
            ) { values ->
                val items = mutableListOf<GoalDisplayItem>()
                val visitsTarget = values[0] as Int
                val currentVisits = values[1] as Int
                val baptConfTarget = values[2] as Int
                val currentBaptConf = values[3] as Int
                val initiatoriesTarget = values[4] as Int
                val currentInitiatories = values[5] as Int
                val endowmentsTarget = values[6] as Int
                val currentEndowments = values[7] as Int
                val sealingsTarget = values[8] as Int
                val currentSealings = values[9] as Int

                items.add(GoalDisplayItem("visits", visitsTarget, currentVisits, "$currentVisits of $visitsTarget Visits", visitsTarget > 0))
                items.add(GoalDisplayItem("baptConf", baptConfTarget, currentBaptConf, "$currentBaptConf of $baptConfTarget Bapt/Conf", baptConfTarget > 0))
                items.add(GoalDisplayItem("initiatories", initiatoriesTarget, currentInitiatories, "$currentInitiatories of $initiatoriesTarget Initiatories", initiatoriesTarget > 0))
                items.add(GoalDisplayItem("endowments", endowmentsTarget, currentEndowments, "$currentEndowments of $endowmentsTarget Endowments", endowmentsTarget > 0))
                items.add(GoalDisplayItem("sealings", sealingsTarget, currentSealings, "$currentSealings of $sealingsTarget Sealings", sealingsTarget > 0))

                items.toList()
            }.collectLatest { combinedItems ->
                _goalDisplayItems.value = combinedItems
            }
        }
    }
}