package com.example.alertsheets

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Run as Foreground Service to prevent system killing
        val notification = androidx.core.app.NotificationCompat.Builder(this, "service_channel")
            .setContentTitle("AlertsToSheets Service")
            .setContentText("Monitoring notifications in the background")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this icon exists or use android.R.drawable.ic_dialog_info
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
            
        // Service ID 101 for the foreground notification
        try {
            startForeground(101, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return START_STICKY
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        // Optional: Broadcast connected state or log it
        android.util.Log.d("NotificationService", "Listener Connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Optional: Try to rebind if supported or log
        android.util.Log.d("NotificationService", "Listener Disconnected - requesting rebind")
        try {
            requestRebind(android.content.ComponentName(this, NotificationService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "service_channel",
                "Background Monitoring",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Keeps the monitoring service alive"
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val extras = sbn.notification.extras
        // Get the title and text content
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullContent = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString("\n")

        Log.d("NotificationService", "Received: $fullContent")

        // 1. App Filter Check
        val targetApps = PrefsManager.getTargetApps(this)
        // If filter is active and pkg not in list, ignore.
        if (targetApps.isNotEmpty() && !targetApps.contains(sbn.packageName)) {
            return
        }

        // 2. Dynamic / Template
        // We now prioritize the Global JSON Template.
        // We now prioritize the Global JSON Template.
        // targetApps already checked above.
        
        // If we are here, we are allowed to process (checked filter above).
        
        // Load Template
        val template = PrefsManager.getJsonTemplate(this)
        val isBnnMode = template.contains("{{id}}") || template.contains("fdCodes")
        
        if (isBnnMode) {
             // BNN logic (Legacy Pipe)
             if (fullContent.contains("|") && DeDuplicator.shouldProcess(fullContent)) {
                 val parsed = Parser.parse(fullContent)
                 if (parsed != null) {
                     // We have ParsedData. Now apply to Template.
                     val json = TemplateEngine.applyBnn(template, parsed)
                     scope.launch {
                         NetworkClient.sendJson(this@NotificationService, json)
                     }
                 }
             }
        } else {
            // Generic App Notification Logic
            if (DeDuplicator.shouldProcess(fullContent)) {
                val json = TemplateEngine.applyGeneric(template, sbn.packageName, title, text, bigText)
                scope.launch {
                    NetworkClient.sendJson(this@NotificationService, json)
                }
            }
        }
        
        /*
        // Old Logic commented out for reference
        // ...
        */
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Ignore
    }
}
