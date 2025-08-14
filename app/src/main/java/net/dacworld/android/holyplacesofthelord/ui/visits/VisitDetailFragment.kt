package net.dacworld.android.holyplacesofthelord.ui.visits

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentVisitDetailBinding
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.util.ColorUtils // Assuming ColorUtils is in this package
import net.dacworld.android.holyplacesofthelord.data.VisitDetailViewModel
import net.dacworld.android.holyplacesofthelord.data.VisitDetailViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class VisitDetailFragment : Fragment() {

    private var _binding: FragmentVisitDetailBinding? = null
    private val binding get() = _binding!!

    private val args: VisitDetailFragmentArgs by navArgs()

    private val viewModel: VisitDetailViewModel by viewModels {
        VisitDetailViewModelFactory(
            requireActivity().application,
            args.visitId
        )
    }

    // Date formatter
    private val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVisitDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCustomToolbar()
        setupMenuProvider()
        setupInsetHandling()

        //setupBottomInsetHandling()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.visit.collect { visit ->
                    visit?.let { populateUi(it) }
                }
            }
        }
    }

    private fun setupInsetHandling() {
        Log.d("VisitDetailInsets", "setupInsetHandling: Initializing.")

        // --- Top Inset Handling for AppBarLayout (EXACT REPLICATION OF RecordVisitFragment) ---
        Log.d("VisitDetailInsets", "setupInsetHandling: Setting up AppBarLayout listener.")
        ViewCompat.setOnApplyWindowInsetsListener(binding.visitDetailAppBarLayout) { appBarView, windowInsets ->
            Log.d("VisitDetailInsets", "setupInsetHandling: AppBarLayout listener CALLED.")
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            Log.d("VisitDetailInsets", "setupInsetHandling: AppBarLayout - Received top system inset: ${systemBarInsets.top}")

            appBarView.setPadding(
                appBarView.paddingLeft,
                systemBarInsets.top, // Apply top system inset for status bar
                appBarView.paddingRight,
                appBarView.paddingBottom
            )
            Log.d("VisitDetailInsets", "setupInsetHandling: AppBarLayout - Applied padding. View's paddingTop: ${appBarView.paddingTop}")

            // Return WindowInsetsCompat.CONSUMED, exactly like in RecordVisitFragment's AppBarLayout listener
            WindowInsetsCompat.CONSUMED
        }
        Log.d("VisitDetailInsets", "setupInsetHandling: AppBarLayout listener SET UP.")


        // --- Bottom Inset Handling for the main content area ---
        val viewToPadBottom = binding.visitDetailRootContainer
        Log.d("VisitDetailInsets", "setupInsetHandling: Setting up listener for ${viewToPadBottom.javaClass.simpleName} (ID: ${viewToPadBottom.id}) for bottom insets.")
        ViewCompat.setOnApplyWindowInsetsListener(viewToPadBottom) { v, windowInsets ->
            Log.d("VisitDetailInsets", "setupInsetHandling: ${viewToPadBottom.javaClass.simpleName} (ID: ${v.id}) listener CALLED.")

            val navBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            Log.d("VisitDetailInsets", "setupInsetHandling: ${viewToPadBottom.javaClass.simpleName} - NavBar bottom: ${navBarInsets.bottom}, IME bottom: ${imeInsets.bottom}")

            // Determine effective bottom padding: prefer IME if visible, else navigation bar.
            var desiredBottomPadding = if (imeInsets.bottom > 0) {
                Log.d("VisitDetailInsets", "setupInsetHandling: Using IME height (${imeInsets.bottom}) for bottom padding.")
                imeInsets.bottom
            } else {
                Log.d("VisitDetailInsets", "setupInsetHandling: Using Nav Bar height (${navBarInsets.bottom}) for bottom padding.")
                navBarInsets.bottom
            }

            // --- Logic to consider app's BottomNavigationView height (from your original setupBottomInsetHandling) ---
            val activityRootView = requireActivity().window.decorView
            val appBottomNavView = activityRootView.findViewById<BottomNavigationView>(R.id.main_bottom_navigation)

            if (appBottomNavView != null && appBottomNavView.visibility == View.VISIBLE) {
                Log.d("VisitDetailInsets", "setupInsetHandling: App's BottomNavView found and visible. Height: ${appBottomNavView.height}")
                if (desiredBottomPadding < appBottomNavView.height) {
                    desiredBottomPadding = appBottomNavView.height
                    Log.d("VisitDetailInsets", "setupInsetHandling: Overriding with App's BottomNavView height. New desiredBottomPadding: $desiredBottomPadding")
                }
            } else {
                Log.d("VisitDetailInsets", "setupInsetHandling: App's BottomNavView not found, not visible, or height check not met.")
            }
            // --- End of BottomNavigationView logic ---

            Log.d("VisitDetailInsets", "setupInsetHandling: ${viewToPadBottom.javaClass.simpleName} - Final desiredBottomPadding: $desiredBottomPadding")

            v.updatePadding(
                left = v.paddingLeft,
                top = 0,
                right = v.paddingRight,
                bottom = desiredBottomPadding
            )
            Log.d("VisitDetailInsets", "setupInsetHandling: ${viewToPadBottom.javaClass.simpleName} - Applied padding. View's paddingBottom: ${v.paddingBottom}")

            windowInsets
        }
        Log.d("VisitDetailInsets", "setupInsetHandling: Listener for ${viewToPadBottom.javaClass.simpleName} (ID: ${viewToPadBottom.id}) SET UP.")

        // Requesting initial insets if views might not be attached yet.
        if (binding.visitDetailAppBarLayout.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(binding.visitDetailAppBarLayout)
        } else {
            binding.visitDetailAppBarLayout.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { v.removeOnAttachStateChangeListener(this); ViewCompat.requestApplyInsets(v) }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }

        if (viewToPadBottom.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(viewToPadBottom)
        } else {
            viewToPadBottom.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { v.removeOnAttachStateChangeListener(this); ViewCompat.requestApplyInsets(v) }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }



    private fun setupCustomToolbar() {
        val toolbar: MaterialToolbar = binding.visitDetailToolbar

        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)

        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        actionBar?.apply {
            title = getString(R.string.visit_detail_title)
            setDisplayHomeAsUpEnabled(true) // This enables the back arrow icon
            setDisplayShowTitleEnabled(true)
        }

        // IMPORTANT: When using setDisplayHomeAsUpEnabled(true) with setSupportActionBar,
        // the click handling for the "home" button (back arrow) is typically handled
        // by the system routing it to onOptionsItemSelected(android.R.id.home) OR
        // by the NavController if your NavGraph is set up with an AppBarConfiguration
        // that includes this fragment's destination.
        // For directness and consistency with RecordVisitFragment's toolbar.setNavigationOnClickListener,
        // we can keep it, but it might be redundant if the MenuProvider handles android.R.id.home.
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupMenuProvider() {
        val menuHost: MenuHost = requireActivity() // Or use requireView() if toolbar is always part of fragment view

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_visit_detail, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    // android.R.id.home is NOT typically handled here if setDisplayHomeAsUpEnabled(true)
                    // is used and the NavController or toolbar.setNavigationOnClickListener handles it.
                    // If toolbar.setNavigationOnClickListener is removed, you MIGHT need to handle android.R.id.home here.
                    // For now, let's assume toolbar.setNavigationOnClickListener handles Up navigation.

                    R.id.action_edit_visit -> {
                        viewModel.visit.value?.let { currentVisit ->
                            val action = VisitDetailFragmentDirections
                                .actionVisitDetailFragmentToRecordVisitFragment(
                                    visitId = currentVisit.id,
                                    placeId = currentVisit.placeID,
                                    placeName = currentVisit.holyPlaceName ?: "",
                                    placeType = currentVisit.type ?: ""
                                )
                            findNavController().navigate(action)
                        }
                        true // Consume the event
                    }
                    else -> false // Let other components (like NavController for Up) handle it
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED) // Use viewLifecycleOwner and RESUMED state
    }

    private fun populateUi(visit: Visit) {
        binding.apply {
            // Place Name and Color
            detailVisitPlaceName.text = visit.holyPlaceName
            val placeNameColor = ColorUtils.getTextColorForTempleType(requireContext(), visit.type)
            detailVisitPlaceName.setTextColor(placeNameColor)

            // Visit Date
            detailVisitDate.text = visit.dateVisited?.let { dateFormatter.format(it) } ?: "Date not set"

            // Favorite Indicator
            if (visit.isFavorite) {
                detailVisitFavoriteIndicator.visibility = View.VISIBLE
                // Optionally, set a specific tint for the favorite star if it's different from the default
                // detailVisitFavoriteIndicator.setColorFilter(ContextCompat.getColor(requireContext(), R.color.your_favorite_color))
            } else {
                detailVisitFavoriteIndicator.visibility = View.GONE
            }

            // Ordinances
            populateOrdinances(visit)

            // Comments
            if (!visit.comments.isNullOrEmpty()) {
                detailVisitComments.visibility = View.VISIBLE
                detailVisitComments.text = visit.comments
            } else {
                detailVisitComments.visibility = View.GONE
            }

            // Picture
            if (visit.picture != null && visit.picture.isNotEmpty()) {
                detailVisitPicture.visibility = View.VISIBLE
                detailVisitPicture.load(visit.picture) {
                    crossfade(true)
                    //placeholder(R.drawable.ic_image_placeholder) // Optional: create a placeholder drawable
                    //error(R.drawable.ic_image_broken) // Optional: create a broken image drawable
                }
            } else {
                detailVisitPicture.visibility = View.GONE
            }
        }
    }

    private fun populateOrdinances(visit: Visit) {
        val builder = SpannableStringBuilder()
        var hasOrdinances = false
        var activeOrdinancesOnCurrentLine = 0

        fun appendOrdinance(label: String, value: Short?, colorResId: Int) {
            value?.let {
                if (it > 0) {
                    hasOrdinances = true
                    if (builder.isNotEmpty()) { // Only add spacer/newline if builder already has content
                        if (activeOrdinancesOnCurrentLine == 1) {
                            // We've already added one to this line, so this is the second. Add spacer.
                            builder.append("   ") // Adjust spacing string as needed
                        } else { // activeOrdinancesOnCurrentLine is 0 (or became 0 after a pair)
                            // This means we are starting a new line (it's not the very first ordinance overall)
                            builder.append("\n")
                        }
                    }
                    val start = builder.length
                    builder.append("$label: $it")
                    val end = builder.length
                    context?.let { ctx ->
                        builder.setSpan(
                            ForegroundColorSpan(ContextCompat.getColor(ctx, colorResId)),
                            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
//                        builder.setSpan(
//                            StyleSpan(Typeface.BOLD),
//                            start, start + label.length + 1, // Bold the "Label:" part
//                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
//                        )
                    }
                    // --- NEW: Update counter ---
                    activeOrdinancesOnCurrentLine++
                    if (activeOrdinancesOnCurrentLine == 2) {
                        activeOrdinancesOnCurrentLine = 0 // Reset after two items form a line
                    }
                }
            }
        }

        fun appendHours(label: String, value: Double?, colorResId: Int) {
            value?.let {
                if (it > 0.0) {
                    hasOrdinances = true
                    val start = builder.length
                    if (builder.isNotEmpty()) builder.append("\n")
                    builder.append(String.format(Locale.getDefault(), "%s: %.1f", label, it)) // Format to 1 decimal place
                    val end = builder.length
                    context?.let { ctx ->
                        builder.setSpan(
                            ForegroundColorSpan(ContextCompat.getColor(ctx, colorResId)),
                            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        builder.setSpan(
                            StyleSpan(Typeface.BOLD),
                            start, start + label.length + 1, // Bold the "Label:" part
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
        }

        // Order based on your desired layout
        appendOrdinance("Baptisms", visit.baptisms, R.color.BaptismBlue)
        appendOrdinance("Confirmations", visit.confirmations, R.color.Confirmations)
        appendOrdinance("Initiatories", visit.initiatories, R.color.Initiatories)
        appendOrdinance("Endowments", visit.endowments, R.color.Endowments)
        appendOrdinance("Sealings", visit.sealings, R.color.Sealings)
        appendHours("Hours Worked", visit.shiftHrs, R.color.alt_grey_text) // Use a suitable color for hours

        if (hasOrdinances) {
            binding.detailVisitOrdinancesPerformed.visibility = View.VISIBLE
            binding.detailVisitOrdinancesPerformed.text = builder
        } else {
            binding.detailVisitOrdinancesPerformed.visibility = View.GONE
        }
    }


    private fun setupBottomInsetHandling() {
        val contentViewToPad = binding.visitDetailRootContainer // ID of the root ConstraintLayout

        ViewCompat.setOnApplyWindowInsetsListener(contentViewToPad) { v, windowInsets ->
            // We are only interested in navigation bars, not IME for this screen
            val navBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            Log.d("VisitDetailInsets", "System navBarInsets.bottom: ${navBarInsets.bottom}")

            var effectiveBottomPadding = navBarInsets.bottom

            // Check if your app's BottomNavigationView is visible and potentially contributes to necessary padding
            // This logic is retained from your example, adjust if BottomNav is always hidden here.
            val activityRootView = requireActivity().window.decorView
            val appBottomNavView = activityRootView.findViewById<BottomNavigationView>(R.id.main_bottom_navigation)

            if (appBottomNavView != null && appBottomNavView.visibility == View.VISIBLE) {
                // If app's bottom nav is visible, its height might be what we need to clear
                // This is often relevant if the system nav bar is gesture-based (height 0)
                // but your app's nav bar is still present.
                // Or if your app's nav bar is taller than the system's.
                if (effectiveBottomPadding < appBottomNavView.height) {
                    effectiveBottomPadding = appBottomNavView.height
                    Log.d("VisitDetailInsets", "Using App's BottomNavView height ($effectiveBottomPadding) as padding")
                }
            } else {
                Log.d("VisitDetailInsets", "App's BottomNavView not found or not visible.")
            }

            Log.d("VisitDetailInsets", "Final desiredBottomPadding: $effectiveBottomPadding")

            v.updatePadding(bottom = effectiveBottomPadding)

            // Return the insets, potentially with system bars consumed if you fully handle them
            WindowInsetsCompat.CONSUMED
        }

        // Request insets to be applied initially
        if (contentViewToPad.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(contentViewToPad)
        } else {
            contentViewToPad.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
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
        (activity as? AppCompatActivity)?.setSupportActionBar(null)
        _binding = null
    }
}
