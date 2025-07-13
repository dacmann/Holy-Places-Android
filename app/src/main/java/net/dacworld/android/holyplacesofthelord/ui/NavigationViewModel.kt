package net.dacworld.android.holyplacesofthelord.ui

import android.util.Log // For logging
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationViewModel : ViewModel() {

    // Using StateFlow for navigation events. String? where null means no pending navigation.
    private val _navigateToPlaceDetail = MutableStateFlow<String?>(null)
    val navigateToPlaceDetail: StateFlow<String?> = _navigateToPlaceDetail.asStateFlow()

    fun requestNavigationToPlaceDetail(placeId: String) {
        _navigateToPlaceDetail.value = placeId
        Log.d("NavigationViewModel", "Navigation requested for place ID: $placeId")
    }

    // Call this method after navigation has been performed to prevent re-navigation
    // on configuration change or re-subscription.
    fun onPlaceDetailNavigated() {
        _navigateToPlaceDetail.value = null // Reset the event
        Log.d("NavigationViewModel", "Navigation event to place detail reset.")
    }
}