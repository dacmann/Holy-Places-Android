package net.dacworld.android.holyplacesofthelord.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update // Required for the .update {} extension

// Data class to represent the entire UI state managed by this ViewModel
data class ToolbarUiState(
    val title: String = "Holy Places", // Default title
    val count: Int = 0,
    val searchQuery: String = ""
)

class SharedToolbarViewModel : ViewModel() {
    // Private MutableStateFlow that can be updated internally
    private val _uiState = MutableStateFlow(ToolbarUiState())

    // Publicly exposed StateFlow that is read-only for observers
    val uiState: StateFlow<ToolbarUiState> = _uiState.asStateFlow()

    fun updateToolbarInfo(title: String, count: Int) {
        // Use the .update extension function for atomic updates to the StateFlow
        _uiState.update { currentState ->
            currentState.copy(
                title = title,
                count = count
            )
        }
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
