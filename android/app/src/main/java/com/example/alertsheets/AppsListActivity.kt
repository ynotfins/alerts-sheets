package com.example.alertsheets

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AppsListActivity - V2 Source-based app selection
 * 
 * NOW USES SOURCEMANAGER (not PrefsManager)
 */
class AppsListActivity : AppCompatActivity() {

    private lateinit var adapter: AppsAdapter
    private lateinit var sourceManager: SourceManager
    private var allApps = mutableListOf<ApplicationInfo>()
    private var filteredApps = mutableListOf<ApplicationInfo>()
    private var selectedApps = mutableSetOf<String>()
    private var showSystemApps = false
    private var searchQuery = ""
    
    // ✅ FIX: Managed coroutine scope with lifecycle
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps_list)
        
        // ✅ V2: Use SourceManager
        sourceManager = SourceManager(applicationContext)
        
        val recycler = findViewById<RecyclerView>(R.id.recycler_all_apps)
        recycler.layoutManager = LinearLayoutManager(this)

        val progressBar = findViewById<ProgressBar>(R.id.progress_loading)
        val searchBox = findViewById<EditText>(R.id.edit_search)
        val systemCheckbox = findViewById<CheckBox>(R.id.check_show_system)

        // ✅ FIX: Load everything async on IO thread
        scope.launch {
            // Load selected apps from SourceManager (IO thread)
            val appSources = withContext(Dispatchers.IO) {
                sourceManager.getSourcesByType(SourceType.APP)
            }
            selectedApps = appSources.map { it.id }.toMutableSet()

            adapter =
                    AppsAdapter(filteredApps, selectedApps) { pkg, isSelected ->
                        // ✅ FIX: Save on IO thread
                        scope.launch(Dispatchers.IO) {
                            if (isSelected) {
                                addAppSource(pkg)
                                withContext(Dispatchers.Main) {
                                    selectedApps.add(pkg)
                                }
                            } else {
                                sourceManager.deleteSource(pkg)
                                withContext(Dispatchers.Main) {
                                    selectedApps.remove(pkg)
                                }
                            }
                        }
                    }
            recycler.adapter = adapter

            // Search functionality
            searchBox.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.lowercase() ?: ""
                    filterApps()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            // System apps toggle
            systemCheckbox.setOnCheckedChangeListener { _, isChecked ->
                showSystemApps = isChecked
                filterApps()
            }

            // Load apps (IO thread)
            val pm = packageManager
            val apps = withContext(Dispatchers.IO) {
                pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                        .sortedBy { it.loadLabel(pm).toString().lowercase() }
            }
            
            allApps.clear()
            allApps.addAll(apps)
            filterApps()
            progressBar.visibility = View.GONE
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // ✅ FIX: Cancel all coroutines to prevent memory leaks
        scope.cancel()
    }

    private fun filterApps() {
        val pm = packageManager
        filteredApps.clear()
        
        for (app in allApps) {
            // Filter system apps based on toggle
            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            
            // ✅ FIX: Treat updated system apps (like BNN installed via APK) as user apps
            val treatAsUserApp = isUpdatedSystemApp || !isSystemApp
            
            // When "System Apps" checkbox is checked: show ONLY system apps
            // When unchecked: show user apps (including updated system apps)
            if (showSystemApps) {
                // User wants system apps - skip user apps
                if (treatAsUserApp) continue
            } else {
                // User wants user/installed apps - skip pure system apps
                if (!treatAsUserApp) continue
            }
            
            // Filter by search query
            if (searchQuery.isNotEmpty()) {
                val appName = try {
                    app.loadLabel(pm).toString().lowercase()
                } catch (e: Exception) {
                    app.packageName.lowercase()
                }
                val packageName = app.packageName.lowercase()
                
                if (!appName.contains(searchQuery) && !packageName.contains(searchQuery)) {
                    continue
                }
            }
            
            filteredApps.add(app)
        }
        
        adapter.notifyDataSetChanged()
    }
    
    /**
     * ✅ V2: Add app as Source with intelligent defaults
     */
    private fun addAppSource(packageName: String) {
        val pm = packageManager
        
        // Get app info
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
        
        val appName = appInfo?.let { 
            pm.getApplicationLabel(it).toString() 
        } ?: packageName
        
        // Detect if BNN
        val isBnn = packageName.contains("bnn", ignoreCase = true)
        
        // Create Source with smart defaults
        val source = Source(
            id = packageName,
            type = SourceType.APP,
            name = appName,
            enabled = true,
            
            // BNN gets special treatment
            autoClean = !isBnn,  // BNN doesn't need emoji cleaning
            templateId = if (isBnn) "rock-solid-bnn-format" else "rock-solid-app-default",
            parserId = if (isBnn) "bnn" else "generic",
            endpointId = "default-endpoint",
            
            iconColor = if (isBnn) 0xFFA855F7.toInt() else 0xFF4A9EFF.toInt(), // Purple or Blue
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        sourceManager.saveSource(source)
    }
}
