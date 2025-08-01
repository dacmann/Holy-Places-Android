// visit/VisitsFragment.kt
package net.dacworld.android.holyplacesofthelord.ui.visits

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentVisitsBinding
import net.dacworld.android.holyplacesofthelord.ui.SharedVisitsViewModel
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.data.VisitViewModel
import android.util.Log
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.bottomnavigation.BottomNavigationView

class VisitsFragment : Fragment() {

    private var _binding: FragmentVisitsBinding? = null
    private val binding get() = _binding!!

    // Using by viewModels() KTX extension
    private val visitViewModel: VisitViewModel by viewModels()
    private val sharedVisitsViewModel: SharedVisitsViewModel by viewModels() // Or activityViewModels() if shared with Activity

    private lateinit var visitListAdapter: VisitListAdapter

    private var stableRestingBottomPaddingRecyclerView: Int? = null
    private var isInitialInsetApplicationRecyclerView = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVisitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupMenu()
        setupRecyclerView()
        setupSearchViewListeners()
        setupFab()
        observeViewModel()

        // Apply bottom padding to the RecyclerView to account for the main_bottom_navigation and IME
        Log.d("VisitsFragmentInsets", "Setting up bottom inset handling for RecyclerView.")
        val viewToPad = binding.visitsRecyclerView // Target the RecyclerView

        ViewCompat.setOnApplyWindowInsetsListener(viewToPad) { v, windowInsets ->
            Log.d("VisitsFragmentInsets", "--- RecyclerView Bottom Inset Listener Triggered ---")

            val systemNavigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            Log.d("VisitsFragmentInsets", "RecyclerView - Reported SystemNav.bottom: ${systemNavigationBars.bottom}")
            Log.d("VisitsFragmentInsets", "RecyclerView - Reported IME.bottom: ${imeInsets.bottom}")

            var effectiveNavHeight = systemNavigationBars.bottom
            Log.d("VisitsFragmentInsets", "RecyclerView - Initial systemNavigationBars.bottom: $effectiveNavHeight")

            // Try to find the app's BottomNavigationView from the Activity
            val activityRootView = requireActivity().window.decorView
            // IMPORTANT: Ensure R.id.main_bottom_navigation is the correct ID
            // for your app's BottomNavigationView in the MainActivity layout.
            val appBottomNavView = activityRootView.findViewById<BottomNavigationView>(R.id.main_bottom_navigation)

            if (appBottomNavView != null && appBottomNavView.visibility == View.VISIBLE) {
                Log.d("VisitsFragmentInsets", "RecyclerView - App's BottomNavView found: Height=${appBottomNavView.height}, Visible=true")
                if (appBottomNavView.height > 0) {
                    effectiveNavHeight = appBottomNavView.height
                    Log.d("VisitsFragmentInsets", "RecyclerView - Using App's BottomNavView height ($effectiveNavHeight) as effectiveNavHeight")
                } else {
                    Log.d("VisitsFragmentInsets", "RecyclerView - AppBottomNavView height is 0. Current effectiveNavHeight (from system): $effectiveNavHeight")
                }
            } else {
                Log.d("VisitsFragmentInsets", "RecyclerView - App's BottomNavView not found or not visible.")
            }

            // Capture the stable resting bottom padding ONCE when IME is not visible
            // and on the first few applications to ensure we get a good value.
            if (imeInsets.bottom == 0 && (stableRestingBottomPaddingRecyclerView == null || isInitialInsetApplicationRecyclerView)) {
                stableRestingBottomPaddingRecyclerView = effectiveNavHeight
                // Only set isInitialInsetApplicationRecyclerView to false if we have a positive nav height,
                // otherwise, we might capture 0 if the nav bar isn't ready yet.
                if (effectiveNavHeight > 0) {
                    isInitialInsetApplicationRecyclerView = false
                }
                Log.d("VisitsFragmentInsets", "RecyclerView - CAPTURED/UPDATED Stable Resting Bottom Padding: $stableRestingBottomPaddingRecyclerView (isInitial: $isInitialInsetApplicationRecyclerView)")
            }

            val desiredBottomPadding: Int
            if (stableRestingBottomPaddingRecyclerView != null) {
                if (imeInsets.bottom > 0) {
                    // Keyboard is visible, use the greater of its height or our stable resting padding
                    desiredBottomPadding = kotlin.math.max(stableRestingBottomPaddingRecyclerView!!, imeInsets.bottom)
                    Log.d("VisitsFragmentInsets", "RecyclerView - Keyboard visible. DesiredPadding = max(stable: $stableRestingBottomPaddingRecyclerView, IME: ${imeInsets.bottom}) = $desiredBottomPadding")
                } else {
                    // Keyboard is not visible, revert to the stable resting padding
                    desiredBottomPadding = stableRestingBottomPaddingRecyclerView!!
                    Log.d("VisitsFragmentInsets", "RecyclerView - Keyboard NOT visible. Reverting to Stable Resting Padding: $desiredBottomPadding")
                }
            } else {
                // Fallback if stableRestingBottomPadding hasn't been captured yet (should be rare after first few layouts)
                desiredBottomPadding = kotlin.math.max(effectiveNavHeight, imeInsets.bottom)
                Log.d("VisitsFragmentInsets", "RecyclerView - Stable resting padding not yet captured. Using fallback. DesiredPadding = $desiredBottomPadding")
            }

            Log.d("VisitsFragmentInsets", "RecyclerView - Final Calculation: effectiveNavHeight=$effectiveNavHeight, imeInsets.bottom=${imeInsets.bottom} => desiredBottomPadding=$desiredBottomPadding")

            // Apply the calculated padding
            v.updatePadding(bottom = desiredBottomPadding)

            // Important for RecyclerView: allow items to scroll into the padded area.
            if (v is androidx.recyclerview.widget.RecyclerView) {
                v.clipToPadding = false
            }

            Log.d("VisitsFragmentInsets", "RecyclerView - View's actual paddingBottom after update: ${v.paddingBottom}")
            Log.d("VisitsFragmentInsets", "--- RecyclerView Bottom Inset Listener End ---")

            windowInsets // Return the insets, unmodified, for other listeners
        }

