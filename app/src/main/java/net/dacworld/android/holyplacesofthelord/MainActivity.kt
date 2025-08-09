package net.dacworld.android.holyplacesofthelord

import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.data.DataViewModelFactory
import net.dacworld.android.holyplacesofthelord.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val dataViewModel: DataViewModel by viewModels {
        val application = application as MyApplication
        DataViewModelFactory(application,
            application.templeDao,
            application.visitDao,
            application.userPreferencesManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // If using the traditional splash screen method, you might have setTheme() here
        // For example: setTheme(R.style.AppTheme) // Switch from splash theme to main app theme
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        // It's a phone, lock to portrait
        if (!resources.getBoolean(R.bool.is_tablet)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // force the background of the bottom nav bar to not be tinted
        val bottomNav = binding.mainBottomNavigation
        val bottomNavBackground = bottomNav.background
        if (bottomNavBackground is MaterialShapeDrawable) {
            val resolvedAppColorSurface = ContextCompat.getColor(this, R.color.app_colorSurface)
            bottomNavBackground.fillColor = ColorStateList.valueOf(resolvedAppColorSurface)
            bottomNavBackground.setTintList(ColorStateList.valueOf(resolvedAppColorSurface))
            bottomNavBackground.elevation = 0f
            bottomNav.invalidate() // Request a redraw
        } else {
            Log.w("MainActivity", "BottomNavigationView background is not a MaterialShapeDrawable. Current background: ${bottomNavBackground?.javaClass?.name}")
        }


        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        binding.mainBottomNavigation.setupWithNavController(navController)
        // Optional: Control BottomNav visibility based on destination
        // This is useful if you have screens like Login/Splash where you DON'T want bottom nav
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.mainBottomNavigation.visibility = when (destination.id) {
                // R.id.loginFragment, // Add any fragments where you want to hide bottom nav
                // R.id.splashFragment -> View.GONE
                R.id.placeDetailFragment -> android.view.View.VISIBLE // Explicitly show on detail
                else -> android.view.View.VISIBLE // Show by default
            }
        }
        // Trigger initial data update check.
        // UI for loading/results should be handled by observing DataViewModel's StateFlows
        // in relevant fragments (e.g., TabsHostFragment or PlacesFragment).
        dataViewModel.checkForUpdates()
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, null) || super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // MainActivity has no global menu items to inflate.
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // MainActivity has no global menu items to handle.
        return super.onOptionsItemSelected(item)
    }
}
