package net.dacworld.android.holyplacesofthelord.ui.places // Or your preferred package

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.databinding.ItemTempleBinding // View Binding
import net.dacworld.android.holyplacesofthelord.util.ColorUtils // Import ColorUtils

class TempleAdapter(private val onItemClicked: (Temple) -> Unit) :
    ListAdapter<Temple, TempleAdapter.TempleViewHolder>(TempleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TempleViewHolder {
        val binding = ItemTempleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TempleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TempleViewHolder, position: Int) {
        val currentTemple = getItem(position)
        Log.d("TempleAdapter", "onBindViewHolder - Position: $position, Temple: ${currentTemple?.name}") // <<< VERY IMPORTANT LOG
        holder.bind(currentTemple) // Assuming a bind method in ViewHolder
        // Set the click listener on the item view
        holder.itemView.setOnClickListener {
            onItemClicked(currentTemple)
        }
    }

    class TempleViewHolder(private val binding: ItemTempleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(temple: Temple) {
            binding.templeNameTextView.text = temple.name
            binding.templeSnippetTextView.text = temple.snippet

            // Use the helper function
            val nameColor = ColorUtils.getTextColorForTempleType(binding.root.context, temple.type)
            binding.templeNameTextView.setTextColor(nameColor)
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