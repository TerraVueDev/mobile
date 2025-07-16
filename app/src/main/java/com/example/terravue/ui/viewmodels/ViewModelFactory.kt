package com.example.terravue.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.terravue.data.repositories.ServiceRepository
import com.example.terravue.data.local.AppDatabase
import com.example.terravue.data.remote.GitHubDataSource
import com.example.terravue.utils.ImpactLevelClassifier
import com.example.terravue.services.AIService

/**
 * Enhanced Factory for creating ViewModels with AI dependencies
 *
 * This factory handles dependency injection for ViewModels,
 * ensuring proper repository instances are provided including AI service.
 * In a larger app, consider using Dagger/Hilt for DI.
 */
class ViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    // Create repository with all dependencies including AI
    private val serviceRepository by lazy {
        ServiceRepository( // Use the simplified version
            context = context,
            serviceDao = AppDatabase.getDatabase(context).serviceDao(),
            gitHubDataSource = GitHubDataSource(),
            impactClassifier = ImpactLevelClassifier(context),
            aiService = AIService(context)
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(context, serviceRepository) as T
            }
            modelClass.isAssignableFrom(ServiceDetailsViewModel::class.java) -> {
                ServiceDetailsViewModel(serviceRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}