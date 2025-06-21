package com.example.terravue.ui.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.terravue.R
import com.example.terravue.ui.adapters.ServiceAdapter
import com.example.terravue.ui.viewmodels.MainViewModel
import com.example.terravue.ui.viewmodels.ViewModelFactory
import com.example.terravue.data.repositories.ServiceRepository
import com.example.terravue.data.local.AppDatabase
import com.example.terravue.data.remote.GitHubDataSource
import com.example.terravue.utils.ImpactLevelClassifier
import com.example.terravue.ui.components.ImpactCountsManager
import com.example.terravue.ui.dialogs.ServiceDetailsDialogFragment
import kotlinx.coroutines.launch

/**
 * MainActivity - Entry point for TerraVue environmental impact tracker
 *
 * This activity displays user's installed apps categorized by their environmental impact:
 * - High Impact: Social media, entertainment apps with high energy consumption
 * - Medium Impact: Streaming, gaming apps with moderate energy usage
 * - Low Impact: Productivity, utility apps with minimal energy footprint
 */
class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var servicesRecyclerView: RecyclerView
    private lateinit var servicesAdapter: ServiceAdapter
    private lateinit var searchEditText: EditText
    private lateinit var noResultsTextView: TextView
    private lateinit var impactCountsManager: ImpactCountsManager

    // ViewModel using modern Android Architecture
    private val viewModel: MainViewModel by viewModels {
        ViewModelFactory(
            context = this,
            serviceRepository = ServiceRepository(
                context = this,
                serviceDao = AppDatabase.getDatabase(this).serviceDao(),
                gitHubDataSource = GitHubDataSource(),
                impactClassifier = ImpactLevelClassifier(this)
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActivity()
        initializeComponents()
        observeViewModel()
//        loadInitialData()lifecycleScope.launch {
//            try {
//                val database = AppDatabase.getDatabase(this@MainActivity)
//                val count = database.serviceDao().getTotalServicesCount()
//                Toast.makeText(this@MainActivity, "Database ready! Services: $count", Toast.LENGTH_SHORT).show()
//            } catch (e: Exception) {
//                Toast.makeText(this@MainActivity, "Database error: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//        }
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

    private fun initializeComponents() {
        initializeViews()
        setupRecyclerView()
        setupSearchFunctionality()
        setupImpactCountsManager()
    }

    private fun initializeViews() {
        searchEditText = findViewById(R.id.searchEditText)
        servicesRecyclerView = findViewById(R.id.servicesRecyclerView)
        noResultsTextView = findViewById(R.id.noResultsTextView)
    }

    private fun setupRecyclerView() {
        servicesAdapter = ServiceAdapter { service ->
            // Handle service click - show detailed impact information
            viewModel.showServiceDetails(service)
        }

        servicesRecyclerView.apply {
            adapter = servicesAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)

            // Add recycling optimizations for better performance
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    private fun setupSearchFunctionality() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.searchServices(s.toString())
            }
        })
    }

    private fun setupImpactCountsManager() {
        impactCountsManager = ImpactCountsManager(
            findViewById(R.id.highImpactCountText),
            findViewById(R.id.mediumImpactCountText),
            findViewById(R.id.lowImpactCountText)
        )
    }

    private fun observeViewModel() {
        // Observe filtered services for RecyclerView updates
        lifecycleScope.launch {
            viewModel.filteredServices.collect { services ->
                servicesAdapter.updateServices(services)
                impactCountsManager.updateCounts(services)
                updateEmptyState(services.isEmpty() && viewModel.searchQuery.value.isNotBlank())
            }
        }

        // Observe loading state
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                // Show/hide loading indicator
                // You can add a progress bar to the layout if needed
            }
        }

        // Observe error states
        lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                error?.let {
                    showErrorMessage(it)
                    viewModel.clearError()
                }
            }
        }

        // Observe data loading status
        lifecycleScope.launch {
            viewModel.dataLoadingStatus.collect { status ->
                status?.let {
                    showDataLoadingStatus(it)
                }
            }
        }

        // Observe service details dialog
        lifecycleScope.launch {
            viewModel.showServiceDetailsDialog.collect { service ->
                service?.let {
                    showServiceDetailsDialog(it)
                    viewModel.clearServiceDetailsDialog()
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModel.loadServices()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        noResultsTextView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        servicesRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showErrorMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showDataLoadingStatus(status: String) {
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
    }

    private fun showServiceDetailsDialog(service: com.example.terravue.domain.models.Service) {
        // Create and show a detailed dialog with environmental impact information
        val dialogFragment = ServiceDetailsDialogFragment.newInstance(service)
        dialogFragment.show(supportFragmentManager, "service_details")
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to app (in case user installed/uninstalled apps)
        viewModel.refreshServices()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ViewModel will handle cleanup automatically
    }
}