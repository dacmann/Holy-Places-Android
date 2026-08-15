package net.dacworld.android.holyplacesofthelord.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.model.Achievement
import net.dacworld.android.holyplacesofthelord.model.achievementType
import net.dacworld.android.holyplacesofthelord.model.threshold
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CelebrationBoardClient {

    data class Submission(
        val posterId: String,
        val userName: String,
        val location: String,
        val achievement: Achievement,
        val places: List<String>
    )

    sealed class PostResult {
        data object Success : PostResult()
        data class Failure(val message: String) : PostResult()
    }

    suspend fun post(submission: Submission): PostResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(CelebrationBoardConfig.API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val payload = JSONObject().apply {
                put("posterId", submission.posterId)
                put("userName", submission.userName)
                put("location", submission.location)
                put("achievementId", submission.achievement.iconName)
                put("achievementName", submission.achievement.name)
                put("achievementDetails", submission.achievement.details)
                put("achievementType", submission.achievement.achievementType ?: "")
                put("threshold", submission.achievement.threshold ?: 0)
                put("dateAchieved", dateFormat.format(submission.achievement.achieved ?: Date()))
                put("placeAchieved", submission.achievement.placeAchieved ?: "")
                put("places", JSONArray(submission.places))
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            connection.disconnect()

            val json = runCatching { JSONObject(body) }.getOrNull()
            if (status in 200..299 && json?.optBoolean("ok") == true) {
                return@withContext PostResult.Success
            }
            val serverMessage = json?.optString("error")?.takeIf { it.isNotBlank() }
            if (serverMessage != null) {
                return@withContext PostResult.Failure(serverMessage)
            }
            if (body.contains("<?php")) {
                return@withContext PostResult.Failure("Celebration Board API is not running PHP on the server.")
            }
            PostResult.Failure("Server returned status $status.")
        } catch (e: Exception) {
            PostResult.Failure(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }
}
