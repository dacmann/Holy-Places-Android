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
import net.dacworld.android.holyplacesofthelord.data.ProfileRepository
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Achievement
import net.dacworld.android.holyplacesofthelord.model.Profile
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
    private val achievementRepository: AchievementRepository,
    private val profileRepository: ProfileRepository? = null
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

    fun loadGoalProgress() {
        viewModelScope.launch {
            val currentYear = getCurrentYearString()

            // Determine if profiles are enabled and get the active profile/profileId
            val profilesEnabledFlow = profileRepository?.profilesEnabled
                ?: kotlinx.coroutines.flow.flowOf(false)
            val activeProfileFlow = profileRepository?.activeProfile
                ?: kotlinx.coroutines.flow.flowOf(null)
            val activeProfileIdFlow = profileRepository?.activeProfileId
                ?: kotlinx.coroutines.flow.flowOf(null)

            // Goal targets always come from the active profile when one exists
            val visitsTargetFlow = combine(activeProfileFlow, userPreferencesManager.templeVisitsGoalFlow) { profile, legacy ->
                profile?.annualVisitGoal ?: legacy
            }
            val baptConfTargetFlow = combine(activeProfileFlow, userPreferencesManager.baptismsGoalFlow) { profile, legacy ->
                profile?.annualBaptismGoal ?: legacy
            }
            val initiatoriesTargetFlow = combine(activeProfileFlow, userPreferencesManager.initiatoriesGoalFlow) { profile, legacy ->
                profile?.annualInitiatoryGoal ?: legacy
            }
            val endowmentsTargetFlow = combine(activeProfileFlow, userPreferencesManager.endowmentsGoalFlow) { profile, legacy ->
                profile?.annualEndowmentGoal ?: legacy
            }
            val sealingsTargetFlow = combine(activeProfileFlow, userPreferencesManager.sealingsGoalFlow) { profile, legacy ->
                profile?.annualSealingGoal ?: legacy
            }

            val excludeNoOrdinancesFlow = combine(activeProfileFlow, userPreferencesManager.excludeVisitsNoOrdinancesFlow) { profile, legacy ->
                profile?.excludeNonOrdinanceVisits ?: legacy
            }

            // Always scope visit counts to the active profile
            val scopedProfileIdFlow = activeProfileIdFlow

            val currentVisitsFlow = combine(excludeNoOrdinancesFlow, scopedProfileIdFlow) { exclude, profileId ->
                Pair(exclude, profileId)
            }.flatMapLatest { (exclude, profileId) ->
                visitDao.getTempleVisitsCountForYear(currentYear, exclude, profileId)
            }
            val currentBaptismsFlow = scopedProfileIdFlow.flatMapLatest { pid ->
                visitDao.getTotalBaptismsForYear(currentYear, pid).map { it ?: 0 }
            }
            val currentConfirmationsFlow = scopedProfileIdFlow.flatMapLatest { pid ->
                visitDao.getTotalConfirmationsForYear(currentYear, pid).map { it ?: 0 }
            }
            val currentInitiatoriesFlow = scopedProfileIdFlow.flatMapLatest { pid ->
                visitDao.getTotalInitiatoriesForYear(currentYear, pid).map { it ?: 0 }
            }
            val currentEndowmentsFlow = scopedProfileIdFlow.flatMapLatest { pid ->
                visitDao.getTotalEndowmentsForYear(currentYear, pid).map { it ?: 0 }
            }
            val currentSealingsFlow = scopedProfileIdFlow.flatMapLatest { pid ->
                visitDao.getTotalSealingsForYear(currentYear, pid).map { it ?: 0 }
            }

            val currentBaptConfFlow = currentBaptismsFlow.combine(currentConfirmationsFlow) { b, c -> b + c }

            // Reactive goal title: "<Name>'s Year Goals" when profiles on, else "Year Goal Progress"
            launch {
                combine(profilesEnabledFlow, activeProfileFlow) { enabled, profile ->
                    if (enabled && profile != null) "${profile.name}'s $currentYear Goals"
                    else "$currentYear Goal Progress"
                }.collectLatest { title -> _goalProgressTitle.value = title }
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