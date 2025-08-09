package net.dacworld.android.holyplacesofthelord.ui.home

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // For shared ViewModel
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder // For Dialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import net.dacworld.android.holyplacesofthelord.MyApplication // For ViewModelFactory
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.DataViewModel // ViewModel
import net.dacworld.android.holyplacesofthelord.data.DataViewModelFactory // ViewModelFactory
import net.dacworld.android.holyplacesofthelord.data.UpdateDetails // Data class for dialog
import net.dacworld.android.holyplacesofthelord.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Correct way to initialize HomeViewModel with its factory
    private val homeViewModel: HomeViewModel by viewModels {
        val application = requireActivity().application as MyApplication
        HomeViewModelFactory(application.userPreferencesManager, application.visitDao) // Ensure this matches your MyApplication properties
    }

    // Access the shared DataViewModel
    private val dataViewModel: DataViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication
        DataViewModelFactory(application,application.templeDao, application.visitDao,application.userPreferencesManager)
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

        // Apply window insets for navigation and IME
        setupWindowInsets(binding.root)

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
        setupGoalProgressObservers()
    }

    // In HomeFragment.kt

    private fun setupWindowInsets(viewReceivingInsets: View) {
        val activityRootView = requireActivity().window.decorView
        val appBottomNavView = activityRootView.findViewById<BottomNavigationView>(R.id.main_bottom_navigation)

        // Function to calculate and apply spacer height
        val applySpacerHeight = {
            ViewCompat.getRootWindowInsets(viewReceivingInsets)?.let { insets ->
                val systemNavigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                var effectiveNavHeight = systemNavigationBars.bottom

                if (appBottomNavView != null && appBottomNavView.visibility == View.VISIBLE && appBottomNavView.height > 0) {
                    Log.d("HomeFragmentInsets", "BNV Current Height: ${appBottomNavView.height}")
                    if (effectiveNavHeight < appBottomNavView.height) {
                        effectiveNavHeight = appBottomNavView.height
                    }
                } else {
                    Log.d("HomeFragmentInsets", "BNV Not ready or height is 0. BNV: $appBottomNavView, Visible: ${appBottomNavView?.visibility}, Height: ${appBottomNavView?.height}")
                }

                val baseMarginDp = 0
                val baseMarginPx = (baseMarginDp * resources.displayMetrics.density).toInt()
                val desiredSpacerHeight = kotlin.math.max(effectiveNavHeight, imeInsets.bottom) + baseMarginPx
                Log.d("HomeFragmentInsets", "Desired Spacer Height: $desiredSpacerHeight (SysNav: ${systemNavigationBars.bottom}, IME: ${imeInsets.bottom}, EffNav: $effectiveNavHeight)")


                binding.bottomInsetSpacer.layoutParams.height = desiredSpacerHeight
                binding.bottomInsetSpacer.requestLayout()
            }
        }

        // Initial application via setOnApplyWindowInsetsListener
        ViewCompat.setOnApplyWindowInsetsListener(viewReceivingInsets) { _, insets ->
            // This listener will be called when system insets change (e.g. keyboard)
            // We can call our common function here.
            applySpacerHeight()
            insets // Return original insets, we are handling it manually via spacer
        }

        // If the BottomNavigationView is already laid out, apply immediately.
        // Otherwise, add a GlobalLayoutListener to wait for it.
        if (appBottomNavView != null && appBottomNavView.isLaidOut && appBottomNavView.height > 0) {
            Log.d("HomeFragmentInsets", "BNV already laid out. Applying spacer height directly.")
            applySpacerHeight()
        } else if (appBottomNavView != null) {
            Log.d("HomeFragmentInsets", "BNV not laid out or height is 0. Adding OnGlobalLayoutListener.")
            val viewTreeObserver = appBottomNavView.viewTreeObserver
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (appBottomNavView.height > 0) {
                        // Remove the listener to avoid multiple calls
                        if (appBottomNavView.viewTreeObserver.isAlive) {
                            appBottomNavView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        } else {
                            // Fallback for older APIs or if somehow not alive
                            try { viewTreeObserver.removeOnGlobalLayoutListener(this) } catch (e: Exception) {}
                        }
                        Log.d("HomeFragmentInsets", "BNV layout complete via OnGlobalLayoutListener. Height: ${appBottomNavView.height}. Applying spacer height.")
                        applySpacerHeight()
                    } else {
                        Log.d("HomeFragmentInsets", "OnGlobalLayoutListener: BNV height still 0.")
                    }
                }
            })
        } else {
            Log.d("HomeFragmentInsets", "BottomNavView not found at all. Only system/IME insets will be used.")
            // If BNV is null, still apply spacer height based on system/IME insets
            applySpacerHeight()
        }


        // Request insets to be applied initially to trigger the listener if needed
        if (viewReceivingInsets.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(viewReceivingInsets)
        } else {
            viewReceivingInsets.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
        Log.d("HomeFragmentInsets", "Finished setting up dynamic spacer height logic.")
    }

    private fun setupGoalProgressObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe Goal Title from homeViewModel
                launch {
                    homeViewModel.goalProgressTitle.collectLatest { title ->
                        binding.goalProgressTitle.text = title
                    }
                }

                // Observe Goal Items from homeViewModel
                launch {
                    homeViewModel.goalDisplayItems.collectLatest { items ->
                        val anyGoalActive = items.any { it.hasActiveGoal }
                        if (anyGoalActive) {
                            // Assuming goalProgressContainer is the ID of the LinearLayout
                            binding.goalProgressContainer.visibility = View.VISIBLE
                        } else {
                            binding.goalProgressContainer.visibility = View.GONE
                            return@collectLatest // No need to process items if container is hidden
                        }

                        // Update individual goal TextViews -
                        // These IDs should be directly available from the binding object
                        val visitsItem = items.find { it.id == "visits" }
                        binding.visitsGoalText.apply { // Corrected: binding.visitsGoalText
                            text = visitsItem?.displayText ?: ""
                            visibility = if (visitsItem?.hasActiveGoal == true) View.VISIBLE else View.GONE
                        }

                        val baptConfItem = items.find { it.id == "baptConf" }
                        binding.baptConfGoalText.apply { // Corrected: binding.baptConfGoalText
                            text = baptConfItem?.displayText ?: ""
                            visibility = if (baptConfItem?.hasActiveGoal == true) View.VISIBLE else View.GONE
                        }

                        val initiatoriesItem = items.find { it.id == "initiatories" }
                        binding.initiatoriesGoalText.apply { // Corrected: binding.initiatoriesGoalText
                            text = initiatoriesItem?.displayText ?: ""
                            visibility = if (initiatoriesItem?.hasActiveGoal == true) View.VISIBLE else View.GONE
                        }

                        val endowmentsItem = items.find { it.id == "endowments" }
                        binding.endowmentsGoalText.apply { // Corrected: binding.endowmentsGoalText
                            text = endowmentsItem?.displayText ?: ""
                            visibility = if (endowmentsItem?.hasActiveGoal == true) View.VISIBLE else View.GONE
                        }

                        val sealingsItem = items.find { it.id == "sealings" }
                        binding.sealingsGoalText.apply { // Corrected: binding.sealingsGoalText
                            text = sealingsItem?.displayText ?: ""
                            visibility = if (sealingsItem?.hasActiveGoal == true) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
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
