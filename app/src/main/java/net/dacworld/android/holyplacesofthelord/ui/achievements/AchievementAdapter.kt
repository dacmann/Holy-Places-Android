package net.dacworld.android.holyplacesofthelord.ui.achievements

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.model.Achievement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AchievementAdapter : ListAdapter<Achievement, AchievementAdapter.AchievementViewHolder>(AchievementDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AchievementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.achievementIcon)
        private val titleView: TextView = itemView.findViewById(R.id.achievementTitle)
        private val detailsView: TextView = itemView.findViewById(R.id.achievementDetails)
        private val placeView: TextView = itemView.findViewById(R.id.achievementPlace)
        private val dateView: TextView = itemView.findViewById(R.id.achievementDate)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.achievementProgressBar)
        private val progressContainer: View = itemView.findViewById(R.id.achievementProgressContainer)

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        fun bind(achievement: Achievement) {
            val context = itemView.context
            val iconResId = context.resources.getIdentifier(
                achievement.iconName.lowercase(),
                "drawable",
                context.packageName
            )
            if (iconResId != 0) {
                iconView.setImageResource(iconResId)
            } else {
                // Fallback for ach12MT{year} - use ach12mt
                val fallbackResId = context.resources.getIdentifier("ach12mt", "drawable", context.packageName)
                if (fallbackResId != 0) {
                    iconView.setImageResource(fallbackResId)
                } else {
                    iconView.setImageDrawable(null)
                }
            }
            iconView.contentDescription = context.getString(R.string.cd_achievement_icon)

            titleView.text = achievement.name
            titleView.setTextColor(getColorForAchievementType(context, achievement.iconName))

            val isCompleted = achievement.achieved != null
            if (isCompleted) {
                detailsView.text = achievement.details
                detailsView.visibility = View.VISIBLE
                placeView.visibility = View.VISIBLE
                placeView.text = achievement.placeAchieved?.let { context.getString(R.string.achievement_at, it) } ?: ""
                dateView.visibility = View.VISIBLE
                dateView.text = achievement.achieved?.let { context.getString(R.string.achievement_on, dateFormat.format(it)) } ?: ""
                progressContainer.visibility = View.GONE
                itemView.minimumHeight = context.resources.getDimensionPixelSize(R.dimen.achievement_row_completed_height)
            } else {
                val remaining = achievement.remaining
                detailsView.text = if (remaining != null) {
                    "${achievement.details} ($remaining remaining)"
                } else {
                    achievement.details
                }
                detailsView.visibility = View.VISIBLE
                placeView.visibility = View.GONE
                dateView.visibility = View.GONE
                progressContainer.visibility = View.VISIBLE
                progressBar.progress = ((achievement.progress ?: 0f) * 100).toInt()
                val typeColor = getColorForAchievementType(context, achievement.iconName)
                progressBar.progressTintList = ColorStateList.valueOf(typeColor)
                itemView.minimumHeight = context.resources.getDimensionPixelSize(R.dimen.achievement_row_incomplete_height)
            }
        }

        private fun getColorForAchievementType(context: android.content.Context, iconName: String): Int {
            val colorRes = when (iconName.lastOrNull()) {
                'B' -> R.color.achievement_baptisms
                'I' -> R.color.achievement_initiatories
                'E' -> R.color.achievement_endowments
                'S' -> R.color.achievement_sealings
                'W' -> R.color.achievement_worker
                'T' -> R.color.achievement_temples
                'H' -> R.color.achievement_historic
                else -> when {
                    iconName.contains("12MT") -> R.color.achievement_temples
                    else -> R.color.alt_grey_text
                }
            }
            return ContextCompat.getColor(context, colorRes)
        }
    }

    private class AchievementDiffCallback : DiffUtil.ItemCallback<Achievement>() {
        override fun areItemsTheSame(oldItem: Achievement, newItem: Achievement): Boolean {
            return oldItem.iconName == newItem.iconName && oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Achievement, newItem: Achievement): Boolean {
            return oldItem == newItem
        }
    }
}
