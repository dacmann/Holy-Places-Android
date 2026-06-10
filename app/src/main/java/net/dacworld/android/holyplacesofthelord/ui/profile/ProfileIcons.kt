package net.dacworld.android.holyplacesofthelord.ui.profile

import net.dacworld.android.holyplacesofthelord.R

/**
 * Maps iOS SF Symbol names (stored in [Profile.iconName]) to Android Material
 * drawable resource IDs.  Add new entries here when the iOS icon palette grows.
 */
object ProfileIcons {

    private val sfSymbolToDrawable: Map<String, Int> = mapOf(
        "person.fill"          to R.drawable.ic_profile_person,
        "tree.fill"            to R.drawable.ic_profile_park,
        "star.fill"            to R.drawable.ic_star_filled,
        "heart.fill"           to R.drawable.ic_profile_favorite,
        "leaf.fill"            to R.drawable.ic_profile_eco,
        "sun.max.fill"         to R.drawable.ic_profile_wb_sunny,
        "moon.fill"            to R.drawable.ic_profile_nights_stay,
        "flame.fill"           to R.drawable.ic_profile_local_fire,
        "bolt.fill"            to R.drawable.ic_profile_bolt,
        "crown.fill"           to R.drawable.ic_profile_emoji_events,
        "bird.fill"            to R.drawable.ic_profile_air,
        "fish.fill"            to R.drawable.ic_profile_set_meal,
        "globe.americas.fill"  to R.drawable.ic_profile_public,
        "hands.sparkles.fill"  to R.drawable.ic_profile_clean_hands,
        "book.fill"            to R.drawable.ic_profile_menu_book,
        "mountain.2.fill"      to R.drawable.ic_profile_landscape,
        "sparkles"             to R.drawable.ic_profile_auto_awesome,
        "shield.fill"          to R.drawable.ic_profile_security
    )

    fun drawableResId(sfSymbolName: String): Int =
        sfSymbolToDrawable[sfSymbolName] ?: R.drawable.ic_profile_person
}
