package net.dacworld.android.holyplacesofthelord.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.dacworld.android.holyplacesofthelord.model.Converters
import net.dacworld.android.holyplacesofthelord.model.Profile
import net.dacworld.android.holyplacesofthelord.model.ProfileContract
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.model.VisitContract
import net.dacworld.android.holyplacesofthelord.dao.ProfileDao
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ${VisitContract.TABLE_NAME} ADD COLUMN ${VisitContract.COLUMN_HAS_PICTURE} INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE ${VisitContract.TABLE_NAME} SET ${VisitContract.COLUMN_HAS_PICTURE} = 1 " +
                "WHERE ${VisitContract.COLUMN_PICTURE_DATA} IS NOT NULL AND LENGTH(${VisitContract.COLUMN_PICTURE_DATA}) > 0"
        )
    }
}

/**
 * v2 → v3: Adds the local multi-profile feature.
 *   - New table: profiles
 *   - New nullable column on visits: profile_id (TEXT, default NULL)
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
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
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_profiles_profile_id " +
                "ON ${ProfileContract.TABLE_NAME} (${ProfileContract.COLUMN_ID})"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_profiles_created_date " +
                "ON ${ProfileContract.TABLE_NAME} (${ProfileContract.COLUMN_CREATED_DATE})"
        )
        db.execSQL(
            "ALTER TABLE ${VisitContract.TABLE_NAME} " +
                "ADD COLUMN ${VisitContract.COLUMN_PROFILE_ID} TEXT"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_visits_profile_id " +
                "ON ${VisitContract.TABLE_NAME} (${VisitContract.COLUMN_PROFILE_ID})"
        )
    }
}

@Database(
    entities = [Temple::class, Visit::class, Profile::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun templeDao(): TempleDao
    abstract fun visitDao(): VisitDao
    abstract fun profileDao(): ProfileDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA cursor_window_size = 20000000")
                            db.execSQL("PRAGMA page_size = 65536")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}