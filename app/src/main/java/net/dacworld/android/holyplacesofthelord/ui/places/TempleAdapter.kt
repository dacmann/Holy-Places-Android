package net.dacworld.android.holyplacesofthelord.ui.places // Or your preferred package

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.databinding.ItemTempleBinding // View Binding

class TempleAdapter : ListAdapter<Temple, TempleAdapter.TempleViewHolder>(TempleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TempleViewHolder {
        val binding = ItemTempleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TempleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TempleViewHolder, position: Int) {
        val currentTemple = getItem(position)
        Log.d("TempleAdapter", "onBindViewHolder - Position: $position, Temple: ${currentTemple?.name}") // <<< VERY IMPORTANT LOG
        holder.bind(currentTemple) // Assuming a bind method in ViewHolder
    }

    class TempleViewHolder(private val binding: ItemTempleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(temple: Temple) {
            if (temple == null) {
                Log.e("TempleViewHolder", "bind() called with null temple!") // Should not happen with ListAdapter if DiffUtil is correct
                return
            }
            Log.d("TempleViewHolder", "Binding data for: ${temple.name}") // <<< IMPORTANT LOG
            Log.d("TempleViewHolder", "  Name TextView: ${binding.templeNameTextView}") // Check if TextView is null
            Log.d("TempleViewHolder", "  Location TextView: ${binding.templeSnippetTextView}")

            binding.templeNameTextView.text = temple.name
            binding.templeSnippetTextView.text = temple.snippet
        }
    }
}

class TempleDiffCallback : DiffUtil.ItemCallback<Temple>() {
    override fun areItemsTheSame(oldItem: Temple, newItem: Temple): Boolean {
        return oldItem.id == newItem.id // Assuming 'id' is a unique identifier
    }

    override fun areContentsTheSame(oldItem: Temple, newItem: Temple): Boolean {
        return oldItem == newItem // Relies on your Temple data class having a proper equals()
    }
}