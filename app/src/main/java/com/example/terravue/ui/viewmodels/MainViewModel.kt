package com.example.terravue.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.terravue.domain.models.Service
import com.example.terravue.data.repositories.ServiceRepository
import com.example.terravue.utils.NetworkUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job

/**
 * MainViewModel - Manages UI state and business logic for the main screen
 *
 * Responsibilities:
 * - Load and cache installed services data
 * - Handle search functionality with real-time filtering
 * - Manage environmental impact calculations
 * - Coordinate between repository and UI
 * - Handle error states and loading indicators
 */
class MainViewModel(
    private val context: Context,
    private val serviceRepository: ServiceRepository
) : ViewModel() {

    // Private mutable state
    private val _allServices = MutableStateFlow<List<Service>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _dataLoadingStatus = MutableStateFlow<String?>(null)
    private val _showServiceDetailsDialog = MutableStateFlow<Service?>(null)

    // Public read-only state
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    val dataLoadingStatus: StateFlow<String?> = _dataLoadingStatus.asStateFlow()
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

    // Track current loading job to avoid multiple simultaneous loads
    private var loadingJob: Job? = null

    init {
        // Initialize with cached data if available
        loadCachedServices()
    }

    /**
     * Load services from repository - tries GitHub data first, falls back to offline
     */
    fun loadServices() {
        if (loadingJob?.isActive == true) return

        loadingJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                // Check network connectivity
                val hasNetwork = NetworkUtils.isNetworkAvailable(context)

                if (hasNetwork) {
                    _dataLoadingStatus.value = "Loading latest environmental impact data..."

                    // Try to load from GitHub first
                    val result = serviceRepository.loadServicesFromGitHub()

                    if (result.isSuccess) {
                        _allServices.value = result.getOrDefault(emptyList())
                        _dataLoadingStatus.value = "✓ Latest data loaded successfully"
                    } else {
                        // Fallback to cached/offline data
                        loadOfflineServices()
                        _dataLoadingStatus.value = "⚠ Using offline data - network issue"
                    }
                } else {
                    loadOfflineServices()
                    _dataLoadingStatus.value = "📱 Offline mode - using cached data"
                }

            } catch (e: Exception) {
                handleError("Failed to load services: ${e.message}")
                loadOfflineServices() // Always try to show something
            } finally {
                _isLoading.value = false
                // Clear status message after delay
                kotlinx.coroutines.delay(3000)
                _dataLoadingStatus.value = null
            }
        }
    }

    /**
     * Refresh services data - forces reload from network
     */
    fun refreshServices() {
        loadingJob?.cancel()
        loadServices()
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
     * Load cached services from local database
     */
    private fun loadCachedServices() {
        viewModelScope.launch {
            try {
                val cachedServices = serviceRepository.getCachedServices()
                if (cachedServices.isNotEmpty()) {
                    _allServices.value = cachedServices
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
        } catch (e: Exception) {
            handleError("Failed to load offline data: ${e.message}")
        }
    }

    /**
     * Handle errors with user-friendly messages
     */
    private fun handleError(message: String) {
        _errorMessage.value = message

        // Log error for debugging (in production, use proper logging)
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
            suggestionsCount = generateEcoSuggestions(services).size
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

        return suggestions
    }

    override fun onCleared() {
        super.onCleared()
        loadingJob?.cancel()
    }
}

/**
 * Data class for environmental impact summary
 */
data class EnvironmentalSummary(
    val totalApps: Int,
    val highImpactCount: Int,
    val mediumImpactCount: Int,
    val lowImpactCount: Int,
    val estimatedDailyCO2: Double,
    val suggestionsCount: Int
)