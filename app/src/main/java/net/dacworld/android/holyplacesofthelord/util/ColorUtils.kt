package net.dacworld.android.holyplacesofthelord.util

import android.content.Context
import android.util.Log
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import net.dacworld.android.holyplacesofthelord.R

enum class Ordinance {
    BAPTISMS,
    CONFIRMATIONS,
    INITIATORIES,
    ENDOWMENTS,
    SEALINGS,
    HOURS_WORKED
}

object ColorUtils {
    fun getTextColorForTempleType(context: Context, templeType: String?): Int {
        return ContextCompat.getColor(context, getPlaceTypeColorRes(templeType))
    }

    /**
     * Color resource for a place type under the active [AppTheme] palette.
     * Mono uses the on-surface label color so names and filter rows stay readable.
     */
    @ColorRes
    fun getPlaceTypeColorRes(templeType: String?): Int {
        if (AppTheme.current == ColorTheme.MONO) {
            return R.color.app_colorOnSurface
        }
        val useRedGreen = AppTheme.current == ColorTheme.RED_GREEN
        return when (templeType) {
            "T" -> if (useRedGreen) R.color.t1_temples else R.color.t2_temples
            "H" -> if (useRedGreen) R.color.t1_historic_site else R.color.t2_historic_site
            "A" -> if (useRedGreen) R.color.t1_announced_temples else R.color.t2_announced_temples
            "C" -> if (useRedGreen) R.color.t1_under_construction else R.color.t2_under_construction
            "V" -> if (useRedGreen) R.color.t1_visitors_centers else R.color.t2_visitors_centers
            else -> {
                Log.w("ColorUtils", "Unknown temple type code: '$templeType'")
                R.color.app_colorOnSurface
            }
        }
    }

    /**
     * Filled shape for a place type, matching iOS `placeTypeSymbolName`.
     * Returns null when the setting is off or the type has no symbol (Other).
     */
    @DrawableRes
    fun getPlaceTypeSymbolRes(templeType: String?): Int? {
        if (!AppTheme.showPlaceTypeSymbols) return null
        return when (templeType) {
            "T" -> R.drawable.ic_type_circle_filled
            "C" -> R.drawable.ic_type_square_filled
            "A" -> R.drawable.ic_type_triangle_filled
            "H" -> R.drawable.ic_type_star_filled
            "V" -> R.drawable.ic_type_diamond_filled
            else -> null
        }
    }

    @ColorRes
    fun getOrdinanceColorRes(ordinance: Ordinance): Int {
        if (AppTheme.current == ColorTheme.MONO) {
            return R.color.app_colorOnSurface
        }
        return when (ordinance) {
            Ordinance.BAPTISMS -> R.color.BaptismBlue
            Ordinance.CONFIRMATIONS -> R.color.Confirmations
            Ordinance.INITIATORIES -> R.color.Initiatories
            Ordinance.ENDOWMENTS -> R.color.Endowments
            Ordinance.SEALINGS -> R.color.Sealings
            Ordinance.HOURS_WORKED -> R.color.app_colorOnSurface
        }
    }

    fun getOrdinanceColor(context: Context, ordinance: Ordinance): Int {
        return ContextCompat.getColor(context, getOrdinanceColorRes(ordinance))
    }
}
