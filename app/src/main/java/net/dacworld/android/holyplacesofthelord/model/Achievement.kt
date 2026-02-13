package net.dacworld.android.holyplacesofthelord.model

import java.util.Date

/**
 * Data class representing an achievement in the Holy Places app.
 * Matches the iOS Achievement structure for migration parity.
 */
data class Achievement(
    val name: String,
    val details: String,
    val iconName: String,
    val achieved: Date? = null,
    val placeAchieved: String? = null,
    val progress: Float? = null,
    val remaining: Int? = null
)
