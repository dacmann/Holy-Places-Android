package net.dacworld.android.holyplacesofthelord.ui.home // Or your preferred package

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.dacworld.android.holyplacesofthelord.R // Assuming your R file
import net.dacworld.android.holyplacesofthelord.databinding.LayoutSettingsBottomSheetBinding
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels // For viewModels delegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.data.SettingsViewModel
import net.dacworld.android.holyplacesofthelord.data.SettingsViewModelFactory
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager

class SettingsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutSettingsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val settingsViewModel: SettingsViewModel by viewModels {
        // Pass the application context to the factory
        SettingsViewModelFactory(requireContext().applicationContext)
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
        setupListeners()
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
            }
        }
    }
    private fun setupListeners() {
        binding.valueTempleVisitsGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateTempleVisitsGoal(value)
        }
        binding.valueBaptismsGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateBaptismsGoal(value)
        }
        binding.valueInitiatoriesGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateInitiatoriesGoal(value)
        }
        binding.valueEndowmentsGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateEndowmentsGoal(value)
        }
        binding.valueSealingsGoal.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toIntOrNull() ?: UserPreferencesManager.DEFAULT_GOAL_VALUE
            settingsViewModel.updateSealingsGoal(value)
        }

        binding.switchExcludeVisitsNoOrdinances.setOnCheckedChangeListener { _, isChecked ->
            settingsViewModel.updateExcludeVisitsNoOrdinances(isChecked)
        }
        binding.switchEnableHoursWorked.setOnCheckedChangeListener { _, isChecked ->
            settingsViewModel.updateEnableHoursWorked(isChecked)
        }
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

                // Optional: Adjust peek height if you want some initial visibility before full expansion
                // behavior.peekHeight = resources.displayMetrics.heightPixels / 2
                // Optional: Skip collapsed state if you only want expanded or hidden
                // behavior.skipCollapsed = true
            }
        }
        return dialog
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
