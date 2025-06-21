package com.example.terravue.services

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.example.terravue.domain.models.ImpactLevel
import com.example.terravue.domain.models.Service
import com.example.terravue.domain.models.CategoryInfo
import com.example.terravue.domain.models.AnnualEstimate
import com.example.terravue.utils.ImpactLevelClassifier
import java.util.Date

/**
 * Enhanced ServiceDiscoveryManager for TerraVue
 *
 * Discovers installed apps and classifies their environmental impact
 * Integrates with ImpactLevelClassifier for accurate categorization
 */
class ServiceDiscoveryManager(
    private val context: Context,
    private val impactClassifier: ImpactLevelClassifier? = null
) {

    private val packageManager = context.packageManager
    // Use passed classifier or create fallback
    private val classifier = impactClassifier ?: ImpactLevelClassifier(context)

    /**
     * Get all installed services with environmental impact classification
     */
    fun getInstalledServices(): List<Service> {
        val installedApps = getInstalledApplications()
        val userApps = filterUserApplications(installedApps)
        val services = convertToServices(userApps)
        return services.sortedBy { it.name }
    }

    /**
     * Get installed applications from PackageManager
     */
    private fun getInstalledApplications(): List<ApplicationInfo> {
        return try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.e("ServiceDiscovery", "Failed to get installed applications", e)
            emptyList()
        }
    }

    /**
     * Filter to only user-installed applications
     */
    private fun filterUserApplications(apps: List<ApplicationInfo>): List<ApplicationInfo> {
        return apps.filter { isUserApplication(it) }
    }

    /**
     * Check if an application is user-installed (not system)
     */
    private fun isUserApplication(app: ApplicationInfo): Boolean {
        return try {
            // Check if it's a user app or updated system app
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convert ApplicationInfo objects to Service objects
     */
    private fun convertToServices(apps: List<ApplicationInfo>): List<Service> {
        val services = mutableListOf<Service>()

        apps.forEach { app ->
            try {
                val service = createService(app)
                // Only add non-null services (filters out system apps)
                service?.let { services.add(it) }
            } catch (e: Exception) {
                handleServiceCreationError(e, app)
            }
        }

        return services
    }

    /**
     * Create a Service object from ApplicationInfo
     */
    private fun createService(app: ApplicationInfo): Service? {
        return try {
            val appName = getAppName(app)
            val appIcon = getAppIcon(app)
            val packageName = app.packageName

            // Use the GitHub-powered classifier
            val impactLevel = classifier.determineImpactLevel(packageName, appName)

            Log.d("ServiceDiscovery", "Processing: $packageName - $appName - $impactLevel")

            // If impactLevel is null (system app), return null to skip this app
            if (impactLevel == null) {
                return null
            }

            // Get additional category information if available
            val categoryInfo = getCategoryInfoForApp(packageName, appName, impactLevel)

            Service(
                id = packageName.hashCode().toString(),
                name = appName,
                packageName = packageName,
                icon = appIcon,
                impactLevel = impactLevel,
                categoryInfo = categoryInfo,
                installDate = Date(),
                lastUsed = Date()
            )
        } catch (e: Exception) {
            Log.e("ServiceDiscovery", "Failed to create service for ${app.packageName}", e)
            null
        }
    }

    /**
     * Get human-readable app name
     */
    private fun getAppName(app: ApplicationInfo): String {
        return try {
            packageManager.getApplicationLabel(app).toString()
        } catch (e: Exception) {
            app.packageName
        }
    }

    /**
     * Get app icon
     */
    private fun getAppIcon(app: ApplicationInfo) = try {
        app.loadIcon(packageManager)
    } catch (e: Exception) {
        packageManager.defaultActivityIcon
    }

    /**
     * Get category information for an app if available
     */
    private fun getCategoryInfoForApp(
        packageName: String,
        appName: String,
        impactLevel: ImpactLevel
    ): CategoryInfo? {
        return try {
            // Try to find the specific category for this app
            val matchedCategory = findCategoryForApp(packageName, appName)
            matchedCategory?.let {
                val classifierInfo = classifier.getCategoryInfo(it)
                classifierInfo?.let { info ->
                    CategoryInfo(
                        impact = info.impact,
                        description = info.description,
                        source = info.source,
                        annualEstimate = info.annualEstimate?.let { estimate ->
                            AnnualEstimate(
                                wh = estimate.wh,
                                whComparison = estimate.whComparison,
                                co2 = estimate.co2,
                                co2Comparison = estimate.co2Comparison
                            )
                        }
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Find category name for an app
     */
    private fun findCategoryForApp(packageName: String, appName: String): String? {
        return try {
            // Get all available categories and check which one matches this app
            classifier.getAllCategories().find { categoryName ->
                val categoryInfo = classifier.getCategoryInfo(categoryName)
                categoryInfo?.let {
                    // Check if this app would be classified into this category
                    val testImpact = when (categoryInfo.impact.lowercase()) {
                        "high" -> ImpactLevel.HIGH
                        "medium" -> ImpactLevel.MEDIUM
                        "low" -> ImpactLevel.LOW
                        else -> ImpactLevel.LOW
                    }

                    // If the impact levels match, this might be the right category
                    testImpact == classifier.determineImpactLevel(packageName, appName)
                } ?: false
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Handle errors during service creation
     */
    private fun handleServiceCreationError(e: Exception, app: ApplicationInfo) {
        Log.e("ServiceDiscovery", "Error creating service for ${app.packageName}: ${e.message}")
        // In production, you might want to report this to crash analytics
    }

    /**
     * Get detailed information about a specific service
     */
    fun getServiceDetails(packageName: String): ServiceDetails? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = getAppName(appInfo)
            val impactLevel = classifier.determineImpactLevel(packageName, appName)

            if (impactLevel == null) return null

            ServiceDetails(
                packageName = packageName,
                appName = appName,
                impactLevel = impactLevel,
                installDate = getInstallDate(packageName),
                lastUpdateTime = getLastUpdateTime(packageName),
                dataUsage = getDataUsageEstimate(impactLevel),
                energyUsage = getEnergyUsageEstimate(impactLevel)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get app install date
     */
    private fun getInstallDate(packageName: String): Long {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.firstInstallTime
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Get app last update time
     */
    private fun getLastUpdateTime(packageName: String): Long {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.lastUpdateTime
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Estimate daily data usage based on impact level
     */
    private fun getDataUsageEstimate(impactLevel: ImpactLevel): Double {
        return when (impactLevel) {
            ImpactLevel.HIGH -> 500.0 // MB per day
            ImpactLevel.MEDIUM -> 200.0
            ImpactLevel.LOW -> 50.0
        }
    }

    /**
     * Estimate daily energy usage based on impact level
     */
    private fun getEnergyUsageEstimate(impactLevel: ImpactLevel): Double {
        return when (impactLevel) {
            ImpactLevel.HIGH -> 8.0 // Wh per day
            ImpactLevel.MEDIUM -> 3.2
            ImpactLevel.LOW -> 0.8
        }
    }

    /**
     * Check if GitHub data is loaded
     */
    fun isGitHubDataLoaded(): Boolean {
        return classifier.isDataLoaded()
    }

    /**
     * Get total count of discovered apps
     */
    fun getTotalAppsCount(): Int {
        return getInstalledApplications().size
    }

    /**
     * Get count of user apps (non-system)
     */
    fun getUserAppsCount(): Int {
        return filterUserApplications(getInstalledApplications()).size
    }
}

/**
 * Data class for detailed service information
 */
data class ServiceDetails(
    val packageName: String,
    val appName: String,
    val impactLevel: ImpactLevel,
    val installDate: Long,
    val lastUpdateTime: Long,
    val dataUsage: Double, // MB per day
    val energyUsage: Double // Wh per day
)