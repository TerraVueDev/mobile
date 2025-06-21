package com.example.terravue.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.terravue.data.local.dao.ServiceDao
import com.example.terravue.data.local.entities.ServiceEntity
import java.util.Date

/**
 * Main Room database for TerraVue environmental tracking app
 *
 * Handles local storage of:
 * - Installed app services and their environmental impact
 * - Usage statistics and user preferences
 * - Cached environmental data from GitHub
 */
@Database(
    entities = [
        ServiceEntity::class
        // Add more entities here as app grows:
        // UsageStatsEntity::class,
        // EcoGoalEntity::class,
        // UserPreferencesEntity::class
    ],
    version = 1,
    exportSchema = false // Set to true in production for proper migrations
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Service data access object
     */
    abstract fun serviceDao(): ServiceDao

    // Future DAOs:
    // abstract fun usageStatsDao(): UsageStatsDao
    // abstract fun ecoGoalDao(): EcoGoalDao

    companion object {
        // Singleton prevents multiple instances of database opening at the same time
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get database instance with proper singleton pattern
         */
        fun getDatabase(context: Context): AppDatabase {
            // If the INSTANCE is not null, return it,
            // otherwise create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "terravue_database"
                )
                    .addCallback(DatabaseCallback()) // Add initial data
                    .fallbackToDestructiveMigration() // Only for development!
                    // .addMigrations(MIGRATION_1_2) // Add proper migrations for production
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * Database callback for initialization
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                // Populate database with initial data if needed
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.serviceDao())
                    }
                }
            }
        }

        /**
         * Populate database with initial environmental data
         */
        private suspend fun populateDatabase(serviceDao: ServiceDao) {
            // Add some sample environmental impact data
            // This can be useful for testing or demo purposes

            // Note: In production, this data comes from ServiceDiscoveryManager
            // and GitHub data source, so this is just for initial setup

            val sampleServices = listOf(
                ServiceEntity(
                    id = "sample_social_media",
                    name = "Sample Social Media",
                    packageName = "com.example.social",
                    impactLevel = "HIGH",
                    description = "Social media apps typically have high environmental impact due to constant data streaming and server communication.",
                    dailyCO2Grams = 2.5,
                    dailyEnergyWh = 8.0,
                    cachedAt = Date(),
                    isSystemApp = false
                ),
                ServiceEntity(
                    id = "sample_productivity",
                    name = "Sample Productivity App",
                    packageName = "com.example.productivity",
                    impactLevel = "LOW",
                    description = "Productivity apps generally have low environmental impact with minimal background activity.",
                    dailyCO2Grams = 0.3,
                    dailyEnergyWh = 1.2,
                    cachedAt = Date(),
                    isSystemApp = false
                )
            )

            // Only insert sample data if database is empty
            val existingCount = serviceDao.getTotalServicesCount()
            if (existingCount == 0) {
                serviceDao.insertServices(sampleServices)
            }
        }

        /**
         * Close database instance (for testing or cleanup)
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

/**
 * Type converters for Room database
 * Handles conversion between complex types and primitive types that Room can store
 */
class Converters {

    /**
     * Convert timestamp to Date
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * Convert Date to timestamp
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    /**
     * Convert List of Strings to single String (comma-separated)
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString(",")
    }

    /**
     * Convert String to List of Strings
     */
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split(",")?.map { it.trim() }
    }
}

// Future migration examples for when you need to update the database schema:

/*
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Example: Add new column to services table
        database.execSQL("ALTER TABLE services ADD COLUMN userNotes TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Example: Create new table
        database.execSQL("""
            CREATE TABLE usage_stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                serviceId TEXT NOT NULL,
                date INTEGER NOT NULL,
                usageMinutes INTEGER NOT NULL,
                FOREIGN KEY(serviceId) REFERENCES services(id) ON DELETE CASCADE
            )
        """)
    }
}
*/