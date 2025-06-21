package com.example.terravue.domain.models

import android.graphics.drawable.Drawable
import java.util.Date

/**
 * Domain model for a digital service/app and its environmental impact
 */
data class Service(
    val id: String = "",
    val name: String,
    val packageName: String,
    val icon: Drawable? = null,
    val impactLevel: ImpactLevel,
    val categoryInfo: CategoryInfo? = null,
    val installDate: Date? = null,
    val lastUsed: Date? = null,
    val usageStats: UsageStats? = null
) {

    // Computed properties for easy access to environmental data
    val description: String?
        get() = categoryInfo?.description

    val energyConsumption: String?
        get() = categoryInfo?.annualEstimate?.wh

    val energyComparison: String?
        get() = categoryInfo?.annualEstimate?.whComparison

    val co2Emissions: String?
        get() = categoryInfo?.annualEstimate?.co2

    val co2Comparison: String?
        get() = categoryInfo?.annualEstimate?.co2Comparison

    val sourceUrl: String?
        get() = categoryInfo?.source

    val hasDetailedInfo: Boolean
        get() = categoryInfo != null

    /**
     * Calculate estimated daily environmental impact based on usage
     */
    fun getDailyImpactEstimate(): DailyImpact {
        val baseImpact = when (impactLevel) {
            ImpactLevel.HIGH -> DailyImpact(co2Grams = 2.5, energyWh = 8.0)
            ImpactLevel.MEDIUM -> DailyImpact(co2Grams = 1.0, energyWh = 3.2)
            ImpactLevel.LOW -> DailyImpact(co2Grams = 0.2, energyWh = 0.8)
        }

        // Adjust based on actual usage if available
        val usageMultiplier = usageStats?.let { stats ->
            (stats.dailyUsageMinutes / 30.0).coerceIn(0.1, 3.0)
        } ?: 1.0

        return DailyImpact(
            co2Grams = baseImpact.co2Grams * usageMultiplier,
            energyWh = baseImpact.energyWh * usageMultiplier
        )
    }

    /**
     * Get environmental improvement suggestions
     */
    fun getEcoSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()

        when (impactLevel) {
            ImpactLevel.HIGH -> {
                suggestions.add("Limit daily usage to reduce carbon footprint")
                suggestions.add("Use app in dark mode to save device energy")
                if (name.contains("streaming", ignoreCase = true)) {
                    suggestions.add("Download content for offline viewing")
                }
            }
            ImpactLevel.MEDIUM -> {
                suggestions.add("Consider eco-friendly alternatives")
                suggestions.add("Adjust video quality to reduce data usage")
            }
            ImpactLevel.LOW -> {
                suggestions.add("Great choice! This app has minimal environmental impact")
            }
        }

        return suggestions
    }
}