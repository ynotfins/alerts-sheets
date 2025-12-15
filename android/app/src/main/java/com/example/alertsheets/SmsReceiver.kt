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

            // 1. Check Config Logic
            val smsConfigs = PrefsManager.getSmsConfigList(context).filter { it.isEnabled }
            
            // If no configs, we block. We only want to send specific alerts.
            if (smsConfigs.isEmpty()) {
                 LogRepository.addLog(LogEntry(
                    packageName = "com.android.sms", 
                    title = sender,
                    content = "SMS Ignored (No Targets Configured)",
                    status = LogStatus.IGNORED,
                    rawJson = "Go to SMS Config to add numbers."
                ))
                return
            }

            // 2. Find Matching Target
            // Normalize sender for comparison: remove all non-digits
            val senderDigits = sender.filter { it.isDigit() }
            
            val match = smsConfigs.find { config ->
                // Normalize config number
                val configDigits = config.phoneNumber.filter { it.isDigit() }
                
                // US Match Logic: Check if last 10 digits match (avoids +1 issues)
                 val senderTen = if (senderDigits.length >= 10) senderDigits.takeLast(10) else senderDigits
                 val configTen = if (configDigits.length >= 10) configDigits.takeLast(10) else configDigits
                 
                senderTen == configTen
            }
            
            if (match == null) {
                // Sender not in list
                 LogRepository.addLog(LogEntry(
                    packageName = "com.android.sms", 
                    title = sender,
                    content = "SMS Filtered (Sender Not Allowed)",
                    status = LogStatus.IGNORED,
                    rawJson = "Sender $sender not in allowed list"
                ))
                return
            }
            
            // 3. Check Content Filter
            if (match.filterText.isNotEmpty()) {
                val passes = if (match.isCaseSensitive) {
                    fullBody.contains(match.filterText)
                } else {
                    fullBody.contains(match.filterText, ignoreCase = true)
                }
                
                if (!passes) {
                     LogRepository.addLog(LogEntry(
                        packageName = "com.android.sms", 
                        title = "${match.name} ($sender)",
                        content = "SMS Filtered (Content Mismatch)",
                        status = LogStatus.IGNORED,
                        rawJson = "Body did not contain '${match.filterText}'"
                    ))
                    return
                }
            }

            // 4. Prepare Data
            val shouldClean = PrefsManager.getShouldCleanData(context)
            val finalBody = if (shouldClean) TemplateEngine.cleanText(fullBody) else fullBody
            val finalSender = if (shouldClean) TemplateEngine.cleanText(sender) else sender
            
            // 5. Template
            val template = PrefsManager.getSmsJsonTemplate(context)
            
            // 6. Transform unique to SMS
            // We reuse applyGeneric but mapped for SMS
            // We treat 'pkg' as 'sms', 'title' as 'sender', 'text' as 'body'
            val json = TemplateEngine.applyGeneric(
                template = template,
                pkg = "sms",
                title = finalSender, // Use raw sender or matched name? Maybe "${match.name} ($sender)"? Let's stick to sender for now or user variable.
                text = finalBody,
                bigText = ""
            )
            
            // 7. Log & Send
            val entry = LogEntry(
                packageName = "SMS:${match.name}",
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
