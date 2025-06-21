package com.example.terravue.ui.adapters

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.terravue.R
import com.example.terravue.domain.models.Service
import com.example.terravue.domain.models.ImpactLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced ServiceAdapter with proper icon loading
 */
class ServiceAdapter(
    private val onServiceClick: (Service) -> Unit
) : ListAdapter<Service, ServiceAdapter.ServiceViewHolder>(ServiceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service, parent, false)
        return ServiceViewHolder(view, onServiceClick)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun updateServices(newServices: List<Service>) {
        submitList(newServices)
    }

    class ServiceViewHolder(
        itemView: View,
        private val onServiceClick: (Service) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val serviceIcon: ImageView = itemView.findViewById(R.id.serviceIcon)
        private val serviceName: TextView = itemView.findViewById(R.id.serviceName)
        private val impactBadge: TextView = itemView.findViewById(R.id.impactBadge)
        private val packageManager = itemView.context.packageManager

        private var currentService: Service? = null

        init {
            itemView.setOnClickListener {
                currentService?.let { service ->
                    onServiceClick(service)
                }
            }
        }

        fun bind(service: Service) {
            currentService = service

            // Set basic info immediately
            serviceName.text = service.name
            updateImpactBadge(service.impactLevel)

            // Load icon asynchronously
            loadAppIcon(service.packageName)

            // Update accessibility
            updateAccessibility(service)
        }

        private fun loadAppIcon(packageName: String) {
            // Set default icon immediately
            serviceIcon.setImageResource(R.drawable.ic_eco_leaf)

            // Load actual icon in background
            CoroutineScope(Dispatchers.IO).launch {
                val icon = try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    appInfo.loadIcon(packageManager)
                } catch (e: Exception) {
                    null
                }

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    icon?.let { serviceIcon.setImageDrawable(it) }
                }
            }
        }

        private fun updateImpactBadge(impactLevel: ImpactLevel) {
            impactBadge.text = impactLevel.displayName

            val context = itemView.context
            val badgeColor = ContextCompat.getColor(context, impactLevel.bgColorResId)
            val textColor = ContextCompat.getColor(context, impactLevel.textColorResId)

            impactBadge.background?.setTint(badgeColor)
            impactBadge.setTextColor(textColor)
        }

        private fun updateAccessibility(service: Service) {
            val impactDescription = when (service.impactLevel) {
                ImpactLevel.HIGH -> "high environmental impact"
                ImpactLevel.MEDIUM -> "medium environmental impact"
                ImpactLevel.LOW -> "low environmental impact"
            }

            itemView.contentDescription = "${service.name}, $impactDescription. Tap for details."
        }
    }

    private class ServiceDiffCallback : DiffUtil.ItemCallback<Service>() {
        override fun areItemsTheSame(oldItem: Service, newItem: Service): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Service, newItem: Service): Boolean {
            return oldItem.name == newItem.name &&
                    oldItem.impactLevel == newItem.impactLevel &&
                    oldItem.packageName == newItem.packageName
        }
    }
}

