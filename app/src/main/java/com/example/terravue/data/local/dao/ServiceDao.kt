package com.example.terravue.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.terravue.data.local.entities.ServiceEntity
import java.util.Date

/**
 * Data Access Object for Service operations
 *
 * Provides comprehensive database operations for environmental
 * impact service data including CRUD operations, search,
 * filtering, and analytics queries.
 */
@Dao
interface ServiceDao {

    // =====================================================================
    // BASIC CRUD OPERATIONS
    // =====================================================================

    /**
     * Get all services ordered by name
     */
    @Query("SELECT * FROM services ORDER BY name ASC")
    suspend fun getAllServices(): List<ServiceEntity>

    /**
     * Get all services as Flow for reactive UI updates
     */
    @Query("SELECT * FROM services ORDER BY name ASC")
    fun getAllServicesFlow(): Flow<List<ServiceEntity>>

    /**
     * Get service by package name
     */
    @Query("SELECT * FROM services WHERE packageName = :packageName LIMIT 1")
    suspend fun getServiceByPackageName(packageName: String): ServiceEntity?

    /**
     * Get service by ID
     */
    @Query("SELECT * FROM services WHERE id = :id LIMIT 1")
    suspend fun getServiceById(id: String): ServiceEntity?

    /**
     * Insert single service
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: ServiceEntity)

    /**
     * Insert multiple services
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServices(services: List<ServiceEntity>)

    /**
     * Update service
     */
    @Update
    suspend fun updateService(service: ServiceEntity)

    /**
     * Delete service
     */
    @Delete
    suspend fun deleteService(service: ServiceEntity)

    /**
     * Delete all services (for cache refresh)
     */
    @Query("DELETE FROM services")
    suspend fun clearAllServices()

    // =====================================================================
    // SEARCH AND FILTERING
    // =====================================================================

    /**
     * Search services by name or package name
     */
    @Query("""
        SELECT * FROM services 
        WHERE name LIKE :query OR packageName LIKE :query 
        ORDER BY name ASC
    """)
    suspend fun searchServices(query: String): List<ServiceEntity>

    /**
     * Get services by impact level
     */
    @Query("SELECT * FROM services WHERE impactLevel = :impactLevel ORDER BY name ASC")
    suspend fun getServicesByImpactLevel(impactLevel: String): List<ServiceEntity>

    /**
     * Get services by impact level as Flow
     */
    @Query("SELECT * FROM services WHERE impactLevel = :impactLevel ORDER BY name ASC")
    fun getServicesByImpactLevelFlow(impactLevel: String): Flow<List<ServiceEntity>>

    /**
     * Get favorite services
     */
    @Query("SELECT * FROM services WHERE isFavorite = 1 ORDER BY name ASC")
    suspend fun getFavoriteServices(): List<ServiceEntity>

    /**
     * Get recently used services
     */
    @Query("""
        SELECT * FROM services 
        WHERE lastUsed IS NOT NULL 
        ORDER BY lastUsed DESC 
        LIMIT :limit
    """)
    suspend fun getRecentlyUsedServices(limit: Int = 10): List<ServiceEntity>

    /**
     * Get high usage services
     */
    @Query("""
        SELECT * FROM services 
        WHERE usageMinutes > :minUsage 
        ORDER BY usageMinutes DESC
    """)
    suspend fun getHighUsageServices(minUsage: Int = 60): List<ServiceEntity>

    // =====================================================================
    // UPDATE OPERATIONS
    // =====================================================================

    /**
     * Update usage statistics
     */
    @Query("""
        UPDATE services 
        SET usageMinutes = :usageMinutes, 
            lastUsed = :lastUsed,
            dailyCO2Grams = :dailyCO2Grams,
            dailyEnergyWh = :dailyEnergyWh
        WHERE packageName = :packageName
    """)
    suspend fun updateUsageStats(
        packageName: String,
        usageMinutes: Int,
        lastUsed: Date,
        dailyCO2Grams: Double,
        dailyEnergyWh: Double
    )

