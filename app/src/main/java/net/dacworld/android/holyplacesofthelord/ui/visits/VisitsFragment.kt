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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder // For confirmation dialog
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
import net.dacworld.android.holyplacesofthelord.data.VisitDisplayListItem
import net.dacworld.android.holyplacesofthelord.util.ColorUtils
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.fragment.app.activityViewModels
import net.dacworld.android.holyplacesofthelord.model.PlaceFilter

class VisitsFragment : Fragment() {

    private var _binding: FragmentVisitsBinding? = null
    private val binding get() = _binding!!

    // Using by viewModels() KTX extension
    private val visitViewModel: VisitViewModel by viewModels()
    private val sharedVisitsViewModel: SharedVisitsViewModel by activityViewModels()

    private lateinit var visitListAdapter: VisitListAdapter

    private var stableRestingBottomPaddingRecyclerView: Int? = null
    private var isInitialInsetApplicationRecyclerView = true

    private var previousSortOrder: net.dacworld.android.holyplacesofthelord.ui.VisitSortOrder? = null
    private var previousPlaceTypeFilter: net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter? = null
    private var isVisitsInitialLoad = true // To differentiate from subsequent updates
    private var shouldScrollAfterNextSubmit: Boolean = false // More direct flag for scrolling

    private var previousSearchQuery: String? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVisitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Log the state of isVisitsInitialLoad as it enters onViewCreated
        Log.d("VisitsFragment", "onViewCreated - Entry: isVisitsInitialLoad = $isVisitsInitialLoad, savedInstanceState is ${if (savedInstanceState == null) "null" else "NOT null"}")

        // shouldScrollAfterNextSubmit should always be reset here, as it's for explicit user actions
        // that should only apply to the next list submission.
        shouldScrollAfterNextSubmit = false

        // Initialize previous values from SharedViewModel. This is important so that
        // the observers don't falsely detect a change if LiveData re-emits its current value.
        previousSortOrder = sharedVisitsViewModel.sortOrder.value
        previousPlaceTypeFilter = sharedVisitsViewModel.selectedPlaceTypeFilter.value
        // previousSearchQuery = sharedVisitsViewModel.searchQuery.value // if you use it

        setupToolbar()
        setupMenu()
        setupRecyclerView()
        setupSwipeToDelete()
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

