package com.example.alertsheets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sb = StringBuilder()
            var sender = ""

            for (msg in messages) {
                if (sender.isEmpty()) {
                    sender = msg.originatingAddress ?: "Unknown"
                }
                sb.append(msg.messageBody)
            }

            val fullBody = sb.toString()
            Log.d("SmsReceiver", "Received SMS from $sender: $fullBody")

            // ✅ V2: Get enabled SMS sources
            val sourceManager = SourceManager(context)
            val smsSources = sourceManager.getSourcesByType(SourceType.SMS).filter { it.enabled }
            
            // If no sources configured, block
            if (smsSources.isEmpty()) {
                 LogRepository.addLog(LogEntry(
                    packageName = "com.android.sms", 
                    title = sender,
                    content = "SMS Ignored (No Sources Configured)",
                    status = LogStatus.IGNORED,
                    rawJson = "Go to SMS Config to add senders."
                ))
                return
            }

            // 2. Find Matching Source
            // Normalize sender for comparison: remove all non-digits
            val senderDigits = sender.filter { it.isDigit() }
            
            val matchedSource = smsSources.find { source ->
                // Extract phone number from ID (format: "sms:+15551234567")
                val sourcePhone = source.id.removePrefix("sms:")
                val sourceDigits = sourcePhone.filter { it.isDigit() }
                
                // US Match Logic: Check if last 10 digits match (avoids +1 issues)
                 val senderTen = if (senderDigits.length >= 10) senderDigits.takeLast(10) else senderDigits
                 val sourceTen = if (sourceDigits.length >= 10) sourceDigits.takeLast(10) else sourceDigits
                 
                senderTen == sourceTen
            }
            
            if (matchedSource == null) {
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
            
            // 3. Check Content Filter (stored in extras prefs)
            val extras = context.getSharedPreferences("source_extras", Context.MODE_PRIVATE)
            val filterText = extras.getString("${matchedSource.id}:filterText", null)
            
            if (!filterText.isNullOrEmpty()) {
                val isCaseSensitive = extras.getBoolean("${matchedSource.id}:isCaseSensitive", false)
                val passes = if (isCaseSensitive) {
                    fullBody.contains(filterText)
                } else {
                    fullBody.contains(filterText, ignoreCase = true)
                }
                
                if (!passes) {
                     LogRepository.addLog(LogEntry(
                        packageName = "com.android.sms", 
                        title = "${matchedSource.name} ($sender)",
                        content = "SMS Filtered (Content Mismatch)",
                        status = LogStatus.IGNORED,
                        rawJson = "Body did not contain '$filterText'"
                    ))
                    return
                }
            }

            // 4. ✅ V2: Per-source auto-clean
            val shouldClean = matchedSource.autoClean
            val finalBody = if (shouldClean) TemplateEngine.cleanText(fullBody) else fullBody
            val finalSender = if (shouldClean) TemplateEngine.cleanText(sender) else sender
            
            // 5. ✅ V2: Get template from source
            val template = PrefsManager.getTemplateById(context, matchedSource.templateId)
                    ?: PrefsManager.getSmsJsonTemplate(context)
            
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
                packageName = "SMS:${matchedSource.name}",
                title = sender,
                content = finalBody,
                status = LogStatus.PENDING,
                rawJson = json
            )
            LogRepository.addLog(entry)
            
            val endpoints = PrefsManager.getEndpoints(context)
            if (endpoints.isNotEmpty()) {
                endpoints.forEach { ep ->
                    QueueProcessor.enqueue(context, ep.url, json)
                }
            } else {
                LogRepository.updateStatus(entry.id, LogStatus.FAILED)
            }
        }
    }
}
