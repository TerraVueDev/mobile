package com.example.terravue.domain.models

import java.util.Date

/**
 * Detailed category information from GitHub data source
 */
data class CategoryInfo(
    val impact: String,
    val description: String,
    val source: String?,
    val annualEstimate: AnnualEstimate?,
    val category: String? = null,
    val lastUpdated: Date? = null
)

/**
 * Annual environmental impact estimates
 */
data class AnnualEstimate(
    val wh: String,
    val whComparison: String,
    val co2: String,
    val co2Comparison: String,
    val calculationMethod: String? = null
)

/**
 * Daily environmental impact calculation
 */
data class DailyImpact(
    val co2Grams: Double,
    val energyWh: Double
) {
    /**
     * Convert to human-readable format
     */
    fun toDisplayString(): String {
        return "Daily: ${String.format("%.1f", co2Grams)}g CO₂, ${String.format("%.1f", energyWh)}Wh"
    }

    /**
     * Get equivalent environmental comparisons
     */
    fun getComparisons(): List<String> {
        val comparisons = mutableListOf<String>()

        when {
            co2Grams > 10 -> comparisons.add("Like driving ${String.format("%.1f", co2Grams * 0.004)} km")
            co2Grams > 1 -> comparisons.add("Like charging a phone ${String.format("%.0f", co2Grams * 0.5)} times")
            else -> comparisons.add("Less than breathing for 1 minute")
        }

        when {
            energyWh > 5 -> comparisons.add("Like running an LED bulb for ${String.format("%.0f", energyWh / 0.01)} minutes")
            energyWh > 1 -> comparisons.add("Like a smartphone charge cycle")
            else -> comparisons.add("Minimal energy usage")
        }

        return comparisons
    }
}
