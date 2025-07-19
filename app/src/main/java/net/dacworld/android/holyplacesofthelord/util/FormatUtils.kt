// In a new file, e.g., utils/FormatUtils.kt
package net.dacworld.android.holyplacesofthelord.utils // Or your preferred utils package

import java.text.NumberFormat
import kotlin.math.roundToInt

const val METERS_IN_A_MILE = 1609.34
const val FEET_IN_A_METER = 3.28084
const val FEET_IN_A_MILE = 5280.0

/**
 * Formats a distance in meters into a display string (e.g., "1.2 miles", "800 feet").
 *
 * @param maxFractionDigitsForMiles The maximum number of decimal places for miles.
 * @return A formatted distance string, or null if the input distance is null.
 */
fun Double?.toDistanceDisplayString(maxFractionDigitsForMiles: Int = 1): String? {
    if (this == null || this < 0) return null // Handle null or invalid distances

    val miles = this / METERS_IN_A_MILE

    val numberFormat = NumberFormat.getInstance().apply {
        maximumFractionDigits = maxFractionDigitsForMiles
    }

    return if (miles >= 1.0) {
        "${numberFormat.format(miles)} miles"
    } else {
        // Under a mile, display in feet
        val feet = this * FEET_IN_A_METER
        if (feet < 1.0) { // Very close, less than 1 foot
            "< 1 foot"
        } else {
            "${feet.roundToInt()} feet" // Round feet to the nearest whole number
        }
    }
}

/**
 * Alternative formatting: Always show miles, and if less than a certain threshold (e.g., 0.1 miles),
 * also show feet in parentheses.
 */
fun Double?.toDetailedDistanceDisplayString(
    milesThresholdForFeetDisplay: Double = 0.2, // e.g., if less than 0.2 miles, show feet
    maxFractionDigitsForMiles: Int = 1
): String? {
    if (this == null || this < 0) return null

    val miles = this / METERS_IN_A_MILE
    val numberFormatMiles = NumberFormat.getInstance().apply {
        maximumFractionDigits = maxFractionDigitsForMiles
        minimumFractionDigits = 1 // Ensure at least one decimal place like "1.0 miles"
    }
    val milesString = "${numberFormatMiles.format(miles)} miles"

    return if (miles < milesThresholdForFeetDisplay && miles > 0) { // Avoid showing (0 feet) if exactly 0 miles
        val feet = this * FEET_IN_A_METER
        if (feet < 1.0) {
            "$milesString (< 1 foot)"
        } else {
            "$milesString (${feet.roundToInt()} feet)"
        }
    } else {
        milesString
    }
}
