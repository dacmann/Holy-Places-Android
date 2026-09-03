package net.dacworld.android.holyplacesofthelord.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Prefixes a place name or filter title with the matching type symbol when the
 * setting is on. Used by list rows, the Places filter spinner, and filter menus.
 */
fun placeTypeSymbolTitle(
    context: Context,
    title: CharSequence,
    placeType: String?,
    tintColor: Int
): CharSequence {
    val symbolRes = ColorUtils.getPlaceTypeSymbolRes(placeType) ?: return title
    val raw = ContextCompat.getDrawable(context, symbolRes) ?: return title
    val drawable = DrawableCompat.wrap(raw.mutate())
    DrawableCompat.setTint(drawable, tintColor)
    val size = (12f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    drawable.setBounds(0, 0, size, size)

    val builder = SpannableStringBuilder()
    builder.append("\uFFFC")
    builder.setSpan(
        CenteredImageSpan(drawable),
        0,
        1,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    builder.append("\u00A0")
    builder.append(title)
    return builder
}

private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable, ALIGN_BOTTOM) {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val rect = drawable.bounds
        if (fm != null) {
            val fontHeight = fm.descent - fm.ascent
            val extra = (rect.height() - fontHeight).coerceAtLeast(0)
            val half = extra / 2
            fm.ascent -= half
            fm.top -= half
            fm.descent += extra - half
            fm.bottom += extra - half
        }
        return rect.right
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val b = drawable
        canvas.save()
        val transY = top + (bottom - top - b.bounds.height()) / 2
        canvas.translate(x, transY.toFloat())
        b.draw(canvas)
        canvas.restore()
    }
}
