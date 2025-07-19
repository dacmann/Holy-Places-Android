// OptionsFragment.kt
package net.dacworld.android.holyplacesofthelord.ui.places

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.values
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentOptionsBinding
import net.dacworld.android.holyplacesofthelord.model.PlaceFilter
import net.dacworld.android.holyplacesofthelord.model.PlaceSort
import net.dacworld.android.holyplacesofthelord.ui.OptionsUiState
import net.dacworld.android.holyplacesofthelord.ui.SharedOptionsViewModel
import kotlinx.coroutines.launch
import android.widget.Toast
import net.dacworld.android.holyplacesofthelord.ui.places.CustomSpinnerAdapter
import net.dacworld.android.holyplacesofthelord.ui.SharedOptionsViewModelFactory
import net.dacworld.android.holyplacesofthelord.data.DataViewModel

class OptionsFragment : Fragment() {

    private var _binding: FragmentOptionsBinding? = null
    private val binding get() = _binding!!

    private val dataViewModel: DataViewModel by activityViewModels()
    private val sharedOptionsViewModel: SharedOptionsViewModel by activityViewModels { // or viewModels depending on desired scope
        SharedOptionsViewModelFactory(dataViewModel)
    }

    private lateinit var filterAdapter: CustomSpinnerAdapter<PlaceFilter>
    private lateinit var sortAdapter: CustomSpinnerAdapter<PlaceSort>

