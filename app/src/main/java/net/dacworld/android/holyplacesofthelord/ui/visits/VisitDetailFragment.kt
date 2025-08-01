package net.dacworld.android.holyplacesofthelord.ui.visits

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.*
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

        setupToolbar()
        setupMenu()
        setupBottomInsetHandling() // Apply bottom inset handling

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.visit.collect { visit ->
                    visit?.let { populateUi(it) }
                }
            }
        }
    }

    private fun setupToolbar() {
        // Activity might handle the toolbar, or you might have a specific one in your layout
        // For now, let's assume the activity's toolbar is used and we just set the title.
        // If this fragment has its own toolbar, you'd initialize it here.
        requireActivity().title = getString(R.string.visit_detail_title) // From strings.xml
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_visit_detail, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_edit_visit -> {
                        viewModel.visit.value?.let { currentVisit ->
                            // Navigate to RecordVisitFragment with currentVisit.id and other details
                            val action = VisitDetailFragmentDirections
                                .actionVisitDetailFragmentToRecordVisitFragment(
                                    visitId = currentVisit.id,
                                    placeId = currentVisit.placeID, // Assuming placeID is correct FK
                                    placeName = currentVisit.holyPlaceName ?: "",
                                    placeType = currentVisit.type ?: "" // Pass the T, H, A, C, V type
                                )
                            findNavController().navigate(action)
                        }
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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
                detailVisitCommentsLabel.visibility = View.VISIBLE
                detailVisitComments.visibility = View.VISIBLE
                detailVisitComments.text = visit.comments
            } else {
                detailVisitCommentsLabel.visibility = View.GONE
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

        fun appendOrdinance(label: String, value: Short?, colorResId: Int) {
            value?.let {
                if (it > 0) {
                    hasOrdinances = true
                    val start = builder.length
                    if (builder.isNotEmpty()) builder.append("\n") // New line for subsequent ordinances
                    builder.append("$label: $it")
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
        _binding = null
    }
}
