package com.example.terravue.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Data source for loading environmental impact data from GitHub repository
 *
 * Handles:
 * - Loading categories.json (app impact classifications)
 * - Loading links.json (domain to category mappings)
 * - Caching responses for offline use
 * - Error handling and retry logic
 * - Network timeout management
 */
class GitHubDataSource {

    companion object {
        private const val CATEGORIES_URL = "https://raw.githubusercontent.com/TerraVueDev/assets/refs/heads/main/categories.json"
        private const val LINKS_URL = "https://raw.githubusercontent.com/TerraVueDev/assets/refs/heads/main/links.json"

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    // In-memory cache for the session
    private var categoriesData: JSONObject? = null
    private var linksData: JSONObject? = null
    private var lastLoadTime: Long = 0
    private val cacheValidityMs = TimeUnit.HOURS.toMillis(6) // Cache for 6 hours

    /**
     * Load latest environmental impact data from GitHub
     * Returns true if successful, false if failed
     */
    suspend fun loadLatestData(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if cache is still valid
            if (isCacheValid()) {
                return@withContext true
            }

            // Load both files concurrently with retry logic
            val categoriesResult = loadWithRetry { fetchJsonFromUrl(CATEGORIES_URL) }
            val linksResult = loadWithRetry { fetchJsonFromUrl(LINKS_URL) }

            if (categoriesResult != null && linksResult != null) {
                categoriesData = JSONObject(categoriesResult)
                linksData = JSONObject(linksResult)
                lastLoadTime = System.currentTimeMillis()

                // Validate the loaded data
                if (validateLoadedData()) {
                    true
                } else {
                    // Clear invalid data
                    categoriesData = null
                    linksData = null
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            println("GitHubDataSource: Failed to load data - ${e.message}")
            false
        }
    }

    /**
     * Get categories data (impact classifications)
     */
    fun getCategoriesData(): JSONObject? = categoriesData

    /**
     * Get links data (domain mappings)
     */
    fun getLinksData(): JSONObject? = linksData

    /**
     * Check if data is currently loaded and valid
     */
    fun isDataLoaded(): Boolean = categoriesData != null && linksData != null && isCacheValid()

    /**
     * Get specific category information
     */
    fun getCategoryInfo(categoryName: String): CategoryData? {
        return try {
            categoriesData?.getJSONObject(categoryName)?.let { categoryJson ->
                CategoryData(
                    impact = categoryJson.getString("impact"),
                    description = categoryJson.getString("description"),
                    source = categoryJson.optString("source"),
                    annualEstimate = categoryJson.optJSONObject("annual_estimate")?.let { annualJson ->
                        AnnualEstimateData(
                            wh = annualJson.getString("wh"),
                            whComparison = annualJson.getString("wh-comparison"),
                            co2 = annualJson.getString("co2"),
                            co2Comparison = annualJson.getString("co2-comparison")
                        )
                    }
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get all available categories
     */
    fun getAllCategories(): List<String> {
        return categoriesData?.keys()?.asSequence()?.toList() ?: emptyList()
    }

    /**
     * Find category for a given domain
     */
    fun findCategoryForDomain(domain: String): String? {
        return try {
            linksData?.optString(domain)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Search for categories matching app name or package
     */
    fun findMatchingCategories(appName: String, packageName: String): List<String> {
        val matchingCategories = mutableListOf<String>()

        try {
            linksData?.keys()?.forEach { domain ->
                val domainName = domain.split(".")[0].lowercase()

                if (appName.contains(domainName, ignoreCase = true) ||
                    packageName.contains(domainName, ignoreCase = true)) {
                    linksData?.getString(domain)?.let { category ->
                        if (!matchingCategories.contains(category)) {
                            matchingCategories.add(category)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty list on error
        }

        return matchingCategories
    }

    /**
     * Get impact statistics summary
     */
    fun getImpactStatistics(): ImpactStatistics {
        try {
            val categories = getAllCategories()
            var highCount = 0
            var mediumCount = 0
            var lowCount = 0

            categories.forEach { category ->
                getCategoryInfo(category)?.let { info ->
                    when (info.impact.lowercase()) {
                        "high" -> highCount++
                        "medium" -> mediumCount++
                        "low" -> lowCount++
                    }
                }
            }

            return ImpactStatistics(
                totalCategories = categories.size,
                highImpactCategories = highCount,
                mediumImpactCategories = mediumCount,
                lowImpactCategories = lowCount,
                totalDomains = linksData?.length() ?: 0,
                lastUpdated = lastLoadTime
            )
        } catch (e: Exception) {
            return ImpactStatistics.empty()
        }
    }

    // Private helper methods

    private fun isCacheValid(): Boolean {
        return categoriesData != null &&
                linksData != null &&
                (System.currentTimeMillis() - lastLoadTime) < cacheValidityMs
    }

    private suspend fun loadWithRetry(loader: suspend () -> String?): String? {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = loader()
                if (result != null) return result
            } catch (e: Exception) {
                if (attempt == MAX_RETRIES - 1) throw e
                kotlinx.coroutines.delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return null
    }

    private suspend fun fetchJsonFromUrl(urlString: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "TerraVue-Android/1.0")
                setRequestProperty("Accept", "application/json")

                // Add cache control headers
                setRequestProperty("Cache-Control", "max-age=3600")
            }

            val responseCode = connection.responseCode
            when {
                responseCode == HttpURLConnection.HTTP_OK -> {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
                responseCode == HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    // Data hasn't changed, use cached version
                    null
                }
                else -> {
                    throw Exception("HTTP $responseCode: ${connection.responseMessage}")
                }
            }
        } catch (e: Exception) {
            println("Network error loading $urlString: ${e.message}")
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                // Ignore disconnect errors
            }
        }
    }

    private fun validateLoadedData(): Boolean {
        return try {
            // Basic validation - check if data has expected structure
            val hasCategories = categoriesData?.length() ?: 0 > 0
            val hasLinks = linksData?.length() ?: 0 > 0

            // Validate at least one category has required fields
            val firstCategory = getAllCategories().firstOrNull()
            val validCategory = firstCategory?.let { categoryName ->
                getCategoryInfo(categoryName)?.let { info ->
                    info.impact.isNotBlank() && info.description.isNotBlank()
                }
            } ?: false

            hasCategories && hasLinks && validCategory
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear cached data (useful for testing or forced refresh)
     */
    fun clearCache() {
        categoriesData = null
        linksData = null
        lastLoadTime = 0
    }
}

// Data classes for structured access

data class CategoryData(
    val impact: String,
    val description: String,
    val source: String?,
    val annualEstimate: AnnualEstimateData?
)

data class AnnualEstimateData(
    val wh: String,
    val whComparison: String,
    val co2: String,
    val co2Comparison: String
)

data class ImpactStatistics(
    val totalCategories: Int,
    val highImpactCategories: Int,
    val mediumImpactCategories: Int,
    val lowImpactCategories: Int,
    val totalDomains: Int,
    val lastUpdated: Long
) {
    companion object {
        fun empty() = ImpactStatistics(
            totalCategories = 0,
            highImpactCategories = 0,
            mediumImpactCategories = 0,
            lowImpactCategories = 0,
            totalDomains = 0,
            lastUpdated = 0
        )
    }
}