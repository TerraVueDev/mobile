package com.example.terravue.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.terravue.R
import com.example.terravue.domain.models.Service
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServiceDetailsDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_SERVICE_ID = "service_id"
        private const val ARG_SERVICE_NAME = "service_name"
        private const val ARG_SERVICE_PACKAGE = "service_package"
        private const val ARG_SERVICE_IMPACT = "service_impact"

        fun newInstance(service: Service): ServiceDetailsDialogFragment {
            return ServiceDetailsDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVICE_ID, service.id)
                    putString(ARG_SERVICE_NAME, service.name)
                    putString(ARG_SERVICE_PACKAGE, service.packageName)
                    putString(ARG_SERVICE_IMPACT, service.impactLevel.name)
                }
            }
        }
    }

    private lateinit var service: Service

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_TerraVue_Dialog)

        arguments?.let { args ->
            val serviceId = args.getString(ARG_SERVICE_ID) ?: return
            val serviceName = args.getString(ARG_SERVICE_NAME) ?: return
            val servicePackage = args.getString(ARG_SERVICE_PACKAGE) ?: return
            val impactLevel = when (args.getString(ARG_SERVICE_IMPACT)) {
                "HIGH" -> com.example.terravue.domain.models.ImpactLevel.HIGH
                "MEDIUM" -> com.example.terravue.domain.models.ImpactLevel.MEDIUM
                else -> com.example.terravue.domain.models.ImpactLevel.LOW
            }

            service = Service(
                id = serviceId,
                name = serviceName,
                packageName = servicePackage,
                impactLevel = impactLevel
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_service_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        setupHeader(view)
        setupImpactHeader(view)
        setupEnvironmentalFactors(view)
        setupImpactDetails(view)
        setupEcoSuggestions(view)
        setupActionButtons(view)
    }

    private fun setupHeader(view: View) {
        val serviceIcon = view.findViewById<ImageView>(R.id.dialogServiceIcon)
        val serviceName = view.findViewById<TextView>(R.id.dialogServiceName)
        val packageName = view.findViewById<TextView>(R.id.dialogPackageName)

        serviceName.text = service.name
        packageName?.text = service.packageName

        // Load app icon
        loadDialogIcon(serviceIcon, service.packageName)
    }

    private fun setupImpactHeader(view: View) {
        val impactHeaderSection = view.findViewById<LinearLayout>(R.id.impactHeaderSection)
        val impactLabel = view.findViewById<TextView>(R.id.dialogImpactLabel)

        // Set impact text and color like browser extension
        impactLabel.text = "${service.impactLevel.displayName.lowercase()}"

        // Update background color based on impact level
        val backgroundColor = ContextCompat.getColor(requireContext(), when(service.impactLevel) {
            com.example.terravue.domain.models.ImpactLevel.HIGH -> R.color.red_high
            com.example.terravue.domain.models.ImpactLevel.MEDIUM -> R.color.orange_medium
            com.example.terravue.domain.models.ImpactLevel.LOW -> R.color.primary_green
        })
        impactHeaderSection.setBackgroundColor(backgroundColor)
    }

    private fun setupEnvironmentalFactors(view: View) {
        val carbonFootprintDesc = view.findViewById<TextView>(R.id.carbonFootprintDesc)
        val powerConsumptionDesc = view.findViewById<TextView>(R.id.powerConsumptionDesc)
        val impactExplanationTitle = view.findViewById<TextView>(R.id.impactExplanationTitle)
        val impactExplanationDesc = view.findViewById<TextView>(R.id.impactExplanationDesc)

        val dailyImpact = service.getDailyImpactEstimate()
        val annualCO2 = dailyImpact.co2Grams * 365
        val annualEnergy = dailyImpact.energyWh * 365

        // Carbon Footprint description
        val co2Description = "${service.name}'s annual CO2 emissions are estimated to be equivalent to ${String.format("%.1f", annualCO2/1000)}kg annually."
        carbonFootprintDesc?.text = co2Description

        // Power Consumption description
        val energyDescription = "${service.name}'s annual power consumption is roughly equivalent to ${String.format("%.1f", annualEnergy/1000)}kWh annually."
        powerConsumptionDesc?.text = energyDescription

        // Impact Explanation
        impactExplanationTitle?.text = "Why ${service.name} has a ${service.impactLevel.displayName.lowercase()} impact?"
        val explanation = "${service.name}'s ${service.impactLevel.displayName.lowercase()} environmental impact comes from ${getDefaultExplanation(service.impactLevel)}."
        impactExplanationDesc?.text = explanation
    }

    private fun getDefaultExplanation(impactLevel: com.example.terravue.domain.models.ImpactLevel): String {
        return when (impactLevel) {
            com.example.terravue.domain.models.ImpactLevel.HIGH -> "constant data streaming, server processing, and high energy consumption"
            com.example.terravue.domain.models.ImpactLevel.MEDIUM -> "moderate data usage and server communication"
            com.example.terravue.domain.models.ImpactLevel.LOW -> "minimal data usage and efficient processing"
        }
    }

    private fun setupImpactDetails(view: View) {
        val dailyImpact = service.getDailyImpactEstimate()

        // Update compact daily/annual values
        view.findViewById<TextView>(R.id.dailyCO2Value)?.text =
            String.format("%.1fg", dailyImpact.co2Grams)
        view.findViewById<TextView>(R.id.dailyEnergyValue)?.text =
            String.format("%.1fWh", dailyImpact.energyWh)

        val annualCO2 = dailyImpact.co2Grams * 365 / 1000
        val annualEnergy = dailyImpact.energyWh * 365 / 1000

        view.findViewById<TextView>(R.id.annualCO2Value)?.text =
            String.format("%.1fkg", annualCO2)
        view.findViewById<TextView>(R.id.annualEnergyValue)?.text =
            String.format("%.1fkWh", annualEnergy)
    }

    private fun setupEcoSuggestions(view: View) {
        val suggestionsRecyclerView = view.findViewById<RecyclerView>(R.id.suggestionsRecyclerView)
        if (suggestionsRecyclerView != null) {
            val suggestions = getEnhancedSuggestions()

            suggestionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            suggestionsRecyclerView.adapter = SuggestionsAdapter(suggestions)
        }
    }

    private fun setupActionButtons(view: View) {
        view.findViewById<MaterialButton>(R.id.closeButton)?.setOnClickListener {
            dismiss()
        }
    }

    private fun loadDialogIcon(imageView: ImageView, packageName: String) {
        // Set default icon immediately
        imageView.setImageResource(R.drawable.ic_eco_leaf)

        // Load actual icon in background
        CoroutineScope(Dispatchers.IO).launch {
            val icon = try {
                val packageManager = requireContext().packageManager
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appInfo.loadIcon(packageManager)
            } catch (e: Exception) {
                null
            }

            // Update UI on main thread
            withContext(Dispatchers.Main) {
                icon?.let { imageView.setImageDrawable(it) }
            }
        }
    }

    private fun getEnhancedSuggestions(): List<SuggestionItem> {
        val suggestions = mutableListOf<SuggestionItem>()

        // Use fallback suggestions based on impact level
        when (service.impactLevel) {
            com.example.terravue.domain.models.ImpactLevel.HIGH -> {
                suggestions.add(
                    SuggestionItem(
                        title = "Reduce Usage Time",
                        description = "Limit daily usage to 1-2 hours to significantly reduce carbon footprint",
                        icon = R.drawable.ic_timer
                    )
                )
                suggestions.add(
                    SuggestionItem(
                        title = "Enable Dark Mode",
                        description = "Dark themes use less battery and reduce screen energy consumption",
                        icon = R.drawable.ic_dark_mode
                    )
                )
                suggestions.add(
                    SuggestionItem(
                        title = "Download Content Offline",
                        description = "Pre-download videos, music, or content to reduce streaming energy",
                        icon = R.drawable.ic_download
                    )
                )
            }
            com.example.terravue.domain.models.ImpactLevel.MEDIUM -> {
                suggestions.add(
                    SuggestionItem(
                        title = "Optimize Settings",
                        description = "Reduce video quality, disable auto-play, limit notifications",
                        icon = R.drawable.ic_swap
                    )
                )
                suggestions.add(
                    SuggestionItem(
                        title = "Use Wi-Fi When Possible",
                        description = "Wi-Fi typically uses less energy than mobile data",
                        icon = R.drawable.ic_eco_tip
                    )
                )
            }
            com.example.terravue.domain.models.ImpactLevel.LOW -> {
                suggestions.add(
                    SuggestionItem(
                        title = "Excellent Choice!",
                        description = "This app has minimal environmental impact. Keep using it!",
                        icon = R.drawable.ic_eco_leaf
                    )
                )
                suggestions.add(
                    SuggestionItem(
                        title = "Consider Similar Apps",
                        description = "Look for productivity and utility apps that minimize energy usage",
                        icon = R.drawable.ic_eco_tip
                    )
                )
            }
        }

        return suggestions
    }

    // Data class for suggestions
    data class SuggestionItem(
        val title: String,
        val description: String,
        val icon: Int
    )

    /**
     * Simple adapter for eco-friendly suggestions
     */
    private class SuggestionsAdapter(
        private val suggestions: List<SuggestionItem>
    ) : RecyclerView.Adapter<SuggestionsAdapter.SuggestionViewHolder>() {

        class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val suggestionTitle: TextView = view.findViewById(R.id.suggestionTitle)
            val suggestionText: TextView = view.findViewById(R.id.suggestionText)
            val suggestionIcon: ImageView = view.findViewById(R.id.suggestionIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_suggestion, parent, false)
            return SuggestionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
            val item = suggestions[position]
            holder.suggestionTitle.text = item.title
            holder.suggestionText.text = item.description
            holder.suggestionIcon.setImageResource(item.icon)
        }

        override fun getItemCount() = suggestions.size
    }
}