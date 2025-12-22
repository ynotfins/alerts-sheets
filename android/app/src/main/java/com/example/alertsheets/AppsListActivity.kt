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

            // Load apps (IO thread) - Android 11+ compatible enumeration
            val pm = packageManager
            val apps = withContext(Dispatchers.IO) {
                // Method 1: getInstalledApplications (direct ApplicationInfo)
                val allApplications = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledApplications(
                            android.content.pm.PackageManager.ApplicationInfoFlags.of(
                                (PackageManager.GET_META_DATA.toLong())
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    }
                } catch (e: Exception) {
                    Log.e("AppsList", "Failed to getInstalledApplications: ${e.message}")
                    emptyList()
                }
                
                // Method 2: Query launchable activities (backup for restricted visibility)
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val launchableApps = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.queryIntentActivities(
                            launcherIntent,
                            android.content.pm.PackageManager.ResolveInfoFlags.of(
                                PackageManager.MATCH_ALL.toLong()
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                    }
                }.mapNotNull { resolveInfo ->
                    try {
                        resolveInfo.activityInfo?.applicationInfo
                    } catch (e: Exception) {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("AppsList", "Failed to query launcher activities: ${e.message}")
                    emptyList()
                }
                
                // Merge both lists, deduplicate by packageName
                val merged = (allApplications + launchableApps)
                    .distinctBy { it.packageName }
                    .sortedBy { 
                        try {
                            it.loadLabel(pm).toString().lowercase()
                        } catch (e: Exception) {
                            it.packageName.lowercase()
                        }
                    }
                
                // Log comprehensive diagnostics
                Log.v("AppsList", "=== Android 11+ App Discovery ===")
                Log.v("AppsList", "getInstalledApplications: ${allApplications.size} apps")
                Log.v("AppsList", "queryLauncherActivities: ${launchableApps.size} apps")
                Log.v("AppsList", "Merged unique: ${merged.size} apps")
                
                // Check for BNN specifically
                val BNN_PACKAGE = "us.bnn.newsapp"
                val bnnDirect = allApplications.any { it.packageName == BNN_PACKAGE }
                val bnnLauncher = launchableApps.any { it.packageName == BNN_PACKAGE }
                val bnnMerged = merged.any { it.packageName == BNN_PACKAGE }
                Log.v("AppsList", "BNN ($BNN_PACKAGE):")
                Log.v("AppsList", "  - In getInstalledApplications: $bnnDirect")
                Log.v("AppsList", "  - In launcherActivities: $bnnLauncher")
                Log.v("AppsList", "  - In merged list: $bnnMerged")
                
                if (!bnnMerged) {
                    // Fallback: search for any app containing "bnn" or "news"
                    val bnnFuzzy = merged.filter { 
                        it.packageName.contains("bnn", ignoreCase = true) ||
                        it.packageName.contains("news", ignoreCase = true)
                    }
                    Log.v("AppsList", "  - Fuzzy search (bnn/news): ${bnnFuzzy.size} matches")
                    bnnFuzzy.forEach {
                        Log.v("AppsList", "    -> ${it.packageName}")
                    }
                }
                
                merged
            }
            
            allApps.clear()
            allApps.addAll(apps)
            
            // Log final counts
            val systemCount = apps.count { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            val userCount = apps.count { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            Log.v("AppsList", "Final list: ${apps.size} apps (System: $systemCount, User: $userCount)")
            
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
        
        // Log filtering results
        Log.v("AppsList", "Filter applied (showSystem=$showSystemApps, search='$searchQuery'): $addedApps apps shown (skipped: $skippedSystemApps system, $skippedUserApps user)")
        
        // Check if BNN was filtered out
        val BNN_PACKAGE = "us.bnn.newsapp"
        if (allApps.any { it.packageName == BNN_PACKAGE } && !filteredApps.any { it.packageName == BNN_PACKAGE }) {
            val bnnApp = allApps.first { it.packageName == BNN_PACKAGE }
            val isSystem = (bnnApp.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdated = (bnnApp.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            Log.w("AppsList", "⚠️ BNN filtered out! isSystem=$isSystem, isUpdated=$isUpdated, showSystemApps=$showSystemApps")
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
