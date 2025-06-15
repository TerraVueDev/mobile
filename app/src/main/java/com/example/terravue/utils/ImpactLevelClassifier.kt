package com.example.terravue.utils

import android.content.Context
import com.example.terravue.models.ImpactLevel
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class ImpactLevelClassifier(private val context: Context) {

    companion object {
        private const val CATEGORIES_URL = "https://raw.githubusercontent.com/TerraVueDev/assets/refs/heads/main/categories.json"
        private const val LINKS_URL = "https://raw.githubusercontent.com/TerraVueDev/assets/refs/heads/main/links.json"

        // Expanded system apps list
        private val SYSTEM_APPS_TO_IGNORE = setOf(
            // Google Play Services & Core
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.ext.services",
            "com.google.android.partnersetup",
            "com.google.android.setupwizard",

            // Android System Components
            "com.android.systemui",
            "com.android.webview",
            "com.google.android.webview",
            "android",
            "com.android.chrome",
            "com.android.shell",
            "com.android.sharedstoragebackup",
            "com.android.statementservice",

            // System Apps
            "com.android.vending", // Google Play Store
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.settings",
            "com.android.systemui",
            "com.android.externalstorage",
            "com.android.providers.settings",
            "com.android.providers.downloads",
            "com.android.providers.media",
            "com.android.providers.calendar",
            "com.android.providers.contacts",

            // Launchers
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.sec.android.app.launcher", // Samsung
            "com.miui.home", // Xiaomi
            "com.huawei.android.launcher", // Huawei
            "com.oppo.launcher", // Oppo
            "com.oneplus.launcher", // OnePlus

            // Input Methods & Keyboards
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.android.inputmethod.latin",
            "com.sohu.inputmethod.sogou",

            // Telephony & Communication
            "com.android.phone",
            "com.android.mms",
            "com.android.contacts",
            "com.android.dialer",
            "com.samsung.android.messaging",

            // System Services
            "com.android.bluetooth",
            "com.android.nfc",
            "com.android.server.telecom",
            "com.android.printspooler",
            "com.android.wallpaper",
            "com.android.wallpaperbackup",

            // Certificate & Security
            "com.android.certinstaller",
            "com.android.keychain",
            "com.google.android.permissioncontroller",

            // OEM System Apps (add more as needed)
            "com.samsung.android.app.telephonyui",
            "com.samsung.android.bixby.agent",
            "com.samsung.android.oneconnect",
            "com.miui.securitycenter",
            "com.xiaomi.finddevice"
        )

        // Expanded system app keywords
        private val SYSTEM_APP_KEYWORDS = listOf(
            "android.process",
            "com.android.system",
            "com.google.android.gms",
            "systemui",
            ".launcher",
            "inputmethod",
            "com.android.server",
            "com.android.internal",
            "com.google.android.ext",
            "com.google.android.apps.carrier",
            ".provider",
            ".service",
            "com.samsung.android.app.system",
            "com.miui.system",
            "com.huawei.system",
            "com.oppo.system",
            "com.oneplus.system",
            "com.android.cts", // Compatibility Test Suite
            "com.google.android.projection" // Android Auto
        )

        // OEM prefixes that are typically system apps
        private val OEM_SYSTEM_PREFIXES = listOf(
            "com.samsung.android.app.",
            "com.samsung.android.service.",
            "com.miui.",
            "com.xiaomi.mi",
            "com.huawei.android.internal",
            "com.oppo.android.internal",
            "com.oneplus.android.internal",
            "com.android.internal",
            "com.google.android.apps.wellbeing", // Digital Wellbeing
            "com.google.android.apps.restore" // Restore
        )

        private val FALLBACK_HIGH_IMPACT_APPS = listOf(
            "instagram", "facebook", "twitter", "tiktok", "snapchat"
        )

        private val FALLBACK_MEDIUM_IMPACT_STREAMING_APPS = listOf(
            "netflix", "youtube", "spotify", "disney"
        )

        private val FALLBACK_LOW_IMPACT_PRODUCTIVITY_APPS = listOf(
            "office", "docs", "drive", "calendar"
        )
    }


    // Cache for the loaded data
    private var categoriesData: JSONObject? = null
    private var linksData: JSONObject? = null
    private val domainToCategory = ConcurrentHashMap<String, String>()
    private var isDataLoaded = false

    // Data classes for structured access
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

    suspend fun loadDataFromGitHub(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Load both JSON files concurrently
                val categoriesDeferred = async { fetchJsonFromUrl(CATEGORIES_URL) }
                val linksDeferred = async { fetchJsonFromUrl(LINKS_URL) }

                val categoriesJson = categoriesDeferred.await()
                val linksJson = linksDeferred.await()

                if (categoriesJson != null && linksJson != null) {
                    categoriesData = JSONObject(categoriesJson)
                    linksData = JSONObject(linksJson)

                    // Populate domain to category mapping for faster lookups
                    populateDomainMapping()
                    isDataLoaded = true
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun fetchJsonFromUrl(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                // Ignore disconnect errors
            }
        }
    }


    private fun populateDomainMapping() {
        linksData?.let { links ->
            links.keys().forEach { domain ->
                val category = links.getString(domain)
                domainToCategory[domain] = category
            }
        }
    }

    fun determineImpactLevel(packageName: String, appName: String): ImpactLevel? {
        // First check if this is a system app that should be ignored
        if (isSystemApp(packageName, appName)) {
            return null // Return null to indicate this app should be ignored
        }

        return if (isDataLoaded) {
            determineImpactLevelFromGitHub(packageName, appName)
        } else {
            // Fallback to hardcoded logic
            determineImpactLevelFallback(packageName, appName)
        }
    }

    private fun determineImpactLevelFromGitHub(packageName: String, appName: String): ImpactLevel {
        // Try to match domain from package name or app name
        val matchedCategory = findCategoryFromPackageOrApp(packageName, appName)

        return if (matchedCategory != null) {
            val categoryInfo = getCategoryInfo(matchedCategory)
            when (categoryInfo?.impact?.lowercase()) {
                "high" -> ImpactLevel.HIGH
                "medium" -> ImpactLevel.MEDIUM
                "low" -> ImpactLevel.LOW
                else -> ImpactLevel.LOW
            }
        } else {
            // Fallback logic if no match found
            determineImpactLevelFallback(packageName, appName)
        }
    }

    private fun findCategoryFromPackageOrApp(packageName: String, appName: String): String? {
        // Check if package name or app name contains any of the domains
        domainToCategory.forEach { (domain, category) ->
            val domainName = domain.split(".")[0] // Get the main part (e.g., "youtube" from "youtube.com")
            if (packageName.contains(domainName, ignoreCase = true) ||
                appName.contains(domainName, ignoreCase = true)) {
                return category
            }
        }
        return null
    }

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
            e.printStackTrace()
            null
        }
    }

    // Fallback method using hardcoded values
    private fun determineImpactLevelFallback(packageName: String, appName: String): ImpactLevel {
        return when {
            isHighImpactAppFallback(packageName) -> ImpactLevel.HIGH
            isMediumImpactAppFallback(packageName, appName) -> ImpactLevel.MEDIUM
            isLowImpactAppFallback(packageName) -> ImpactLevel.LOW
            else -> ImpactLevel.LOW
        }
    }

    private fun isHighImpactAppFallback(packageName: String): Boolean {
        return FALLBACK_HIGH_IMPACT_APPS.any { packageName.contains(it, ignoreCase = true) }
    }

    private fun isMediumImpactAppFallback(packageName: String, appName: String): Boolean {
        return FALLBACK_MEDIUM_IMPACT_STREAMING_APPS.any { packageName.contains(it, ignoreCase = true) } ||
                isGamingApp(packageName, appName)
    }

    private fun isGamingApp(packageName: String, appName: String): Boolean {
        return packageName.contains("game", ignoreCase = true) ||
                appName.contains("game", ignoreCase = true)
    }

    private fun isLowImpactAppFallback(packageName: String): Boolean {
        return FALLBACK_LOW_IMPACT_PRODUCTIVITY_APPS.any { packageName.contains(it, ignoreCase = true) }
    }

    // Utility method to get all available categories
    fun getAllCategories(): List<String> {
        return categoriesData?.keys()?.asSequence()?.toList() ?: emptyList()
    }

    // Check if data is loaded
    fun isDataLoaded(): Boolean = isDataLoaded

    private fun isSystemApp(packageName: String, appName: String): Boolean {
        val lowerPackageName = packageName.lowercase()
        val lowerAppName = appName.lowercase()

        // 1. Check exact package name matches
        if (SYSTEM_APPS_TO_IGNORE.contains(lowerPackageName)) {
            return true
        }

        // 2. Check OEM system prefixes
        if (OEM_SYSTEM_PREFIXES.any { lowerPackageName.startsWith(it) }) {
            return true
        }
        if (SYSTEM_APP_KEYWORDS.any { keyword ->
            lowerPackageName.contains(keyword) || lowerAppName.contains(keyword)
        }) {
            return true
        }

        // 4. Additional heuristics for system apps
        return isSystemAppByHeuristics(lowerPackageName, lowerAppName)
    }

    private fun isSystemAppByHeuristics(packageName: String, appName: String): Boolean {
        // Apps with no clear user-facing name (often system components)
        if (appName.matches(Regex("^[A-Z][a-z]*[A-Z][a-z]*$")) && appName.length < 15) {
            return true
        }

        // Apps that start with system-like names
        val systemAppNamePrefixes = listOf(
            "android",
            "system",
            "google play",
            "samsung",
            "miui",
            "huawei",
            "configuration",
            "setup wizard",
            "device policy"
        )

        return systemAppNamePrefixes.any { prefix ->
            appName.startsWith(prefix, ignoreCase = true)
        }
    }

}