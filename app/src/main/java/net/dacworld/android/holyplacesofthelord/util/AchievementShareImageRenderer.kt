package net.dacworld.android.holyplacesofthelord.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.model.Achievement
import java.text.SimpleDateFormat
import java.util.Locale

object AchievementShareImageRenderer {

    private const val WIDTH = 675
    private const val HEIGHT = 410

    fun shareCaption(context: Context, achievement: Achievement): String {
        return context.getString(
            R.string.achievement_share_caption,
            achievement.name,
            achievement.details
        )
    }

    fun render(context: Context, achievement: Achievement): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val accent = accentColor(achievement.iconName)
        val placeColor = if (achievement.iconName.lastOrNull() == 'H') {
            Color.rgb(115, 0, 0)
        } else {
            Color.rgb(0, 0, 128)
        }

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 6f, barPaint)
        canvas.drawRect(0f, HEIGHT - 6f, WIDTH.toFloat(), HEIGHT.toFloat(), barPaint)

        val logo = ContextCompat.getDrawable(context, R.drawable.morningstarmoroni)
        logo?.let {
            val side = 76
            it.setBounds(10, 12, 10 + side, 12 + side)
            it.draw(canvas)
        }

        var y = 22f
        y += drawCentered(
            canvas,
            CelebrationBoardConfig.APP_DISPLAY_NAME,
            y,
            serif(36f, bold = true),
            Color.rgb(64, 56, 46)
        ) + 1f

        val tagline = context.getString(R.string.home_tagline_quote)
        val taglinePaint = serif(20f, italic = true).apply { color = Color.DKGRAY }
        val taglineWidth = taglinePaint.measureText(tagline)
        val taglineX = (WIDTH - taglineWidth) / 2f
        canvas.drawText(tagline, taglineX, y + textBaseline(taglinePaint), taglinePaint)
        y += taglinePaint.textSize

        val reference = context.getString(R.string.home_tagline_reference)
        val refPaint = serif(15f, italic = true).apply { color = Color.GRAY }
        val refWidth = refPaint.measureText(reference)
        canvas.drawText(
            reference,
            taglineX + taglineWidth - refWidth,
            y + textBaseline(refPaint),
            refPaint
        )
        y += refPaint.textSize + 18f

        y += drawCentered(
            canvas,
            context.getString(R.string.achievement_unlocked_heading),
            y,
            serif(40f, bold = true),
            accent
        ) + 8f

        val contentTop = y
        val contentBottom = HEIGHT - 8f
        val contentHeight = contentBottom - contentTop
        val iconSide = minOf(220f, contentHeight)
        val iconX = 12f
        val textX = iconX + iconSide + 10f
        val textWidth = WIDTH - textX - 12f

        val iconRes = context.resources.getIdentifier(
            achievement.iconName.lowercase(Locale.US),
            "drawable",
            context.packageName
        ).takeIf { it != 0 } ?: context.resources.getIdentifier("ach12mt", "drawable", context.packageName)
        if (iconRes != 0) {
            ContextCompat.getDrawable(context, iconRes)?.let { drawable ->
                val iconY = contentTop + (contentHeight - iconSide) / 2f
                drawable.setBounds(
                    iconX.toInt(),
                    iconY.toInt(),
                    (iconX + iconSide).toInt(),
                    (iconY + iconSide).toInt()
                )
                drawable.draw(canvas)
            }
        }

        val titlePaint = serif(32f, bold = true).apply { color = accent }
        val detailsPaint = serif(22f).apply { color = Color.DKGRAY }
        val metaPaint = serif(20f).apply { color = Color.GRAY }
        val placePaint = serif(20f).apply { color = placeColor }

        val titleHeight = measureMultiline(achievement.name, titlePaint, textWidth)
        val detailsHeight = measureMultiline(achievement.details, detailsPaint, textWidth)
        val hasPlace = !achievement.placeAchieved.isNullOrBlank()
        val hasDate = achievement.achieved != null
        var textBlockHeight = titleHeight + 4f + detailsHeight
        if (hasPlace || hasDate) textBlockHeight += 12f
        if (hasPlace) textBlockHeight += metaPaint.textSize
        if (hasPlace && hasDate) textBlockHeight += 4f
        if (hasDate) textBlockHeight += metaPaint.textSize

        var textY = contentTop + ((contentHeight - textBlockHeight) / 2f).coerceAtLeast(0f)
        textY += drawMultiline(canvas, achievement.name, textX, textY, textWidth, titlePaint)
        textY += 4f
        textY += drawMultiline(canvas, achievement.details, textX, textY, textWidth, detailsPaint)
        if (hasPlace || hasDate) textY += 12f
        if (hasPlace) {
            val atText = "at "
            canvas.drawText(atText, textX, textY + textBaseline(metaPaint), metaPaint)
            canvas.drawText(
                achievement.placeAchieved.orEmpty(),
                textX + metaPaint.measureText(atText),
                textY + textBaseline(placePaint),
                placePaint
            )
            textY += metaPaint.textSize + 4f
        }
        if (hasDate) {
            val formatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val dateText = "on ${formatter.format(achievement.achieved!!)}"
            canvas.drawText(dateText, textX, textY + textBaseline(metaPaint), metaPaint)
        }

        return bitmap
    }

    private fun accentColor(iconName: String): Int {
        return when (iconName.lastOrNull()) {
            'B' -> Color.rgb(0, 84, 147)
            'I' -> Color.rgb(50, 50, 0)
            'E' -> Color.rgb(255, 140, 0)
            'S' -> Color.rgb(104, 71, 141)
            'W' -> Color.rgb(95, 99, 104)
            'H' -> Color.rgb(115, 0, 0)
            'T' -> Color.rgb(0, 0, 128)
            else -> Color.rgb(0, 0, 128)
        }
    }

    private fun serif(size: Float, bold: Boolean = false, italic: Boolean = false): Paint {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.SERIF, style)
        }
    }

    private fun textBaseline(paint: Paint): Float {
        return -paint.fontMetrics.ascent
    }

    private fun drawCentered(canvas: Canvas, text: String, y: Float, paint: Paint, color: Int): Float {
        paint.color = color
        val width = paint.measureText(text)
        canvas.drawText(text, (WIDTH - width) / 2f, y + textBaseline(paint), paint)
        return paint.textSize
    }

    private fun measureMultiline(text: String, paint: Paint, maxWidth: Float): Float {
        val lines = wrap(text, paint, maxWidth)
        return lines.size * paint.textSize
    }

    private fun drawMultiline(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint
    ): Float {
        val lines = wrap(text, paint, maxWidth)
        var currentY = y
        for (line in lines) {
            canvas.drawText(line, x, currentY + textBaseline(paint), paint)
            currentY += paint.textSize
        }
        return lines.size * paint.textSize
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf("") }
    }
}
