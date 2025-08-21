// summary/SummaryFragment.kt
package net.dacworld.android.holyplacesofthelord.ui.summary

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.bottomnavigation.BottomNavigationView
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.HolyPlaceStat
import net.dacworld.android.holyplacesofthelord.data.MostVisitedPlaceItem
import net.dacworld.android.holyplacesofthelord.databinding.FragmentSummaryBinding
import net.dacworld.android.holyplacesofthelord.data.SummaryViewModel
import net.dacworld.android.holyplacesofthelord.data.TempleVisitYearStats

class SummaryFragment : Fragment() {

    private var _binding: FragmentSummaryBinding? = null
    private val binding get() = _binding!!

    private val summaryViewModel: SummaryViewModel by viewModels()

    private var clickableYearColor: Int = 0
    private var defaultYearHeaderColor: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Call the method in your ViewModel to reload/refresh the summary data
        summaryViewModel.loadSummaryData() // Or whatever your data loading method is named
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Fragment_Lifecycle", "SummaryFragment onViewCreated ENTERED") // Unique, simple tag
        super.onViewCreated(view, savedInstanceState)

        clickableYearColor = ContextCompat.getColor(requireContext(), R.color.brand_primary)
        defaultYearHeaderColor = ContextCompat.getColor(requireContext(), R.color.brand_primary)

        setupInsetHandling()

        // Quote Module
        summaryViewModel.quote.observe(viewLifecycleOwner) { quoteText ->
            binding.textViewQuote.text = quoteText
        }
        binding.layoutQuoteModule.setOnClickListener {
            summaryViewModel.selectRandomQuote() // Change quote on click
        }

        // Holy Places Section
        summaryViewModel.holyPlacesStats.observe(viewLifecycleOwner) { stats ->
            populateHolyPlacesTable(stats)
        }

        // Temple Visits Section - Labels
        summaryViewModel.rightYearHeaderUiLabel.observe(viewLifecycleOwner) { yearLabelText ->
            Log.d("SummaryFragment_Observe", "rightYearHeaderUiLabel observed: '$yearLabelText'")
            binding.textViewTVHeaderYearPrevious.text = yearLabelText
            val isClickable = yearLabelText.endsWith(">")
            binding.textViewTVHeaderYearPrevious.isClickable = isClickable
            binding.textViewTVHeaderYearPrevious.setTextColor(if (isClickable) clickableYearColor else defaultYearHeaderColor)
        }

        summaryViewModel.leftYearHeaderUiLabel.observe(viewLifecycleOwner) { yearLabelText ->
            Log.d("SummaryFragment_Observe", "leftYearHeaderUiLabel observed: '$yearLabelText'")
            binding.textViewTVHeaderYearCurrent.text = yearLabelText
            val isClickable = yearLabelText.startsWith("<")
            binding.textViewTVHeaderYearCurrent.isClickable = isClickable
            binding.textViewTVHeaderYearCurrent.setTextColor(if (isClickable) clickableYearColor else defaultYearHeaderColor)
        }
        // Set click listeners for year navigation
        binding.textViewTVHeaderYearPrevious.setOnClickListener {
            Log.d("SummaryFragment_Click", "textViewTVHeaderYearPrevious CLICKED. IsClickable=${binding.textViewTVHeaderYearPrevious.isClickable}")
            if (binding.textViewTVHeaderYearPrevious.isClickable) {
                Log.d("SummaryFragment", "Right Year (Previous) Clicked")
                summaryViewModel.onNavigateRightClicked() // New method in ViewModel
            }
        }

