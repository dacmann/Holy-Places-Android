package net.dacworld.android.holyplacesofthelord.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    fun getAllVisits(): Flow<List<Visit>>

    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_ID} = :visitId")
    fun getVisitById(visitId: Long): Flow<Visit?>

    // Get all visits for a specific Temple
    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_PLACE_ID} = :templeId ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    fun getVisitsForTemple(templeId: String): Flow<List<Visit>>

    // Get favorite visits
    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_IS_FAVORITE} = 1 ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    fun getFavoriteVisits(): Flow<List<Visit>>

    // Example: Get visits within a specific year
    @Query("SELECT * FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_YEAR} = :year ORDER BY ${VisitContract.COLUMN_DATE_VISITED} DESC")
    fun getVisitsByYear(year: String): Flow<List<Visit>>

    @Query("SELECT COUNT(*) FROM ${VisitContract.TABLE_NAME}")
    suspend fun getVisitCount(): Int

    // --- Update Operations ---

    @Update
    suspend fun updateVisit(visit: Visit)

    // --- Delete Operations ---

    @Delete
    suspend fun deleteVisit(visit: Visit)

    @Query("DELETE FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_ID} = :visitId")
    suspend fun deleteVisitById(visitId: Long): Int // Returns number of rows deleted

    @Query("DELETE FROM ${VisitContract.TABLE_NAME}")
    suspend fun deleteAllVisits()

    // Example: Delete all visits for a specific temple (useful if not using CASCADE on foreign key)
    // @Query("DELETE FROM ${VisitContract.TABLE_NAME} WHERE ${VisitContract.COLUMN_PLACE_ID} = :templeId")
    // suspend fun deleteVisitsForTemple(templeId: String)
}


// Assuming you have VisitContract.kt like this in your model package:
// package net.dacworld.android.holyplacesofthelord.model
//
// object VisitContract {
//     const val TABLE_NAME = "visits"
//     const val COLUMN_ID = "visit_id"
//     const val COLUMN_PLACE_ID = "place_id"
//     const val COLUMN_DATE_VISITED = "date_visited"
//     const val COLUMN_IS_FAVORITE = "is_favorite"
//     const val COLUMN_YEAR = "year"
//     // ... other column name constants
// }