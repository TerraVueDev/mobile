package com.example.terravue.utils

import android.content.Context
import com.example.terravue.domain.models.ImpactLevel
import com.example.terravue.data.remote.CategoryData
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced impact level classifier with modern architecture support
 *
 * Features:
 * - Integration with GitHubDataSource
 * - Improved system app filtering
 * - Machine learning potential for future versions
 * - Better fallback mechanisms
 * - Support for custom user classifications
 */
class ImpactLevelClassifier(val context: Context) {

    companion object {
        // Enhanced system apps filtering
        private val SYSTEM_APPS_TO_IGNORE = setOf(
            // Core Android System
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.ext.services",
            "com.android.systemui",
            "com.android.webview",
            "com.google.android.webview",
            "android",
            "com.android.vending", // Play Store

            // Device Management
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.externalstorage",
            "com.android.providers.settings",
            "com.android.providers.downloads",
            "com.android.providers.media",

            // Communication System Apps
            "com.android.phone",
            "com.android.mms",
            "com.android.contacts",
            "com.android.dialer",

            // Security & Certificates
            "com.android.certinstaller",
            "com.android.keychain",
            "com.google.android.permissioncontroller",

            // Input & Accessibility
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",

            // Launchers (Major OEMs)
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.sec.android.app.launcher", // Samsung
            "com.miui.home", // Xiaomi
            "com.huawei.android.launcher", // Huawei
            "com.oppo.launcher", // Oppo
            "com.oneplus.launcher" // OnePlus
        )

        // System app detection patterns
        private val SYSTEM_APP_PATTERNS = listOf(
            "android.process",
            "com.android.system",
            "com.google.android.gms",
            "systemui",
            ".launcher",
            "inputmethod",
            "com.android.server",
            "com.android.internal",
            "com.google.android.ext",
            ".provider",
            ".service"
        )

        // OEM system prefixes
        private val OEM_SYSTEM_PREFIXES = listOf(
            "com.samsung.android.app.",
            "com.samsung.android.service.",
            "com.miui.",
            "com.xiaomi.mi",
            "com.huawei.android.internal",
            "com.oppo.android.internal",
            "com.oneplus.android.internal",
            "com.android.internal"
        )

        // Enhanced fallback classifications
        private val HIGH_IMPACT_KEYWORDS = listOf(
            "instagram", "facebook", "twitter", "tiktok", "snapchat",
            "youtube", "twitch", "discord", "reddit", "linkedin"
        )

        private val MEDIUM_IMPACT_KEYWORDS = listOf(
            "netflix", "spotify", "amazon", "disney", "hulu",
            "uber", "lyft", "deliveroo", "foodpanda", "grab",
            "game", "gaming", "play"
        )

        private val LOW_IMPACT_KEYWORDS = listOf(
            "office", "docs", "drive", "calendar", "email",
            "calculator", "notes", "weather", "clock", "flashlight",
            "banking", "finance", "health", "fitness"
        )
    }

    // GitHub data integration
    private var categoriesData: JSONObject? = null
    private var linksData: JSONObject? = null
    private val domainToCategory = ConcurrentHashMap<String, String>()
    private var isDataLoaded = false

    // User custom classifications (for future features)
    private val userClassifications = ConcurrentHashMap<String, ImpactLevel>()

    /**
     * Update classifier with GitHub data
     */
    fun updateFromGitHubData(categories: JSONObject?, links: JSONObject?) {
        categoriesData = categories
        linksData = links

        if (categories != null && links != null) {
            populateDomainMapping()
            isDataLoaded = true
        }
    }

    /**
     * Determine impact level for an app
     * Returns null if app should be ignored (system app)
     */
    fun determineImpactLevel(packageName: String, appName: String): ImpactLevel? {
        // First check if this is a system app that should be ignored
        if (isSystemApp(packageName, appName)) {
            return null
        }

        // Check user custom classifications first
        userClassifications[packageName]?.let { return it }

        // Use GitHub data if available
        return if (isDataLoaded) {
            determineImpactLevelFromGitHub(packageName, appName)
        } else {
            // Fallback to hardcoded logic
            determineImpactLevelFallback(packageName, appName)
        }
    }

