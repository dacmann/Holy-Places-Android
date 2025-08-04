// ui/SharedVisitsViewModel.kt
package net.dacworld.android.holyplacesofthelord.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.dacworld.android.holyplacesofthelord.R

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

enum class VisitPlaceTypeFilter(val typeCode: String?, val displayNameResource: Int) {
    ALL(null, R.string.filter_type_all_visits),
    ACTIVE_TEMPLES("T", R.string.filter_type_active_temples),
    HISTORICAL_SITES("H", R.string.filter_type_historical_sites),
    VISITORS_CENTERS("V", R.string.filter_type_visitors_centers),
    UNDER_CONSTRUCTION("C", R.string.filter_type_under_construction);
    // "Other" is omitted for now
}

class SharedVisitsViewModel : ViewModel() {

    private val _searchQuery = MutableLiveData<String?>()
    val searchQuery: LiveData<String?> = _searchQuery

    private val _sortOrder = MutableLiveData<VisitSortOrder>(VisitSortOrder.BY_DATE_DESC) // Default sort order
    val sortOrder: LiveData<VisitSortOrder> = _sortOrder

    private val _filterOptions = MutableLiveData<VisitFilterOptions>(VisitFilterOptions()) // Default filters
    val filterOptions: LiveData<VisitFilterOptions> = _filterOptions

    // --- NEW LiveData and method for the Place Type Filter ---
    private val _selectedPlaceTypeFilter = MutableLiveData(VisitPlaceTypeFilter.ALL)
    val selectedPlaceTypeFilter: LiveData<VisitPlaceTypeFilter> = _selectedPlaceTypeFilter

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query
    }

    fun setSortOrder(sortOrder: VisitSortOrder) {
        if (_sortOrder.value != sortOrder) {
            _sortOrder.value = sortOrder
        }
    }

    fun setFilterOptions(options: VisitFilterOptions) {
        _filterOptions.value = options
    }

    // This method updates the existing filterOptions for scope buttons
    fun setScopeFilterOptions(options: VisitFilterOptions) {
        if (_filterOptions.value != options) {
            _filterOptions.value = options
        }
    }

    // Example method for updating a specific scope button filter (if needed)
    fun updateShowOnlyFavorites(isFavorite: Boolean) {
        val currentOptions = _filterOptions.value ?: VisitFilterOptions()
        if (currentOptions.showOnlyFavorites != isFavorite) {
            _filterOptions.value = currentOptions.copy(showOnlyFavorites = isFavorite)
        }
    }

    // --- NEW Method for updating the Place Type Filter ---
    fun setPlaceTypeFilter(placeTypeFilter: VisitPlaceTypeFilter) {
        android.util.Log.d("SharedViewModel", "setPlaceTypeFilter called with: $placeTypeFilter. Current LiveData value is: ${_selectedPlaceTypeFilter.value}")
        if (_selectedPlaceTypeFilter.value != placeTypeFilter) {
            _selectedPlaceTypeFilter.value = placeTypeFilter
            android.util.Log.d("SharedViewModel", "LiveData updated to: ${_selectedPlaceTypeFilter.value}")
        } else {
            android.util.Log.d("SharedViewModel", "New filter is same as current. LiveData NOT updated.")
        }
    }
}
