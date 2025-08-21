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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View

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

    private var stableBottomNavActualHeight: Int = 0
    private var areActivityInsetsInitialized: Boolean = false

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
        // --- 1. Setup Insets for BottomNavigationView ---
        // This ensures the BNV itself is padded correctly for the system navigation bar (gesture pill, etc.)
        // and its stable height (including this padding) is captured.
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val systemNavBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val currentPaddingBottom = view.paddingBottom
            val newPaddingBottom = systemNavBars.bottom

            if (currentPaddingBottom != newPaddingBottom) {
                Log.d("MainActivity_BNV_Insets", "Applying BNV paddingBottom: $newPaddingBottom (was $currentPaddingBottom)")
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, newPaddingBottom)
            } else {
                Log.d("MainActivity_BNV_Insets", "BNV paddingBottom already $newPaddingBottom")
            }

            // After BNV has its padding, its height is stable for this configuration.
            // Capture it using post to ensure measurement after this layout pass.
            if (!areActivityInsetsInitialized || stableBottomNavActualHeight == 0) {
                view.post {
                    val measuredHeight = view.measuredHeight
                    if (measuredHeight > 0) {
                        stableBottomNavActualHeight = measuredHeight
                        areActivityInsetsInitialized = true
                        Log.d("MainActivity_BNV_Height", "Stable BNV Actual Height set (post): $stableBottomNavActualHeight")
                    } else {
                        Log.w("MainActivity_BNV_Height", "BNV measuredHeight was 0 in post. Will retry.")
                    }
                }
            }
            // Return original insets. Fragments will get the stableBottomNavActualHeight.
            insets
        }

        // Ensure insets are requested for the BottomNavigationView
        if (bottomNav.isAttachedToWindow) {
            Log.d("MainActivity_BNV_Setup", "BNV already attached. Requesting apply insets.")
            bottomNav.requestApplyInsets()
        } else {
            Log.d("MainActivity_BNV_Setup", "BNV not attached. Adding OnAttachStateChangeListener for insets.")
            bottomNav.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    Log.d("MainActivity_BNV_Setup", "BNV attached to window. Requesting apply insets.")
                    v.removeOnAttachStateChangeListener(this)
                    v.requestApplyInsets()
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }

        // --- 2. Apply Custom Background Styling to BottomNavigationView ---
        // This is your existing logic. It should run after the BNV is configured for insets.
        val bottomNavBackground = bottomNav.background // This line is here
        if (bottomNavBackground is MaterialShapeDrawable) {
            val resolvedAppColorSurface = ContextCompat.getColor(this, R.color.app_colorSurface)
            bottomNavBackground.fillColor = ColorStateList.valueOf(resolvedAppColorSurface)
            // bottomNavBackground.setTintList(ColorStateList.valueOf(resolvedAppColorSurface)) // Often redundant
            // bottomNavBackground.elevation = 0f // As per your original code
            // bottomNav.invalidate() // requestApplyInsets() or layout changes should trigger redraw
            Log.d("MainActivity_BNV_Style", "Applied MaterialShapeDrawable background styling to BNV.")
        } else {
            Log.w("MainActivity_BNV_Style", "BottomNavigationView background is not a MaterialShapeDrawable. Background: ${bottomNavBackground?.javaClass?.name}")
        }

        // --- 3. Setup Navigation Controller ---
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        binding.mainBottomNavigation.setupWithNavController(navController)

        // Optional: Control BottomNav visibility based on destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Log the destination ID and label (if available)
            val destinationLabel = destination.label ?: "No Label"
            Log.d("MainActivity_Nav", "Destination Changed. ID: ${destination.id}, ResName: ${try { resources.getResourceEntryName(destination.id) } catch (e: Exception) { "N/A" }}, Label: $destinationLabel")
            binding.mainBottomNavigation.visibility = when (destination.id) {
                R.id.placeDetailFragment -> android.view.View.VISIBLE
                else -> android.view.View.VISIBLE
            }
        }

        // --- 4. Initial Data Load ---
        dataViewModel.checkForUpdates()
    }

    // --- Method to provide stable BNV height to Fragments ---
    fun getStableBottomNavActualHeight(): Int {
        if (stableBottomNavActualHeight == 0 && binding.mainBottomNavigation.isLaidOut && binding.mainBottomNavigation.height > 0) {
            stableBottomNavActualHeight = binding.mainBottomNavigation.height
            Log.w("MainActivity_BNV_Height", "Stable BNV Height read directly as fallback: $stableBottomNavActualHeight. (areActivityInsetsInitialized=$areActivityInsetsInitialized)")
            if (stableBottomNavActualHeight > 0 && !areActivityInsetsInitialized) {
                // areActivityInsetsInitialized = true; // Consider if this is safe if fallback is used
            }
        } else if (stableBottomNavActualHeight == 0 && binding.mainBottomNavigation.isLaidOut) {
            Log.w("MainActivity_BNV_Height", "getStable...: BNV is laid out but height is 0 or stableHeight is 0. BNV Height: ${binding.mainBottomNavigation.height}")
        }

        if (stableBottomNavActualHeight == 0) {
            Log.e("MainActivity_BNV_Height", "getStableBottomNavActualHeight() called but stableBottomNavActualHeight is still 0. BNV visible: ${binding.mainBottomNavigation.visibility == View.VISIBLE}, BNV laid out: ${binding.mainBottomNavigation.isLaidOut}, BNV height: ${binding.mainBottomNavigation.height}")
        }
        return stableBottomNavActualHeight
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
