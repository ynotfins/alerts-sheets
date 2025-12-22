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
    private lateinit var gridSourceCards: GridLayout
    private lateinit var dotPermissions: View
    private lateinit var dotLogs: View
    private lateinit var textSourcesHeader: TextView
    private lateinit var textSourcesCount: TextView
    private lateinit var textSentToday: TextView
    private lateinit var textFailedToday: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)
        
        sourceManager = SourceManager(this)
        
        gridSourceCards = findViewById(R.id.grid_source_cards)
        dotPermissions = findViewById(R.id.dot_permissions)
        dotLogs = findViewById(R.id.dot_logs)
        textSourcesHeader = findViewById(R.id.text_sources_header)
        textSourcesCount = findViewById(R.id.text_sources_count)
        textSentToday = findViewById(R.id.text_sent_today)
        textFailedToday = findViewById(R.id.text_failed_today)
        
        setupPermanentCards()
    }
    
    override fun onResume() {
        super.onResume()
        loadDynamicCards()
        updateStatus()
    }
    
    private fun setupPermanentCards() {
        // Lab card
        findViewById<View>(R.id.card_lab).setOnClickListener {
            startActivity(Intent(this, LabActivity::class.java))
        }
        
        // Permissions card
        findViewById<View>(R.id.card_permissions).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        
        // Logs card
        findViewById<View>(R.id.card_logs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }
    
    private fun loadDynamicCards() {
        scope.launch(Dispatchers.IO) {
            val sources = sourceManager.getAllSources()
            
            withContext(Dispatchers.Main) {
                // Clear existing dynamic cards
                gridSourceCards.removeAllViews()
                
                // Show/hide section header
                if (sources.isNotEmpty()) {
                    textSourcesHeader.visibility = View.VISIBLE
                } else {
                    textSourcesHeader.visibility = View.GONE
                }
                
                // Add dynamic source cards
                sources.forEach { source ->
                    addSourceCard(source)
                }
            }
        }
    }
    
    private fun addSourceCard(source: Source) {
        val cardView = layoutInflater.inflate(R.layout.item_dashboard_source_card, null)
        
        cardView.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = resources.getDimensionPixelSize(R.dimen.card_height)  // 160dp
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(
                resources.getDimensionPixelSize(R.dimen.card_margin),
                resources.getDimensionPixelSize(R.dimen.card_margin),
                resources.getDimensionPixelSize(R.dimen.card_margin),
                resources.getDimensionPixelSize(R.dimen.card_margin)
            )
        }
        
        // Set colors
        cardView.findViewById<androidx.cardview.widget.CardView>(R.id.source_card)
            ?.setCardBackgroundColor(source.cardColor)
        
        // Set icon
        val iconView = cardView.findViewById<ImageView>(R.id.source_icon)
        val iconRes = getIconResource(source.iconName)
        iconView?.setImageResource(iconRes)
        iconView?.setColorFilter(Color.WHITE)
        
        // Set name
        cardView.findViewById<TextView>(R.id.source_name)?.text = source.name
        
        // Set subtitle (type)
        val subtitle = when (source.type) {
            com.example.alertsheets.domain.models.SourceType.APP -> "App Notifications"
            com.example.alertsheets.domain.models.SourceType.SMS -> "SMS Messages"
        }
        cardView.findViewById<TextView>(R.id.source_subtitle)?.text = subtitle
        
        // Status dot
        val statusDot = cardView.findViewById<View>(R.id.source_status_dot)
        statusDot?.visibility = if (source.enabled) View.VISIBLE else View.GONE
        
        // Click handler
        cardView.setOnClickListener {
            val intent = Intent(this@MainActivity, LabActivity::class.java)
            intent.putExtra("source_id", source.id)
            startActivity(intent)
        }
        
        gridSourceCards.addView(cardView)
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
            
            // Calculate today's stats
            val today = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            val todayLogs = LogRepository.getLogs().filter { it.timestamp > today }
            val sentToday = todayLogs.count { it.status == LogStatus.SENT }
            val failedToday = todayLogs.count { it.status == LogStatus.FAILED }
            
            withContext(Dispatchers.Main) {
                // Update stats
                textSourcesCount.text = sources.size.toString()
                textSentToday.text = sentToday.toString()
                textFailedToday.text = failedToday.toString()
                
                // Update permission dot
                dotPermissions.visibility = if (!hasNotificationAccess) View.VISIBLE else View.GONE
                
                // Update log dot (show if recent activity)
                val recentLogs = LogRepository.getLogs().take(5)
                dotLogs.visibility = if (recentLogs.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
