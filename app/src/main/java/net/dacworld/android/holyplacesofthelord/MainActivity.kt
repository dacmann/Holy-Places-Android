package net.dacworld.android.holyplacesofthelord

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import net.dacworld.android.holyplacesofthelord.data.DataViewModel // Your ViewModel
import net.dacworld.android.holyplacesofthelord.databinding.ActivityMainBinding // View Binding
import net.dacworld.android.holyplacesofthelord.ui.ViewPagerAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val dataViewModel: DataViewModel by viewModels() // Initialize your ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the ViewPager adapter
        val viewPagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = viewPagerAdapter

        // Connect the TabLayout with the ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Home"
                1 -> "Places"
                // Add more cases for other tabs
                else -> null
            }
        }.attach()

        // You might want to trigger an initial data load or update check here
        // if your ViewModel doesn't do it automatically in its init block.
        dataViewModel.checkForUpdates()
    }
}