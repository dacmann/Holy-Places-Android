package net.dacworld.android.holyplacesofthelord.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.model.VisitContract // If you created this

@Dao
interface VisitDao {

    // --- Insert Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE) // Or IGNORE, ABORT if needed
    suspend fun insertVisit(visit: Visit): Long // Returns the new rowId for the inserted item

    // --- Query Operations ---

    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT " +
            "${VisitContract.COLUMN_ID}, " +
            "${VisitContract.COLUMN_PLACE_ID}, " +
            "${VisitContract.COLUMN_BAPTISMS}, " +
            "${VisitContract.COLUMN_COMMENTS}, " +
            "${VisitContract.COLUMN_CONFIRMATIONS}, " +
            "${VisitContract.COLUMN_DATE_VISITED}, " +
            "${VisitContract.COLUMN_ENDOWMENTS}, " +
            "${VisitContract.COLUMN_HOLY_PLACE_NAME}, " +
            "${VisitContract.COLUMN_INITIATORIES}, " +
            "${VisitContract.COLUMN_IS_FAVORITE}, " +
            // VisitContract.COLUMN_PICTURE_DATA IS INTENTIONALLY OMITTED HERE
            "${VisitContract.COLUMN_SEALINGS}, " +
            "${VisitContract.COLUMN_SHIFT_HRS}, " +
            "${VisitContract.COLUMN_VISIT_TYPE}, " +
            "${VisitContract.COLUMN_YEAR}, " + // Last column, no trailing comma
            "${VisitContract.COLUMN_HAS_PICTURE} " +
            "FROM ${VisitContract.TABLE_NAME} ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    fun getAllVisits(): Flow<List<Visit>>

    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT " +
            "${VisitContract.COLUMN_ID}, " +
            "${VisitContract.COLUMN_PLACE_ID}, " +
            "${VisitContract.COLUMN_BAPTISMS}, " +
            "${VisitContract.COLUMN_COMMENTS}, " +
            "${VisitContract.COLUMN_CONFIRMATIONS}, " +
            "${VisitContract.COLUMN_DATE_VISITED}, " +
            "${VisitContract.COLUMN_ENDOWMENTS}, " +
            "${VisitContract.COLUMN_HOLY_PLACE_NAME}, " +
            "${VisitContract.COLUMN_INITIATORIES}, " +
            "${VisitContract.COLUMN_IS_FAVORITE}, " +
            // IMPORTANT: VisitContract.COLUMN_PICTURE_DATA (the blob) is OMITTED
            "${VisitContract.COLUMN_SEALINGS}, " +
            "${VisitContract.COLUMN_SHIFT_HRS}, " +
            "${VisitContract.COLUMN_VISIT_TYPE}, " +
            "${VisitContract.COLUMN_YEAR}, " +
            "${VisitContract.COLUMN_HAS_PICTURE} " + // INCLUDE the new has_picture flag
            "FROM ${VisitContract.TABLE_NAME} ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    fun getVisitsForListAdapter(): Flow<List<Visit>>

    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_ID} = :visitId")
    fun getVisitById(visitId: Long): Flow<Visit?>

    // Get all visits for a specific Temple
    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_PLACE_ID} = :templeId ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    fun getVisitsForTemple(templeId: String): Flow<List<Visit>>

    // NEW: Suspend function to get a list of visits for a specific Temple ID (for background processing)
    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_PLACE_ID} = :templeId")
    suspend fun getVisitsListForTempleId(templeId: String): List<Visit>

    // Get favorite visits
    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_IS_FAVORITE} = 1 ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    fun getFavoriteVisits(): Flow<List<Visit>>

    // Example: Get visits within a specific year
    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_YEAR} = :year ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    fun getVisitsByYear(year: String): Flow<List<Visit>>

    @Query("SELECT COUNT(*) FROM ${VisitContract.TABLE_NAME}")
    suspend fun getVisitCount(): Int

    @Query("SELECT DISTINCT ${VisitContract.COLUMN_PLACE_ID} FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_PLACE_ID} IS NOT NULL AND ${VisitContract.COLUMN_PLACE_ID} != ''")
    fun getVisitedTemplePlaceIds(): Flow<List<String>> // <<< CHANGE HERE from Flow<Set<String>>

    // --- Update Operations ---

    @Update
    suspend fun updateVisit(visit: Visit)
    // NEW: Suspend function to update a list of visits (batch update)
    @Update
    suspend fun updateVisits(visits: List<Visit>): Int // Returns number of rows updated


    // --- Delete Operations ---

    @Delete
    suspend fun deleteVisit(visit: Visit)

    @Query("DELETE FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_ID} = :visitId")
    suspend fun deleteVisitById(visitId: Long): Int // Returns number of rows deleted

    @Query("DELETE FROM ${VisitContract.TABLE_NAME}")
    suspend fun deleteAllVisits()

    /**
     * Fetches all visits sorted by date DESC (newest first), for achievement calculation.
     * Picture data excluded for performance.
     */
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT " +
            "${VisitContract.COLUMN_ID}, " +
            "${VisitContract.COLUMN_PLACE_ID}, " +
            "${VisitContract.COLUMN_BAPTISMS}, " +
            "${VisitContract.COLUMN_COMMENTS}, " +
            "${VisitContract.COLUMN_CONFIRMATIONS}, " +
            "${VisitContract.COLUMN_DATE_VISITED}, " +
            "${VisitContract.COLUMN_ENDOWMENTS}, " +
            "${VisitContract.COLUMN_HOLY_PLACE_NAME}, " +
            "${VisitContract.COLUMN_INITIATORIES}, " +
            "${VisitContract.COLUMN_IS_FAVORITE}, " +
            "${VisitContract.COLUMN_SEALINGS}, " +
            "${VisitContract.COLUMN_SHIFT_HRS}, " +
            "${VisitContract.COLUMN_VISIT_TYPE}, " +
            "${VisitContract.COLUMN_YEAR}, " +
            "${VisitContract.COLUMN_HAS_PICTURE} " +
            "FROM ${VisitContract.TABLE_NAME} ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    suspend fun getAllVisitsForAchievementCalc(): List<Visit>

    /**
     * Fetches all visits sorted by date, intended for export operations.
     * The pictureData column (VisitContract.COLUMN_PICTURE_DATA) is EXCLUDED
     * from this query to prevent SQLiteBlobTooBigException.
     */
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT " +
            "${VisitContract.COLUMN_ID}, " +
            "${VisitContract.COLUMN_PLACE_ID}, " +
            "${VisitContract.COLUMN_BAPTISMS}, " +
            "${VisitContract.COLUMN_COMMENTS}, " +
            "${VisitContract.COLUMN_CONFIRMATIONS}, " +
            "${VisitContract.COLUMN_DATE_VISITED}, " +
            "${VisitContract.COLUMN_ENDOWMENTS}, " +
            "${VisitContract.COLUMN_HOLY_PLACE_NAME}, " +
            "${VisitContract.COLUMN_INITIATORIES}, " +
            "${VisitContract.COLUMN_IS_FAVORITE}, " +
            // VisitContract.COLUMN_PICTURE_DATA IS INTENTIONALLY OMITTED HERE
            "${VisitContract.COLUMN_SEALINGS}, " +
            "${VisitContract.COLUMN_SHIFT_HRS}, " +
            "${VisitContract.COLUMN_VISIT_TYPE}, " +
            "${VisitContract.COLUMN_YEAR}, " + // Last column, no trailing comma
            "${VisitContract.COLUMN_HAS_PICTURE} " +
            "FROM ${VisitContract.TABLE_NAME} ORDER BY ${VisitContract.COLUMN_DATE_VISITED} ASC")
    suspend fun getAllVisitsListForExport(): List<Visit>

    /**
     * Gets a single visit with picture data by ID
     * Used for loading photo data individually during export
     */
    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_ID} = :visitId")
    suspend fun getVisitWithPictureById(visitId: Long): Visit?

    /**
     * Checks if a visit already exists with the given holy place name and date.
     * Note: dateVisitedMillis should be the Long representation of the Date (e.g., from Date.getTime()).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_HOLY_PLACE_NAME} = :holyPlaceName AND ${VisitContract.COLUMN_DATE_VISITED} = :dateVisitedMillis LIMIT 1)")
    suspend fun visitExistsByNameAndDate(holyPlaceName: String, dateVisitedMillis: Long): Boolean

    /**
     * Checks if a visit exists for a given holy place name on a specific calendar day.
     * @param holyPlaceName The name of the holy place.
     * @param startOfDayMillis The millisecond timestamp for the beginning of the day (e.g., 00:00:00.000).
     * @param endOfDayMillis The millisecond timestamp for the beginning of the NEXT day (exclusive upper bound).
     * @return True if a visit exists within that day range, false otherwise.
     */
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT EXISTS(SELECT 1 FROM ${VisitContract.TABLE_NAME} WHERE " +
            "${VisitContract.COLUMN_HOLY_PLACE_NAME} = :holyPlaceName AND " +
            "${VisitContract.COLUMN_DATE_VISITED} >= :startOfDayMillis AND " +
            "${VisitContract.COLUMN_DATE_VISITED} < :endOfDayMillis LIMIT 1)")
    suspend fun visitExistsByNameAndDayRange(holyPlaceName: String, startOfDayMillis: Long, endOfDayMillis: Long): Boolean

    /**
     * Gets a visit for a given holy place name on a specific calendar day.
     * @param holyPlaceName The name of the holy place.
     * @param startOfDayMillis The millisecond timestamp for the beginning of the day (e.g., 00:00:00.000).
     * @param endOfDayMillis The millisecond timestamp for the beginning of the NEXT day (exclusive upper bound).
     * @return The visit if it exists within that day range, null otherwise.
     */
    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE " +
            "${VisitContract.COLUMN_HOLY_PLACE_NAME} = :holyPlaceName AND " +
            "${VisitContract.COLUMN_DATE_VISITED} >= :startOfDayMillis AND " +
            "${VisitContract.COLUMN_DATE_VISITED} < :endOfDayMillis LIMIT 1")
    suspend fun getVisitByNameAndDayRange(holyPlaceName: String, startOfDayMillis: Long, endOfDayMillis: Long): Visit?

    // --- Goal Progress Queries ---

    /**
     * Gets the total number of temple visits for a specific year.
     * If excludeNoOrdinances is true, only counts visits where at least one ordinance was performed.
     * Assumes 'T' in the 'visit_type' (Visit.type) column indicates a Temple visit.
     */
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("""
    SELECT COUNT(visit_id) FROM visits
    WHERE year = :year AND ${VisitContract.COLUMN_VISIT_TYPE} = 'T' 
    AND (:excludeNoOrdinances = 0 OR 
         (${VisitContract.COLUMN_BAPTISMS} > 0 OR 
          ${VisitContract.COLUMN_CONFIRMATIONS} > 0 OR 
          ${VisitContract.COLUMN_INITIATORIES} > 0 OR 
          ${VisitContract.COLUMN_ENDOWMENTS} > 0 OR 
          ${VisitContract.COLUMN_SEALINGS} > 0))
""")
    fun getTempleVisitsCountForYear(year: String, excludeNoOrdinances: Boolean): Flow<Int>

    /**
     * Gets the total sum of baptisms for a specific year.
     * Baptisms are stored as Short?, SUM will handle nulls as 0 in aggregation if other values exist.
     */
    @Query("SELECT SUM(${VisitContract.COLUMN_BAPTISMS}) FROM visits WHERE year = :year")
    fun getTotalBaptismsForYear(year: String): Flow<Int?> // SUM can return null if no rows match

    /**
     * Gets the total sum of confirmations for a specific year.
     */
    @Query("SELECT SUM(${VisitContract.COLUMN_CONFIRMATIONS}) FROM visits WHERE year = :year")
    fun getTotalConfirmationsForYear(year: String): Flow<Int?>

    /**
     * Gets the total sum of initiatories for a specific year.
     */
    @Query("SELECT SUM(${VisitContract.COLUMN_INITIATORIES}) FROM visits WHERE year = :year")
    fun getTotalInitiatoriesForYear(year: String): Flow<Int?>

    /**
     * Gets the total sum of endowments for a specific year.
     */
    @Query("SELECT SUM(${VisitContract.COLUMN_ENDOWMENTS}) FROM visits WHERE year = :year")
    fun getTotalEndowmentsForYear(year: String): Flow<Int?>

    /**
     * Gets the total sum of sealings for a specific year.
     */
    @Query("SELECT SUM(${VisitContract.COLUMN_SEALINGS}) FROM visits WHERE year = :year")
    fun getTotalSealingsForYear(year: String): Flow<Int?>
}
