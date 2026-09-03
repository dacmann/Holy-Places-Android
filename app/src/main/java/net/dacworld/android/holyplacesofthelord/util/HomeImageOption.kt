package net.dacworld.android.holyplacesofthelord.util

/**
 * Home-screen background mode. [storedValue] matches the DataStore key values
 * used by the Android settings so Default / Random / Specific stay stable.
 */
enum class HomeImageOption(val storedValue: String) {
    DEFAULT("default"),
    RANDOM("random"),
    SPECIFIC("specific");

    companion object {
        fun fromStoredValue(value: String?): HomeImageOption =
            values().firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}

/**
 * Overlay text/icon color on the home screen. [storedValue] matches iOS
 * `homeTextColor` (0 = white, 1 = black).
 */
enum class HomeTextColor(val storedValue: Int) {
    WHITE(0),
    BLACK(1);

    companion object {
        fun fromStoredValue(value: Int?): HomeTextColor =
            values().firstOrNull { it.storedValue == value } ?: WHITE
    }
}
