package com.example.alertsheets.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.alertsheets.*
import com.example.alertsheets.data.repositories.EndpointRepository
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main Dashboard - 3 permanent cards in row + dynamic source cards
 */
class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var sourceManager: SourceManager
    private lateinit var endpointRepo: EndpointRepository
    
    private lateinit var gridCards: GridLayout
    private lateinit var dotPermissions: ImageView
    private lateinit var dotLogs: ImageView
    private lateinit var textStats: TextView
    private lateinit var textSourcesHeader: TextView
    private lateinit var emptyState: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)
        
        sourceManager = SourceManager(this)
        endpointRepo = EndpointRepository(this)
        
        // Initialize UI
        gridCards = findViewById(R.id.grid_cards)
        dotPermissions = findViewById(R.id.dot_permissions)
        dotLogs = findViewById(R.id.dot_logs)
        textStats = findViewById(R.id.text_stats)
        textSourcesHeader = findViewById(R.id.text_sources_header)
        emptyState = findViewById(R.id.empty_state)
        
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
        scope.launch {
            val sources = withContext(Dispatchers.IO) {
                sourceManager.getAllSources()
            }
            
            gridCards.removeAllViews()
            textSourcesHeader.text = "Sources (${sources.size})"
            
            if (sources.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                gridCards.visibility = View.GONE
                return@launch
            }
            
            emptyState.visibility = View.GONE
            gridCards.visibility = View.VISIBLE
            
            sources.forEach { source ->
                val card = layoutInflater.inflate(R.layout.item_dashboard_source_card, gridCards, false)
                
                val icon = card.findViewById<ImageView>(R.id.source_icon)
                val name = card.findViewById<TextView>(R.id.source_name)
                val dot = card.findViewById<ImageView>(R.id.source_status_dot)
                
                // Set icon
                val iconRes = when (source.iconName) {
                    "fire" -> R.drawable.ic_fire
                    "sms" -> R.drawable.ic_sms
                    "email" -> R.drawable.ic_email
                    "location" -> R.drawable.ic_location
                    "alert" -> R.drawable.ic_alert
                    "link" -> R.drawable.ic_link
                    "security" -> R.drawable.ic_security
                    "medical" -> R.drawable.ic_medical
                    "dashboard" -> R.drawable.ic_dashboard
                    else -> R.drawable.ic_notification
                }
                icon.setImageResource(iconRes)
                icon.setColorFilter(source.cardColor)
                
                name.text = source.name
                
                // Status dot
                val isConfigured = source.endpointIds.isNotEmpty() && source.templateJson.isNotEmpty()
                dot.setImageResource(
                    if (source.enabled && isConfigured) R.drawable.bg_status_dot_green
                    else R.drawable.bg_status_dot_red
                )
                
                // Click to edit
                card.setOnClickListener {
                    val intent = Intent(this@MainActivity, LabActivity::class.java)
                    intent.putExtra("source_id", source.id)
                    startActivity(intent)
                }
                
                // Layout params for 2-column grid
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                card.layoutParams = params
                
                gridCards.addView(card)
            }
        }
    }
    
    private fun updateStatus() {
        scope.launch(Dispatchers.IO) {
            // Check permissions
            val hasNotificationAccess = try {
                val enabled = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                enabled.isNotificationListenerAccessGranted(android.content.ComponentName(this@MainActivity, com.example.alertsheets.services.AlertsNotificationListener::class.java))
            } catch (e: Exception) {
                false
            }
            
            // Calculate today's stats
            val today = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            val todayLogs = LogRepository.getLogs().filter { it.timestamp > today }
            val sentToday = todayLogs.count { it.status == LogStatus.SENT }
            val failedToday = todayLogs.count { it.status == LogStatus.FAILED }
            val totalToday = todayLogs.size
            
            withContext(Dispatchers.Main) {
                // Update stats text
                textStats.text = "Today: $totalToday events • $sentToday sent • $failedToday failed"
                
                // Update permission dot
                dotPermissions.setImageResource(
                    if (hasNotificationAccess) R.drawable.bg_status_dot_green
                    else R.drawable.bg_status_dot_red
                )
                
                // Update log dot (show green if recent activity)
                val recentLogs = todayLogs.take(5)
                dotLogs.setImageResource(
                    if (recentLogs.any { it.status == LogStatus.SENT }) R.drawable.bg_status_dot_green
                    else if (recentLogs.isNotEmpty()) R.drawable.bg_status_dot_red
                    else R.drawable.bg_status_dot_red
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
