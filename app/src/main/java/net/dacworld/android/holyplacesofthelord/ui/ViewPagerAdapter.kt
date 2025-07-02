package net.dacworld.android.holyplacesofthelord.ui // Or your preferred package

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import net.dacworld.android.holyplacesofthelord.ui.home.HomeFragment
import net.dacworld.android.holyplacesofthelord.ui.places.PlacesFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2 // You have two tabs: Home and Places

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> PlacesFragment()
            // Add more cases here if you create more tabs (Visits, Summary, Map)
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}