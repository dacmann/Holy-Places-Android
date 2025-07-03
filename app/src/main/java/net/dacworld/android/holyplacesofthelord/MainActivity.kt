package net.dacworld.android.holyplacesofthelord

import android.os.Bundle
import androidx.activity.viewModels // Ensure this import is present
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.data.DataViewModelFactory // Import your factory
import net.dacworld.android.holyplacesofthelord.databinding.ActivityMainBinding
import net.dacworld.android.holyplacesofthelord.ui.ViewPagerAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Provide the DataViewModelFactory to the viewModels delegate
    private val dataViewModel: DataViewModel by viewModels {
        // Access dependencies from your Application class
        val application = application as MyApplication // Make sure MyApplication is your Application class name
        DataViewModelFactory(application.templeDao, application.userPreferencesManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the ViewPager adapter
        // The ViewPagerAdapter will create fragments. If those fragments (like PlacesFragment)
        // also use `activityViewModels { factory }`, they will share the SAME
        // ViewModel instance created here in the Activity, which is good.
        val viewPagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = viewPagerAdapter

        // Connect the TabLayout with the ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            // Configure your tabs
            when (position) {
                0 -> {
                    tab.text = "Home"
                    tab.setIcon(R.drawable.morningstar)
                }
                1 -> {
                    tab.text = "Places"
                    tab.setIcon(R.drawable.starofmelchizedek)
                }
                // Add more cases for other tabs if your ViewPagerAdapter has more items
                else -> {
                    tab.text = "Tab ${position + 1}"
                }
            }
        }.attach()

        // Now this call should be safe as dataViewModel is properly initialized
        dataViewModel.checkForUpdates()
    }
}