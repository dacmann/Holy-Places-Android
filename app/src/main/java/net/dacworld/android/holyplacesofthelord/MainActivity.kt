package net.dacworld.android.holyplacesofthelord

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.collectLatest // For collecting StateFlow
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.data.DataViewModelFactory
import net.dacworld.android.holyplacesofthelord.databinding.ActivityMainBinding
import net.dacworld.android.holyplacesofthelord.ui.SharedToolbarViewModel // ViewModel with StateFlow
import net.dacworld.android.holyplacesofthelord.ui.ViewPagerAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var centeredToolbarTitle: TextView
    private lateinit var persistentSearchView: SearchView
    private val dataViewModel: DataViewModel by viewModels {
        val application = application as MyApplication
        DataViewModelFactory(application, application.templeDao, application.userPreferencesManager)
    }

    private val sharedToolbarViewModel: SharedToolbarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarMainTitle)
        centeredToolbarTitle = binding.toolbarTitleCentered
        persistentSearchView = binding.searchViewPersistent

        val viewPagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = viewPagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "Home"
                    tab.setIcon(R.drawable.morningstar)
                }
                1 -> {
                    tab.text = "Places"
                    tab.setIcon(R.drawable.starofmelchizedek)
                }
                else -> {
                    tab.text = "Tab ${position + 1}"
                }
            }
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                invalidateOptionsMenu() // Re-create the options menu
                if (position == 0) { // "Home" tab
                    binding.appBarLayout.visibility = View.GONE
                } else { // "Places" tab or other tabs
                    binding.appBarLayout.visibility = View.VISIBLE
                    // Title will be updated by the collector below based on sharedToolbarViewModel.uiState
                }
            }
        })

        // Set initial visibility based on the starting tab
        if (binding.viewPager.currentItem == 0) {
            binding.appBarLayout.visibility = View.GONE
        } else {
            binding.appBarLayout.visibility = View.VISIBLE
        }

        dataViewModel.checkForUpdates() // Assuming this handles initial data load if needed
        setupPersistentSearchView()

        // Observe ToolbarUiState from SharedToolbarViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedToolbarViewModel.uiState.collectLatest { toolbarState ->
                    if (binding.appBarLayout.visibility == View.VISIBLE) { // General check for visibility
                        if (binding.viewPager.currentItem == 1) { // Places Tab
                            centeredToolbarTitle.text = "${toolbarState.title} (${toolbarState.count})"
                            // Update persistentSearchView's query if needed (e.g., on config change)
                            if (persistentSearchView.query.toString() != toolbarState.searchQuery) {
                                // Set query without submitting, to restore state
                                persistentSearchView.setQuery(toolbarState.searchQuery, false)
                            }
                        } else {
                            // Handle title for other visible tabs if any
                            // centeredToolbarTitle.text = "Some Other Title"
                        }
                    }
                }
            }
        }
    }
    private fun setupPersistentSearchView() {
        // Set initial query from ViewModel if any (e.g., after config change or if Places is default tab)
        val initialQuery = sharedToolbarViewModel.uiState.value.searchQuery
        if (initialQuery.isNotEmpty() && binding.viewPager.currentItem == 1) { // Only set if on Places tab
            persistentSearchView.setQuery(initialQuery, false)
        }

        persistentSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                sharedToolbarViewModel.setSearchQuery(query ?: "")
                Log.d("MainActivity", "Search Submitted: ${query ?: ""}")
                persistentSearchView.clearFocus() // Important for usability
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Update ViewModel only if the current tab is the "Places" tab
                if (binding.viewPager.currentItem == 1) {
                    sharedToolbarViewModel.setSearchQuery(newText ?: "")
                }
                Log.d("MainActivity", "Search Text Changed: ${newText ?: ""}")
                return true
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // You NO LONGER inflate the places_toolbar_menu here for search.
        // Inflate other menus if you have them for specific tabs.
        // For example, if you had a settings icon:
        // if (binding.viewPager.currentItem == 0) { // Home tab specific menu
        //     menuInflater.inflate(R.menu.home_menu, menu)
        // } else {
        //     menu.clear()
        // }
        // For now, if search was the only thing, this method might become very simple or only call super.
        menu.clear() // Clear any previously inflated menus if not managed by tab
        return true // Or super.onCreateOptionsMenu(menu) if you have other static menu items
    }

    // You can remove onMenuItemActionExpand/Collapse logic related to the old search menu item.

}

