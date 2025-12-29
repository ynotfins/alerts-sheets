package com.example.alertsheets.domain

import android.content.Context
import android.util.Log
import com.example.alertsheets.data.ReliableHttpSender
import com.example.alertsheets.data.repositories.EndpointRepository
import com.example.alertsheets.data.repositories.SourceRepository
import com.example.alertsheets.domain.models.Endpoint
import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceType
import com.example.alertsheets.utils.DeliveryLogBuffer
import com.example.alertsheets.utils.SmsSenderNormalizer
import com.example.alertsheets.utils.StructuredLogger
import com.example.alertsheets.utils.TemplateEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Golden Path: Single authoritative delivery service
 * 
 * ALL sends (SMS, notifications, dirty test) MUST route through this pipeline
 * 
 * Flow:
 * 1. Normalize sender/input
 * 2. Match source (SMS type only for now)
 * 3. Pick ONE endpoint (first enabled endpoint for Golden Path v1)
 * 4. Render JSON payload using source's templateJson
 * 5. Call ReliableHttpSender.postJson()
 * 6. Emit structured logs + append to DeliveryLogBuffer
 * 
 * RULES:
 * - SENT means HTTP attempt happened and outcome known
 * - No fake "SENT" states allowed
 * - Every step logged with StructuredLogger
 * - NO PII in logs (use sender shape: digitsLen + hasPlus + last2)
 */
object DeliveryPipeline {
    
