// ui/visits/VisitListAdapter.kt
package net.dacworld.android.holyplacesofthelord.ui.visits

import android.content.Context
import android.text.format.DateUtils
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

            binding.visitItemDate.text = visit.dateVisited?.let {
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

            if (ordinances.isNotEmpty()) {
                ordinances.insert(0, "~") // Add tilde if there are ordinances
            }

            if (visit.picture != null) { // Assuming picture is ByteArray?, non-null means there's a picture
                ordinances.append("  \uD83D\uDCF7") // Camera emoji 📷
            }
            if (visit.isFavorite) {
                ordinances.append("   ⭐") // Star emoji
            }
            binding.visitItemOrdinances.text = ordinances.toString().trim()


            // Set text color based on temple type
            val typeColor = when (visit.type) {
                "T" -> ContextCompat.getColor(context, R.color.t2_temples)
                "H" -> ContextCompat.getColor(context, R.color.t2_historic_site)
                "A" -> ContextCompat.getColor(context, R.color.t2_announced_temples)
                "C" -> ContextCompat.getColor(context, R.color.t2_under_construction)
                "V" -> ContextCompat.getColor(context, R.color.t2_visitors_centers)
                else -> ContextCompat.getColor(context, R.color.grey_text) // Replace with your actual color
            }
            binding.visitItemPlaceName.setTextColor(typeColor)
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
