package net.dacworld.android.holyplacesofthelord.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.dacworld.android.holyplacesofthelord.model.Converters
import net.dacworld.android.holyplacesofthelord.model.NameChangeContract
import net.dacworld.android.holyplacesofthelord.model.Profile
import net.dacworld.android.holyplacesofthelord.model.ProfileContract
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.model.TempleContract
import net.dacworld.android.holyplacesofthelord.model.TempleNameChange
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.model.VisitContract
import net.dacworld.android.holyplacesofthelord.dao.NameChangeDao
import net.dacworld.android.holyplacesofthelord.dao.ProfileDao
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao

private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
    db.query(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
        arrayOf(tableName)
    ).use { cursor ->
        return cursor.moveToFirst()
    }
}

private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
    db.query("PRAGMA table_info($tableName)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex < 0) return false
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == columnName) return true
        }
    }
    return false
}

private fun indexExists(db: SupportSQLiteDatabase, indexName: String): Boolean {
    db.query(
        "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
        arrayOf(indexName)
    ).use { cursor ->
        return cursor.moveToFirst()
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, VisitContract.TABLE_NAME, VisitContract.COLUMN_HAS_PICTURE)) {
            db.execSQL(
                "ALTER TABLE ${VisitContract.TABLE_NAME} ADD COLUMN " +
                    "${VisitContract.COLUMN_HAS_PICTURE} INTEGER NOT NULL DEFAULT 0"
            )
        }
        db.execSQL(
            "UPDATE ${VisitContract.TABLE_NAME} SET ${VisitContract.COLUMN_HAS_PICTURE} = 1 " +
                "WHERE ${VisitContract.COLUMN_PICTURE_DATA} IS NOT NULL " +
                "AND LENGTH(${VisitContract.COLUMN_PICTURE_DATA}) > 0"
        )
    }
}

/**
 * v2 → v3: Adds the local multi-profile feature.
 *   - New table: profiles
 *   - New nullable column on visits: profile_id (TEXT, default NULL)
 *
 * Idempotent so a partially-applied migration (e.g. interrupted launch) can safely retry.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!tableExists(db, ProfileContract.TABLE_NAME)) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${ProfileContract.TABLE_NAME} (
                    ${ProfileContract.COLUMN_ID}                         TEXT    NOT NULL PRIMARY KEY,
                    ${ProfileContract.COLUMN_NAME}                       TEXT    NOT NULL,
                    ${ProfileContract.COLUMN_IS_DEFAULT}                 INTEGER NOT NULL DEFAULT 0,
                    ${ProfileContract.COLUMN_ICON_NAME}                  TEXT    NOT NULL DEFAULT 'person.fill',
                    ${ProfileContract.COLUMN_CREATED_DATE}               INTEGER NOT NULL,
                    ${ProfileContract.COLUMN_ANNUAL_VISIT_GOAL}          INTEGER NOT NULL DEFAULT 0,
                    ${ProfileContract.COLUMN_ANNUAL_BAPTISM_GOAL}        INTEGER NOT NULL DEFAULT 0,
                    ${ProfileContract.COLUMN_ANNUAL_INITIATORY_GOAL}     INTEGER NOT NULL DEFAULT 0,
                    ${ProfileContract.COLUMN_ANNUAL_ENDOWMENT_GOAL}      INTEGER NOT NULL DEFAULT 0,
                    ${ProfileContract.COLUMN_ANNUAL_SEALING_GOAL}        INTEGER NOT NULL DEFAULT 0,
                    ${ProfileContract.COLUMN_EXCLUDE_NON_ORDINANCE_VISITS} INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
        if (!indexExists(db, "index_profiles_profile_id")) {
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_profiles_profile_id " +
                    "ON ${ProfileContract.TABLE_NAME} (${ProfileContract.COLUMN_ID})"
            )
        }
        if (!columnExists(db, VisitContract.TABLE_NAME, VisitContract.COLUMN_PROFILE_ID)) {
            db.execSQL(
                "ALTER TABLE ${VisitContract.TABLE_NAME} " +
                    "ADD COLUMN ${VisitContract.COLUMN_PROFILE_ID} TEXT"
            )
        }
        if (!indexExists(db, "index_visits_profile_id")) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_visits_profile_id " +
                    "ON ${VisitContract.TABLE_NAME} (${VisitContract.COLUMN_PROFILE_ID})"
            )
        }
        // Not declared on @Entity — 1.8/1.8.1 created this and Room validation rejected the migration.
        db.execSQL("DROP INDEX IF EXISTS index_profiles_created_date")
    }
}

/**
 * v3 → v4: Historical Names + Map Timeline support.
 *   - New table: temple_name_changes (historical names with change dates and old images)
 *   - New nullable column on temples: dedicated_date (TEXT, ISO local date)
 *
 * Idempotent so a partially-applied migration (e.g. interrupted launch) can safely retry.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!tableExists(db, NameChangeContract.TABLE_NAME)) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${NameChangeContract.TABLE_NAME} (
                    ${NameChangeContract.COLUMN_ID}             INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    ${NameChangeContract.COLUMN_TEMPLE_ID}      TEXT    NOT NULL,
                    ${NameChangeContract.COLUMN_OLD_NAME}       TEXT    NOT NULL,
                    ${NameChangeContract.COLUMN_CHANGE_DATE}    TEXT,
                    ${NameChangeContract.COLUMN_OLD_IMAGE_URL}  TEXT,
                    ${NameChangeContract.COLUMN_OLD_IMAGE_DATA} BLOB,
                    FOREIGN KEY(${NameChangeContract.COLUMN_TEMPLE_ID})
                        REFERENCES ${TempleContract.TABLE_NAME}(${TempleContract.COLUMN_ID})
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }
        if (!indexExists(db, "index_temple_name_changes_temple_id")) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_temple_name_changes_temple_id " +
                    "ON ${NameChangeContract.TABLE_NAME} (${NameChangeContract.COLUMN_TEMPLE_ID})"
            )
        }
        if (!columnExists(db, TempleContract.TABLE_NAME, "dedicated_date")) {
            db.execSQL("ALTER TABLE ${TempleContract.TABLE_NAME} ADD COLUMN dedicated_date TEXT")
        }
    }
}

@Database(
    entities = [Temple::class, Visit::class, Profile::class, TempleNameChange::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun templeDao(): TempleDao
    abstract fun visitDao(): VisitDao
    abstract fun profileDao(): ProfileDao
    abstract fun nameChangeDao(): NameChangeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "holy_places_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA cursor_window_size = 20000000")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
