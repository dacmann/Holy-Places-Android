package net.dacworld.android.holyplacesofthelord.data

import net.dacworld.android.holyplacesofthelord.model.Temple

// This data class is now in its own file, accessible globally within the module.
data class HolyPlacesData(
    val temples: List<Temple>,
    val version: String? = null,
    val changesDate: String? = null,
    val changesMsg1: String? = null,
    val changesMsg2: String? = null,
    val changesMsg3: String? = null
)
