package net.dacworld.android.holyplacesofthelord.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.databinding.FragmentQrPreviewBottomSheetBinding
import net.dacworld.android.holyplacesofthelord.util.QRCodeGenerator
import java.io.File

class QRCodePreviewBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentQrPreviewBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val url by lazy { requireArguments().getString(ARG_URL)!! }
    private val titleRes by lazy { requireArguments().getInt(ARG_TITLE_RES) }
    private val fileNamePrefix by lazy { requireArguments().getString(ARG_FILE_PREFIX)!! }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrPreviewBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.qrTitle.setText(titleRes)
        binding.qrUrlLabel.text = url

        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = QRCodeGenerator.generate(url)
            if (_binding != null) {
                binding.qrImage.setImageBitmap(bitmap)
            }
        }

        binding.qrShareButton.setOnClickListener {
            shareQRImage()
        }
    }

    private fun shareQRImage() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bitmap = QRCodeGenerator.generate(url)
                val shareDir = File(requireContext().cacheDir, "share").apply { mkdirs() }
                val file = File(shareDir, "${fileNamePrefix}.png")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }

                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, url)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, null))
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to share QR code", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "QRCodePreviewBottomSheet"
        private const val ARG_URL = "url"
        private const val ARG_TITLE_RES = "title_res"
        private const val ARG_FILE_PREFIX = "file_prefix"

        fun newInstance(url: String, titleRes: Int, fileNamePrefix: String) =
            QRCodePreviewBottomSheetFragment().apply {
                arguments = bundleOf(
                    ARG_URL to url,
                    ARG_TITLE_RES to titleRes,
                    ARG_FILE_PREFIX to fileNamePrefix
                )
            }
    }
}