    private val TAG = "DeliveryPipeline"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    
    /**
     * Deliver SMS event (Golden Path v1)
     */
    fun deliverSmsEvent(context: Context, senderRaw: String, message: String, timestamp: Long = System.currentTimeMillis()) {
        scope.launch {
            val alertId = "alert_${System.currentTimeMillis()}"
            val startTime = System.currentTimeMillis()
            
            Log.d(TAG, "📥 SMS event received | alertId=$alertId")
            
            // Step 1: Normalize sender (digits only)
            val senderNormalized = SmsSenderNormalizer.normalize(senderRaw)
            val senderShape = getSenderShape(senderRaw)
            
            Log.d(TAG, "Normalized sender: $senderShape (${senderNormalized.length} digits)")
            
            // Step 2: Match source (SMS type)
            val sourceRepo = SourceRepository(context)
            val source = sourceRepo.findBySender(senderRaw)
            
            if (source == null) {
                // No matching source - log and fail
                val reason = "no_matching_source"
                Log.w(TAG, "❌ SMS ignored: $reason | $senderShape")
                
                StructuredLogger.logEvent(
                    level = "INFO",
                    sourceId = null,
                    endpointId = null,
                    alertId = alertId,
                    event = "source_ignored",
                    details = "reason=$reason type=SMS $senderShape"
                )
                
                DeliveryLogBuffer.append(
                    DeliveryLogBuffer.DeliveryLogEntry(
                        timestamp = System.currentTimeMillis(),
                        alertId = alertId,
                        sourceId = null,
                        endpointId = null,
                        event = "source_ignored",
                        httpCode = null,
                        latencyMs = null,
                        errorClass = "NoMatchingSource",
                        errorMessage = "No configured source for $senderShape",
                        details = reason
                    )
                )
                return@launch
            }
            
            if (!source.enabled) {
                Log.w(TAG, "❌ Source disabled: ${source.name}")
                
                StructuredLogger.logEvent(
                    level = "INFO",
                    sourceId = source.id,
                    endpointId = null,
                    alertId = alertId,
                    event = "source_disabled",
                    details = "sourceName=${source.name}"
                )
                
                DeliveryLogBuffer.append(
                    DeliveryLogBuffer.DeliveryLogEntry(
                        timestamp = System.currentTimeMillis(),
                        alertId = alertId,
                        sourceId = source.id,
                        endpointId = null,
                        event = "source_disabled",
                        httpCode = null,
                        latencyMs = null,
                        errorClass = "SourceDisabled",
                        errorMessage = "Source ${source.name} is disabled",
                        details = null
                    )
                )
                return@launch
            }
            
            Log.d(TAG, "✓ Source matched: ${source.name} (${source.id})")
            
            StructuredLogger.logEvent(
                level = "INFO",
                sourceId = source.id,
                endpointId = null,
                alertId = alertId,
                event = "source_match_ok",
                details = "sourceName=${source.name}"
            )
            
            // Step 3: Pick ONE endpoint (Golden Path: first enabled endpoint)
            val endpointRepo = EndpointRepository(context)
            val endpoint = source.endpointIds
                .firstNotNullOfOrNull { endpointId ->
                    endpointRepo.getById(endpointId)?.takeIf { it.enabled }
                }
            
            if (endpoint == null) {
                Log.e(TAG, "❌ No enabled endpoint for source: ${source.name}")
                
                StructuredLogger.logEvent(
                    level = "ERROR",
                    sourceId = source.id,
                    endpointId = null,
                    alertId = alertId,
                    event = "no_endpoint",
                    details = "endpointIds=${source.endpointIds.joinToString(",")}"
                )
                
                DeliveryLogBuffer.append(
                    DeliveryLogBuffer.DeliveryLogEntry(
                        timestamp = System.currentTimeMillis(),
                        alertId = alertId,
                        sourceId = source.id,
                        endpointId = null,
                        event = "no_endpoint",
                        httpCode = null,
                        latencyMs = null,
                        errorClass = "NoEnabledEndpoint",
                        errorMessage = "No enabled endpoint configured for ${source.name}",
                        details = "endpointIds=${source.endpointIds.size}"
                    )
                )
                return@launch
            }
            
            Log.d(TAG, "✓ Endpoint selected: ${endpoint.name} (${endpoint.id})")
            
            // Step 4: Render JSON payload using source's templateJson
            val parsedData = ParsedData(
                incidentId = alertId,
                timestamp = dateFormat.format(Date(timestamp)),
                originalBody = message,
                address = message.take(100) // Use message as fallback for address field
            )
            
            val templateContent = source.templateJson
            if (templateContent.isBlank()) {
                Log.e(TAG, "❌ Source has no template JSON: ${source.name}")
                
                StructuredLogger.logEvent(
                    level = "ERROR",
                    sourceId = source.id,
                    endpointId = endpoint.id,
                    alertId = alertId,
                    event = "payload_render_fail",
                    details = "reason=no_template"
                )
                
                DeliveryLogBuffer.append(
                    DeliveryLogBuffer.DeliveryLogEntry(
                        timestamp = System.currentTimeMillis(),
                        alertId = alertId,
                        sourceId = source.id,
                        endpointId = endpoint.id,
                        event = "payload_render_fail",
                        httpCode = null,
                        latencyMs = null,
                        errorClass = "NoTemplate",
                        errorMessage = "Source ${source.name} has no template configured",
                        details = null
                    )
                )
                return@launch
            }
            
            val json = try {
                TemplateEngine.apply(templateContent, parsedData, source)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Template rendering failed: ${e.message}", e)
                
                StructuredLogger.logEvent(
                    level = "ERROR",
                    sourceId = source.id,
                    endpointId = endpoint.id,
                    alertId = alertId,
                    event = "payload_render_fail",
                    details = "error=${e.message}"
                )
                
                DeliveryLogBuffer.append(
                    DeliveryLogBuffer.DeliveryLogEntry(
                        timestamp = System.currentTimeMillis(),
                        alertId = alertId,
                        sourceId = source.id,
                        endpointId = endpoint.id,
                        event = "payload_render_fail",
                        httpCode = null,
                        latencyMs = null,
                        errorClass = e.javaClass.simpleName,
                        errorMessage = e.message ?: "Template rendering error",
                        details = null
                    )
                )
                return@launch
            }
            
            Log.d(TAG, "✓ Payload rendered (${json.length} chars)")
            
            StructuredLogger.logEvent(
                level = "INFO",
                sourceId = source.id,
                endpointId = endpoint.id,
                alertId = alertId,
                event = "payload_render_ok",
                details = "payloadSize=${json.length}"
            )
            
            // Step 5: Prepare headers (including auth if needed)
            val headers = endpoint.headers.toMutableMap()
            
            // ✅ Add shared secret Authorization header for Firestore ingest endpoints
            // Detection: URL contains "cloudfunctions.net/ingest" OR name contains "Firestore Ingest"
            // Only add if Authorization not already present (endpoint-level override wins)
            val needsAuth = endpoint.url.contains("cloudfunctions.net/ingest", ignoreCase = true) ||
                           endpoint.name.contains("Firestore Ingest", ignoreCase = true)
            
            if (needsAuth && !headers.containsKey("Authorization")) {
                val secret = com.example.alertsheets.BuildConfig.INGEST_SHARED_SECRET
                
                if (secret.isNotEmpty()) {
                    headers["Authorization"] = "Bearer $secret"
                    Log.d(TAG, "✓ Authorization header added for ingest endpoint (secret length=${secret.length})")
                } else {
                    // ❌ Secret missing - fail early with clear error
                    Log.e(TAG, "❌ INGEST_SHARED_SECRET is empty! Cannot authenticate to Firestore ingest endpoint.")
                    
                    StructuredLogger.logEvent(
                        level = "ERROR",
                        sourceId = source.id,
                        endpointId = endpoint.id,
                        alertId = alertId,
                        event = "auth_missing",
                        details = "INGEST_SHARED_SECRET not configured in local.properties"
                    )
                    
                    DeliveryLogBuffer.append(
                        DeliveryLogBuffer.DeliveryLogEntry(
                            timestamp = System.currentTimeMillis(),
                            alertId = alertId,
                            sourceId = source.id,
                            endpointId = endpoint.id,
                            event = "auth_missing",
                            httpCode = null,
                            latencyMs = null,
                            errorClass = "ConfigError",
                            errorMessage = "INGEST_SHARED_SECRET not configured",
                            details = "Add INGEST_SHARED_SECRET to android/local.properties"
                        )
                    )
                    return@launch
                }
            } else if (needsAuth && headers.containsKey("Authorization")) {
                Log.d(TAG, "ℹ️ Endpoint already has Authorization header (endpoint-level override)")
            } else {
                Log.d(TAG, "Endpoint does not require auth (Apps Script or other)")
            }
            
            // Step 6: Call ReliableHttpSender.postJson()
            Log.d(TAG, "📤 Sending to: ${endpoint.name} (${endpoint.url.take(50)}...)")
            
            StructuredLogger.logEvent(
                level = "INFO",
                sourceId = source.id,
                endpointId = endpoint.id,
                alertId = alertId,
                event = "http_attempt",
                details = "endpointName=${endpoint.name} authRequired=$needsAuth"
            )
            
            DeliveryLogBuffer.append(
                DeliveryLogBuffer.DeliveryLogEntry(
                    timestamp = System.currentTimeMillis(),
                    alertId = alertId,
                    sourceId = source.id,
                    endpointId = endpoint.id,
                    event = "http_attempt",
                    httpCode = null,
                    latencyMs = null,
                    errorClass = null,
                    errorMessage = null,
                    details = "endpoint=${endpoint.name}"
                )
            )
            
            val httpStartTime = System.currentTimeMillis()
            val result = ReliableHttpSender.postJson(
                endpointUrl = endpoint.url,
                headers = headers,
                bodyJson = json
            )
            val totalLatency = System.currentTimeMillis() - startTime
            
            // Step 7: Emit structured logs + append to DeliveryLogBuffer
            if (result.success) {
                Log.i(TAG, "✅ HTTP OK | code=${result.httpCode} latency=${result.latencyMs}ms")
                
                StructuredLogger.logHttpOk(
                    sourceId = source.id,
                    endpointId = endpoint.id,
                    alertId = alertId,
                    httpCode = result.httpCode,
                    latency = result.latencyMs
                )
                
                DeliveryLogBuffer.append(
                    DeliveryLogBuffer.DeliveryLogEntry(
                        timestamp = System.currentTimeMillis(),
                        alertId = alertId,
                        sourceId = source.id,
                        endpointId = endpoint.id,
                        event = "http_ok",
                        httpCode = result.httpCode,
                        latencyMs = result.latencyMs,
                        errorClass = null,
                        errorMessage = null,
                        details = "totalLatency=${totalLatency}ms response=${result.responseBody?.take(120) ?: ""}"  // ✅ Add response snippet
                    )
                )
                
            } else {
                Log.e(TAG, "❌ HTTP FAIL | code=${result.httpCode} error=${result.errorClass}: ${result.errorMessage}")
                
                StructuredLogger.logHttpFail(
                    sourceId = source.id,
                    endpointId = endpoint.id,
                    alertId = alertId,
                    httpCode = result.httpCode,
                    error = "${result.errorClass}: ${result.errorMessage}"
                )
                
                DeliveryLogBuffer.append(
                    DeliveryLogBuffer.DeliveryLogEntry(
                        timestamp = System.currentTimeMillis(),
                        alertId = alertId,
                        sourceId = source.id,
                        endpointId = endpoint.id,
                        event = "http_fail",
                        httpCode = result.httpCode,
                        latencyMs = result.latencyMs,
                        errorClass = result.errorClass,
                        errorMessage = result.errorMessage,
                        details = "totalLatency=${totalLatency}ms response=${result.responseBody?.take(120) ?: ""}"  // ✅ Add response snippet
                    )
                )
            }
        }
    }
    
