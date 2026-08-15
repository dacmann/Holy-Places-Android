package net.dacworld.android.holyplacesofthelord.model

import net.dacworld.android.holyplacesofthelord.util.CelebrationBoardConfig

val Achievement.isCelebrationBoardEligible: Boolean
    get() = CelebrationBoardConfig.isEligible(iconName)

/** Achievement type letter: T, H, B, I, E, S, W (null for Temple Consistent / unknown). */
val Achievement.achievementType: String?
    get() {
        if (!iconName.startsWith("ach")) return null
        val body = iconName.drop(3)
        if (body.contains("MT")) return null
        val last = body.lastOrNull() ?: return null
        return if (last.isLetter()) last.uppercaseChar().toString() else null
    }

val Achievement.threshold: Int?
    get() {
        if (!iconName.startsWith("ach")) return null
        var body = iconName.drop(3)
        if (body.contains("MT")) return null
        if (body.lastOrNull()?.isLetter() == true) {
            body = body.dropLast(1)
        }
        return body.toIntOrNull()
    }

val Achievement.unlockKey: String
    get() = "$iconName|$name"
