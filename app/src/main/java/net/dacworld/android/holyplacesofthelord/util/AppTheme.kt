package net.dacworld.android.holyplacesofthelord.util

import net.dacworld.android.holyplacesofthelord.R

/**
 * The color palette used for place names, place-type accents and ordinance text.
 * [storedValue] matches the value persisted by the iOS app so the two stay in sync.
 */
enum class ColorTheme(val storedValue: String, val displayNameRes: Int) {
    RED_GREEN("4414", R.string.color_theme_red_green),
    PURPLE_ORANGE("3830", R.string.color_theme_purple_orange),
    MONO("mono", R.string.color_theme_mono);

    companion object {
        val DEFAULT = PURPLE_ORANGE

        fun fromStoredValue(value: String?): ColorTheme =
            values().firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}

/**
 * Holds the active [ColorTheme] and whether place-type symbols are shown, so that
 * [ColorUtils] can resolve colors and symbols synchronously while binding views.
 *
 * Seeded from DataStore at startup and updated whenever the setting changes.
 */
object AppTheme {
    @Volatile
    var current: ColorTheme = ColorTheme.DEFAULT

    @Volatile
    var showPlaceTypeSymbols: Boolean = false
}
