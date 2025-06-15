package com.example.terravue.ui

import android.widget.TextView
import com.example.terravue.models.ImpactLevel
import com.example.terravue.models.Service

class ImpactCountsManager(
    private val highImpactCountText: TextView,
    private val mediumImpactCountText: TextView,
    private val lowImpactCountText: TextView
) {

    fun updateCounts(services: List<Service>) {
        val counts = calculateImpactCounts(services)
        displayCounts(counts)
    }

    private fun calculateImpactCounts(services: List<Service>): ImpactCounts {
        return ImpactCounts(
            high = services.count { it.impactLevel == ImpactLevel.HIGH },
            medium = services.count { it.impactLevel == ImpactLevel.MEDIUM },
            low = services.count { it.impactLevel == ImpactLevel.LOW }
        )
    }

    private fun displayCounts(counts: ImpactCounts) {
        highImpactCountText.text = counts.high.toString()
        mediumImpactCountText.text = counts.medium.toString()
        lowImpactCountText.text = counts.low.toString()
    }

    data class ImpactCounts(
        val high: Int,
        val medium: Int,
        val low: Int
    )
}
