package net.dacworld.android.holyplacesofthelord.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

object ProfileContract {
    const val TABLE_NAME = "profiles"
    const val COLUMN_ID = "profile_id"
    const val COLUMN_NAME = "name"
    const val COLUMN_IS_DEFAULT = "is_default"
    const val COLUMN_ICON_NAME = "icon_name"
    const val COLUMN_CREATED_DATE = "created_date"
    const val COLUMN_ANNUAL_VISIT_GOAL = "annual_visit_goal"
    const val COLUMN_ANNUAL_BAPTISM_GOAL = "annual_baptism_goal"
    const val COLUMN_ANNUAL_INITIATORY_GOAL = "annual_initiatory_goal"
    const val COLUMN_ANNUAL_ENDOWMENT_GOAL = "annual_endowment_goal"
    const val COLUMN_ANNUAL_SEALING_GOAL = "annual_sealing_goal"
    const val COLUMN_EXCLUDE_NON_ORDINANCE_VISITS = "exclude_non_ordinance_visits"

    const val MAX_PROFILES = 10
    const val DEFAULT_PROFILE_NAME = "Me"
    const val DEFAULT_ICON_NAME = "person.fill"
}

/**
 * Local-only profile for grouping visits by household member.
 * Mirrors the iOS Profile CoreData entity (see ProfileManager.swift).
 *
 * The profile system is purely device-local — no authentication, no cloud sync.
 * Up to [ProfileContract.MAX_PROFILES] profiles can exist; one is marked
 * as the default ("Me") profile and cannot be deleted.
 */
@Entity(
    tableName = ProfileContract.TABLE_NAME,
    indices = [Index(value = [ProfileContract.COLUMN_ID], unique = true)]
)
data class Profile(
    @PrimaryKey
    @ColumnInfo(name = ProfileContract.COLUMN_ID)
    val profileId: String,

    @ColumnInfo(name = ProfileContract.COLUMN_NAME)
    val name: String,

    @ColumnInfo(name = ProfileContract.COLUMN_IS_DEFAULT)
    val isDefault: Boolean = false,

    @ColumnInfo(name = ProfileContract.COLUMN_ICON_NAME, defaultValue = "'person.fill'")
    val iconName: String = ProfileContract.DEFAULT_ICON_NAME,

    @ColumnInfo(name = ProfileContract.COLUMN_CREATED_DATE)
    val createdDate: Date,

    @ColumnInfo(name = ProfileContract.COLUMN_ANNUAL_VISIT_GOAL, defaultValue = "0")
    val annualVisitGoal: Int = 0,

    @ColumnInfo(name = ProfileContract.COLUMN_ANNUAL_BAPTISM_GOAL, defaultValue = "0")
    val annualBaptismGoal: Int = 0,

    @ColumnInfo(name = ProfileContract.COLUMN_ANNUAL_INITIATORY_GOAL, defaultValue = "0")
    val annualInitiatoryGoal: Int = 0,

    @ColumnInfo(name = ProfileContract.COLUMN_ANNUAL_ENDOWMENT_GOAL, defaultValue = "0")
    val annualEndowmentGoal: Int = 0,

    @ColumnInfo(name = ProfileContract.COLUMN_ANNUAL_SEALING_GOAL, defaultValue = "0")
    val annualSealingGoal: Int = 0,

    @ColumnInfo(name = ProfileContract.COLUMN_EXCLUDE_NON_ORDINANCE_VISITS, defaultValue = "0")
    val excludeNonOrdinanceVisits: Boolean = false
) {
    companion object {
        /**
         * Available SF-Symbol-style icon names. The same identifiers are stored on iOS
         * to keep profile data interoperable across platforms.
         * Maps to Android Material Icons via [ProfileIcons.materialIconResId].
         */
        val AVAILABLE_ICONS: List<String> = listOf(
            "person.fill",
            "tree.fill",
            "star.fill",
            "heart.fill",
            "leaf.fill",
            "sun.max.fill",
            "moon.fill",
            "flame.fill",
            "bolt.fill",
            "crown.fill",
            "bird.fill",
            "fish.fill",
            "globe.americas.fill",
            "hands.sparkles.fill",
            "book.fill",
            "mountain.2.fill",
            "sparkles",
            "shield.fill"
        )
    }
}
