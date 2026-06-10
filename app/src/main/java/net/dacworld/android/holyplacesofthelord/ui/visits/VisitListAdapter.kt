// ui/visits/VisitListAdapter.kt
package net.dacworld.android.holyplacesofthelord.ui.visits

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.data.VisitDisplayListItem
import net.dacworld.android.holyplacesofthelord.databinding.ItemSectionHeaderBinding
import net.dacworld.android.holyplacesofthelord.databinding.ListItemVisitBinding
import java.text.SimpleDateFormat
import java.util.Locale
import net.dacworld.android.holyplacesofthelord.util.ColorUtils


class VisitListAdapter(
    private val onVisitClicked: (Visit) -> Unit,
    private val onSelectionChanged: (() -> Unit)? = null
) : ListAdapter<VisitDisplayListItem, RecyclerView.ViewHolder>(VisitDiffCallback()) {

    private val _selectedIds = mutableSetOf<Long>()
    var isMultiSelectMode: Boolean = false
        private set

    fun enterMultiSelectMode() {
        isMultiSelectMode = true
        notifyDataSetChanged()
    }

    fun toggleSelection(visitId: Long) {
        if (_selectedIds.contains(visitId)) _selectedIds.remove(visitId)
        else _selectedIds.add(visitId)
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun clearSelection() {
        isMultiSelectMode = false
        _selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun getVisibleVisitIds(): List<Long> =
        currentList.filterIsInstance<VisitDisplayListItem.VisitRowItem>().map { it.visit.id }

    fun selectAllVisible() {
        val visibleIds = getVisibleVisitIds()
        if (visibleIds.isEmpty()) return
        val allSelected = visibleIds.all { _selectedIds.contains(it) }
        if (allSelected) {
            visibleIds.forEach { _selectedIds.remove(it) }
        } else {
            _selectedIds.addAll(visibleIds)
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun getSelectedIds(): List<Long> = _selectedIds.toList()
    fun getSelectedCount(): Int = _selectedIds.size

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_VISIT_ROW = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is VisitDisplayListItem.HeaderItem -> VIEW_TYPE_HEADER
            is VisitDisplayListItem.VisitRowItem -> VIEW_TYPE_VISIT_ROW
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemSectionHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_VISIT_ROW -> {
                val binding = ListItemVisitBinding.inflate(inflater, parent, false)
                VisitViewHolder(binding, parent.context, onVisitClicked)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

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
                    val visit = item.visit
                    val isSelected = _selectedIds.contains(visit.id)
                    holder.bind(visit, isSelected, isMultiSelectMode,
                        onClickAction = {
                            if (isMultiSelectMode) toggleSelection(visit.id)
                            else onVisitClicked(visit)
                        }
                    )
                }
            }
        }
    }

    class HeaderViewHolder(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(headerItem: VisitDisplayListItem.HeaderItem) {
            binding.sectionHeaderTextView.text = "${headerItem.title} (${headerItem.count})"
        }
    }

    class VisitViewHolder(
        private val binding: ListItemVisitBinding,
        private val context: Context,
        private val onVisitClicked: (Visit) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())

        fun bind(
            visit: Visit,
            isSelected: Boolean = false,
            isMultiSelectMode: Boolean = false,
            onClickAction: () -> Unit = {}
        ) {
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
            if (visit.hasPicture) {
                indicators.append("  \uD83D\uDCF7")
            }
            if (visit.isFavorite) {
                indicators.append("   ⭐")
            }

            binding.visitItemDate.text = "$combinedDateAndOrdinances${indicators.toString().trimEnd()}"

            Log.d("VisitListAdapter", "Binding visit: '${visit.holyPlaceName}', Type: '${visit.type}'")

            val typeColor = ColorUtils.getTextColorForTempleType(context, visit.type)
            binding.visitItemPlaceName.setTextColor(typeColor)

            itemView.isActivated = isSelected
            itemView.alpha = if (isMultiSelectMode && !isSelected) 0.5f else 1.0f

            itemView.setOnClickListener { onClickAction() }
            itemView.setOnLongClickListener(null)
        }
    }
}

class VisitDiffCallback : DiffUtil.ItemCallback<VisitDisplayListItem>() {
    override fun areItemsTheSame(oldItem: VisitDisplayListItem, newItem: VisitDisplayListItem): Boolean {
        return oldItem.stableId == newItem.stableId
    }

    override fun areContentsTheSame(oldItem: VisitDisplayListItem, newItem: VisitDisplayListItem): Boolean {
        return oldItem == newItem
    }
}
