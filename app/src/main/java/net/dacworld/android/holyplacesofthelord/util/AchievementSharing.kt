package net.dacworld.android.holyplacesofthelord.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.model.Achievement
import net.dacworld.android.holyplacesofthelord.model.isCelebrationBoardEligible
import java.io.File
import java.io.FileOutputStream

object AchievementSharing {

    fun handleShareAction(fragment: Fragment, achievement: Achievement, sourceView: View? = null) {
        if (achievement.isCelebrationBoardEligible) {
            val ctx = fragment.requireContext()
            MaterialAlertDialogBuilder(ctx)
                .setTitle(achievement.name)
                .setItems(
                    arrayOf(
                        ctx.getString(R.string.share_achievement),
                        ctx.getString(R.string.post_to_celebration_board)
                    )
                ) { _, which ->
                    when (which) {
                        0 -> shareAchievementImage(fragment, achievement)
                        1 -> beginCelebrationBoardPost(fragment, achievement)
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else {
            shareAchievementImage(fragment, achievement)
        }
    }

    fun shareAchievementImage(fragment: Fragment, achievement: Achievement) {
        val context = fragment.requireContext()
        val image = AchievementShareImageRenderer.render(context, achievement)
        val caption = AchievementShareImageRenderer.shareCaption(context, achievement)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("achievement", caption))

        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(shareDir, "achievement_share.png")
        FileOutputStream(file).use { out ->
            image.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            putExtra(Intent.EXTRA_SUBJECT, CelebrationBoardConfig.APP_DISPLAY_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        fragment.startActivity(Intent.createChooser(intent, context.getString(R.string.share_achievement)))
    }

    fun beginCelebrationBoardPost(
        fragment: Fragment,
        achievement: Achievement,
        onStatus: ((message: String, isError: Boolean) -> Unit)? = null
    ) {
        val context = fragment.requireContext()
        val prefs = UserPreferencesManager.getInstance(context.applicationContext)
        fragment.lifecycleScope.launch {
            val profileId = prefs.activeProfileIdFlow.firstOrNull()
            if (profileId.isNullOrBlank()) {
                val message = context.getString(R.string.celebration_board_no_profile)
                onStatus?.invoke(message, true) ?: showMessage(context, CelebrationBoardConfig.DISPLAY_NAME, message)
                return@launch
            }
            val savedName = prefs.celebrationBoardSavedName(profileId).orEmpty()
            val savedLocation = prefs.celebrationBoardSavedLocation(profileId).orEmpty()
            withContext(Dispatchers.Main) {
                showIdentityDialog(fragment, achievement, profileId, savedName, savedLocation, onStatus)
            }
        }
    }

    private fun showIdentityDialog(
        fragment: Fragment,
        achievement: Achievement,
        profileId: String,
        savedName: String,
        savedLocation: String,
        onStatus: ((String, Boolean) -> Unit)?
    ) {
        val context = fragment.requireContext()
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_celebration_board_identity, null, false)
        val nameField = view.findViewById<TextInputEditText>(R.id.identity_name)
        val locationField = view.findViewById<TextInputEditText>(R.id.identity_location)
        nameField.setText(savedName)
        locationField.setText(savedLocation)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.celebration_board_name)
            .setMessage(R.string.celebration_board_identity_message)
            .setView(view)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_post) { _, _ ->
                val name = nameField.text?.toString().orEmpty()
                val location = locationField.text?.toString().orEmpty()
                submitPost(fragment, achievement, profileId, name, location, onStatus)
            }
            .show()
    }

    private fun submitPost(
        fragment: Fragment,
        achievement: Achievement,
        profileId: String,
        name: String,
        location: String,
        onStatus: ((String, Boolean) -> Unit)?
    ) {
        val context = fragment.requireContext()
        val trimmedName = name.trim()
        val trimmedLocation = location.trim()
        if (trimmedName.isEmpty() || trimmedLocation.isEmpty()) {
            val message = context.getString(R.string.celebration_board_name_location_required)
            onStatus?.invoke(message, true) ?: showMessage(context, CelebrationBoardConfig.DISPLAY_NAME, message)
            return
        }
        val prefs = UserPreferencesManager.getInstance(context.applicationContext)
        val posting = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.celebration_board_posting)
            .setCancelable(false)
            .create()
        posting.show()

        fragment.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val places = AchievementPlaceListBuilder.places(
                achievement,
                db.visitDao(),
                profileId,
                db
            )
            val submission = CelebrationBoardClient.Submission(
                posterId = profileId,
                userName = trimmedName,
                location = trimmedLocation,
                achievement = achievement,
                places = places
            )
            val result = CelebrationBoardClient.post(submission)
            withContext(Dispatchers.Main) {
                posting.dismiss()
                when (result) {
                    is CelebrationBoardClient.PostResult.Success -> {
                        prefs.saveCelebrationBoardIdentity(profileId, trimmedName, trimmedLocation)
                        val message = context.getString(R.string.celebration_board_post_success)
                        onStatus?.invoke(context.getString(R.string.celebration_board_posted_status), false)
                            ?: showMessage(context, CelebrationBoardConfig.DISPLAY_NAME, message)
                    }
                    is CelebrationBoardClient.PostResult.Failure -> {
                        onStatus?.invoke(result.message, true)
                            ?: showMessage(
                                context,
                                context.getString(R.string.celebration_board_post_failed),
                                result.message
                            )
                    }
                }
            }
        }
    }

    private fun showMessage(context: Context, title: String, message: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
