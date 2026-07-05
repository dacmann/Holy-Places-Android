package net.dacworld.android.holyplacesofthelord.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import net.dacworld.android.holyplacesofthelord.dao.NameChangeDao
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.model.TempleNameChange
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.model.effectiveName
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Shared logic for the Historical Names feature (iOS 5.6/5.7 parity):
 * persisting name-change history, keeping visit names date-accurate, and
 * caching historical place images.
 */
object HistoricalNamesHelper {

    private const val TAG = "HistoricalNames"

    fun toLocalDate(date: Date?): LocalDate? =
        date?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

    /**
     * Builds a map of historical name -> current (canonical) temple name.
     * Mirrors iOS `canonicalName(for:)` — used so Summary/achievement stats
     * don't double-count a renamed temple.
     */
    suspend fun buildCanonicalNameMap(
        templeDao: TempleDao,
        nameChangeDao: NameChangeDao
    ): Map<String, String> {
        val changes = nameChangeDao.getAllNameChanges()
        if (changes.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for ((templeId, templeChanges) in changes.groupBy { it.templeId }) {
            val currentName = templeDao.getTempleByIdForSync(templeId)?.name ?: continue
            for (change in templeChanges) {
                if (change.oldName != currentName) {
                    map[change.oldName] = currentName
                }
            }
        }
        return map
    }

    /**
     * Returns copies of [visits] whose holyPlaceName has been normalized to the
     * canonical (current) temple name for aggregation purposes.
     */
    fun normalizeVisitNames(visits: List<Visit>, canonicalNameMap: Map<String, String>): List<Visit> {
        if (canonicalNameMap.isEmpty()) return visits
        return visits.map { visit ->
            val canonical = visit.holyPlaceName?.let { canonicalNameMap[it] }
            if (canonical != null) visit.copy(holyPlaceName = canonical) else visit
        }
    }

    /** Persists the parser-populated name changes for every temple in [temples]. */
    suspend fun persistNameChanges(nameChangeDao: NameChangeDao, temples: List<Temple>) {
        for (temple in temples) {
            if (temple.id.isBlank()) continue
            nameChangeDao.replaceForTemple(temple.id, temple.nameChanges)
        }
    }

    /**
     * Ensures every visit of [templeId] carries the name that was in use on its
     * visit date: the historical name before a rename's changeDate, the current
     * name after. Idempotent — safe to run on every sync (mirrors the combined
     * behavior of iOS `storePlaces()` renames and `revertMisnamedVisits()`).
     *
     * Returns the visits that need updating (caller executes the DB write).
     */
    fun reconcileVisitNames(
        visits: List<Visit>,
        currentName: String,
        nameChanges: List<TempleNameChange>,
        additionalKnownNames: Set<String> = emptySet()
    ): List<Visit> {
        val knownNames = buildSet {
            add(currentName)
            nameChanges.forEach { add(it.oldName) }
            addAll(additionalKnownNames)
        }
        val updates = mutableListOf<Visit>()
        for (visit in visits) {
            val visitDate = toLocalDate(visit.dateVisited) ?: continue
            val expectedName = nameChanges.effectiveName(currentName, visitDate)
            // Only touch names we recognize; leave user-customized names alone
            // unless the stored name is one of this temple's known names.
            if (visit.holyPlaceName != expectedName &&
                (visit.holyPlaceName == null || visit.holyPlaceName in knownNames)
            ) {
                updates.add(visit.copy(holyPlaceName = expectedName))
            }
        }
        return updates
    }

    /**
     * Runs [reconcileVisitNames] for every temple that has name-change history
     * and writes the corrections. Returns the number of updated visits.
     */
    suspend fun reconcileAllVisitNames(
        templeDao: TempleDao,
        visitDao: VisitDao,
        nameChangeDao: NameChangeDao
    ): Int {
        val changesByTemple = nameChangeDao.getAllNameChanges().groupBy { it.templeId }
        if (changesByTemple.isEmpty()) return 0
        var updatedCount = 0
        for ((templeId, changes) in changesByTemple) {
            val temple = templeDao.getTempleByIdForSync(templeId) ?: continue
            val visits = visitDao.getVisitsListForTempleId(templeId)
            if (visits.isEmpty()) continue
            val updates = reconcileVisitNames(visits, temple.name, changes)
            if (updates.isNotEmpty()) {
                updatedCount += visitDao.updateVisits(updates)
                Log.i(TAG, "Reconciled ${updates.size} visit name(s) for '${temple.name}'.")
            }
        }
        return updatedCount
    }

    /**
     * Downloads any historical place images (oldImage URLs) that are not yet
     * cached locally. Failures are logged and retried on a later run.
     */
    suspend fun downloadMissingOldImages(context: Context, nameChangeDao: NameChangeDao) {
        val pending = nameChangeDao.getNameChangesNeedingImageDownload()
        if (pending.isEmpty()) return
        Log.d(TAG, "Downloading ${pending.size} historical place image(s).")
        val imageLoader = ImageLoader.Builder(context.applicationContext).build()
        for (change in pending) {
            val url = change.oldImageUrl ?: continue
            val imageData = fetchImage(context, imageLoader, url)
            if (imageData != null) {
                nameChangeDao.updateOldImageData(change.id, imageData)
                Log.d(TAG, "Cached historical image for '${change.oldName}' (${imageData.size} bytes).")
            } else {
                Log.w(TAG, "Failed to download historical image for '${change.oldName}' from $url")
            }
        }
    }

    private suspend fun fetchImage(context: Context, imageLoader: ImageLoader, imageUrl: String): ByteArray? {
        return try {
            val request = ImageRequest.Builder(context.applicationContext)
                .data(imageUrl)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    stream.toByteArray()
                }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching historical image from $imageUrl", e)
            null
        }
    }
}
