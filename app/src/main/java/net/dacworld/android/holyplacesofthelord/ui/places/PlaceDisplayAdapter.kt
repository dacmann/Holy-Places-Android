package net.dacworld.android.holyplacesofthelord.ui.places // Or your preferred package

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.databinding.ItemTempleBinding // View Binding
import net.dacworld.android.holyplacesofthelord.util.ColorUtils // Import ColorUtils
// Import your ItemSectionHeaderBinding if you have created it (we'll do this soon)
// import net.dacworld.android.holyplacesofthelord.databinding.ItemSectionHeaderBinding

// Renaming for clarity, though you can keep TempleAdapter if you prefer
class PlaceDisplayAdapter(private val onItemClicked: (Temple) -> Unit) :
    ListAdapter<DisplayListItem, RecyclerView.ViewHolder>(ListItemDiffCallback()) { // Changed here

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_TEMPLE_ROW = 1
    }

    // ViewHolder for Header items
    class HeaderViewHolder(
        // private val binding: ItemSectionHeaderBinding // We'll create this layout soon
        // For now, let's assume a simple TextView in the binding, or just pass the view
        private val binding: net.dacworld.android.holyplacesofthelord.databinding.ItemSectionHeaderBinding // Use the actual binding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(headerItem: DisplayListItem.HeaderItem) {
            // binding.headerTitle.text = "${headerItem.title} (${headerItem.count})" // Example
            binding.sectionHeaderTextView.text = "${headerItem.title} (${headerItem.count})" // Adjust to your header layout's TextView ID
        }
    }

    // Your existing TempleViewHolder, slightly adapted (mostly unchanged)
    class TempleRowViewHolder( // Renamed for clarity
        private val binding: ItemTempleBinding,
        private val onItemClickedCallback: (Temple) -> Unit // Renamed param for clarity
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(temple: Temple) {
            binding.templeNameTextView.text = temple.name
            binding.templeSnippetTextView.text = temple.snippet // Assuming snippet is still relevant

            val nameColor = ColorUtils.getTextColorForTempleType(binding.root.context, temple.type)
            binding.templeNameTextView.setTextColor(nameColor)

            itemView.setOnClickListener {
                onItemClickedCallback(temple)
            }
        }
    }
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DisplayListItem.HeaderItem -> VIEW_TYPE_HEADER
            is DisplayListItem.TempleRowItem -> VIEW_TYPE_TEMPLE_ROW
            // null can happen with ListAdapter during diffing, default to an existing type or handle carefully
            null -> VIEW_TYPE_TEMPLE_ROW // Or throw exception, or have a default placeholder type
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                // You'll need to create this layout file: res/layout/item_section_header.xml
                val binding = net.dacworld.android.holyplacesofthelord.databinding.ItemSectionHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_TEMPLE_ROW -> {
                val binding = ItemTempleBinding.inflate(inflater, parent, false)
                // Pass the onItemClicked lambda to the ViewHolder
                TempleRowViewHolder(binding, onItemClicked)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentItem = getItem(position) // This is now a DisplayListItem
        // Log.d("PlaceDisplayAdapter", "onBindViewHolder - Position: $position, Item: $currentItem")

        when (holder.itemViewType) { // Use itemViewType to be safe
            VIEW_TYPE_HEADER -> {
                (holder as HeaderViewHolder).bind(currentItem as DisplayListItem.HeaderItem)
            }
            VIEW_TYPE_TEMPLE_ROW -> {
                (holder as TempleRowViewHolder).bind((currentItem as DisplayListItem.TempleRowItem).temple)
                // Note: The click listener is now set inside TempleRowViewHolder's bind method
            }
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

// New DiffUtil.ItemCallback for DisplayListItem
class ListItemDiffCallback : DiffUtil.ItemCallback<DisplayListItem>() {
    override fun areItemsTheSame(oldItem: DisplayListItem, newItem: DisplayListItem): Boolean {
        return when {
            oldItem is DisplayListItem.HeaderItem && newItem is DisplayListItem.HeaderItem ->
                oldItem.title == newItem.title // Assuming title is a unique identifier for headers
            oldItem is DisplayListItem.TempleRowItem && newItem is DisplayListItem.TempleRowItem ->
                oldItem.temple.id == newItem.temple.id // Use unique ID from your Temple model
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: DisplayListItem, newItem: DisplayListItem): Boolean {
        return oldItem == newItem // Relies on data classes implementing equals() correctly
    }
}
sealed class DisplayListItem {
    data class HeaderItem(val title: String, val count: Int) : DisplayListItem()
    data class TempleRowItem(val temple: Temple) : DisplayListItem()
}