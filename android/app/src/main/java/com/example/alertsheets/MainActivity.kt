package com.example.alertsheets

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: EndpointsAdapter
    private var endpoints: MutableList<Endpoint> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI References
        val recycler = findViewById<RecyclerView>(R.id.recycler_endpoints)
        val btnAdd = findViewById<Button>(R.id.btn_add_endpoint)
        val btnPermissions = findViewById<Button>(R.id.btn_permissions)
        val btnAppFilter = findViewById<Button>(R.id.btn_app_filter)
        val statusText = findViewById<TextView>(R.id.status_text)
        
        // Migration: Check for legacy URL
        migrateLegacyUrl()

        // Load Data
        endpoints = PrefsManager.getEndpoints(this).toMutableList()
        
        // Adapter Setup
        adapter = EndpointsAdapter(endpoints, 
            onToggle = { endpoint, isEnabled ->
                endpoint.isEnabled = isEnabled
                saveEndpoints()
            },
            onDelete = { endpoint ->
                confirmDelete(endpoint)
            }
        )
        
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Listeners
        btnAdd.setOnClickListener { showAddDialog() }
        
        btnPermissions.setOnClickListener {
            showPermissionsDialog()
        }
        
        btnAppFilter.setOnClickListener {
            showAppFilterDialog()
        }
        
        updateStatus(statusText)
    }
    
    // ... migrateLegacyUrl ...

    private fun showAppFilterDialog() {
        // In a real app we would load installed packages. 
        // For this "autonomy level", we'll offer a text input or a few common presets + custom.
        // Or better: Let's query installed apps!
        
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        
        val appNames = mutableListOf<String>()
        val packageNames = mutableListOf<String>()
        
        apps.sortedBy { it.loadLabel(pm).toString() }.forEach {
            appNames.add(it.loadLabel(pm).toString())
            packageNames.add(it.activityInfo.packageName)
        }
        
        val currentTargets = PrefsManager.getTargetApps(this)
        val selectedIndices = mutableListOf<Int>()
        val checkedItems = BooleanArray(packageNames.size)
        
        packageNames.forEachIndexed { index, pkg ->
            if (currentTargets.contains(pkg)) {
                checkedItems[index] = true
                selectedIndices.add(index)
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Select Apps to Listen To")
            .setMultiChoiceItems(appNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedIndices.add(which)
                } else {
                    selectedIndices.remove(which)
                }
            }
            .setPositiveButton("Save") { _, _ ->
                val newTargets = selectedIndices.map { packageNames[it] }.toSet()
                PrefsManager.saveTargetApps(this, newTargets)
                Toast.makeText(this, "Saved ${newTargets.size} apps", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun migrateLegacyUrl() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val legacyUrl = prefs.getString("script_url", null)
        
        if (!legacyUrl.isNullOrEmpty()) {
            val alreadyExplored = PrefsManager.getEndpoints(this)
            if (alreadyExplored.isEmpty()) {
                val newEndpoint = Endpoint(name = "Legacy Sheet", url = legacyUrl)
                PrefsManager.saveEndpoints(this, listOf(newEndpoint))
                // Clear legacy to avoid re-migration
                prefs.edit().remove("script_url").apply()
            }
        }
    }
    
    private fun saveEndpoints() {
        PrefsManager.saveEndpoints(this, endpoints)
        adapter.updateData(endpoints)
    }
    
    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_endpoint, null)
        val inputName = view.findViewById<EditText>(R.id.input_name)
        val inputUrl = view.findViewById<EditText>(R.id.input_url)
        
        AlertDialog.Builder(this)
            .setTitle("Add Endpoint")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val name = inputName.text.toString().trim()
                val url = inputUrl.text.toString().trim()
                if (url.isNotEmpty()) { // Url is mandatory
                    val finalName = if (name.isEmpty()) "Endpoint ${endpoints.size + 1}" else name
                    endpoints.add(Endpoint(name = finalName, url = url))
                    saveEndpoints()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun confirmDelete(endpoint: Endpoint) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${endpoint.name}?")
            .setMessage("Are you sure you want to remove this endpoint?")
            .setPositiveButton("Delete") { _, _ ->
                endpoints.remove(endpoint)
                saveEndpoints()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPermissionsDialog() {
        val options = arrayOf("Notification Access", "Battery Optimization", "Accessibility Service")
        AlertDialog.Builder(this)
            .setTitle("Manage Permissions")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    1 -> {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    2 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.status_text)
        updateStatus(statusText)
    }
    
    private fun updateStatus(textView: TextView) {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val notificationEnabled = listeners != null && listeners.contains(packageName)
        
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryIgnored = pm.isIgnoringBatteryOptimizations(packageName)
        
        val status = StringBuilder()
        if (notificationEnabled) status.append("✓ Notifications Active\n") else status.append("✗ Notifications Disabled\n")
        if (batteryIgnored) status.append("✓ Background Unrestricted\n") else status.append("✗ Background Restricted\n")
        
        textView.text = status.toString().trim()
    }
}
