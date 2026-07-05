package net.dacworld.android.holyplacesofthelord.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import net.dacworld.android.holyplacesofthelord.model.TempleNameChange

@Dao
interface NameChangeDao {

    @Query("SELECT * FROM temple_name_changes WHERE temple_id = :templeId")
    suspend fun getNameChangesForTemple(templeId: String): List<TempleNameChange>

    /** Resolves a historical name to its name-change row (e.g. for visit import matching). */
    @Query("SELECT * FROM temple_name_changes WHERE old_name = :oldName LIMIT 1")
    suspend fun getByOldName(oldName: String): TempleNameChange?

    @Query("SELECT * FROM temple_name_changes")
    suspend fun getAllNameChanges(): List<TempleNameChange>

    /** Name changes that have a change date (the ones relevant for date-aware naming). */
    @Query("SELECT * FROM temple_name_changes WHERE change_date IS NOT NULL")
    suspend fun getDatedNameChanges(): List<TempleNameChange>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nameChanges: List<TempleNameChange>)

    @Query("DELETE FROM temple_name_changes WHERE temple_id = :templeId")
    suspend fun deleteForTemple(templeId: String)

    @Query("DELETE FROM temple_name_changes")
    suspend fun deleteAll()

    /**
     * Replaces the name-change rows for a temple, preserving any cached
     * old_image_data whose old_image_url is unchanged.
     */
    @Transaction
    suspend fun replaceForTemple(templeId: String, newChanges: List<TempleNameChange>) {
        val existing = getNameChangesForTemple(templeId)
        val existingKeys = existing.map { Triple(it.oldName, it.changeDate, it.oldImageUrl) }.toSet()
        val newKeys = newChanges.map { Triple(it.oldName, it.changeDate, it.oldImageUrl) }.toSet()
        if (existingKeys == newKeys) return // unchanged; keep cached image data
        deleteForTemple(templeId)
        if (newChanges.isEmpty()) return
        val toInsert = newChanges.map { change ->
            val cached = existing.firstOrNull {
                it.oldName == change.oldName &&
                    it.oldImageUrl == change.oldImageUrl &&
                    it.oldImageData != null
            }
            // id = 0 lets Room autogenerate a fresh key
            change.copy(id = 0, templeId = templeId, oldImageData = cached?.oldImageData)
        }
        insertAll(toInsert)
    }

    @Query("UPDATE temple_name_changes SET old_image_data = :imageData WHERE name_change_id = :id")
    suspend fun updateOldImageData(id: Long, imageData: ByteArray?)

    /** Rows that declare an old image URL but have no cached image bytes yet. */
    @Query("SELECT * FROM temple_name_changes WHERE old_image_url IS NOT NULL AND old_image_url != '' AND old_image_data IS NULL")
    suspend fun getNameChangesNeedingImageDownload(): List<TempleNameChange>
}
