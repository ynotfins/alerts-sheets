package com.example.alertsheets

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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

            // Load apps (IO thread) - GET ALL APPS INCLUDING SYSTEM, USER, DISABLED, ETC
            val pm = packageManager
            val apps = withContext(Dispatchers.IO) {
                // ✅ CRITICAL FIX: Use multiple flags to get EVERY app on the device
                pm.getInstalledApplications(
                    android.content.pm.PackageManager.GET_META_DATA or
                    android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
                    android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
                ).sortedBy { 
                    try {
                        it.loadLabel(pm).toString().lowercase()
                    } catch (e: Exception) {
                        it.packageName.lowercase()
                    }
                }
            }
            
            allApps.clear()
            allApps.addAll(apps)
            
            // ✅ DEBUG: Log what we found
            Log.d("AppsListActivity", "📱 Loaded ${apps.size} total apps")
            val systemCount = apps.count { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            val userCount = apps.size - systemCount
            val updatedSystemCount = apps.count { (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            Log.d("AppsListActivity", "  - System apps: $systemCount")
            Log.d("AppsListActivity", "  - User apps: $userCount")
            Log.d("AppsListActivity", "  - Updated system apps: $updatedSystemCount")
            
            // ✅ DEBUG: Check for BNN specifically
            val bnnApps = apps.filter { it.packageName.contains("bnn", ignoreCase = true) }
            if (bnnApps.isNotEmpty()) {
                bnnApps.forEach { 
                    val name = try { pm.getApplicationLabel(it).toString() } catch (e: Exception) { it.packageName }
                    val isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdated = (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    Log.d("AppsListActivity", "  🔥 BNN FOUND: $name (${it.packageName}) - System=$isSystem, Updated=$isUpdated")
                }
            } else {
                Log.w("AppsListActivity", "  ⚠️ BNN NOT FOUND in installed apps!")
            }
            
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
        
        var skippedSystemApps = 0
        var skippedUserApps = 0
        var addedApps = 0
        
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
                if (treatAsUserApp) {
                    skippedUserApps++
                    continue
                }
            } else {
                // User wants user/installed apps - skip pure system apps
                if (!treatAsUserApp) {
                    skippedSystemApps++
                    continue
                }
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
            addedApps++
        }
        
        // ✅ DEBUG: Log filtering results
        Log.d("AppsListActivity", "🔍 Filter results (showSystemApps=$showSystemApps, search='$searchQuery'):")
        Log.d("AppsListActivity", "  - Added to list: $addedApps")
        Log.d("AppsListActivity", "  - Skipped system apps: $skippedSystemApps")
        Log.d("AppsListActivity", "  - Skipped user apps: $skippedUserApps")
        
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
