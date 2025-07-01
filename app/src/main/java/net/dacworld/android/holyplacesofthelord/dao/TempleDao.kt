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

    // --- Insert Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemple(temple: Temple) // Use suspend for coroutines

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTemples(temples: List<Temple>)

    // --- Query Operations ---

    @Query("SELECT * FROM ${TempleContract.TABLE_NAME} ORDER BY name ASC")
    fun getAllTemples(): Flow<List<Temple>> // Flow for reactive updates

    @Query("SELECT * FROM ${TempleContract.TABLE_NAME} WHERE ${TempleContract.COLUMN_ID} = :templeId")
    fun getTempleById(templeId: String): Flow<Temple?> // Flow for single item, nullable

    @Query("SELECT * FROM ${TempleContract.TABLE_NAME} WHERE name LIKE :searchQuery || '%' ORDER BY name ASC")
    fun searchTemplesByName(searchQuery: String): Flow<List<Temple>>

    // Example: Query to get temples by country
    @Query("SELECT * FROM ${TempleContract.TABLE_NAME} WHERE country = :countryName ORDER BY name ASC")
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

// Assuming you have TempleContract.kt like this in your model package:
// package net.dacworld.android.holyplacesofthelord.model
//
// object TempleContract {
//     const val TABLE_NAME = "temples"
//     const val COLUMN_ID = "temple_id" // Primary Key
//     // ... other column name constants
// }