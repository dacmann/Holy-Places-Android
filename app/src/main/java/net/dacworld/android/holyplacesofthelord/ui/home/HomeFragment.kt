package net.dacworld.android.holyplacesofthelord.ui.home

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.color
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // For shared ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder // For Dialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.MyApplication // For ViewModelFactory
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.DataViewModel // ViewModel
import net.dacworld.android.holyplacesofthelord.data.DataViewModelFactory // ViewModelFactory
import net.dacworld.android.holyplacesofthelord.data.UpdateDetails // Data class for dialog
import net.dacworld.android.holyplacesofthelord.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Access the shared DataViewModel
    private val dataViewModel: DataViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication
        DataViewModelFactory(application,application.templeDao, application.userPreferencesManager)
    }

    // Flag to manage if the initial seed dialog is currently being shown by this fragment
    private var isInitialSeedDialogShowing = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        // You can set up any specific HomeFragment UI elements from binding here
        // For example: binding.welcomeTextView.text = "Welcome Home!"
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observer for the Initial Seed Dialog details
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataViewModel.initialSeedUpdateDetails.collectLatest { details: UpdateDetails? ->
                    details?.let {
                        // Check if this specific dialog type is already showing or has been handled
                        // and if the fragment is still added to an activity
                        if (!isInitialSeedDialogShowing && isAdded) {
                            isInitialSeedDialogShowing = true // Set flag before showing
                            showInitialSeedDialog(
                                details = it,
                                onDismiss = {
                                    dataViewModel.initialSeedDialogShown()
                                    // isInitialSeedDialogShowing = false; // Reset in the dialog's dismiss listener
                                }
                            )
                        }
                    }
                }
            }
        }
        // --- START: ADDED CODE FOR INFO AND SETTINGS ICONS ---
        binding.infoIcon.setOnClickListener {
            InfoBottomSheetFragment().show(childFragmentManager, InfoBottomSheetFragment.TAG)
        }

        binding.settingsIcon.setOnClickListener {   11
            SettingsBottomSheetFragment().show(childFragmentManager, SettingsBottomSheetFragment.TAG)
        }
        // --- END: ADDED CODE FOR INFO AND SETTINGS ICONS ---
    }

    private fun showInitialSeedDialog(details: UpdateDetails, onDismiss: () -> Unit) {
        // Double check context and fragment's state before showing a dialog
        if (!isAdded || context == null) {
            Log.w("HomeFragment", "Dialog not shown for initial seed, fragment not attached or context null.")
            // Even if dialog isn't shown, we should ensure the viewmodel action is called
            // to prevent the dialog from trying to show again if the state was somehow missed.
            onDismiss()
            isInitialSeedDialogShowing = false // Ensure flag is reset
            return
        }

        val formattedMessage = details.messages.joinToString(separator = "\n\n")
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(details.updateTitle)
            .setMessage(formattedMessage)
            // We will set the button actions after create() for more control, or use setOnShowListener
            .setCancelable(false)
            // Positive button text is set here, action will be set later or handled by setOnShowListener logic
            .setPositiveButton(android.R.string.ok, null) // Set text, listener is overridden by setOnShowListener if needed or set after create
            .create() // Create the dialog instance

        // Set the OnShowListener to modify the button color after the dialog is shown
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.BaptismBlue))
            // You can also set an explicit click listener here if you want to override the one from setPositiveButton
            // However, it's often cleaner to let setPositiveButton handle the dismiss and your onDismiss logic.
            // If the listener set with .setPositiveButton isn't firing correctly after this,
            // you might need to re-set its listener here OR set the listener after .create() like this:
            // positiveButton?.setOnClickListener {
            //     dialog.dismiss()
            //     onDismiss()
            // }
        }

        // Set the click listener for the positive button
        // This ensures your onDismiss logic is correctly tied
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok)) { d, _ ->
            d.dismiss() // Dismiss the dialog
            onDismiss()   // Call your original onDismiss logic
        }


        dialog.setOnDismissListener {
            isInitialSeedDialogShowing = false
            Log.d("HomeFragment", "Initial seed dialog dismissed.")
        }

        dialog.show()
        Log.d("HomeFragment", "Showing initial seed dialog: ${details.updateTitle}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        isInitialSeedDialogShowing = false // Reset flag to be safe
    }
}
