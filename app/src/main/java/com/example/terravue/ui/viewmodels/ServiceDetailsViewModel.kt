package com.example.terravue.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.terravue.domain.models.Service
import com.example.terravue.domain.models.DailyImpact
import com.example.terravue.data.repositories.ServiceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ServiceDetailsViewModel - Manages detailed service information and eco-goals
 *
 * Responsibilities:
 * - Display detailed environmental impact information
 * - Handle eco-goal setting and tracking
 * - Manage service-specific recommendations
 * - Track usage statistics and improvements
 */
class ServiceDetailsViewModel(
    private val serviceRepository: ServiceRepository
) : ViewModel() {

    // Private mutable state
    private val _selectedService = MutableStateFlow<Service?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _showGoalDialog = MutableStateFlow(false)
    private val _goalSaved = MutableStateFlow(false)

    // Public read-only state
    val selectedService: StateFlow<Service?> = _selectedService.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    val showGoalDialog: StateFlow<Boolean> = _showGoalDialog.asStateFlow()
    val goalSaved: StateFlow<Boolean> = _goalSaved.asStateFlow()

    // Derived state for detailed calculations
    val dailyImpact: StateFlow<DailyImpact?> = _selectedService.map { service ->
        service?.getDailyImpactEstimate()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val ecoSuggestions: StateFlow<List<String>> = _selectedService.map { service ->
        service?.getEcoSuggestions() ?: emptyList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val environmentalComparisons: StateFlow<List<String>> = dailyImpact.map { impact ->
        impact?.getComparisons() ?: emptyList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Set the service to display details for
     */
    fun setService(service: Service) {
        _selectedService.value = service
        _errorMessage.value = null
    }

    /**
     * Update service usage statistics
     */
    fun updateUsageStats(usageMinutes: Int) {
        val service = _selectedService.value ?: return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                serviceRepository.updateServiceUsage(service.packageName, usageMinutes)

                // Optionally reload service details to show updated stats
                // This would require implementing a method to fetch single service details

            } catch (e: Exception) {
                _errorMessage.value = "Failed to update usage statistics: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Show goal setting dialog
     */
    fun showGoalSettingDialog() {
        _showGoalDialog.value = true
    }

    /**
     * Hide goal setting dialog
     */
    fun hideGoalSettingDialog() {
        _showGoalDialog.value = false
    }

    /**
     * Save an eco-goal for this service
     */
    fun saveEcoGoal(goalType: EcoGoalType, targetValue: Double) {
        val service = _selectedService.value ?: return

        viewModelScope.launch {
            try {
                _isLoading.value = true

                // In a real implementation, you would save this to a database
                // For now, we'll just simulate saving
                kotlinx.coroutines.delay(1000) // Simulate network/database call

                _goalSaved.value = true
                _showGoalDialog.value = false

                // Reset goal saved state after showing confirmation
                kotlinx.coroutines.delay(2000)
                _goalSaved.value = false

            } catch (e: Exception) {
                _errorMessage.value = "Failed to save eco-goal: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Get annual impact projections
     */
    fun getAnnualProjections(): AnnualImpactProjection? {
        val daily = dailyImpact.value ?: return null

        return AnnualImpactProjection(
            annualCO2Kg = (daily.co2Grams * 365) / 1000,
            annualEnergyKwh = (daily.energyWh * 365) / 1000,
            equivalentKmDriving = daily.co2Grams * 365 * 0.004,
            equivalentTreesNeeded = ((daily.co2Grams * 365) / 22000).toInt()
        )
    }

    /**
     * Generate sharing text for environmental impact
     */
    fun generateSharingText(): String {
        val service = _selectedService.value ?: return ""
        val daily = dailyImpact.value ?: return ""
        val annual = getAnnualProjections() ?: return ""

        return """
            🌱 My ${service.name} environmental impact:
            
            📱 Daily: ${String.format("%.1f", daily.co2Grams)}g CO₂, ${String.format("%.1f", daily.energyWh)}Wh
            📅 Annual: ${String.format("%.1f", annual.annualCO2Kg)}kg CO₂, ${String.format("%.1f", annual.annualEnergyKwh)}kWh
            🚗 Equivalent to driving ${String.format("%.1f", annual.equivalentKmDriving)}km per year
            🌳 Need ${annual.equivalentTreesNeeded} trees to offset this impact
            
            Track your digital carbon footprint with TerraVue! 🌍 #DigitalSustainability #EcoTech
        """.trimIndent()
    }

    /**
     * Get improvement suggestions based on current usage
     */
    fun getImprovementSuggestions(): List<ImprovementSuggestion> {
        val service = _selectedService.value ?: return emptyList()
        val suggestions = mutableListOf<ImprovementSuggestion>()

        when (service.impactLevel) {
            com.example.terravue.domain.models.ImpactLevel.HIGH -> {
                suggestions.add(
                    ImprovementSuggestion(
                        title = "Reduce Daily Usage",
                        description = "Limit usage to 1 hour per day",
                        potentialSaving = "Save up to 1.5g CO₂ daily",
                        difficultyLevel = DifficultyLevel.MEDIUM
                    )
                )
                suggestions.add(
                    ImprovementSuggestion(
                        title = "Use Dark Mode",
                        description = "Enable dark mode to reduce screen energy consumption",
                        potentialSaving = "Save up to 0.5g CO₂ daily",
                        difficultyLevel = DifficultyLevel.EASY
                    )
                )
            }
            com.example.terravue.domain.models.ImpactLevel.MEDIUM -> {
                suggestions.add(
                    ImprovementSuggestion(
                        title = "Optimize Settings",
                        description = "Reduce video quality and disable auto-play",
                        potentialSaving = "Save up to 0.3g CO₂ daily",
                        difficultyLevel = DifficultyLevel.EASY
                    )
                )
            }
            com.example.terravue.domain.models.ImpactLevel.LOW -> {
                suggestions.add(
                    ImprovementSuggestion(
                        title = "Great Choice!",
                        description = "This app already has minimal environmental impact",
                        potentialSaving = "Keep up the good work!",
                        difficultyLevel = DifficultyLevel.EASY
                    )
                )
            }
        }

        return suggestions
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources if needed
    }
}

/**
 * Eco-goal types for service-specific goals
 */
enum class EcoGoalType(val displayName: String) {
    REDUCE_USAGE("Reduce Daily Usage"),
    LIMIT_CO2("Limit CO₂ Emissions"),
    ENERGY_SAVING("Save Energy"),
    MINDFUL_USAGE("Mindful Usage")
}

/**
 * Difficulty levels for improvement suggestions
 */
enum class DifficultyLevel(val displayName: String, val color: String) {
    EASY("Easy", "#4CAF50"),
    MEDIUM("Medium", "#FF9800"),
    HARD("Hard", "#F44336")
}

/**
 * Data class for annual impact projections
 */
data class AnnualImpactProjection(
    val annualCO2Kg: Double,
    val annualEnergyKwh: Double,
    val equivalentKmDriving: Double,
    val equivalentTreesNeeded: Int
)

/**
 * Data class for improvement suggestions
 */
data class ImprovementSuggestion(
    val title: String,
    val description: String,
    val potentialSaving: String,
    val difficultyLevel: DifficultyLevel
)