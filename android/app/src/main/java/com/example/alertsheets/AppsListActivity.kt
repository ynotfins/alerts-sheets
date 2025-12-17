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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsListActivity : AppCompatActivity() {

    private lateinit var adapter: AppsAdapter
    private var allApps = mutableListOf<ApplicationInfo>()
    private var filteredApps = mutableListOf<ApplicationInfo>()
    private var selectedApps = mutableSetOf<String>()
    private var showSystemApps = false
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps_list)

        selectedApps = PrefsManager.getTargetApps(this).toMutableSet()

        val recycler = findViewById<RecyclerView>(R.id.recycler_all_apps)
        recycler.layoutManager = LinearLayoutManager(this)

        val progressBar = findViewById<ProgressBar>(R.id.progress_loading)
        val searchBox = findViewById<EditText>(R.id.edit_search)
        val systemCheckbox = findViewById<CheckBox>(R.id.check_show_system)

        adapter =
                AppsAdapter(filteredApps, selectedApps) { pkg, isSelected ->
                    if (isSelected) selectedApps.add(pkg) else selectedApps.remove(pkg)
                    PrefsManager.saveTargetApps(this, selectedApps)
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

        // Load apps
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val apps =
                    pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                            .sortedBy { it.loadLabel(pm).toString().lowercase() }

            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(apps)
                filterApps()
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun filterApps() {
        val pm = packageManager
        filteredApps.clear()
        
        for (app in allApps) {
            // Filter system apps if toggle is off
            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!showSystemApps && isSystemApp) continue
            
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
}
