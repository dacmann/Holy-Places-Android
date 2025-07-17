package net.dacworld.android.holyplacesofthelord.util

import android.util.Log
import net.dacworld.android.holyplacesofthelord.data.HolyPlacesData
import net.dacworld.android.holyplacesofthelord.model.Temple // Ensure your Temple model is imported
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate // Keep this for the return type
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// --- Utility functions copied directly from DataViewModel.kt (file [1]) ---
// Temple.announcedDate should be LocalDate?
// Temple.order should be Short? or Short

fun extractAnnouncedDateObject(snippet: String): LocalDate? {
    val regex = """announced\s+(\d{1,2}\s+[a-zA-Z]+\s+\d{4})""".toRegex(RegexOption.IGNORE_CASE)
    val matchResult = regex.find(snippet)

    if (matchResult != null && matchResult.groupValues.size > 1) {
        val dateStrComponent = matchResult.groupValues[1].trim()
        try {
            val inputFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
            return LocalDate.parse(dateStrComponent, inputFormatter) // Return LocalDate object
        } catch (e: DateTimeParseException) {
            Log.w("HolyPlacesXmlParserUtil", "Could not parse date from snippet component: '$dateStrComponent'")
            return null
        }
    }
    return null
}

fun extractOrderFromSnippet(snippet: String): Short { // Assuming Temple.order is Short
    val numberString = StringBuilder()
    for (char in snippet) {
        if (char.isDigit()) {
            numberString.append(char)
        } else {
            break
        }
    }
    return if (numberString.isNotEmpty()) {
        numberString.toString().toShortOrNull() ?: 300 // Default to 300 if conversion fails
    } else {
        300 // Default if no leading number
    }
}
// --- End of utility functions ---

object HolyPlacesXmlParser {

    private const val TAG = "HolyPlacesXmlParser"

    fun parse(inputStream: InputStream): HolyPlacesData {
        val temples = mutableListOf<Temple>()
        var currentTemple: Temple? = null
        var text: String? = null

        var xmlVersion: String? = null
        var changesDate: String? = null
        var changesMsg1: String? = null
        var changesMsg2: String? = null
        var changesMsg3: String? = null
        var changesMsgQuiz: String? = null

        var inDocumentRoot = true

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.trim()

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("Place", ignoreCase = true)) {
                            currentTemple = Temple()
                            inDocumentRoot = false
                        }
                    }

                    XmlPullParser.TEXT -> {
                        text = parser.text?.trim()?.takeIf { it.isNotEmpty() }
                    }

                    XmlPullParser.END_TAG -> {
                        if (inDocumentRoot) {
                            when {
                                tagName.equals("Version", ignoreCase = true) -> xmlVersion = text
                                tagName.equals("ChangesDate", ignoreCase = true) -> changesDate = text
                                tagName.equals("ChangesMsg1", ignoreCase = true) -> changesMsg1 = text
                                tagName.equals("ChangesMsg2", ignoreCase = true) -> changesMsg2 = text
                                tagName.equals("ChangesMsg3", ignoreCase = true) -> changesMsg3 = text
                                tagName.equals("ChangesMsgQuiz", ignoreCase = true) -> changesMsgQuiz = text
                                tagName.equals("Document", ignoreCase = true) -> { /* End of document */ }
                            }
                        } else currentTemple?.let { temple ->
                            // var processSnippetForExtras = false // As discussed, likely redundant
                            when {
                                tagName.equals("ID", ignoreCase = true) -> temple.id = text ?: ""
                                tagName.equals("name", ignoreCase = true) -> temple.name = text ?: ""
                                tagName.equals("fhc", ignoreCase = true) -> temple.fhCode = text
                                tagName.equals("Snippet", ignoreCase = true) -> {
                                    temple.snippet = text ?: ""
                                    // processSnippetForExtras = true
                                }
                                tagName.equals("site_url", ignoreCase = true) -> temple.siteUrl = text ?: ""
                                tagName.equals("infoURL", ignoreCase = true) -> temple.infoUrl = text
                                tagName.equals("image", ignoreCase = true) -> temple.pictureUrl = text ?: ""
                                tagName.equals("SqFt", ignoreCase = true) -> temple.sqFt = text?.toIntOrNull()
                                tagName.equals("Address", ignoreCase = true) -> temple.address = text ?: ""
                                tagName.equals("CityState", ignoreCase = true) -> temple.cityState = text ?: ""
                                tagName.equals("Country", ignoreCase = true) -> temple.country = text ?: ""
                                tagName.equals("Phone", ignoreCase = true) -> temple.phone = text ?: ""
                                tagName.equals("longitude", ignoreCase = true) -> temple.longitude = text?.toDoubleOrNull() ?: 0.0
                                tagName.equals("latitude", ignoreCase = true) -> temple.latitude = text?.toDoubleOrNull() ?: 0.0
                                tagName.equals("type", ignoreCase = true) -> temple.type = text ?: ""

                                tagName.equals("Place", ignoreCase = true) -> {
                                    if (temple.snippet.isNotBlank()) {
                                        // Assigns LocalDate? to Temple.announcedDate (assuming type is LocalDate?)
                                        temple.announcedDate = extractAnnouncedDateObject(temple.snippet)
                                        temple.order = extractOrderFromSnippet(temple.snippet)

                                        if ((temple.type == "T" || temple.type == "C" || temple.type == "A") && temple.announcedDate == null) {
                                            Log.w(TAG, "⚠️ Could not parse announced date for: ${temple.name} — Snippet: ${temple.snippet}")
                                        }
                                    } else {
                                        temple.order = 300
                                        temple.announcedDate = null
                                    }
                                    if (temple.id.isNotBlank()) {
                                        temples.add(temple)
                                    } else {
                                        Log.w(TAG, "Skipped a place due to missing ID during parsing.")
                                    }
                                    currentTemple = null
                                    inDocumentRoot = true
                                }
                            }
                        }
                        // ***** THE FIX: Reset text variable after it's been used for the current END_TAG *****
                        text = null
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "XML parsing error: ${e.message}", e)
            return HolyPlacesData(temples, xmlVersion, changesDate, changesMsg1, changesMsg2, changesMsg3)
        } catch (e: IOException) {
            Log.e(TAG, "IO error during XML parsing: ${e.message}", e)
            return HolyPlacesData(temples, xmlVersion, changesDate, changesMsg1, changesMsg2, changesMsg3)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during XML parsing: ${e.message}", e)
            return HolyPlacesData(temples, xmlVersion, changesDate, changesMsg1, changesMsg2, changesMsg3)
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                Log.w(TAG, "Error closing input stream: ${e.message}")
            }
        }

        return HolyPlacesData(
            temples = temples,
            version = xmlVersion,
            changesDate = changesDate,
            changesMsg1 = changesMsg1,
            changesMsg2 = changesMsg2,
            changesMsg3 = changesMsg3,
        )
    }
}