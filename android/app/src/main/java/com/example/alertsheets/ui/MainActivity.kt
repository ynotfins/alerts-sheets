package com.example.alertsheets.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.alertsheets.LogRepository
import com.example.alertsheets.R
import com.example.alertsheets.data.repositories.EndpointRepository
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.SourceType
import com.example.alertsheets.utils.SmsRoleManager

/**
 * MainActivity - V2 Samsung One UI Dashboard
 * 
 * Pure black background (#000000)
 * Colorful large icons
 * Clean card-based layout
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var sourceManager: SourceManager
    private lateinit var tvServiceStatus: TextView
    private lateinit var btnMaster: Button
    private lateinit var footerTicker: TextView
    private lateinit var dotPermissions: ImageView
    private lateinit var dotApps: ImageView
    private lateinit var dotSms: ImageView
    private lateinit var dotSources: ImageView
    private lateinit var dotPayloads: ImageView
    private lateinit var dotEndpoints: ImageView
    private lateinit var dotLogs: ImageView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)
        
        sourceManager = SourceManager(applicationContext)
        
        // Find views
        tvServiceStatus = findViewById(R.id.tv_service_status)
        btnMaster = findViewById(R.id.btn_master_status)
        footerTicker = findViewById(R.id.footer_ticker)
        dotPermissions = findViewById(R.id.dot_permissions)
        dotApps = findViewById(R.id.dot_apps)
        dotSms = findViewById(R.id.dot_sms)
        dotSources = findViewById(R.id.dot_sources)
        dotPayloads = findViewById(R.id.dot_payloads)
        dotEndpoints = findViewById(R.id.dot_endpoints)
        dotLogs = findViewById(R.id.dot_logs)
        
        // Setup card clicks
        setupCardClicks()
        
        // Update dashboard
        updateDashboardStatus()
    }
    
    override fun onResume() {
        super.onResume()
        updateDashboardStatus()
    }
    
    private fun setupCardClicks() {
        findViewById<FrameLayout>(R.id.card_apps).setOnClickListener {
            startActivity(Intent(this, com.example.alertsheets.AppsListActivity::class.java))
        }
        
        findViewById<FrameLayout>(R.id.card_sms).setOnClickListener {
            startActivity(Intent(this, com.example.alertsheets.SmsConfigActivity::class.java))
        }
        
        findViewById<FrameLayout>(R.id.card_sources).setOnClickListener {
            startActivity(Intent(this, com.example.alertsheets.SourceConfigActivity::class.java))
        }
        
        findViewById<FrameLayout>(R.id.card_config).setOnClickListener {
            startActivity(Intent(this, com.example.alertsheets.AppConfigActivity::class.java))
        }
        
        findViewById<FrameLayout>(R.id.card_endpoints).setOnClickListener {
            startActivity(Intent(this, com.example.alertsheets.EndpointActivity::class.java))
        }
        
        findViewById<FrameLayout>(R.id.card_permissions).setOnClickListener {
            startActivity(Intent(this, com.example.alertsheets.PermissionsActivity::class.java))
        }
        
        findViewById<FrameLayout>(R.id.card_logs).setOnClickListener {
            startActivity(Intent(this, com.example.alertsheets.LogActivity::class.java))
        }
    }
    
    private fun updateDashboardStatus() {
        // Service status
        tvServiceStatus.text = "● Service Active (GOD MODE)"
        tvServiceStatus.setTextColor(0xFF00D980.toInt()) // Samsung Green
        
        // Master button (always LIVE in v2)
        btnMaster.text = "LIVE"
        btnMaster.setBackgroundColor(0xFF00D980.toInt()) // Samsung Green
        
        // Check permissions for the permissions card dot
        val hasNotifListener = checkNotificationListener()
        val hasSmsPermission = checkSmsPermission()
        val hasBatteryOptimization = checkBatteryOptimization()
        val allPermissionsGranted = hasNotifListener && hasSmsPermission && hasBatteryOptimization
        
        // Update permissions dot color
        dotPermissions.setColorFilter(
            if (allPermissionsGranted) 0xFF00D980.toInt() else 0xFFFF5252.toInt()
        )
        
        // ✅ FIX: Other cards should show RED if not configured
        val appSources = sourceManager.getSourcesByType(SourceType.APP).filter { it.enabled }
        val smsSources = sourceManager.getSourcesByType(SourceType.SMS).filter { it.enabled }
        val stats = sourceManager.getTodayStats()
        
        // Apps card: RED if no apps configured
        dotApps.setColorFilter(
            if (appSources.isNotEmpty()) 0xFF00D980.toInt() else 0xFFFF5252.toInt()
        )
        
        // SMS card: RED if no SMS sources configured
        dotSms.setColorFilter(
            if (smsSources.isNotEmpty()) 0xFF00D980.toInt() else 0xFFFF5252.toInt()
        )
        
        // Sources card: GREEN if any sources configured, YELLOW if endpoints/templates not assigned
        val allSources = sourceManager.getAllSources()
        val sourcesConfigured = allSources.isNotEmpty()
        val sourcesFullyConfigured = allSources.all { source ->
            val endpoint = sourceManager.getEndpointById(source.endpointId)
            endpoint != null && endpoint.enabled
        }
        dotSources.setColorFilter(
            when {
                !sourcesConfigured -> 0xFFFF5252.toInt() // RED - no sources
                !sourcesFullyConfigured -> 0xFFFF9800.toInt() // ORANGE - some sources need config
                else -> 0xFF00D980.toInt() // GREEN - all good
            }
        )
        
        // Payloads card: Always green (templates always available)
        dotPayloads.setColorFilter(0xFF00D980.toInt())
        
        // Endpoints card: Check if any endpoints are enabled
        val endpointRepo = EndpointRepository(this)
        val endpoints = endpointRepo.getEnabled()
        dotEndpoints.setColorFilter(
            if (endpoints.isNotEmpty()) 0xFF00D980.toInt() else 0xFFFF5252.toInt()
        )
        
        // Logs card: Check if we have logs
        val hasLogs = LogRepository.getLogs().isNotEmpty()
        dotLogs.setColorFilter(
            if (hasLogs) 0xFF00D980.toInt() else 0xFFFF5252.toInt()
        )
        
        // Footer ticker
        val smsRoleStatus = if (SmsRoleManager.isDefaultSmsApp(this)) "ROLE_SMS ✓" else "ROLE_SMS ✗"
        
        footerTicker.text = "Monitoring: ${appSources.size} Apps, ${smsSources.size} SMS • " +
                "Today: ${stats["sent"]} sent, ${stats["failed"]} failed • $smsRoleStatus"
    }
    
    private fun checkNotificationListener(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val serviceName = "$packageName/com.example.alertsheets.services.AlertsNotificationListener"
        val isEnabledLegacy = flat != null && (flat.contains(packageName) || flat.contains(serviceName))
        val isEnabledCompat = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        return isEnabledLegacy || isEnabledCompat
    }
    
    private fun checkSmsPermission(): Boolean {
        val defaultSmsPackage = try {
            android.provider.Telephony.Sms.getDefaultSmsPackage(this)
        } catch (e: Exception) {
            null
        }
        val isDefaultSms = defaultSmsPackage == packageName
        val hasPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        return isDefaultSms || hasPermission
    }
    
    private fun checkBatteryOptimization(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }
}

