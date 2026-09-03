package net.dacworld.android.holyplacesofthelord.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Stores the user-imported home-screen background as a JPEG in app files,
 * matching iOS `homeAlternatePicture` without putting image bytes in DataStore.
 */
class HomeBackgroundStore private constructor(private val context: Context) {

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun file(): File = File(context.filesDir, FILE_NAME)

    fun exists(): Boolean {
        val stored = file()
        return stored.exists() && stored.length() > 0
    }

    /**
     * Reads [uri], rotates from EXIF, downsamples to [MAX_DIMENSION], and writes JPEG.
     * Returns true when the file was saved.
     */
    suspend fun saveFromUri(contentResolver: ContentResolver, uri: Uri): Boolean {
        val saved = withContext(Dispatchers.IO) {
            val jpeg = resizeAndCompress(contentResolver, uri) ?: return@withContext false
            try {
                FileOutputStream(file()).use { out ->
                    out.write(jpeg)
                    out.flush()
                }
                true
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write home background JPEG", e)
                false
            }
        }
        if (saved) {
            _revision.value = _revision.value + 1
        }
        return saved
    }

    private fun resizeAndCompress(contentResolver: ContentResolver, imageUri: Uri): ByteArray? {
        try {
            val rotationDegrees: Float = try {
                contentResolver.openInputStream(imageUri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    when (exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                Log.w(TAG, "Could not read EXIF orientation, assuming 0°", e)
                0f
            }

            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            if (options.outWidth == -1 || options.outHeight == -1) {
                Log.e(TAG, "Failed to decode image bounds. URI: $imageUri")
                return null
            }

            options.inSampleSize = calculateInSampleSize(options, MAX_DIMENSION, MAX_DIMENSION)
            options.inJustDecodeBounds = false

            var bitmap = contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap. URI: $imageUri")
                return null
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }

            var finalBitmap = bitmap
            val currentWidth = bitmap.width
            val currentHeight = bitmap.height
            if (currentWidth > MAX_DIMENSION || currentHeight > MAX_DIMENSION) {
                val ratio = if (currentWidth > currentHeight) {
                    MAX_DIMENSION.toFloat() / currentWidth
                } else {
                    MAX_DIMENSION.toFloat() / currentHeight
                }
                val newWidth = (currentWidth * ratio).toInt()
                val newHeight = (currentHeight * ratio).toInt()
                if (newWidth > 0 && newHeight > 0) {
                    finalBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    if (finalBitmap != bitmap) bitmap.recycle()
                }
            }

            val outputStream = java.io.ByteArrayOutputStream()
            val success = finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            if (finalBitmap != bitmap && !finalBitmap.isRecycled) finalBitmap.recycle()
            else if (!bitmap.isRecycled) bitmap.recycle()

            if (!success) {
                Log.e(TAG, "JPEG compression failed. URI: $imageUri")
                return null
            }
            return outputStream.toByteArray()
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Out of memory processing home background URI: $imageUri", oom)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error processing home background URI: $imageUri", e)
            return null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        private const val TAG = "HomeBackgroundStore"
        private const val FILE_NAME = "home_alternate.jpg"
        private const val MAX_DIMENSION = 2048
        private const val JPEG_QUALITY = 85

        @Volatile
        private var INSTANCE: HomeBackgroundStore? = null

        fun getInstance(context: Context): HomeBackgroundStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HomeBackgroundStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
