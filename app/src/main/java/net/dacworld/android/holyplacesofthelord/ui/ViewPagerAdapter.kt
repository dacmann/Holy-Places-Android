package net.dacworld.android.holyplacesofthelord.ui // Or your preferred package

import androidx.fragment.app.Fragment // <<< Ensure this import is used
// import androidx.fragment.app.FragmentActivity // <<< This import might no longer be needed if not used elsewhere
import androidx.viewpager2.adapter.FragmentStateAdapter
import net.dacworld.android.holyplacesofthelord.ui.home.HomeFragment
import net.dacworld.android.holyplacesofthelord.ui.places.PlacesFragment
// Import your other future tab fragments here once created
// import net.dacworld.android.holyplacesofthelord.ui.visits.VisitsFragment
// import net.dacworld.android.holyplacesofthelord.ui.summary.SummaryFragment
// import net.dacworld.android.holyplacesofthelord.ui.map.MapTabFragment

class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) { // <<< CHANGED to accept Fragment

    // If you plan for 5 tabs eventually:
    // override fun getItemCount(): Int = 5
    // For now, let's keep it at 2 until you create the other fragments.
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> PlacesFragment()
            // 2 -> VisitsFragment() // Uncomment when VisitsFragment exists
            // 3 -> SummaryFragment() // Uncomment when SummaryFragment exists
            // 4 -> MapTabFragment() // Uncomment when MapTabFragment exists
            else -> throw IllegalArgumentException("Invalid position for view pager: $position")
        }
    }
}
