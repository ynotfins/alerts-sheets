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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: EndpointsAdapter
    private lateinit var appsAdapter: AppsAdapter
    private var endpoints: MutableList<Endpoint> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI References
        val recycler = findViewById<RecyclerView>(R.id.recycler_endpoints)
        val recyclerApps = findViewById<RecyclerView>(R.id.recycler_apps)
        val btnAdd = findViewById<Button>(R.id.btn_add_endpoint)
        val btnPermissions = findViewById<Button>(R.id.btn_permissions)
        val btnAppFilter = findViewById<Button>(R.id.btn_app_filter)
        val statusText = findViewById<TextView>(R.id.status_text)
        
        // Migration: Check for legacy URL
        migrateLegacyUrl()

        // Load Data
        endpoints = PrefsManager.getEndpoints(this).toMutableList()
        
        // Adapter Setup
        // Adapter Setup
        adapter = EndpointsAdapter(endpoints, 
            onToggle = { endpoint, isEnabled ->
                endpoint.isEnabled = isEnabled
                saveEndpoints()
            },
            onDelete = { endpoint ->
                // This is now the EDIT action via the pencil button
                showEditDialog(endpoint)
            }
        )
        
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        
        // Apps Adapter Setup
        val targetApps = PrefsManager.getTargetApps(this).toList()
        appsAdapter = AppsAdapter(targetApps) { pkg ->
            try {
                val intent = Intent(this, AppConfigActivity::class.java)
                intent.putExtra("package_name", pkg)
                // Try to get app name
                val pm = packageManager
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
                intent.putExtra("app_name", appName)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening config: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
        recyclerApps.layoutManager = LinearLayoutManager(this)
        recyclerApps.adapter = appsAdapter

        // Listeners
        btnAdd.setOnClickListener { showAddDialog() }
        
        btnPermissions.setOnClickListener {
            showPermissionsDialog()
        }
        
        btnAppFilter.setOnClickListener {
            showAppFilterDialog()
        }
        
        findViewById<Button>(R.id.btn_verify).setOnClickListener {
            runVerification()
        }
        
        // Initial Check
        runVerification()
    }
    
    // ... migrateLegacyUrl ...

    private fun showAppFilterDialog() {
        val progressBar = android.widget.ProgressBar(this)
        progressBar.isIndeterminate = true
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Loading Apps...")
            .setView(progressBar)
            .setCancelable(false)
            .show()

        Thread {
            val pm = packageManager
            // 1. Get ALL installed packages (task may be heavy)
            val allPackages = pm.getInstalledPackages(0)

            val appNames = mutableListOf<String>()
            val packageNames = mutableListOf<String>()

            allPackages.forEach { pkgInfo ->
                val isSystem = (pkgInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val intent = pm.getLaunchIntentForPackage(pkgInfo.packageName)
                val hasLauncher = intent != null

                if (!isSystem || hasLauncher) {
                    try {
                        appNames.add(pkgInfo.applicationInfo.loadLabel(pm).toString())
                        packageNames.add(pkgInfo.packageName)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Sort alphabetically
            val sortedIndices = appNames.mapIndexed { index, name -> index to name }
                .sortedBy { it.second.lowercase() }

            val sortedAppNames = sortedIndices.map { it.second }
            val sortedPackageNames = sortedIndices.map { packageNames[it.first] }

            val currentTargets = PrefsManager.getTargetApps(this@MainActivity)
            val selectedIndices = mutableListOf<Int>()
            val checkedItems = BooleanArray(sortedPackageNames.size)

            sortedPackageNames.forEachIndexed { index, pkg ->
                if (currentTargets.contains(pkg)) {
                    checkedItems[index] = true
                    selectedIndices.add(index)
                }
            }
            
            runOnUiThread {
                progressDialog.dismiss()
                
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Select Apps to Listen To")
                    .setMultiChoiceItems(sortedAppNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                        if (isChecked) {
                            selectedIndices.add(which)
                        } else {
                            selectedIndices.remove(which)
                        }
                    }
                    .setPositiveButton("Save") { _, _ ->
                        val newTargets = selectedIndices.map { sortedPackageNames[it] }.toSet()
                        PrefsManager.saveTargetApps(this@MainActivity, newTargets)
                        Toast.makeText(this@MainActivity, "Saved ${newTargets.size} apps", Toast.LENGTH_SHORT).show()
                        if (::appsAdapter.isInitialized) {
                            appsAdapter.updateData(newTargets.toList())
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
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

    private fun showEditDialog(endpoint: Endpoint) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_endpoint, null)
        val inputName = view.findViewById<EditText>(R.id.input_name)
        val inputUrl = view.findViewById<EditText>(R.id.input_url)
        
        inputName.setText(endpoint.name)
        inputUrl.setText(endpoint.url)
        
        AlertDialog.Builder(this)
            .setTitle("Edit Endpoint")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = inputName.text.toString().trim()
                val url = inputUrl.text.toString().trim()
                if (url.isNotEmpty()) {
                    endpoint.name = if (name.isEmpty()) "Endpoint" else name
                    endpoint.url = url
                    saveEndpoints()
                }
            }
            .setNeutralButton("Delete") { _, _ ->
                confirmDelete(endpoint)
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
        if (::appsAdapter.isInitialized) {
            appsAdapter.updateData(PrefsManager.getTargetApps(this).toList())
        }
    }
    
    private fun updateStatus(textView: TextView) {
        // Now handled by runVerification
    }

    private fun runVerification() {
        val statusLight = findViewById<android.view.View>(R.id.status_light)
        val statusText = findViewById<TextView>(R.id.status_text)
        val statusDetail = findViewById<TextView>(R.id.status_detail)
        val btnVerify = findViewById<Button>(R.id.btn_verify)
        
        btnVerify.isEnabled = false
        btnVerify.text = "Checking..."
        statusText.text = "Verifying..."
        statusLight.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.YELLOW) // Orange/Yellow
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val sb = StringBuilder()
            var allGood = true
            
            // 1. Check Permissions
            val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            val notifEnabled = listeners != null && listeners.contains(packageName)
            if (notifEnabled) sb.append("✓ Notification Access\n") else { sb.append("✗ Notification Access Missing\n"); allGood = false }
            
            // 2. Check Endpoints
            val endpoints = PrefsManager.getEndpoints(this@MainActivity).filter { it.isEnabled }
            if (endpoints.isNotEmpty()) sb.append("✓ ${endpoints.size} Endpoint(s) Configured\n") else { sb.append("✗ No Active Endpoints\n"); allGood = false }
            
            // 3. Check JSON Template
            try {
                val t = PrefsManager.getJsonTemplate(this@MainActivity)
                if (t.startsWith("{")) sb.append("✓ JSON Template Valid\n") else { sb.append("✗ JSON Invalid\n"); allGood = false }
            } catch (e: Exception) {
                sb.append("✗ JSON Template Error\n")
                allGood = false
            }

            // 4. Test Connectivity (Ping)
            if (allGood && endpoints.isNotEmpty()) {
                val pingSuccess = NetworkClient.sendVerificationPing(this@MainActivity)
                if (pingSuccess) {
                    sb.append("✓ Connectivity Verified (Green Light)\n")
                } else {
                    sb.append("✗ Connection Failed (Check URL/Internet)\n")
                    allGood = false
                }
            }
            
            statusDetail.text = sb.toString()
            btnVerify.isEnabled = true
            btnVerify.text = "Verify"
            
            if (allGood) {
                statusText.text = "System Online"
                statusLight.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
            } else {
                statusText.text = "Authentication / Config Error"
                statusLight.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices ?: "")
        val myComponentName = android.content.ComponentName(context, service).flattenToString()
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(myComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
