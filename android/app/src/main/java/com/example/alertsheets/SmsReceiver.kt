package com.example.alertsheets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sb = StringBuilder()
            var sender = ""
            var timestamp = 0L

            for (msg in messages) {
                if (sender.isEmpty()) {
                    sender = msg.originatingAddress ?: "Unknown"
                    timestamp = msg.timestampMillis
                }
                sb.append(msg.messageBody)
            }

            val fullBody = sb.toString()
            Log.d("SmsReceiver", "Received SMS from $sender: $fullBody")

            // 1. Check Filters (SMS Targets)
            // If targets list is empty, we accept ALL? Or None?
            // Usually explicit allow-list. 
            // BUT for this user "I need to send this sms", likely implies specific monitoring.
            // Let's assume if SMS Targets list is NOT empty, we filter. If empty, maybe ignore?
            // Actually, for now, let's log it and check Prefs.
            
            val smsTargets = PrefsManager.getSmsTargets(context)
            // Logic: If list is empty -> Monitor ALL? Or Monitor None? 
            // In NotificationService, empty list = Monitor None (if filter checked).
            // Let's adopt: Empty = Monitor None (safety).
            // BUT user might just want to monitor all.
            // Let's check if the sender is in the list.
            
            // Clean sender number format if needed (strip +1, etc) to match?
            // relaxing for now: contains check
            
            val isMonitored = smsTargets.isEmpty() || smsTargets.any { sender.contains(it) }
            
            if (!isMonitored) {
                 LogRepository.addLog(LogEntry(
                    packageName = "com.android.sms", // Virtual package for logs
                    title = sender,
                    content = "SMS Filtered Out",
                    status = LogStatus.IGNORED,
                    rawJson = "Sender not in target list"
                ))
                return
            }

            // 2. Prepare Data
            val shouldClean = PrefsManager.getShouldCleanData(context)
            val finalBody = if (shouldClean) TemplateEngine.cleanText(fullBody) else fullBody
            val finalSender = if (shouldClean) TemplateEngine.cleanText(sender) else sender
            
            // 3. Template
            val template = PrefsManager.getJsonTemplate(context)
            
            // 4. Transform unique to SMS
            // We reuse applyGeneric but mapped for SMS
            // We treat 'pkg' as 'sms', 'title' as 'sender', 'text' as 'body'
            val json = TemplateEngine.applyGeneric(
                template = template,
                pkg = "sms",
                title = finalSender,
                text = finalBody,
                bigText = ""
            )
            
            // 5. Log & Send
            val entry = LogEntry(
                packageName = "SMS:$sender",
                title = sender,
                content = finalBody,
                status = LogStatus.PENDING,
                rawJson = json
            )
            LogRepository.addLog(entry)
            
            scope.launch {
                val success = NetworkClient.sendJson(context, json)
                val status = if(success) LogStatus.SENT else LogStatus.FAILED
                LogRepository.updateStatus(entry.id, status)
            }
        }
    }
}
