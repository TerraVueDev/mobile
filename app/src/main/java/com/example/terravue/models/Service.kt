package com.example.terravue.models

import android.graphics.drawable.Drawable
import com.example.terravue.R

class Service (
    val name: String,
    val icon: Drawable,
    val impactLevel: ImpactLevel
)

enum class ImpactLevel (val displayName: String, val bgColorResId: Int, val textColorResId: Int) {
    HIGH("high impact", R.color.high_badge_background, R.color.high_badge_text),
    MEDIUM("medium impact", R.color.medium_badge_background, R.color.medium_badge_text),
    LOW("low impact", R.color.low_badge_background, R.color.low_badge_text)
}