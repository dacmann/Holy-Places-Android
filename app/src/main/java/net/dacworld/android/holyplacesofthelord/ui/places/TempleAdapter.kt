package net.dacworld.android.holyplacesofthelord.ui.places // Or your preferred package

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
        val temple = getItem(position)
        holder.bind(temple)
    }

    class TempleViewHolder(private val binding: ItemTempleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(temple: Temple) {
            binding.templeNameTextView.text = temple.name
            binding.templeLocationTextView.text = "${temple.cityState}, ${temple.country}"
            // Add more bindings for other Temple properties you want to display
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