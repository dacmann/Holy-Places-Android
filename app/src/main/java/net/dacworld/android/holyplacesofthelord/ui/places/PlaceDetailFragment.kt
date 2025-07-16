package net.dacworld.android.holyplacesofthelord.ui.placedetail

import android.content.ActivityNotFoundException
import android.content.Intent
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
        // The view that needs padding. This should be the container of your
        // content that is getting obscured. binding.contentConstraintLayout seems
        // appropriate from your fragment_place_detail.xml.
        val contentViewToPad = binding.root

        // THE ONE AND ONLY LISTENER ATTACHMENT
        ViewCompat.setOnApplyWindowInsetsListener(contentViewToPad) { v, insets ->
            // ALL YOUR ORIGINAL LISTENER LOGIC AND PADDING CODE GOES HERE

            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            var desiredBottomPadding = systemBars.bottom

            val activityRootView = requireActivity().window.decorView
            val bottomNavView = activityRootView.findViewById<BottomNavigationView>(R.id.main_bottom_navigation)

            if (bottomNavView != null) {if (bottomNavView.visibility == View.VISIBLE) {
                    desiredBottomPadding += bottomNavView.height // This is the part that might need the fixed dimen later
                }
            } else {
                Log.d("PlaceDetailFragmentInsets", "BottomNavView not found!")
            }

            desiredBottomPadding = kotlin.math.max(desiredBottomPadding, ime.bottom)
            Log.d("PlaceDetailFragmentInsets", "Final Desired Bottom Padding: $desiredBottomPadding")

            v.updatePadding(
                left = systemBars.left,
                right = systemBars.right,
                bottom = desiredBottomPadding
            )
            Log.d("PlaceDetailFragmentInsets", "Applied Padding: Left=${v.paddingLeft}, Top=${v.paddingTop}, Right=${v.paddingRight}, Bottom=${v.paddingBottom}")

            insets // Return insets
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


        binding.textViewFhCodeDetail.text = temple.fhCode ?: "N/A"
        binding.textViewAddressDetail.text = temple.address ?: getString(R.string.address_not_available)
        binding.textViewPhoneDetail.text = temple.phone ?: getString(R.string.phone_not_available)

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important for preventing memory leaks
    }
}