    // --- NEW: Method to set up swipe-to-delete ---
    private fun setupSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0, // No drag & drop
            ItemTouchHelper.LEFT // Swipe left to delete
        ) {
            private val deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete) // Your delete icon
            private val intrinsicWidth = deleteIcon?.intrinsicWidth ?: 0
            private val intrinsicHeight = deleteIcon?.intrinsicHeight ?: 0
            private val background = ColorDrawable()
            private val backgroundColor = Color.parseColor("#f44336") // Red color for delete

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // Not used for swipe-only
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Use viewHolder.adapterPosition
                val position = viewHolder.adapterPosition // CHANGED FROM bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    return
                }
                val item = visitListAdapter.currentList.getOrNull(position)
                if (item is VisitDisplayListItem.VisitRowItem) {
                    val visitToDelete = item.visit
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.confirm_delete_title))
                        .setMessage(getString(R.string.confirm_delete_message, visitToDelete.holyPlaceName))
                        .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                            visitListAdapter.notifyItemChanged(position)
                            dialog.dismiss()
                        }
                        .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                            visitViewModel.deleteVisitById(visitToDelete.id)
                            Snackbar.make(binding.root, "${visitToDelete.holyPlaceName} deleted", Snackbar.LENGTH_SHORT)
                                .show()
                            dialog.dismiss()
                        }
                        .setOnCancelListener {
                            visitListAdapter.notifyItemChanged(position)
                        }
                        .show()
                } else {
                    if (position < visitListAdapter.itemCount) {
                        visitListAdapter.notifyItemChanged(position)
                    }
                }
            }
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, // Amount of horizontal displacement caused by user's action
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val itemHeight = itemView.bottom - itemView.top
                val isCancelled = dX == 0f && !isCurrentlyActive

                if (isCancelled) {
                    // Clear canvas if swipe is cancelled
                    clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    return
                }

                // Draw the red background
                background.color = backgroundColor
                background.setBounds(
                    itemView.right + dX.toInt(), // Start from where the item is swiped
                    itemView.top,
                    itemView.right, // End at the original right edge of the item
                    itemView.bottom
                )
                background.draw(c)

                // Calculate position of delete icon
                val deleteIconTop = itemView.top + (itemHeight - intrinsicHeight) / 2
                val deleteIconMargin = (itemHeight - intrinsicHeight) / 2
                val deleteIconLeft = itemView.right - deleteIconMargin - intrinsicWidth
                val deleteIconRight = itemView.right - deleteIconMargin
                val deleteIconBottom = deleteIconTop + intrinsicHeight

                // Draw the delete icon only if swiping left (dX < 0)
                if (dX < 0) { // Swiping left
                    deleteIcon?.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
                    deleteIcon?.draw(c)
                }
                // (If you wanted a different icon/background for right swipe (dX > 0), you'd add it here)

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.visitsRecyclerView)
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
                applyMenuTextAndColorsFromPlaceFilter(menu.findItem(R.id.action_filter_visits_submenu)?.subMenu)
            }

            // Map MenuItem IDs to the corresponding PlaceFilter enum constant
            // This determines which PlaceFilter.displayName and PlaceFilter.customColorRes to use
            private val menuItemToPlaceFilterMapForVisits: Map<Int, PlaceFilter> by lazy {
                mapOf(
                    // CORRECTED IDs for the filter items:
                    R.id.filter_type_all to PlaceFilter.HOLY_PLACES, // Or PlaceFilter.HOLY_PLACES based on desired text
                    R.id.filter_type_active_temples to PlaceFilter.ACTIVE_TEMPLES,
                    R.id.filter_type_historical_sites to PlaceFilter.HISTORICAL_SITES,
                    R.id.filter_type_under_construction to PlaceFilter.TEMPLES_UNDER_CONSTRUCTION,
                    R.id.filter_type_visitors_centers to PlaceFilter.VISITORS_CENTERS
                    // Announced Temples are not included in this menu as per your requirement
                )
            }

            private fun applyMenuTextAndColorsFromPlaceFilter(subMenu: Menu?) {
                if (subMenu == null) return
                for (i in 0 until subMenu.size()) {
                    val menuItem = subMenu.getItem(i)
                    val placeFilterForMenuItem = menuItemToPlaceFilterMapForVisits[menuItem.itemId]

                    if (placeFilterForMenuItem != null && placeFilterForMenuItem.customColorRes != null) {
                        try {
                            val colorInt = ContextCompat.getColor(requireContext(), placeFilterForMenuItem.customColorRes!!) // Non-null asserted due to check
                            val title = requireContext().getString(placeFilterForMenuItem.displayNameRes) // Use displayName from PlaceFilter
                            val spannableString = SpannableString(title)
                            spannableString.setSpan(
                                ForegroundColorSpan(colorInt),
                                0,
                                title.length,
                                SpannableString.SPAN_INCLUSIVE_INCLUSIVE
                            )
                            menuItem.title = spannableString // Set the styled title
                            Log.d("VisitsFragmentMenu", "Applied PlaceFilter text/color to menu item: '${menuItem.title}', Enum: ${placeFilterForMenuItem.name}")
                        } catch (e: Exception) {
                            Log.e("VisitsFragmentMenu", "Error applying PlaceFilter style to menu item (ID: ${menuItem.itemId}): ${e.message}", e)
                        }
                    } else if (placeFilterForMenuItem != null) {
                        // If color is null but PlaceFilter is mapped, still set the displayName
                        menuItem.title = requireContext().getString(placeFilterForMenuItem.displayNameRes)
                        Log.d("VisitsFragmentMenu", "Applied PlaceFilter text (no color) to menu item: '${menuItem.title}', Enum: ${placeFilterForMenuItem.name}")
                    }
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.action_sort_visits -> {
                        true // Return true if the event was handled
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

                    R.id.action_filter_visits -> { // This is the one that will remain
                        // Navigate to the fragment that will handle Export/Import
                        // The Directions class name should reflect the destination fragment ID from nav_graph.xml
                        findNavController().navigate(VisitsFragmentDirections.actionVisitsFragmentToExportImportFragment()) // Use the new action
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
                val currentQuery = sharedVisitsViewModel.searchQuery.value
                if (currentQuery != query) {
                    sharedVisitsViewModel.setSearchQuery(query)
                    // The searchQuery observer will handle setting shouldScrollAfterNextSubmit
                }
                binding.visitsSearchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val currentQuery = sharedVisitsViewModel.searchQuery.value
                // Only update and potentially trigger scroll if text actually changes
                // Be mindful of rapid updates; you might want debouncing here in a real app
                if (currentQuery != newText) {
                    sharedVisitsViewModel.setSearchQuery(newText)
                }
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
        visitViewModel.allVisits.observe(viewLifecycleOwner) { visitDisplayItemsList ->
            visitListAdapter.submitList(visitDisplayItemsList) {
                // --- ADD SCROLL TO TOP HERE ---
                if (visitDisplayItemsList.isNotEmpty() && (isVisitsInitialLoad || shouldScrollAfterNextSubmit)) {
                    binding.visitsRecyclerView.scrollToPosition(0)
                    Log.d("VisitsFragment", "Scrolled to top. Initial: $isVisitsInitialLoad, UserChange: $shouldScrollAfterNextSubmit")
                } else if (visitDisplayItemsList.isEmpty() && (isVisitsInitialLoad || shouldScrollAfterNextSubmit)) {
                    Log.d("VisitsFragment", "List is empty, not scrolling. Initial: $isVisitsInitialLoad, UserChange: $shouldScrollAfterNextSubmit")
                } else {
                    Log.d("VisitsFragment", "List updated, not scrolling. Initial: $isVisitsInitialLoad, UserChange: $shouldScrollAfterNextSubmit")
                }

                // CRITICAL: isVisitsInitialLoad is set to false only AFTER the first data is processed by this observer.
                // It will remain false for subsequent view creations of THIS fragment instance unless the fragment instance itself is recreated.
                if (isVisitsInitialLoad) {
                    isVisitsInitialLoad = false
                    Log.d("VisitsFragment", "isVisitsInitialLoad has been set to false.")
                    // Check backup reminder status after initial load
                    visitViewModel.checkBackupReminderStatus()
                }
                shouldScrollAfterNextSubmit = false // Reset this flag after any potential scroll
            }

            binding.visitsEmptyTextView.visibility =
                if (visitDisplayItemsList.isEmpty()) View.VISIBLE else View.GONE

            val actualVisitCount = visitDisplayItemsList.count { it is VisitDisplayListItem.VisitRowItem }

            val currentFilterFromSharedVM = sharedVisitsViewModel.selectedPlaceTypeFilter.value
            if (currentFilterFromSharedVM != null) {
                val filterName = getString(currentFilterFromSharedVM.displayNameResource)
                Log.d(
                    "VisitsFragment",
                    "[V_FRAG_LOG_2] allVisits observer: Updating title text part 1. Filter: ${currentFilterFromSharedVM.name}, TypeCode for color check: ${currentFilterFromSharedVM.typeCode}, Count: $actualVisitCount"
                )
                // Use actualVisitCount for the title string
                binding.visitsToolbarTitleCentered.text = getString(R.string.title_visits_count, actualVisitCount)

                // Also set the color here, as this observer might be the last one to touch the title
                val titleColor = ColorUtils.getTextColorForTempleType(requireContext(), currentFilterFromSharedVM.typeCode)
                binding.visitsToolbarTitleCentered.setTextColor(titleColor)
            } else {
                Log.w("VisitsFragment", "[V_FRAG_LOG_3] allVisits observer: selectedPlaceTypeFilter.value is NULL. Using default count title.")
                binding.visitsToolbarTitleCentered.text = getString(R.string.title_visits_count, actualVisitCount) // Use actualVisitCount
                binding.visitsToolbarTitleCentered.setTextColor(MaterialColors.getColor(binding.visitsToolbarTitleCentered, com.google.android.material.R.attr.colorOnSurface))
            }

        } // End of visitViewModel.allVisits.observe

        // Backup reminder observer
        visitViewModel.shouldShowBackupReminder.observe(viewLifecycleOwner) { shouldShow ->
            if (shouldShow) {
                showBackupReminderDialog()
            }
        }

        sharedVisitsViewModel.sortOrder.observe(viewLifecycleOwner) { sortOrder ->
            sortOrder?.let { currentSortOrder ->
                // Update Toolbar Subtitle based on sort order
                Log.d("VisitsFragment", "[V_FRAG_LOG_5] sharedVisitsViewModel.sortOrder observer: $currentSortOrder")
                // If it's not the initial load AND the sort order actually changed from the previous one
                if (!isVisitsInitialLoad && previousSortOrder != currentSortOrder) {
                    Log.d("VisitsFragment", "Sort order *actually* changed by user/event. Flagging for scroll.")
                    shouldScrollAfterNextSubmit = true
                }
                previousSortOrder = currentSortOrder

                visitViewModel.updateSortOrder(currentSortOrder)
                val subtitle = when (currentSortOrder) {
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
            } // End of sharedVisitsViewModel.sortOrder.observe

            // --- NEW OBSERVER for Place Type Filter changes from SharedViewModel ---
            sharedVisitsViewModel.selectedPlaceTypeFilter.observe(viewLifecycleOwner) { placeTypeFilter ->
                placeTypeFilter?.let { currentFilter ->
                    Log.d("VisitsFragment", "[V_FRAG_LOG_6] selectedPlaceTypeFilter observer. Filter: ${currentFilter.name}, TypeCode: ${currentFilter.typeCode}")
                    Log.d("VisitsFragment", "[V_FRAG_LOG_7] selectedPlaceTypeFilter observer: Calling visitViewModel.updatePlaceTypeFilter with ${currentFilter.name}")

                    if (!isVisitsInitialLoad && previousPlaceTypeFilter != currentFilter) {
                        Log.d("VisitsFragment", "Filter *actually* changed by user/event. Flagging for scroll.")
                        shouldScrollAfterNextSubmit = true
                    }
                    previousPlaceTypeFilter = currentFilter

                    visitViewModel.updatePlaceTypeFilter(currentFilter)

                    var displayFilterName = getString(currentFilter.displayNameResource)
                    // Check if the current filter is UNDER_CONSTRUCTION
                    if (currentFilter == net.dacworld.android.holyplacesofthelord.ui.VisitPlaceTypeFilter.UNDER_CONSTRUCTION) {
                        displayFilterName = getString(R.string.filter_type_under_construction_short_title) // Use the short title
                    }

                    val currentActualVisitCount = visitListAdapter.currentList.count { it is VisitDisplayListItem.VisitRowItem }

                    Log.d("VisitsFragment", "[V_FRAG_LOG_8] selectedPlaceTypeFilter observer: Updating title text part 2 (name & color). FilterName: $displayFilterName")
                    binding.visitsToolbarTitleCentered.text = getString(R.string.toolbar_title_format, displayFilterName, currentActualVisitCount)

                    val context = requireContext()
                    Log.d("VisitsFragment", "[V_FRAG_LOG_9] selectedPlaceTypeFilter observer: Calling ColorUtils with TypeCode: ${currentFilter.typeCode}")
                    val titleColor = ColorUtils.getTextColorForTempleType(context, currentFilter.typeCode)
                    binding.visitsToolbarTitleCentered.setTextColor(titleColor)
                }
            }

            sharedVisitsViewModel.searchQuery.observe(viewLifecycleOwner) { newQuery ->
                // Normalize the incoming query (null to empty string) for consistent handling
                val queryToUse = newQuery ?: ""

                Log.d("VisitsFragment", "[V_FRAG_LOG_11] sharedVisitsViewModel.searchQuery observer. NewRaw: '$newQuery', Using: '$queryToUse', Previous: '$previousSearchQuery', InitialLoad: $isVisitsInitialLoad")

                // Only trigger scroll and viewmodel update if the processed query actually changes
                // and it's not part of the initial data load cycle.
                if (!isVisitsInitialLoad && previousSearchQuery != queryToUse) {
                    Log.d("VisitsFragment", "Search query *actually* changed by user/event ('$previousSearchQuery' -> '$queryToUse'). Flagging for scroll.")
                    shouldScrollAfterNextSubmit = true
                } else if (isVisitsInitialLoad) {
                    Log.d("VisitsFragment", "Search query observer: Initial load ($isVisitsInitialLoad), previous: '$previousSearchQuery', new: '$queryToUse'. Not flagging for scroll based on change.")
                } else if (previousSearchQuery == queryToUse) {
                    Log.d("VisitsFragment", "Search query observer: Query ('$queryToUse') hasn't changed from previous. Not flagging for scroll.")
                }

                previousSearchQuery = queryToUse // Update previousSearchQuery *after* comparison, using the normalized query

                // Call the new method in VisitViewModel
                visitViewModel.setSearchQuery(queryToUse) // Pass the normalized query
            }
        }
    }

    private fun showBackupReminderDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_reminder_title)
            .setMessage(R.string.backup_reminder_message)
            .setPositiveButton(R.string.backup_reminder_backup_now) { dialog, _ ->
                visitViewModel.onBackupReminderShown()
                // Navigate to Export/Import screen
                findNavController().navigate(R.id.action_visitsFragment_to_exportImportFragment)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.backup_reminder_remind_later) { dialog, _ ->
                visitViewModel.onBackupReminderDismissed()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.visitsRecyclerView.adapter = null // Clear adapter to prevent memory leaks
        _binding = null
    }

}
