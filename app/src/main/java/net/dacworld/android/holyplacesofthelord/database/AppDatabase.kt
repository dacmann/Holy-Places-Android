package net.dacworld.android.holyplacesofthelord.database // Or your chosen package for database files

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.dacworld.android.holyplacesofthelord.model.Temple
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.model.Converters
// Import your DAO interfaces
import net.dacworld.android.holyplacesofthelord.dao.TempleDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao

@Database(
    entities = [Temple::class, Visit::class], // List all your entities here
    version = 1,                              // Start with version 1. Increment for schema changes.
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
                    // .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Add migrations if you have them
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