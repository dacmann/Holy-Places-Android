package net.dacworld.android.holyplacesofthelord.ui.placedetail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.NavigationUI
import androidx.appcompat.widget.Toolbar
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
            // Option A: Simplest pop
            // navController.popBackStack()

            // Option B: More robust, like NavigationUI.navigateUp
            // This takes into account if you're at the start destination of a nested graph.
            // For a simple detail screen, popBackStack() is usually fine.
            navController.navigateUp()
        }

        // REMOVE or COMMENT OUT:
        // NavigationUI.setupWithNavController(binding.placeDetailToolbar, navController, appBarConfiguration)


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

        // Bind data to views, using IDs from fragment_place_detail.xml
        binding.textViewTempleNameDetail.text = temple.name
        binding.textViewSnippetDetail.text = temple.snippet // Show empty string if snippet is null
        binding.textViewFhCodeDetail.text = temple.fhCode ?: "N/A"
        binding.textViewAddressDetail.text = temple.address ?: getString(R.string.address_not_available)
        binding.textViewPhoneDetail.text = temple.phone ?: getString(R.string.phone_not_available)

        // Image Loading with Coil
        when {
            temple.pictureData != null -> {
                binding.imageViewTempleDetail.load(temple.pictureData) {
                    crossfade(true)
                    placeholder(R.drawable.default_placeholder_image) // Provide your placeholder
                    error(R.drawable.default_placeholder_image)     // Provide your error drawable
                }
            }
            temple.pictureUrl.isNotBlank() -> {
                binding.imageViewTempleDetail.load(temple.pictureUrl) {
                    crossfade(true)
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
