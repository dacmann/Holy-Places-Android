package net.dacworld.android.holyplacesofthelord.data // Or your preferred package

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager // Your manager

class SettingsViewModel(private val userPreferencesManager: UserPreferencesManager) : ViewModel() {

    // --- StateFlows for each setting ---
    val templeVisitsGoal: StateFlow<Int> = userPreferencesManager.templeVisitsGoalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val baptismsGoal: StateFlow<Int> = userPreferencesManager.baptismsGoalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val initiatoriesGoal: StateFlow<Int> = userPreferencesManager.initiatoriesGoalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val endowmentsGoal: StateFlow<Int> = userPreferencesManager.endowmentsGoalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val sealingsGoal: StateFlow<Int> = userPreferencesManager.sealingsGoalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_GOAL_VALUE)

    val excludeVisitsNoOrdinances: StateFlow<Boolean> = userPreferencesManager.excludeVisitsNoOrdinancesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_EXCLUDE_VISITS)

    val enableHoursWorked: StateFlow<Boolean> = userPreferencesManager.enableHoursWorkedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesManager.DEFAULT_ENABLE_HOURS)

    // --- Methods to update preferences ---
    fun updateTempleVisitsGoal(value: Int) = viewModelScope.launch {
        userPreferencesManager.saveTempleVisitsGoal(value)
    }

    fun updateBaptismsGoal(value: Int) = viewModelScope.launch {
        userPreferencesManager.saveBaptismsGoal(value)
    }

    fun updateInitiatoriesGoal(value: Int) = viewModelScope.launch {
        userPreferencesManager.saveInitiatoriesGoal(value)
    }

    fun updateEndowmentsGoal(value: Int) = viewModelScope.launch {
        userPreferencesManager.saveEndowmentsGoal(value)
    }

    fun updateSealingsGoal(value: Int) = viewModelScope.launch {
        userPreferencesManager.saveSealingsGoal(value)
    }

    fun updateExcludeVisitsNoOrdinances(isEnabled: Boolean) = viewModelScope.launch {
        userPreferencesManager.saveExcludeVisitsNoOrdinances(isEnabled)
    }

    fun updateEnableHoursWorked(isEnabled: Boolean) = viewModelScope.launch {
        userPreferencesManager.saveEnableHoursWorked(isEnabled)
    }
}
