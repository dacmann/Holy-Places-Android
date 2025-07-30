package net.dacworld.android.holyplacesofthelord.ui.visits

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isNotEmpty
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.observe
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

        RecordVisitViewModelFactory(
            application,
            visitDao,
            visitIdArg,
            placeIdArg,
            placeNameArg,
            placeTypeArg
        )
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        viewModel.onImageSelected(uri)
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
        setupMenu()
        setupInitialUI()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Title can be dynamic based on whether it's a new or existing visit
        // This is handled in observeViewModel -> updateUi based on navArgs or ViewModel state.
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_record_visit, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_save_visit -> {
                        viewModel.saveVisit()
                        true
                    }
                    android.R.id.home -> {
                        findNavController().navigateUp()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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

        binding.buttonFavorite.setOnClickListener {
            viewModel.onToggleFavorite()
        }

        binding.buttonAddRemovePicture.setOnClickListener {
            if (viewModel.uiState.value?.selectedImageUri == null && viewModel.uiState.value?.pictureByteArray == null) {
                imagePickerLauncher.launch("image/*")
            } else {
                viewModel.onImageSelected(null) // Signal removal
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
                if (s.toString() == (if (isDecimal) String.format(Locale.US, "%.1f", 0.0) else "0") &&
                    (viewModel.uiState.value == null ||
                            (ordinanceType == OrdinanceType.BAPTISMS && viewModel.uiState.value?.baptisms == 0.toShort()) ||
                            (ordinanceType == OrdinanceType.CONFIRMATIONS && viewModel.uiState.value?.confirmations == 0.toShort()) ||
                            (ordinanceType == OrdinanceType.INITIATORIES && viewModel.uiState.value?.initiatories == 0.toShort()) ||
                            (ordinanceType == OrdinanceType.ENDOWMENTS && viewModel.uiState.value?.endowments == 0.toShort()) ||
                            (ordinanceType == OrdinanceType.SEALINGS && viewModel.uiState.value?.sealings == 0.toShort()) ||
                            (ordinanceType == null && viewModel.uiState.value?.shiftHrs == 0.0))) {
                    // Avoid sending "0" or "0.0" if it's already that in VM or it's the initial state,
                    // mostly to prevent loops if uiState initially populates it as "0"
                    // and then the text watcher immediately sends "0" back.
                    // This can be refined. The core idea is that user-initiated changes are what we want to send.
                }

                if (ordinanceType != null) {
                    viewModel.onOrdinanceCountChanged(ordinanceType, s.toString())
                } else { // Hours worked
                    viewModel.onHoursWorkedChanged(s.toString())
                }
            }
        })

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && editText.text.isNullOrBlank()) {
                editText.setText(if (isDecimal) "0.0" else "0")
            }
        }
    }


    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner, Observer { state ->
            if (state == null) return@Observer
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
        // Set Toolbar Title
        binding.toolbar.title = if (navArgs.visitId != -1L && navArgs.visitId != 0L) {
            getString(R.string.title_edit_visit)
        } else {
            getString(R.string.title_record_visit)
        }

        binding.textViewPlaceName.text = state.holyPlaceName

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

        binding.buttonFavorite.setImageResource(
            if (state.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        binding.buttonFavorite.contentDescription = if (state.isFavorite) getString(R.string.cd_remove_favorite) else getString(R.string.cd_add_favorite)

        // Image display logic using Coil
        when {
            state.selectedImageUri != null -> { // User just picked an image
                binding.imageViewVisitPicture.load(state.selectedImageUri) {
                    //crossfade(true)
                    //placeholder(R.drawable.ic_placeholder_image) // Optional placeholder
                    //error(R.drawable.ic_broken_image) // Optional error drawable
                }
                binding.imageViewVisitPicture.visibility = View.VISIBLE
                binding.buttonAddRemovePicture.text = getString(R.string.button_remove_picture)
                binding.buttonAddRemovePicture.setIconResource(R.drawable.ic_delete)
            }
            state.pictureByteArray != null && state.pictureByteArray.isNotEmpty() -> { // Existing image from DB
                binding.imageViewVisitPicture.load(state.pictureByteArray) {
                    //crossfade(true)
                    //placeholder(R.drawable.ic_placeholder_image)
                    //error(R.drawable.ic_broken_image)
                }
                binding.imageViewVisitPicture.visibility = View.VISIBLE
                binding.buttonAddRemovePicture.text = getString(R.string.button_remove_picture) // Or "Change Picture"
                binding.buttonAddRemovePicture.setIconResource(R.drawable.ic_delete) // Or "ic_edit"
            }
            else -> { // No image
                binding.imageViewVisitPicture.setImageURI(null) // Clear
                binding.imageViewVisitPicture.visibility = View.GONE
                binding.buttonAddRemovePicture.text = getString(R.string.button_add_picture)
                binding.buttonAddRemovePicture.setIconResource(R.drawable.ic_add_a_photo)
            }
        }

        // Visibility of temple ordinance group based on initial placeType passed to ViewModel
        if (navArgs.placeType != "T") { // Assuming "T" is for Temple, from navArgs
            binding.groupTempleOrdinances.visibility = View.GONE
        } else {
            binding.groupTempleOrdinances.visibility = View.VISIBLE
        }
    }

    private fun updateEditTextIfChanged(editText: EditText, newValue: String) {
        if (editText.text.toString() != newValue) {
            editText.setText(newValue)
            // Consider moving cursor to end only if the focus is not on this editText
            // or if the change is significant, to avoid disrupting user typing.
            // For programmatic updates like this, usually setting text is enough.
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
