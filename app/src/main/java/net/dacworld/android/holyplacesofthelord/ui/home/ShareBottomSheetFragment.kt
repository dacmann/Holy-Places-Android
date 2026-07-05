package net.dacworld.android.holyplacesofthelord.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.dacworld.android.holyplacesofthelord.databinding.FragmentShareBottomSheetBinding
import net.dacworld.android.holyplacesofthelord.util.AppShareLinks

class ShareBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentShareBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShareBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowSendLinkAndroid.setOnClickListener {
            sendLink(AppShareLinks.GOOGLE_PLAY_URL.toString())
        }

        binding.rowSendLinkIos.setOnClickListener {
            sendLink(AppShareLinks.APP_STORE_URL.toString())
        }

        binding.rowQrAndroid.setOnClickListener {
            showQRPreview(
                url = AppShareLinks.GOOGLE_PLAY_URL.toString(),
                titleRes = net.dacworld.android.holyplacesofthelord.R.string.share_qr_android,
                fileNamePrefix = "qr_google_play"
            )
        }

        binding.rowQrIos.setOnClickListener {
            showQRPreview(
                url = AppShareLinks.APP_STORE_URL.toString(),
                titleRes = net.dacworld.android.holyplacesofthelord.R.string.share_qr_ios,
                fileNamePrefix = "qr_app_store"
            )
        }

        binding.rowPromoPdf.setOnClickListener {
            PDFViewerDialogFragment().show(parentFragmentManager, PDFViewerDialogFragment.TAG)
            dismissAllowingStateLoss()
        }
    }

    private fun sendLink(url: String) {
        val shareText = "${AppShareLinks.SHARE_TEXT}\n$url"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, null))
        dismissAllowingStateLoss()
    }

    private fun showQRPreview(url: String, titleRes: Int, fileNamePrefix: String) {
        QRCodePreviewBottomSheetFragment.newInstance(url, titleRes, fileNamePrefix)
            .show(parentFragmentManager, QRCodePreviewBottomSheetFragment.TAG)
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ShareBottomSheet"
    }
}
