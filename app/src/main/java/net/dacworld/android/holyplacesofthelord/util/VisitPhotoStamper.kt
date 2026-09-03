package net.dacworld.android.holyplacesofthelord.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import java.text.DateFormat
import java.util.Date

/**
 * Draws the visit date across the bottom of a visit photo, matching the iOS place
 * details pager where each visit photo carries its date.
 */
object VisitPhotoStamper {

    private const val TAG = "VisitPhotoStamper"

    // Sized as fractions of the image height so the caption scales with the photo.
    private const val TEXT_HEIGHT_FRACTION = 0.07f
    private const val BAND_HEIGHT_FRACTION = 0.11f
    private const val MAX_DECODED_EDGE = 1600

    fun decodeAndStamp(picture: ByteArray, dateVisited: Date?): Bitmap? {
        val source = decodeScaled(picture) ?: return null
        if (dateVisited == null) return source
        return try {
            val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(dateVisited)
            stamp(source, dateText)
        } catch (e: Exception) {
            Log.e(TAG, "Could not stamp visit photo; showing it unstamped.", e)
            source
        }
    }

    private fun decodeScaled(picture: ByteArray): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
            val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (longestEdge / sampleSize > MAX_DECODED_EDGE) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
        } catch (e: Exception) {
            Log.e(TAG, "Could not decode visit photo.", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Ran out of memory decoding visit photo.", e)
            null
        }
    }

    private fun stamp(source: Bitmap, dateText: String): Bitmap {
        val target = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        if (target !== source) source.recycle()
        val canvas = Canvas(target)

        val bandHeight = target.height * BAND_HEIGHT_FRACTION
        val scrimPaint = Paint().apply { color = Color.argb(130, 0, 0, 0) }
        canvas.drawRect(
            0f,
            target.height - bandHeight,
            target.width.toFloat(),
            target.height.toFloat(),
            scrimPaint
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = target.height * TEXT_HEIGHT_FRACTION
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val baseline = target.height - (bandHeight - textPaint.textSize) / 2f - textPaint.descent()
        canvas.drawText(dateText, target.width / 2f, baseline, textPaint)
        return target
    }
}