    /**
     * Update user classification (when user manually changes impact level)
     */
    @Query("""
        UPDATE services 
        SET userClassification = :classification 
        WHERE packageName = :packageName
    """)
    suspend fun updateUserClassification(packageName: String, classification: String)

    /**
     * Toggle favorite status
     */
    @Query("""
        UPDATE services 
        SET isFavorite = :isFavorite 
        WHERE packageName = :packageName
    """)
    suspend fun updateFavoriteStatus(packageName: String, isFavorite: Boolean)

    // =====================================================================
    // CLEANUP OPERATIONS
    // =====================================================================

    /**
     * Delete old cached services
     */
    @Query("DELETE FROM services WHERE cachedAt < :cutoffDate")
    suspend fun deleteOldServices(cutoffDate: Date)

    /**
     * Delete system apps (cleanup operation)
     */
    @Query("DELETE FROM services WHERE isSystemApp = 1")
    suspend fun deleteSystemApps()

    /**
     * Delete services not used in X days
     */
    @Query("""
        DELETE FROM services 
        WHERE lastUsed < :cutoffDate OR lastUsed IS NULL
    """)
    suspend fun deleteUnusedServices(cutoffDate: Date)

    // =====================================================================
    // ANALYTICS AND STATISTICS
    // =====================================================================

    /**
     * Get count of services by impact level
     */
    @Query("SELECT COUNT(*) FROM services WHERE impactLevel = :impactLevel")
    suspend fun getCountByImpactLevel(impactLevel: String): Int

    /**
     * Get total services count
     */
    @Query("SELECT COUNT(*) FROM services")
    suspend fun getTotalServicesCount(): Int

    /**
     * Get total usage minutes across all apps
     */
    @Query("SELECT SUM(usageMinutes) FROM services")
    suspend fun getTotalUsageMinutes(): Int?

    /**
     * Get total daily CO2 emissions
     */
    @Query("SELECT SUM(dailyCO2Grams) FROM services")
    suspend fun getTotalDailyCO2(): Double?

    /**
     * Get total daily energy consumption
     */
    @Query("SELECT SUM(dailyEnergyWh) FROM services")
    suspend fun getTotalDailyEnergy(): Double?

    /**
     * Get impact level distribution
     */
    @Query("""
        SELECT impactLevel, COUNT(*) as count 
        FROM services 
        GROUP BY impactLevel
    """)
    suspend fun getImpactLevelDistribution(): List<ImpactLevelCount>

    /**
     * Get average usage minutes
     */
    @Query("""
        SELECT AVG(usageMinutes) as avgUsage
        FROM services 
        WHERE usageMinutes > 0
    """)
    suspend fun getAverageUsageMinutes(): Double?

    /**
     * Get top apps by CO2 emissions
     */
    @Query("""
        SELECT * FROM services 
        WHERE dailyCO2Grams > 0 
        ORDER BY dailyCO2Grams DESC 
        LIMIT :limit
    """)
    suspend fun getTopCO2Emitters(limit: Int = 10): List<ServiceEntity>

    /**
     * Get apps with detailed environmental data
     */
    @Query("""
        SELECT * FROM services 
        WHERE description IS NOT NULL 
        AND energyConsumption IS NOT NULL
        ORDER BY name ASC
    """)
    suspend fun getServicesWithDetailedData(): List<ServiceEntity>

    /**
     * Search with filters
     */
    @Query("""
        SELECT * FROM services 
        WHERE (name LIKE :query OR packageName LIKE :query)
        AND (:impactLevel IS NULL OR impactLevel = :impactLevel)
        AND (:minUsage IS NULL OR usageMinutes >= :minUsage)
        ORDER BY 
            CASE WHEN :orderBy = 'name' THEN name END ASC,
            CASE WHEN :orderBy = 'usage' THEN usageMinutes END DESC,
            CASE WHEN :orderBy = 'co2' THEN dailyCO2Grams END DESC
    """)
    suspend fun searchWithFilters(
        query: String,
        impactLevel: String?,
        minUsage: Int?,
        orderBy: String = "name"
    ): List<ServiceEntity>
}

/**
 * Data class for impact level statistics
 */
data class ImpactLevelCount(
    val impactLevel: String,
    val count: Int
)