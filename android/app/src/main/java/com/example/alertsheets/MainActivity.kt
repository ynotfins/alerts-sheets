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

    private lateinit var statusTitle: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var tickerText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        statusTitle = findViewById(R.id.tv_status_title)
        serviceStatus = findViewById(R.id.tv_service_status)
        tickerText = findViewById(R.id.footer_ticker)

        // Card Bindings
        findViewById<android.view.View>(R.id.card_apps).setOnClickListener {
             startActivity(Intent(this, AppsListActivity::class.java))
        }

        findViewById<android.view.View>(R.id.card_sms).setOnClickListener {
            startActivity(Intent(this, SmsConfigActivity::class.java))
        }
        
        findViewById<android.view.View>(R.id.card_config).setOnClickListener {
            // "Payloads"
            startActivity(Intent(this, AppConfigActivity::class.java))
        }
        
        findViewById<android.view.View>(R.id.card_endpoints).setOnClickListener {
             // We need an EndpointConfigActivity. For now, let's reuse a simple dialog or new activity?
             // Since we removed the recycler from main, we MUST have a way to manage endpoints.
             // I will implement EndpointConfigActivity in next step if it doesn't exist.
             // For now, I'll direct to a placeholder or create it.
             // Let's create `EndpointActivity` later. I'll point to it now.
             startActivity(Intent(this, EndpointActivity::class.java))
        }

        findViewById<android.view.View>(R.id.card_logs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        
        // Setup Ticker
        tickerText.isSelected = true 
    }
    
    override fun onResume() {
        super.onResume()
        checkPermissions()
        updateServiceStatus()
        updateMonitoringTicker()
        
        // Update Queue Status (Poll DB or just check Count)
        updateQueueStatus()
    }
    
    private fun updateQueueStatus() {
        val tvQueue = findViewById<TextView>(R.id.tv_queue_status)
        CoroutineScope(Dispatchers.IO).launch {
             val db = com.example.alertsheets.data.QueueDbHelper(this@MainActivity)
             val count = db.getPendingCount()
             db.close()
             
             withContext(Dispatchers.Main) {
                 if (count > 0) {
                     tvQueue.text = "Queue: $count pending..."
                     tvQueue.setTextColor(android.graphics.Color.YELLOW)
                 } else {
                     tvQueue.text = "Queue: Idle"
                     tvQueue.setTextColor(android.graphics.Color.LTGRAY)
                 }
             }
        }
    }
    
    // ... migrateLegacyUrl ...

    // --- Permissions & Status Helpers ---
    private fun checkPermissions() {
        // Status Light Logic
        val light: android.view.View = findViewById(R.id.tv_service_status) // Wait, we reused tv_service_status as text. Do we have a light?
        // Layout has tv_service_status text.
        // Let's color the text.
        
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val notifEnabled = listeners != null && listeners.contains(packageName)
        
        if (notifEnabled) {
            serviceStatus.text = "● Service Active"
            serviceStatus.setTextColor(android.graphics.Color.GREEN)
        } else {
            serviceStatus.text = "● Service Paused (Perm Missing)"
            serviceStatus.setTextColor(android.graphics.Color.RED)
            serviceStatus.setOnClickListener {
                 startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }
    
    private fun updateServiceStatus() {
        // Redundant with checkPermissions for now
    }

    private fun updateMonitoringTicker() {
        val apps = PrefsManager.getTargetApps(this)
        val smsTargets = PrefsManager.getSmsTargets(this)
        
        val displayList = mutableListOf<String>()
        
        // 1. App Names
        if (apps.isNotEmpty()) {
            val pm = packageManager
            val appNames = apps.map { pkg ->
                try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
            }
            displayList.addAll(appNames)
        }
        
        // 2. SMS Targets
        if (smsTargets.isNotEmpty()) {
             displayList.add("SMS (${smsTargets.size})")
        }
        
        if (displayList.isEmpty()) {
            tickerText.text = "No monitoring targets selected. Tap cards to configure."
        } else {
            tickerText.text = "Monitoring: ${displayList.joinToString(", ")}   ***   "
            tickerText.isSelected = true 
        }
    }
}
