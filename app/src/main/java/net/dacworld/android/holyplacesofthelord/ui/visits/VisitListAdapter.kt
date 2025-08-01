// ui/visits/VisitListAdapter.kt
package net.dacworld.android.holyplacesofthelord.ui.visits

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.R // For color resources
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.databinding.ListItemVisitBinding
import java.text.SimpleDateFormat
import java.util.Locale
import net.dacworld.android.holyplacesofthelord.util.ColorUtils

class VisitListAdapter(
    private val onVisitClicked: (Visit) -> Unit
) : ListAdapter<Visit, VisitListAdapter.VisitViewHolder>(VisitDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VisitViewHolder {
        val binding = ListItemVisitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VisitViewHolder(binding, parent.context)
    }

    override fun onBindViewHolder(holder: VisitViewHolder, position: Int) {
        val visit = getItem(position)
        holder.bind(visit)
        holder.itemView.setOnClickListener {
            onVisitClicked(visit)
        }
    }

    class VisitViewHolder(
        private val binding: ListItemVisitBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())

        fun bind(visit: Visit) {
            binding.visitItemPlaceName.text = visit.holyPlaceName ?: context.getString(R.string.unknown)

            val dateString = visit.dateVisited?.let {
                dateFormatter.format(it)
            } ?: context.getString(R.string.unknown)

            // Construct Ordinance Summary
            val ordinances = StringBuilder()
            if (visit.baptisms != null && visit.baptisms > 0) ordinances.append(" B")
            if (visit.confirmations != null && visit.confirmations > 0) ordinances.append(" C")
            if (visit.initiatories != null && visit.initiatories > 0) ordinances.append(" I")
            if (visit.endowments != null && visit.endowments > 0) ordinances.append(" E")
            if (visit.sealings != null && visit.sealings > 0) ordinances.append(" S")
            if (visit.shiftHrs != null && visit.shiftHrs > 0) ordinances.append(" ${visit.shiftHrs} hrs")

            val summaryText = ordinances.toString().trim() // Get the summary part
            val combinedDateAndOrdinances: String

            if (summaryText.isNotEmpty()) {
                // If the dateString is "Unknown", we don't want to add an extra space before "~"
                val separator = if (dateString == context.getString(R.string.unknown)) "~" else " ~ "
                combinedDateAndOrdinances = "$dateString$separator$summaryText"
            } else {
                combinedDateAndOrdinances = dateString // Only date if no ordinances
            }

            // Append picture and favorite indicators from original logic to the combined string
            val indicators = StringBuilder()
            if (visit.picture != null) { // Assuming picture is ByteArray?, non-null means there's a picture
                indicators.append("  \uD83D\uDCF7") // Camera emoji 📷
            }
            if (visit.isFavorite) {
                indicators.append("   ⭐") // Star emoji
            }

            binding.visitItemDate.text = "$combinedDateAndOrdinances${indicators.toString().trimEnd()}" // Append indicators



            Log.d("VisitListAdapter", "Binding visit: '${visit.holyPlaceName}', Type: '${visit.type}'") // <-- ADD THIS

            // Set text color based on temple type
            val typeColor = ColorUtils.getTextColorForTempleType(context, visit.type)
            binding.visitItemPlaceName.setTextColor(typeColor)

            Log.d("VisitListAdapter", "Applied color for type '${visit.type}': $typeColor to '${visit.holyPlaceName}'") // <-- Optional: Log applied color

        }
    }
}

class VisitDiffCallback : DiffUtil.ItemCallback<Visit>() {
    override fun areItemsTheSame(oldItem: Visit, newItem: Visit): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Visit, newItem: Visit): Boolean {
        return oldItem == newItem // Relies on Visit being a data class or having a proper equals()
    }
}
