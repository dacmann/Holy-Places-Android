package net.dacworld.android.holyplacesofthelord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.data.AchievementRepository
import net.dacworld.android.holyplacesofthelord.data.ProfileRepository
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Achievement
import net.dacworld.android.holyplacesofthelord.model.VisitPhoto
import net.dacworld.android.holyplacesofthelord.util.HomeBackgroundStore
import net.dacworld.android.holyplacesofthelord.util.HomeImageOption
import net.dacworld.android.holyplacesofthelord.util.HomeTextColor
import java.io.File
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

data class HomeAppearanceState(
    val option: HomeImageOption = HomeImageOption.DEFAULT,
    val textColor: HomeTextColor = HomeTextColor.WHITE,
    val cropToFill: Boolean = true,
    val randomPhoto: VisitPhoto? = null,
    val alternateFile: File? = null
)

class HomeViewModel(
    private val userPreferencesManager: UserPreferencesManager,
    private val visitDao: VisitDao,
    private val achievementRepository: AchievementRepository,
    private val profileRepository: ProfileRepository? = null,
    private val homeBackgroundStore: HomeBackgroundStore? = null
) : ViewModel() {

    private val _text = MutableStateFlow("This is home Fragment (from HomeViewModel)") // Changed to StateFlow
    val text: StateFlow<String> = _text.asStateFlow()

    val completedAchievements: StateFlow<List<Achievement>> = achievementRepository.completedAchievements

    // --- Goal Progress ---
    private val _goalProgressTitle = MutableStateFlow<String?>(null)
    val goalProgressTitle: StateFlow<String?> = _goalProgressTitle.asStateFlow()

    private val _goalDisplayItems = MutableStateFlow<List<GoalDisplayItem>>(emptyList())
    val goalDisplayItems: StateFlow<List<GoalDisplayItem>> = _goalDisplayItems.asStateFlow()

    private val _randomPhoto = MutableStateFlow<VisitPhoto?>(null)

    val homeAppearance: StateFlow<HomeAppearanceState> = combine(
        userPreferencesManager.homeImageOptionFlow,
        userPreferencesManager.homeTextColorFlow,
        userPreferencesManager.homeImageCropToFillFlow,
        _randomPhoto,
        homeBackgroundStore?.revision ?: flowOf(0)
    ) { option, textColor, cropToFill, photo, _ ->
        HomeAppearanceState(
            option = HomeImageOption.fromStoredValue(option),
            textColor = HomeTextColor.fromStoredValue(textColor),
            cropToFill = cropToFill,
            randomPhoto = photo,
            alternateFile = homeBackgroundStore?.file()?.takeIf { homeBackgroundStore.exists() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeAppearanceState())

    init {
        loadGoalProgress()
        observeRandomHomePhoto()
    }

    private fun observeRandomHomePhoto() {
        viewModelScope.launch {
            combine(
                userPreferencesManager.homeImageOptionFlow,
                profileRepository?.scopedProfileId ?: flowOf(null)
            ) { option, profileId ->
                HomeImageOption.fromStoredValue(option) to profileId
            }.collectLatest { (option, profileId) ->
                if (option == HomeImageOption.RANDOM) {
                    _randomPhoto.value = visitDao.getRandomVisitPhoto(profileId)
                } else {
                    _randomPhoto.value = null
                }
            }
        }
    }

    fun refreshRandomPhoto() {
        viewModelScope.launch {
            val option = HomeImageOption.fromStoredValue(
                userPreferencesManager.homeImageOptionFlow.first()
            )
            if (option != HomeImageOption.RANDOM) return@launch
            val profileId = profileRepository?.scopedProfileId?.first()
            _randomPhoto.value = visitDao.getRandomVisitPhoto(profileId)
        }
    }

    private fun getCurrentYearString(): String {
        return SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
    }

    fun loadGoalProgress() {
        viewModelScope.launch {
            val currentYear = getCurrentYearString()

            // Determine if profiles are enabled and get the active profile/profileId
            val profilesEnabledFlow = profileRepository?.profilesEnabled
                ?: flowOf(false)
            val activeProfileFlow = profileRepository?.activeProfile
                ?: flowOf(null)
            val activeProfileIdFlow = profileRepository?.activeProfileId
                ?: flowOf(null)

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
