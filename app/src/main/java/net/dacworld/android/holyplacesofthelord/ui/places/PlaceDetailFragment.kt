package net.dacworld.android.holyplacesofthelord.ui.placedetail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.NavigationUI
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import coil.load
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.MyApplication
import net.dacworld.android.holyplacesofthelord.R // Make sure this is imported for drawables
import net.dacworld.android.holyplacesofthelord.data.DataViewModel
import net.dacworld.android.holyplacesofthelord.data.DataViewModelFactory
import net.dacworld.android.holyplacesofthelord.databinding.FragmentPlaceDetailBinding // Correct binding class
import net.dacworld.android.holyplacesofthelord.model.Temple
import com.google.android.material.bottomnavigation.BottomNavigationView
import net.dacworld.android.holyplacesofthelord.util.ColorUtils
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import java.net.URLEncoder

class PlaceDetailFragment : Fragment() {

    private var _binding: FragmentPlaceDetailBinding? = null
    private val binding get() = _binding!!

    private val dataViewModel: DataViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication
        DataViewModelFactory(application, application.templeDao, application.userPreferencesManager)
    }

    private val args: PlaceDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Keep the view.post for initial setup if it helped the "navigate to" crash,
        // or set up directly if that crash is no longer observed.
        // For this specific test, let's try direct setup first to simplify.
        // If the "navigate to" crash returns, we can re-add view.post around this.

        val navController = findNavController()
        // val appBarConfiguration = AppBarConfiguration(navController.graph) // Not strictly needed for manual Up

        // 1. Set this fragment's toolbar as the SupportActionBar
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.placeDetailToolbar)

        // 2. Manually set the title to empty
        (requireActivity() as AppCompatActivity).supportActionBar?.title = ""

        // 3. Manually enable and handle the Up button
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.placeDetailToolbar.setNavigationOnClickListener {
            navController.navigateUp()
        }

        // <<<<<<<<<<<< START: ADD INSET HANDLING CODE HERE >>>>>>>>>>>>>>>>
        val contentViewToPad = binding.root

        // THE ONE AND ONLY LISTENER ATTACHMENT
        ViewCompat.setOnApplyWindowInsetsListener(contentViewToPad) { v, insets ->
            // Get specific inset types
            val systemNavigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars()) // For system's own nav bar
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())                     // For the keyboard
            val systemBarsForSides = insets.getInsets(WindowInsetsCompat.Type.systemBars())     // For L/R padding (e.g., gesture nav)

            // Start with the height of the system's own navigation bar
            var effectiveNavHeight = systemNavigationBars.bottom
            Log.d("PlaceDetailFragmentInsets", "Initial systemNavigationBars.bottom: $effectiveNavHeight")

            // Check for your app's BottomNavigationView
            val activityRootView = requireActivity().window.decorView
            val appBottomNavView = activityRootView.findViewById<BottomNavigationView>(R.id.main_bottom_navigation)

            if (appBottomNavView != null && appBottomNavView.visibility == View.VISIBLE) {
                Log.d("PlaceDetailFragmentInsets", "App's BottomNavView found: Height=${appBottomNavView.height}, Visible=true")
                // If your app's BottomNav is visible and taller than the system's nav bar,
                // or if the system nav bar is very small (common in gesture mode),
                // then the app's BottomNav height is likely the one to use as the primary navigation height.
                if (effectiveNavHeight < appBottomNavView.height) {
                    effectiveNavHeight = appBottomNavView.height
                    Log.d("PlaceDetailFragmentInsets", "Using App's BottomNavView height ($effectiveNavHeight) as effectiveNavHeight")
                }
            } else {
                Log.d("PlaceDetailFragmentInsets", "App's BottomNavView not found or not visible.")
            }

            // The final bottom padding should be enough for whatever is tallest:
            // the effective navigation area (system or app's) OR the keyboard.
            val desiredBottomPadding = kotlin.math.max(effectiveNavHeight, imeInsets.bottom)

            Log.d("PlaceDetailFragmentInsets", "IME.bottom: ${imeInsets.bottom}, EffectiveNavHeight: $effectiveNavHeight, Final desiredBottomPadding: $desiredBottomPadding")

            v.updatePadding(
                left = systemBarsForSides.left,
                right = systemBarsForSides.right,
                bottom = desiredBottomPadding
                // Top padding is not modified here, assuming AppBarLayout handles it
            )

            insets // Return inset
        }
        // Request insets to be applied initially.
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
        Log.d("PlaceDetailFragmentInsets", "Finished setting up inset handling.")
        // <<<<<<<<<<<< END: ADD INSET HANDLING CODE HERE >>>>>>>>>>>>>>>>


        val templeId = args.templeId
        if (templeId.isNotEmpty()) {
            loadTempleDetails(templeId)
        } else {
            Log.e("PlaceDetailFragment", "Temple ID is missing.")
            // Display an error message to the user or navigate back
            binding.textViewTempleNameDetail.text = getString(R.string.error_temple_not_found)
            // Hide other views or show an error state
        }
    }

    private fun loadTempleDetails(templeId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val temple = dataViewModel.getTempleDetailsWithPicture(templeId) // Assuming this fetches all needed details

            if (temple != null) {
                bindTempleData(temple)
                binding.placeDetailToolbar.title = ""
            } else {
                Log.w("PlaceDetailFragment", "Temple with ID $templeId not found.")
                binding.textViewTempleNameDetail.text = getString(R.string.error_temple_not_found)
                // Optionally hide other views or display a more prominent error message
                binding.textViewSnippetDetail.visibility = View.GONE
                binding.textViewFhCodeDetail.visibility = View.GONE
                // ... hide other elements ...
            }
        }
    }

    private fun bindTempleData(temple: Temple) {
        // Toolbar title is now handled in onViewCreated to ensure it's empty.
        // Do NOT set (activity as? AppCompatActivity)?.supportActionBar?.title = temple.name here

        val templeType = temple.type
        var subtitle: String? = null
        var finalSnippetText = temple.snippet ?: ""

        val relevantTypes = listOf("T", "A", "C")
        val delimiter = " - "

        if (templeType != null && relevantTypes.contains(templeType) && !temple.snippet.isNullOrBlank()) {
            Log.d("PlaceDetailDebug", "Attempting to split SNIPPET: '${temple.snippet}' with delimiter: '${delimiter}'")
            val parts = temple.snippet.split(delimiter, limit = 2) // Split the snippet
            Log.d("PlaceDetailDebug", "Parts count after SNIPPET split: ${parts.size}")

            if (parts.size == 2) {
                // The part *before* " - " from the snippet becomes the subtitle
                subtitle = parts[0].trim()
                // The part *after* " - " from the snippet becomes the new snippet text
                finalSnippetText = parts[1].trim()
                Log.d("PlaceDetailDebug", "SNIPPET SPLIT SUCCESS: subtitle='${subtitle}', finalSnippetText='${finalSnippetText}'")
            }
        }

        // Bind data to views, using IDs from fragment_place_detail.xml
        binding.textViewTempleNameDetail.text = temple.name
        // --- Apply the text color logic ---
        val nameColor = ColorUtils.getTextColorForTempleType(requireContext(), temple.type)
        binding.textViewTempleNameDetail.setTextColor(nameColor)

        // Set Subtitle
        if (subtitle != null) {
            binding.textViewTempleSubtitleDetail.text = subtitle
            binding.textViewTempleSubtitleDetail.visibility = View.VISIBLE
        } else {
            binding.textViewTempleSubtitleDetail.visibility = View.GONE
        }

        // Set Snippet Text
        Log.d("PlaceDetailDebug", "FINAL finalSnippetText before setText: '$finalSnippetText'")
        binding.textViewSnippetDetail.text = finalSnippetText
        binding.textViewSnippetDetail.visibility = if (finalSnippetText.isBlank()) View.GONE else View.VISIBLE
        Log.d("PlaceDetailDebug", "Snippet visibility: ${if (finalSnippetText.isBlank()) "GONE" else "VISIBLE"}")


        binding.textViewFhCodeDetail.text = temple.fhCode ?: ""

        binding.textViewAddressDetail.text = temple.address
        binding.textViewAddressDetail.visibility = if (temple.address.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.textViewCityStateDetail.text = temple.cityState
        binding.textViewCityStateDetail.visibility = if (temple.cityState.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.textViewCountryDetail.text = temple.country
        binding.textViewCountryDetail.visibility = if (temple.country.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.textViewPhoneDetail.text = temple.phone ?: getString(R.string.phone_not_available)

        // --- More Info Button Visibility/Action ---
        val moreInfoButton = binding.buttonMoreInfo
        val infoLink = temple.infoUrl // Using the correct field name from your Temple model

        if (infoLink.isNullOrBlank()) {
            moreInfoButton.visibility = View.GONE
            Log.d("PlaceDetailDebug", "More Info button: infoUrl is blank. Hiding button.")
        } else {
            moreInfoButton.visibility = View.VISIBLE
            moreInfoButton.setOnClickListener {
                // Your existing logic from context [1] for opening the URL
                openUrl(infoLink, getString(R.string.error_no_app_for_info_url))
            }
            Log.d("PlaceDetailDebug", "More Info button: infoUrl is '$infoLink'. Showing button.")
        }

        // --- Conditional Button Text for Schedule/Web Site ---
        val scheduleButton = binding.buttonSchedule
        if (templeType != "T") {
            scheduleButton.text = getString(R.string.web_site_button_text) // Web Site
            Log.d("PlaceDetailDebug", "Temple type is '$templeType', setting button text to 'Web Site'")
            // Optionally, you might want to change the button's action/visibility too
            // e.g., scheduleButton.setOnClickListener { openWebsite(temple.websiteUrl) }
        } else {
            scheduleButton.text = getString(R.string.schedule_button_text) // Schedule
            Log.d("PlaceDetailDebug", "Temple type is '$templeType', setting button text to 'Schedule'")
            // e.g., scheduleButton.setOnClickListener { openScheduleScreen(temple.id) }
        }
        // --- End of Conditional Button Text ---

        // Make the address clickable for navigation
        if (temple.latitude != 0.0 || temple.longitude != 0.0) {
            binding.textViewAddressDetail.setOnClickListener {
                showNavigationChooser(temple)
            }
            Log.d("PlaceDetailFragment", "Address is clickable for navigation for ${temple.name}.")
        } else {
            binding.textViewAddressDetail.setOnClickListener(null)
            binding.textViewAddressDetail.isClickable = false
            Log.d("PlaceDetailFragment", "Address is NOT clickable (invalid lat/long).")
        }

        // Phone
        val phoneNumber = temple.phone
        if (!phoneNumber.isNullOrBlank()) {
            binding.textViewPhoneDetail.text = phoneNumber
            binding.textViewPhoneDetail.visibility = View.VISIBLE
            binding.textViewPhoneDetail.setOnClickListener {
                try {
                    // Create an Intent to dial the number
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = "tel:$phoneNumber".toUri()
                    }
                    startActivity(dialIntent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, getString(R.string.no_dialer_app_found), Toast.LENGTH_LONG).show()
                    Log.e("PlaceDetailFragment", "No application can handle dial intent for $phoneNumber", e)
                }
            }
            Log.d("PlaceDetailFragment", "Phone number $phoneNumber is clickable.")

        } else {
            binding.textViewPhoneDetail.text = getString(R.string.phone_not_available) // Or just hide it
            binding.textViewPhoneDetail.visibility = View.GONE // Recommended if phone is not available
            binding.textViewPhoneDetail.setOnClickListener(null)
            binding.textViewPhoneDetail.isClickable = false
            Log.d("PlaceDetailFragment", "Phone number is not available or blank.")
        }


        // Image Loading with Coil
        when {
            temple.pictureData != null -> {
                binding.imageViewTempleDetail.load(temple.pictureData) {
                    placeholder(R.drawable.default_placeholder_image) // Provide your placeholder
                    error(R.drawable.default_placeholder_image)     // Provide your error drawable
                }
            }
            temple.pictureUrl.isNotBlank() -> {
                binding.imageViewTempleDetail.load(temple.pictureUrl) {
                    placeholder(R.drawable.default_placeholder_image)
                    error(R.drawable.default_placeholder_image)
                }
            }
            else -> {
                binding.imageViewTempleDetail.setImageResource(R.drawable.default_placeholder_image)
            }
        }
        // Make snippet GONE if it's empty or null to prevent empty space
        binding.textViewSnippetDetail.visibility = if (temple.snippet.isBlank()) View.GONE else View.VISIBLE


        // Setup Button Click Listeners
        binding.buttonMoreInfo.setOnClickListener {
            temple.infoUrl?.let { url ->
                if (url.isNotBlank()) {
                    openUrl(url, getString(R.string.error_no_app_for_info_url))
                } else {
                    Toast.makeText(context, getString(R.string.info_url_not_available), Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(context, getString(R.string.info_url_not_available), Toast.LENGTH_SHORT).show()
        }

        binding.buttonSchedule.setOnClickListener {
            temple.siteUrl?.let { url ->
                if (url.isNotBlank()) {
                    openUrl(url, getString(R.string.error_no_app_for_schedule_url))
                } else {
                    Toast.makeText(context, getString(R.string.schedule_url_not_available), Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(context, getString(R.string.schedule_url_not_available), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl(urlString: String, noAppErrorMessage: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("PlaceDetailFragment", "ActivityNotFoundException for URL: $urlString", e)
            Toast.makeText(context, noAppErrorMessage, Toast.LENGTH_LONG).show()
        } catch (e: Exception) { // Catch other potential exceptions like malformed URL
            Log.e("PlaceDetailFragment", "Exception opening URL: $urlString", e)
            Toast.makeText(context, getString(R.string.error_invalid_url), Toast.LENGTH_LONG).show()
        }
    }

    fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    private fun showNavigationChooser(temple: Temple) {
        val latitude = temple.latitude
        val longitude = temple.longitude
        val templeName = temple.name ?: "Destination" // Fallback name

        if (latitude == 0.0 && longitude == 0.0) {
            Toast.makeText(context, "Location coordinates not available.", Toast.LENGTH_SHORT).show()
            return
        }

        val availableApps = mutableListOf<Pair<String, Intent>>()
        val packageManager = requireActivity().packageManager

        // --- Waze ---
        val wazePackageName = "com.waze"
        if (isPackageInstalled(wazePackageName, packageManager)) {
            val wazeUri = "waze://?ll=$latitude,$longitude&navigate=yes"
            val wazeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeUri))
            // wazeIntent.setPackage(wazePackageName) // Optional: waze:// is fairly unique
            availableApps.add("Waze" to wazeIntent)
            Log.d("NavChooser", "Waze added to chooser.")
        }

        // --- Google Maps (Show Pin with Label) ---
        val gmapsPackageName = "com.google.android.apps.maps"
        if (isPackageInstalled(gmapsPackageName, packageManager)) {
            val gmmLabel = Uri.encode(templeName)
            val gmmGeoUri = "geo:0,0?q=$latitude,$longitude($gmmLabel)"
            val gmmIntent = Intent(Intent.ACTION_VIEW, Uri.parse(gmmGeoUri))
            // We'll set the package if Google Maps is chosen to go directly to it.
            availableApps.add("Google Maps" to gmmIntent)
            Log.d("NavChooser", "Google Maps (geo URI with label) added to chooser.")
        }

        if (availableApps.isEmpty()) {
            Toast.makeText(context, getString(R.string.no_map_app_found), Toast.LENGTH_LONG).show()
            Log.e("NavChooser", "Neither Waze nor Google Maps found.")
            return
        }

        // --- Build and Show Chooser Dialog ---
        val appNames = availableApps.map { it.first }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Navigate with...")
            .setItems(appNames) { _, which ->
                val chosenPair = availableApps[which]
                val selectedIntent = chosenPair.second

                // Set package for explicit choices to go directly to the app
                if (chosenPair.first == "Google Maps") {
                    selectedIntent.setPackage(gmapsPackageName)
                } else if (chosenPair.first == "Waze") {
                    selectedIntent.setPackage(wazePackageName) // Good practice, though waze:// is usually specific enough
                }

                try {
                    Log.d("NavChooser", "Starting activity for ${chosenPair.first}. Intent URI: ${selectedIntent.dataString}, Package: ${selectedIntent.getPackage()}")
                    startActivity(selectedIntent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "Could not launch ${appNames[which]}.", Toast.LENGTH_SHORT).show()
                    Log.e("NavChooser", "ActivityNotFound for ${appNames[which]}. URI: ${selectedIntent.dataString}", e)
                    // No generic fallback here as per request
                }
            }
            .setNegativeButton("Cancel", null) // Only show explicit choices and a cancel
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important for preventing memory leaks
    }
}
