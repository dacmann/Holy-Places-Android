package net.dacworld.android.holyplacesofthelord.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dacworld.android.holyplacesofthelord.data.ProfileRepository

class ProfileViewModelFactory(
    private val repository: ProfileRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
