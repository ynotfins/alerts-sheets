package com.example.alertsheets.domain

import android.content.Context
import android.util.Log
import com.example.alertsheets.BuildConfig
import com.example.alertsheets.data.IngestQueue
import com.example.alertsheets.data.repositories.EndpointRepository
import com.example.alertsheets.data.repositories.TemplateRepository
import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.RawNotification
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.parsers.ParserRegistry
import com.example.alertsheets.utils.TemplateEngine
import com.example.alertsheets.utils.HttpClient
import com.example.alertsheets.utils.Logger
import com.example.alertsheets.LogRepository
import com.example.alertsheets.LogEntry
import com.example.alertsheets.LogStatus
import com.example.alertsheets.utils.PayloadSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Clean Data Pipeline for V2
 * 
 * Orchestrates the complete flow from raw notification to sent payload:
 * 1. Capture (done by services)
 * 2. Identify Source
 * 3. Parse
 * 4. Transform (apply template)
 * 5. Send (HTTP POST)
 * 6. Log
 * 7. Update Stats
 * 
 * All steps are logged for debugging
 * Failures are retried
 * Offline queue is supported
 */
class DataPipeline(private val context: Context) {
    
    private val sourceManager = SourceManager(context)
    private val templateRepo = TemplateRepository(context)
    private val endpointRepo = EndpointRepository(context)
    private val httpClient = HttpClient()
    private val logger = Logger(context)
    private val ingestQueue by lazy { IngestQueue(context) }  // ✅ Lazy init for Firestore ingest
    
    private val TAG = "DataPipeline"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Process a notification through the entire pipeline
     * This is the main entry point
     */
    fun process(source: Source, raw: RawNotification) {
        scope.launch {
            // ✅ CRITICAL: Create LogEntry FIRST so Activity Logs UI can show it
            val logEntry = LogEntry(
                packageName = raw.packageName,
                title = raw.title,
                content = raw.text.take(200), // Truncate for readability
                status = LogStatus.PENDING,
                rawJson = PayloadSerializer.toJson(raw)
            )
            LogRepository.addLog(logEntry)
            Log.v("Pipe", "Log entry created: ${logEntry.id}")
            
            try {
                logger.log("📥 Processing: ${raw.packageName} - ${raw.title}")
                
                // Step 1: Get parser
                val parser = ParserRegistry.get(source.parserId)
                if (parser == null) {
                    logger.error("❌ No parser found: ${source.parserId}")
                    sourceManager.recordNotificationProcessed(source.id, success = false)
                    return@launch
                }
                
                // Step 2: Parse
                val parsed = parser.parse(raw)
                if (parsed == null) {
                    logger.error("❌ Parse failed: ${source.name}")
                    LogRepository.updateStatus(logEntry.id, LogStatus.FAILED)
                    sourceManager.recordNotificationProcessed(source.id, success = false)
                    return@launch
                }
                
                // Step 3: Add timestamp
                val parsedWithTimestamp = parsed.copy(timestamp = TemplateEngine.getTimestamp())
                logger.log("✓ Parsed: ${parsedWithTimestamp.incidentId}")
                LogRepository.updateStatus(logEntry.id, LogStatus.PROCESSING)
                Log.v("Pipe", "Log entry updated to PROCESSING: ${logEntry.id}")
                
                // Step 4: Get template JSON from source (NOT from shared template repo!)
                val templateContent = sourceManager.getTemplateJsonForSource(source)
                if (templateContent.isEmpty()) {
                    logger.error("❌ Source has no template JSON: ${source.name}")
                    LogRepository.updateStatus(logEntry.id, LogStatus.FAILED)
                    sourceManager.recordNotificationProcessed(source.id, success = false)
                    return@launch
                }
                
                // Step 5: Apply template (with per-source auto-clean!)
                val json = TemplateEngine.apply(templateContent, parsedWithTimestamp, source)
                logger.log("✓ Template applied (autoClean=${source.autoClean})")
                
                // Step 5.5: Enqueue to Firestore (DUAL-WRITE, NON-BLOCKING)
                // ⚠️ CRITICAL: This MUST NOT block Apps Script delivery
                if (BuildConfig.ENABLE_FIRESTORE_INGEST && source.enableFirestoreIngest) {
                    try {
                        val timestamp = try {
                            Instant.now().toString()
                        } catch (e: Exception) {
                            // Fallback if Instant not available (older Android)
                            System.currentTimeMillis().toString()
                        }
                        
                        ingestQueue.enqueue(
                            sourceId = source.id,
                            payload = json,
                            timestamp = timestamp
                        )
                        logger.log("📤 Enqueued to Firestore: ${source.name}")
                        Log.d(TAG, "✅ Firestore enqueue success for ${source.id}")
                    } catch (e: Exception) {
                        // ❌ CRITICAL: Firestore failure MUST NOT block delivery
                        logger.error("⚠️ Firestore enqueue failed (non-fatal): ${e.message}")
                        Log.w(TAG, "⚠️ Firestore enqueue failed (continuing with Apps Script)", e)
                    }
                }
                
                // Step 6: Get ALL endpoints for this source (fan-out delivery)
                val endpoints = source.endpointIds
                    .mapNotNull { endpointRepo.getById(it) }
                    .filter { it.enabled }
                
                if (endpoints.isEmpty()) {
                    logger.error("❌ No valid endpoints configured for: ${source.name}")
                    LogRepository.updateStatus(logEntry.id, LogStatus.FAILED)
                    sourceManager.recordNotificationProcessed(source.id, success = false)
                    return@launch
                }
                
                logger.log("📤 Delivering to ${endpoints.size} endpoint(s)")
                
                // Step 7: Fan-out delivery to ALL endpoints
                var anySuccess = false
                var allSuccess = true
                
                for (endpoint in endpoints) {
                    try {
                        val startTime = System.currentTimeMillis()
                        val response = httpClient.post(
                            url = endpoint.url,
                            body = json,
                            headers = endpoint.headers,
                            timeout = endpoint.timeout
                        )
                        val responseTime = System.currentTimeMillis() - startTime
                        
                        if (response.isSuccess) {
                            logger.log("✓ Sent to ${endpoint.name}: ${response.code}")
                            endpointRepo.updateStats(endpoint.id, success = true, responseTime)
                            anySuccess = true
                        } else {
                            logger.error("❌ Failed ${endpoint.name}: ${response.code} - ${response.message}")
                            endpointRepo.updateStats(endpoint.id, success = false, responseTime)
                            allSuccess = false
                        }
                    } catch (e: Exception) {
                        logger.error("❌ Exception ${endpoint.name}: ${e.message}")
                        endpointRepo.updateStats(endpoint.id, success = false, 0L)
                        allSuccess = false
                    }
                }
                
                // Step 8: Update overall status
                val finalStatus = when {
                    allSuccess -> LogStatus.SENT
                    anySuccess -> LogStatus.PARTIAL // Some succeeded
                    else -> LogStatus.FAILED
                }
                
                LogRepository.updateStatus(logEntry.id, finalStatus)
                Log.v("Pipe", "Log entry final status: $finalStatus for ${logEntry.id}")
                sourceManager.recordNotificationProcessed(source.id, success = anySuccess)
                
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
                logger.error("❌ Pipeline error: ${e.message}")
                LogRepository.updateStatus(logEntry.id, LogStatus.FAILED)
                Log.v("Pipe", "Log entry updated to FAILED (exception): ${logEntry.id}")
                sourceManager.recordNotificationProcessed(source.id, success = false)
            }
        }
    }
    
