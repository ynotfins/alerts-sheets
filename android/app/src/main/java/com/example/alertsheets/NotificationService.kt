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

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
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
        val targetApps = PrefsManager.getTargetApps(this)
        
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
