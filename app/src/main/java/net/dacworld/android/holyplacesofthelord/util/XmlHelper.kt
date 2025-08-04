package net.dacworld.android.holyplacesofthelord.util

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.dacworld.android.holyplacesofthelord.model.Visit // Your Visit model

object XmlHelper {

    // For consistent date formatting and parsing (matches iOS .full style)
    // Using Locale.US as a default for parsing if current locale fails,
    // as "Saturday, August 6, 1994" is a common English representation.
    // Ideally, the XML would use a locale-independent format like ISO 8601.
    private val primaryDateFormatter = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault())
    private val fallbackDateFormatter = DateFormat.getDateInstance(DateFormat.FULL, Locale.US)


    // Namespace for XML, not strictly needed for simple XML but good practice if it grows
    private val ns: String? = null // No namespace

    // To hold the last critical parsing error if the list is empty
    var lastParseError: XmlParseException? = null


    // --- XML TAGS --- (Match iOS export)
    private const val TAG_DOCUMENT = "Document"
    private const val TAG_EXPORT_DATE_DOC = "ExportDate" // Note: Different from Visit's dateVisited
    private const val TAG_TOTAL_VISITS = "TotalVisits"
    private const val TAG_VISITS_COLLECTION = "Visits"
    private const val TAG_VISIT_ITEM = "Visit"
    private const val TAG_HOLY_PLACE_NAME = "holyPlace" // From iOS XML
    private const val TAG_TYPE = "type"
    private const val TAG_DATE_VISITED = "dateVisited"
    private const val TAG_COMMENTS = "comments"
    private const val TAG_IS_FAVORITE = "isFavorite" // New field
    private const val TAG_HOURS_WORKED = "hoursWorked"
    private const val TAG_SEALINGS = "sealings"
    private const val TAG_ENDOWMENTS = "endowments"
    private const val TAG_INITIATORIES = "initiatories"
    private const val TAG_CONFIRMATIONS = "confirmations"
    private const val TAG_BAPTISMS = "baptisms"


    // DTO for parsing from XML before converting to Visit entity
    // This helps manage potentially missing fields or different types during parsing
    data class VisitDto(
        val holyPlaceName: String?,
        val type: String?,
        val dateVisitedString: String?,
        val comments: String?,
        val isFavorite: Boolean?,
        val hoursWorked: Double?,
        val sealings: Short?,
        val endowments: Short?,
        val initiatories: Short?,
        val confirmations: Short?,
        val baptisms: Short?
        // Note: placeID is resolved later using holyPlaceName
    )

    class XmlParseException(message: String, cause: Throwable? = null) : IOException(message, cause)


    /**
     * Generates XML from a list of Visit objects.
     */
    @Throws(IOException::class)
    fun generateVisitsXml(visits: List<net.dacworld.android.holyplacesofthelord.model.Visit>, outputStream: OutputStream) {
        val serializer: XmlSerializer = Xml.newSerializer()
        serializer.setOutput(outputStream, "UTF-8")
        serializer.startDocument("UTF-8", true)

        serializer.startTag(ns, TAG_DOCUMENT)

        // Add ExportDate and TotalVisits (matching iOS structure)
        val exportDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US) // ISO 8601 for export metadata
        serializer.startTag(ns, TAG_EXPORT_DATE_DOC).text(exportDateFormatter.format(Date())).endTag(ns, TAG_EXPORT_DATE_DOC)
        serializer.startTag(ns, TAG_TOTAL_VISITS).text(visits.size.toString()).endTag(ns, TAG_TOTAL_VISITS)

        serializer.startTag(ns, TAG_VISITS_COLLECTION)

        for (visit in visits) {
            serializer.startTag(ns, TAG_VISIT_ITEM)

            visit.holyPlaceName?.let { serializer.startTag(ns, TAG_HOLY_PLACE_NAME).text(it).endTag(ns, TAG_HOLY_PLACE_NAME) }
            visit.type?.let { serializer.startTag(ns, TAG_TYPE).text(it).endTag(ns, TAG_TYPE) }

            visit.dateVisited?.let {
                val dateString = primaryDateFormatter.format(it)
                serializer.startTag(ns, TAG_DATE_VISITED).text(dateString).endTag(ns, TAG_DATE_VISITED)
            }

            visit.comments?.let {
                serializer.startTag(ns, TAG_COMMENTS)
                serializer.cdsect(it) // Use CDATA for comments
                serializer.endTag(ns, TAG_COMMENTS)
            }

            // isFavorite - export as "true" or "false" string
            serializer.startTag(ns, TAG_IS_FAVORITE).text(visit.isFavorite.toString()).endTag(ns, TAG_IS_FAVORITE)


            // Conditional fields (like in iOS, typically for Temples, type "T")
            // Assuming these fields can be null or 0 in the Visit object if not applicable
            visit.shiftHrs?.let { if (it > 0.0 || visit.type == "T") serializer.startTag(ns, TAG_HOURS_WORKED).text(it.toString()).endTag(ns, TAG_HOURS_WORKED) else if (visit.type == "T") serializer.startTag(ns, TAG_HOURS_WORKED).text("0.0").endTag(ns, TAG_HOURS_WORKED) }
            visit.sealings?.let { if (it > 0 || visit.type == "T") serializer.startTag(ns, TAG_SEALINGS).text(it.toString()).endTag(ns, TAG_SEALINGS) else if (visit.type == "T") serializer.startTag(ns, TAG_SEALINGS).text("0").endTag(ns, TAG_SEALINGS)}
            visit.endowments?.let { if (it > 0 || visit.type == "T") serializer.startTag(ns, TAG_ENDOWMENTS).text(it.toString()).endTag(ns, TAG_ENDOWMENTS) else if (visit.type == "T") serializer.startTag(ns, TAG_ENDOWMENTS).text("0").endTag(ns, TAG_ENDOWMENTS) }
            visit.initiatories?.let { if (it > 0 || visit.type == "T") serializer.startTag(ns, TAG_INITIATORIES).text(it.toString()).endTag(ns, TAG_INITIATORIES) else if (visit.type == "T") serializer.startTag(ns, TAG_INITIATORIES).text("0").endTag(ns, TAG_INITIATORIES) }
            visit.confirmations?.let { if (it > 0 || visit.type == "T") serializer.startTag(ns, TAG_CONFIRMATIONS).text(it.toString()).endTag(ns, TAG_CONFIRMATIONS) else if (visit.type == "T") serializer.startTag(ns, TAG_CONFIRMATIONS).text("0").endTag(ns, TAG_CONFIRMATIONS) }
            visit.baptisms?.let { if (it > 0 || visit.type == "T") serializer.startTag(ns, TAG_BAPTISMS).text(it.toString()).endTag(ns, TAG_BAPTISMS) else if (visit.type == "T") serializer.startTag(ns, TAG_BAPTISMS).text("0").endTag(ns, TAG_BAPTISMS) }

            serializer.endTag(ns, TAG_VISIT_ITEM)
        }
        serializer.endTag(ns, TAG_VISITS_COLLECTION)
        serializer.endTag(ns, TAG_DOCUMENT)
        serializer.endDocument()
        outputStream.flush()
    }


    /**
     * Parses an XML InputStream into a list of VisitDto objects.
     */
    @Throws(XmlPullParserException::class, IOException::class, XmlParseException::class)
    fun parseVisitsXml(inputStream: InputStream): List<VisitDto> {
        lastParseError = null // Reset before parsing
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            val visitsDto = mutableListOf<VisitDto>()
            var currentDto: MutableVisitDto? = null // Use a mutable holder
            var currentTag: String? = null

            try {
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            if (currentTag == TAG_VISIT_ITEM) {
                                currentDto = MutableVisitDto()
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text
                            if (text != null && currentDto != null && currentTag != null) {
                                when (currentTag) {
                                    TAG_HOLY_PLACE_NAME -> currentDto.holyPlaceName = text.trim()
                                    TAG_TYPE -> currentDto.type = text.trim()
                                    TAG_DATE_VISITED -> currentDto.dateVisitedString = text.trim()
                                    TAG_COMMENTS -> currentDto.comments = text.trim() // CDATA is handled, this is the content
                                    TAG_IS_FAVORITE -> currentDto.isFavorite = text.trim().toBooleanStrictOrNull()
                                    TAG_HOURS_WORKED -> currentDto.hoursWorked = text.trim().toDoubleOrNull()
                                    TAG_SEALINGS -> currentDto.sealings = text.trim().toShortOrNull()
                                    TAG_ENDOWMENTS -> currentDto.endowments = text.trim().toShortOrNull()
                                    TAG_INITIATORIES -> currentDto.initiatories = text.trim().toShortOrNull()
                                    TAG_CONFIRMATIONS -> currentDto.confirmations = text.trim().toShortOrNull()
                                    TAG_BAPTISMS -> currentDto.baptisms = text.trim().toShortOrNull()
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == TAG_VISIT_ITEM && currentDto != null) {
                                visitsDto.add(currentDto.toVisitDto())
                                currentDto = null // Reset for next Visit item
                            }
                            currentTag = null // Reset current tag
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: XmlPullParserException) {
                lastParseError = XmlParseException("XML parsing error at line ${parser.lineNumber}: ${e.message}", e)
                throw lastParseError!!
            } catch (e: IOException) {
                lastParseError = XmlParseException("XML file read error: ${e.message}", e)
                throw lastParseError!!
            } catch (e: Exception) { // Catch any other unexpected errors during parsing logic
                lastParseError = XmlParseException("Unexpected error during XML parsing at line ${parser.lineNumber}: ${e.message}", e)
                throw lastParseError!!
            }
            return visitsDto
        }
    }


    fun parseDateString(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            primaryDateFormatter.parse(dateString)
        } catch (e: ParseException) {
            try {
                // Try fallback if primary fails (e.g. device locale changed since export)
                fallbackDateFormatter.parse(dateString)
            } catch (e2: ParseException) {
                // Log the error or handle it as per your app's requirements
                // e.g., Log.w("XmlHelper", "Could not parse date string: $dateString", e2)
                null // Return null if both formatters fail
            }
        }
    }


    // Mutable temporary holder for parsing a Visit item
    private data class MutableVisitDto(
        var holyPlaceName: String? = null,
        var type: String? = null,
        var dateVisitedString: String? = null,
        var comments: String? = null,
        var isFavorite: Boolean? = null,
        var hoursWorked: Double? = null,
        var sealings: Short? = null,
        var endowments: Short? = null,
        var initiatories: Short? = null,
        var confirmations: Short? = null,
        var baptisms: Short? = null
    ) {
        fun toVisitDto() = VisitDto(
            holyPlaceName, type, dateVisitedString, comments, isFavorite,
            hoursWorked, sealings, endowments, initiatories, confirmations, baptisms
        )
    }
}
