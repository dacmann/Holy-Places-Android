package net.dacworld.android.holyplacesofthelord.ui.achievements

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.MyApplication
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentAchievementsBinding

class AchievementsFragment : Fragment() {

    private var _binding: FragmentAchievementsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AchievementViewModel by viewModels {
        val app = requireActivity().application as MyApplication
        AchievementViewModelFactory(app.achievementRepository)
    }

    private lateinit var adapter: AchievementAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAchievementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_done) {
                findNavController().navigateUp()
                true
            } else false
        }
        // Custom action view (Done button) needs its own click listener
        binding.toolbar.post {
            binding.toolbar.menu.findItem(R.id.action_done)?.actionView?.setOnClickListener {
                findNavController().navigateUp()
            }
        }

        adapter = AchievementAdapter()
        binding.achievementRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.achievementRecyclerView.adapter = adapter
        val dividerDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.achievement_row_divider)!!
        binding.achievementRecyclerView.addItemDecoration(AchievementDividerItemDecoration(dividerDrawable))

        binding.achievementTabs.addTab(
            binding.achievementTabs.newTab().setText(getString(R.string.achievements_completed))
        )
        binding.achievementTabs.addTab(
            binding.achievementTabs.newTab().setText(getString(R.string.achievements_not_completed))
        )

        binding.achievementTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.setTab(
                    if (tab?.position == 0) AchievementViewModel.AchievementTab.COMPLETED
                    else AchievementViewModel.AchievementTab.NOT_COMPLETED
                )
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.selectedTab,
                    viewModel.completedAchievements,
                    viewModel.incompleteAchievements
                ) { tab, completed, incomplete ->
                    Triple(tab, completed, incomplete)
                }.collectLatest { (tab, completed, incomplete) ->
                    val list = when (tab) {
                        AchievementViewModel.AchievementTab.COMPLETED -> completed
                        AchievementViewModel.AchievementTab.NOT_COMPLETED -> incomplete
                    }
                    adapter.submitList(list)
                    binding.emptyStateText.visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Set initial tab
        viewModel.setTab(AchievementViewModel.AchievementTab.COMPLETED)
        binding.achievementTabs.getTabAt(0)?.select()

        setupWindowInsets()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom,
                left = systemBars.left,
                right = systemBars.right
            )
            windowInsets
        }
        if (binding.root.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(binding.root)
        } else {
            binding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
