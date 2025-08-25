package net.dacworld.android.holyplacesofthelord.ui.visits

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs // Import for Safe Args
import coil.load // Import Coil for image loading
import com.google.android.material.datepicker.MaterialDatePicker
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.databinding.FragmentRecordVisitBinding
import net.dacworld.android.holyplacesofthelord.data.Event
import net.dacworld.android.holyplacesofthelord.data.OrdinanceType
import net.dacworld.android.holyplacesofthelord.data.RecordVisitViewModel
import net.dacworld.android.holyplacesofthelord.data.RecordVisitViewModelFactory
import net.dacworld.android.holyplacesofthelord.data.VisitUiState
import net.dacworld.android.holyplacesofthelord.util.ColorUtils
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class RecordVisitFragment : Fragment() {

    private var _binding: FragmentRecordVisitBinding? = null
    private val binding get() = _binding!!

    // Using Safe Args to retrieve navigation arguments
    private val navArgs: RecordVisitFragmentArgs by navArgs()

    private val viewModel: RecordVisitViewModel by viewModels {
        val application = requireActivity().application
        val visitDao = AppDatabase.getDatabase(application).visitDao()

        // Use navArgs for type-safe argument access
        val visitIdArg = if (navArgs.visitId == -1L || navArgs.visitId == 0L) null else navArgs.visitId
        // Ensure non-null args are handled if your nav graph doesn't enforce it (though it should)
        val placeIdArg = navArgs.placeId
        val placeNameArg = navArgs.placeName
        val placeTypeArg = navArgs.placeType
        val userPrefsManager = UserPreferencesManager.getInstance(requireContext().applicationContext)


        RecordVisitViewModelFactory(
            application,
            visitDao,
            visitIdArg,
            placeIdArg,
            placeNameArg,
            placeTypeArg,
            userPrefsManager
        )
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        viewModel.processImageUri(uri)
    }

    private val displayDateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordVisitBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupInitialUI()
        setupListeners()
        observeViewModel()

        // << --- Add MenuProvider for Save action --- >>
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.menu_record_visit, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle actions based on item ID
                return when (menuItem.itemId) {
                    R.id.action_save_visit -> {
                        viewModel.saveVisit()
                        true // Consume the event
                    }
                    else -> false // Let other components handle the event
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED) // Observe while RESUMED
        // << --- End MenuProvider --- >>

        val contentViewToPad = binding.nestedScrollViewContent // Or the parent LinearLayout if needed

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayoutRecordVisit) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the top inset as padding to the AppBarLayout
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            WindowInsetsCompat.CONSUMED // Or return the insets if you want them propagated further
        }

        ViewCompat.setOnApplyWindowInsetsListener(contentViewToPad) { v, insets ->
            val systemNavigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Start with the height of the system's own navigation bar
            var effectiveNavHeight = systemNavigationBars.bottom
            Log.d("RecordVisitInsets", "Initial systemNavigationBars.bottom: $effectiveNavHeight")

            val activityRootView = requireActivity().window.decorView
            val appBottomNavView = activityRootView.findViewById<BottomNavigationView>(R.id.main_bottom_navigation)

            if (appBottomNavView != null && appBottomNavView.visibility == View.VISIBLE) {
                Log.d("RecordVisitInsets", "App's BottomNavView found: Height=${appBottomNavView.height}, Visible=true")
                if (effectiveNavHeight < appBottomNavView.height) {
                    effectiveNavHeight = appBottomNavView.height
                    Log.d("RecordVisitInsets", "Using App's BottomNavView height ($effectiveNavHeight) as effectiveNavHeight")
                }
            } else {
                Log.d("RecordVisitInsets", "App's BottomNavView not found or not visible on this screen.")
            }

            val desiredBottomPadding = kotlin.math.max(effectiveNavHeight, imeInsets.bottom)
            Log.d("RecordVisitInsets", "IME.bottom: ${imeInsets.bottom}, EffectiveNavHeight: $effectiveNavHeight, Final desiredBottomPadding: $desiredBottomPadding")

            v.updatePadding(
                // Keep original left, top, right padding
                // Let the CoordinatorLayout and AppBarLayout handle top padding.
                bottom = desiredBottomPadding
            )
            insets
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
        Log.d("RecordVisitInsets", "Finished setting up inset handling for RecordVisitFragment.")
        // <<<<<<<<<<<< END: ADD INSET HANDLING CODE HERE >>>>>>>>>>>>>>>>
    }

    private fun setupToolbar() {
        // The main toolbar (with centered title and save action)
        val toolbar = binding.toolbarRecordVisit // Correct ID from new XML
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_record_visit) // Add back temporarily

        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayShowTitleEnabled(true)
