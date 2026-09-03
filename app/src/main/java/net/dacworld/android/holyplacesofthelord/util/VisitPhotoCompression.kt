package net.dacworld.android.holyplacesofthelord.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Caps visit photos at 1920px on the long edge and ~1.5 MB so base64 XML stays
 * well under parser CDATA limits. Matches iOS `VisitPhotoCompression`.
 */
object VisitPhotoCompression {
    const val MAX_DIMENSION = 1920
    const val MAX_BYTES = 1_500_000
    private const val JPEG_QUALITY = 70
    private const val JPEG_QUALITY_FLOOR = 40
    private const val TAG = "VisitPhotoCompression"

    /**
     * Returns JPEG bytes suitable for visit XML / storage, or null if the data
     * cannot be decoded. Unchanged when already within the size and dimension caps.
     */
    fun encodedData(data: ByteArray): ByteArray? {
        if (data.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "Could not decode image bounds")
            return null
        }
        if (data.size <= MAX_BYTES && longest <= MAX_DIMENSION) {
            return data
        }
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(longest)
            }
            val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, options)
            if (decoded == null) {
                Log.w(TAG, "Could not decode image for compression")
                return null
            }
            var working = decoded
            val resized = resized(working)
            if (resized != working) {
                working.recycle()
                working = resized
            }
            val jpeg = jpegData(working)
            if (!working.isRecycled) working.recycle()
            jpeg
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Out of memory compressing visit photo", oom)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress visit photo", e)
            null
        }
    }

    private fun calculateInSampleSize(longest: Int): Int {
        var inSampleSize = 1
        var half = longest / 2
        while (half / inSampleSize >= MAX_DIMENSION) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun resized(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun jpegData(bitmap: Bitmap): ByteArray? {
        var quality = JPEG_QUALITY
        ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                return null
            }
            var data = out.toByteArray()
            while (data.size > MAX_BYTES && quality > JPEG_QUALITY_FLOOR) {
                quality -= 10
                out.reset()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    return data
                }
                data = out.toByteArray()
            }
            return data
        }
    }
}
