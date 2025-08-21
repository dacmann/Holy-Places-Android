package net.dacworld.android.holyplacesofthelord.model // Ensure this matches your package

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import net.dacworld.android.holyplacesofthelord.R // Import your project's R file

// --- Enums and Helper Function from previous response ---

enum class PlaceFilter(@field:StringRes val displayNameRes: Int, @field:ColorRes val customColorRes: Int? = null) {
    HOLY_PLACES(R.string.place_filter_holy_places, R.color.grey_text),
    ACTIVE_TEMPLES(R.string.place_filter_active_temples, R.color.t2_temples),
    HISTORICAL_SITES(R.string.place_filter_historical_sites, R.color.t2_historic_site),
    VISITORS_CENTERS(R.string.place_filter_visitors_centers, R.color.t2_visitors_centers),
    TEMPLES_UNDER_CONSTRUCTION(
        R.string.place_filter_temples_under_construction,
        R.color.t2_under_construction
    ),
    ANNOUNCED_TEMPLES(R.string.place_filter_announced_temples, R.color.t2_announced_temples),
    ALL_TEMPLES(R.string.place_filter_all_temples, R.color.grey_text);
}

enum class PlaceSort(@field:StringRes val displayNameRes: Int) {
    ALPHABETICAL(R.string.place_sort_alphabetical),
    NEAREST(R.string.place_sort_nearest),
    COUNTRY(R.string.place_sort_country),
    DEDICATION_DATE(R.string.place_sort_dedication_date),
    SIZE(R.string.place_sort_size),
    ANNOUNCED_DATE(R.string.place_sort_announced_date);
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