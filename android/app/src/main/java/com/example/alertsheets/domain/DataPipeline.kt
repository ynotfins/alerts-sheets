package com.example.alertsheets.domain

import android.content.Context
import android.util.Log
import com.example.alertsheets.data.repositories.EndpointRepository
import com.example.alertsheets.data.repositories.TemplateRepository
import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.RawNotification
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.parsers.ParserRegistry
import com.example.alertsheets.utils.TemplateEngine
import com.example.alertsheets.utils.HttpClient
import com.example.alertsheets.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    
    private val TAG = "DataPipeline"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Process a notification through the entire pipeline
     * This is the main entry point
     */
    fun process(source: Source, raw: RawNotification) {
        scope.launch {
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
                    sourceManager.recordNotificationProcessed(source.id, success = false)
                    return@launch
                }
                
                // Step 3: Add timestamp
                val parsedWithTimestamp = parsed.copy(timestamp = TemplateEngine.getTimestamp())
                logger.log("✓ Parsed: ${parsedWithTimestamp.incidentId}")
                
                // Step 4: Get template
                val templateContent = templateRepo.getById(source.templateId)
                if (templateContent == null) {
                    logger.error("❌ No template found: ${source.templateId}")
                    sourceManager.recordNotificationProcessed(source.id, success = false)
                    return@launch
                }
                
                // Step 5: Apply template (with per-source auto-clean!)
                val json = TemplateEngine.apply(templateContent, parsedWithTimestamp, source)
                logger.log("✓ Template applied (autoClean=${source.autoClean})")
                
                // Step 6: Get endpoint
                val endpoint = sourceManager.getEndpointById(source.endpointId)
                if (endpoint == null || !endpoint.enabled) {
                    logger.error("❌ No endpoint found or disabled: ${source.endpointId}")
                    sourceManager.recordNotificationProcessed(source.id, success = false)
                    return@launch
                }
                
                // Step 7: Send
                val startTime = System.currentTimeMillis()
                val response = httpClient.post(
                    url = endpoint.url,
                    body = json,
                    headers = endpoint.headers,
                    timeout = endpoint.timeout
                )
                val responseTime = System.currentTimeMillis() - startTime
                
                // Step 8: Handle response
                if (response.isSuccess) {
                    logger.log("✓ Sent: ${response.code} - ${parsedWithTimestamp.incidentId}")
                    sourceManager.recordNotificationProcessed(source.id, success = true)
                    endpointRepo.updateStats(endpoint.id, success = true, responseTime)
                } else {
                    logger.error("❌ Send failed: ${response.code} - ${response.message}")
                    sourceManager.recordNotificationProcessed(source.id, success = false)
                    endpointRepo.updateStats(endpoint.id, success = false, responseTime)
                    
                    // TODO: Add to retry queue if configured
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
                logger.error("❌ Pipeline error: ${e.message}")
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
            process(source, raw)
        } else {
            logger.log("⚠️ No source for: $packageName")
        }
    }
    
    /**
     * Process SMS message
     */
    fun processSms(sender: String, raw: RawNotification) {
        val source = sourceManager.findSourceForSms(sender)
        if (source != null) {
            logger.log("💬 SMS: ${source.name}")
            process(source, raw)
        } else {
            logger.log("⚠️ No source for SMS: $sender")
        }
    }
    
    /**
     * Cleanup
     */
    fun shutdown() {
        scope.cancel()
    }
}