//        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayShowTitleEnabled(false) // Important: hide default title

        // Set navigation icon listener for the close button
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupInitialUI() {
        setupNumericInputListeners(binding.editTextBaptisms, OrdinanceType.BAPTISMS)
        setupNumericInputListeners(binding.editTextConfirmations, OrdinanceType.CONFIRMATIONS)
        setupNumericInputListeners(binding.editTextInitiatories, OrdinanceType.INITIATORIES)
        setupNumericInputListeners(binding.editTextEndowments, OrdinanceType.ENDOWMENTS)
        setupNumericInputListeners(binding.editTextSealings, OrdinanceType.SEALINGS)
        setupNumericInputListeners(binding.editTextHoursWorked, isDecimal = true, step = 0.5)
    }

    private fun setupListeners() {
        binding.buttonVisitDate.setOnClickListener {
            showDatePicker()
        }

        binding.buttonFavoriteVisit.setOnClickListener {
            viewModel.onToggleFavorite()
        }

        binding.buttonAddRemovePicture.setOnClickListener {
            if (viewModel.uiState.value?.selectedImageUri == null && viewModel.uiState.value?.pictureByteArray == null) {
                imagePickerLauncher.launch("image/*")
            } else {
                viewModel.onImageSelectionCleared()
            }
        }

        binding.editTextComments.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                viewModel.onCommentsChanged(s.toString())
            }
        })
    }

    private fun setupNumericInputListeners(
        editText: EditText,
        ordinanceType: OrdinanceType? = null,
        isDecimal: Boolean = false,
        step: Double = 1.0
    ) {
        val incrementButton: View
        val decrementButton: View

        when (ordinanceType) {
            OrdinanceType.BAPTISMS -> {
                incrementButton = binding.buttonBaptismsIncrement
                decrementButton = binding.buttonBaptismsDecrement
            }
            OrdinanceType.CONFIRMATIONS -> {
                incrementButton = binding.buttonConfirmationsIncrement
                decrementButton = binding.buttonConfirmationsDecrement
            }
            OrdinanceType.INITIATORIES -> {
                incrementButton = binding.buttonInitiatoriesIncrement
                decrementButton = binding.buttonInitiatoriesDecrement
            }
            OrdinanceType.ENDOWMENTS -> {
                incrementButton = binding.buttonEndowmentsIncrement
                decrementButton = binding.buttonEndowmentsDecrement
            }
            OrdinanceType.SEALINGS -> {
                incrementButton = binding.buttonSealingsIncrement
                decrementButton = binding.buttonSealingsDecrement
            }
            null -> { // For hours worked
                incrementButton = binding.buttonHoursIncrement
                decrementButton = binding.buttonHoursDecrement
            }
        }

        val updateValue = { delta: Double ->
            try {
                var currentValue = editText.text.toString().toDoubleOrNull() ?: 0.0
                currentValue += delta
                if (currentValue < 0) currentValue = 0.0

                val newValueString = if (isDecimal) {
                    String.format(Locale.US, "%.1f", currentValue)
                } else {
                    currentValue.toInt().toString()
                }
                // Check if the current EditText value is already what we're about to set.
                // This prevents the TextWatcher from firing and potentially causing loops
                // or unwanted cursor jumps if the value is programmatically set to the same value.
                if (editText.text.toString() != newValueString) {
                    editText.setText(newValueString)
                }
                // The TextWatcher will now send the updated value to the ViewModel
            } catch (e: NumberFormatException) {
                // Ignore
            }
        }

        incrementButton.setOnClickListener { updateValue(step) }
        decrementButton.setOnClickListener { updateValue(-step) }

        editText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                // Simplified logic slightly, ensure it still prevents unwanted updates
                val stringValue = s?.toString() ?: ""
                val currentValueInVm: Any? = when(ordinanceType) {
                    OrdinanceType.BAPTISMS -> viewModel.uiState.value?.baptisms
                    OrdinanceType.CONFIRMATIONS -> viewModel.uiState.value?.confirmations
                    OrdinanceType.INITIATORIES -> viewModel.uiState.value?.initiatories
                    OrdinanceType.ENDOWMENTS -> viewModel.uiState.value?.endowments
                    OrdinanceType.SEALINGS -> viewModel.uiState.value?.sealings
                    null -> viewModel.uiState.value?.shiftHrs
                }

                val vmValueString = if (isDecimal) String.format(Locale.US, "%.1f", (currentValueInVm as? Double) ?: 0.0)
                else (currentValueInVm as? Short ?: 0).toString()

                if (stringValue == vmValueString && stringValue == (if (isDecimal) "0.0" else "0")) {
                    // Avoid redundant updates if the value is already "0" or "0.0" in both UI and VM
                    // This helps prevent loops if initial state is 0 and text watcher fires.
                } else {
                    if (ordinanceType != null) {
                        viewModel.onOrdinanceCountChanged(ordinanceType, stringValue)
                    } else { // Hours worked
                        viewModel.onHoursWorkedChanged(stringValue)
                    }
                }
            }
        })

        // Modify the existing setOnFocusChangeListener
        editText.setOnFocusChangeListener { view, hasFocus ->
            val et = view as EditText // We know it's an EditText here
            if (hasFocus) {
                // Logic for when GAINING focus
                et.post { // Post to allow layout to settle
                    et.requestRectangleOnScreen(
                        Rect(0, 0, et.width, et.height),
                        false // Don't force immediate, allow smooth scroll
                    )
                }
                et.post { // Use post for robustness
                    et.text?.let { currentText ->
                        val expectedInitialValue = if (isDecimal) "0.0" else "0" // Use isDecimal parameter
                        if (currentText.toString() == expectedInitialValue) {
                            Log.d("RecordVisitFocus", "Selecting all for ${et.idToString()}, value: '$currentText', isDecimal: $isDecimal")
                            et.selectAll()
                        } else {
                            // Optional: Move cursor to end if not the initial value
                            if (et.hasFocus()) { // Check focus again as it might change during post
                                Log.d("RecordVisitFocus", "Not initial value for ${et.idToString()}, moving cursor to end. Value: '$currentText', isDecimal: $isDecimal")
                                et.setSelection(currentText.length)
                            }
                        }
                    }
                }
            } else {
                // Existing logic for when LOSING focus
                if (et.text.isNullOrBlank()) {
                    val defaultValue = if (isDecimal) "0.0" else "0" // Use isDecimal parameter
                    Log.d("RecordVisitFocus", "Focus LOST and text is blank for ${et.idToString()}, setting to '$defaultValue', isDecimal: $isDecimal")
                    et.setText(defaultValue)
                } else {
                    // For decimal fields, ensure correct formatting on focus lost if not blank
                    if (isDecimal) {
                        try {
                            val numericValue = et.text.toString().toDouble()
                            val formattedValue = String.format(Locale.US, "%.1f", numericValue)
                            if (et.text.toString() != formattedValue) {
                                et.setText(formattedValue)
                            }
                            Log.d("RecordVisitFocus", "Focus LOST for decimal field ${et.idToString()}, text is '${et.text}'. Applied formatting if needed.")
                        } catch (e: NumberFormatException) {
                            // If not a valid number, and not blank, default it.
                            val defaultValue = String.format(Locale.US, "%.1f", 0.0)
                            Log.e("RecordVisitFocus", "Error parsing decimal on focus lost for ${et.idToString()}. Setting to '$defaultValue'. Input: '${et.text}'", e)
                            et.setText(defaultValue)
                        }
                    } else {
                        Log.d("RecordVisitFocus", "Focus LOST for non-decimal field ${et.idToString()}, text is '${et.text}'. No change.")
                    }
                }
            }
        }
    }


    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner, Observer { state ->
            if (state == null) {
                Log.w("RecordVisitFragment", "Observer: uiState is null!") // Add a log for null state too
                return@Observer
            }
            // <<< THIS LOG IS CRITICAL >>>
            Log.d("RecordVisitFragment", "Observer received uiState: Baptisms=${state.baptisms}, Confirmations=${state.confirmations}, Initiatories=${state.initiatories}, Endowments=${state.endowments}, Sealings=${state.sealings}, ShiftHrs=${state.shiftHrs}")
            updateUi(state)
        })

        viewModel.saveResultEvent.observe(viewLifecycleOwner, EventObserver { success ->
            if (success) {
                Toast.makeText(context, getString(R.string.visit_saved_success), Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } else {
                Toast.makeText(context, getString(R.string.visit_saved_error), Toast.LENGTH_SHORT).show()
            }
        })

        // isOrdinanceWorker observation can be added here if you implement that feature in ViewModel/Prefs
    }

    private fun updateUi(state: VisitUiState) {
        Log.d("RecordVisitFragment", "updateUi: placeName='${state.holyPlaceName}', placeType from UiState='${state.visitType}', navArgs.placeType='${navArgs.placeType}'")
        Log.d("RecordVisitFragment", "updateUi: Visibility: Baptisms=${binding.editTextBaptisms.visibility == View.VISIBLE}, Confirmations=${binding.editTextConfirmations.visibility == View.VISIBLE}")

        // Place Name and Color
        binding.textViewPlaceName.text = state.holyPlaceName
        Log.d("RecordVisitFragment", "navArgs.placeType: ${navArgs.placeType}")
        val placeNameColor = ColorUtils.getTextColorForTempleType(requireContext(), navArgs.placeType)
        binding.textViewPlaceName.setTextColor(placeNameColor)

        updateEditTextIfChanged(binding.editTextComments, state.comments ?: "")

        state.dateVisited?.let {
            binding.buttonVisitDate.text = displayDateFormat.format(it)
        } ?: run {
            binding.buttonVisitDate.text = getString(R.string.select_visit_date)
        }

        updateEditTextIfChanged(binding.editTextBaptisms, state.baptisms?.toString() ?: "0")
        updateEditTextIfChanged(binding.editTextConfirmations, state.confirmations?.toString() ?: "0")
        updateEditTextIfChanged(binding.editTextInitiatories, state.initiatories?.toString() ?: "0")
        updateEditTextIfChanged(binding.editTextEndowments, state.endowments?.toString() ?: "0")
        updateEditTextIfChanged(binding.editTextSealings, state.sealings?.toString() ?: "0")
        updateEditTextIfChanged(binding.editTextHoursWorked, String.format(Locale.US, "%.1f", state.shiftHrs ?: 0.0))

        // Favorite Button - use correct ID and smaller icons
        binding.buttonFavoriteVisit.setImageResource(
            if (state.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline // Use small icons
        )
        binding.buttonFavoriteVisit.contentDescription = if (state.isFavorite) getString(R.string.cd_remove_favorite) else getString(R.string.cd_add_favorite)

        // --- Conditional Visibility Logic for Temple Sections ---
        if (navArgs.placeType == "T") {
            binding.groupTempleOrdinances.visibility = View.VISIBLE // For individual ordinances

            // Now handle the "Ordinance Worker" section using the preference
            if (state.isHoursWorkedEntryEnabled) {
                binding.groupOrdinanceWorker.visibility = View.VISIBLE // Use YOUR variable name
                val hoursText = String.format(Locale.US, "%.1f", state.shiftHrs ?: 0.0)
                updateEditTextIfChanged(binding.editTextHoursWorked, hoursText)
                // If checkbox_ordinance_worker's checked state is also part of VisitUiState, set it here.
                // e.g., binding.checkboxOrdinanceWorker.isChecked = state.isMarkedAsOrdinanceWorker
            } else {
                binding.groupOrdinanceWorker.visibility = View.GONE // Use YOUR variable name
                // Optional: Visually clear hours field when the section is hidden
                // updateEditTextIfChanged(binding.editTextHoursWorked, "0.0")
            }
        } else {
            // Not a Temple: Hide both temple-specific sections
            binding.groupTempleOrdinances.visibility = View.GONE
            binding.groupOrdinanceWorker.visibility = View.GONE // Use YOUR variable name
        }
        // Image display logic using Coil
        when {
            state.pictureByteArray != null && state.pictureByteArray.isNotEmpty() -> {
                // Load the processed image from ByteArray using Coil
                binding.imageViewVisitPicture.load(state.pictureByteArray) {
//                    crossfade(true)
//                    placeholder(R.drawable.ic_menu_gallery) // Placeholder image
//                    error(R.drawable.ic_menu_camera)       // Error image
                }
                binding.imageViewVisitPicture.visibility = View.VISIBLE
                binding.buttonAddRemovePicture.text = getString(R.string.button_remove_picture)
                binding.buttonAddRemovePicture.setIconResource(R.drawable.ic_delete)
            }
            else -> { // No image selected, or it was cleared, or processing failed and nulled it
                binding.imageViewVisitPicture.setImageDrawable(null) // Clear
                binding.imageViewVisitPicture.visibility = View.GONE
                binding.buttonAddRemovePicture.text = getString(R.string.button_add_picture)
                binding.buttonAddRemovePicture.setIconResource(R.drawable.ic_add_a_photo)
            }
        }
    }

    // Helper to prevent cursor jumps and unnecessary TextWatcher firings
    private fun EditText.idToString(): String {
        return try {
            resources.getResourceEntryName(this.id)
        } catch (e: Exception) {
            this.id.toString()
        }
    }

    private fun updateEditTextIfChanged(editText: EditText, newValue: String) {
        val currentText = editText.text.toString()
        // <<< THIS LOG IS CRITICAL >>>
        Log.d("RecordVisitFragment", "updateEditTextIfChanged for ID '${editText.idToString()}': CurrentText='$currentText', NewValue='$newValue'")
        if (currentText != newValue) {
            Log.d("RecordVisitFragment", "updateEditTextIfChanged for ID '${editText.idToString()}': SETTING TEXT to '$newValue'")
            editText.setText(newValue)
            // editText.setSelection(newValue.length) // Optional
        } else {
            Log.d("RecordVisitFragment", "updateEditTextIfChanged for ID '${editText.idToString()}': NO CHANGE needed.")
        }
    }

    private fun showDatePicker() {
        val currentSelection = viewModel.uiState.value?.dateVisited?.time ?: MaterialDatePicker.todayInUtcMilliseconds()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_visit_date))
            .setSelection(currentSelection)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectedUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            selectedUtc.timeInMillis = selection

            val localCalendar = Calendar.getInstance()
            localCalendar.set(
                selectedUtc.get(Calendar.YEAR),
                selectedUtc.get(Calendar.MONTH),
                selectedUtc.get(Calendar.DAY_OF_MONTH),
                12, 0, 0 // Noon to avoid timezone issues near midnight
            )
            localCalendar.set(Calendar.MILLISECOND, 0)

            viewModel.onDateChanged(localCalendar.time)
        }
        datePicker.show(childFragmentManager, "DATE_PICKER_TAG")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Helper for simpler TextWatcher (move to a common utils file if used elsewhere)
abstract class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    abstract override fun afterTextChanged(s: Editable?)
}

// Helper for observing LiveData events (move to a common utils file if used elsewhere)
class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(value: Event<T>) {
        value.getContentIfNotHandled()?.let {
            onEventUnhandledContent(it)
        }
    }
}

