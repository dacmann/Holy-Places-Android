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

    // NEW: Add support for place list navigation
    private val _navigateToNextPlace = MutableStateFlow<String?>(null)
    val navigateToNextPlace: StateFlow<String?> = _navigateToNextPlace.asStateFlow()
    
    private val _navigateToPreviousPlace = MutableStateFlow<String?>(null)
    val navigateToPreviousPlace: StateFlow<String?> = _navigateToPreviousPlace.asStateFlow()

    // Add support for visit navigation
    private val _navigateToNextVisit = MutableStateFlow<Long?>(null)
    val navigateToNextVisit: StateFlow<Long?> = _navigateToNextVisit.asStateFlow()
    
    private val _navigateToPreviousVisit = MutableStateFlow<Long?>(null)
    val navigateToPreviousVisit: StateFlow<Long?> = _navigateToPreviousVisit.asStateFlow()

    fun requestNavigationToPlaceDetail(placeId: String) {
        _navigateToPlaceDetail.value = placeId
        Log.d("NavigationViewModel", "Navigation requested for place ID: $placeId")
    }

    // NEW: Methods for place list navigation
    fun requestNavigationToNextPlace(placeId: String) {
        _navigateToNextPlace.value = placeId
        Log.d("NavigationViewModel", "Next place navigation requested for place ID: $placeId")
    }
    
    fun requestNavigationToPreviousPlace(placeId: String) {
        _navigateToPreviousPlace.value = placeId
        Log.d("NavigationViewModel", "Previous place navigation requested for place ID: $placeId")
    }

    // Call this method after navigation has been performed to prevent re-navigation
    // on configuration change or re-subscription.
    fun onPlaceDetailNavigated() {
        _navigateToPlaceDetail.value = null // Reset the event
        Log.d("NavigationViewModel", "Navigation event to place detail reset.")
    }

    // NEW: Reset methods for place list navigation
    fun onNextPlaceNavigated() {
        _navigateToNextPlace.value = null
        Log.d("NavigationViewModel", "Next place navigation event reset.")
    }
    
    fun onPreviousPlaceNavigated() {
        _navigateToPreviousPlace.value = null
        Log.d("NavigationViewModel", "Previous place navigation event reset.")
    }

    // Add visit navigation methods
    fun requestNavigationToNextVisit(visitId: Long) {
        _navigateToNextVisit.value = visitId
        Log.d("NavigationViewModel", "Next visit navigation requested for visit ID: $visitId")
    }
    
    fun requestNavigationToPreviousVisit(visitId: Long) {
        _navigateToPreviousVisit.value = visitId
        Log.d("NavigationViewModel", "Previous visit navigation requested for visit ID: $visitId")
    }
    
    fun onNextVisitNavigated() {
        _navigateToNextVisit.value = null
        Log.d("NavigationViewModel", "Next visit navigation event reset.")
    }
    
    fun onPreviousVisitNavigated() {
        _navigateToPreviousVisit.value = null
        Log.d("NavigationViewModel", "Previous visit navigation event reset.")
    }
}