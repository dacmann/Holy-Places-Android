package net.dacworld.android.holyplacesofthelord.ui.home // Or your preferred package

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.dacworld.android.holyplacesofthelord.BuildConfig
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.databinding.LayoutInfoBottomSheetBinding
import net.dacworld.android.holyplacesofthelord.util.IntentUtils
import kotlinx.coroutines.launch as coroutineLaunch

// If using ViewBinding (recommended)
// import net.dacworld.android.holyplacesofthelord.databinding.LayoutInfoBottomSheetBinding

class InfoBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutInfoBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val dataViewModel: DataViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = LayoutInfoBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUIListeners()

        // --- Observe Data Version and Update UI ---
        viewLifecycleOwner.lifecycleScope.coroutineLaunch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d("InfoFragmentCollector", "Starting to collect currentDataVersion.") // <<< ADD/CHECK LOG
                dataViewModel.currentDataVersion.collect { dataVersion ->
                    Log.d("InfoFragmentCollector", "Collected dataVersion: $dataVersion") // <<< ADD/CHECK LOG
                    val appVersion = BuildConfig.VERSION_NAME
                    val displayDataVersion = dataVersion ?: getString(R.string.status_unknown)
                    binding.versionInfoText.text = getString(R.string.version_info_format, appVersion, displayDataVersion)
                    Log.d("InfoFragmentCollector", "Set text to: ${binding.versionInfoText.text}") // <<< ADD/CHECK LOG
                }
            }
        }

    }

    private fun setupUIListeners() {
        binding.buttonCloseInfoSheet.setOnClickListener {
            dismiss()
        }

        // Email Button
        binding.emailLinkButton.setOnClickListener {
            context?.let { ctx ->
                IntentUtils.openEmail(ctx, "dacmann@icloud.com", "Holy Places App Feedback")
            }
        }

        // FAQ Button
        binding.faqButton.setOnClickListener {
            context?.let { ctx ->
                IntentUtils.openUrl(ctx, "https://dacworld.net/holyplaces/holyplacesfaq.html", "Could not open FAQ link.")
            }
        }

        // Article Link
        binding.articleLinkText.setOnClickListener {
            context?.let { ctx ->
                IntentUtils.openUrl(ctx, "https://oneclimbs.com/2011/11/21/restoring-the-pentagram-to-its-proper-place/", "Could not open article link.")
            }
        }
    }


    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog // Cast to BottomSheetDialog
        dialog?.let {
            // The view with this ID is a FrameLayout
            val bottomSheet = it.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) // Use FrameLayout here
            if (bottomSheet != null) { // bottomSheet is now explicitly FrameLayout
                val behavior = BottomSheetBehavior.from(bottomSheet)

                // Expand the bottom sheet to its maximum possible height
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
