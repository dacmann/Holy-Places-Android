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
import net.dacworld.android.holyplacesofthelord.util.IntentUtils
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import net.dacworld.android.holyplacesofthelord.util.IntentUtils.openUrl
import java.net.URLEncoder
import androidx.core.graphics.Insets
import net.dacworld.android.holyplacesofthelord.MainActivity

class PlaceDetailFragment : Fragment() {

    private var _binding: FragmentPlaceDetailBinding? = null
    private val binding get() = _binding!!

    private val dataViewModel: DataViewModel by activityViewModels {
        val application = requireActivity().application as MyApplication
        DataViewModelFactory(application, application.templeDao, application.visitDao,application.userPreferencesManager)
    }

    private val args: PlaceDetailFragmentArgs by navArgs()

    private var currentTemple: Temple? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

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
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars()) // System's own nav bar (gesture, etc.)
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            val mainActivity = requireActivity() as? MainActivity // Cast to your MainActivity
            // Get the stable height from MainActivity
            val appBottomNavHeight = mainActivity?.getStableBottomNavActualHeight() ?: 0

            Log.d("PlaceDetailFragmentInsets", "Listener Fired. View ID: ${v.id}")
            Log.d("PlaceDetailFragmentInsets", "SystemBars Insets: T=${systemBars.top}, L=${systemBars.left}, R=${systemBars.right}, B=${systemBars.bottom}")
            Log.d("PlaceDetailFragmentInsets", "NavigationBars Insets (System Gesture Nav): B=${navigationBars.bottom}")
            Log.d("PlaceDetailFragmentInsets", "IME Insets: B=${imeInsets.bottom}")
            Log.d("PlaceDetailFragmentInsets", "App BottomNav Height (from Activity): $appBottomNavHeight")
            Log.d("PlaceDetailFragmentInsets", "View Initial Padding: L=${v.paddingLeft}, T=${v.paddingTop}, R=${v.paddingRight}, B=${v.paddingBottom}")


            val isImeVisible = imeInsets.bottom > 0
            var desiredBottomPadding = 0

            if (isImeVisible) {
                // If IME is visible, it takes precedence.
                // The padding should be enough to clear the IME.
                desiredBottomPadding = imeInsets.bottom
                Log.d("PlaceDetailFragmentInsets", "IME is visible. DesiredBottomPadding = IME height: $desiredBottomPadding")
            } else {
                // If IME is NOT visible, content should be padded to clear the app's BottomNavigationView.
                // The appBottomNavHeight from MainActivity already accounts for the system navigation bar
                // because the BNV itself is padded by MainActivity's inset listener.
                desiredBottomPadding = appBottomNavHeight
                Log.d("PlaceDetailFragmentInsets", "IME NOT visible. DesiredBottomPadding = App BNV height: $desiredBottomPadding")

                // As a fallback, if appBottomNavHeight is 0 (e.g., during initial setup or if BNV is hidden),
                // ensure at least system navigation bar space is cleared if that's greater.
                // However, appBottomNavHeight SHOULD be the primary source here.
                if (appBottomNavHeight == 0 && navigationBars.bottom > 0) {
                    Log.w("PlaceDetailFragmentInsets", "App BNV height was 0. Using system navigationBars.bottom as fallback for padding: ${navigationBars.bottom}")
                    desiredBottomPadding = kotlin.math.max(desiredBottomPadding, navigationBars.bottom)
                }
            }

            Log.d("PlaceDetailFragmentInsets", "Final Desired Bottom Padding: $desiredBottomPadding")

            // Apply padding
            // TOP PADDING: Set to 0 if your AppBarLayout (containing placeDetailToolbar) handles top insets.
            // If placeDetailToolbar is inside an AppBarLayout that has fitsSystemWindows=true or its own inset listener,
            // then top padding for contentViewToPad here should be 0.
            v.setPadding(
                systemBars.left,
                0,  // Assuming AppBarLayout handles status bar insets for the toolbar
                systemBars.right,
                desiredBottomPadding
            )
            Log.d("PlaceDetailFragmentInsets", "View Set Padding: L=${v.paddingLeft}, T=${v.paddingTop}, R=${v.paddingRight}, B=${v.paddingBottom}")

            // --- Inset Consumption ---
            val consumedInsetsBuilder = WindowInsetsCompat.Builder(insets)
            var consumedSomething = false

            // Consume IME if it was visible and we padded for it.
            if (isImeVisible && imeInsets.bottom > 0) {
                consumedInsetsBuilder.setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                Log.d("PlaceDetailFragmentInsets", "Consumed IME insets.")
                consumedSomething = true
            }

            // Consume navigationBars insets if:
            // 1. IME is NOT visible (otherwise IME handling takes precedence)
            // 2. We are padding using appBottomNavHeight (which means the BNV is visible and providing its height)
            // 3. And navigationBars.bottom > 0 (meaning there's a system nav bar to account for).
            // The appBottomNavHeight effectively "handles" the navigationBars space because the BNV is above it.
            if (!isImeVisible && appBottomNavHeight > 0 && navigationBars.bottom > 0) {
                consumedInsetsBuilder.setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                Log.d("PlaceDetailFragmentInsets", "Consumed NavigationBars insets (App BNV handled this space).")
                consumedSomething = true
            }
            // Note: We don't need to consume systemBars.left/right/top here if this 'v' is the primary scrolling content
            // and the AppBar is handling top. If 'v' was a specific child needing only certain parts of systemBars,
            // consumption logic would be more granular. For this full-screen fragment content, this is typical.

            if (consumedSomething) {
                return@setOnApplyWindowInsetsListener consumedInsetsBuilder.build()
            } else {
                // If we didn't explicitly consume IME or NavigationBars (e.g., both were 0, or BNV was hidden and no IME),
                // return the original insets. Other views might still need them (e.g., for displayCutout).
                return@setOnApplyWindowInsetsListener insets
            }
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

        // --- Setup for the new "Record Visit" button ---
        binding.buttonRecordVisit.setOnClickListener {
            currentTemple?.let { temple ->
                // Ensure temple.name and temple.type have fallbacks if they can be null
                val action = PlaceDetailFragmentDirections.actionPlaceDetailFragmentToRecordVisitFragment(
                    visitId = -1L, // -1L or 0L indicates a new visit
                    placeId = temple.id, // Assuming temple.id is non-null
                    placeName = temple.name ?: getString(R.string.unknown),
                    placeType = temple.type ?: "U" // "U" for Unknown/Unspecified
                )
                findNavController().navigate(action)
            } ?: run {
                // This case should ideally not happen if a temple is loaded and displayed
                Toast.makeText(context, getString(R.string.temple_details_not_available_for_visit), Toast.LENGTH_SHORT).show()
                Log.e("PlaceDetailFragment", "Attempted to record visit but currentTemple is null.")
            }
        }
    }

    private fun loadTempleDetails(templeId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val temple = dataViewModel.getTempleDetailsWithPicture(templeId) // Assuming this fetches all needed details

            currentTemple = temple

            if (temple != null) {
                bindTempleData(temple)
                binding.placeDetailToolbar.title = ""
            } else {
                Log.w("PlaceDetailFragment", "Temple with ID $templeId not found.")
                binding.textViewTempleNameDetail.text = getString(R.string.error_temple_not_found)
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
        // --- End of Address Fields & Click Handling ---

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
                context?.let { ctx -> // Ensure context is not null
                    IntentUtils.openUrl(ctx, infoLink, getString(R.string.error_no_app_for_info_url))
                }
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
        val navigationChooserListener = View.OnClickListener {
            Log.d("PlaceDetailFragment", "Address field clicked, showing custom navigation chooser for ${temple.name}.")
            showNavigationChooser(temple) // This is your custom method
        }

        // Apply to textViewAddressDetail if it has text
        if (!temple.address.isNullOrBlank()) {
            binding.textViewAddressDetail.setOnClickListener(navigationChooserListener)
        } else {
            binding.textViewAddressDetail.setOnClickListener(null)
            binding.textViewAddressDetail.isClickable = false
        }

        // Apply to textViewCityStateDetail if it has text
        if (!temple.cityState.isNullOrBlank()) {
            binding.textViewCityStateDetail.setOnClickListener(navigationChooserListener)
        } else {
            binding.textViewCityStateDetail.setOnClickListener(null)
            binding.textViewCityStateDetail.isClickable = false
        }

        // Apply to textViewCountryDetail if it has text
        if (!temple.country.isNullOrBlank()) {
            binding.textViewCountryDetail.setOnClickListener(navigationChooserListener)
        } else {
            binding.textViewCountryDetail.setOnClickListener(null)
            binding.textViewCountryDetail.isClickable = false
        }
        // --- End of Address Fields & Click Handling ---

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
                    context?.let { ctx -> // Ensure context is not null
                        IntentUtils.openUrl(ctx, url, getString(R.string.error_no_app_for_info_url))
                    }
                } else {
                    Toast.makeText(context, getString(R.string.info_url_not_available), Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(context, getString(R.string.info_url_not_available), Toast.LENGTH_SHORT).show()
        }

        binding.buttonSchedule.setOnClickListener {
            temple.siteUrl?.let { url ->
                if (url.isNotBlank()) {
                    context?.let { ctx -> // Ensure context is not null
                        IntentUtils.openUrl(ctx, url, getString(R.string.error_no_app_for_info_url))
                    }
                } else {
                    Toast.makeText(context, getString(R.string.schedule_url_not_available), Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(context, getString(R.string.schedule_url_not_available), Toast.LENGTH_SHORT).show()
        }
        binding.buttonRecordVisit.isEnabled = true
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
        currentTemple = null
    }
}
