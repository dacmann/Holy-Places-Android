package net.dacworld.android.holyplacesofthelord.ui.home

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch as coroutineLaunch
import net.dacworld.android.holyplacesofthelord.BuildConfig
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.databinding.LayoutInfoBottomSheetBinding
import net.dacworld.android.holyplacesofthelord.util.IntentUtils
import net.dacworld.android.holyplacesofthelord.util.WhatsNewNotes

class InfoBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutInfoBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val dataViewModel: DataViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutInfoBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUIListeners()

        viewLifecycleOwner.lifecycleScope.coroutineLaunch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    dataViewModel.currentDataVersion,
                    dataViewModel.currentDataChangesDate
                ) { dataVersion, _ -> dataVersion }.collect { dataVersion ->
                    val appVersion = BuildConfig.VERSION_NAME
                    val displayDataVersion = dataVersion ?: getString(R.string.status_unknown)
                    bindVersionText(appVersion, displayDataVersion)
                }
            }
        }
    }

    private fun bindVersionText(appVersion: String, dataVersion: String) {
        val prefix = getString(R.string.version_info_prefix)
        val separator = getString(R.string.version_info_separator)
        val full = prefix + appVersion + separator + dataVersion
        val spannable = SpannableString(full)
        val linkColor = ContextCompat.getColor(requireContext(), R.color.BaptismBlue)

        val appStart = prefix.length
        val appEnd = appStart + appVersion.length
        spannable.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                showAppNotes()
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = linkColor
                ds.isUnderlineText = false
            }
        }, appStart, appEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val dataStart = appEnd + separator.length
        val dataEnd = dataStart + dataVersion.length
        spannable.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                showDataNotes()
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = linkColor
                ds.isUnderlineText = false
            }
        }, dataStart, dataEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.versionInfoText.text = spannable
        binding.versionInfoText.movementMethod = LinkMovementMethod.getInstance()
        binding.versionInfoText.highlightColor = android.graphics.Color.TRANSPARENT
    }

    private fun showAppNotes() {
        val notes = WhatsNewNotes.forCurrentVersion(requireContext())
        val title = getString(R.string.whats_new_in_version, BuildConfig.VERSION_NAME)
        val message = notes.messages.joinToString("\n\n").ifBlank {
            getString(R.string.no_update_notes_available)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showDataNotes() {
        val date = dataViewModel.currentDataChangesDate.value
        val title = if (!date.isNullOrBlank() && date != "Unknown") {
            getString(R.string.data_update_title, date)
        } else {
            getString(R.string.data_update_title_fallback)
        }
        val summary = dataViewModel.updateChangesSummary.value
        val message = if (summary.isBlank() || summary == "No update information available.") {
            getString(R.string.no_update_notes_available)
        } else {
            summary
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setupUIListeners() {
        binding.buttonCloseInfoSheet.setOnClickListener {
            dismiss()
        }

        binding.emailLinkButton.setOnClickListener {
            context?.let { ctx ->
                val deviceName = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
                val androidVersion = android.os.Build.VERSION.RELEASE
                val appVersion = BuildConfig.VERSION_NAME
                val dataVersion = dataViewModel.currentDataVersion.value ?: "Unknown"
                val emailBody = "\n\n\n-----------------------------------\n" +
                        "Device: $deviceName\n" +
                        "Android Version: $androidVersion\n" +
                        "Holy Places Version: $appVersion | $dataVersion\n" +
                        "-----------------------------------"
                IntentUtils.openEmail(ctx, "dacmann@icloud.com", "Holy Places App Feedback", emailBody)
            }
        }

        binding.faqButton.setOnClickListener {
            context?.let { ctx ->
                IntentUtils.openUrl(ctx, "https://dacworld.net/holyplaces/holyplacesfaq.html", "Could not open FAQ link.")
            }
        }

        binding.articleLinkText.setOnClickListener {
            context?.let { ctx ->
                IntentUtils.openUrl(ctx, "https://oneclimbs.com/2011/11/21/restoring-the-pentagram-to-its-proper-place/", "Could not open article link.")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.let {
            val bottomSheet = it.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isFitToContents = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "InfoBottomSheetFragment"
    }
}
