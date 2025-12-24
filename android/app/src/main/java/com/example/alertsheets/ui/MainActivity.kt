package com.example.alertsheets.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.alertsheets.BuildConfig
import com.example.alertsheets.R
import com.example.alertsheets.LabActivity
import com.example.alertsheets.PermissionsActivity
import com.example.alertsheets.LogActivity
import com.example.alertsheets.LogEntry
import com.example.alertsheets.LogRepository
import com.example.alertsheets.LogStatus
import com.example.alertsheets.data.repositories.EndpointRepository
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceType
import com.example.alertsheets.utils.PermissionsUtil
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
    private lateinit var cardPermissions: FrameLayout
    private lateinit var textPermissionsTitle: TextView
    private lateinit var textPermissionsSubtitle: TextView
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
        cardPermissions = findViewById(R.id.card_permissions)
        textPermissionsTitle = findViewById(R.id.text_permissions_title)
        textPermissionsSubtitle = findViewById(R.id.text_permissions_subtitle)
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
        
        // DEBUG ONLY: Test Harness accessible via intent action (no reflection)
        // The intent action exists ONLY in debug/AndroidManifest.xml
        // Release builds will have no matching activity, so this safely does nothing
        if (BuildConfig.DEBUG) {
            try {
                val intent = Intent("com.example.alertsheets.DEBUG_INGEST_TEST")
                intent.setPackage(packageName)
                
                // Verify activity exists (debug-only)
                if (packageManager.resolveActivity(intent, 0) != null) {
                    // Successfully found test harness
                    // You can optionally add a visible UI card here if desired
                    // For now, just log availability
                    android.util.Log.i("MainActivity", "✅ Test harness available in debug build")
                }
            } catch (e: Exception) {
                // Test harness not available (expected in release)
            }
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
                val subtitle = card.findViewById<TextView>(R.id.source_subtitle)
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
                
                // Set subtitle with source type and endpoint count
                val sourceTypeText = when (source.type) {
                    SourceType.APP -> "App"
                    SourceType.SMS -> "SMS"
                }
                subtitle.text = "$sourceTypeText • ${source.endpointIds.size} endpoint(s)"
                
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
            // Check permissions using centralized utility
            val permissionStatus = PermissionsUtil.checkAllPermissions(this@MainActivity)
            
            // Calculate today's stats
            val today = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            val todayLogs = LogRepository.getLogs().filter { it.timestamp > today }
            val sentToday = todayLogs.count { it.status == LogStatus.SENT }
            val failedToday = todayLogs.count { it.status == LogStatus.FAILED }
            val totalToday = todayLogs.size
            
            withContext(Dispatchers.Main) {
                // Update stats text
                textStats.text = "Today: $totalToday events • $sentToday sent • $failedToday failed"
                
                // Update permission tile dynamically (green if OK, red if not)
                if (permissionStatus.allGranted) {
                    cardPermissions.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.tile_bg_green))
                    textPermissionsTitle.text = "Perms"
                    textPermissionsSubtitle.text = "All granted"
                } else {
                    cardPermissions.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.tile_bg_red))
                    textPermissionsTitle.text = "Perms"
                    textPermissionsSubtitle.text = PermissionsUtil.formatMissingList(permissionStatus.missing)
                }
                
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