        binding.textViewTVHeaderYearCurrent.setOnClickListener {
            if (binding.textViewTVHeaderYearCurrent.isClickable) {
                Log.d("SummaryFragment", "Left Year (Current) Clicked")
                summaryViewModel.onNavigateLeftClicked() // New method in ViewModel
            }
        }
        summaryViewModel.templeVisitPreviousYearStats.observe(viewLifecycleOwner) { stats ->
            Log.d("SummaryFragment", "Observed stats for RIGHT column: Year ${stats.year}")
            populateTempleVisitsRow(stats, "previous") // "previous" now refers to the right column's data
        }
        summaryViewModel.templeVisitCurrentYearStats.observe(viewLifecycleOwner) { stats ->
            Log.d("SummaryFragment", "Observed stats for LEFT column: Year ${stats.year}")
            populateTempleVisitsRow(stats, "current")  // "current" now refers to the left column's data
        }
        // ---- START: Set Ordinance Name and All Total Text Colors ----

        // Unique Temples Row
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewUniqueName).setTextColor(ContextCompat.getColor(requireContext(), R.color.t2_temples))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewUniqueCurrent).setTextColor(ContextCompat.getColor(requireContext(), R.color.t2_temples))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewUniquePrevious).setTextColor(ContextCompat.getColor(requireContext(), R.color.t2_temples))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewUniqueTotal).setTextColor(ContextCompat.getColor(requireContext(), R.color.t2_temples))

        // Sealings Row
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewSealingsName).setTextColor(ContextCompat.getColor(requireContext(), R.color.Sealings))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewSealingsCurrent).setTextColor(ContextCompat.getColor(requireContext(), R.color.Sealings))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewSealingsPrevious).setTextColor(ContextCompat.getColor(requireContext(), R.color.Sealings))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewSealingsTotal).setTextColor(ContextCompat.getColor(requireContext(), R.color.Sealings))

        // Endowments Row
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewEndowmentsName).setTextColor(ContextCompat.getColor(requireContext(), R.color.Endowments))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewEndowmentsCurrent).setTextColor(ContextCompat.getColor(requireContext(), R.color.Endowments))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewEndowmentsPrevious).setTextColor(ContextCompat.getColor(requireContext(), R.color.Endowments))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewEndowmentsTotal).setTextColor(ContextCompat.getColor(requireContext(), R.color.Endowments))

        // Initiatories Row
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewInitiatoriesName).setTextColor(ContextCompat.getColor(requireContext(), R.color.Initiatories))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewInitiatoriesCurrent).setTextColor(ContextCompat.getColor(requireContext(), R.color.Initiatories))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewInitiatoriesPrevious).setTextColor(ContextCompat.getColor(requireContext(), R.color.Initiatories))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewInitiatoriesTotal).setTextColor(ContextCompat.getColor(requireContext(), R.color.Initiatories))

        // Confirmations Row
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewConfirmationsName).setTextColor(ContextCompat.getColor(requireContext(), R.color.Confirmations))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewConfirmationsCurrent).setTextColor(ContextCompat.getColor(requireContext(), R.color.Confirmations))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewConfirmationsPrevious).setTextColor(ContextCompat.getColor(requireContext(), R.color.Confirmations))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewConfirmationsTotal).setTextColor(ContextCompat.getColor(requireContext(), R.color.Confirmations))

        // Baptisms Row
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewBaptismsName).setTextColor(ContextCompat.getColor(requireContext(), R.color.BaptismBlue))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewBaptismsCurrent).setTextColor(ContextCompat.getColor(requireContext(), R.color.BaptismBlue))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewBaptismsPrevious).setTextColor(ContextCompat.getColor(requireContext(), R.color.BaptismBlue))
        binding.tableLayoutTempleVisits.findViewById<TextView>(R.id.textViewBaptismsTotal).setTextColor(ContextCompat.getColor(requireContext(), R.color.BaptismBlue))

        // ---- END: Set Ordinance Name and All Total Text Colors ----

        // Temple Visits Section - Data
        summaryViewModel.templeVisitCurrentYearStats.observe(viewLifecycleOwner) { stats ->
            populateTempleVisitsRow(stats, "current")
        }
        summaryViewModel.templeVisitPreviousYearStats.observe(viewLifecycleOwner) { stats ->
            populateTempleVisitsRow(stats, "previous")
        }
        summaryViewModel.templeVisitTotalStats.observe(viewLifecycleOwner) { stats ->
            populateTempleVisitsRow(stats, "total")
        }

        // Most Visited Section
        summaryViewModel.mostVisitedPlaces.observe(viewLifecycleOwner) { places ->
            populateMostVisitedList(places)
        }

        // Initial data load is triggered in ViewModel's init block
        // If you want a refresh mechanism, you might add a swipe-to-refresh
        // or a button that calls summaryViewModel.loadSummaryData()
    }

    private fun setupInsetHandling() {
        // The root view of your fragment_summary.xml is likely the NestedScrollView
        // If your binding.root is the NestedScrollView itself, this is fine.
        // Otherwise, you might need to target binding.yourNestedScrollViewId
        val viewToPad = binding.root // Assuming binding.root IS the NestedScrollView

        // Apply padding for Status Bar (top) and Navigation Bar/IME (bottom)
        ViewCompat.setOnApplyWindowInsetsListener(viewToPad) { v, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            // Determine effective bottom padding: prefer IME if visible, else navigation bar.
            var desiredBottomPadding = if (imeInsets.bottom > 0) {
                imeInsets.bottom
            } else {
                systemBars.bottom // For navigation bar
            }

            // --- Consider your app's BottomNavigationView height ---
            val activityRootView = requireActivity().window.decorView

            val appBottomNavView = activityRootView.findViewById<BottomNavigationView>(R.id.main_bottom_navigation)

            if (appBottomNavView != null && appBottomNavView.visibility == View.VISIBLE) {
                if (desiredBottomPadding < appBottomNavView.height) {
                    desiredBottomPadding = appBottomNavView.height
                }
            }
            // --- End of BottomNavigationView logic ---

            v.updatePadding(
                top = systemBars.top, // Padding for the status bar
                bottom = desiredBottomPadding // Padding for navigation bar/IME and your app's BottomNav
                // left and right padding are usually handled by the layout's own attributes
            )

            // It's good practice to consume only the insets you've used,
            // but for simplicity and if this is the main scrolling content,
            // returning the original insets (or CONSUMED) is often acceptable.
            // For precise control, you'd create new insets excluding what you've applied.
            WindowInsetsCompat.CONSUMED // Or return windowInsets if other views need to react
        }

        // Request initial insets apply if the view is already attached
        if (viewToPad.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(viewToPad)
        } else {
            viewToPad.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }
    private fun populateHolyPlacesTable(stats: List<HolyPlaceStat>) {
        binding.tableLayoutHolyPlaces.removeViews(1, binding.tableLayoutHolyPlaces.childCount - 1) // Clear old rows, keep header

        val context = requireContext()
        stats.forEach { stat ->
            val row = LayoutInflater.from(context).inflate(R.layout.summary_table_row_holy_place, binding.tableLayoutHolyPlaces, false) as ViewGroup

            val typeTextView = row.findViewById<TextView>(R.id.textViewHolyPlaceType)
            val visitedTextView = row.findViewById<TextView>(R.id.textViewHolyPlaceVisited)
            val totalTextView = row.findViewById<TextView>(R.id.textViewHolyPlaceTotal)

            typeTextView.text = stat.typeName
            visitedTextView.text = stat.visitedCount.toString()
            totalTextView.text = stat.totalCount.toString()

            try {
                val textColor = ContextCompat.getColor(context, stat.colorRes)
                typeTextView.setTextColor(textColor)
                visitedTextView.setTextColor(textColor)
                totalTextView.setTextColor(textColor)
            } catch (e: Exception) {
            }

            binding.tableLayoutHolyPlaces.addView(row)
        }
    }

    private fun populateTempleVisitsRow(stats: TempleVisitYearStats, yearType: String) {

        val attendedTextView: TextView?
        val uniqueTextView: TextView?
        val hoursTextView: TextView?
        val sealingsTextView: TextView?
        val endowmentsTextView: TextView?
        val initiatoriesTextView: TextView?
        val confirmationsTextView: TextView?
        val baptismsTextView: TextView?
        val ordinancesTotalTextView: TextView?

        when (yearType) {
            "current" -> {
                attendedTextView = binding.textViewAttendedCurrent
                uniqueTextView = binding.textViewUniqueCurrent
                hoursTextView = binding.textViewHoursCurrent
                sealingsTextView = binding.textViewSealingsCurrent
                endowmentsTextView = binding.textViewEndowmentsCurrent
                initiatoriesTextView = binding.textViewInitiatoriesCurrent
                confirmationsTextView = binding.textViewConfirmationsCurrent
                baptismsTextView = binding.textViewBaptismsCurrent
                ordinancesTotalTextView = binding.textViewTVOrdinancesCurrent
            }
            "previous" -> {
                attendedTextView = binding.textViewAttendedPrevious
                uniqueTextView = binding.textViewUniquePrevious
                hoursTextView = binding.textViewHoursPrevious
                sealingsTextView = binding.textViewSealingsPrevious
                endowmentsTextView = binding.textViewEndowmentsPrevious
                initiatoriesTextView = binding.textViewInitiatoriesPrevious
                confirmationsTextView = binding.textViewConfirmationsPrevious
                baptismsTextView = binding.textViewBaptismsPrevious
                ordinancesTotalTextView = binding.textViewTVOrdinancesPrevious
            }
            "total" -> {
                attendedTextView = binding.textViewAttendedTotal
                uniqueTextView = binding.textViewUniqueTotal
                hoursTextView = binding.textViewHoursTotal
                sealingsTextView = binding.textViewSealingsTotal
                endowmentsTextView = binding.textViewEndowmentsTotal
                initiatoriesTextView = binding.textViewInitiatoriesTotal
                confirmationsTextView = binding.textViewConfirmationsTotal
                baptismsTextView = binding.textViewBaptismsTotal
                ordinancesTotalTextView = binding.textViewTVOrdinancesTotal
            }
            else -> return // Should not happen
        }

        attendedTextView?.text = stats.attended.toString()
        uniqueTextView?.text = stats.uniqueTemples.toString()
        hoursTextView?.text = String.format("%.1f", stats.hoursWorked) // Format to 1 decimal place
        sealingsTextView?.text = stats.sealings.toString()
        endowmentsTextView?.text = stats.endowments.toString()
        initiatoriesTextView?.text = stats.initiatories.toString()
        confirmationsTextView?.text = stats.confirmations.toString()
        baptismsTextView?.text = stats.baptisms.toString()
        ordinancesTotalTextView?.text = stats.totalOrdinances.toString()

        // You might want to bold the "Total" row text if desired
        if (yearType == "total") {
            listOfNotNull(attendedTextView, uniqueTextView, hoursTextView, sealingsTextView, endowmentsTextView,
                initiatoriesTextView, confirmationsTextView, baptismsTextView, ordinancesTotalTextView)
                .forEach { it.setTypeface(null, Typeface.BOLD) }
        }
    }

    private fun populateMostVisitedList(places: List<MostVisitedPlaceItem>) {
        binding.linearLayoutMostVisited.removeAllViews() // Clear old items

        val context = requireContext()
        if (places.isEmpty()) {
            val noDataTextView = TextView(context).apply {
                text = getString(R.string.no_visits_yet) // Add to strings.xml: <string name="no_visits_yet">No visits recorded yet.</string>
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16,16,16,16) // Add some basic margin
                }
                // You might want to style this text further (size, color)
            }
            binding.linearLayoutMostVisited.addView(noDataTextView)
            return
        }

        places.forEach { place ->
            val placeTextView = TextView(context).apply {
                text = "${place.visitCount} - ${place.placeName}"
                setTextColor(ContextCompat.getColor(context, place.typeColorRes))
                textSize = 16f // Example size
                setPadding(8, 8, 8, 8) // Example padding
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            binding.linearLayoutMostVisited.addView(placeTextView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important to prevent memory leaks
    }
}

