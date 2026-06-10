package net.dacworld.android.holyplacesofthelord.ui.profile

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.MyApplication
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentProfileManagementBinding
import net.dacworld.android.holyplacesofthelord.model.Profile
import net.dacworld.android.holyplacesofthelord.model.ProfileContract

class ProfileManagementFragment : Fragment() {

    private var _binding: FragmentProfileManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels {
        val app = requireActivity().application as MyApplication
        ProfileViewModelFactory(app.profileRepository)
    }

    private lateinit var adapter: ProfilesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupSwipeToDelete()
        setupFab()
        observeViewModel()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.profileMgmtToolbar)
        binding.profileMgmtToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProfilesAdapter { profile ->
            navigateToEditor(profile)
        }
        binding.profilesRecyclerView.apply {
            this.adapter = this@ProfileManagementFragment.adapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupFab() {
        binding.addProfileFab.setOnClickListener {
            val count = adapter.itemCount
            if (count >= ProfileContract.MAX_PROFILES) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.profile_limit_title)
                    .setMessage(getString(R.string.profile_limit_message, ProfileContract.MAX_PROFILES))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } else {
                navigateToEditor(null)
            }
        }
    }

    private fun navigateToEditor(profile: Profile?) {
        val action = ProfileManagementFragmentDirections
            .actionProfileManagementFragmentToProfileEditorFragment(
                profileId = profile?.profileId ?: "",
                profileName = profile?.name ?: "",
                profileIconName = profile?.iconName ?: ProfileContract.DEFAULT_ICON_NAME
            )
        findNavController().navigate(action)
    }

    private fun setupSwipeToDelete() {
        val deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
        val background = ColorDrawable()

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val item = adapter.currentList.getOrNull(vh.adapterPosition) ?: return 0
                return if (item.profile.isDefault) 0 else super.getSwipeDirs(rv, vh)
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.currentList.getOrNull(vh.adapterPosition) ?: return
                val profile = item.profile
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.delete_profile_title, profile.name))
                    .setMessage(
                        getString(R.string.delete_profile_message, profile.name, item.visitCount)
                    )
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        adapter.notifyItemChanged(vh.adapterPosition)
                    }
                    .setPositiveButton(R.string.delete) { _, _ ->
                        viewModel.deleteProfile(profile)
                    }
                    .setOnCancelListener { adapter.notifyItemChanged(vh.adapterPosition) }
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = vh.itemView
                background.color = Color.parseColor("#f44336")
                background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                background.draw(c)
                deleteIcon?.let { icon ->
                    val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                    val iconTop = itemView.top + iconMargin
                    val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                    icon.setBounds(iconLeft, iconTop, iconLeft + icon.intrinsicWidth, iconTop + icon.intrinsicHeight)
                    if (dX < 0) icon.draw(c)
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        })
        touchHelper.attachToRecyclerView(binding.profilesRecyclerView)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.profiles,
                    viewModel.activeProfileId
                ) { profiles, activeId -> Pair(profiles, activeId) }
                    .collectLatest { (profiles, activeId) ->
                        val items = profiles.map { profile ->
                            val visitCount = try {
                                (requireActivity().application as MyApplication)
                                    .profileRepository.run {
                                        // Use DAO directly for a one-shot count
                                        (requireActivity().application as MyApplication)
                                            .profileDao.visitCountForProfile(profile.profileId)
                                    }
                            } catch (e: Exception) { 0 }
                            ProfilesAdapter.ProfileItem(
                                profile = profile,
                                visitCount = visitCount,
                                isActive = profile.profileId == activeId
                            )
                        }
                        adapter.submitProfileItems(items)
                        binding.profilesEmptyText.visibility =
                            if (profiles.isEmpty()) View.VISIBLE else View.GONE
                        binding.addProfileFab.isEnabled = profiles.size < ProfileContract.MAX_PROFILES
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
