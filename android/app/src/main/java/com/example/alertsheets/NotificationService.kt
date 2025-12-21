package com.example.alertsheets

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class NotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var sourceManager: SourceManager

    override fun onCreate() {
        super.onCreate()
        sourceManager = SourceManager(this)
        createNotificationChannel()
        
        // V2 Migration: Run once
        MigrationManager.migrateIfNeeded(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Run as Foreground Service to prevent system killing
        val notification =
                androidx.core.app.NotificationCompat.Builder(this, "service_channel")
                        .setContentTitle("AlertsToSheets Service")
                        .setContentText("Monitoring notifications in the background")
                        .setSmallIcon(
                                R.drawable.ic_launcher_foreground
                        ) // Ensure this icon exists or use android.R.drawable.ic_dialog_info
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                        .build()

        // Service ID 101 for the foreground notification
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                        101,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(101, notification)
            }
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
            val channel =
                    android.app.NotificationChannel(
                            "service_channel",
                            "Background Monitoring",
                            android.app.NotificationManager.IMPORTANCE_LOW
                    )
            channel.description = "Keeps the monitoring service alive"
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 0. Master Switch Check (V2: Always on, but we'll keep check for now)
        if (!PrefsManager.getMasterEnabled(this)) {
            Log.d("NotificationService", "Master Switch OFF. Ignoring notification.")
            return
        }

        val extras = sbn.notification.extras
        // Get the title and text content
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullContent = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString("\n")

        Log.d("NotificationService", "Received: $fullContent")

        // ✅ V2: Check if source is enabled
        val source = sourceManager.getSourcesByType(SourceType.APP)
            .find { it.id == sbn.packageName && it.enabled }
        
        if (source == null) {
            // App not monitored or disabled
            LogRepository.addLog(
                    LogEntry(
                            packageName = sbn.packageName,
                            title = title,
                            content = "Source not enabled",
                            status = LogStatus.IGNORED,
                            rawJson = "No active source for ${sbn.packageName}"
                    )
            )
            return
        }

        // ✅ V2: Per-source auto-clean
        val shouldClean = source.autoClean
        val finalTitle = if (shouldClean) TemplateEngine.cleanText(title) else title
        val finalText = if (shouldClean) TemplateEngine.cleanText(text) else text
        val finalBigText = if (shouldClean) TemplateEngine.cleanText(bigText) else bigText

        // 2. Detect BNN vs Generic (based on parser)
        val isBnnNotification = source.parserId == "bnn"

        var jsonToSend: String? = null
        var logContent = text

        if (isBnnNotification) {
            // BNN Parsing Pipeline
            Log.d("NotificationService", "BNN Detected. Package: ${sbn.packageName}, Content: ${fullContent.take(200)}")
            
            if (DeDuplicator.shouldProcess(fullContent)) {
                val parsed = Parser.parse(fullContent)

                if (parsed != null) {
                    // Update timestamp
                    val timestamped =
                            parsed.copy(
                                    timestamp = TemplateEngine.getTimestamp(),
                                    originalBody =
                                            if (shouldClean) TemplateEngine.cleanText(fullContent)
                                            else fullContent
                            )

                    // Canonical JSON for Apps Script (Matches parsing.md)
                    jsonToSend = com.google.gson.Gson().toJson(timestamped)

                    // Log the "Details" part for readability in app
                    logContent = parsed.incidentDetails.ifEmpty { parsed.incidentType }
                    Log.i("NotificationService", "✓ BNN Parsed & Sent: ID=${parsed.incidentId}, State=${parsed.state}, City=${parsed.city}")
                    Log.d("NotificationService", "JSON: ${jsonToSend?.take(300)}")
                } else {
                    Log.e(
                            "NotificationService",
                            "✗ BNN PARSE FAILED! Using generic template. Content: ${fullContent.take(200)}"
                    )
                    // Fallback to generic template for this source
                    val template = PrefsManager.getTemplateById(this, source.templateId)
                            ?: PrefsManager.getAppJsonTemplate(this)
                    jsonToSend =
                            TemplateEngine.applyGeneric(
                                    template,
                                    sbn.packageName,
                                    finalTitle,
                                    finalText,
                                    finalBigText
                            )
                    Log.w("NotificationService", "Generic fallback JSON: ${jsonToSend?.take(200)}")
                }
            } else {
                LogRepository.addLog(
                        LogEntry(
                                packageName = sbn.packageName,
                                title = finalTitle,
                                content = "Duplicate blocked",
                                status = LogStatus.IGNORED,
                                rawJson = fullContent
                        )
                )
                return
            }
        } else {
            // ✅ V2: Generic Pipeline with Source template
            if (DeDuplicator.shouldProcess(fullContent)) {
                val template = PrefsManager.getTemplateById(this, source.templateId)
                        ?: PrefsManager.getAppJsonTemplate(this)
                jsonToSend =
                        TemplateEngine.applyGeneric(
                                template,
                                sbn.packageName,
                                finalTitle,
                                finalText,
                                finalBigText
                        )
            } else {
                LogRepository.addLog(
                        LogEntry(
                                packageName = sbn.packageName,
                                title = finalTitle,
                                content = "Duplicate blocked",
                                status = LogStatus.IGNORED,
                                rawJson = fullContent
                        )
                )
                return
            }
        }

        if (jsonToSend != null) {
            val entry =
                    LogEntry(
                            packageName = sbn.packageName,
                            title = finalTitle,
                            content = logContent,
                            status = LogStatus.PENDING,
                            rawJson = jsonToSend
                    )
            LogRepository.addLog(entry)

            val endpoints = PrefsManager.getEndpoints(this)
            if (endpoints.isNotEmpty()) {
                endpoints.forEach { ep ->
                    QueueProcessor.enqueue(this, ep.url, jsonToSend, entry.id)
                }
                // We keep it PENDING in LogRepository until functionality to link queue to log is
                // built.
            } else {
                LogRepository.updateStatus(entry.id, LogStatus.FAILED)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Ignore
    }
}
