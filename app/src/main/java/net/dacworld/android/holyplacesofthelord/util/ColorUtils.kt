package net.dacworld.android.holyplacesofthelord.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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

    /**
     * Map pin bitmap for the active color theme. Purple/Orange uses the original PNGs;
     * Red/Green recolors the pin head to the matching t1 color.
     */
    fun mapPinBitmap(context: Context, @DrawableRes pinRes: Int, typeCode: String?): Bitmap {
        val source = BitmapFactory.decodeResource(context.resources, pinRes)
            ?: throw IllegalStateException("Missing map pin drawable $pinRes")
        if (AppTheme.current != ColorTheme.RED_GREEN || typeCode == null) {
            return source
        }
        return recolorPinHead(source, getTextColorForTempleType(context, typeCode))
    }

    private fun recolorPinHead(source: Bitmap, targetColor: Int): Bitmap {
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val targetHsv = FloatArray(3)
        Color.colorToHSV(targetColor, targetHsv)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val hsv = FloatArray(3)

        // Average brightness of the original pin head, so the typical pixel
        // lands on the theme color instead of staying as bright as the PNG.
        var valueSum = 0.0
        var valueCount = 0
        for (pixel in pixels) {
            if (Color.alpha(pixel) < 20) continue
            Color.colorToHSV(pixel, hsv)
            if (hsv[1] < 0.12f) continue
            valueSum += hsv[2]
            valueCount++
        }
        val sourceValue = if (valueCount > 0) (valueSum / valueCount).toFloat() else 0.75f
        val valueScale = if (sourceValue > 0.05f) targetHsv[2] / sourceValue else 1f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = Color.alpha(pixel)
            if (alpha < 20) continue
            Color.colorToHSV(pixel, hsv)
            // Keep white highlights, the metallic shaft, and near-black pixels.
            if (hsv[1] < 0.12f) continue
            hsv[0] = targetHsv[0]
            hsv[1] = targetHsv[1]
            hsv[2] = (hsv[2] * valueScale).coerceIn(0.08f, 1f)
            pixels[i] = Color.HSVToColor(alpha, hsv)
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return bitmap
    }
}
