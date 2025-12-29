package com.example.alertsheets.utils

import android.util.Log
import com.example.alertsheets.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured logging in NDJSON format
 * Each log line is a valid JSON object
 * 
 * Format: {"timestamp":"ISO8601","level":"INFO|ERROR","source_id":"","endpoint_id":"","alert_id":"","event":"","details":""}
 * 
 * NO PII is logged. All fields are system identifiers only.
 */
object StructuredLogger {
    
    private const val TAG = "StructuredLogger"
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    
    // In-memory buffer for debug screen (last 100 entries)
    private val logBuffer = mutableListOf<LogEntry>()
    private const val MAX_BUFFER_SIZE = 100
    
    data class LogEntry(
        val timestamp: String,
        val level: String,
        val source_id: String? = null,
        val endpoint_id: String? = null,
        val alert_id: String? = null,
        val event: String,
        val details: String? = null
    )
    
    /**
     * Generic log event (flexible for any event type)
     */
    fun logEvent(
        level: String,
        sourceId: String? = null,
        endpointId: String? = null,
        alertId: String? = null,
        event: String,
        details: String? = null
    ) {
        log(
            level = level,
            sourceId = sourceId,
            endpointId = endpointId,
            alertId = alertId,
            event = event,
            details = details
        )
    }
    
    /**
     * Log ingest queued event
     */
    fun logIngestQueued(
        sourceId: String,
        alertId: String,
        details: String? = null
    ) {
        log(
            level = "INFO",
            sourceId = sourceId,
            alertId = alertId,
            event = "ingest_queued",
            details = details
        )
    }
    
    /**
     * Log delivery attempt started
     */
    fun logAttemptStarted(
        sourceId: String,
        endpointId: String,
        alertId: String,
        details: String? = null
    ) {
        log(
            level = "INFO",
            sourceId = sourceId,
            endpointId = endpointId,
            alertId = alertId,
            event = "attempt_started",
            details = details
        )
    }
    
    /**
     * Log HTTP success
     */
    fun logHttpOk(
        sourceId: String,
        endpointId: String,
        alertId: String,
        httpCode: Int,
        latency: Long,
        details: String? = null
    ) {
        log(
            level = "INFO",
            sourceId = sourceId,
            endpointId = endpointId,
            alertId = alertId,
            event = "http_ok",
            details = details ?: "code=$httpCode latency=${latency}ms"
        )
    }
    
    /**
     * Log HTTP failure
     */
    fun logHttpFail(
        sourceId: String,
        endpointId: String,
        alertId: String,
        httpCode: Int,
        error: String,
        details: String? = null
    ) {
        log(
            level = "ERROR",
            sourceId = sourceId,
            endpointId = endpointId,
            alertId = alertId,
            event = "http_fail",
            details = details ?: "code=$httpCode error=$error"
        )
    }
    
    /**
     * Log retry scheduled
     */
    fun logRetryScheduled(
        sourceId: String,
        endpointId: String,
        alertId: String,
        retryCount: Int,
        details: String? = null
    ) {
        log(
            level = "WARN",
            sourceId = sourceId,
            endpointId = endpointId,
            alertId = alertId,
            event = "retry_scheduled",
            details = details ?: "retry=$retryCount"
        )
    }
    
    /**
     * Log delivery success (final state)
     */
    fun logSuccess(
        sourceId: String,
        endpointId: String,
        alertId: String,
        details: String? = null
    ) {
        log(
            level = "INFO",
            sourceId = sourceId,
            endpointId = endpointId,
            alertId = alertId,
            event = "success",
            details = details
        )
    }
    
    /**
     * Core logging function
     */
    private fun log(
        level: String,
        sourceId: String? = null,
        endpointId: String? = null,
        alertId: String? = null,
        event: String,
        details: String? = null
    ) {
        val timestamp = dateFormat.format(Date())
        
        val entry = LogEntry(
            timestamp = timestamp,
            level = level,
            source_id = sourceId,
            endpoint_id = endpointId,
            alert_id = alertId,
            event = event,
            details = details
        )
        
        // Add to buffer for debug screen
        synchronized(logBuffer) {
            logBuffer.add(entry)
            if (logBuffer.size > MAX_BUFFER_SIZE) {
                logBuffer.removeAt(0) // Remove oldest
            }
        }
        
        // Emit NDJSON
        val ndjson = gson.toJson(entry)
        
        // Mirror to Logcat in debug builds
        if (BuildConfig.DEBUG) {
            when (level) {
                "ERROR" -> Log.e(TAG, ndjson)
                "WARN" -> Log.w(TAG, ndjson)
                else -> Log.i(TAG, ndjson)
            }
        }
    }
    
    /**
     * Get recent logs for debug screen
     */
    fun getRecentLogs(limit: Int = 20): List<LogEntry> {
        return synchronized(logBuffer) {
            logBuffer.takeLast(limit)
        }
    }
    
    /**
     * Export all logs as NDJSON string
     */
    fun exportNDJSON(): String {
        return synchronized(logBuffer) {
            logBuffer.joinToString("\n") { gson.toJson(it) }
        }
    }
    
    /**
     * Clear log buffer (for testing)
     */
    fun clear() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
    }
}