    /**
     * Process app notification
     */
    fun processAppNotification(packageName: String, raw: RawNotification) {
        val source = sourceManager.findSourceForNotification(packageName)
        if (source != null) {
            logger.log("📱 App: ${source.name}")
            Log.v("Pipe", "App notification from $packageName -> source ${source.name}")
            process(source, raw)
        } else {
            logger.log("⚠️ No source for: $packageName")
            Log.v("Pipe", "No source configured for $packageName, ignoring")
            // Log as IGNORED
            LogRepository.addLog(LogEntry(
                packageName = packageName,
                title = "Notification Ignored",
                content = "No source configured for this app",
                status = LogStatus.IGNORED,
                rawJson = PayloadSerializer.toJson(raw)
            ))
        }
    }
    
    /**
     * Process SMS message
     */
    fun processSms(sender: String, raw: RawNotification) {
        val source = sourceManager.findSourceForSms(sender)
        if (source != null) {
            logger.log("💬 SMS: ${source.name}")
            Log.v("Pipe", "SMS from $sender -> source ${source.name}")
            process(source, raw)
        } else {
            logger.log("⚠️ No source for SMS: $sender")
            Log.v("Pipe", "No source configured for SMS from $sender, ignoring")
            // Log as IGNORED
            LogRepository.addLog(LogEntry(
                packageName = "SMS",
                title = "SMS Ignored",
                content = "No source configured for sender: $sender",
                status = LogStatus.IGNORED,
                rawJson = PayloadSerializer.toJson(raw)
            ))
        }
    }
    
    /**
     * Cleanup
     */
    fun shutdown() {
        scope.cancel()
    }
}