    /**
     * Deliver test event (for Dirty Test button)
     * Uses same pipeline as real SMS
     */
    fun deliverTestEvent(context: Context, endpointUrl: String, bodyJson: String) {
        scope.launch {
            val alertId = "test_${System.currentTimeMillis()}"
            val startTime = System.currentTimeMillis()
            
            Log.d(TAG, "🧪 Test event | alertId=$alertId")
            
            StructuredLogger.logEvent(
                level = "INFO",
                sourceId = "manual_test",
                endpointId = "test_endpoint",
                alertId = alertId,
                event = "test_attempt_started",
                details = "url=${endpointUrl.take(50)}"
            )
            
            DeliveryLogBuffer.append(
                DeliveryLogBuffer.DeliveryLogEntry(
                    timestamp = System.currentTimeMillis(),
                    alertId = alertId,
                    sourceId = "manual_test",
                    endpointId = "test_endpoint",
                    event = "test_attempt_started",
                    httpCode = null,
                    latencyMs = null,
                    errorClass = null,
                    errorMessage = null,
                    details = "url=${endpointUrl.take(50)}"
                )
            )
            
            val result = ReliableHttpSender.postJson(
                endpointUrl = endpointUrl,
                headers = emptyMap(),
                bodyJson = bodyJson
            )
            
            val totalLatency = System.currentTimeMillis() - startTime
            
            if (result.success) {
                // ✅ Check if response contains confirmation
                val responseBody = result.responseBody ?: ""
                val isConfirmed = responseBody.contains("\"ok\":true", ignoreCase = true) ||
                                  responseBody.contains("\"success\":true", ignoreCase = true) ||
                                  responseBody.contains("\"saved\":true", ignoreCase = true)
                
                val event = if (isConfirmed) "test_http_ok_confirmed" else "test_http_ok_unconfirmed"
                val details = "code=${result.httpCode} latency=${result.latencyMs}ms confirmed=$isConfirmed body=${responseBody.take(300)}"
                
                Log.i(TAG, "✅ Test HTTP OK ($event) | code=${result.httpCode} latency=${result.latencyMs}ms | body: ${responseBody.take(100)}")
                
                StructuredLogger.logEvent(
                    level = "INFO",
                    sourceId = "manual_test",
                    endpointId = "test_endpoint",
                    alertId = alertId,
                    event = event,
                    details = details
                )
                
                DeliveryLogBuffer.append(
                    DeliveryLogBuffer.DeliveryLogEntry(
                        timestamp = System.currentTimeMillis(),
                        alertId = alertId,
                        sourceId = "manual_test",
                        endpointId = "test_endpoint",
                        event = event,
                        httpCode = result.httpCode,
                        latencyMs = result.latencyMs,
                        errorClass = null,
                        errorMessage = null,
                        details = details
                    )
                )
                
            } else {
                val details = "code=${result.httpCode} error=${result.errorClass}: ${result.errorMessage} body=${result.responseBody?.take(300)}"
                Log.e(TAG, "❌ Test HTTP FAIL | $details")
                
                StructuredLogger.logEvent(
                    level = "ERROR",
                    sourceId = "manual_test",
                    endpointId = "test_endpoint",
                    alertId = alertId,
                    event = "test_http_fail",
                    details = details
                )
                
                DeliveryLogBuffer.append(
                    DeliveryLogBuffer.DeliveryLogEntry(
                        timestamp = System.currentTimeMillis(),
                        alertId = alertId,
                        sourceId = "manual_test",
                        endpointId = "test_endpoint",
                        event = "test_http_fail",
                        httpCode = result.httpCode,
                        latencyMs = result.latencyMs,
                        errorClass = result.errorClass,
                        errorMessage = result.errorMessage,
                        details = details
                    )
                )
            }
        }
    }
    
