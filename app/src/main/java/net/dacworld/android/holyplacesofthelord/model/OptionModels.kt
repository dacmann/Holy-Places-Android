package net.dacworld.android.holyplacesofthelord.model // Ensure this matches your package

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import net.dacworld.android.holyplacesofthelord.R // Import your project's R file
import net.dacworld.android.holyplacesofthelord.util.ColorUtils

// --- Enums and Helper Function from previous response ---

/**
 * [placeTypeCode] is the place type this filter narrows to (null for the "any type"
 * filters), and drives both the color and the optional type symbol so the filter menus
 * always match the lists.
 */
enum class PlaceFilter(
    @field:StringRes val displayNameRes: Int,
    val placeTypeCode: String? = null
) {
    HOLY_PLACES(R.string.place_filter_holy_places),
    ACTIVE_TEMPLES(R.string.place_filter_active_temples, "T"),
    HISTORICAL_SITES(R.string.place_filter_historical_sites, "H"),
    VISITORS_CENTERS(R.string.place_filter_visitors_centers, "V"),
    TEMPLES_UNDER_CONSTRUCTION(R.string.place_filter_temples_under_construction, "C"),
    ANNOUNCED_TEMPLES(R.string.place_filter_announced_temples, "A"),
    ALL_TEMPLES(R.string.place_filter_all_temples);

    @get:ColorRes
    val customColorRes: Int?
        get() = if (placeTypeCode == null) R.color.grey_text
        else ColorUtils.getPlaceTypeColorRes(placeTypeCode)
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