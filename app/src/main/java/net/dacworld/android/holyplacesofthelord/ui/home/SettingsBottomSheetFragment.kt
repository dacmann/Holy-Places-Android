package net.dacworld.android.holyplacesofthelord.ui.home

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.View.OnFocusChangeListener
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import coil.request.CachePolicy
import coil.size.Scale
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.dacworld.android.holyplacesofthelord.MyApplication
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.SettingsViewModel
import net.dacworld.android.holyplacesofthelord.data.SettingsViewModelFactory
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.databinding.LayoutSettingsBottomSheetBinding
import net.dacworld.android.holyplacesofthelord.model.ProfileContract
import net.dacworld.android.holyplacesofthelord.ui.profile.ProfileViewModel
import net.dacworld.android.holyplacesofthelord.ui.profile.ProfileViewModelFactory
import net.dacworld.android.holyplacesofthelord.util.ColorTheme
import net.dacworld.android.holyplacesofthelord.util.HomeImageOption
import net.dacworld.android.holyplacesofthelord.util.HomeTextColor

class SettingsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutSettingsBottomSheetBinding? = null
    private var isUpdatingTextProgrammatically = false
    // Colors and type symbols are resolved while views bind, so the host activity is
    // rebuilt once the sheet closes to pick up a new palette everywhere at once.
    private var appearanceChanged = false
    private val binding get() = _binding!!

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireContext().applicationContext)
    }

    private val profileViewModel: ProfileViewModel by viewModels {
        val app = requireActivity().application as MyApplication
        ProfileViewModelFactory(app.profileRepository)
    }

    private val homeImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { settingsViewModel.importHomeImage(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutSettingsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonCloseSettingsSheet.setOnClickListener {
            dismiss()
        }

        observeSettings()
        observeProfileSettings()
        setupListeners()
        setupProfileListeners()
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsViewModel.templeVisitsGoal.collect { goal ->
                        if (binding.valueTempleVisitsGoal.text.toString() != goal.toString()) {
                            binding.valueTempleVisitsGoal.setText(goal.toString())
                        }
                    }
                }
                launch {
                    settingsViewModel.baptismsGoal.collect { goal ->
                        if (binding.valueBaptismsGoal.text.toString() != goal.toString()) {
                            binding.valueBaptismsGoal.setText(goal.toString())
                        }
                    }
                }
                launch {
                    settingsViewModel.initiatoriesGoal.collect { goal ->
                        if (binding.valueInitiatoriesGoal.text.toString() != goal.toString()) {
                            binding.valueInitiatoriesGoal.setText(goal.toString())
                        }
                    }
                }
                launch {
                    settingsViewModel.endowmentsGoal.collect { goal ->
                        if (binding.valueEndowmentsGoal.text.toString() != goal.toString()) {
                            binding.valueEndowmentsGoal.setText(goal.toString())
                        }
                    }
                }
                launch {
                    settingsViewModel.sealingsGoal.collect { goal ->
                        if (binding.valueSealingsGoal.text.toString() != goal.toString()) {
                            binding.valueSealingsGoal.setText(goal.toString())
                        }
                    }
                }
                launch {
                    settingsViewModel.excludeVisitsNoOrdinances.collect { isEnabled ->
                        if (binding.switchExcludeVisitsNoOrdinances.isChecked != isEnabled) {
                            binding.switchExcludeVisitsNoOrdinances.isChecked = isEnabled
                        }
                    }
                }
                launch {
                    settingsViewModel.enableHoursWorked.collect { isEnabled ->
                        if (binding.switchEnableHoursWorked.isChecked != isEnabled) {
                            binding.switchEnableHoursWorked.isChecked = isEnabled
                        }
                    }
                }
                launch {
                    settingsViewModel.defaultCommentsText.collect { text ->
                        if (binding.editTextDefaultComments.text.toString() != text) {
                            isUpdatingTextProgrammatically = true
                            binding.editTextDefaultComments.setText(text)
                            // Small delay to ensure UI updates properly
                            kotlinx.coroutines.delay(50)
                            isUpdatingTextProgrammatically = false
                        }
                    }
                }
                launch {
                    settingsViewModel.colorTheme.collect { theme ->
                        val buttonId = when (theme) {
                            ColorTheme.RED_GREEN -> R.id.button_theme_red_green
                            ColorTheme.PURPLE_ORANGE -> R.id.button_theme_purple_orange
                            ColorTheme.MONO -> R.id.button_theme_mono
                        }
                        if (binding.colorThemeToggleGroup.checkedButtonId != buttonId) {
                            binding.colorThemeToggleGroup.check(buttonId)
                        }
                    }
                }
                launch {
                    settingsViewModel.showPlaceTypeSymbols.collect { isEnabled ->
                        if (binding.switchShowTypeSymbols.isChecked != isEnabled) {
                            binding.switchShowTypeSymbols.isChecked = isEnabled
                        }
                    }
                }
                launch {
                    settingsViewModel.showStockPlaceImageOnVisits.collect { isEnabled ->
                        if (binding.switchShowStockPlaceImage.isChecked != isEnabled) {
                            binding.switchShowStockPlaceImage.isChecked = isEnabled
                            binding.switchShowStockPlaceImage.jumpDrawablesToCurrentState()
                        }
                    }
                }
                launch {
                    settingsViewModel.homeImageOption.collect { option ->
                        val buttonId = homeImageButtonId(option)
                        if (binding.homeImageToggleGroup.checkedButtonId != buttonId) {
                            binding.homeImageToggleGroup.check(buttonId)
                        }
                    }
                }
                launch {
                    settingsViewModel.homeTextColor.collect { color ->
                        val buttonId = homeTextColorButtonId(color)
                        if (binding.homeTextColorToggleGroup.checkedButtonId != buttonId) {
                            binding.homeTextColorToggleGroup.check(buttonId)
                        }
                        val textColor = ContextCompat.getColor(
                            requireContext(),
                            if (color == HomeTextColor.WHITE) android.R.color.white else android.R.color.black
                        )
                        binding.buttonImportHomeImage.setTextColor(textColor)
                    }
                }
                launch {
                    settingsViewModel.alternateHomeImageRevision.collect { revision ->
                        val hasImage = settingsViewModel.hasAlternateHomeImage.value ||
                            settingsViewModel.alternateHomeImageFile()?.exists() == true
                        binding.buttonImportHomeImage.setText(
                            if (hasImage) R.string.home_image_change else R.string.home_image_import
                        )
                        val file = settingsViewModel.alternateHomeImageFile()
                        if (hasImage && file != null && file.exists()) {
                            binding.homeImagePreview.load(file) {
                                scale(Scale.FILL)
                                diskCacheKey("home_alternate_preview_$revision")
                                memoryCacheKey("home_alternate_preview_$revision")
                                diskCachePolicy(CachePolicy.DISABLED)
                            }
                        } else {
                            binding.homeImagePreview.setImageDrawable(null)
                        }
                    }
                }
                launch {
                    settingsViewModel.homeImageSelectionError.collect { messageRes ->
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.home_image_not_available)
                            .setMessage(messageRes)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            }
        }
    }
    private fun observeProfileSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    profileViewModel.profilesEnabled.collect { enabled ->
                        if (binding.switchEnableProfiles.isChecked != enabled) {
                            binding.switchEnableProfiles.isChecked = enabled
                        }
                        val visibility = if (enabled) View.VISIBLE else View.GONE
                        binding.manageProfilesRow.visibility = visibility
                        binding.addProfileRow.visibility = visibility
                    }
                }
            }
        }
    }

    private fun setupProfileListeners() {
        binding.switchEnableProfiles.setOnCheckedChangeListener { _, isChecked ->
            profileViewModel.toggleProfilesEnabled(isChecked)
            val visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.manageProfilesRow.visibility = visibility
            binding.addProfileRow.visibility = visibility
        }

        binding.manageProfilesRow.setOnClickListener {
            dismiss()
            requireParentFragment().findNavController()
                .navigate(R.id.action_home_to_profileManagement)
        }

        binding.addProfileRow.setOnClickListener {
            val currentCount = profileViewModel.profiles.value.size
            if (currentCount >= ProfileContract.MAX_PROFILES) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.profile_limit_title)
                    .setMessage(getString(R.string.profile_limit_message, ProfileContract.MAX_PROFILES))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } else {
                dismiss()
                requireParentFragment().findNavController()
                    .navigate(R.id.action_home_to_profileEditor)
            }
        }
    }

    private fun setupListeners() {
        // Helper function to set up focus listener for selecting text
        val selectAllOnFocusChange = OnFocusChangeListener { view, hasFocus ->
            val editText = view as? android.widget.EditText
            Log.d("FocusDebug", "onFocusChange: ${editText?.hint}, hasFocus: $hasFocus, currentText: '${editText?.text}'")
            if (hasFocus) {
                editText?.let { et ->
                    et.post {
                        et.text?.let { text ->
                            Log.d("FocusDebug", "Posted action for ${et.hint}: currentText: '$text'")
                            if (text.toString() == "0") {
                                Log.d("FocusDebug", "Selecting all for ${et.hint}")
                                et.selectAll()
                            } else {
                                Log.d("FocusDebug", "Not '0', moving cursor for ${et.hint}")
                                if (et.hasFocus()) {
                                    et.setSelection(text.length)
                                }
                            }
                        }
                    }
                }
            }
        }
        binding.valueTempleVisitsGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateTempleVisitsGoal(value)
        }
        binding.valueTempleVisitsGoal.onFocusChangeListener = selectAllOnFocusChange // Add this

        binding.valueBaptismsGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateBaptismsGoal(value)
        }
        binding.valueBaptismsGoal.onFocusChangeListener = selectAllOnFocusChange // Add this

        binding.valueInitiatoriesGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateInitiatoriesGoal(value)
        }
        binding.valueInitiatoriesGoal.onFocusChangeListener = selectAllOnFocusChange // Add this

        binding.valueEndowmentsGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateEndowmentsGoal(value)
        }
        binding.valueEndowmentsGoal.onFocusChangeListener = selectAllOnFocusChange // Add this

        binding.valueSealingsGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateSealingsGoal(value)
        }
        binding.valueSealingsGoal.onFocusChangeListener = selectAllOnFocusChange // Add this

        binding.switchExcludeVisitsNoOrdinances.setOnCheckedChangeListener { _, isChecked ->
            settingsViewModel.updateExcludeVisitsNoOrdinances(isChecked)
        }
        binding.switchEnableHoursWorked.setOnCheckedChangeListener { _, isChecked ->
            settingsViewModel.updateEnableHoursWorked(isChecked)
        }

        binding.editTextDefaultComments.doOnTextChanged { text, _, _, _ ->
            if (!isUpdatingTextProgrammatically) {
                val value = text.toString()
                settingsViewModel.updateDefaultCommentsText(value)
            }
        }

        binding.colorThemeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val theme = when (checkedId) {
                R.id.button_theme_red_green -> ColorTheme.RED_GREEN
                R.id.button_theme_purple_orange -> ColorTheme.PURPLE_ORANGE
                R.id.button_theme_mono -> ColorTheme.MONO
                else -> null
            }
            if (theme != null && theme != settingsViewModel.colorTheme.value) {
                settingsViewModel.updateColorTheme(theme)
                appearanceChanged = true
            }
        }

        binding.switchShowTypeSymbols.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != settingsViewModel.showPlaceTypeSymbols.value) {
                settingsViewModel.updateShowPlaceTypeSymbols(isChecked)
                appearanceChanged = true
            }
        }

        binding.switchShowStockPlaceImage.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != settingsViewModel.showStockPlaceImageOnVisits.value) {
                settingsViewModel.updateShowStockPlaceImageOnVisits(isChecked)
            }
        }

        binding.homeImageToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val option = when (checkedId) {
                R.id.button_home_image_default -> HomeImageOption.DEFAULT
                R.id.button_home_image_random -> HomeImageOption.RANDOM
                R.id.button_home_image_specific -> HomeImageOption.SPECIFIC
                else -> null
            }
            if (option != null) {
                settingsViewModel.selectHomeImageOption(option)
            }
        }

        binding.homeTextColorToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val color = when (checkedId) {
                R.id.button_home_text_white -> HomeTextColor.WHITE
                R.id.button_home_text_black -> HomeTextColor.BLACK
                else -> null
            }
            if (color != null && color != settingsViewModel.homeTextColor.value) {
                settingsViewModel.updateHomeTextColor(color)
            }
        }

        binding.buttonImportHomeImage.setOnClickListener {
            homeImagePickerLauncher.launch("image/*")
        }
        binding.homeImageImportContainer.setOnClickListener {
            homeImagePickerLauncher.launch("image/*")
        }
    }

    private fun homeImageButtonId(option: HomeImageOption): Int = when (option) {
        HomeImageOption.DEFAULT -> R.id.button_home_image_default
        HomeImageOption.RANDOM -> R.id.button_home_image_random
        HomeImageOption.SPECIFIC -> R.id.button_home_image_specific
    }

    private fun homeTextColorButtonId(color: HomeTextColor): Int = when (color) {
        HomeTextColor.WHITE -> R.id.button_home_text_white
        HomeTextColor.BLACK -> R.id.button_home_text_black
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val d = dialogInterface as BottomSheetDialog
            val bottomSheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED

                // Make it full screen
                val layoutParams = it.layoutParams
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                it.layoutParams = layoutParams

                // Skip collapsed state to prevent layout issues
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }


    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (appearanceChanged) {
            appearanceChanged = false
            activity?.recreate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsBottomSheetFragment"

        fun newInstance(): SettingsBottomSheetFragment {
            return SettingsBottomSheetFragment()
        }
    }
}
