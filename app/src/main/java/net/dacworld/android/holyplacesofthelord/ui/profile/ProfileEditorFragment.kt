package net.dacworld.android.holyplacesofthelord.ui.profile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.MyApplication
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentProfileEditorBinding
import net.dacworld.android.holyplacesofthelord.model.Profile
import net.dacworld.android.holyplacesofthelord.model.ProfileContract

class ProfileEditorFragment : Fragment() {

    private var _binding: FragmentProfileEditorBinding? = null
    private val binding get() = _binding!!

    private val args: ProfileEditorFragmentArgs by navArgs()

    private val viewModel: ProfileViewModel by viewModels {
        val app = requireActivity().application as MyApplication
        ProfileViewModelFactory(app.profileRepository)
    }

    private lateinit var iconAdapter: ProfileIconAdapter
    private var existingProfile: Profile? = null

    private val isEditing: Boolean get() = args.profileId.isNotEmpty()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupIconGrid()
        prefillFields()
        setupSaveButton()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.profileEditorToolbar)
        binding.profileEditorToolbar.title = if (isEditing)
            getString(R.string.title_edit_profile)
        else
            getString(R.string.title_new_profile)

        binding.profileEditorToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupIconGrid() {
        iconAdapter = ProfileIconAdapter { /* selection handled inside adapter */ }
        val initialIcon = args.profileIconName.ifEmpty { ProfileContract.DEFAULT_ICON_NAME }
        iconAdapter.setSelectedIcon(initialIcon)
        binding.iconGridRecyclerView.apply {
            adapter = iconAdapter
            layoutManager = GridLayoutManager(context, 6)
        }
    }

    private fun prefillFields() {
        if (isEditing) {
            binding.profileNameEditText.setText(args.profileName)

            viewLifecycleOwner.lifecycleScope.launch {
                val app = requireActivity().application as MyApplication
                existingProfile = app.profileRepository.profiles.first()
                    .firstOrNull { it.profileId == args.profileId }
            }
        }
    }

    private fun setupSaveButton() {
        binding.profileNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.saveProfileButton.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.saveProfileButton.isEnabled = args.profileName.isNotBlank() || !isEditing.not()

        binding.saveProfileButton.setOnClickListener {
            val name = binding.profileNameEditText.text?.toString()?.trim() ?: ""
            if (name.isBlank()) {
                binding.profileNameInputLayout.error = getString(R.string.error_profile_name_required)
                return@setOnClickListener
            }
            binding.profileNameInputLayout.error = null
            val selectedIcon = iconAdapter.getSelectedIcon()

            viewLifecycleOwner.lifecycleScope.launch {
                if (isEditing) {
                    val current = existingProfile
                    if (current != null) {
                        viewModel.updateProfile(current.copy(name = name, iconName = selectedIcon))
                    }
                } else {
                    val created = (requireActivity().application as MyApplication)
                        .profileRepository.createProfile(name, selectedIcon)
                    if (created == null) {
                        Snackbar.make(binding.root, R.string.profile_limit_message_short, Snackbar.LENGTH_LONG).show()
                        return@launch
                    }
                }
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