        // Request insets to be applied initially if the view is already attached
        if (viewToPad.isAttachedToWindow) {
            Log.d("VisitsFragmentInsets", "RecyclerView is already attached. Requesting apply insets.")
            ViewCompat.requestApplyInsets(viewToPad)
        } else {
            Log.d("VisitsFragmentInsets", "RecyclerView is not yet attached. Adding OnAttachStateChangeListener.")
            viewToPad.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    Log.d("VisitsFragmentInsets", "RecyclerView attached to window. Requesting apply insets from listener.")
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
        Log.d("VisitsFragmentInsets", "Finished setting up RecyclerView bottom inset handling.")

    }

    private fun setupToolbar() {
        val appCompatActivity = (activity as? AppCompatActivity)
        appCompatActivity?.setSupportActionBar(binding.visitsToolbar) // Use visitsToolbar

        // Clear default ActionBar title/subtitle as we are using custom TextViews
        appCompatActivity?.supportActionBar?.title = ""
        appCompatActivity?.supportActionBar?.subtitle = ""

        // Further setup for title/subtitle will be in observeViewModel or a dedicated update method
    }

    private fun setupMenu() {
        // The MenuHost
        val menuHost: MenuHost =
            requireActivity() // Or viewLifecycleOwner for fragment-specific menu

        // Add menu items without overriding the host's default menu
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.menu_visits_toolbar, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.action_sort_visits -> {
                        // This will be handled by the submenu, but you can add a top-level action if needed
                        // Snackbar.make(binding.root, "Sort options main item clicked", Snackbar.LENGTH_SHORT).show()
                        true // Return true if the event was handled
                    }

                    R.id.action_filter_visits -> {
                        // TODO: Navigate to Visit Options/Filter screen
                        // findNavController().navigate(VisitsFragmentDirections.actionVisitsFragmentToVisitOptionsFragment())
                        Snackbar.make(binding.root, "Filter clicked", Snackbar.LENGTH_SHORT).show()
                        true
                    }
                    // Handle sub-menu sort item clicks
                    R.id.sort_visits_by_date_desc -> {
                        sharedVisitsViewModel.setSortOrder(net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_DATE_DESC)
                        true
                    }

                    R.id.sort_visits_by_date_asc -> {
                        sharedVisitsViewModel.setSortOrder(net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_DATE_ASC)
                        true
                    }

                    R.id.sort_visits_by_place_asc -> {
                        sharedVisitsViewModel.setSortOrder(net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_PLACE_NAME_ASC)
                        true
                    }

                    R.id.sort_visits_by_place_desc -> {
                        sharedVisitsViewModel.setSortOrder(net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_PLACE_NAME_DESC)
                        true
                    }

                    else -> false // Let other components handle the event
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        visitListAdapter = VisitListAdapter { visit ->
            // Navigate to Visit Detail screen
            val action = VisitsFragmentDirections.actionVisitsFragmentToVisitDetailFragment(visit.id)
            findNavController().navigate(action)
        }
        binding.visitsRecyclerView.apply {
            adapter = visitListAdapter
            layoutManager = LinearLayoutManager(context)
            // Add DividerItemDecoration if needed, but be mindful of headers.
            // You might want a custom decoration that skips drawing dividers for header items.
            if (itemDecorationCount == 0) { // Add decoration only once
                addItemDecoration(DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL))
            }
        }
    }

    private fun setupSearchViewListeners() { // Was setupSearchView()
        // binding.visitsSearchView is now the one inside AppBarLayout
        binding.visitsSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                sharedVisitsViewModel.setSearchQuery(query)
                binding.visitsSearchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                sharedVisitsViewModel.setSearchQuery(newText)
                return true
            }
        })

        sharedVisitsViewModel.searchQuery.value?.let {
            binding.visitsSearchView.setQuery(it, false)
        }
    }


    private fun setupFab() {
        binding.addVisitFab.setOnClickListener {
            // Navigate to Add/Edit Visit screen
            // val action = VisitsFragmentDirections.actionVisitsFragmentToAddEditVisitFragment(null) // Pass null for new visit
            // findNavController().navigate(action)
            Snackbar.make(binding.root, "Add FAB clicked", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        visitViewModel.allVisits.observe(viewLifecycleOwner) { visits -> // Replace with your actual filtered list LiveData
            visits?.let {
                visitListAdapter.submitList(it)
                binding.visitsEmptyTextView.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE

                // Update Toolbar Title
                binding.visitsToolbarTitleCentered.text = getString(R.string.title_visits_count, it.size)
                // binding.visitsProgressBar.visibility = View.GONE
            }
        }

        sharedVisitsViewModel.sortOrder.observe(viewLifecycleOwner) { sortOrder ->
            // Update Toolbar Subtitle based on sort order
            val subtitle = when (sortOrder) {
                net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_DATE_DESC -> getString(R.string.sort_by_date_latest_first)
                net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_DATE_ASC -> getString(R.string.sort_by_date_oldest_first)
                net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_PLACE_NAME_ASC -> getString(R.string.sort_by_place_a_z)
                net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_PLACE_NAME_DESC -> getString(R.string.sort_by_place_z_a)
                else -> "" // Default or no subtitle
            }
            if (subtitle.isNotEmpty()) {
                binding.visitsToolbarSubtitle.text = subtitle
                binding.visitsToolbarSubtitle.visibility = View.VISIBLE
            } else {
                binding.visitsToolbarSubtitle.visibility = View.GONE
            }
            //Snackbar.make(binding.root, "Sort order changed to: $sortOrder", Snackbar.LENGTH_SHORT).show()
        }

        sharedVisitsViewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            // Update UI based on search query if needed (e.g., showing "Searching for..." in subtitle)
            // Snackbar.make(binding.root, "Search query: $query", Snackbar.LENGTH_SHORT).show()
        }

        // TODO: Observe loading state from VisitViewModel
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.visitsRecyclerView.adapter = null // Clear adapter to prevent memory leaks
        _binding = null
    }

    // TODO: Add onCreateOptionsMenu if you want to put search as a menu item
    /*
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_visits_toolbar, menu) // or a different menu if search is here
        val searchItem = menu.findItem(R.id.action_search_visits)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(...)
        super.onCreateOptionsMenu(menu, inflater)
    }
    */
}
