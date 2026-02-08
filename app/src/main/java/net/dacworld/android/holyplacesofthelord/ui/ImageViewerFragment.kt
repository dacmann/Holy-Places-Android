package net.dacworld.android.holyplacesofthelord.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import coil.load
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.FragmentImageViewerBinding

class ImageViewerFragment : Fragment() {

    private var _binding: FragmentImageViewerBinding? = null
    private val binding get() = _binding!!

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

        val imageUrl = arguments?.getString("image_url")
        val imageDataBase64 = arguments?.getString("image_data_base64")
        
        when {
            !imageUrl.isNullOrEmpty() -> {
                // Load URL image
                binding.photoView.load(imageUrl) {
                    error(R.drawable.ic_error)
                }
            }
            !imageDataBase64.isNullOrEmpty() -> {
                // Load Base64 image
                loadBase64Image(imageDataBase64)
            }
            else -> {
                // No image provided
                binding.photoView.load(R.drawable.ic_error)
            }
        }

        // Set up close button
        binding.closeButton.setOnClickListener {
            findNavController().navigateUp()
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
}