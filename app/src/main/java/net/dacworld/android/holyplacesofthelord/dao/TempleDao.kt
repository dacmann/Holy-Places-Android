package net.dacworld.android.holyplacesofthelord.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.model.TempleContract // If you created this

@Dao
interface TempleDao {

    /**
     * Fetches all temples, but the Temple objects returned will have pictureData as null.
     * This is intended for synchronization and list displays where the full blob is not needed.
     * The `picture_url` IS selected.
     */
    @Query("SELECT temple_id, name, address, snippet, city_state, country, phone, latitude, longitude, \"order\", announced_date, site_url, type, info_url, sq_ft, fh_code, picture_url, (picture_data IS NOT NULL) as hasLocalPictureData FROM temples ORDER BY name ASC")
    suspend fun getAllTemplesForSyncOrList(): List<Temple> // pictureData will be null

    /**
     * Fetches a single temple by its ID, including its pictureData BLOB.
     * Use this when displaying detailed information for a specific temple.
     */
    @Query("SELECT * FROM temples WHERE temple_id = :id")
    suspend fun getTempleWithPictureById(id: String): Temple?

    /**
     * Gets the count of all Temple entities of a specific type.
     */
    @Query("SELECT COUNT(temple_id) FROM temples WHERE type = :type")
    suspend fun getCountByType(type: String): Int

    /**
     * Fetches a temple's ID by its exact name.
     * Used for linking imported visits to existing temples.
     */
    @Query("SELECT temple_id FROM temples WHERE name = :name LIMIT 1")
    suspend fun getTempleIdByName(name: String): String?

    // <<< START: ADDITIONS FOR ExportImportViewModel >>>
    /**
     * Fetches a single temple by its ID.
     * Intended for one-shot synchronous fetching (within a coroutine), e.g., during data import.
     * This version selects all columns, including pictureData if available and needed.
     * If pictureData is not strictly needed for this operation, consider creating a variant
     * that excludes it for performance, similar to getAllTemplesForSyncOrList.
     * For current ExportImportViewModel usage, the full Temple object is convenient.
     */
    @Query("SELECT * FROM temples WHERE temple_id = :id")
    suspend fun getTempleByIdForSync(id: String): Temple?

    /**
     * Fetches a single temple by its exact name.
     * Intended for one-shot synchronous fetching (within a coroutine), e.g., as a fallback during data import.
     * Returns the full Temple object.
     * Ensure that 'name' has a unique constraint or be aware this will fetch the first match if names are not unique.
     */
    @Query("SELECT * FROM temples WHERE name = :name LIMIT 1")
    suspend fun getTempleByNameForSync(name: String): Temple?
    // <<< END: ADDITIONS FOR ExportImportViewModel >>>

    /**
     * Inserts a temple. If the Temple object has pictureData, it will be written.
     * During initial sync from XML, pictureData should be null.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(temple: Temple): Long

    /**
     * Updates a temple. If the Temple object has pictureData, it will be written.
     * Used for updating metadata. pictureData on the passed Temple object should be null
     * if only metadata is being updated.
     */
    @Update
    suspend fun update(temple: Temple): Int

    /**
     * Specifically updates the picture_url and picture_data for a given temple ID.
     * This is the preferred method for updating image information after fetching.
     */
    @Query("UPDATE temples SET picture_url = :pictureUrl, picture_data = :pictureData WHERE temple_id = :id")
    suspend fun updatePicture(id: String, pictureUrl: String?, pictureData: ByteArray?)

    /**
     * Fetches all temple IDs. Used for identifying orphans.
     */
    @Query("SELECT temple_id FROM temples")
    suspend fun getAllTempleIds(): List<String>

    /**
     * Deletes temples by their IDs. Used for removing orphans.
     */
    @Query("DELETE FROM temples WHERE temple_id IN (:ids)")
    suspend fun deleteTemplesByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemple(temple: Temple) // Use suspend for coroutines

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTemples(temples: List<Temple>)

    @Query("SELECT temple_id, name, snippet, city_state, country, picture_url, latitude, longitude, \"order\", announced_date, type, sq_ft, fh_code FROM temples ORDER BY name ASC")
    fun getAllTemples(): Flow<List<Temple>> // Flow for reactive updates

    // New method to get a simple list for comparison
    @Query("SELECT temple_id, name, snippet, city_state, country, picture_url, latitude, longitude, \"order\", announced_date, type, sq_ft, fh_code FROM temples ORDER BY name ASC")
    suspend fun getAllTemplesList(): List<Temple>

    @Query("SELECT * FROM temples WHERE temple_id = :id")
    suspend fun getTempleById(id: String): Temple?

    @Query("SELECT temple_id, name, snippet, city_state, country, picture_url, latitude, longitude, \"order\", announced_date, type, sq_ft, fh_code FROM ${TempleContract.TABLE_NAME} WHERE name LIKE :searchQuery || '%' ORDER BY name ASC")
    fun searchTemplesByName(searchQuery: String): Flow<List<Temple>>

    // Example: Query to get temples by country
    @Query("SELECT temple_id, name, snippet, city_state, country, picture_url, latitude, longitude, \"order\", announced_date, type, sq_ft, fh_code FROM ${TempleContract.TABLE_NAME} WHERE country = :countryName ORDER BY name ASC")
    fun getTemplesByCountry(countryName: String): Flow<List<Temple>>

    // If you need a version that doesn't use Flow (e.g., for one-shot operations in a background thread)
    @Query("SELECT * FROM ${TempleContract.TABLE_NAME} WHERE ${TempleContract.COLUMN_ID} = :templeId")
    suspend fun getTempleByIdOnce(templeId: String): Temple?

    @Query("SELECT COUNT(*) FROM ${TempleContract.TABLE_NAME}")
    suspend fun getTempleCount(): Int

    // --- Update Operations ---

    @Update
    suspend fun updateTemple(temple: Temple)

    // --- Delete Operations ---

    @Delete
    suspend fun deleteTemple(temple: Temple)

    @Query("DELETE FROM ${TempleContract.TABLE_NAME} WHERE ${TempleContract.COLUMN_ID} = :templeId")
    suspend fun deleteTempleById(templeId: String): Int // Returns number of rows deleted

    @Query("DELETE FROM ${TempleContract.TABLE_NAME}")
    suspend fun deleteAllTemples()

    @Query("DELETE FROM temples") // Assuming your table is named 'temples'
    suspend fun clearAllTemples()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(temples: List<Temple>)

    @Transaction
    suspend fun clearAndInsertAll(temples: List<Temple>) {
        clearAllTemples()
        insertAll(temples)
    }
}
