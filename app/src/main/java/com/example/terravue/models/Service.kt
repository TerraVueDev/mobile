// service/Service.kt
package com.example.terravue.models

import android.graphics.drawable.Drawable
import com.example.terravue.R
import com.example.terravue.utils.ImpactLevelClassifier

class Service(
    val name: String,
    val icon: Drawable,
    val impactLevel: ImpactLevel,
    val categoryInfo: ImpactLevelClassifier.CategoryInfo? = null
) {

    // Helper properties for easy access to GitHub data
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

    // Check if this service has detailed GitHub data
    val hasDetailedInfo: Boolean
        get() = categoryInfo != null
}

enum class ImpactLevel(val displayName: String, val bgColorResId: Int, val textColorResId: Int) {
    HIGH("high impact", R.color.high_badge_background, R.color.high_badge_text),
    MEDIUM("medium impact", R.color.medium_badge_background, R.color.medium_badge_text),
    LOW("low impact", R.color.low_badge_background, R.color.low_badge_text)
}