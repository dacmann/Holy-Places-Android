package net.dacworld.android.holyplacesofthelord.ui.placedetail

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.ItemPlaceDetailPhotoBinding
import net.dacworld.android.holyplacesofthelord.util.VisitPhotoStamper
import java.util.Date

/**
 * One page of the place details photo pager.
 */
sealed class PlacePhotoPage {
    /** The place's own image, always shown first when there is one. */
    data class Stock(val pictureData: ByteArray?, val pictureUrl: String?) : PlacePhotoPage()

    /** A photo from one of your visits, with the visit date drawn into the image. */
    data class VisitPhotoPage(
        val visitId: Long,
        val dateVisited: Date?,
        val picture: ByteArray
    ) : PlacePhotoPage()
}

/**
 * Pages through the place's stock image and the photos from your visits.
 *
 * Visit photos are decoded and date-stamped off the main thread and cached by visit id,
 * so swiping back and forth doesn't redo the work. Stock and visit pages use different
 * view types so ViewPager2 cannot recycle the stock image onto a later visit page.
 */
class PlaceDetailPhotoAdapter(
    private val scope: CoroutineScope,
    private val onPhotoClicked: (PlacePhotoPage) -> Unit
) : RecyclerView.Adapter<PlaceDetailPhotoAdapter.PhotoViewHolder>() {

    private var pages: List<PlacePhotoPage> = emptyList()
    private val stampedPhotos = mutableMapOf<Long, Bitmap>()

    fun submitPages(newPages: List<PlacePhotoPage>) {
        val oldPages = pages
        if (oldPages.isNotEmpty() &&
            newPages.size >= oldPages.size &&
            oldPages.indices.all { index -> samePage(oldPages[index], newPages[index]) }
        ) {
            val inserted = newPages.size - oldPages.size
            pages = newPages
            if (inserted > 0) {
                notifyItemRangeInserted(oldPages.size, inserted)
            }
            return
        }
        pages = newPages
        stampedPhotos.clear()
        notifyDataSetChanged()
    }

    private fun samePage(left: PlacePhotoPage, right: PlacePhotoPage): Boolean = when {
        left is PlacePhotoPage.Stock && right is PlacePhotoPage.Stock -> true
        left is PlacePhotoPage.VisitPhotoPage && right is PlacePhotoPage.VisitPhotoPage ->
            left.visitId == right.visitId
        else -> false
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = when (pages[position]) {
        is PlacePhotoPage.Stock -> VIEW_TYPE_STOCK
        is PlacePhotoPage.VisitPhotoPage -> VIEW_TYPE_VISIT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPlaceDetailPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(pages[position], position)
    }

    override fun onViewRecycled(holder: PhotoViewHolder) {
        holder.onRecycled()
        super.onViewRecycled(holder)
    }

    inner class PhotoViewHolder(
        private val binding: ItemPlaceDetailPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var stampJob: Job? = null

        fun bind(page: PlacePhotoPage, position: Int) {
            stampJob?.cancel()
            stampJob = null
            binding.placeDetailPhotoImage.dispose()
            binding.placeDetailPhotoImage.setOnClickListener { onPhotoClicked(page) }
            binding.placeDetailPhotoImage.contentDescription = binding.root.context.getString(
                R.string.place_detail_photo_page,
                position + 1,
                itemCount
            )
            when (page) {
                is PlacePhotoPage.Stock -> bindStock(page)
                is PlacePhotoPage.VisitPhotoPage -> bindVisitPhoto(page)
            }
        }

        fun onRecycled() {
            stampJob?.cancel()
            stampJob = null
            binding.placeDetailPhotoImage.dispose()
            binding.placeDetailPhotoImage.setImageDrawable(null)
        }

        private fun bindStock(page: PlacePhotoPage.Stock) {
            when {
                page.pictureData != null -> binding.placeDetailPhotoImage.load(page.pictureData) {
                    placeholder(R.drawable.default_placeholder_image)
                    error(R.drawable.default_placeholder_image)
                }
                !page.pictureUrl.isNullOrBlank() -> binding.placeDetailPhotoImage.load(page.pictureUrl) {
                    placeholder(R.drawable.default_placeholder_image)
                    error(R.drawable.default_placeholder_image)
                }
                else -> binding.placeDetailPhotoImage.setImageResource(R.drawable.default_placeholder_image)
            }
        }

        private fun bindVisitPhoto(page: PlacePhotoPage.VisitPhotoPage) {
            val cached = stampedPhotos[page.visitId]
            if (cached != null) {
                binding.placeDetailPhotoImage.setImageBitmap(cached)
                return
            }
            binding.placeDetailPhotoImage.setImageResource(R.drawable.default_placeholder_image)
            val boundVisitId = page.visitId
            stampJob = scope.launch {
                val stamped = withContext(Dispatchers.Default) {
                    VisitPhotoStamper.decodeAndStamp(page.picture, page.dateVisited)
                } ?: return@launch
                stampedPhotos[boundVisitId] = stamped
                val current = pages.getOrNull(bindingAdapterPosition)
                if (current is PlacePhotoPage.VisitPhotoPage && current.visitId == boundVisitId) {
                    binding.placeDetailPhotoImage.setImageBitmap(stamped)
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_STOCK = 0
        private const val VIEW_TYPE_VISIT = 1
    }
}
