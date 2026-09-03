package net.dacworld.android.holyplacesofthelord.model

import java.util.Date

/**
 * A photo attached to a visit, with just enough detail for the place details
 * photo pager and the home-screen random image. Room maps query aliases
 * (`id`, `dateVisited`, `picture`) onto these properties.
 */
data class VisitPhoto(
    val id: Long,
    val dateVisited: Date?,
    val picture: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisitPhoto) return false
        return id == other.id && dateVisited == other.dateVisited
    }

    override fun hashCode(): Int = id.hashCode() * 31 + (dateVisited?.hashCode() ?: 0)
}
