package net.dacworld.android.holyplacesofthelord.util

import net.dacworld.android.holyplacesofthelord.model.Temple

/**
 * Synthetic temples for free-text "Other" visits. Room requires a Temple row
 * because [net.dacworld.android.holyplacesofthelord.model.Visit.placeID] is a FK.
 * These rows are excluded from Places, Map, and XML orphan deletion.
 */
object OtherPlaceHelper {
    const val ID_PREFIX = "other:"
    const val TYPE = "O"

    fun isOtherId(id: String?): Boolean = !id.isNullOrBlank() && id.startsWith(ID_PREFIX)

    fun isOtherType(type: String?): Boolean = type == TYPE

    fun idForName(name: String): String {
        val slug = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "unnamed" }
        return ID_PREFIX + slug
    }

    fun createTemple(name: String): Temple {
        val trimmed = name.trim()
        return Temple(
            id = idForName(trimmed),
            name = trimmed,
            type = TYPE
        )
    }
}
