package net.dacworld.android.holyplacesofthelord.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.data.ProfileRepository
import net.dacworld.android.holyplacesofthelord.model.Profile

class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeProfile: StateFlow<Profile?> = repository.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val profilesEnabled: StateFlow<Boolean> = repository.profilesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val activeProfileId: StateFlow<String?> = repository.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun createProfile(name: String, iconName: String = "person.fill") = viewModelScope.launch {
        repository.createProfile(name, iconName)
    }

    fun updateProfile(profile: Profile) = viewModelScope.launch {
        repository.updateProfile(profile)
    }

    fun deleteProfile(profile: Profile) = viewModelScope.launch {
        if (!profile.isDefault) {
            repository.deleteProfile(profile)
        }
    }

    fun setActiveProfile(profileId: String) = viewModelScope.launch {
        repository.setActiveProfile(profileId)
    }

    fun toggleProfilesEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setProfilesEnabled(enabled)
    }

    fun copyVisits(visitIds: List<Long>, targetProfileId: String) = viewModelScope.launch {
        repository.copyVisitsToProfile(visitIds, targetProfileId)
    }
}
