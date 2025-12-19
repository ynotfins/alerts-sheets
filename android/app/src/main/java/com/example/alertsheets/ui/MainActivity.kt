package com.example.alertsheets.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.alertsheets.R
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)
        
        sourceManager = SourceManager(applicationContext)
        
        // Find views
        tvServiceStatus = findViewById(R.id.tv_service_status)
        btnMaster = findViewById(R.id.btn_master_status)
        footerTicker = findViewById(R.id.footer_ticker)
        
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
            // TODO: Navigate to Apps management
            Toast.makeText(this, "Apps Management", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<FrameLayout>(R.id.card_sms).setOnClickListener {
            // TODO: Navigate to SMS management
            Toast.makeText(this, "SMS Management", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<FrameLayout>(R.id.card_config).setOnClickListener {
            // TODO: Navigate to Payloads
            Toast.makeText(this, "Payloads", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<FrameLayout>(R.id.card_endpoints).setOnClickListener {
            // TODO: Navigate to Endpoints
            Toast.makeText(this, "Endpoints", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<FrameLayout>(R.id.card_permissions).setOnClickListener {
            // TODO: Navigate to Permissions
            Toast.makeText(this, "Permissions", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<FrameLayout>(R.id.card_logs).setOnClickListener {
            // TODO: Navigate to Logs
            Toast.makeText(this, "Activity Logs", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateDashboardStatus() {
        // Service status
        tvServiceStatus.text = "● Service Active (GOD MODE)"
        tvServiceStatus.setTextColor(0xFF00D980.toInt()) // Samsung Green
        
        // Master button (always LIVE in v2)
        btnMaster.text = "LIVE"
        btnMaster.setBackgroundColor(0xFF00D980.toInt()) // Samsung Green
        
        // Footer ticker
        val appSources = sourceManager.getSourcesByType(SourceType.APP).filter { it.enabled }
        val smsSources = sourceManager.getSourcesByType(SourceType.SMS).filter { it.enabled }
        val stats = sourceManager.getTodayStats()
        
        val smsRoleStatus = if (SmsRoleManager.isDefaultSmsApp(this)) "ROLE_SMS ✓" else "ROLE_SMS ✗"
        
        footerTicker.text = "Monitoring: ${appSources.size} Apps, ${smsSources.size} SMS • " +
                "Today: ${stats["sent"]} sent, ${stats["failed"]} failed • $smsRoleStatus"
    }
}