    /**
     * Get detailed category information
     */
    fun getCategoryInfo(categoryName: String): CategoryInfo? {
        return try {
            categoriesData?.getJSONObject(categoryName)?.let { categoryJson ->
                val annualEstimate = categoryJson.optJSONObject("annual_estimate")?.let { annualJson ->
                    AnnualEstimate(
                        wh = annualJson.getString("wh"),
                        whComparison = annualJson.getString("wh-comparison"),
                        co2 = annualJson.getString("co2"),
                        co2Comparison = annualJson.getString("co2-comparison")
                    )
                }

                CategoryInfo(
                    impact = categoryJson.getString("impact"),
                    description = categoryJson.getString("description"),
                    source = categoryJson.optString("source"),
                    annualEstimate = annualEstimate
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Add user custom classification
     */
    fun addUserClassification(packageName: String, impactLevel: ImpactLevel) {
        userClassifications[packageName] = impactLevel
        // TODO: Persist to local storage for future sessions
    }

    /**
     * Get all available categories
     */
    fun getAllCategories(): List<String> {
        return categoriesData?.keys()?.asSequence()?.toList() ?: emptyList()
    }

    /**
     * Check if GitHub data is loaded
     */
    fun isDataLoaded(): Boolean = isDataLoaded

    /**
     * Get classification confidence score (for future ML features)
     */
    fun getClassificationConfidence(packageName: String, appName: String): Float {
        return when {
            userClassifications.containsKey(packageName) -> 1.0f
            isDataLoaded && findCategoryFromPackageOrApp(packageName, appName) != null -> 0.9f
            matchesFallbackKeywords(packageName, appName) -> 0.7f
            else -> 0.5f
        }
    }

    // Private helper methods

    private fun populateDomainMapping() {
        linksData?.let { links ->
            links.keys().forEach { domain ->
                try {
                    val category = links.getString(domain)
                    domainToCategory[domain] = category
                } catch (e: Exception) {
                    // Skip invalid entries
                }
            }
        }
    }

    private fun determineImpactLevelFromGitHub(packageName: String, appName: String): ImpactLevel {
        val matchedCategory = findCategoryFromPackageOrApp(packageName, appName)

        return if (matchedCategory != null) {
            val categoryInfo = getCategoryInfo(matchedCategory)
            when (categoryInfo?.impact?.lowercase()) {
                "high" -> ImpactLevel.HIGH
                "medium" -> ImpactLevel.MEDIUM
                "low" -> ImpactLevel.LOW
                else -> determineImpactLevelFallback(packageName, appName)
            }
        } else {
            determineImpactLevelFallback(packageName, appName)
        }
    }

    private fun findCategoryFromPackageOrApp(packageName: String, appName: String): String? {
        // Check exact domain matches first
        domainToCategory.forEach { (domain, category) ->
            if (packageName.contains(domain, ignoreCase = true)) {
                return category
            }
        }

        // Check partial matches
        domainToCategory.forEach { (domain, category) ->
            val domainName = domain.split(".")[0]
            if (packageName.contains(domainName, ignoreCase = true) ||
                appName.contains(domainName, ignoreCase = true)) {
                return category
            }
        }

        return null
    }

    private fun determineImpactLevelFallback(packageName: String, appName: String): ImpactLevel {
        val lowerPackage = packageName.lowercase()
        val lowerName = appName.lowercase()

        return when {
            HIGH_IMPACT_KEYWORDS.any { keyword ->
                lowerPackage.contains(keyword) || lowerName.contains(keyword)
            } -> ImpactLevel.HIGH

            MEDIUM_IMPACT_KEYWORDS.any { keyword ->
                lowerPackage.contains(keyword) || lowerName.contains(keyword)
            } -> ImpactLevel.MEDIUM

            LOW_IMPACT_KEYWORDS.any { keyword ->
                lowerPackage.contains(keyword) || lowerName.contains(keyword)
            } -> ImpactLevel.LOW

            else -> ImpactLevel.LOW // Default to low impact
        }
    }

    private fun matchesFallbackKeywords(packageName: String, appName: String): Boolean {
        val lowerPackage = packageName.lowercase()
        val lowerName = appName.lowercase()

        val allKeywords = HIGH_IMPACT_KEYWORDS + MEDIUM_IMPACT_KEYWORDS + LOW_IMPACT_KEYWORDS
        return allKeywords.any { keyword ->
            lowerPackage.contains(keyword) || lowerName.contains(keyword)
        }
    }

    private fun isSystemApp(packageName: String, appName: String): Boolean {
        val lowerPackageName = packageName.lowercase()
        val lowerAppName = appName.lowercase()

        // Check exact matches
        if (SYSTEM_APPS_TO_IGNORE.contains(lowerPackageName)) {
            return true
        }

        // Check OEM prefixes
        if (OEM_SYSTEM_PREFIXES.any { lowerPackageName.startsWith(it) }) {
            return true
        }

        // Check patterns
        if (SYSTEM_APP_PATTERNS.any { pattern ->
                lowerPackageName.contains(pattern) || lowerAppName.contains(pattern)
            }) {
            return true
        }

        // Additional heuristics
        return isSystemAppByHeuristics(lowerPackageName, lowerAppName)
    }

    private fun isSystemAppByHeuristics(packageName: String, appName: String): Boolean {
        // Very short package names are often system components
        if (packageName.split(".").size < 3 && packageName.length < 15) {
            return true
        }

        // Apps with technical names and no clear branding
        if (appName.matches(Regex("^[A-Z][a-z]*[A-Z][a-z]*$")) && appName.length < 15) {
            return true
        }

        // System-like app names
        val systemAppNamePrefixes = listOf(
            "android", "system", "google play", "samsung",
            "miui", "huawei", "configuration", "setup"
        )

        return systemAppNamePrefixes.any { prefix ->
            appName.startsWith(prefix, ignoreCase = true)
        }
    }

    // Data classes for compatibility with existing code
    data class CategoryInfo(
        val impact: String,
        val description: String,
        val source: String?,
        val annualEstimate: AnnualEstimate?
    )

    data class AnnualEstimate(
        val wh: String,
        val whComparison: String,
        val co2: String,
        val co2Comparison: String
    )
}