package com.example.terravue.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.terravue.data.repositories.ServiceRepository

/**
 * Factory for creating ViewModels with dependencies
 *
 * This factory handles dependency injection for ViewModels,
 * ensuring proper repository instances are provided.
 * In a larger app, consider using Dagger/Hilt for DI.
 */
class ViewModelFactory(
    private val context: Context,
    private val serviceRepository: ServiceRepository
) : ViewModelProvider.Factory {

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