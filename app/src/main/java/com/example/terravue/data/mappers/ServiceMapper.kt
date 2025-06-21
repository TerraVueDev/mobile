package com.example.terravue.data.mappers

import android.graphics.drawable.Drawable
import com.example.terravue.data.local.entities.ServiceEntity
import com.example.terravue.domain.models.Service
import com.example.terravue.domain.models.ImpactLevel
import com.example.terravue.domain.models.CategoryInfo
import com.example.terravue.domain.models.AnnualEstimate
import com.example.terravue.domain.models.UsageStats
import com.example.terravue.domain.models.UsageFrequency
import java.util.Date

/**
 * Mapper class for converting between data layer entities and domain models
 *
 * This class handles the conversion between:
 * - ServiceEntity (database) ↔ Service (domain model)
 * - Includes proper mapping of complex types and relationships
 */
object ServiceMapper {

    /**
     * Convert ServiceEntity to domain Service model
     */
    fun entityToDomain(
        entity: ServiceEntity,
        icon: Drawable? = null
    ): Service {
        return Service(
            id = entity.id,
            name = entity.name,
            packageName = entity.packageName,
            icon = icon, // Icons are not stored in database, passed separately
            impactLevel = mapStringToImpactLevel(entity.userClassification ?: entity.impactLevel),
            categoryInfo = mapToCategoryInfo(entity),
            installDate = null, // Could be added to entity in future
            lastUsed = entity.lastUsed,
            usageStats = mapToUsageStats(entity)
        )
    }

    /**
     * Convert domain Service model to ServiceEntity
     */
    fun domainToEntity(
        service: Service,
        cachedAt: Date = Date()
    ): ServiceEntity {
        return ServiceEntity(
            id = service.id,
            name = service.name,
            packageName = service.packageName,
            impactLevel = service.impactLevel.name,
            description = service.categoryInfo?.description,
            energyConsumption = service.categoryInfo?.annualEstimate?.wh,
            co2Emissions = service.categoryInfo?.annualEstimate?.co2,
            sourceUrl = service.categoryInfo?.source,
            cachedAt = cachedAt,
            lastUsed = service.lastUsed,
            usageMinutes = service.usageStats?.dailyUsageMinutes ?: 0,
            dailyCO2Grams = service.getDailyImpactEstimate().co2Grams,
            dailyEnergyWh = service.getDailyImpactEstimate().energyWh,
            isSystemApp = false, // Set externally based on classification
            isFavorite = false, // Default value, can be updated separately
            userClassification = null // Set when user manually overrides
        )
    }

    /**
     * Convert list of entities to domain models
     */
    fun entitiesToDomain(
        entities: List<ServiceEntity>,
        iconProvider: (String) -> Drawable? = { null }
    ): List<Service> {
        return entities.map { entity ->
            entityToDomain(
                entity = entity,
                icon = iconProvider(entity.packageName)
            )
        }
    }

    /**
     * Convert list of domain models to entities
     */
    fun domainToEntities(
        services: List<Service>,
        cachedAt: Date = Date()
    ): List<ServiceEntity> {
        return services.map { service ->
            domainToEntity(service, cachedAt)
        }
    }

    // Private helper methods

    /**
     * Map string impact level to enum
     */
    private fun mapStringToImpactLevel(impactLevelString: String): ImpactLevel {
        return when (impactLevelString.uppercase()) {
            "HIGH" -> ImpactLevel.HIGH
            "MEDIUM" -> ImpactLevel.MEDIUM
            "LOW" -> ImpactLevel.LOW
            else -> {
                // Log warning about unknown impact level
                println("Warning: Unknown impact level '$impactLevelString', defaulting to LOW")
                ImpactLevel.LOW
            }
        }
    }

