package com.example.terravue

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.terravue.models.Service

class ServiceAdapter(private var services: List<Service>): RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder>() {
    class ServiceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val serviceIcon: ImageView = view.findViewById(R.id.serviceIcon)
        val serviceName: TextView = view.findViewById(R.id.serviceName)
        val impactBadge: TextView = view.findViewById(R.id.impactBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_service, parent, false)
        return ServiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = services[position]

        holder.serviceName.text = service.name
        holder.serviceIcon.setImageDrawable(service.icon)
        holder.impactBadge.text = service.impactLevel.displayName

        val badgeColor = ContextCompat.getColor(holder.itemView.context, service.impactLevel.bgColorResId)
        val textColor = ContextCompat.getColor(holder.itemView.context, service.impactLevel.textColorResId)
        holder.impactBadge.background.setTint(badgeColor)
        holder.impactBadge.setTextColor(textColor)
    }

    override fun getItemCount(): Int {
        return services.size
    }

    fun updateServices(newServices: List<Service>) {
        services = newServices
        notifyDataSetChanged()
    }
}