    /**
     * Deliver test event with full Endpoint support (for auth tokens)
     * SYNCHRONOUS - returns result for immediate UI feedback
     * 
     * @param endpoint Full endpoint object (includes authType, headers, etc.)
     * @param bodyJson Pre-rendered JSON payload
     * @return HttpResult for Toast display
     */
    suspend fun deliverTestEventWithAuth(endpoint: Endpoint, bodyJson: String): ReliableHttpSender.HttpResult {
        val alertId = "test_${System.currentTimeMillis()}"
        val startTime = System.currentTimeMillis()
        
        Log.d(TAG, "🧪 Test event with auth | alertId=$alertId endpoint=${endpoint.name}")
        
        StructuredLogger.logEvent(
            level = "INFO",
            sourceId = "manual_test",
            endpointId = endpoint.id,
            alertId = alertId,
            event = "test_attempt_started",
            details = "url=${endpoint.url.take(50)} authType=${endpoint.authType}"
        )
        
        DeliveryLogBuffer.append(
            DeliveryLogBuffer.DeliveryLogEntry(
                timestamp = System.currentTimeMillis(),
                alertId = alertId,
                sourceId = "manual_test",
                endpointId = endpoint.id,
                event = "test_attempt_started",
                httpCode = null,
                latencyMs = null,
                errorClass = null,
                errorMessage = null,
                details = "url=${endpoint.url.take(50)}"
            )
        )
        
        // ✅ Prepare headers (including auth if needed)
        val headers = endpoint.headers.toMutableMap()
        
        // ✅ Add shared secret Authorization header for Firestore ingest endpoints
        val needsAuth = endpoint.url.contains("cloudfunctions.net/ingest", ignoreCase = true) ||
                       endpoint.name.contains("Firestore Ingest", ignoreCase = true)
        
        if (needsAuth && !headers.containsKey("Authorization")) {
            val secret = com.example.alertsheets.BuildConfig.INGEST_SHARED_SECRET
            
            if (secret.isNotEmpty()) {
                headers["Authorization"] = "Bearer $secret"
                Log.d(TAG, "✓ Test: Authorization header added (secret length=${secret.length})")
            } else {
                Log.e(TAG, "❌ Test: INGEST_SHARED_SECRET is empty! Cannot authenticate.")
            }
        } else if (needsAuth && headers.containsKey("Authorization")) {
            Log.d(TAG, "ℹ️ Test: Endpoint already has Authorization header (override)")
        } else {
            Log.d(TAG, "Test: Endpoint does not require auth")
        }
        
        val result = ReliableHttpSender.postJson(
            endpointUrl = endpoint.url,
            headers = headers,
            bodyJson = bodyJson
        )
        
        val totalLatency = System.currentTimeMillis() - startTime
        
        if (result.success) {
            // ✅ Check if response contains confirmation
            val responseBody = result.responseBody ?: ""
            val isConfirmed = responseBody.contains("\"ok\":true", ignoreCase = true) ||
                              responseBody.contains("\"success\":true", ignoreCase = true) ||
                              responseBody.contains("\"saved\":true", ignoreCase = true)
            
            val event = if (isConfirmed) "test_http_ok_confirmed" else "test_http_ok_unconfirmed"
            val details = "code=${result.httpCode} latency=${result.latencyMs}ms confirmed=$isConfirmed body=${responseBody.take(300)}"
            
            Log.i(TAG, "✅ Test HTTP OK ($event) | code=${result.httpCode} latency=${result.latencyMs}ms | body: ${responseBody.take(100)}")
            
            StructuredLogger.logEvent(
                level = "INFO",
                sourceId = "manual_test",
                endpointId = endpoint.id,
                alertId = alertId,
                event = event,
                details = details
            )
            
            DeliveryLogBuffer.append(
                DeliveryLogBuffer.DeliveryLogEntry(
                    timestamp = System.currentTimeMillis(),
                    alertId = alertId,
                    sourceId = "manual_test",
                    endpointId = endpoint.id,
                    event = event,
                    httpCode = result.httpCode,
                    latencyMs = result.latencyMs,
                    errorClass = null,
                    errorMessage = null,
                    details = details
                )
            )
            
        } else {
            val details = "code=${result.httpCode} error=${result.errorClass}: ${result.errorMessage} body=${result.responseBody?.take(300)}"
            Log.e(TAG, "❌ Test HTTP FAIL | $details")
            
            StructuredLogger.logEvent(
                level = "ERROR",
                sourceId = "manual_test",
                endpointId = endpoint.id,
                alertId = alertId,
                event = "test_http_fail",
                details = details
            )
            
            DeliveryLogBuffer.append(
                DeliveryLogBuffer.DeliveryLogEntry(
                    timestamp = System.currentTimeMillis(),
                    alertId = alertId,
                    sourceId = "manual_test",
                    endpointId = endpoint.id,
                    event = "test_http_fail",
                    httpCode = result.httpCode,
                    latencyMs = result.latencyMs,
                    errorClass = result.errorClass,
                    errorMessage = result.errorMessage,
                    details = details
                )
            )
        }
        
        return result
    }
    
    /**
     * Get sender shape for safe logging (NO PII)
     * Returns: "hasPlus=true digitsLen=11 last2=55"
     */
    private fun getSenderShape(sender: String): String {
        val hasPlus = sender.startsWith("+")
        val digits = sender.filter { it.isDigit() }
        val last2 = if (digits.length >= 2) digits.takeLast(2) else "N/A"
        return "hasPlus=$hasPlus digitsLen=${digits.length} last2=$last2"
    }
}

