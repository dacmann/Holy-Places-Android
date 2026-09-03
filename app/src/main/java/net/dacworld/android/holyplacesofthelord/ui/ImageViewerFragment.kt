package net.dacworld.android.holyplacesofthelord.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import coil.load
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentImageViewerBinding

class ImageViewerFragment : Fragment() {

    private var _binding: FragmentImageViewerBinding? = null
    private val binding get() = _binding!!

    private val imageViewerViewModel: ImageViewerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageUrl = imageViewerViewModel.imageUrl
            ?: arguments?.getString("image_url")
        val imageBytes = imageViewerViewModel.imageBytes
        val imageDataBase64 = arguments?.getString("image_data_base64")

        when {
            imageBytes != null && imageBytes.isNotEmpty() -> {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    binding.photoView.setImageBitmap(bitmap)
                } else {
                    binding.photoView.load(R.drawable.ic_error)
                }
            }
            !imageUrl.isNullOrEmpty() -> {
                binding.photoView.load(imageUrl) {
                    error(R.drawable.ic_error)
                }
            }
            !imageDataBase64.isNullOrEmpty() -> {
                loadBase64Image(imageDataBase64)
            }
            else -> {
                binding.photoView.load(R.drawable.ic_error)
            }
        }

        binding.closeButton.bringToFront()
        binding.closeButton.setOnClickListener { closeViewer() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    closeViewer()
                }
            }
        )
    }

    private fun closeViewer() {
        imageViewerViewModel.clear()
        val overlayFm = requireActivity().supportFragmentManager
        if (overlayFm.popBackStackImmediate(BACK_STACK_NAME, FragmentManager.POP_BACK_STACK_INCLUSIVE)) {
            return
        }
        if (overlayFm.fragments.any { it === this }) {
            overlayFm.beginTransaction().remove(this).commit()
            return
        }
        try {
            findNavController().navigateUp()
        } catch (_: IllegalStateException) {
            overlayFm.beginTransaction().remove(this).commit()
        }
    }

    private fun loadBase64Image(base64String: String) {
        try {
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            binding.photoView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            binding.photoView.load(R.drawable.ic_error)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val BACK_STACK_NAME = "image_viewer"

        fun newInstance(): ImageViewerFragment = ImageViewerFragment()
    }
}
