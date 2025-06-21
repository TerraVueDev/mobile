package com.example.terravue.data.repositories

import android.content.Context
import com.example.terravue.domain.models.Service
import com.example.terravue.domain.models.ImpactLevel
import com.example.terravue.data.local.dao.ServiceDao
import com.example.terravue.data.remote.GitHubDataSource
import com.example.terravue.utils.ImpactLevelClassifier
import com.example.terravue.services.ServiceDiscoveryManager
import com.example.terravue.data.mappers.ServiceMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date

/**
 * Repository pattern implementation for Service data management
 *
 * Coordinates between:
 * - Local database (Room) for caching
 * - Remote GitHub data source for latest impact data
 * - System service discovery for installed apps
 * - Impact classification logic
 *
 * Provides offline-first architecture with network fallback
 */
class ServiceRepository(
    private val context: Context,
    private val serviceDao: ServiceDao,
    private val gitHubDataSource: GitHubDataSource,
    private val impactClassifier: ImpactLevelClassifier
) {

    /**
     * Load services with latest GitHub data, cache locally
     * Returns Result to handle success/failure states
     */
    suspend fun loadServicesFromGitHub(): Result<List<Service>> = withContext(Dispatchers.IO) {
        try {
            // First, try to load latest impact data from GitHub
            val githubDataLoaded = gitHubDataSource.loadLatestData()

            if (githubDataLoaded) {
                // Update classifier with new data
                impactClassifier.updateFromGitHubData(
                    categories = gitHubDataSource.getCategoriesData(),
                    links = gitHubDataSource.getLinksData()
                )
            }

            // Discover installed apps using updated classifier
            val discoveredServices = discoverInstalledApps()

            // Cache the results locally
            cacheServices(discoveredServices)

            Result.success(discoveredServices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load services using offline/cached data only
     */
    suspend fun loadServicesOffline(): List<Service> = withContext(Dispatchers.IO) {
        try {
            // Try cached data first
            val cachedServices = getCachedServices()

            if (cachedServices.isNotEmpty()) {
                return@withContext cachedServices
            }

            // If no cache, discover with offline classifier
            discoverInstalledApps()
        } catch (e: Exception) {
            // Return empty list on any error
            emptyList()
        }
    }

    /**
     * Get cached services from local database
     */
    suspend fun getCachedServices(): List<Service> = withContext(Dispatchers.IO) {
        try {
            val entities = serviceDao.getAllServices()
            ServiceMapper.entitiesToDomain(entities) { packageName ->
                // Icon provider function - you can enhance this to load actual icons
                null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get services as Flow for reactive UI updates
     */
    fun getServicesFlow(): Flow<List<Service>> {
        return serviceDao.getAllServicesFlow().map { entities ->
            ServiceMapper.entitiesToDomain(entities) { packageName ->
                // Icon provider function
                null
            }
        }
    }

    /**
     * Search services by query
     */
    suspend fun searchServices(query: String): List<Service> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) {
                getCachedServices()
            } else {
                val entities = serviceDao.searchServices("%$query%")
                ServiceMapper.entitiesToDomain(entities)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get services by impact level
     */
    suspend fun getServicesByImpactLevel(impactLevel: ImpactLevel): List<Service> = withContext(Dispatchers.IO) {
        try {
            val entities = serviceDao.getServicesByImpactLevel(impactLevel.name)
            ServiceMapper.entitiesToDomain(entities)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Update service usage statistics
     */
    suspend fun updateServiceUsage(packageName: String, usageMinutes: Int) = withContext(Dispatchers.IO) {
        try {
            // Calculate new CO2 and energy values based on usage
            val dailyCO2 = when {
                usageMinutes > 120 -> 2.5 * (usageMinutes / 60.0)
                usageMinutes > 60 -> 1.0 * (usageMinutes / 60.0)
                else -> 0.2 * (usageMinutes / 60.0)
            }

            val dailyEnergy = when {
                usageMinutes > 120 -> 8.0 * (usageMinutes / 60.0)
                usageMinutes > 60 -> 3.2 * (usageMinutes / 60.0)
                else -> 0.8 * (usageMinutes / 60.0)
            }

            serviceDao.updateUsageStats(packageName, usageMinutes, Date(), dailyCO2, dailyEnergy)
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }

    /**
     * Delete old cached data (cleanup)
     */
    suspend fun cleanupOldData(olderThanDays: Int = 7) = withContext(Dispatchers.IO) {
        try {
            val cutoffDate = Date(System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L))
            serviceDao.deleteOldServices(cutoffDate)
        } catch (e: Exception) {
            // Silent cleanup failure
        }
    }

    /**
     * Get environmental impact summary statistics
     */
    suspend fun getImpactSummary(): ImpactSummary = withContext(Dispatchers.IO) {
        try {
            val allServices = getCachedServices()

            ImpactSummary(
                totalApps = allServices.size,
                highImpactCount = allServices.count { it.impactLevel == ImpactLevel.HIGH },
                mediumImpactCount = allServices.count { it.impactLevel == ImpactLevel.MEDIUM },
                lowImpactCount = allServices.count { it.impactLevel == ImpactLevel.LOW },
                estimatedDailyCO2 = calculateTotalDailyCO2(allServices),
                estimatedDailyEnergy = calculateTotalDailyEnergy(allServices),
                lastUpdated = Date()
            )
        } catch (e: Exception) {
            ImpactSummary.empty()
        }
    }

    // Private helper methods

    /**
     * Discover installed apps using ServiceDiscoveryManager
     * ServiceDiscoveryManager now returns domain Service objects directly
     */
    private suspend fun discoverInstalledApps(): List<Service> {
        val discoveryManager = ServiceDiscoveryManager(
            context = context,
            impactClassifier = impactClassifier
        )

        // ServiceDiscoveryManager.getInstalledServices() now returns List<Service> (domain models)
        // No conversion needed since we updated ServiceDiscoveryManager to return domain models
        return discoveryManager.getInstalledServices()
    }

    /**
     * Cache services to local database
     */
    private suspend fun cacheServices(services: List<Service>) {
        try {
            // Clear old cache
            serviceDao.clearAllServices()

            // Convert domain models to entities and insert
            val entities = ServiceMapper.domainToEntities(services)
            serviceDao.insertServices(entities)
        } catch (e: Exception) {
            // Log error but don't fail the whole operation
            println("Failed to cache services: ${e.message}")
        }
    }

    /**
     * Calculate total daily CO2 emissions
     */
    private fun calculateTotalDailyCO2(services: List<Service>): Double {
        return services.sumOf { service ->
            service.getDailyImpactEstimate().co2Grams
        }
    }

    /**
     * Calculate total daily energy consumption
     */
    private fun calculateTotalDailyEnergy(services: List<Service>): Double {
        return services.sumOf { service ->
            service.getDailyImpactEstimate().energyWh
        }
    }
}

/**
 * Data class for impact summary statistics
 */
data class ImpactSummary(
    val totalApps: Int,
    val highImpactCount: Int,
    val mediumImpactCount: Int,
    val lowImpactCount: Int,
    val estimatedDailyCO2: Double, // grams
    val estimatedDailyEnergy: Double, // watt-hours
    val lastUpdated: Date
) {
    companion object {
        fun empty() = ImpactSummary(
            totalApps = 0,
            highImpactCount = 0,
            mediumImpactCount = 0,
            lowImpactCount = 0,
            estimatedDailyCO2 = 0.0,
            estimatedDailyEnergy = 0.0,
            lastUpdated = Date()
        )
    }

    /**
     * Get annual projections
     */
    fun getAnnualProjections(): AnnualProjections {
        return AnnualProjections(
            co2Kg = (estimatedDailyCO2 * 365) / 1000,
            energyKwh = (estimatedDailyEnergy * 365) / 1000,
            equivalentTreesNeeded = ((estimatedDailyCO2 * 365) / 22000).toInt()
        )
    }
}

/**
 * Annual environmental impact projections
 */
data class AnnualProjections(
    val co2Kg: Double,
    val energyKwh: Double,
    val equivalentTreesNeeded: Int
) {
    fun getHumanReadableComparisons(): List<String> {
        val comparisons = mutableListOf<String>()

        when {
            co2Kg > 1000 -> comparisons.add("Like driving ${String.format("%.0f", co2Kg * 4.6)} km per year")
            co2Kg > 100 -> comparisons.add("Like ${String.format("%.0f", co2Kg / 2.3)} days of breathing")
            else -> comparisons.add("Less than a short car trip")
        }

        when {
            energyKwh > 100 -> comparisons.add("Like powering a home for ${String.format("%.1f", energyKwh / 30)} days")
            energyKwh > 10 -> comparisons.add("Like charging a laptop ${String.format("%.0f", energyKwh / 0.05)} times")
            else -> comparisons.add("Minimal energy impact")
        }

        if (equivalentTreesNeeded > 0) {
            comparisons.add("Plant $equivalentTreesNeeded trees to offset this impact")
        }

        return comparisons
    }
}