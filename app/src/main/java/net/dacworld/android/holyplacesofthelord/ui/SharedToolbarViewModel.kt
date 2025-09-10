package net.dacworld.android.holyplacesofthelord.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update // Required for the .update {} extension

// Data class to represent the entire UI state managed by this ViewModel
data class ToolbarUiState(
    val title: String = "Holy Places",
    val count: Int = 0,
    val searchQuery: String = "",
    val subtitle: String = "",
    val isSearchModeActive: Boolean = false
)

class SharedToolbarViewModel : ViewModel() {
    // Private MutableStateFlow that can be updated internally
    private val _uiState = MutableStateFlow(ToolbarUiState())

    // Publicly exposed StateFlow that is read-only for observers
    val uiState: StateFlow<ToolbarUiState> = _uiState.asStateFlow()

    fun updateToolbarInfo(
        title: String,
        count: Int,
        subtitle: String, // <<< ADD THIS PARAMETER
        currentSearchQuery: String? = null // <<< ADD THIS PARAMETER (make it nullable with a default if desired)
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                title = title,
                count = count,
                subtitle = subtitle, // Now 'subtitle' (on the right) refers to the new function parameter
                searchQuery = currentSearchQuery
                    ?: currentState.searchQuery // Now 'currentSearchQuery' (on the right) refers to the new function parameter
                // And 'searchQuery' (on the left) refers to the field in ToolbarUiState
            )
        }
        Log.d(
            "SharedToolbarVM",
            "Toolbar updated: Title='$title', Count=$count, Subtitle='$subtitle', Query='${_uiState.value.searchQuery}'"
        ) // Optional: update log
    }
    // Optional: If you need to update only the subtitle sometimes
    fun updateSubtitle(newSubtitle: String) { // Changed parameter name to avoid confusion
        _uiState.update { currentState ->
            currentState.copy(subtitle = newSubtitle) // This copy is fine
        }
        Log.d("SharedToolbarVM", "Subtitle updated: '$newSubtitle'")
    }

    fun setSearchQuery(query: String) {
        _uiState.update { currentState ->
            // Only update if the query actually changed to avoid unnecessary recompositions/updates
            if (currentState.searchQuery != query) {
                currentState.copy(searchQuery = query)
            } else {
                currentState // Return current state if no change
            }
        }
    }

}
