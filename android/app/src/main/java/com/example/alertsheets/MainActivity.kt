package com.example.alertsheets

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvServiceStatus: TextView
    private lateinit var btnMaster: Button
    private lateinit var tvFooterTicker: TextView

    // Dot Views
    private lateinit var dotApps: ImageView
    private lateinit var dotSms: ImageView
    private lateinit var dotPayloads: ImageView
    private lateinit var dotEndpoints: ImageView
    private lateinit var dotPermissions: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        // Initialize log repository
        LogRepository.initialize(this)

        // Init Views
        tvServiceStatus = findViewById(R.id.tv_service_status)
        btnMaster = findViewById(R.id.btn_master_status)
        tvFooterTicker = findViewById(R.id.footer_ticker)

        dotApps = findViewById(R.id.dot_apps)
        dotSms = findViewById(R.id.dot_sms)
        dotPayloads = findViewById(R.id.dot_payloads)
        dotEndpoints = findViewById(R.id.dot_endpoints)
        dotPermissions = findViewById(R.id.dot_permissions)

        // Card Click Listeners
        findViewById<View>(R.id.card_apps).setOnClickListener {
            startActivity(Intent(this, AppsListActivity::class.java))
        }
        findViewById<View>(R.id.card_sms).setOnClickListener {
            startActivity(Intent(this, SmsConfigActivity::class.java))
        }
        findViewById<View>(R.id.card_config).setOnClickListener {
            startActivity(Intent(this, AppConfigActivity::class.java))
        }
        findViewById<View>(R.id.card_endpoints).setOnClickListener {
            startActivity(Intent(this, EndpointActivity::class.java))
        }
        findViewById<View>(R.id.card_logs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        findViewById<View>(R.id.card_permissions).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        // Master Button Logic
        updateMasterButtonVisuals()
        btnMaster.setOnClickListener {
            val current = PrefsManager.getMasterEnabled(this)
            PrefsManager.setMasterEnabled(this, !current)
            updateMasterButtonVisuals()
            updateDashboardStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateDashboardStatus()
        updateFooterTicker()
    }

    private fun updateMasterButtonVisuals() {
        val enabled = PrefsManager.getMasterEnabled(this)
        if (enabled) {
            btnMaster.text = "LIVE"
            btnMaster.background.setTint(Color.parseColor("#4CAF50")) // Green
        } else {
            btnMaster.text = "PAUSED"
            btnMaster.background.setTint(Color.parseColor("#F44336")) // Red
        }
    }

    private fun updateDashboardStatus() {
        // 1. Check Permissions
        val permNotif = checkNotifListener()
        val permSms =
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                        PackageManager.PERMISSION_GRANTED
        val permBattery =
                (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(
                        packageName
                )
        val allPerms = permNotif
        // Note: Strict battery/SMS might be optional for core functionality, but let's assume
        // strict for Dot.
        // User asked for override. If Notification Listener is ON, we are "mostly" green.

        setDotColor(dotPermissions, allPerms)

        // 2. Check Apps
        // God Mode: Empty list means "Capture Everything", so it is always a valid state.
        val hasApps = true
        setDotColor(dotApps, hasApps)

        // 3. Check SMS
        setDotColor(dotSms, permSms)

        // 4. Check Endpoints
        val endpoints = PrefsManager.getEndpoints(this)
        val hasEndpoint = endpoints.isNotEmpty() && endpoints.any { it.url.isNotEmpty() }
        setDotColor(dotEndpoints, hasEndpoint)

        // 5. Check Payloads (Test Status)
        val testStatus = PrefsManager.getPayloadTestStatus(this)
        // 1 = Success, 0/2 = Warn
        setDotColor(
                dotPayloads,
                true
        ) // Default Green for now as per user preference unless specific fail

        // 6. Master Live Status
        val masterEnabled = PrefsManager.getMasterEnabled(this)

        if (!masterEnabled) {
            tvServiceStatus.text = "● SYSTEM PAUSED"
            tvServiceStatus.setTextColor(Color.RED)
        } else if (allPerms
        ) { // Simplified master check: If Perms OK + Enabled -> Service assumes running
            tvServiceStatus.text = "● Service Active"
            tvServiceStatus.setTextColor(Color.GREEN)
            // Ensure service
            startForegroundService(Intent(this, NotificationService::class.java))
        } else {
            tvServiceStatus.text = "● Service Waiting (Perms)"
            tvServiceStatus.setTextColor(Color.YELLOW)
        }
    }

    private fun setDotColor(view: ImageView, isGreen: Boolean) {
        if (isGreen) {
            view.setColorFilter(Color.parseColor("#4CAF50"))
        } else {
            view.setColorFilter(Color.parseColor("#F44336"))
        }
    }

    private fun checkNotifListener(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabledLegacy = flat != null && flat.contains(packageName)
        val isEnabledCompat =
                NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        return isEnabledLegacy || isEnabledCompat
    }

    private fun updateFooterTicker() {
        val selectedApps = PrefsManager.getTargetApps(this)
        
        val text = if (selectedApps.isEmpty()) {
            "Monitoring: ALL APPS (God Mode)"
        } else {
            val pm = packageManager
            val appNames = selectedApps.take(5).mapNotNull { pkg ->
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.split(".").lastOrNull()
                }
            }
            
            val displayText = if (appNames.size < selectedApps.size) {
                appNames.joinToString(", ") + " and ${selectedApps.size - appNames.size} more"
            } else {
                appNames.joinToString(", ")
            }
            
            "Monitoring: $displayText"
        }
        
        tvFooterTicker.text = text
    }
}
