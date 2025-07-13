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

            // --- Logic to change text color based on temple.type (String from Firestore) ---
            val context = binding.root.context

            // Using the single character codes from your Swift project
            val nameColor = when (temple.type) { // temple.type is a String like "T", "H", etc.
                "T" -> ContextCompat.getColor(context, R.color.t1_temples)
                "H" -> ContextCompat.getColor(context, R.color.t1_historic_site)
                "A" -> ContextCompat.getColor(context, R.color.t1_announced_temples)
                "C" -> ContextCompat.getColor(context, R.color.t1_under_construction)
                "V" -> ContextCompat.getColor(context, R.color.t1_visitors_centers)
                else -> {
                    Log.w("TempleViewHolder", "Unknown temple type code: '${temple.type}' for temple: ${temple.name}")
                    ContextCompat.getColor(context, R.color.app_colorOnSurface)
                }
            }
            binding.templeNameTextView.setTextColor(nameColor)
            // --- End of color change logic ---
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