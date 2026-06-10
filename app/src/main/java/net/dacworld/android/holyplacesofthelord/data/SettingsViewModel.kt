package net.dacworld.android.holyplacesofthelord.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.model.Profile

class SettingsViewModel(
    private val userPreferencesManager: UserPreferencesManager,
    private val profileRepository: ProfileRepository? = null
) : ViewModel() {

    private val activeProfile: StateFlow<Profile?> = profileRepository?.activeProfile
        ?.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        ?: kotlinx.coroutines.flow.MutableStateFlow(null)

    // Goals always come from the active profile when one exists (even if profile UI is disabled).
    val templeVisitsGoal: StateFlow<Int> =
        combine(activeProfile, userPreferencesManager.templeVisitsGoalFlow) { profile, legacy ->
            profile?.annualVisitGoal ?: legacy
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val baptismsGoal: StateFlow<Int> =
        combine(activeProfile, userPreferencesManager.baptismsGoalFlow) { profile, legacy ->
            profile?.annualBaptismGoal ?: legacy
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val initiatoriesGoal: StateFlow<Int> =
        combine(activeProfile, userPreferencesManager.initiatoriesGoalFlow) { profile, legacy ->
            profile?.annualInitiatoryGoal ?: legacy
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val endowmentsGoal: StateFlow<Int> =
        combine(activeProfile, userPreferencesManager.endowmentsGoalFlow) { profile, legacy ->
            profile?.annualEndowmentGoal ?: legacy
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val sealingsGoal: StateFlow<Int> =
        combine(activeProfile, userPreferencesManager.sealingsGoalFlow) { profile, legacy ->
            profile?.annualSealingGoal ?: legacy
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val excludeVisitsNoOrdinances: StateFlow<Boolean> =
        combine(activeProfile, userPreferencesManager.excludeVisitsNoOrdinancesFlow) { profile, legacy ->
            profile?.excludeNonOrdinanceVisits ?: legacy
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_EXCLUDE_VISITS)

    val enableHoursWorked: StateFlow<Boolean> = userPreferencesManager.enableHoursWorkedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_ENABLE_HOURS)

    val defaultCommentsText: StateFlow<String> = userPreferencesManager.defaultCommentsTextFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_COMMENTS_TEXT)

    // --- Save methods — route to active Profile entity when one exists ---

    fun updateTempleVisitsGoal(value: Int) = viewModelScope.launch {
        val profile = activeProfileForGoals()
        if (profile != null) profileRepository?.updateProfile(profile.copy(annualVisitGoal = value))
        else userPreferencesManager.saveTempleVisitsGoal(value)
    }

    fun updateBaptismsGoal(value: Int) = viewModelScope.launch {
        val profile = activeProfileForGoals()
        if (profile != null) profileRepository?.updateProfile(profile.copy(annualBaptismGoal = value))
        else userPreferencesManager.saveBaptismsGoal(value)
    }

    fun updateInitiatoriesGoal(value: Int) = viewModelScope.launch {
        val profile = activeProfileForGoals()
        if (profile != null) profileRepository?.updateProfile(profile.copy(annualInitiatoryGoal = value))
        else userPreferencesManager.saveInitiatoriesGoal(value)
    }

    fun updateEndowmentsGoal(value: Int) = viewModelScope.launch {
        val profile = activeProfileForGoals()
        if (profile != null) profileRepository?.updateProfile(profile.copy(annualEndowmentGoal = value))
        else userPreferencesManager.saveEndowmentsGoal(value)
    }

    fun updateSealingsGoal(value: Int) = viewModelScope.launch {
        val profile = activeProfileForGoals()
        if (profile != null) profileRepository?.updateProfile(profile.copy(annualSealingGoal = value))
        else userPreferencesManager.saveSealingsGoal(value)
    }

    fun updateExcludeVisitsNoOrdinances(isEnabled: Boolean) = viewModelScope.launch {
        val profile = activeProfileForGoals()
        if (profile != null) profileRepository?.updateProfile(profile.copy(excludeNonOrdinanceVisits = isEnabled))
        else userPreferencesManager.saveExcludeVisitsNoOrdinances(isEnabled)
    }

    fun updateEnableHoursWorked(isEnabled: Boolean) = viewModelScope.launch {
        userPreferencesManager.saveEnableHoursWorked(isEnabled)
    }

    fun updateDefaultCommentsText(text: String) = viewModelScope.launch {
        userPreferencesManager.saveDefaultCommentsText(text)
    }

    private fun activeProfileForGoals(): Profile? = activeProfile.value
}
