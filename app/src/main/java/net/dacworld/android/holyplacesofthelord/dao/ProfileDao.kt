package net.dacworld.android.holyplacesofthelord.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.dacworld.android.holyplacesofthelord.model.Profile
import net.dacworld.android.holyplacesofthelord.model.ProfileContract
import net.dacworld.android.holyplacesofthelord.model.VisitContract

@Dao
interface ProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile)

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query(
        "SELECT * FROM ${ProfileContract.TABLE_NAME} " +
            "ORDER BY ${ProfileContract.COLUMN_IS_DEFAULT} DESC, " +
            "${ProfileContract.COLUMN_CREATED_DATE} ASC"
    )
    fun getAll(): Flow<List<Profile>>

    @Query(
        "SELECT * FROM ${ProfileContract.TABLE_NAME} " +
            "ORDER BY ${ProfileContract.COLUMN_IS_DEFAULT} DESC, " +
            "${ProfileContract.COLUMN_CREATED_DATE} ASC"
    )
    suspend fun getAllSnapshot(): List<Profile>

    @Query("SELECT * FROM ${ProfileContract.TABLE_NAME} WHERE ${ProfileContract.COLUMN_ID} = :profileId LIMIT 1")
    suspend fun getById(profileId: String): Profile?

    @Query("SELECT * FROM ${ProfileContract.TABLE_NAME} WHERE ${ProfileContract.COLUMN_ID} = :profileId LIMIT 1")
    fun observeById(profileId: String): Flow<Profile?>

    @Query("SELECT * FROM ${ProfileContract.TABLE_NAME} WHERE ${ProfileContract.COLUMN_IS_DEFAULT} = 1 LIMIT 1")
    suspend fun getDefault(): Profile?

    @Query("SELECT COUNT(*) FROM ${ProfileContract.TABLE_NAME}")
    suspend fun count(): Int

    @Query(
        "SELECT COUNT(*) FROM ${VisitContract.TABLE_NAME} " +
            "WHERE ${VisitContract.COLUMN_PROFILE_ID} = :profileId"
    )
    suspend fun visitCountForProfile(profileId: String): Int

    @Query(
        "SELECT COUNT(*) FROM ${VisitContract.TABLE_NAME} " +
            "WHERE ${VisitContract.COLUMN_PROFILE_ID} = :profileId"
    )
    fun observeVisitCountForProfile(profileId: String): Flow<Int>

    @Query(
        "UPDATE ${VisitContract.TABLE_NAME} SET ${VisitContract.COLUMN_PROFILE_ID} = :profileId " +
            "WHERE ${VisitContract.COLUMN_PROFILE_ID} IS NULL"
    )
    suspend fun assignUnassignedVisitsToProfile(profileId: String): Int

    @Query(
        "DELETE FROM ${VisitContract.TABLE_NAME} " +
            "WHERE ${VisitContract.COLUMN_PROFILE_ID} = :profileId"
    )
    suspend fun deleteVisitsForProfile(profileId: String): Int
}
