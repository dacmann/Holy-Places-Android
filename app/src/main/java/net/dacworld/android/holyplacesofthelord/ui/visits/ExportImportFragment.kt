package net.dacworld.android.holyplacesofthelord.ui.visits // Or your fragment's package

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentExportImportBinding
import net.dacworld.android.holyplacesofthelord.data.ExportImportViewModel
import net.dacworld.android.holyplacesofthelord.data.OperationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportImportFragment : Fragment() {

    private var _binding: FragmentExportImportBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView.

    private val viewModel: ExportImportViewModel by viewModels()

    private lateinit var createDocumentLauncher: ActivityResultLauncher<String>
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInsetHandling()
        setupToolbar()
        setupLaunchers()
        setupClickListeners()
        observeViewModel()
    }

    // In ExportImportFragment.kt

    private fun setupInsetHandling() {
        Log.d("ExportImportInsets", "setupInsetHandling CALLED")

        val toolbarToAdjust = binding.exportImportToolbar // Renamed for clarity
        Log.d("ExportImportInsets", "Toolbar reference: $toolbarToAdjust")

        ViewCompat.setOnApplyWindowInsetsListener(toolbarToAdjust) { v, windowInsets ->
            Log.d("ExportImportInsets", "Toolbar Listener: Insets received: ${windowInsets.toString()}")
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            Log.d("ExportImportInsets", "Toolbar Listener: SystemBars top: ${systemBars.top}")

            val params = v.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.topMargin = systemBars.top
                v.layoutParams = params // Apply the changed LayoutParams
                Log.d("ExportImportInsets", "Toolbar Listener: Applied TOP margin: ${systemBars.top}. OriginalPaddingTop was: ${v.paddingTop}")
            } else {
                Log.e("ExportImportInsets", "Toolbar Listener: Could not get MarginLayoutParams for Toolbar. Margin not applied.")
            }

            // It's good practice to consume the insets you've handled if appropriate,
            // but for this simple case, just returning the original insets is often fine.
            windowInsets
        }

        // You might still want bottom padding for the root content if you have elements near the navigation bar
        val viewToPadBottom = binding.exportImportRoot // Assuming export_import_root is your root ConstraintLayout
        ViewCompat.setOnApplyWindowInsetsListener(viewToPadBottom) { v, windowInsets ->
            val navigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navigationBars.bottom) // Apply bottom padding for nav bar
            Log.d("ExportImportInsets", "RootView (${v.idToString()}): Applied BOTTOM padding: ${navigationBars.bottom}. New paddingBottom: ${v.paddingBottom}")
            windowInsets
        }

        requestApplyInsetsWhenAttached(toolbarToAdjust)
        requestApplyInsetsWhenAttached(viewToPadBottom)
    }

    // Ensure you have these helper methods in your fragment:
    private fun requestApplyInsetsWhenAttached(view: View?) {
        if (view == null) {
            Log.e("ExportImportInsets", "requestApplyInsetsWhenAttached: View is null!")
            return
        }
        if (view.isAttachedToWindow) {
            Log.d("ExportImportInsets", "View ${view.idToString()} already attached. Requesting insets.")
            ViewCompat.requestApplyInsets(view)
        } else {
            Log.d("ExportImportInsets", "View ${view.idToString()} not attached. Adding listener to request insets on attach.")
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    Log.d("ExportImportInsets", "View ${v.idToString()} attached. Requesting insets.")
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }

    fun View.idToString(): String { // Can be an extension function or a private fun in the fragment
        return try {
            resources.getResourceEntryName(id)
        } catch (e: Exception) {
            "no_id" // Or any other placeholder
        }
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.exportImportToolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_export_import)
        // Handle Up navigation
        binding.exportImportToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupLaunchers() {
        // Launcher for creating a document (Export)
        createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/xml")
        ) { uri: Uri? ->
            uri?.let {
                viewModel.exportVisitsToXml(it)
            }
        }

        // Launcher for opening a document (Import)
        openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                viewModel.importVisitsFromXml(it)
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonExportXml.setOnClickListener {
            val simpleDateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val dateString = simpleDateFormat.format(Date())
            val suggestedName = getString(R.string.default_export_filename_template, dateString)
            createDocumentLauncher.launch(suggestedName)
        }

        binding.buttonImportXml.setOnClickListener {
            // Launch with both common XML MIME types
            openDocumentLauncher.launch(
                arrayOf(
                    "application/xml",
                    "text/xml"
                )
            )
        }
    }

    private fun observeViewModel() {
        viewModel.operationStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is OperationStatus.Idle -> {
                    binding.progressbarOperation.visibility = View.GONE
                    binding.textviewStatus.text = ""
                    setButtonsEnabled(true)
                }
                is OperationStatus.InProgress -> {
                    binding.progressbarOperation.visibility = View.VISIBLE
                    binding.textviewStatus.text = if (binding.buttonExportXml.isPressed || binding.buttonImportXml.isPressed) { // A bit of a hack to guess operation
                        getString(R.string.status_exporting) // Default to exporting, or refine this
                    } else {
                        getString(R.string.status_importing)
                    }
                    // More accurately: you could have separate LiveData for export/import state
                    // or pass operation type to ViewModel and reflect it back.
                    // For now, this is a simple guess. The ViewModel will update with final message.
                    setButtonsEnabled(false)
                }
                is OperationStatus.Success -> {
                    binding.progressbarOperation.visibility = View.GONE
                    binding.textviewStatus.text = status.message
                    Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                    setButtonsEnabled(true)
                    viewModel.resetOperationStatus()
                }
                is OperationStatus.Error -> {
                    binding.progressbarOperation.visibility = View.GONE
                    binding.textviewStatus.text = status.message
                    Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                    setButtonsEnabled(true)
                    viewModel.resetOperationStatus() // Reset
                }
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.buttonExportXml.isEnabled = enabled
        binding.buttonImportXml.isEnabled = enabled
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear the binding reference
    }
}
