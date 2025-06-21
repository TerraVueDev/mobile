package com.example.terravue.domain.models

import java.util.Date

/**
 * Environmental goals and tracking
 */
data class EcoGoal(
    val id: String,
    val type: EcoGoalType,
    val targetValue: Double,
    val currentValue: Double,
    val deadline: Date,
    val isActive: Boolean = true
) {
    val progress: Double
        get() = (currentValue / targetValue).coerceIn(0.0, 1.0)

    val isCompleted: Boolean
        get() = currentValue >= targetValue
}

enum class EcoGoalType(val displayName: String, val unit: String) {
    REDUCE_DAILY_CO2("Reduce Daily CO₂", "grams"),
    LIMIT_HIGH_IMPACT_APPS("Limit High Impact Apps", "apps"),
    INCREASE_LOW_IMPACT_USAGE("Use More Eco-Friendly Apps", "minutes"),
    REDUCE_SCREEN_TIME("Reduce Total Screen Time", "minutes")
}