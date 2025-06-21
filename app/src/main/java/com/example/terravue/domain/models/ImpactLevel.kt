package com.example.terravue.domain.models

/**
 * Environmental impact levels with associated colors and metadata
 */
enum class ImpactLevel(
    val displayName: String,
    val bgColorResId: Int,
    val textColorResId: Int,
    val co2FactorPerHour: Double,
    val description: String
) {
    HIGH(
        displayName = "High Impact",
        bgColorResId = com.example.terravue.R.color.high_badge_background,
        textColorResId = com.example.terravue.R.color.high_badge_text,
        co2FactorPerHour = 15.0,
        description = "Social media and entertainment apps with high energy consumption"
    ),
    MEDIUM(
        displayName = "Medium Impact",
        bgColorResId = com.example.terravue.R.color.medium_badge_background,
        textColorResId = com.example.terravue.R.color.medium_badge_text,
        co2FactorPerHour = 6.0,
        description = "Streaming and gaming apps with moderate energy usage"
    ),
    LOW(
        displayName = "Low Impact",
        bgColorResId = com.example.terravue.R.color.low_badge_background,
        textColorResId = com.example.terravue.R.color.low_badge_text,
        co2FactorPerHour = 1.5,
        description = "Productivity and utility apps with minimal energy footprint"
    );

    /**
     * Get annual CO2 estimate based on average daily usage
     */
    fun getAnnualCO2Estimate(dailyUsageMinutes: Int): Double {
        val dailyHours = dailyUsageMinutes / 60.0
        val dailyCO2 = co2FactorPerHour * dailyHours
        return dailyCO2 * 365
    }
}