    /**
     * Map entity data to CategoryInfo domain model
     */
    private fun mapToCategoryInfo(entity: ServiceEntity): CategoryInfo? {
        // Only create CategoryInfo if we have meaningful data
        return if (entity.description != null ||
            entity.energyConsumption != null ||
            entity.co2Emissions != null) {

            val annualEstimate = if (entity.energyConsumption != null && entity.co2Emissions != null) {
                AnnualEstimate(
                    wh = entity.energyConsumption,
                    whComparison = generateEnergyComparison(entity.energyConsumption),
                    co2 = entity.co2Emissions,
                    co2Comparison = generateCO2Comparison(entity.co2Emissions),
                    calculationMethod = "Cached from GitHub data"
                )
            } else null

            CategoryInfo(
                impact = entity.impactLevel,
                description = entity.description ?: "No description available",
                source = entity.sourceUrl,
                annualEstimate = annualEstimate,
                category = null, // Could be added to entity
                lastUpdated = entity.cachedAt
            )
        } else null
    }

    /**
     * Map entity usage data to UsageStats domain model
     */
    private fun mapToUsageStats(entity: ServiceEntity): UsageStats? {
        return if (entity.usageMinutes > 0) {
            UsageStats(
                dailyUsageMinutes = entity.usageMinutes,
                weeklyUsageMinutes = entity.usageMinutes * 7, // Estimate
                monthlyUsageMinutes = entity.usageMinutes * 30, // Estimate
                lastUsedDate = entity.lastUsed,
                usageFrequency = determineUsageFrequency(entity.usageMinutes)
            )
        } else null
    }

    /**
     * Determine usage frequency based on daily minutes
     */
    private fun determineUsageFrequency(dailyMinutes: Int): UsageFrequency {
        return when {
            dailyMinutes >= 120 -> UsageFrequency.HEAVY    // 2+ hours
            dailyMinutes >= 60 -> UsageFrequency.MODERATE  // 1-2 hours
            dailyMinutes >= 10 -> UsageFrequency.LIGHT     // 10-60 minutes
            else -> UsageFrequency.RARELY                  // <10 minutes
        }
    }

    /**
     * Generate human-readable energy consumption comparison
     */
    private fun generateEnergyComparison(energyWh: String): String {
        return try {
            val whValue = energyWh.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            when {
                whValue == null -> "Unable to calculate comparison"
                whValue > 1000 -> "Like running a laptop for ${String.format("%.1f", whValue / 50)} hours"
                whValue > 100 -> "Like charging a phone ${String.format("%.0f", whValue / 15)} times"
                whValue > 10 -> "Like running an LED bulb for ${String.format("%.0f", whValue)} hours"
                else -> "Minimal energy consumption"
            }
        } catch (e: Exception) {
            "Comparison unavailable"
        }
    }

    /**
     * Generate human-readable CO2 emissions comparison
     */
    private fun generateCO2Comparison(co2: String): String {
        return try {
            val co2Value = co2.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            when {
                co2Value == null -> "Unable to calculate comparison"
                co2Value > 1000 -> "Like driving ${String.format("%.1f", co2Value * 0.004)} km"
                co2Value > 100 -> "Like ${String.format("%.0f", co2Value / 1000)} days of breathing"
                co2Value > 10 -> "Like charging a phone ${String.format("%.0f", co2Value * 0.5)} times"
                else -> "Very low carbon footprint"
            }
        } catch (e: Exception) {
            "Comparison unavailable"
        }
    }

    /**
     * Update entity with fresh domain data (for cache updates)
     */
    fun updateEntityFromDomain(
        existingEntity: ServiceEntity,
        updatedService: Service
    ): ServiceEntity {
        return existingEntity.copy(
            name = updatedService.name,
            description = updatedService.categoryInfo?.description ?: existingEntity.description,
            energyConsumption = updatedService.categoryInfo?.annualEstimate?.wh ?: existingEntity.energyConsumption,
            co2Emissions = updatedService.categoryInfo?.annualEstimate?.co2 ?: existingEntity.co2Emissions,
            sourceUrl = updatedService.categoryInfo?.source ?: existingEntity.sourceUrl,
            lastUsed = updatedService.lastUsed ?: existingEntity.lastUsed,
            usageMinutes = updatedService.usageStats?.dailyUsageMinutes ?: existingEntity.usageMinutes,
            dailyCO2Grams = updatedService.getDailyImpactEstimate().co2Grams,
            dailyEnergyWh = updatedService.getDailyImpactEstimate().energyWh,
            cachedAt = Date() // Update cache timestamp
        )
    }
}