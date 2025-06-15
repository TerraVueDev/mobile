package com.example.terravue.services

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.example.terravue.models.ImpactLevel
import com.example.terravue.models.Service
import com.example.terravue.utils.ImpactLevelClassifier

class ServiceDiscoveryManager(
    private val context: Context,
    private val impactClassifier: ImpactLevelClassifier? = null
) {

    private val packageManager = context.packageManager
    // Use passed classifier or create fallback
    private val classifier = impactClassifier ?: ImpactLevelClassifier(context)

    fun getInstalledServices(): List<Service> {
        val installedApps = getInstalledApplications()
        val userApps = filterUserApplications(installedApps)
        val services = convertToServices(userApps)
        return services.sortedBy { it.name }
    }

    private fun getInstalledApplications(): List<ApplicationInfo> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    }

    private fun filterUserApplications(apps: List<ApplicationInfo>): List<ApplicationInfo> {
        return apps.filter { isUserApplication(it) }
    }

    private fun isUserApplication(app: ApplicationInfo): Boolean {
        return (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

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

    private fun createService(app: ApplicationInfo): Service? {
        val appName = packageManager.getApplicationLabel(app).toString()
        val appIcon = app.loadIcon(packageManager)

        // Use the GitHub-powered classifier
        val impactLevel = classifier.determineImpactLevel(app.packageName, appName)


        Log.d("SystemAppFilter", "Checking: ${app.packageName} - $appName - $impactLevel")
        // If impactLevel is null (system app), return null to skip this app
        if (impactLevel == null) {
            return null
        }

        // Get additional category information if available
        val categoryInfo = getCategoryInfoForApp(app.packageName, appName, impactLevel)

        return Service(appName, appIcon, impactLevel, categoryInfo)
    }

    private fun getCategoryInfoForApp(
        packageName: String,
        appName: String,
        impactLevel: ImpactLevel
    ): ImpactLevelClassifier.CategoryInfo? {
        return try {
            // Try to find the specific category for this app
            val matchedCategory = findCategoryForApp(packageName, appName)
            matchedCategory?.let { classifier.getCategoryInfo(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun findCategoryForApp(packageName: String, appName: String): String? {
        // Get all available categories and check which one matches this app
        return classifier.getAllCategories().find { categoryName ->
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
    }

    private fun handleServiceCreationError(e: Exception, app: ApplicationInfo) {
        e.printStackTrace()
        // Add proper logging here
    }

    // Helper method to check if GitHub data is loaded
    fun isGitHubDataLoaded(): Boolean {
        return classifier.isDataLoaded()
    }

    // Helper method to get detailed impact information
    fun getDetailedImpactInfo(service: Service): String? {
        return service.categoryInfo?.let { info ->
            buildString {
                appendLine("Description: ${info.description}")
                info.annualEstimate?.let { estimate ->
                    appendLine("Annual Energy: ${estimate.wh}")
                    appendLine("Annual CO2: ${estimate.co2}")
                }
                info.source?.let { source ->
                    appendLine("Source: $source")
                }
            }
        }
    }
}