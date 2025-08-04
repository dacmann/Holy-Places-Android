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
import net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder
import net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter

import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.data.VisitViewModel
import android.util.Log
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import net.dacworld.android.holyplacesofthelord.util.ColorUtils

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
                        true // Return true if the event was handled
                    }

                    R.id.action_filter_visits -> {
                        findNavController().navigate(VisitsFragmentDirections.actionVisitsFragmentToVisitOptionsFragment())
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

                    // --- NEW FILTER SUBMENU ITEM CLICKS ---
                    R.id.filter_type_all -> {
                        sharedVisitsViewModel.setPlaceTypeFilter(net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter.ALL)
                        true
                    }
                    R.id.filter_type_active_temples -> {
                        sharedVisitsViewModel.setPlaceTypeFilter(net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter.ACTIVE_TEMPLES)
                        true
                    }
                    R.id.filter_type_historical_sites -> {
                        sharedVisitsViewModel.setPlaceTypeFilter(net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter.HISTORICAL_SITES)
                        true
                    }
                    R.id.filter_type_visitors_centers -> {
                        sharedVisitsViewModel.setPlaceTypeFilter(net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter.VISITORS_CENTERS)
                        true
                    }
                    R.id.filter_type_under_construction -> {
                        sharedVisitsViewModel.setPlaceTypeFilter(net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter.UNDER_CONSTRUCTION)
                        true
                    }

                    R.id.action_filter_visits -> {
                        // Navigate to the fragment that will handle Export/Import
                        // This assumes VisitOptionsFragment is being repurposed and the action name is still valid.
                        // If you rename VisitOptionsFragment to ExportImportFragment and update the nav graph,
                        // this Directions class might change (e.g., VisitsFragmentDirections.actionVisitsFragmentToExportImportFragment())
                        findNavController().navigate(VisitsFragmentDirections.actionVisitsFragmentToVisitOptionsFragment())
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
        visitViewModel.allVisits.observe(viewLifecycleOwner) { visits ->
            visits?.let { currentVisitsList ->
                Log.d("VisitsFragment", "[V_FRAG_LOG_1] visitViewModel.allVisits observer. Count: ${currentVisitsList.size}")
                visitListAdapter.submitList(currentVisitsList)
                binding.visitsEmptyTextView.visibility =
                    if (currentVisitsList.isEmpty()) View.VISIBLE else View.GONE

                // Update title with count here
                val currentFilterFromSharedVM = sharedVisitsViewModel.selectedPlaceTypeFilter.value
                if (currentFilterFromSharedVM != null) {
                    val filterName = getString(currentFilterFromSharedVM.displayNameResource)
                    Log.d("VisitsFragment", "[V_FRAG_LOG_2] allVisits observer: Updating title text part 1. Filter: ${currentFilterFromSharedVM.name}, TypeCode for color check: ${currentFilterFromSharedVM.typeCode}, Count: ${currentVisitsList.size}")
                    binding.visitsToolbarTitleCentered.text = getString(R.string.title_visits_count, currentVisitsList.size)

                    // Also set the color here, as this observer might be the last one to touch the title
                    val titleColor = ColorUtils.getTextColorForTempleType(requireContext(), currentFilterFromSharedVM.typeCode)
                    binding.visitsToolbarTitleCentered.setTextColor(titleColor)
                } else {
                    Log.w("VisitsFragment", "[V_FRAG_LOG_3] allVisits observer: selectedPlaceTypeFilter.value is NULL. Using default count title.")
                    binding.visitsToolbarTitleCentered.text = getString(R.string.title_visits_count, currentVisitsList.size)
                    // Reset to default color if needed
                    binding.visitsToolbarTitleCentered.setTextColor(MaterialColors.getColor(binding.visitsToolbarTitleCentered, com.google.android.material.R.attr.colorOnSurface))
                }

            } ?: Log.d("VisitsFragment", "[V_FRAG_LOG_4] visitViewModel.allVisits observer: NULL list.")
        }

        sharedVisitsViewModel.sortOrder.observe(viewLifecycleOwner) { sortOrder ->
            sortOrder?.let { currentSortOrder ->
                // Update Toolbar Subtitle based on sort order
                Log.d("VisitsFragment", "[V_FRAG_LOG_5] sharedVisitsViewModel.sortOrder observer: $currentSortOrder")
                visitViewModel.updateSortOrder(currentSortOrder)
                val subtitle = when (sortOrder) {
                    net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_DATE_DESC -> getString(
                        R.string.sort_by_date_latest_first
                    )

                    net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_DATE_ASC -> getString(
                        R.string.sort_by_date_oldest_first
                    )

                    net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_PLACE_NAME_ASC -> getString(
                        R.string.sort_by_place_a_z
                    )

                    net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder.BY_PLACE_NAME_DESC -> getString(
                        R.string.sort_by_place_z_a
                    )
                }
                if (subtitle.isNotEmpty()) {
                    binding.visitsToolbarSubtitle.text = subtitle
                    binding.visitsToolbarSubtitle.visibility = View.VISIBLE
                } else {
                    binding.visitsToolbarSubtitle.visibility = View.GONE
                }
                //Snackbar.make(binding.root, "Sort order changed to: $sortOrder", Snackbar.LENGTH_SHORT).show()
            }

            // --- NEW OBSERVER for Place Type Filter changes from SharedViewModel ---
            sharedVisitsViewModel.selectedPlaceTypeFilter.observe(viewLifecycleOwner) { placeTypeFilter ->
                placeTypeFilter?.let { currentFilter ->
                    Log.d("VisitsFragment", "[V_FRAG_LOG_6] selectedPlaceTypeFilter observer. Filter: ${currentFilter.name}, TypeCode: ${currentFilter.typeCode}")

                    Log.d("VisitsFragment", "[V_FRAG_LOG_7] selectedPlaceTypeFilter observer: Calling visitViewModel.updatePlaceTypeFilter with ${currentFilter.name}")
                    visitViewModel.updatePlaceTypeFilter(currentFilter)

                    var displayFilterName = getString(currentFilter.displayNameResource)
                    // Check if the current filter is UNDER_CONSTRUCTION
                    if (currentFilter == net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter.UNDER_CONSTRUCTION) {
                        displayFilterName = getString(R.string.filter_type_under_construction_short_title) // Use the short title
                    }

                    // Attempt to get the latest count. It might be slightly stale if allVisits hasn't updated yet,
                    // but allVisits observer will correct it shortly.
                    val currentCount = visitViewModel.allVisits.value?.size ?: 0
                    Log.d("VisitsFragment", "[V_FRAG_LOG_8] selectedPlaceTypeFilter observer: Updating title text part 2 (name & color). FilterName: $displayFilterName")
                    binding.visitsToolbarTitleCentered.text = getString(R.string.toolbar_title_format, displayFilterName, currentCount)


                    val context = requireContext()
                    Log.d("VisitsFragment", "[V_FRAG_LOG_9] selectedPlaceTypeFilter observer: Calling ColorUtils with TypeCode: ${currentFilter.typeCode}")
                    val titleColor = ColorUtils.getTextColorForTempleType(context, currentFilter.typeCode)
                    binding.visitsToolbarTitleCentered.setTextColor(titleColor)
                }
            }

            sharedVisitsViewModel.searchQuery.observe(viewLifecycleOwner) { query ->
                Log.d("VisitsFragment", "[V_FRAG_LOG_11] sharedVisitsViewModel.searchQuery observer: '$query'")
                // ... search logic ...
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.visitsRecyclerView.adapter = null // Clear adapter to prevent memory leaks
        _binding = null
    }

}
