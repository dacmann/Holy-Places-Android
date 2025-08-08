package net.dacworld.android.holyplacesofthelord.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.util.Log // Add this if you want logging within the utility

object IntentUtils {

    fun openUrl(context: Context, url: String, errorMessage: String) {
        if (url.isBlank()) {
            Log.w("IntentUtils", "URL is blank, not attempting to open.")
            Toast.makeText(context, "URL is not available.", Toast.LENGTH_SHORT).show() // Or a more specific error
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("IntentUtils", "ActivityNotFoundException for URL: $url", e)
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("IntentUtils", "Exception opening URL $url", e)
            Toast.makeText(context, "Could not open link. Please try again.", Toast.LENGTH_LONG).show() // Generic error
        }
    }

    fun openEmail(context: Context, emailAddress: String, subject: String = "", chooserTitle: String = "Send Email") {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:") // Only email apps should handle this
                putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
                if (subject.isNotBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                }
            }
            // Check if an app can handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, chooserTitle))
            } else {
                Toast.makeText(context, "No email app found.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ActivityNotFoundException) {
            Log.e("IntentUtils", "ActivityNotFoundException for email to: $emailAddress", e)
            Toast.makeText(context, "No email app found.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("IntentUtils", "Exception opening email for $emailAddress", e)
            Toast.makeText(context, "Could not open email. Please try again.", Toast.LENGTH_LONG).show()
        }
    }
}
    