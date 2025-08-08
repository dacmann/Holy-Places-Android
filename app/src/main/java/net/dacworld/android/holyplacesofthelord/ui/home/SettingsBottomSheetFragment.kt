package net.dacworld.android.holyplacesofthelord.ui.home // Or your preferred package

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.dacworld.android.holyplacesofthelord.R // Your R file

// If using ViewBinding
// import net.dacworld.android.holyplacesofthelord.databinding.LayoutSettingsBottomSheetBinding

class SettingsBottomSheetFragment : BottomSheetDialogFragment() {

    // If using ViewBinding
    // private var _binding: LayoutSettingsBottomSheetBinding? = null
    // private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // If using ViewBinding:
        // _binding = LayoutSettingsBottomSheetBinding.inflate(inflater, container, false)
        // return binding.root
        // If not using ViewBinding:
        return inflater.inflate(R.layout.layout_settings_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // If using ViewBinding:
        // val closeButton = binding.buttonCloseSettingsSheet
        // val exampleSwitch = binding.switchExampleSetting

        // If not using ViewBinding:
        val closeButton: ImageButton? = view.findViewById(R.id.button_close_settings_sheet)
        closeButton?.setOnClickListener {
            dismiss() // Dismisses the bottom sheet
        }

        // Add listeners or setup for your settings content here
        // For example:
        // val exampleSwitch: com.google.android.material.switchmaterial.SwitchMaterial? = view.findViewById(R.id.switch_example_setting)
        // exampleSwitch?.setOnCheckedChangeListener { _, isChecked ->
        //    // Handle setting change
        // }
    }

    // If using ViewBinding
    // override fun onDestroyView() {
    //     super.onDestroyView()
    //     _binding = null
    // }

    companion object {
        const val TAG = "SettingsBottomSheetFragment" // Tag for showing the dialog
    }
}
