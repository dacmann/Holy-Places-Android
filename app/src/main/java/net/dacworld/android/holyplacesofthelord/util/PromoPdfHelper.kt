package net.dacworld.android.holyplacesofthelord.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object PromoPdfHelper {

    /**
     * Returns a cached copy of the promotional PDF, downloading from [AppShareLinks.PROMO_PDF_URL]
     * on first use.
     */
    suspend fun ensureCached(context: Context): File = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        val dest = File(shareDir, AppShareLinks.PROMO_PDF_CACHE_NAME)
        if (dest.exists() && dest.length() > 0L) {
            return@withContext dest
        }

        val connection = (URL(AppShareLinks.PROMO_PDF_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }

        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Failed to download promotional PDF (HTTP ${connection.responseCode})")
            }
            connection.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest
        } finally {
            connection.disconnect()
        }
    }
}
