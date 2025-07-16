package com.example.terravue.data.repositories

import android.content.Context
import com.example.terravue.domain.models.Service
import com.example.terravue.domain.models.ImpactLevel
import com.example.terravue.domain.models.AIGeneratedContent
import com.example.terravue.data.local.dao.ServiceDao
import com.example.terravue.data.remote.GitHubDataSource
import com.example.terravue.utils.ImpactLevelClassifier
import com.example.terravue.services.ServiceDiscoveryManager
import com.example.terravue.services.AIService
import com.example.terravue.data.mappers.ServiceMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date

/**
 * Enhanced Repository with simplified AI integration
 */
class ServiceRepository(
    private val context: Context,
    private val serviceDao: ServiceDao,
    private val gitHubDataSource: GitHubDataSource,
    private val impactClassifier: ImpactLevelClassifier,
    private val aiService: AIService
) {

    /**
     * Load services with AI-enhanced content (simplified approach)
     */
    suspend fun loadServicesWithAI(): Result<List<Service>> = withContext(Dispatchers.IO) {
        try {
            // First, load services normally
            val result = loadServicesFromGitHub()

            if (result.isSuccess) {
                val services = result.getOrNull() ?: emptyList()

                // Check if AI is available
                if (aiService.isAIAvailable()) {
                    // Enhance services with AI content (sequential for simplicity)
                    val enhancedServices = enhanceServicesWithAISequential(services)
                    cacheServices(enhancedServices)
                    Result.success(enhancedServices)
                } else {
                    // AI not available, return regular services
                    cacheServices(services)
                    Result.success(services)
                }
            } else {
                result
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load services with latest GitHub data, cache locally
     */
    suspend fun loadServicesFromGitHub(): Result<List<Service>> = withContext(Dispatchers.IO) {
        try {
            val githubDataLoaded = gitHubDataSource.loadLatestData()

            if (githubDataLoaded) {
                impactClassifier.updateFromGitHubData(
                    categories = gitHubDataSource.getCategoriesData(),
                    links = gitHubDataSource.getLinksData()
                )
            }

            val discoveredServices = discoverInstalledApps()
            cacheServices(discoveredServices)

            Result.success(discoveredServices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Enhance services with AI content sequentially (simpler approach)
     */
    private suspend fun enhanceServicesWithAISequential(services: List<Service>): List<Service> {
        val enhancedServices = mutableListOf<Service>()

        // Process first 3 services to avoid quota limits
        val servicesToProcess = services.take(3)

        servicesToProcess.forEachIndexed { index, service ->
            try {
                println("Enhancing service ${index + 1}/${servicesToProcess.size}: ${service.name}")

                // Skip if service already has recent AI content
                if (service.aiContent != null && !service.needsAIRefresh()) {
                    enhancedServices.add(service)
                    return@forEachIndexed
                }

                val enhancedService = generateAIContentForService(service)
                enhancedServices.add(enhancedService)

            } catch (e: Exception) {
                println("AI generation failed for ${service.name}: ${e.message}")
                enhancedServices.add(service) // Add original service
            }
        }

        // Add remaining services without AI enhancement
        enhancedServices.addAll(services.drop(10))

        return enhancedServices
    }

    /**
     * Generate AI content for a single service (sequential approach)
     */
    private suspend fun generateAIContentForService(service: Service): Service {
        val dailyImpact = service.getDailyImpactEstimate()
        val usageMinutes = service.usageStats?.dailyUsageMinutes

        try {
            // Generate AI content sequentially
            val co2Comparison = aiService.generateCO2Comparison(
                appName = service.name,
                co2Value = "${String.format("%.1f", dailyImpact.co2Grams)}g",
                impactLevel = service.impactLevel.name
            )

            val energyComparison = aiService.generateEnergyComparison(
                appName = service.name,
                energyValue = "${String.format("%.1f", dailyImpact.energyWh)}Wh",
                impactLevel = service.impactLevel.name
            )

            val explanation = aiService.generateImpactExplanation(
                appName = service.name,
                impactLevel = service.impactLevel.displayName,
                categoryInfo = service.categoryInfo?.description
            )

            val suggestions = aiService.generateEcoSuggestions(
                appName = service.name,
                impactLevel = service.impactLevel.name,
                usageMinutes = usageMinutes
            )

            val annualProjection = aiService.generateAnnualProjection(
                appName = service.name,
                dailyCO2 = dailyImpact.co2Grams,
                dailyEnergy = dailyImpact.energyWh
            )

            // Create AI content
            val aiContent = AIGeneratedContent(
                co2Comparison = co2Comparison,
                energyComparison = energyComparison,
                impactExplanation = explanation,
                ecoSuggestions = suggestions,
                annualProjection = annualProjection
            )

            return service.withAIContent(aiContent)

        } catch (e: Exception) {
            println("Failed to generate AI content for ${service.name}: ${e.message}")
            return service
        }
    }

    /**
     * Refresh AI content for a specific service
     */
    suspend fun refreshAIContentForService(service: Service): Service = withContext(Dispatchers.IO) {
        try {
            if (aiService.isAIAvailable()) {
                generateAIContentForService(service)
            } else {
                service
            }
        } catch (e: Exception) {
            service
        }
    }

    /**
     * Refresh AI content for services that need updates
     */
    suspend fun refreshExpiredAIContent(): Int = withContext(Dispatchers.IO) {
        try {
            val services = getCachedServices()
            val expiredServices = services.filter { it.needsAIRefresh() }.take(5) // Limit to 5

            if (expiredServices.isEmpty()) return@withContext 0

            val refreshedServices = enhanceServicesWithAISequential(expiredServices)

            // Update cache with refreshed services
            // Note: You might want to implement selective cache updates

            refreshedServices.size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get AI service statistics
     */
    suspend fun getAIServiceStats(): AIServiceStats = withContext(Dispatchers.IO) {
        try {
            val cacheStats = aiService.getCacheStats()
            val services = getCachedServices()
            val servicesWithAI = services.count { it.hasAIContent }
            val expiredAIContent = services.count { it.needsAIRefresh() }

            AIServiceStats(
                isAIAvailable = aiService.isAIAvailable(),
                totalServices = services.size,
                servicesWithAI = servicesWithAI,
                expiredAIContent = expiredAIContent,
                cacheStats = cacheStats
            )
        } catch (e: Exception) {
            AIServiceStats.default()
        }
    }

    /**
     * Clear AI cache
     */
    suspend fun clearAICache() {
        aiService.clearCache()
    }

    // Original repository methods...
    suspend fun loadServicesOffline(): List<Service> = withContext(Dispatchers.IO) {
        try {
            val cachedServices = getCachedServices()
            if (cachedServices.isNotEmpty()) {
                return@withContext cachedServices
            }
            discoverInstalledApps()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCachedServices(): List<Service> = withContext(Dispatchers.IO) {
        try {
            val entities = serviceDao.getAllServices()
            ServiceMapper.entitiesToDomain(entities) { packageName -> null }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getServicesFlow(): Flow<List<Service>> {
        return serviceDao.getAllServicesFlow().map { entities ->
            ServiceMapper.entitiesToDomain(entities) { packageName -> null }
        }
    }

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

    suspend fun getServicesByImpactLevel(impactLevel: ImpactLevel): List<Service> = withContext(Dispatchers.IO) {
        try {
            val entities = serviceDao.getServicesByImpactLevel(impactLevel.name)
            ServiceMapper.entitiesToDomain(entities)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateServiceUsage(packageName: String, usageMinutes: Int) = withContext(Dispatchers.IO) {
        try {
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

    suspend fun cleanupOldData(olderThanDays: Int = 7) = withContext(Dispatchers.IO) {
        try {
            val cutoffDate = Date(System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L))
            serviceDao.deleteOldServices(cutoffDate)
        } catch (e: Exception) {
            // Silent cleanup failure
        }
    }

    // Private helper methods
    private suspend fun discoverInstalledApps(): List<Service> {
        val discoveryManager = ServiceDiscoveryManager(
            context = context,
            impactClassifier = impactClassifier
        )
        return discoveryManager.getInstalledServices()
    }

    private suspend fun cacheServices(services: List<Service>) {
        try {
            serviceDao.clearAllServices()
            val entities = ServiceMapper.domainToEntities(services)
            serviceDao.insertServices(entities)
        } catch (e: Exception) {
            println("Failed to cache services: ${e.message}")
        }
    }
}

/**
 * Statistics about AI service usage
 */
data class AIServiceStats(
    val isAIAvailable: Boolean,
    val totalServices: Int,
    val servicesWithAI: Int,
    val expiredAIContent: Int,
    val cacheStats: AIService.CacheStats
) {
    companion object {
        fun default() = AIServiceStats(
            isAIAvailable = false,
            totalServices = 0,
            servicesWithAI = 0,
            expiredAIContent = 0,
            cacheStats = AIService.CacheStats(
                totalEntries = 0,
                validEntries = 0,
                expiredEntries = 0,
                requestCount = 0,
                quotaExceeded = false
            )
        )
    }

    val aiCoveragePercentage: Float
        get() = if (totalServices > 0) (servicesWithAI.toFloat() / totalServices) * 100 else 0f

    val quotaStatus: String
        get() = when {
            cacheStats.quotaExceeded -> "Quota Exceeded"
            cacheStats.requestCount >= 10 -> "Session Limit Reached"
            cacheStats.requestCount > 5 -> "Approaching Limit (${cacheStats.requestCount}/10)"
            else -> "Available (${cacheStats.requestCount}/10)"
        }
}