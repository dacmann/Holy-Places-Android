package net.dacworld.android.holyplacesofthelord.model // Ensure this matches your package

import net.dacworld.android.holyplacesofthelord.R // Import your project's R file

// --- Enums and Helper Function from previous response ---

enum class PlaceFilter(val displayName: String, val customColorRes: Int? = null) {
    HOLY_PLACES("Holy Places",R.color.grey_text),
    ACTIVE_TEMPLES("Active Temples", R.color.t2_temples),
    HISTORICAL_SITES("Historical Sites", R.color.t2_historic_site),
    VISITORS_CENTERS("Visitors' Centers", R.color.t2_visitors_centers),
    TEMPLES_UNDER_CONSTRUCTION("Temples Under Construction", R.color.t2_under_construction),
    ANNOUNCED_TEMPLES("Announced Temples", R.color.t2_announced_temples),
    ALL_TEMPLES("All Temples", R.color.grey_text);

    companion object {
        // Optional: fun at(index: Int): PlaceFilter = values()[index]
        // Optional: fun fromDisplayName(name: String): PlaceFilter? = values().find { it.displayName == name }
    }
}

enum class PlaceSort(val displayName: String) {
    ALPHABETICAL("Alphabetical"),
    NEAREST("Nearest"),
    COUNTRY("Country"),
    DEDICATION_DATE("Dedication Date"),
    SIZE("Size"),
    ANNOUNCED_DATE("Announced Date");

    companion object {
        // Optional: fun at(index: Int, currentFilter: PlaceFilter): PlaceSort = getSortOptionsForFilter(currentFilter)[index]
        // Optional: fun fromDisplayName(name: String): PlaceSort? = values().find { it.displayName == name }
    }
}

// Helper function to get dynamic sort options
// This is a top-level function in this file, accessible by importing the file's package.
// Alternatively, you could put it inside a companion object of PlaceSort or PlaceFilter,
// or in a dedicated utility object (e.g., object OptionsHelper { ... }).
// Keeping it top-level here is fine for now.
fun getSortOptionsForFilter(filter: PlaceFilter): List<PlaceSort> {
    return when (filter) {
        PlaceFilter.ACTIVE_TEMPLES -> listOf(
            PlaceSort.ALPHABETICAL,
            PlaceSort.NEAREST,
            PlaceSort.COUNTRY,
            PlaceSort.DEDICATION_DATE,
            PlaceSort.SIZE,
            PlaceSort.ANNOUNCED_DATE // Assuming announced date is relevant for active temples if they were announced prior
        )
        PlaceFilter.TEMPLES_UNDER_CONSTRUCTION,
        PlaceFilter.ANNOUNCED_TEMPLES,
        PlaceFilter.ALL_TEMPLES -> // ALL_TEMPLES might imply more sort options depending on data
            listOf(
                PlaceSort.ALPHABETICAL,
                PlaceSort.NEAREST,
                PlaceSort.COUNTRY,
                PlaceSort.ANNOUNCED_DATE
            )
        // For HOLY_PLACES, HISTORICAL_SITES, VISITORS_CENTERS:
        else -> listOf(
            PlaceSort.ALPHABETICAL,
            PlaceSort.NEAREST,
            PlaceSort.COUNTRY
        )
    }
}