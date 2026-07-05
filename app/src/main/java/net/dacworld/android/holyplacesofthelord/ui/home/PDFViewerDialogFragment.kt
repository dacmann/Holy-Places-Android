package net.dacworld.android.holyplacesofthelord.ui.home

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.CancellationSignal
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentPdfViewerBinding
import net.dacworld.android.holyplacesofthelord.util.AppShareLinks
import java.io.File

class PDFViewerDialogFragment : DialogFragment() {

    private var _binding: FragmentPdfViewerBinding? = null
    private val binding get() = _binding!!

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfCacheFile: File? = null
    private var pageAdapter: PDFPageAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pdfToolbar.setNavigationOnClickListener { dismissAllowingStateLoss() }
        binding.pdfToolbar.inflateMenu(R.menu.menu_pdf_viewer)
        binding.pdfToolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_pdf_print -> { printPDF(); true }
                R.id.action_pdf_share -> { sharePDF(); true }
                else -> false
            }
        }

        binding.pdfRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = ensurePdfCached()
                pdfCacheFile = file
                openRenderer(file)
                setupAdapter()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open PDF", e)
            } finally {
                binding.pdfLoadingIndicator.isVisible = false
            }
        }
    }

    private suspend fun ensurePdfCached(): File = withContext(Dispatchers.IO) {
        val shareDir = File(requireContext().cacheDir, "share").apply { mkdirs() }
        val dest = File(shareDir, AppShareLinks.PROMO_PDF_CACHE_NAME)
        if (!dest.exists()) {
            requireContext().assets.open(AppShareLinks.PROMO_PDF_ASSET).use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }
        dest
    }

    private suspend fun openRenderer(file: File) = withContext(Dispatchers.IO) {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        parcelFileDescriptor = pfd
        pdfRenderer = PdfRenderer(pfd)
    }

    private fun setupAdapter() {
        val renderer = pdfRenderer ?: return
        val displayWidth = resources.displayMetrics.widthPixels - 16 // subtract padding
        pageAdapter = PDFPageAdapter(renderer, viewLifecycleOwner.lifecycleScope, displayWidth)
        binding.pdfRecyclerView.adapter = pageAdapter
    }

    private fun printPDF() {
        val file = pdfCacheFile ?: return
        val pageCount = pdfRenderer?.pageCount ?: PrintDocumentInfo.PAGE_COUNT_UNKNOWN
        val printManager = requireContext().getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            getString(R.string.share_promo_pdf),
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    callback.onLayoutFinished(
                        PrintDocumentInfo.Builder(AppShareLinks.PROMO_PDF_CACHE_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(pageCount)
                            .build(),
                        newAttributes != oldAttributes
                    )
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    try {
                        file.inputStream().use { input ->
                            ParcelFileDescriptor.AutoCloseOutputStream(destination!!).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        Log.e(TAG, "Print write failed", e)
                        callback.onWriteFailed(e.message)
                    }
                }
            },
            null
        )
    }

    private fun sharePDF() {
        val file = pdfCacheFile ?: return
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, null))
        } catch (e: Exception) {
            Log.e(TAG, "Share PDF failed", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageAdapter?.clearCache()
        pageAdapter = null
        pdfRenderer?.close()
        pdfRenderer = null
        parcelFileDescriptor?.close()
        parcelFileDescriptor = null
        _binding = null
    }

    companion object {
        const val TAG = "PDFViewerDialog"
    }
}
