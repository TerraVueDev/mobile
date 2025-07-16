package com.example.terravue.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.terravue.domain.models.Service
import com.example.terravue.data.repositories.ServiceRepository
import com.example.terravue.data.repositories.AIServiceStats
import com.example.terravue.utils.NetworkUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * Enhanced MainViewModel with AI integration for personalized environmental insights
 */
class MainViewModel(
    private val context: Context,
    private val serviceRepository: ServiceRepository
) : ViewModel() {

    // Private mutable state
    private val _allServices = MutableStateFlow<List<Service>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _isAILoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _dataLoadingStatus = MutableStateFlow<String?>(null)
    private val _aiServiceStatus = MutableStateFlow<String?>(null)
    private val _showServiceDetailsDialog = MutableStateFlow<Service?>(null)

    // Public read-only state
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isAILoading: StateFlow<Boolean> = _isAILoading.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    val dataLoadingStatus: StateFlow<String?> = _dataLoadingStatus.asStateFlow()
    val aiServiceStatus: StateFlow<String?> = _aiServiceStatus.asStateFlow()
    val showServiceDetailsDialog: StateFlow<Service?> = _showServiceDetailsDialog.asStateFlow()

    // Derived state - automatically filters services based on search query
    val filteredServices: StateFlow<List<Service>> = combine(
        _allServices,
        _searchQuery
    ) { services, query ->
        if (query.isBlank()) {
            services
        } else {
            services.filter { service ->
                service.name.contains(query, ignoreCase = true) ||
                        service.packageName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Track current loading jobs
    private var loadingJob: Job? = null
    private var aiRefreshJob: Job? = null

    init {
        // Initialize with cached data if available
        loadCachedServices()
        // Check AI service status
        checkAIServiceStatus()
    }

    /**
     * Load services with AI-enhanced content
     */
    fun loadServicesWithAI() {
        if (loadingJob?.isActive == true) return

        loadingJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                // Check network connectivity
                val hasNetwork = NetworkUtils.isNetworkAvailable(context)

                if (hasNetwork) {
                    _dataLoadingStatus.value = "🌍 Loading latest environmental data..."
                    _isAILoading.value = true
                    _aiServiceStatus.value = "🤖 Preparing AI insights..."

                    // Try to load from GitHub with AI enhancement
                    val result = serviceRepository.loadServicesWithAI()

                    if (result.isSuccess) {
                        _allServices.value = result.getOrDefault(emptyList())
                        _dataLoadingStatus.value = "✅ Latest data with AI insights loaded"

                        // Update AI status based on content
                        updateAIStatusFromServices(result.getOrDefault(emptyList()))
                    } else {
                        // Fallback to cached/offline data
                        loadOfflineServices()
                        _dataLoadingStatus.value = "⚠️ Using offline data - network issue"
                        _aiServiceStatus.value = "📱 AI insights from cache"
                    }
                } else {
                    loadOfflineServices()
                    _dataLoadingStatus.value = "📱 Offline mode - using cached data"
                    _aiServiceStatus.value = "🔌 AI insights unavailable offline"
                }

            } catch (e: Exception) {
                handleError("Failed to load services: ${e.message}")
                loadOfflineServices() // Always try to show something
            } finally {
                _isLoading.value = false
                _isAILoading.value = false
                // Clear status messages after delay
                kotlinx.coroutines.delay(4000)
                _dataLoadingStatus.value = null
                kotlinx.coroutines.delay(2000)
                if (_aiServiceStatus.value?.contains("Preparing") == true) {
                    _aiServiceStatus.value = null
                }
            }
        }
    }

    /**
     * Load services using original method (without AI)
     */
    fun loadServices() {
        if (loadingJob?.isActive == true) return

        loadingJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val hasNetwork = NetworkUtils.isNetworkAvailable(context)

                if (hasNetwork) {
                    _dataLoadingStatus.value = "Loading environmental impact data..."

                    // Use original loading method from parent class implementation
                    loadOfflineServices() // Placeholder - implement original loadServices logic
                    _dataLoadingStatus.value = "✓ Data loaded successfully"
                } else {
                    loadOfflineServices()
                    _dataLoadingStatus.value = "📱 Offline mode - using cached data"
                }

            } catch (e: Exception) {
                handleError("Failed to load services: ${e.message}")
                loadOfflineServices()
            } finally {
                _isLoading.value = false
                kotlinx.coroutines.delay(3000)
                _dataLoadingStatus.value = null
            }
        }
    }

    /**
     * Refresh AI content for expired services
     */
    suspend fun refreshAIContent(): Int {
        return try {
            _isAILoading.value = true
            _aiServiceStatus.value = "🔄 Refreshing AI insights..."

            val refreshedCount = serviceRepository.refreshExpiredAIContent()

            // Reload services to get updated AI content
            val updatedServices = serviceRepository.getCachedServices()
            _allServices.value = updatedServices

            updateAIStatusFromServices(updatedServices)

            refreshedCount
        } catch (e: Exception) {
            _errorMessage.value = "Failed to refresh AI content: ${e.message}"
            0
        } finally {
            _isAILoading.value = false
        }
    }

    /**
     * Get AI service statistics
     */
    suspend fun getAIServiceStats(): AIServiceStats {
        return serviceRepository.getAIServiceStats()
    }

    /**
     * Get count of services with expired AI content
     */
    suspend fun getExpiredAIContentCount(): Int {
        val services = _allServices.value
        return services.count { it.needsAIRefresh() }
    }

    /**
     * Clear AI cache
     */
    fun clearAICache() {
        viewModelScope.launch {
            try {
                serviceRepository.clearAICache()
                _aiServiceStatus.value = "🗑️ AI cache cleared"

                kotlinx.coroutines.delay(2000)
                _aiServiceStatus.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to clear AI cache: ${e.message}"
            }
        }
    }

    /**
     * Refresh services data - forces reload
     */
    fun refreshServices() {
        loadingJob?.cancel()
        loadServicesWithAI() // Use AI-enhanced loading by default
    }

    /**
     * Update search query and trigger filtering
     */
    fun searchServices(query: String) {
        _searchQuery.value = query.trim()
    }

    /**
     * Show detailed information about a service
     */
    fun showServiceDetails(service: Service) {
        _showServiceDetailsDialog.value = service
    }

    /**
     * Clear the service details dialog
     */
    fun clearServiceDetailsDialog() {
        _showServiceDetailsDialog.value = null
    }

    /**
     * Clear current error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Check AI service availability
     */
    private fun checkAIServiceStatus() {
        viewModelScope.launch {
            try {
                val stats = serviceRepository.getAIServiceStats()
                if (stats.isAIAvailable) {
                    _aiServiceStatus.value = "🤖 AI insights available"
                } else {
                    _aiServiceStatus.value = "🤖 AI service unavailable"
                }
            } catch (e: Exception) {
                _aiServiceStatus.value = "🤖 AI status unknown"
            }
        }
    }

    /**
     * Update AI status based on loaded services
     */
    private fun updateAIStatusFromServices(services: List<Service>) {
        val withAI = services.count { it.hasAIContent }
        val total = services.size
        val percentage = if (total > 0) (withAI.toFloat() / total) * 100 else 0f

        _aiServiceStatus.value = when {
            total == 0 -> "🤖 No apps to analyze"
            withAI == 0 -> "🤖 AI insights loading..."
            percentage < 30 -> "🤖 AI insights: ${String.format("%.0f", percentage)}% ready"
            percentage < 80 -> "✨ AI insights: ${String.format("%.0f", percentage)}% complete"
            else -> "✨ AI insights ready (${String.format("%.0f", percentage)}%)"
        }
    }

    /**
     * Load cached services from local database
     */
    private fun loadCachedServices() {
        viewModelScope.launch {
            try {
                val cachedServices = serviceRepository.getCachedServices()
                if (cachedServices.isNotEmpty()) {
                    _allServices.value = cachedServices
                    updateAIStatusFromServices(cachedServices)
                }
            } catch (e: Exception) {
                // Silent fail for cache loading - not critical
            }
        }
    }

    /**
     * Load services using offline/fallback data
     */
    private suspend fun loadOfflineServices() {
        try {
            val offlineServices = serviceRepository.loadServicesOffline()
            _allServices.value = offlineServices
            updateAIStatusFromServices(offlineServices)
        } catch (e: Exception) {
            handleError("Failed to load offline data: ${e.message}")
        }
    }

    /**
     * Handle errors with user-friendly messages
     */
    private fun handleError(message: String) {
        _errorMessage.value = message
        println("TerraVue Error: $message")
    }

    /**
     * Get environmental impact summary for analytics
     */
    fun getEnvironmentalImpactSummary(): EnvironmentalSummary {
        val services = _allServices.value

        return EnvironmentalSummary(
            totalApps = services.size,
            highImpactCount = services.count { it.impactLevel == com.example.terravue.domain.models.ImpactLevel.HIGH },
            mediumImpactCount = services.count { it.impactLevel == com.example.terravue.domain.models.ImpactLevel.MEDIUM },
            lowImpactCount = services.count { it.impactLevel == com.example.terravue.domain.models.ImpactLevel.LOW },
            estimatedDailyCO2 = calculateDailyCO2Estimate(services),
            suggestionsCount = generateEcoSuggestions(services).size,
            // AI-specific metrics
            servicesWithAI = services.count { it.hasAIContent },
            aiCoveragePercentage = if (services.isNotEmpty()) {
                (services.count { it.hasAIContent }.toFloat() / services.size) * 100
            } else 0f
        )
    }

    /**
     * Calculate estimated daily CO2 emissions from app usage
     */
    private fun calculateDailyCO2Estimate(services: List<Service>): Double {
        return services.sumOf { service ->
            when (service.impactLevel) {
                com.example.terravue.domain.models.ImpactLevel.HIGH -> 2.5 // grams CO2 per day
                com.example.terravue.domain.models.ImpactLevel.MEDIUM -> 1.0
                com.example.terravue.domain.models.ImpactLevel.LOW -> 0.2
            }
        }
    }

    /**
     * Generate personalized eco-friendly suggestions
     */
    private fun generateEcoSuggestions(services: List<Service>): List<String> {
        val suggestions = mutableListOf<String>()
        val highImpactApps = services.filter { it.impactLevel == com.example.terravue.domain.models.ImpactLevel.HIGH }

        if (highImpactApps.size > 5) {
            suggestions.add("Consider reducing usage of ${highImpactApps.size} high-impact apps")
        }

        if (services.any { it.name.contains("streaming", ignoreCase = true) }) {
            suggestions.add("Try downloading content for offline viewing to reduce energy consumption")
        }

        // Add AI-specific suggestions if available
        val aiSuggestions = services.flatMap { it.getEcoSuggestions() }.distinct()
        suggestions.addAll(aiSuggestions.take(3))

        return suggestions
    }

    override fun onCleared() {
        super.onCleared()
        loadingJob?.cancel()
        aiRefreshJob?.cancel()
    }
}

/**
 * Enhanced data class for environmental impact summary with AI metrics
 */
data class EnvironmentalSummary(
    val totalApps: Int,
    val highImpactCount: Int,
    val mediumImpactCount: Int,
    val lowImpactCount: Int,
    val estimatedDailyCO2: Double,
    val suggestionsCount: Int,
    // AI-specific fields
    val servicesWithAI: Int = 0,
    val aiCoveragePercentage: Float = 0f
)