package com.example.terravue.domain.models

import java.util.Date

/**
 * App usage statistics for impact calculation
 */
data class UsageStats(
    val dailyUsageMinutes: Int,
    val weeklyUsageMinutes: Int,
    val monthlyUsageMinutes: Int,
    val lastUsedDate: Date?,
    val usageFrequency: UsageFrequency
)

/**
 * Usage frequency categories
 */
enum class UsageFrequency(val displayName: String, val impactMultiplier: Double) {
    HEAVY("Heavy User", 2.0),
    MODERATE("Moderate User", 1.0),
    LIGHT("Light User", 0.5),
    RARELY("Rarely Used", 0.1)
}