package net.dacworld.android.holyplacesofthelord.database // Or your chosen package for database files

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.model.VisitContract
import net.dacworld.android.holyplacesofthelord.model.Converters
// Import your DAO interfaces
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Add the new column to the visits table
        db.execSQL("ALTER TABLE ${VisitContract.TABLE_NAME} ADD COLUMN ${VisitContract.COLUMN_HAS_PICTURE} INTEGER NOT NULL DEFAULT 0")

        // Step 2: Update the new has_picture column based on existing picture_data
        // This ensures existing visits correctly reflect if they have a picture
        db.execSQL("UPDATE ${VisitContract.TABLE_NAME} SET ${VisitContract.COLUMN_HAS_PICTURE} = 1 " +
                "WHERE ${VisitContract.COLUMN_PICTURE_DATA} IS NOT NULL AND LENGTH(${VisitContract.COLUMN_PICTURE_DATA}) > 0")
    }
}

@Database(
    entities = [Temple::class, Visit::class], // List all your entities here
    version = 2,                              // Start with version 1. Increment for schema changes.
    exportSchema = true                       // Recommended: Exports schema to a JSON file (good for version control and complex migrations)
    // Set to false if you don't want this (e.g., for simple apps or tests)
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Abstract methods to get your DAOs. Room will generate the implementations.
    abstract fun templeDao(): TempleDao
    abstract fun visitDao(): VisitDao

    companion object {
        // Singleton prevents multiple instances of database opening at the same time.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "holy_places_database" // Name of your database file
                )
                    .addMigrations(MIGRATION_1_2)
                    // .fallbackToDestructiveMigration() // Use this ONLY during development if you don't want to write migrations yet
                    // This will clear all data on schema change. NOT for production.
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}