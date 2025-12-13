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

        // 2. Dynamic Config Check
        val config = PrefsManager.getAppConfig(this, sbn.packageName)
        if (config.mappings.isNotEmpty() || config.staticFields.isNotEmpty()) {
            // Use Dynamic Logic ensuring safety
            try {
                val dynamicData = DataExtractor.extract(this, sbn, config)
                 if (DeDuplicator.shouldProcess(dynamicData.toString())) {
                     scope.launch {
                         NetworkClient.sendData(this@NotificationService, dynamicData)
                     }
                 }
            } catch (e: Exception) {
                Log.e("NotificationService", "Error in dynamic extraction", e)
            }
             return
        }

        // 3. Fallback: Legacy Pipe Logic (Only if no config exists)
        // Parse logic
        // We only proceed if it looks like one of our target notifications (contains pipes)
        if (fullContent.contains("|")) {
            if (DeDuplicator.shouldProcess(fullContent)) {
                val parsed = Parser.parse(fullContent)
                if (parsed != null) {
                    Log.d("NotificationService", "Parsed valid data: ${parsed.incidentId}")
                    scope.launch {
                        NetworkClient.sendData(this@NotificationService, parsed)
                    }
                }
            } else {
                Log.d("NotificationService", "Duplicate ignored: $title")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Ignore
    }
}
