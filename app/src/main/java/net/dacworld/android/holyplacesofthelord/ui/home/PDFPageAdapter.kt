package net.dacworld.android.holyplacesofthelord.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.R

class PDFPageAdapter(
    private val renderer: PdfRenderer,
    private val scope: CoroutineScope,
    private val targetWidthPx: Int
) : RecyclerView.Adapter<PDFPageAdapter.PageViewHolder>() {

    /** PdfRenderer allows only one page open at a time — serialize all renders. */
    private val renderMutex = Mutex()

    /** Bitmap cache so rendered pages aren't re-rendered on scroll. */
    private val cache = HashMap<Int, Bitmap>()

    override fun getItemCount(): Int = renderer.pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_page, parent, false) as ImageView
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val cached = cache[position]
        if (cached != null) {
            holder.imageView.setImageBitmap(cached)
            return
        }

        holder.imageView.setImageBitmap(null)

        scope.launch {
            val bitmap = renderPage(position)
            cache[position] = bitmap
            withContext(Dispatchers.Main) {
                if (holder.bindingAdapterPosition == position) {
                    holder.imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private suspend fun renderPage(pageIndex: Int): Bitmap = renderMutex.withLock {
        withContext(Dispatchers.Default) {
            renderer.openPage(pageIndex).use { page ->
                val scale = targetWidthPx.toFloat() / page.width
                val height = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    fun clearCache() {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
}
