package com.example.alertsheets.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.alertsheets.*
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main Dashboard - Shows 3 permanent cards + dynamic source cards
 */
class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var sourceManager: SourceManager
    private lateinit var gridCards: GridLayout
    private lateinit var dotPermissions: ImageView
    private lateinit var dotLogs: ImageView
    private lateinit var textStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)
        
        sourceManager = SourceManager(this)
        
        gridCards = findViewById(R.id.grid_cards)
        dotPermissions = findViewById(R.id.dot_permissions)
        dotLogs = findViewById(R.id.dot_logs)
        textStats = findViewById(R.id.text_stats)
        
        setupPermanentCards()
    }
    
    override fun onResume() {
        super.onResume()
        loadDynamicCards()
        updateStatus()
    }
    
    private fun setupPermanentCards() {
        // Lab card
        findViewById<FrameLayout>(R.id.card_lab).setOnClickListener {
            startActivity(Intent(this, LabActivity::class.java))
        }
        
        // Permissions card
        findViewById<FrameLayout>(R.id.card_permissions).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        
        // Logs card
        findViewById<FrameLayout>(R.id.card_logs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }
    
    private fun loadDynamicCards() {
        scope.launch(Dispatchers.IO) {
            val sources = sourceManager.getAllSources()
            
            withContext(Dispatchers.Main) {
                // Remove all cards after the 3 permanent ones
                val permanentCards = 3
                while (gridCards.childCount > permanentCards) {
                    gridCards.removeViewAt(permanentCards)
                }
                
                // Add dynamic source cards
                sources.forEach { source ->
                    addSourceCard(source)
                }
            }
        }
    }
    
    private fun addSourceCard(source: Source) {
        val card = FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 420  // 3x height for 140dp
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(24, 24, 24, 24)
            }
            setBackgroundColor(source.cardColor)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card_dark)
            isClickable = true
            isFocusable = true
            
            setOnClickListener {
                // Edit source in Lab
                val intent = Intent(this@MainActivity, LabActivity::class.java)
                intent.putExtra("source_id", source.id)
                startActivity(intent)
            }
        }
        
        // Icon
        val icon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(192, 192).apply {
                gravity = android.view.Gravity.CENTER
            }
            val iconRes = getIconResource(source.iconName)
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
        }
        card.addView(icon)
        
        // Name
        val nameText = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 48
            }
            text = source.name
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        card.addView(nameText)
        
        // Status dot
        val dot = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(36, 36).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins(24, 24, 24, 24)
            }
            setImageResource(R.drawable.ic_launcher_foreground)
            setColorFilter(if (source.enabled) Color.parseColor("#4CAF50") else Color.parseColor("#FF5252"))
        }
        card.addView(dot)
        
        gridCards.addView(card)
    }
    
    private fun getIconResource(iconName: String): Int {
        return when (iconName) {
            "fire" -> R.drawable.ic_fire
            "sms" -> R.drawable.ic_sms
            "email" -> R.drawable.ic_email
            "notification" -> R.drawable.ic_notification
            "location" -> R.drawable.ic_location
            "alert" -> R.drawable.ic_alert
            "link" -> R.drawable.ic_link
            "security" -> R.drawable.ic_security
            "medical" -> R.drawable.ic_medical
            "dashboard" -> R.drawable.ic_dashboard
            else -> R.drawable.ic_notification
        }
    }
    
    private fun updateStatus() {
        scope.launch(Dispatchers.IO) {
            val sources = sourceManager.getAllSources()
            val stats = sourceManager.getTodayStats()
            
            // Check permissions
            val hasNotificationAccess = try {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.isNotificationPolicyAccessGranted
            } catch (e: Exception) {
                false
            }
            
            withContext(Dispatchers.Main) {
                // Update permission dot
                dotPermissions.setColorFilter(
                    if (hasNotificationAccess) Color.parseColor("#00D980") 
                    else Color.parseColor("#FF0000")
                )
                
                // Update log dot based on recent activity
                val recentLogs = LogRepository.getLogs().take(5)
                val hasFailures = recentLogs.any { it.status == LogStatus.FAILED }
                dotLogs.setColorFilter(
                    if (hasFailures) Color.parseColor("#FF9800")
                    else Color.parseColor("#4CAF50")
                )
                
                // Update stats
                textStats.text = "Monitoring: ${sources.size} sources • Today: ${stats["sent"]} sent, ${stats["failed"]} failed"
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
