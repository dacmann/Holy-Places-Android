package net.dacworld.android.holyplacesofthelord.util

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import net.dacworld.android.holyplacesofthelord.R

object ColorUtils {
    fun getTextColorForTempleType(context: Context, templeType: String?): Int {
        return when (templeType) {
            "T" -> ContextCompat.getColor(context, R.color.t2_temples)
            "H" -> ContextCompat.getColor(context, R.color.t2_historic_site)
            "A" -> ContextCompat.getColor(context, R.color.t2_announced_temples)
            "C" -> ContextCompat.getColor(context, R.color.t2_under_construction)
            "V" -> ContextCompat.getColor(context, R.color.t2_visitors_centers)
            else -> {
                Log.w("ColorUtils", "Unknown temple type code: '$templeType'")
                ContextCompat.getColor(context, R.color.app_colorOnSurface) // Default color
            }
        }
    }
}
