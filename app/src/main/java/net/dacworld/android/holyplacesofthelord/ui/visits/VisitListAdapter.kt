// ui/visits/VisitListAdapter.kt
package net.dacworld.android.holyplacesofthelord.ui.visits

import android.content.Context
import android.text.format.DateUtils // Keep this if used by VisitRowItem binding
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.model.Visit // Keep for VisitRowItem
// --- 1. IMPORT VisitDisplayListItem and its members ---
import net.dacworld.android.holyplacesofthelord.data.VisitDisplayListItem
// --- 2. IMPORT the binding for the header ---
import net.dacworld.android.holyplacesofthelord.databinding.ItemSectionHeaderBinding
import net.dacworld.android.holyplacesofthelord.databinding.ListItemVisitBinding
import java.text.SimpleDateFormat
import java.util.Locale
import net.dacworld.android.holyplacesofthelord.util.ColorUtils


// --- 3. MODIFY ListAdapter generic types ---
class VisitListAdapter(
    private val onVisitClicked: (Visit) -> Unit // Click listener might only apply to VisitRowItems
    // If you need clicks on headers, you'd add another lambda or differentiate
) : ListAdapter<VisitDisplayListItem, RecyclerView.ViewHolder>(VisitDiffCallback()) {

    // --- 4. DEFINE View Type Constants ---
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_VISIT_ROW = 1
    }

    // --- 5. IMPLEMENT getItemViewType ---
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is VisitDisplayListItem.HeaderItem -> VIEW_TYPE_HEADER
            is VisitDisplayListItem.VisitRowItem -> VIEW_TYPE_VISIT_ROW
            // It's good practice to handle null if your list can somehow contain nulls,
            // though ListAdapter with DiffUtil usually prevents this if data is clean.
            // else -> throw IllegalArgumentException("Unknown item type at position $position")
        }
    }

    // --- 6. UPDATE onCreateViewHolder ---
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemSectionHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_VISIT_ROW -> {
                val binding = ListItemVisitBinding.inflate(inflater, parent, false)
                // Pass context to VisitViewHolder if it still needs it
                VisitViewHolder(binding, parent.context, onVisitClicked)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    // --- 7. UPDATE onBindViewHolder ---
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> {
                if (item is VisitDisplayListItem.HeaderItem) {
                    holder.bind(item)
                }
            }
            is VisitViewHolder -> {
                if (item is VisitDisplayListItem.VisitRowItem) {
                    holder.bind(item.visit) // Pass the actual Visit object
                    // The setOnClickListener was here, ensure it's handled in VisitViewHolder if it still needs to be per-item
                }
            }
        }
    }

    // --- 8. CREATE HeaderViewHolder (mirroring PlaceDisplayAdapter) ---
    class HeaderViewHolder(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(headerItem: VisitDisplayListItem.HeaderItem) {
            binding.sectionHeaderTextView.text = "${headerItem.title} (${headerItem.count})"
        }
    }

    // --- VisitViewHolder (now takes onVisitClicked) ---
    // Make sure VisitViewHolder is adapted to take 'onVisitClicked' if the click logic is complex
    // or remains inside it.
    class VisitViewHolder(
        private val binding: ListItemVisitBinding,
        private val context: Context,
        private val onVisitClicked: (Visit) -> Unit // Added for click handling
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())

        fun bind(visit: Visit) { // Takes a Visit object directly
            binding.visitItemPlaceName.text = visit.holyPlaceName ?: context.getString(R.string.unknown)

            val dateString = visit.dateVisited?.let {
                dateFormatter.format(it)
            } ?: context.getString(R.string.unknown)

            val ordinances = StringBuilder()
            if (visit.baptisms != null && visit.baptisms > 0) ordinances.append(" B")
            if (visit.confirmations != null && visit.confirmations > 0) ordinances.append(" C")
            if (visit.initiatories != null && visit.initiatories > 0) ordinances.append(" I")
            if (visit.endowments != null && visit.endowments > 0) ordinances.append(" E")
            if (visit.sealings != null && visit.sealings > 0) ordinances.append(" S")
            if (visit.shiftHrs != null && visit.shiftHrs > 0) ordinances.append(" ${visit.shiftHrs} hrs")

            val summaryText = ordinances.toString().trim()
            val combinedDateAndOrdinances: String

            if (summaryText.isNotEmpty()) {
                val separator = if (dateString == context.getString(R.string.unknown)) "~" else " ~ "
                combinedDateAndOrdinances = "$dateString$separator$summaryText"
            } else {
                combinedDateAndOrdinances = dateString
            }

            val indicators = StringBuilder()
            if (visit.picture != null) {
                indicators.append("  \uD83D\uDCF7")
            }
            if (visit.isFavorite) {
                indicators.append("   ⭐")
            }

            binding.visitItemDate.text = "$combinedDateAndOrdinances${indicators.toString().trimEnd()}"

            Log.d("VisitListAdapter", "Binding visit: '${visit.holyPlaceName}', Type: '${visit.type}'")

            val typeColor = ColorUtils.getTextColorForTempleType(context, visit.type)
            binding.visitItemPlaceName.setTextColor(typeColor)

            Log.d("VisitListAdapter", "Applied color for type '${visit.type}': $typeColor to '${visit.holyPlaceName}'")

            // --- Moved click listener setup into bind to have access to the specific 'visit' item ---
            itemView.setOnClickListener {
                onVisitClicked(visit)
            }
        }
    }
}

// --- 9. UPDATE VisitDiffCallback ---
class VisitDiffCallback : DiffUtil.ItemCallback<VisitDisplayListItem>() {
    override fun areItemsTheSame(oldItem: VisitDisplayListItem, newItem: VisitDisplayListItem): Boolean {
        // Use the stableId we defined in VisitDisplayListItem
        return oldItem.stableId == newItem.stableId
    }

    override fun areContentsTheSame(oldItem: VisitDisplayListItem, newItem: VisitDisplayListItem): Boolean {
        // For data classes, '==' checks structural equality.
        // This is fine if HeaderItem and VisitRowItem's contents changing means they are "different".
        return oldItem == newItem
    }
}

