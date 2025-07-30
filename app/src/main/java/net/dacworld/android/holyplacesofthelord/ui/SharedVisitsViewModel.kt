// ui/SharedVisitsViewModel.kt
package net.dacworld.android.holyplacesofthelord.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// Define Enum for Sort Order (Example)
enum class VisitSortOrder {
    BY_DATE_DESC, // Default
    BY_DATE_ASC,
    BY_PLACE_NAME_ASC,
    BY_PLACE_NAME_DESC
    // Add other sort orders as needed
}

// Define a class for Filter Options (Example - can be expanded)
data class VisitFilterOptions(
    val showOnlyFavorites: Boolean = false,
    val ordinanceBaptisms: Boolean = false,
    val ordinanceConfirmations: Boolean = false,
    val ordinanceInitiatories: Boolean = false,
    val ordinanceEndowments: Boolean = false,
    val ordinanceSealings: Boolean = false
    // Add other filter criteria
) {
    fun hasActiveOrdinanceFilter(): Boolean {
        return ordinanceBaptisms || ordinanceConfirmations || ordinanceInitiatories || ordinanceEndowments || ordinanceSealings
    }
}

class SharedVisitsViewModel : ViewModel() {

    private val _searchQuery = MutableLiveData<String?>()
    val searchQuery: LiveData<String?> = _searchQuery

    private val _sortOrder = MutableLiveData<VisitSortOrder>(VisitSortOrder.BY_DATE_DESC) // Default sort order
    val sortOrder: LiveData<VisitSortOrder> = _sortOrder

    private val _filterOptions = MutableLiveData<VisitFilterOptions>(VisitFilterOptions()) // Default filters
    val filterOptions: LiveData<VisitFilterOptions> = _filterOptions

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query
    }

    fun setSortOrder(sortOrder: VisitSortOrder) {
        _sortOrder.value = sortOrder
    }

    fun setFilterOptions(options: VisitFilterOptions) {
        _filterOptions.value = options
    }

    fun updateShowOnlyFavorites(isFavorite: Boolean) {
        _filterOptions.value = _filterOptions.value?.copy(showOnlyFavorites = isFavorite)
    }

    // Add specific methods to update individual filter options if needed
    // e.g., fun updateOrdinanceBaptismsFilter(enabled: Boolean) { ... }
}
