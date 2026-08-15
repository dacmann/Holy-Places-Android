package net.dacworld.android.holyplacesofthelord.util

import android.content.Context
import net.dacworld.android.holyplacesofthelord.R

/**
 * Year-based temple eras used by Map Timeline, matching iOS `MapVC.eraName(for:)`.
 * Places-list dedication headers remain order-based and are intentionally separate.
 */
object TempleEra {
    fun nameForYear(context: Context, year: Int): String {
        val res = when {
            year < 1877 -> R.string.temple_era_restoration
            year < 1919 -> R.string.temple_era_pioneer
            year < 1964 -> R.string.temple_era_expansion
            year < 1983 -> R.string.temple_era_strengthening
            year < 1999 -> R.string.temple_era_growth
            year < 2003 -> R.string.temple_era_explosive
            year < 2019 -> R.string.temple_era_hastening
            else -> R.string.temple_era_unparalleled
        }
        return context.getString(res)
    }
}