    private var isInitialFilterSetup = true
    private var isInitialSortSetup = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.button_text_options)
            // setDisplayHomeAsUpEnabled(true) // Consider if you want Up navigation
        }
        // (activity as? AppCompatActivity)?.supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
        // binding.yourToolbarId.setNavigationOnClickListener { findNavController().popBackStack() }


        setupSpinners()
        observeViewModel()

        binding.buttonDoneOptions.setOnClickListener {
            // ViewModel is already updated by spinner listeners
            findNavController().popBackStack()
        }
    }

    private fun setupSpinners() {
        // --- Filter Spinner ---

        filterAdapter = CustomSpinnerAdapter(
            requireContext(),
            R.layout.spinner_item_custom, // Layout for selected item
            R.layout.spinner_dropdown_item_custom, // Layout for dropdown items
            PlaceFilter.values().toList(),
            displayMapper = { it.displayName },
            colorMapper = { it.customColorRes } // Pass the color resource from the enum
        )
        binding.spinnerFilter.adapter = filterAdapter

        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInitialFilterSetup) {
                    isInitialFilterSetup = false // Don't trigger update on initial ViewModel load
                    return
                }
                val selectedFilter = parent?.getItemAtPosition(position) as PlaceFilter
                Log.d("OptionsFragment", "Filter Spinner selected: ${selectedFilter.displayName}")
                sharedOptionsViewModel.setFilter(selectedFilter)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- Sort Spinner ---
        // Initial sort options will be set by observing ViewModel
        sortAdapter = CustomSpinnerAdapter(
            requireContext(),
            R.layout.spinner_item_custom,
            R.layout.spinner_dropdown_item_custom,
            mutableListOf(), // Initially empty, will be populated by ViewModel
            displayMapper = { it.displayName },
            colorMapper = { _ -> R.color.grey_text }
        )
        binding.spinnerSort.adapter = sortAdapter

        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInitialSortSetup) {
                    isInitialSortSetup = false // Don't trigger update on initial ViewModel load
                    return
                }
                val selectedSort = parent?.getItemAtPosition(position) as? PlaceSort
                selectedSort?.let {
                    Log.d("OptionsFragment", "Sort Spinner selected: ${it.displayName}")
                    sharedOptionsViewModel.setSort(it)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedOptionsViewModel.uiState.collect { uiState ->
                    Log.d("OptionsFragment", "Observed UI State: Filter=${uiState.currentFilter}, Sort=${uiState.currentSort}")

                    // Update Filter Spinner selection (only if different)
                    val filterPosition = filterAdapter.getPosition(uiState.currentFilter)
                    if (binding.spinnerFilter.selectedItemPosition != filterPosition) {
                        Log.d("OptionsFragment", "Setting filter spinner to position: $filterPosition for ${uiState.currentFilter.displayName}")
                        isInitialFilterSetup = true // Prevent listener from re-triggering ViewModel update
                        binding.spinnerFilter.setSelection(filterPosition, false) // false for no animation
                    }

                    // --- Update Sort Spinner Adapter Content ---
                    val currentAdapterSortItems = (0 until sortAdapter.count).mapNotNull { sortAdapter.getItem(it) }
                    Log.d("OptionsFragment", "Sort Adapter Check: Current items in adapter: ${currentAdapterSortItems.map { it.displayName }}")

                    if (currentAdapterSortItems != uiState.availableSortOptions) {
                        Log.i("OptionsFragment", "DIFFERENCE DETECTED. Updating sort adapter. Adapter had: ${currentAdapterSortItems.map{it.displayName}}. ViewModel has: ${uiState.availableSortOptions.map { it.displayName }}")
                        val oldSortSelection = binding.spinnerSort.selectedItem as? PlaceSort // Preserve if possible
                        sortAdapter.clear()
                        sortAdapter.addAll(uiState.availableSortOptions)
                        // notifyDataSetChanged() is generally called by addAll.
                        // If you face issues where spinner doesn't refresh visually, uncomment it.
                        // sortAdapter.notifyDataSetChanged()

                        // Attempt to reselect the previously selected item if it's still valid, or default to ViewModel's currentSort
                        val newSortToSelect = if (uiState.availableSortOptions.contains(oldSortSelection)) {
                            oldSortSelection
                        } else {
                            uiState.currentSort // This should be valid as per ViewModel logic
                        }
                        val sortPositionToSet = uiState.availableSortOptions.indexOf(newSortToSelect)

                        if (sortPositionToSet != -1) {
                            Log.d("OptionsFragment", "Setting sort spinner selection (after adapter update) to: ${newSortToSelect?.displayName} at pos $sortPositionToSet")
                            isInitialSortSetup = true
                            binding.spinnerSort.setSelection(sortPositionToSet, false)
                        } else if (uiState.availableSortOptions.isNotEmpty()) {
                            // Fallback if the desired sort (even from ViewModel) isn't found, select the first
                            Log.w("OptionsFragment", "Sort item ${newSortToSelect?.displayName} not found after adapter update. Defaulting to first. VM wanted ${uiState.currentSort.displayName}")
                            isInitialSortSetup = true
                            binding.spinnerSort.setSelection(0, false)
                        }

                    } else {
                        Log.d("OptionsFragment", "Sort adapter content MATCHES ViewModel. No adapter data change needed.")
                        // Even if adapter data didn't change, the selected item might need to.
                        // This handles the case where filter changes, available sorts are coincidentally the same,
                        // but the ViewModel wants a *different* default sort for the new filter.
                        val sortPositionInAdapter = uiState.availableSortOptions.indexOf(uiState.currentSort) // uiState.availableSortOptions IS currentAdapterSortItems here
                        if (sortPositionInAdapter != -1 && binding.spinnerSort.selectedItemPosition != sortPositionInAdapter) {
                            Log.d("OptionsFragment", "Setting sort spinner selection (no adapter data change) to: ${uiState.currentSort.displayName} at pos $sortPositionInAdapter")
                            isInitialSortSetup = true
                            binding.spinnerSort.setSelection(sortPositionInAdapter, false)
                        } else if (sortPositionInAdapter == -1 && uiState.availableSortOptions.isNotEmpty()) {
                            Log.w("OptionsFragment", "Could not find position for sort (no adapter data change): ${uiState.currentSort.displayName}")
                        }
                    }

                    // Handle Location Setup Trigger
                    if (uiState.triggerLocationSetup) {
                        Log.d("OptionsFragment", "Location setup trigger received.")
                        // Call your function to request location permissions/settings
                        // requestLocationPermissions() // Example
                        sharedOptionsViewModel.locationSetupTriggerConsumed()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
