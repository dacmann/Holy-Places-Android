package net.dacworld.android.holyplacesofthelord.util

object CelebrationBoardConfig {
    const val DISPLAY_NAME = "Celebration Board"
    const val APP_DISPLAY_NAME = "Holy Places of the Lord"
    const val API_URL = "https://dacworld.net/holyplaces/api/celebrationboard.php"
    const val PAGE_URL = "https://dacworld.net/holyplaces/celebrationboard.html"

    val eligibleIconNames: Set<String> = setOf(
        "ach50T", "ach60T", "ach75T", "ach100T", "ach125T", "ach150T", "ach175T", "ach200T",
        "ach55H", "ach75H", "ach100H", "ach125H", "ach150H",
        "ach100B", "ach200B", "ach400B", "ach800B",
        "ach100I", "ach200I", "ach400I", "ach800I",
        "ach300E", "ach400E", "ach550E", "ach700E",
        "ach200S", "ach400S", "ach800S", "ach1600S",
        "ach200W", "ach400W", "ach800W", "ach1600W"
    )

    fun isEligible(iconName: String): Boolean = eligibleIconNames.contains(iconName)
}
