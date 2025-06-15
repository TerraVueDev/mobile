// ========================================================================================
// MainActivity.kt - Focus only on UI coordination and lifecycle
// ========================================================================================

package com.example.terravue

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.terravue.models.Service
import com.example.terravue.services.ServiceDiscoveryManager
import com.example.terravue.ui.ImpactCountsManager
import com.example.terravue.utils.ImpactLevelClassifier
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var servicesRecyclerView: RecyclerView
    private lateinit var servicesAdapter: ServiceAdapter
    private lateinit var searchEditText: EditText

    // Managers
    private lateinit var serviceDiscoveryManager: ServiceDiscoveryManager
    private lateinit var impactCountsManager: ImpactCountsManager
    private lateinit var impactClassifier: ImpactLevelClassifier

    // Data
    private var allServices = listOf<Service>()
    private var filteredServices = listOf<Service>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActivity()
        initializeClassifierAndLoadData()
    }

    private fun setupActivity() {
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        setupWindowInsets()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initializeClassifierAndLoadData() {
        impactClassifier = ImpactLevelClassifier(this)

        // Load GitHub data asynchronously
        lifecycleScope.launch {
            val success = impactClassifier.loadDataFromGitHub()

            // Initialize managers after classifier is ready (whether success or failure)
            initializeManagers()
            initializeComponents()

            // Show status to user
            val message = if (success) {
                "Impact data loaded from GitHub"
            } else {
                "Using offline impact data"
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeManagers() {
        // Pass the classifier to ServiceDiscoveryManager
        serviceDiscoveryManager = ServiceDiscoveryManager(this, impactClassifier)
        impactCountsManager = ImpactCountsManager(
            findViewById(R.id.highImpactCountText),
            findViewById(R.id.mediumImpactCountText),
            findViewById(R.id.lowImpactCountText)
        )
    }

    private fun initializeComponents() {
        initializeViews()
        setupRecyclerView()
        setupSearchFunctionality()
    }

    private fun initializeViews() {
        searchEditText = findViewById(R.id.searchEditText)
        servicesRecyclerView = findViewById(R.id.servicesRecyclerView)
    }

    private fun setupRecyclerView() {
        loadServices()
        initializeAdapter()
        updateUI()
    }

    private fun loadServices() {
        allServices = serviceDiscoveryManager.getInstalledServices()
        filteredServices = allServices
    }

    private fun initializeAdapter() {
        servicesAdapter = ServiceAdapter(filteredServices)
        servicesRecyclerView.apply {
            adapter = servicesAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
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
        updateFilteredResults()
    }

    private fun updateFilteredResults() {
        servicesAdapter.updateServices(filteredServices)
        impactCountsManager.updateCounts(filteredServices)
    }

    private fun updateUI() {
        impactCountsManager.updateCounts(filteredServices)
    }
}