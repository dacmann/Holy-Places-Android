package net.dacworld.android.holyplacesofthelord.ui.achievements

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.databinding.DialogAchievementUnlockedBinding
import net.dacworld.android.holyplacesofthelord.model.Achievement
import net.dacworld.android.holyplacesofthelord.model.isCelebrationBoardEligible
import net.dacworld.android.holyplacesofthelord.util.AchievementSharing
import java.text.SimpleDateFormat
import java.util.Locale

class AchievementUnlockedDialogFragment : DialogFragment() {

    private var currentIndex = 0
    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        currentIndex = savedInstanceState?.getInt(STATE_INDEX) ?: 0
        val binding = DialogAchievementUnlockedBinding.inflate(layoutInflater)
        bindAchievement(binding)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton(R.string.done) { _, _ ->
                setFragmentResult(RESULT_KEY, bundleOf())
            }
            .setNeutralButton(R.string.next_achievement, null)
            .create()
        isCancelable = false
        dialog.setOnShowListener {
            updateNextButton(dialog, binding)
        }
        return dialog
    }

    private fun bindAchievement(binding: DialogAchievementUnlockedBinding) {
        val achievements = pendingAchievements
        if (achievements.isEmpty()) return
        val achievement = achievements[currentIndex.coerceIn(0, achievements.lastIndex)]
        val context = requireContext()

        val iconResId = context.resources.getIdentifier(
            achievement.iconName.lowercase(Locale.US),
            "drawable",
            context.packageName
        ).takeIf { it != 0 } ?: context.resources.getIdentifier("ach12mt", "drawable", context.packageName)
        if (iconResId != 0) {
            binding.unlockedIcon.setImageResource(iconResId)
        }

        val typeColor = colorForIcon(achievement.iconName)
        binding.unlockedName.text = achievement.name
        binding.unlockedName.setTextColor(typeColor)
        binding.unlockedDetails.text = achievement.details

        if (!achievement.placeAchieved.isNullOrBlank()) {
            binding.unlockedPlace.visibility = View.VISIBLE
            binding.unlockedPlace.text = getString(R.string.achievement_at, achievement.placeAchieved)
        } else {
            binding.unlockedPlace.visibility = View.GONE
        }
        if (achievement.achieved != null) {
            binding.unlockedDate.visibility = View.VISIBLE
            binding.unlockedDate.text = getString(R.string.achievement_on, dateFormat.format(achievement.achieved))
        } else {
            binding.unlockedDate.visibility = View.GONE
        }

        if (achievements.size > 1) {
            binding.unlockedIndex.visibility = View.VISIBLE
            binding.unlockedIndex.text = getString(
                R.string.achievement_unlocked_index,
                currentIndex + 1,
                achievements.size
            )
        } else {
            binding.unlockedIndex.visibility = View.GONE
        }

        binding.unlockedShare.setOnClickListener {
            AchievementSharing.shareAchievementImage(this, achievement)
        }
        if (achievement.isCelebrationBoardEligible) {
            binding.unlockedPost.visibility = View.VISIBLE
            binding.unlockedPost.setOnClickListener {
                AchievementSharing.beginCelebrationBoardPost(this, achievement) { message, isError ->
                    binding.unlockedStatus.visibility = View.VISIBLE
                    binding.unlockedStatus.text = message
                    binding.unlockedStatus.setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark
                        )
                    )
                }
            }
        } else {
            binding.unlockedPost.visibility = View.GONE
        }
    }

    private fun updateNextButton(
        dialog: Dialog,
        binding: DialogAchievementUnlockedBinding
    ) {
        val alert = dialog as? androidx.appcompat.app.AlertDialog ?: return
        val nextButton = alert.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL) ?: return
        val hasNext = pendingAchievements.size > 1 && currentIndex < pendingAchievements.lastIndex
        nextButton.visibility = if (hasNext) View.VISIBLE else View.GONE
        nextButton.setOnClickListener {
            if (currentIndex >= pendingAchievements.lastIndex) return@setOnClickListener
            currentIndex++
            binding.unlockedStatus.visibility = View.GONE
            bindAchievement(binding)
            val stillHasNext = pendingAchievements.size > 1 && currentIndex < pendingAchievements.lastIndex
            nextButton.visibility = if (stillHasNext) View.VISIBLE else View.GONE
        }
    }

    private fun colorForIcon(iconName: String): Int {
        val colorRes = when (iconName.lastOrNull()) {
            'B' -> R.color.achievement_baptisms
            'I' -> R.color.achievement_initiatories
            'E' -> R.color.achievement_endowments
            'S' -> R.color.achievement_sealings
            'W' -> R.color.achievement_worker
            'T' -> R.color.achievement_temples
            'H' -> R.color.achievement_historic
            else -> R.color.achievement_temples
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_INDEX, currentIndex)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (parentFragment == null) {
            pendingAchievements = emptyList()
        }
    }

    companion object {
        const val TAG = "AchievementUnlocked"
        const val RESULT_KEY = "achievement_unlocked_done"
        private const val STATE_INDEX = "index"

        private var pendingAchievements: List<Achievement> = emptyList()

        fun show(fragment: androidx.fragment.app.Fragment, achievements: List<Achievement>) {
            pendingAchievements = achievements
            AchievementUnlockedDialogFragment().show(fragment.childFragmentManager, TAG)
        }
    }
}
