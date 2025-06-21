package com.example.terravue.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        setupImpactDetails(view)
        setupEnvironmentalComparisons(view)
        setupEcoSuggestions(view)
        setupCarbonFootprintBreakdown(view)
        setupActionButtons(view)
    }

    private fun setupHeader(view: View) {
        val serviceIcon = view.findViewById<ImageView>(R.id.dialogServiceIcon)
        val serviceName = view.findViewById<TextView>(R.id.dialogServiceName)
        val packageName = view.findViewById<TextView>(R.id.dialogPackageName)
        val impactBadge = view.findViewById<TextView>(R.id.dialogImpactBadge)

        serviceName.text = service.name
        packageName?.text = service.packageName

        // Load app icon
        loadDialogIcon(serviceIcon, service.packageName)

        // Style impact badge
        impactBadge.text = service.impactLevel.displayName
        val badgeColor = ContextCompat.getColor(requireContext(), service.impactLevel.bgColorResId)
        val textColor = ContextCompat.getColor(requireContext(), service.impactLevel.textColorResId)
        impactBadge.background?.setTint(badgeColor)
        impactBadge.setTextColor(textColor)
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

    // ... rest of the dialog methods remain the same as in the previous implementation
    private fun setupImpactDetails(view: View) {
        val dailyImpact = service.getDailyImpactEstimate()

        view.findViewById<TextView>(R.id.dailyCO2Value)?.text =
            String.format("%.1f g", dailyImpact.co2Grams)
        view.findViewById<TextView>(R.id.dailyEnergyValue)?.text =
            String.format("%.1f Wh", dailyImpact.energyWh)

        val annualCO2 = dailyImpact.co2Grams * 365 / 1000
        val annualEnergy = dailyImpact.energyWh * 365 / 1000

        view.findViewById<TextView>(R.id.annualCO2Value)?.text =
            String.format("%.1f kg", annualCO2)
        view.findViewById<TextView>(R.id.annualEnergyValue)?.text =
            String.format("%.1f kWh", annualEnergy)

        view.findViewById<TextView>(R.id.impactDescription)?.text = service.impactLevel.description
    }

    private fun setupEnvironmentalComparisons(view: View) {
        val comparisonsRecyclerView = view.findViewById<RecyclerView>(R.id.comparisonsRecyclerView)
        if (comparisonsRecyclerView != null) {
            val dailyImpact = service.getDailyImpactEstimate()
            val comparisons = getEnhancedComparisons(dailyImpact)

            comparisonsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            comparisonsRecyclerView.adapter = EnhancedComparisonsAdapter(comparisons)
        }
    }

    private fun setupEcoSuggestions(view: View) {
        val suggestionsRecyclerView = view.findViewById<RecyclerView>(R.id.suggestionsRecyclerView)
        if (suggestionsRecyclerView != null) {
            val suggestions = getEnhancedSuggestions()

            suggestionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            suggestionsRecyclerView.adapter = EnhancedSuggestionsAdapter(suggestions)
        }
    }

    private fun setupCarbonFootprintBreakdown(view: View) {
        val footprintBreakdown = view.findViewById<TextView>(R.id.carbonFootprintBreakdown)
        if (footprintBreakdown != null) {
            val dailyImpact = service.getDailyImpactEstimate()
            val breakdown = generateCarbonFootprintBreakdown(dailyImpact)
            footprintBreakdown.text = breakdown
        }
    }

    private fun setupActionButtons(view: View) {
        view.findViewById<MaterialButton>(R.id.closeButton)?.setOnClickListener {
            dismiss()
        }
    }

    private fun getEnhancedComparisons(dailyImpact: com.example.terravue.domain.models.DailyImpact): List<ComparisonItem> {
        val comparisons = mutableListOf<ComparisonItem>()
        val co2 = dailyImpact.co2Grams
        val energy = dailyImpact.energyWh

        // CO2 Comparisons
        when {
            co2 > 5 -> comparisons.add(
                ComparisonItem(
                    "🚗 Like driving ${String.format("%.1f", co2 * 0.004)} km in a car",
                    R.drawable.ic_car,
                    "Transportation"
                )
            )
            co2 > 1 -> comparisons.add(
                ComparisonItem(
                    "📱 Like charging your phone ${String.format("%.0f", co2 * 0.8)} times",
                    R.drawable.ic_phone,
                    "Electronics"
                )
            )
            else -> comparisons.add(
                ComparisonItem(
                    "🌱 Very low impact - less than a deep breath",
                    R.drawable.ic_eco_leaf,
                    "Minimal"
                )
            )
        }

        // Energy Comparisons
        when {
            energy > 10 -> comparisons.add(
                ComparisonItem(
                    "💡 Like running an LED bulb for ${String.format("%.0f", energy * 100)} minutes",
                    R.drawable.ic_lightbulb,
                    "Lighting"
                )
            )
            energy > 5 -> comparisons.add(
                ComparisonItem(
                    "🔋 Like half a smartphone battery cycle",
                    R.drawable.ic_phone,
                    "Battery"
                )
            )
            else -> comparisons.add(
                ComparisonItem(
                    "⚡ Minimal energy usage - very efficient!",
                    R.drawable.ic_eco_leaf,
                    "Efficient"
                )
            )
        }

        // Annual perspective
        val annualCO2 = (co2 * 365) / 1000
        if (annualCO2 > 1) {
            val treesNeeded = (annualCO2 / 22).toInt().coerceAtLeast(1)
            comparisons.add(
                ComparisonItem(
                    "🌳 Plant $treesNeeded tree${if (treesNeeded > 1) "s" else ""} to offset annual impact",
                    R.drawable.ic_eco_leaf,
                    "Offset"
                )
            )
        }

        return comparisons
    }

    private fun getEnhancedSuggestions(): List<SuggestionItem> {
        val suggestions = mutableListOf<SuggestionItem>()

        when (service.impactLevel) {
            com.example.terravue.domain.models.ImpactLevel.HIGH -> {
                suggestions.add(
                    SuggestionItem(
                        "Reduce daily usage time",
                        "Limit to 1-2 hours per day to significantly reduce carbon footprint",
                        R.drawable.ic_timer,
                        "High Impact",
                        "🔴"
                    )
                )
                suggestions.add(
                    SuggestionItem(
                        "Enable dark mode",
                        "Dark themes use less battery and reduce screen energy consumption",
                        R.drawable.ic_dark_mode,
                        "Easy Win",
                        "🟢"
                    )
                )
                suggestions.add(
                    SuggestionItem(
                        "Download content offline",
                        "Pre-download videos, music, or content to reduce streaming energy",
                        R.drawable.ic_download,
                        "Medium Impact",
                        "🟡"
                    )
                )
            }
            com.example.terravue.domain.models.ImpactLevel.MEDIUM -> {
                suggestions.add(
                    SuggestionItem(
                        "Optimize app settings",
                        "Reduce video quality, disable auto-play, limit notifications",
                        R.drawable.ic_swap,
                        "Medium Impact",
                        "🟡"
                    )
                )
                suggestions.add(
                    SuggestionItem(
                        "Use Wi-Fi when possible",
                        "Wi-Fi typically uses less energy than mobile data",
                        R.drawable.ic_eco_tip,
                        "Easy Win",
                        "🟢"
                    )
                )
            }
            com.example.terravue.domain.models.ImpactLevel.LOW -> {
                suggestions.add(
                    SuggestionItem(
                        "Excellent choice!",
                        "This app has minimal environmental impact. Keep using it!",
                        R.drawable.ic_eco_leaf,
                        "Great Job",
                        "✅"
                    )
                )
                suggestions.add(
                    SuggestionItem(
                        "Consider similar eco-friendly apps",
                        "Look for productivity and utility apps that minimize energy usage",
                        R.drawable.ic_eco_tip,
                        "Recommendation",
                        "💡"
                    )
                )
            }
        }

        return suggestions
    }

    private fun generateCarbonFootprintBreakdown(dailyImpact: com.example.terravue.domain.models.DailyImpact): String {
        val co2 = dailyImpact.co2Grams
        val energy = dailyImpact.energyWh

        return buildString {
            appendLine("📊 Carbon Footprint Breakdown:")
            appendLine()
            appendLine("• Device processing: ${String.format("%.1f", co2 * 0.4)}g CO₂")
            appendLine("• Network transmission: ${String.format("%.1f", co2 * 0.3)}g CO₂")
            appendLine("• Server infrastructure: ${String.format("%.1f", co2 * 0.3)}g CO₂")
            appendLine()
            appendLine("⚡ Energy breakdown:")
            appendLine("• Screen usage: ${String.format("%.1f", energy * 0.6)}Wh")
            appendLine("• Data processing: ${String.format("%.1f", energy * 0.4)}Wh")
        }
    }

    // Enhanced data classes
    data class ComparisonItem(
        val text: String,
        val iconRes: Int,
        val category: String
    )

    data class SuggestionItem(
        val title: String,
        val description: String,
        val iconRes: Int,
        val impact: String,
        val emoji: String
    )

    /**
     * Enhanced adapter for environmental comparisons
     */
    private class EnhancedComparisonsAdapter(
        private val comparisons: List<ComparisonItem>
    ) : RecyclerView.Adapter<EnhancedComparisonsAdapter.ComparisonViewHolder>() {

        class ComparisonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val comparisonText: TextView = view.findViewById(R.id.comparisonText)
            val comparisonIcon: ImageView = view.findViewById(R.id.comparisonIcon)
            val comparisonCategory: TextView? = view.findViewById(R.id.comparisonCategory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComparisonViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_comparison, parent, false)
            return ComparisonViewHolder(view)
        }

        override fun onBindViewHolder(holder: ComparisonViewHolder, position: Int) {
            val item = comparisons[position]
            holder.comparisonText.text = item.text
            holder.comparisonIcon.setImageResource(item.iconRes)
            holder.comparisonCategory?.text = item.category
        }

        override fun getItemCount() = comparisons.size
    }

    /**
     * Enhanced adapter for eco-friendly suggestions
     */
    private class EnhancedSuggestionsAdapter(
        private val suggestions: List<SuggestionItem>
    ) : RecyclerView.Adapter<EnhancedSuggestionsAdapter.SuggestionViewHolder>() {

        class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val suggestionTitle: TextView = view.findViewById(R.id.suggestionTitle)
            val suggestionText: TextView = view.findViewById(R.id.suggestionText)
            val suggestionIcon: ImageView = view.findViewById(R.id.suggestionIcon)
            val suggestionImpact: TextView? = view.findViewById(R.id.suggestionImpact)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_suggestion, parent, false)
            return SuggestionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
            val item = suggestions[position]
            holder.suggestionTitle.text = "${item.emoji} ${item.title}"
            holder.suggestionText.text = item.description
            holder.suggestionIcon.setImageResource(item.iconRes)
            holder.suggestionImpact?.text = item.impact
        }

        override fun getItemCount() = suggestions.size
    }

}
