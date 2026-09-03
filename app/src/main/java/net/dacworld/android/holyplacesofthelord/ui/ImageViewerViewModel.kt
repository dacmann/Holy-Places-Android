package net.dacworld.android.holyplacesofthelord.ui

import androidx.lifecycle.ViewModel

/**
 * Holds the photo for [ImageViewerFragment] in memory so it is never stuffed into a
 * Fragment argument Bundle (which crashes with TransactionTooLargeException).
 */
class ImageViewerViewModel : ViewModel() {
    var imageUrl: String? = null
        private set
    var imageBytes: ByteArray? = null
        private set

    fun show(imageUrl: String? = null, imageBytes: ByteArray? = null) {
        this.imageUrl = imageUrl
        this.imageBytes = imageBytes
    }

    fun clear() {
        imageUrl = null
        imageBytes = null
    }
}
