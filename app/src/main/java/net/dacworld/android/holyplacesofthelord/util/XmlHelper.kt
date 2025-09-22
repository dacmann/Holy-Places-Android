package net.dacworld.android.holyplacesofthelord.util

import android.util.Base64
import android.util.Log
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

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
    private const val TAG_PLACE_ID = "placeID"
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
    private const val TAG_PICTURE = "picture"


    // DTO for parsing from XML before converting to Visit entity
    // This helps manage potentially missing fields or different types during parsing
    data class VisitDto(
        val placeID: String?,
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
        val baptisms: Short?,
        val picture: String? // Base64 encoded picture data
        // Note: placeID is resolved later using holyPlaceName
    )

    class XmlParseException(message: String, cause: Throwable? = null) : IOException(message, cause)
    
    // Constants for memory management
    private const val MAX_PHOTO_SIZE_BYTES = 25 * 1024 * 1024 // 25MB per photo (restored)
    private const val CHUNK_SIZE = 8192 // 8KB chunks for processing
    private const val MAX_PHOTO_COUNT = Int.MAX_VALUE // No limit on photo count
    private const val MEMORY_CLEANUP_INTERVAL = 5 // Clean up memory every 5 visits
    
    /**
     * Safely processes photo data with memory limits
     */
    private fun processPhotoData(
        currentDto: MutableVisitDto, 
        textToAdd: String
    ): Boolean {
        // Initialize StringBuilder if needed
        if (currentDto.picture == null) {
            currentDto.picture = StringBuilder()
        }
        
        // Check memory limits for individual photo size
        val currentLength = currentDto.picture?.length ?: 0
        if (currentLength + textToAdd.length > MAX_PHOTO_SIZE_BYTES) {
            Log.w("XmlHelper", "Photo data too large (${currentLength + textToAdd.length} bytes), skipping for visit: ${currentDto.holyPlaceName}")
            currentDto.picture = null
            return false
        }
        
        // Add text in chunks to avoid large allocations
        val trimmedText = textToAdd.trim()
        if (trimmedText.isNotEmpty()) {
            currentDto.picture?.append(trimmedText)
        }
        
        return true
    }
    
    /**
     * Validates if the photo data is reasonable before processing
     */
    private fun isValidPhotoData(base64String: String): Boolean {
        if (base64String.isEmpty()) return false
        
        // Check if it's valid Base64
        try {
            val decoded = Base64.decode(base64String, Base64.DEFAULT)
            if (decoded.isEmpty()) return false
            
            // Check if it looks like image data (basic header check)
            if (decoded.size < 4) return false
            
            // Check for common image file signatures
            val header = decoded.take(4)
            val isValidImage = when {
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> true // JPEG
                header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && 
                header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> true // PNG
                header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && 
                header[2] == 0x46.toByte() && header[3] == 0x38.toByte() -> true // GIF
                else -> false
            }
            
            return isValidImage
        } catch (e: Exception) {
            Log.w("XmlHelper", "Invalid Base64 photo data: ${e.message}")
            return false
        }
    }

    /**
     * Validates if the decoded photo data looks like valid image data
     */
    private fun isValidImageData(data: ByteArray): Boolean {
        if (data.isEmpty() || data.size < 4) return false
        
        // Check for common image file signatures
        val header = data.take(4)
        return when {
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> true // JPEG
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && 
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> true // PNG
            header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && 
            header[2] == 0x46.toByte() && header[3] == 0x38.toByte() -> true // GIF
            else -> false
        }
    }


    /**
     * Escapes XML special characters
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Generates XML for visits with streaming photo loading to avoid memory issues
     */
    @Throws(IOException::class)
    suspend fun generateVisitsXmlStreaming(visits: List<net.dacworld.android.holyplacesofthelord.model.Visit>, outputStream: OutputStream, visitDao: net.dacworld.android.holyplacesofthelord.dao.VisitDao) {
        val serializer: XmlSerializer = Xml.newSerializer()
        serializer.setOutput(outputStream, "UTF-8")
        serializer.startDocument("UTF-8", true)

        serializer.startTag(ns, TAG_DOCUMENT)

        // Add ExportDate and TotalVisits (matching iOS structure)
        val exportDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        serializer.startTag(ns, TAG_EXPORT_DATE_DOC).text(exportDateFormatter.format(Date())).endTag(ns, TAG_EXPORT_DATE_DOC)
        serializer.startTag(ns, TAG_TOTAL_VISITS).text(visits.size.toString()).endTag(ns, TAG_TOTAL_VISITS)

        serializer.startTag(ns, TAG_VISITS_COLLECTION)

        for (visit in visits) {
            serializer.startTag(ns, TAG_VISIT_ITEM)

            serializer.startTag(ns, TAG_PLACE_ID).text(visit.placeID ?: "").endTag(ns, TAG_PLACE_ID)

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

            serializer.startTag(ns, TAG_IS_FAVORITE).text(visit.isFavorite.toString()).endTag(ns, TAG_IS_FAVORITE)

            visit.shiftHrs?.let { serializer.startTag(ns, TAG_HOURS_WORKED).text(it.toString()).endTag(ns, TAG_HOURS_WORKED) }
            visit.sealings?.let { serializer.startTag(ns, TAG_SEALINGS).text(it.toString()).endTag(ns, TAG_SEALINGS) }
            visit.endowments?.let { serializer.startTag(ns, TAG_ENDOWMENTS).text(it.toString()).endTag(ns, TAG_ENDOWMENTS) }
            visit.initiatories?.let { serializer.startTag(ns, TAG_INITIATORIES).text(it.toString()).endTag(ns, TAG_INITIATORIES) }
            visit.confirmations?.let { serializer.startTag(ns, TAG_CONFIRMATIONS).text(it.toString()).endTag(ns, TAG_CONFIRMATIONS) }
            visit.baptisms?.let { serializer.startTag(ns, TAG_BAPTISMS).text(it.toString()).endTag(ns, TAG_BAPTISMS) }

            // Handle photo data with streaming approach
            if (visit.hasPicture) {
                try {
                    // Load photo data individually to avoid memory issues
                    val visitWithPhoto = visitDao.getVisitWithPictureById(visit.id)
                    if (visitWithPhoto?.picture != null && visitWithPhoto.picture.isNotEmpty()) {
                        val base64Photo = Base64.encodeToString(visitWithPhoto.picture, Base64.DEFAULT)
                        serializer.startTag(ns, TAG_PICTURE)
                        serializer.cdsect(base64Photo) // Use CDATA for photo data
                        serializer.endTag(ns, TAG_PICTURE)
                        Log.d("XmlHelper", "Exported photo for visit: ${visit.holyPlaceName}, size: ${visitWithPhoto.picture.size} bytes")
                    } else {
                        Log.w("XmlHelper", "Failed to load photo data for visit: ${visit.holyPlaceName}")
                    }
                } catch (e: Exception) {
                    Log.w("XmlHelper", "Error loading photo for visit: ${visit.holyPlaceName}", e)
                }
            }

            serializer.endTag(ns, TAG_VISIT_ITEM)
            
            // Force garbage collection after each visit to free memory
            System.gc()
        }

        serializer.endTag(ns, TAG_VISITS_COLLECTION)
        serializer.endTag(ns, TAG_DOCUMENT)
        serializer.endDocument()
    }

    /**
     * Generates XML from a list of Visit objects.
     */
    @Throws(IOException::class)
    fun generateVisitsXml(visits: List<net.dacworld.android.holyplacesofthelord.model.Visit>, outputStream: OutputStream, includePhotos: Boolean = false) {
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

            serializer.startTag(ns, TAG_PLACE_ID).text(visit.placeID).endTag(ns, TAG_PLACE_ID)

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

            // Picture data - Base64 encoded (only if includePhotos is true)
            if (includePhotos) {
                visit.picture?.let { pictureData ->
                    if (pictureData.isNotEmpty()) {
                        val base64String = Base64.encodeToString(pictureData, Base64.NO_WRAP)
                        serializer.startTag(ns, TAG_PICTURE)
                        serializer.cdsect(base64String) // Use CDATA for Base64 data
                        serializer.endTag(ns, TAG_PICTURE)
                    } else {
                        serializer.startTag(ns, TAG_PICTURE).text("").endTag(ns, TAG_PICTURE)
                    }
                } ?: run {
                    serializer.startTag(ns, TAG_PICTURE).text("").endTag(ns, TAG_PICTURE)
                }
            } else {
                serializer.startTag(ns, TAG_PICTURE).text("").endTag(ns, TAG_PICTURE)
            }

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
     * Parses an XML InputStream and processes visits one at a time like iOS.
     * This method processes each visit immediately and calls a callback, freeing memory after each visit.
     */
    @Throws(XmlPullParserException::class, IOException::class, XmlParseException::class)
    fun parseVisitsXml(inputStream: InputStream): List<VisitDto> {
        return parseVisitsXmlStreamingLikeIOS(inputStream)
    }

    /**
     * Streaming parser that processes visits one at a time like iOS - immediately processes and saves each visit
     */
    @Throws(XmlPullParserException::class, IOException::class, XmlParseException::class)
    suspend fun parseVisitsXmlStreaming(
        inputStream: InputStream, 
        onVisitProcessed: suspend (VisitDto) -> Unit,
        shouldSkipPhoto: suspend (String, String) -> Boolean = { _, _ -> false }
    ) {
        lastParseError = null
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            var currentDto: MutableVisitDto? = null
            var currentTag: String? = null
            var photoCount = 0
            var pictureBase64String = StringBuilder()

            try {
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            if (currentTag == TAG_VISIT_ITEM) {
                                currentDto = MutableVisitDto()
                            } else if (currentTag == TAG_PICTURE) {
                                // Initialize StringBuilder for accumulating Base64 data
                                pictureBase64String = StringBuilder()
                                Log.d("XmlHelper", "Started picture tag for visit: ${currentDto?.holyPlaceName}")
                            } else if (currentTag == TAG_COMMENTS) {
                                Log.d("XmlHelper", "Started comments tag for visit: ${currentDto?.holyPlaceName}")
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (currentDto != null && currentTag != null) {
                                when (currentTag) {
                                    TAG_PLACE_ID -> currentDto.placeID = parser.text.trim()
                                    TAG_HOLY_PLACE_NAME -> currentDto.holyPlaceName = parser.text.trim()
                                    TAG_TYPE -> currentDto.type = parser.text.trim()
                                    TAG_DATE_VISITED -> currentDto.dateVisitedString = parser.text.trim()
                                    TAG_COMMENTS -> {
                                        currentDto.comments = parser.text.trim()
                                        if (parser.text.trim().isNotEmpty()) {
                                            Log.d("XmlHelper", "Processed comments: ${parser.text.length} chars for visit: ${currentDto.holyPlaceName}")
                                        }
                                    }
                                    TAG_IS_FAVORITE -> currentDto.isFavorite = parser.text.trim().toBooleanStrictOrNull()
                                    TAG_HOURS_WORKED -> currentDto.hoursWorked = parser.text.trim().toDoubleOrNull()
                                    TAG_SEALINGS -> currentDto.sealings = parser.text.trim().toShortOrNull()
                                    TAG_ENDOWMENTS -> currentDto.endowments = parser.text.trim().toShortOrNull()
                                    TAG_INITIATORIES -> currentDto.initiatories = parser.text.trim().toShortOrNull()
                                    TAG_CONFIRMATIONS -> currentDto.confirmations = parser.text.trim().toShortOrNull()
                                    TAG_BAPTISMS -> currentDto.baptisms = parser.text.trim().toShortOrNull()
                                    TAG_PICTURE -> {
                                        // Accumulate Base64 data like iOS version
                                        val text = parser.text.trim()
                                        if (text.isNotEmpty()) {
                                            pictureBase64String.append(text)
                                            Log.d("XmlHelper", "Accumulating Base64 data, current length: ${pictureBase64String.length} chars for visit: ${currentDto.holyPlaceName}")
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == TAG_PICTURE && pictureBase64String.isNotEmpty()) {
                                // Check if we should skip photo processing for this visit
                                val base64String = pictureBase64String.toString()
                                val holyPlaceName = currentDto?.holyPlaceName ?: ""
                                val dateVisited = currentDto?.dateVisitedString ?: ""
                                
                                if (shouldSkipPhoto(holyPlaceName, dateVisited)) {
                                    Log.d("XmlHelper", "Skipping photo processing for visit: $holyPlaceName (duplicate with existing photo)")
                                    currentDto?.picture = null
                                    pictureBase64String.clear()
                                    // Continue parsing instead of exiting
                                } else {
                                    // Process the accumulated Base64 string immediately like iOS version
                                    Log.d("XmlHelper", "Processing complete Base64 string, length: ${base64String.length} chars for visit: $holyPlaceName")
                                    
                                    try {
                                        // Decode Base64 to byte array immediately
                                        val pictureData = Base64.decode(base64String, Base64.DEFAULT)
                                        Log.d("XmlHelper", "Successfully decoded Base64 to ${pictureData.size} bytes for visit: $holyPlaceName")
                                        
                                        // Validate that the decoded data looks like valid image data
                                        if (isValidImageData(pictureData)) {
                                            // Store the Base64 string in the DTO (like iOS stores in Core Data)
                                            currentDto?.picture = StringBuilder(base64String)
                                            Log.d("XmlHelper", "Valid image data confirmed for visit: $holyPlaceName")
                                        } else {
                                            Log.w("XmlHelper", "Invalid image data for visit: $holyPlaceName")
                                            currentDto?.picture = null
                                        }
                                        
                                    } catch (e: OutOfMemoryError) {
                                        Log.w("XmlHelper", "OutOfMemoryError decoding photo for visit: $holyPlaceName, skipping photo", e)
                                        currentDto?.picture = null
                                    } catch (e: Exception) {
                                        Log.w("XmlHelper", "Failed to decode Base64 image data for visit: $holyPlaceName", e)
                                        currentDto?.picture = null
                                    }
                                    
                                    // Clear the accumulator and force garbage collection
                                    pictureBase64String.clear()
                                    System.gc() // Force garbage collection after each photo
                                }
                            } else if (parser.name == TAG_VISIT_ITEM && currentDto != null) {
                                val hasPhoto = currentDto.picture?.isNotEmpty() == true
                                val hasComments = !currentDto.comments.isNullOrBlank()
                                
                                if (hasPhoto) {
                                    photoCount++
                                }
                                
                                Log.d("XmlHelper", "Completed visit (streaming): ${currentDto.holyPlaceName}, hasPhoto: $hasPhoto, hasComments: $hasComments")
                                
                                // Process the visit immediately like iOS - convert to DTO and call callback
                                val visitDto = currentDto.toVisitDto()
                                onVisitProcessed(visitDto)
                                
                                // Clear the current DTO to free memory immediately
                                currentDto = null
                                
                                // Force garbage collection after each visit (like iOS)
                                System.gc()
                            }
                            currentTag = null
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: XmlPullParserException) {
                lastParseError = XmlParseException("XML parsing error at line ${parser.lineNumber}: ${e.message}", e)
                throw lastParseError!!
            } catch (e: Exception) {
                lastParseError = XmlParseException("Unexpected error during XML parsing: ${e.message}", e)
                throw lastParseError!!
            }

            Log.d("XmlHelper", "Streaming parser completed: $photoCount photos processed")
        }
    }
    
    /**
     * Character-by-character XML parser that handles large photo data without memory issues
     */
    @Throws(XmlPullParserException::class, IOException::class, XmlParseException::class)
    private fun parseVisitsXmlWithCustomReader(inputStream: InputStream): List<VisitDto> {
        lastParseError = null
        Log.d("XmlHelper", "Starting custom XML parser")
        inputStream.use { stream ->
            val reader = InputStreamReader(stream, "UTF-8")
            val visitsDto = mutableListOf<VisitDto>()
            var currentDto: MutableVisitDto? = null
            var inPictureTag = false
            var inCdata = false
            var photoCount = 0
            var char: Int
            val buffer = CharArray(8192) // 8KB buffer for reading
            var bufferPos = 0
            var bufferSize = 0
            val currentTag = StringBuilder()
            val currentValue = StringBuilder()
            var totalCharsProcessed = 0
            var insideTag = false
            var currentOpeningTag = ""

            try {
                while (true) {
                    // Read characters in chunks
                    if (bufferPos >= bufferSize) {
                        bufferSize = reader.read(buffer)
                        if (bufferSize == -1) break
                        bufferPos = 0
                    }
                    
                    char = buffer[bufferPos++].code
                    totalCharsProcessed++
                    
                    if (totalCharsProcessed % 2000000 == 0) {
                        Log.d("XmlHelper", "Processed $totalCharsProcessed characters, visits: ${visitsDto.size}")
                    }
                    
                    when (char.toChar()) {
                        '<' -> {
                            // Start of tag
                            insideTag = true
                            currentTag.clear()
                            // Don't clear currentValue here - we need to preserve content
                            inCdata = false
                            // Found opening tag
                        }
                        '>' -> {
                            // End of tag
                            insideTag = false
                            val tag = currentTag.toString().trim()
                            
                            if (tag.startsWith("/")) {
                                // This is a closing tag
                                val closingTag = tag.substring(1) // Remove the "/"
                                
                                if (closingTag == TAG_VISIT_ITEM) {
                                    // Special case: closing visit tag
                                    if (currentDto != null) {
                                        val hasPhoto = currentDto.picture?.isNotEmpty() == true
                                        val hasComments = !currentDto.comments.isNullOrBlank()
                                        
                                        if (hasPhoto) {
                                            photoCount++
                                        }
                                        
                                        Log.d("XmlHelper", "Completed visit: ${currentDto.holyPlaceName}, hasPhoto: $hasPhoto, hasComments: $hasComments")
                                        
                                        visitsDto.add(currentDto.toVisitDto())
                                        currentDto = null
                                        
                                        // Force garbage collection every 20 visits
                                        if (visitsDto.size % 20 == 0) {
                                            System.gc()
                                        }
                                    }
                                } else if (closingTag == TAG_PICTURE) {
                                    inPictureTag = false
                                } else if (closingTag == currentOpeningTag && currentOpeningTag.isNotEmpty()) {
                                    // Process the content we've collected for this tag
                                    val content = currentValue.toString().trim()
                                    processRegularTag(closingTag, content, currentDto)
                                    currentOpeningTag = ""
                                }
                            } else {
                                // This is an opening tag
                                when {
                                    tag == TAG_VISIT_ITEM -> {
                                        currentDto = MutableVisitDto()
                                    }
                                    tag == TAG_PICTURE -> {
                                        inPictureTag = true
                                        currentDto?.picture = StringBuilder()
                                    }
                                    !inPictureTag && !inCdata -> {
                                        // This is a regular opening tag - remember it and start collecting content
                                        currentOpeningTag = tag
                                        currentValue.clear() // Clear any previous content
                                    }
                                }
                            }
                            
                            currentTag.clear()
                        }
                        else -> {
                            if (inCdata) {
                                // Skip CDATA content - already processed
                                continue
                            } else if (insideTag) {
                                // We're inside a tag, build the tag name
                                currentTag.append(char.toChar())
                            } else if (inPictureTag) {
                                // Inside picture tag - process as photo data
                                processPhotoCharacter(char.toChar(), currentDto)
                            } else if (currentOpeningTag.isNotEmpty()) {
                                // We're outside a tag and have an opening tag, this is content
                                currentValue.append(char.toChar())
                                
                                // Check if we just started a CDATA section
                                val currentContent = currentValue.toString()
                                if (currentContent.endsWith("<![CDATA[")) {
                                    Log.d("XmlHelper", "Found CDATA in '$currentOpeningTag'")
                                    inCdata = true
                                    // Remove the CDATA marker from currentValue and process CDATA content
                                    currentValue.setLength(currentValue.length - 10) // Remove "<![CDATA["
                                    processCdataContent(reader, buffer, bufferPos, bufferSize, currentDto, inPictureTag, currentValue, currentOpeningTag)
                                    bufferPos = 0 // Reset buffer position
                                    bufferSize = 0 // Force next read
                                } else if (currentContent.contains("CDATA")) {
                                    Log.d("XmlHelper", "CDATA detected in '$currentOpeningTag': '${currentContent.takeLast(20)}'")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                lastParseError = XmlParseException("Error parsing XML: ${e.message}", e)
                throw lastParseError!!
            }

            Log.d("XmlHelper", "Successfully parsed ${visitsDto.size} visits with $photoCount photos, total chars: $totalCharsProcessed")
            
            // Count visits with comments for final logging
            val visitsWithComments = visitsDto.count { !it.comments.isNullOrBlank() }
            Log.d("XmlHelper", "Import completed: ${visitsDto.size} visits processed, $photoCount photos imported, $visitsWithComments visits with comments, total characters processed: $totalCharsProcessed")
            return visitsDto
        }
    }

    /**
     * Process CDATA content for photos and comments
     */
    private fun processCdataContent(
        reader: InputStreamReader, 
        buffer: CharArray, 
        startPos: Int, 
        bufferSize: Int, 
        currentDto: MutableVisitDto?, 
        inPictureTag: Boolean,
        currentValue: StringBuilder,
        currentOpeningTag: String
    ) {
        if (currentDto == null) return
        
        Log.d("XmlHelper", "Processing CDATA for '$currentOpeningTag'")
        
        var pos = startPos
        var size = bufferSize
        val cdataContent = StringBuilder()
        
        // Read until we find "]]>"
        while (true) {
            if (pos >= size) {
                size = reader.read(buffer)
                if (size == -1) break
                pos = 0
            }
            
            val char = buffer[pos++]
            cdataContent.append(char)
            
            // Check for "]]>" at the end
            if (cdataContent.length >= 3 && 
                cdataContent[cdataContent.length - 3] == ']' &&
                cdataContent[cdataContent.length - 2] == ']' &&
                cdataContent[cdataContent.length - 1] == '>') {
                // Remove "]]>" and process the content
                val data = cdataContent.substring(0, cdataContent.length - 3)
                
                if (inPictureTag) {
                    // This is photo data
                    if (currentDto.picture != null) {
                        processPhotoDataChunked(currentDto, data)
                        Log.d("XmlHelper", "Processed photo CDATA: ${data.length} chars for visit: ${currentDto.holyPlaceName}")
                    }
                } else {
                    // This is regular content (like comments) - add to currentValue
                    currentValue.append(data)
                    Log.d("XmlHelper", "Processed CDATA for '$currentOpeningTag': ${data.length} chars for visit: ${currentDto.holyPlaceName}")
                }
                break
            }
            
            // Limit data size to prevent memory issues
            if (cdataContent.length > MAX_PHOTO_SIZE_BYTES) {
                Log.w("XmlHelper", "CDATA content too large, skipping for visit: ${currentDto.holyPlaceName}")
                if (inPictureTag) {
                    currentDto.picture = null
                }
                break
            }
        }
    }

    /**
     * Process individual characters for photo data
     */
    private fun processPhotoCharacter(char: Char, currentDto: MutableVisitDto?) {
        if (currentDto?.picture == null) return
        
        // Check if adding this character would exceed our limit
        if ((currentDto.picture?.length ?: 0) >= MAX_PHOTO_SIZE_BYTES) {
            Log.w("XmlHelper", "Photo data too large, skipping for visit: ${currentDto.holyPlaceName}")
            currentDto.picture = null
            return
        }
        
        currentDto.picture?.append(char)
    }

    /**
     * Process regular XML tags
     */
    private fun processRegularTag(tag: String, value: String, currentDto: MutableVisitDto?) {
        if (currentDto == null) return
        
        val trimmedValue = value.trim()
        when (tag) {
            TAG_PLACE_ID -> {
                currentDto.placeID = trimmedValue
            }
            TAG_HOLY_PLACE_NAME -> {
                currentDto.holyPlaceName = trimmedValue
            }
            TAG_TYPE -> {
                currentDto.type = trimmedValue
            }
            TAG_DATE_VISITED -> {
                currentDto.dateVisitedString = trimmedValue
            }
            TAG_COMMENTS -> {
                currentDto.comments = trimmedValue
                if (trimmedValue.isNotEmpty()) {
                    Log.d("XmlHelper", "Set comments: ${trimmedValue.length} chars for visit: ${currentDto.holyPlaceName}")
                } else {
                    Log.d("XmlHelper", "No comments found for visit: ${currentDto.holyPlaceName}")
                }
            }
            TAG_IS_FAVORITE -> {
                currentDto.isFavorite = trimmedValue.toBooleanStrictOrNull()
            }
            TAG_HOURS_WORKED -> {
                currentDto.hoursWorked = trimmedValue.toDoubleOrNull()
            }
            TAG_SEALINGS -> {
                currentDto.sealings = trimmedValue.toShortOrNull()
            }
            TAG_ENDOWMENTS -> {
                currentDto.endowments = trimmedValue.toShortOrNull()
            }
            TAG_INITIATORIES -> {
                currentDto.initiatories = trimmedValue.toShortOrNull()
            }
            TAG_CONFIRMATIONS -> {
                currentDto.confirmations = trimmedValue.toShortOrNull()
            }
            TAG_BAPTISMS -> {
                currentDto.baptisms = trimmedValue.toShortOrNull()
            }
        }
    }

    /**
     * Process photo data in chunks to avoid memory issues
     */
    private fun processPhotoDataChunked(currentDto: MutableVisitDto?, photoData: String) {
        if (currentDto?.picture == null) return
        
        val currentLength = currentDto.picture?.length ?: 0
        val trimmedData = photoData.trim()
        
        // Check if adding this chunk would exceed our limit
        if (currentLength + trimmedData.length > MAX_PHOTO_SIZE_BYTES) {
            Log.w("XmlHelper", "Photo data too large (${currentLength + trimmedData.length} bytes), skipping for visit: ${currentDto.holyPlaceName}")
            currentDto.picture = null
            return
        }
        
        if (trimmedData.isNotEmpty()) {
            currentDto.picture?.append(trimmedData)
            Log.d("XmlHelper", "Added photo data chunk: ${trimmedData.length} chars for visit: ${currentDto.holyPlaceName}")
        }
    }

    /**
     * Process regular XML lines (non-photo data)
     */
    private fun processRegularXmlLine(currentDto: MutableVisitDto?, line: String) {
        if (currentDto == null) return
        
        when {
            line.startsWith("<$TAG_PLACE_ID>") && line.endsWith("</$TAG_PLACE_ID>") -> {
                currentDto.placeID = extractTextContent(line, TAG_PLACE_ID)
            }
            line.startsWith("<$TAG_HOLY_PLACE_NAME>") && line.endsWith("</$TAG_HOLY_PLACE_NAME>") -> {
                currentDto.holyPlaceName = extractTextContent(line, TAG_HOLY_PLACE_NAME)
            }
            line.startsWith("<$TAG_TYPE>") && line.endsWith("</$TAG_TYPE>") -> {
                currentDto.type = extractTextContent(line, TAG_TYPE)
            }
            line.startsWith("<$TAG_DATE_VISITED>") && line.endsWith("</$TAG_DATE_VISITED>") -> {
                currentDto.dateVisitedString = extractTextContent(line, TAG_DATE_VISITED)
            }
            line.startsWith("<$TAG_COMMENTS>") && line.endsWith("</$TAG_COMMENTS>") -> {
                currentDto.comments = extractTextContent(line, TAG_COMMENTS)
            }
            line.startsWith("<$TAG_IS_FAVORITE>") && line.endsWith("</$TAG_IS_FAVORITE>") -> {
                currentDto.isFavorite = extractTextContent(line, TAG_IS_FAVORITE).toBooleanStrictOrNull()
            }
            line.startsWith("<$TAG_HOURS_WORKED>") && line.endsWith("</$TAG_HOURS_WORKED>") -> {
                currentDto.hoursWorked = extractTextContent(line, TAG_HOURS_WORKED).toDoubleOrNull()
            }
            line.startsWith("<$TAG_SEALINGS>") && line.endsWith("</$TAG_SEALINGS>") -> {
                currentDto.sealings = extractTextContent(line, TAG_SEALINGS).toShortOrNull()
            }
            line.startsWith("<$TAG_ENDOWMENTS>") && line.endsWith("</$TAG_ENDOWMENTS>") -> {
                currentDto.endowments = extractTextContent(line, TAG_ENDOWMENTS).toShortOrNull()
            }
            line.startsWith("<$TAG_INITIATORIES>") && line.endsWith("</$TAG_INITIATORIES>") -> {
                currentDto.initiatories = extractTextContent(line, TAG_INITIATORIES).toShortOrNull()
            }
            line.startsWith("<$TAG_CONFIRMATIONS>") && line.endsWith("</$TAG_CONFIRMATIONS>") -> {
                currentDto.confirmations = extractTextContent(line, TAG_CONFIRMATIONS).toShortOrNull()
            }
            line.startsWith("<$TAG_BAPTISMS>") && line.endsWith("</$TAG_BAPTISMS>") -> {
                currentDto.baptisms = extractTextContent(line, TAG_BAPTISMS).toShortOrNull()
            }
        }
    }

    /**
     * Extract text content from XML tag
     */
    private fun extractTextContent(line: String, tag: String): String {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val startIndex = line.indexOf(startTag) + startTag.length
        val endIndex = line.indexOf(endTag)
        return if (startIndex > startTag.length - 1 && endIndex > startIndex) {
            line.substring(startIndex, endIndex).trim()
        } else {
            ""
        }
    }

    /**
     * Parser that completely skips photo data to prevent memory issues
     */
    @Throws(XmlPullParserException::class, IOException::class, XmlParseException::class)
    private fun parseVisitsXmlWithoutPhotos(inputStream: InputStream): List<VisitDto> {
        lastParseError = null // Reset before parsing
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            val visitsDto = mutableListOf<VisitDto>()
            var currentDto: MutableVisitDto? = null
            var currentTag: String? = null
            var skipPhotoData = false

            try {
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            if (currentTag == TAG_VISIT_ITEM) {
                                currentDto = MutableVisitDto()
                            } else if (currentTag == TAG_PICTURE) {
                                // Skip all photo data to prevent memory issues
                                skipPhotoData = true
                                Log.d("XmlHelper", "Skipping photo data for visit: ${currentDto?.holyPlaceName}")
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (currentDto != null && currentTag != null && !skipPhotoData) {
                                // Only process non-photo data
                                processRegularTextData(currentDto, currentTag, parser.text)
                                if (currentTag == TAG_COMMENTS && parser.text.trim().isNotEmpty()) {
                                    Log.d("XmlHelper", "Processed comments (no-photos): ${parser.text.length} chars for visit: ${currentDto.holyPlaceName}")
                                }
                            }
                            // Skip photo data entirely
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == TAG_VISIT_ITEM && currentDto != null) {
                                val hasComments = !currentDto.comments.isNullOrBlank()
                                Log.d("XmlHelper", "Completed visit (no-photos): ${currentDto.holyPlaceName}, hasComments: $hasComments")
                                
                                visitsDto.add(currentDto.toVisitDto())
                                currentDto = null
                                
                                // Force garbage collection every 10 visits
                                if (visitsDto.size % 10 == 0) {
                                    System.gc()
                                }
                            } else if (parser.name == TAG_PICTURE) {
                                skipPhotoData = false
                            }
                            currentTag = null
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: XmlPullParserException) {
                lastParseError = XmlParseException("XML parsing error at line ${parser.lineNumber}: ${e.message}", e)
                throw lastParseError!!
            } catch (e: Exception) {
                lastParseError = XmlParseException("Unexpected error during XML parsing: ${e.message}", e)
                throw lastParseError!!
            }

            // Count visits with comments for final logging
            val visitsWithComments = visitsDto.count { !it.comments.isNullOrBlank() }
            Log.d("XmlHelper", "No-photos parser completed: ${visitsDto.size} visits processed, $visitsWithComments visits with comments (photos skipped to prevent OOM)")

            return visitsDto
        }
    }
    
    /**
     * Streaming parser that handles large photo data without memory issues
     */
    @Throws(XmlPullParserException::class, IOException::class, XmlParseException::class)
    private fun parseVisitsXmlStreaming(inputStream: InputStream): List<VisitDto> {
        lastParseError = null // Reset before parsing
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            val visitsDto = mutableListOf<VisitDto>()
            var currentDto: MutableVisitDto? = null
            var currentTag: String? = null
            var inPictureTag = false
            var photoCount = 0

            try {
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            if (currentTag == TAG_VISIT_ITEM) {
                                currentDto = MutableVisitDto()
                            } else if (currentTag == TAG_PICTURE) {
                                inPictureTag = true
                                currentDto?.picture = StringBuilder()
                                Log.d("XmlHelper", "Started picture tag for visit: ${currentDto?.holyPlaceName}")
                            } else if (currentTag == TAG_COMMENTS) {
                                Log.d("XmlHelper", "Started comments tag for visit: ${currentDto?.holyPlaceName}")
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (currentDto != null && currentTag != null) {
                                if (inPictureTag && currentTag == TAG_PICTURE) {
                                    // Process photo data in streaming fashion
                                    processPhotoDataStreaming(currentDto, parser.text)
                                    Log.d("XmlHelper", "Processed photo text: ${parser.text.length} chars for visit: ${currentDto.holyPlaceName}")
                                } else {
                                    // Process regular text data
                                    processRegularTextData(currentDto, currentTag, parser.text)
                                    if (currentTag == TAG_COMMENTS && parser.text.trim().isNotEmpty()) {
                                        Log.d("XmlHelper", "Processed comments text: ${parser.text.length} chars for visit: ${currentDto.holyPlaceName}")
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == TAG_VISIT_ITEM && currentDto != null) {
                                // Count photos and clean up
                                val hasPhoto = currentDto.picture?.isNotEmpty() == true
                                val hasComments = !currentDto.comments.isNullOrBlank()
                                
                                if (hasPhoto) {
                                    photoCount++
                                }
                                
                                Log.d("XmlHelper", "Completed visit (streaming): ${currentDto.holyPlaceName}, hasPhoto: $hasPhoto, hasComments: $hasComments")
                                
                                visitsDto.add(currentDto.toVisitDto())
                                currentDto = null
                                
                                // Force garbage collection every 5 visits for better memory management
                                if (visitsDto.size % MEMORY_CLEANUP_INTERVAL == 0) {
                                    System.gc()
                                }
                            } else if (parser.name == TAG_PICTURE) {
                                inPictureTag = false
                                Log.d("XmlHelper", "Ended picture tag for visit: ${currentDto?.holyPlaceName}")
                            } else if (parser.name == TAG_COMMENTS) {
                                Log.d("XmlHelper", "Ended comments tag for visit: ${currentDto?.holyPlaceName}")
                            }
                            currentTag = null
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: XmlPullParserException) {
                lastParseError = XmlParseException("XML parsing error at line ${parser.lineNumber}: ${e.message}", e)
                throw lastParseError!!
            } catch (e: Exception) {
                lastParseError = XmlParseException("Unexpected error during XML parsing: ${e.message}", e)
                throw lastParseError!!
            }

            // Count visits with comments for final logging
            val visitsWithComments = visitsDto.count { !it.comments.isNullOrBlank() }
            Log.d("XmlHelper", "Streaming parser completed: ${visitsDto.size} visits processed, $photoCount photos imported, $visitsWithComments visits with comments")

            return visitsDto
        }
    }
    
    /**
     * Processes photo data in a streaming fashion to avoid memory issues
     */
    private fun processPhotoDataStreaming(currentDto: MutableVisitDto, text: String) {
        if (currentDto.picture == null) {
            currentDto.picture = StringBuilder()
        }
        
        val currentLength = currentDto.picture?.length ?: 0
        val textToAdd = text.trim()
        
        // Check if adding this text would exceed our size limit
        if (currentLength + textToAdd.length > MAX_PHOTO_SIZE_BYTES) {
            Log.w("XmlHelper", "Photo data too large (${currentLength + textToAdd.length} bytes), skipping for visit: ${currentDto.holyPlaceName}")
            currentDto.picture = null
            return
        }
        
        // Add text in small chunks to avoid large allocations
        if (textToAdd.isNotEmpty()) {
            currentDto.picture?.append(textToAdd)
        }
    }
    
    /**
     * Processes regular text data (non-photo)
     */
    private fun processRegularTextData(currentDto: MutableVisitDto, tag: String, text: String) {
        when (tag) {
            TAG_PLACE_ID -> currentDto.placeID = text.trim()
            TAG_HOLY_PLACE_NAME -> currentDto.holyPlaceName = text.trim()
            TAG_TYPE -> currentDto.type = text.trim()
            TAG_DATE_VISITED -> currentDto.dateVisitedString = text.trim()
            TAG_COMMENTS -> {
                currentDto.comments = text.trim()
                if (text.trim().isNotEmpty()) {
                    Log.d("XmlHelper", "Set comments via regular text: ${text.trim().length} chars for visit: ${currentDto.holyPlaceName}")
                }
            }
            TAG_IS_FAVORITE -> currentDto.isFavorite = text.trim().toBooleanStrictOrNull()
            TAG_HOURS_WORKED -> currentDto.hoursWorked = text.trim().toDoubleOrNull()
            TAG_SEALINGS -> currentDto.sealings = text.trim().toShortOrNull()
            TAG_ENDOWMENTS -> currentDto.endowments = text.trim().toShortOrNull()
            TAG_INITIATORIES -> currentDto.initiatories = text.trim().toShortOrNull()
            TAG_CONFIRMATIONS -> currentDto.confirmations = text.trim().toShortOrNull()
            TAG_BAPTISMS -> currentDto.baptisms = text.trim().toShortOrNull()
        }
    }
    
    /**
     * Parser that processes visits one at a time like iOS - immediately processes and frees memory
     */
    @Throws(XmlPullParserException::class, IOException::class, XmlParseException::class)
    private fun parseVisitsXmlStreamingLikeIOS(inputStream: InputStream): List<VisitDto> {
        lastParseError = null
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            val visitsDto = mutableListOf<VisitDto>()
            var currentDto: MutableVisitDto? = null
            var currentTag: String? = null
            var photoCount = 0
            var pictureBase64String = StringBuilder()

            try {
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            if (currentTag == TAG_VISIT_ITEM) {
                                currentDto = MutableVisitDto()
                            } else if (currentTag == TAG_PICTURE) {
                                // Initialize StringBuilder for accumulating Base64 data
                                pictureBase64String = StringBuilder()
                                Log.d("XmlHelper", "Started picture tag for visit: ${currentDto?.holyPlaceName}")
                            } else if (currentTag == TAG_COMMENTS) {
                                Log.d("XmlHelper", "Started comments tag for visit: ${currentDto?.holyPlaceName}")
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (currentDto != null && currentTag != null) {
                                when (currentTag) {
                                    TAG_PLACE_ID -> currentDto.placeID = parser.text.trim()
                                    TAG_HOLY_PLACE_NAME -> currentDto.holyPlaceName = parser.text.trim()
                                    TAG_TYPE -> currentDto.type = parser.text.trim()
                                    TAG_DATE_VISITED -> currentDto.dateVisitedString = parser.text.trim()
                                    TAG_COMMENTS -> {
                                        currentDto.comments = parser.text.trim()
                                        if (parser.text.trim().isNotEmpty()) {
                                            Log.d("XmlHelper", "Processed comments: ${parser.text.length} chars for visit: ${currentDto.holyPlaceName}")
                                        }
                                    }
                                    TAG_IS_FAVORITE -> currentDto.isFavorite = parser.text.trim().toBooleanStrictOrNull()
                                    TAG_HOURS_WORKED -> currentDto.hoursWorked = parser.text.trim().toDoubleOrNull()
                                    TAG_SEALINGS -> currentDto.sealings = parser.text.trim().toShortOrNull()
                                    TAG_ENDOWMENTS -> currentDto.endowments = parser.text.trim().toShortOrNull()
                                    TAG_INITIATORIES -> currentDto.initiatories = parser.text.trim().toShortOrNull()
                                    TAG_CONFIRMATIONS -> currentDto.confirmations = parser.text.trim().toShortOrNull()
                                    TAG_BAPTISMS -> currentDto.baptisms = parser.text.trim().toShortOrNull()
                                    TAG_PICTURE -> {
                                        // Accumulate Base64 data like iOS version
                                        val text = parser.text.trim()
                                        if (text.isNotEmpty()) {
                                            pictureBase64String.append(text)
                                            Log.d("XmlHelper", "Accumulating Base64 data, current length: ${pictureBase64String.length} chars for visit: ${currentDto.holyPlaceName}")
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == TAG_PICTURE && pictureBase64String.isNotEmpty()) {
                                // Process the accumulated Base64 string immediately like iOS version
                                val base64String = pictureBase64String.toString()
                                Log.d("XmlHelper", "Processing complete Base64 string, length: ${base64String.length} chars for visit: ${currentDto?.holyPlaceName}")
                                
                                try {
                                    // Decode Base64 to byte array immediately
                                    val pictureData = Base64.decode(base64String, Base64.DEFAULT)
                                    Log.d("XmlHelper", "Successfully decoded Base64 to ${pictureData.size} bytes for visit: ${currentDto?.holyPlaceName}")
                                    
                                    // Validate that the decoded data looks like valid image data
                                    if (isValidImageData(pictureData)) {
                                        // Store the Base64 string in the DTO (like iOS stores in Core Data)
                                        currentDto?.picture = StringBuilder(base64String)
                                        Log.d("XmlHelper", "Valid image data confirmed for visit: ${currentDto?.holyPlaceName}")
                                    } else {
                                        Log.w("XmlHelper", "Invalid image data for visit: ${currentDto?.holyPlaceName}")
                                        currentDto?.picture = null
                                    }
                                    
                                    // Clear the pictureData byte array to free memory immediately
                                    // (The Base64 string is already stored in the DTO)
                                    
                                } catch (e: OutOfMemoryError) {
                                    Log.w("XmlHelper", "OutOfMemoryError decoding photo for visit: ${currentDto?.holyPlaceName}, skipping photo", e)
                                    currentDto?.picture = null
                                } catch (e: Exception) {
                                    Log.w("XmlHelper", "Failed to decode Base64 image data for visit: ${currentDto?.holyPlaceName}", e)
                                    currentDto?.picture = null
                                }
                                
                                // Clear the accumulator and force garbage collection
                                pictureBase64String.clear()
                                System.gc() // Force garbage collection after each photo
                            } else if (parser.name == TAG_VISIT_ITEM && currentDto != null) {
                                val hasPhoto = currentDto.picture?.isNotEmpty() == true
                                val hasComments = !currentDto.comments.isNullOrBlank()
                                
                                if (hasPhoto) {
                                    photoCount++
                                }
                                
                                Log.d("XmlHelper", "Completed visit (iOS-style): ${currentDto.holyPlaceName}, hasPhoto: $hasPhoto, hasComments: $hasComments")
                                
                                visitsDto.add(currentDto.toVisitDto())
                                currentDto = null
                                
                                // Force garbage collection every 5 visits to manage memory
                                if (visitsDto.size % 5 == 0) {
                                    System.gc()
                                }
                            }
                            currentTag = null
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: XmlPullParserException) {
                lastParseError = XmlParseException("XML parsing error at line ${parser.lineNumber}: ${e.message}", e)
                throw lastParseError!!
            } catch (e: Exception) {
                lastParseError = XmlParseException("Unexpected error during XML parsing: ${e.message}", e)
                throw lastParseError!!
            }

            // Count visits with comments for final logging
            val visitsWithComments = visitsDto.count { !it.comments.isNullOrBlank() }
            Log.d("XmlHelper", "iOS-style parser completed: ${visitsDto.size} visits processed, $photoCount photos imported, $visitsWithComments visits with comments")
            
            return visitsDto
        }
    }

    /**
     * Parser that handles photos efficiently like iOS version - processes photos immediately to free memory
     */
    @Throws(XmlPullParserException::class, IOException::class, XmlParseException::class)
    private fun parseVisitsXmlWithPhotoLimits(inputStream: InputStream): List<VisitDto> {
        lastParseError = null
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            val visitsDto = mutableListOf<VisitDto>()
            var currentDto: MutableVisitDto? = null
            var currentTag: String? = null
            var photoCount = 0
            var pictureBase64String = StringBuilder()

            try {
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            if (currentTag == TAG_VISIT_ITEM) {
                                currentDto = MutableVisitDto()
                            } else if (currentTag == TAG_PICTURE) {
                                // Initialize StringBuilder for accumulating Base64 data
                                pictureBase64String = StringBuilder()
                                Log.d("XmlHelper", "Started picture tag for visit: ${currentDto?.holyPlaceName}")
                            } else if (currentTag == TAG_COMMENTS) {
                                Log.d("XmlHelper", "Started comments tag for visit: ${currentDto?.holyPlaceName}")
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (currentDto != null && currentTag != null) {
                                when (currentTag) {
                                    TAG_PLACE_ID -> currentDto.placeID = parser.text.trim()
                                    TAG_HOLY_PLACE_NAME -> currentDto.holyPlaceName = parser.text.trim()
                                    TAG_TYPE -> currentDto.type = parser.text.trim()
                                    TAG_DATE_VISITED -> currentDto.dateVisitedString = parser.text.trim()
                                    TAG_COMMENTS -> {
                                        currentDto.comments = parser.text.trim()
                                        if (parser.text.trim().isNotEmpty()) {
                                            Log.d("XmlHelper", "Processed comments: ${parser.text.length} chars for visit: ${currentDto.holyPlaceName}")
                                        }
                                    }
                                    TAG_IS_FAVORITE -> currentDto.isFavorite = parser.text.trim().toBooleanStrictOrNull()
                                    TAG_HOURS_WORKED -> currentDto.hoursWorked = parser.text.trim().toDoubleOrNull()
                                    TAG_SEALINGS -> currentDto.sealings = parser.text.trim().toShortOrNull()
                                    TAG_ENDOWMENTS -> currentDto.endowments = parser.text.trim().toShortOrNull()
                                    TAG_INITIATORIES -> currentDto.initiatories = parser.text.trim().toShortOrNull()
                                    TAG_CONFIRMATIONS -> currentDto.confirmations = parser.text.trim().toShortOrNull()
                                    TAG_BAPTISMS -> currentDto.baptisms = parser.text.trim().toShortOrNull()
                                    TAG_PICTURE -> {
                                        // Accumulate Base64 data like iOS version
                                        val text = parser.text.trim()
                                        if (text.isNotEmpty()) {
                                            pictureBase64String.append(text)
                                            Log.d("XmlHelper", "Accumulating Base64 data, current length: ${pictureBase64String.length} chars for visit: ${currentDto.holyPlaceName}")
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == TAG_PICTURE && pictureBase64String.isNotEmpty()) {
                                // Process the accumulated Base64 string immediately like iOS version
                                val base64String = pictureBase64String.toString()
                                Log.d("XmlHelper", "Processing complete Base64 string, length: ${base64String.length} chars for visit: ${currentDto?.holyPlaceName}")
                                
                                try {
                                    // Decode Base64 to byte array immediately
                                    val pictureData = Base64.decode(base64String, Base64.DEFAULT)
                                    Log.d("XmlHelper", "Successfully decoded Base64 to ${pictureData.size} bytes for visit: ${currentDto?.holyPlaceName}")
                                    
                                    // Validate that the decoded data looks like valid image data
                                    if (isValidImageData(pictureData)) {
                                        // Store the Base64 string in the DTO (like iOS stores in Core Data)
                                        currentDto?.picture = StringBuilder(base64String)
                                        Log.d("XmlHelper", "Valid image data confirmed for visit: ${currentDto?.holyPlaceName}")
                                    } else {
                                        Log.w("XmlHelper", "Invalid image data for visit: ${currentDto?.holyPlaceName}")
                                        currentDto?.picture = null
                                    }
                                    
                                    // Clear the pictureData byte array to free memory immediately
                                    // (The Base64 string is already stored in the DTO)
                                    
                                } catch (e: OutOfMemoryError) {
                                    Log.w("XmlHelper", "OutOfMemoryError decoding photo for visit: ${currentDto?.holyPlaceName}, skipping photo", e)
                                    currentDto?.picture = null
                                } catch (e: Exception) {
                                    Log.w("XmlHelper", "Failed to decode Base64 image data for visit: ${currentDto?.holyPlaceName}", e)
                                    currentDto?.picture = null
                                }
                                
                                // Clear the accumulator and force garbage collection
                                pictureBase64String.clear()
                                System.gc() // Force garbage collection after each photo
                            } else if (parser.name == TAG_VISIT_ITEM && currentDto != null) {
                                val hasPhoto = currentDto.picture?.isNotEmpty() == true
                                val hasComments = !currentDto.comments.isNullOrBlank()
                                
                                if (hasPhoto) {
                                    photoCount++
                                }
                                
                                Log.d("XmlHelper", "Completed visit (iOS-style): ${currentDto.holyPlaceName}, hasPhoto: $hasPhoto, hasComments: $hasComments")
                                
                                visitsDto.add(currentDto.toVisitDto())
                                currentDto = null
                                
                                // Force garbage collection every 5 visits to manage memory
                                if (visitsDto.size % 5 == 0) {
                                    System.gc()
                                }
                            }
                            currentTag = null
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: XmlPullParserException) {
                lastParseError = XmlParseException("XML parsing error at line ${parser.lineNumber}: ${e.message}", e)
                throw lastParseError!!
            } catch (e: Exception) {
                lastParseError = XmlParseException("Unexpected error during XML parsing: ${e.message}", e)
                throw lastParseError!!
            }

            // Count visits with comments for final logging
            val visitsWithComments = visitsDto.count { !it.comments.isNullOrBlank() }
            Log.d("XmlHelper", "iOS-style parser completed: ${visitsDto.size} visits processed, $photoCount photos imported, $visitsWithComments visits with comments")
            
            return visitsDto
        }
    }
    
    /**
     * Legacy method - kept for compatibility
     */
    @Throws(XmlPullParserException::class, IOException::class, XmlParseException::class)
    private fun parseVisitsXmlWithPhotoLimit(inputStream: InputStream, maxPhotos: Int): List<VisitDto> {
        lastParseError = null // Reset before parsing
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            val visitsDto = mutableListOf<VisitDto>()
            var currentDto: MutableVisitDto? = null // Use a mutable holder
            var currentTag: String? = null
            var photoCount = 0 // Track photos to prevent memory issues
            var skipPhotoData = false // Flag to skip photo content

            try {
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            if (currentTag == TAG_VISIT_ITEM) {
                                currentDto = MutableVisitDto()
                            } else if (currentTag == TAG_PICTURE) {
                                // Skip photo data if we've hit the limit
                                if (photoCount >= maxPhotos) {
                                    skipPhotoData = true
                                    Log.w("XmlHelper", "Photo limit reached ($maxPhotos), skipping remaining photos")
                                } else {
                                    skipPhotoData = false
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text
                            if (text != null && currentDto != null && currentTag != null) {
                                when (currentTag) {
                                    TAG_PLACE_ID -> currentDto.placeID = text.trim()
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
                                    TAG_PICTURE -> {
                                        // Skip photo data if flag is set or if we've hit limits
                                        if (skipPhotoData) {
                                            // Do nothing - skip the photo data entirely
                                        } else if (!processPhotoData(currentDto, text)) {
                                            Log.w("XmlHelper", "Skipping photo data due to size limits")
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == TAG_VISIT_ITEM && currentDto != null) {
                                // Count photos and clean up memory
                                if (currentDto.picture?.isNotEmpty() == true) {
                                    photoCount++
                                }
                                
                                visitsDto.add(currentDto.toVisitDto())
                                currentDto = null // Reset for next Visit item
                                
                                // Force garbage collection periodically to free memory
                                if (visitsDto.size % 5 == 0) {
                                    System.gc()
                                }
                            } else if (parser.name == TAG_PICTURE) {
                                // Reset skip flag when photo tag ends
                                skipPhotoData = false
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
        var placeID: String? = null,
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
        var baptisms: Short? = null,
        var picture: StringBuilder? = null // Use StringBuilder for efficient string building
    ) {
        fun toVisitDto() = VisitDto(
            placeID, holyPlaceName, type, dateVisitedString, comments, isFavorite,
            hoursWorked, sealings, endowments, initiatories, confirmations, baptisms, 
            picture?.toString() // Convert StringBuilder to String only when needed
        )
    }
}
