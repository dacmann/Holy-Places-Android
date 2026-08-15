package net.dacworld.android.holyplacesofthelord.ui.visits

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.databinding.FragmentAddVisitPlacePickerBinding
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.util.ColorUtils

class AddVisitPlacePickerFragment : Fragment() {

    private var _binding: FragmentAddVisitPlacePickerBinding? = null
    private val binding get() = _binding!!

    private val navArgs: AddVisitPlacePickerFragmentArgs by navArgs()

    private val viewModel: AddVisitPlacePickerViewModel by viewModels {
        AddVisitPlacePickerViewModelFactory(
            requireActivity().application,
            navArgs.isChangingPlace,
            navArgs.currentPlaceId,
            navArgs.currentPlaceName,
            navArgs.currentPlaceType,
            UserPreferencesManager.getInstance(requireContext().applicationContext)
        )
    }

    private lateinit var adapter: PlacePickerAdapter
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }
    private var freshLocationRequested = false
    private var lastKnownLocationRequested = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchLocation()
        } else {
            viewModel.setClosestEnabled(false)
            binding.closestPlaceSwitch.isChecked = false
            Toast.makeText(requireContext(), R.string.location_permission_needed_closest, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddVisitPlacePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupList()
        setupTypeToggle()
        setupSearch()
        setupClosestSwitch()
        setupOtherName()
        setupMenu()
        setupInsets()
        observeState()
    }

    private fun setupToolbar() {
        val toolbar = binding.toolbarPlacePicker
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        val title = if (navArgs.isChangingPlace) {
            getString(R.string.title_change_place)
        } else {
            getString(R.string.title_add_new_visit)
        }
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            this.title = title
        }
        binding.placePickerHeading.text = title
        if (navArgs.isChangingPlace) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_clear_material)
        }
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_place_picker, menu)
                menu.findItem(R.id.action_confirm_place)?.title =
                    if (navArgs.isChangingPlace) getString(R.string.done) else getString(R.string.action_next)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_confirm_place)?.isEnabled = viewModel.uiState.value.canConfirm
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_confirm_place -> {
                        confirmSelection()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupList() {
        adapter = PlacePickerAdapter { temple ->
            viewModel.selectPlace(temple.id)
        }
        binding.placePickerList.layoutManager = LinearLayoutManager(requireContext())
        binding.placePickerList.adapter = adapter
    }

    private fun setupTypeToggle() {
        binding.placeTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val category = when (checkedId) {
                R.id.button_type_historic -> PlacePickerCategory.HISTORIC
                R.id.button_type_visitors -> PlacePickerCategory.VISITORS
                R.id.button_type_other -> PlacePickerCategory.OTHER
                else -> PlacePickerCategory.TEMPLE
            }
            if (category != viewModel.uiState.value.category) {
                viewModel.setCategory(category)
            }
        }
    }

    private fun setupSearch() {
        binding.placePickerSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupClosestSwitch() {
        binding.closestPlaceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked == viewModel.uiState.value.closestEnabled) return@setOnCheckedChangeListener
            if (isChecked) {
                ensureLocationThenEnableClosest()
            } else {
                viewModel.setClosestEnabled(false)
            }
        }
    }

    private fun setupOtherName() {
        binding.otherNameEdit.doAfterTextChanged { text ->
            viewModel.setOtherName(text?.toString().orEmpty())
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val expectedButton = when (state.category) {
                        PlacePickerCategory.TEMPLE -> R.id.button_type_temple
                        PlacePickerCategory.HISTORIC -> R.id.button_type_historic
                        PlacePickerCategory.VISITORS -> R.id.button_type_visitors
                        PlacePickerCategory.OTHER -> R.id.button_type_other
                    }
                    if (binding.placeTypeToggle.checkedButtonId != expectedButton) {
                        binding.placeTypeToggle.check(expectedButton)
                    }
                    val otherVisible = state.isOther
                    binding.otherNameLayout.visibility = if (otherVisible) View.VISIBLE else View.GONE
                    binding.placePickerList.visibility = if (otherVisible) View.GONE else View.VISIBLE
                    binding.placePickerSearch.visibility = if (otherVisible) View.GONE else View.VISIBLE
                    binding.closestPlaceRow.visibility = if (otherVisible) View.GONE else View.VISIBLE
                    if (binding.closestPlaceSwitch.isChecked != state.closestEnabled) {
                        binding.closestPlaceSwitch.isChecked = state.closestEnabled
                    }
                    if (otherVisible && binding.otherNameEdit.text?.toString() != state.otherName) {
                        binding.otherNameEdit.setText(state.otherName)
                        binding.otherNameEdit.setSelection(state.otherName.length)
                    }
                    adapter.submit(state.places, state.selectedPlaceId)
                    requireActivity().invalidateOptionsMenu()
                    if (state.closestEnabled && !otherVisible) {
                        fetchLocationIfPermitted()
                    }
                }
            }
        }
    }

    private fun confirmSelection() {
        val selected = viewModel.confirmedSelection()
        if (selected == null) {
            Toast.makeText(requireContext(), R.string.place_picker_select_place, Toast.LENGTH_SHORT).show()
            return
        }
        if (navArgs.isChangingPlace) {
            setFragmentResult(
                RESULT_KEY,
                bundleOf(
                    RESULT_PLACE_ID to selected.placeId,
                    RESULT_PLACE_NAME to selected.placeName,
                    RESULT_PLACE_TYPE to selected.placeType
                )
            )
            findNavController().navigateUp()
        } else {
            val action = AddVisitPlacePickerFragmentDirections
                .actionAddVisitPlacePickerFragmentToRecordVisitFragment(
                    visitId = -1L,
                    placeId = selected.placeId,
                    placeName = selected.placeName,
                    placeType = selected.placeType
                )
            findNavController().navigate(action)
        }
    }

    private fun ensureLocationThenEnableClosest() {
        if (hasLocationPermission()) {
            viewModel.setClosestEnabled(true)
            fetchLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun fetchLocationIfPermitted() {
        if (hasLocationPermission()) {
            fetchLocation()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (!lastKnownLocationRequested) {
            lastKnownLocationRequested = true
            fusedLocationClient.lastLocation
                .addOnSuccessListener { last ->
                    if (last != null) {
                        viewModel.setDeviceLocation(last)
                    }
                }
        }
        if (freshLocationRequested) return
        freshLocationRequested = true
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(5 * 60 * 1000L)
            .setDurationMillis(15_000L)
            .build()
        fusedLocationClient.getCurrentLocation(request, CancellationTokenSource().token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    viewModel.setDeviceLocation(location)
                }
            }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarPlacePicker) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.placePickerList) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "change_place_result"
        const val RESULT_PLACE_ID = "place_id"
        const val RESULT_PLACE_NAME = "place_name"
        const val RESULT_PLACE_TYPE = "place_type"
    }
}

private class PlacePickerAdapter(
    private val onClick: (Temple) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<PlacePickerAdapter.Holder>() {

    private var items: List<Temple> = emptyList()
    private var selectedId: String? = null

    fun submit(places: List<Temple>, selectedPlaceId: String?) {
        items = places
        selectedId = selectedPlaceId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_place_picker, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], items[position].id == selectedId)
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val nameView: android.widget.TextView = itemView.findViewById(R.id.place_picker_name)
        private val checkView: android.widget.ImageView = itemView.findViewById(R.id.place_picker_selected)

        fun bind(temple: Temple, selected: Boolean) {
            nameView.text = temple.name
            nameView.setTextColor(ColorUtils.getTextColorForTempleType(itemView.context, temple.type))
            checkView.visibility = if (selected) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(temple) }
        }
    }
}
