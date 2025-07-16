package com.example.terravue.domain.models

import android.graphics.drawable.Drawable
import java.util.Date

/**
 * Enhanced Service domain model with AI-generated content support
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
    val usageStats: UsageStats? = null,

    // AI-generated content
    val aiContent: AIGeneratedContent? = null
) {

    // Computed properties for easy access to environmental data
    val description: String?
        get() = aiContent?.impactExplanation ?: categoryInfo?.description

    val energyConsumption: String?
        get() = categoryInfo?.annualEstimate?.wh

    val energyComparison: String?
        get() = aiContent?.energyComparison ?: categoryInfo?.annualEstimate?.whComparison

    val co2Emissions: String?
        get() = categoryInfo?.annualEstimate?.co2

    val co2Comparison: String?
        get() = aiContent?.co2Comparison ?: categoryInfo?.annualEstimate?.co2Comparison

    val sourceUrl: String?
        get() = categoryInfo?.source

    val hasDetailedInfo: Boolean
        get() = categoryInfo != null || aiContent != null

    val hasAIContent: Boolean
        get() = aiContent != null

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
     * Prioritizes AI-generated suggestions if available
     */
    fun getEcoSuggestions(): List<String> {
        // Use AI-generated suggestions if available
        aiContent?.ecoSuggestions?.takeIf { it.isNotEmpty() }?.let { return it }

        // Fallback to static suggestions
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

    /**
     * Get annual impact projection with AI enhancement
     */
    fun getAnnualProjection(): String? {
        // Use AI-generated projection if available
        aiContent?.annualProjection?.let { return it }

        // Fallback to calculated projection
        val daily = getDailyImpactEstimate()
        val annualCO2 = daily.co2Grams * 365
        val annualEnergy = daily.energyWh * 365

        return "Annual impact: ${String.format("%.1f", annualCO2)}g CO₂, ${String.format("%.1f", annualEnergy)}Wh"
    }

    /**
     * Create copy with AI content
     */
    fun withAIContent(aiContent: AIGeneratedContent): Service {
        return copy(aiContent = aiContent)
    }

    /**
     * Check if AI content needs refresh
     */
    fun needsAIRefresh(): Boolean {
        val aiContent = this.aiContent ?: return true
        val ageHours = (System.currentTimeMillis() - aiContent.generatedAt) / (1000 * 60 * 60)
        return ageHours >= 24 // Refresh after 24 hours
    }
}

/**
 * AI-generated content for a service
 */
data class AIGeneratedContent(
    val co2Comparison: String?,
    val energyComparison: String?,
    val impactExplanation: String?,
    val ecoSuggestions: List<String>,
    val annualProjection: String?,
    val generatedAt: Long = System.currentTimeMillis(),
    val aiVersion: String = "v1.0" // For future AI model versioning
) {
    /**
     * Check if content is expired and needs refresh
     */
    fun isExpired(maxAgeHours: Int = 24): Boolean {
        val ageHours = (System.currentTimeMillis() - generatedAt) / (1000 * 60 * 60)
        return ageHours >= maxAgeHours
    }

    /**
     * Get content age in hours
     */
    fun getAgeHours(): Long {
        return (System.currentTimeMillis() - generatedAt) / (1000 * 60 * 60)
    }

    companion object {
        /**
         * Create empty AI content placeholder
         */
        fun empty(): AIGeneratedContent {
            return AIGeneratedContent(
                co2Comparison = null,
                energyComparison = null,
                impactExplanation = null,
                ecoSuggestions = emptyList(),
                annualProjection = null
            )
        }

        /**
         * Create loading state
         */
        fun loading(): AIGeneratedContent {
            return AIGeneratedContent(
                co2Comparison = "Generating comparison...",
                energyComparison = "Analyzing energy usage...",
                impactExplanation = "Creating explanation...",
                ecoSuggestions = listOf("Loading suggestions..."),
                annualProjection = "Calculating annual impact..."
            )
        }
    }
}