package com.example.terravue

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.widget.EditText
import android.widget.TextView
import android.text.TextWatcher
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.terravue.models.ImpactLevel
import com.example.terravue.models.Service


class MainActivity : AppCompatActivity() {
    private lateinit var servicesRecyclerView: RecyclerView
    private lateinit var servicesAdapter: ServiceAdapter
    private lateinit var searchEditText: EditText


    private lateinit var highImpactCountText: TextView
    private lateinit var mediumImpactCountText: TextView
    private lateinit var lowImpactCountText: TextView


    // Store original and filtered lists
    private var allServices = listOf<Service>()
    private var filteredServices = listOf<Service>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initializeViews()
        setupRecyclerView()
    }

    private fun initializeViews() {
        highImpactCountText = findViewById(R.id.highImpactCountText)
        mediumImpactCountText = findViewById(R.id.mediumImpactCountText)
        lowImpactCountText = findViewById(R.id.lowImpactCountText)

        // Initialize search EditText
        searchEditText = findViewById(R.id.searchEditText)
        setupSearchFunctionality()
    }

    private fun setupSearchFunctionality() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                filterServices(s.toString())
            }
        })
    }

    private fun filterServices(query: String) {
        filteredServices = if (query.isBlank()) {
            allServices
        } else {
            allServices.filter { service ->
                service.name.contains(query, ignoreCase = true)
            }
        }

        // Update the adapter with filtered results
        servicesAdapter.updateServices(filteredServices)

        // Update impact counts based on filtered results
        updateImpactCounts(filteredServices)
    }

    private fun updateImpactCounts(services: List<Service>) {
        val highCount = services.count { it.impactLevel == ImpactLevel.HIGH }
        val mediumCount = services.count { it.impactLevel == ImpactLevel.MEDIUM }
        val lowCount = services.count { it.impactLevel == ImpactLevel.LOW }

        highImpactCountText.text = highCount.toString()
        mediumImpactCountText.text = mediumCount.toString()
        lowImpactCountText.text = lowCount.toString()
    }

    private fun setupRecyclerView() {
        servicesRecyclerView = findViewById(R.id.servicesRecyclerView)

        allServices = getInstalledServices()
        filteredServices = allServices

        // Update impact count displays
        updateImpactCounts(filteredServices)

        servicesAdapter = ServiceAdapter(filteredServices)
        servicesRecyclerView.adapter = servicesAdapter
        servicesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun getInstalledServices(): List<Service> {
        val packageManager = packageManager
        val services = mutableListOf<Service>()

        // Get all installed applications
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        // Filter for user-installed apps (non-system apps)
        val userApps = installedApps.filter { app ->
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }

        // Convert to Service objects
        userApps.forEach { app ->
            try {
                val appName = packageManager.getApplicationLabel(app).toString()
                val appIcon = app.loadIcon(packageManager)
                val impactLevel = determineImpactLevel(app.packageName, appName)

                services.add(Service(appName, appIcon, impactLevel))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return services.sortedBy { it.name }
    }

    private fun determineImpactLevel(packageName: String, appName: String): ImpactLevel {
        // Define impact levels based on app categories or package names
        return when {
            // Social media apps
            packageName.contains("instagram", true) ||
                    packageName.contains("facebook", true) ||
                    packageName.contains("twitter", true) ||
                    packageName.contains("tiktok", true) ||
                    packageName.contains("snapchat", true) -> ImpactLevel.HIGH

            // Streaming services
            packageName.contains("netflix", true) ||
                    packageName.contains("youtube", true) ||
                    packageName.contains("spotify", true) ||
                    packageName.contains("disney", true) -> ImpactLevel.MEDIUM

            // Gaming apps
            packageName.contains("game", true) ||
                    appName.contains("game", true) -> ImpactLevel.MEDIUM

            // Productivity apps
            packageName.contains("office", true) ||
                    packageName.contains("docs", true) ||
                    packageName.contains("drive", true) ||
                    packageName.contains("calendar", true) -> ImpactLevel.LOW

            // Default for unknown apps
            else -> ImpactLevel.LOW
        }
    }

}