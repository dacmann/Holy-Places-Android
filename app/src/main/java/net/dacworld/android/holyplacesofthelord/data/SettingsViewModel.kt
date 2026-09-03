package net.dacworld.android.holyplacesofthelord.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Profile
import net.dacworld.android.holyplacesofthelord.util.AppTheme
import net.dacworld.android.holyplacesofthelord.util.ColorTheme
import net.dacworld.android.holyplacesofthelord.util.HomeBackgroundStore
import net.dacworld.android.holyplacesofthelord.util.HomeImageOption
import net.dacworld.android.holyplacesofthelord.util.HomeTextColor

class SettingsViewModel(
    private val userPreferencesManager: UserPreferencesManager,
    private val profileRepository: ProfileRepository? = null,
    private val visitDao: VisitDao? = null,
    private val homeBackgroundStore: HomeBackgroundStore? = null,
    private val applicationContext: Context? = null
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

    // --- Appearance (device level, never routed through a Profile) ---

    val colorTheme: StateFlow<ColorTheme> = userPreferencesManager.colorThemeFlow
        .map { ColorTheme.fromStoredValue(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorTheme.DEFAULT)

    val showPlaceTypeSymbols: StateFlow<Boolean> = userPreferencesManager.showPlaceTypeSymbolsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserPreferencesManager.DEFAULT_SHOW_PLACE_TYPE_SYMBOLS
        )

    val showStockPlaceImageOnVisits: StateFlow<Boolean> =
        userPreferencesManager.showStockPlaceImageOnVisitsFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferencesManager.DEFAULT_SHOW_STOCK_PLACE_IMAGE_ON_VISITS
            )

    val homeImageOption: StateFlow<HomeImageOption> = userPreferencesManager.homeImageOptionFlow
        .map { HomeImageOption.fromStoredValue(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            HomeImageOption.DEFAULT
        )

    val homeTextColor: StateFlow<HomeTextColor> = userPreferencesManager.homeTextColorFlow
        .map { HomeTextColor.fromStoredValue(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            HomeTextColor.WHITE
        )

    val hasAlternateHomeImage: StateFlow<Boolean> =
        (homeBackgroundStore?.revision ?: kotlinx.coroutines.flow.flowOf(0))
            .map { homeBackgroundStore?.exists() == true }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                homeBackgroundStore?.exists() == true
            )

    val alternateHomeImageRevision: StateFlow<Int> =
        (homeBackgroundStore?.revision ?: kotlinx.coroutines.flow.flowOf(0))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _homeImageSelectionError = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val homeImageSelectionError: SharedFlow<Int> = _homeImageSelectionError.asSharedFlow()

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

    fun updateColorTheme(theme: ColorTheme) = viewModelScope.launch {
        // Update the cached value immediately so views binding before the DataStore
        // write lands already resolve against the new palette.
        AppTheme.current = theme
        userPreferencesManager.saveColorTheme(theme.storedValue)
    }

    fun updateShowPlaceTypeSymbols(isEnabled: Boolean) = viewModelScope.launch {
        AppTheme.showPlaceTypeSymbols = isEnabled
        userPreferencesManager.saveShowPlaceTypeSymbols(isEnabled)
    }

    fun updateShowStockPlaceImageOnVisits(isEnabled: Boolean) = viewModelScope.launch {
        if (isEnabled == showStockPlaceImageOnVisits.value) return@launch
        userPreferencesManager.saveShowStockPlaceImageOnVisits(isEnabled)
    }

    fun selectHomeImageOption(option: HomeImageOption) {
        if (option == homeImageOption.value) return
        viewModelScope.launch {
            when (option) {
                HomeImageOption.DEFAULT -> {
                    userPreferencesManager.saveHomeTextColor(HomeTextColor.WHITE.storedValue)
                    userPreferencesManager.saveHomeImageOption(option.storedValue)
                }
                HomeImageOption.RANDOM -> {
                    val profileId = profileRepository?.scopedProfileId?.first()
                    val count = visitDao?.countVisitsWithPictures(profileId) ?: 0
                    if (count == 0) {
                        _homeImageSelectionError.emit(R.string.home_image_no_visit_photos)
                        return@launch
                    }
                    userPreferencesManager.saveHomeImageOption(option.storedValue)
                }
                HomeImageOption.SPECIFIC -> {
                    if (homeBackgroundStore?.exists() != true) {
                        _homeImageSelectionError.emit(R.string.home_image_no_imported_image)
                        return@launch
                    }
                    userPreferencesManager.saveHomeImageOption(option.storedValue)
                }
            }
        }
    }

    fun updateHomeTextColor(color: HomeTextColor) = viewModelScope.launch {
        userPreferencesManager.saveHomeTextColor(color.storedValue)
    }

    fun importHomeImage(uri: Uri) {
        val store = homeBackgroundStore ?: return
        val resolver = applicationContext?.contentResolver ?: return
        viewModelScope.launch {
            val saved = store.saveFromUri(resolver, uri)
            if (saved) {
                userPreferencesManager.saveHomeImageOption(HomeImageOption.SPECIFIC.storedValue)
            }
        }
    }

    fun alternateHomeImageFile() = homeBackgroundStore?.file()

    private fun activeProfileForGoals(): Profile? = activeProfile.value
}
