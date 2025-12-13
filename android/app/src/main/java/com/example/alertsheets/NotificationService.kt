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
        if (targetApps.isNotEmpty() && !targetApps.contains(sbn.packageName)) {
            // Filter is active, and this package is NOT in the list. Ignore.
            return
        }

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
