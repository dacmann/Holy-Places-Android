package net.dacworld.android.holyplacesofthelord.util

import android.content.Context
import net.dacworld.android.holyplacesofthelord.BuildConfig
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.UpdateDetails

/**
 * Shared What's New copy for the launch popup and Info version-tap replay.
 */
object WhatsNewNotes {

    const val VERSION_CODE_1_10 = 19
    const val VERSION_CODE_1_9 = 16
    const val VERSION_CODE_1_8_2 = 15

    fun forCurrentVersion(context: Context): UpdateDetails {
        return notesForVersionName(context, BuildConfig.VERSION_NAME)
            ?: notesForVersionBump(context, lastSeen = 0, current = BuildConfig.VERSION_CODE)
    }

    fun notesForVersionName(context: Context, versionName: String): UpdateDetails? {
        val short = versionName.substringBefore('-')
        return when {
            short.startsWith("1.10") -> details110(context)
            short.startsWith("1.9") -> details19(context)
            short.startsWith("1.8.2") -> details182(context)
            short.startsWith("1.8") -> details18(context)
            else -> null
        }
    }

    fun notesForVersionBump(context: Context, lastSeen: Int, current: Int): UpdateDetails {
        return when {
            current >= VERSION_CODE_1_10 && lastSeen < VERSION_CODE_1_10 -> details110(context)
            current >= VERSION_CODE_1_9 && lastSeen < VERSION_CODE_1_9 -> details19(context)
            current >= VERSION_CODE_1_8_2 && lastSeen < VERSION_CODE_1_8_2 -> details182(context)
            else -> details18(context)
        }
    }

    private fun details110(context: Context) = UpdateDetails(
        updateTitle = context.getString(R.string.whats_new_title_1_10),
        messages = listOf(
            context.getString(R.string.whats_new_achievement_share),
            context.getString(R.string.whats_new_add_change_place),
            context.getString(R.string.whats_new_info_version_taps),
            context.getString(R.string.whats_new_timeline_era),
            context.getString(R.string.whats_new_map_filters_hide)
        )
    )

    private fun details19(context: Context) = UpdateDetails(
        updateTitle = context.getString(R.string.whats_new_title_1_9),
        messages = listOf(
            context.getString(R.string.whats_new_map_timeline),
            context.getString(R.string.whats_new_historical_names),
            context.getString(R.string.whats_new_share_sheet),
            context.getString(R.string.whats_new_photo_rotation)
        )
    )

    private fun details182(context: Context) = UpdateDetails(
        updateTitle = context.getString(R.string.whats_new_title_1_8_2),
        messages = listOf(
            context.getString(R.string.whats_new_profiles),
            context.getString(R.string.whats_new_profile_scoped_data),
            context.getString(R.string.whats_new_record_copy_visits),
            context.getString(R.string.whats_new_migration_fix)
        )
    )

    private fun details18(context: Context) = UpdateDetails(
        updateTitle = context.getString(R.string.whats_new_title_1_8),
        messages = listOf(
            context.getString(R.string.whats_new_profiles),
            context.getString(R.string.whats_new_profile_scoped_data),
            context.getString(R.string.whats_new_record_copy_visits)
        )
    )
}
