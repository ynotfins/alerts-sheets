package com.example.alertsheets.utils

import android.content.Context
import com.example.alertsheets.data.repositories.DeliveryLogRepository
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Golden Path: In-memory ring buffer for delivery logs
 * 
 * RULES:
 * - Max 100 entries (ring buffer behavior)
 * - Thread-safe (ConcurrentLinkedQueue)
 * - Only written to by DeliveryPipeline
 * - Read by DebugActivity
 * 
 * Stores REAL delivery attempts, not fake "SENT" states
 */
object DeliveryLogBuffer {
    
    private val buffer = ConcurrentLinkedQueue<DeliveryLogEntry>()
    private const val MAX_SIZE = 100

    @Volatile private var repo: DeliveryLogRepository? = null

    fun init(context: Context) {
        if (repo != null) return
        repo = DeliveryLogRepository(context.applicationContext)
    }
    
    data class DeliveryLogEntry(
        val timestamp: Long,
        val alertId: String,
        val sourceId: String?,
        val endpointId: String?,
        val event: String,  // "attempt_started", "http_ok", "http_fail", "source_ignored", etc.
        val httpCode: Int?,
        val latencyMs: Long?,
        val errorClass: String?,
        val errorMessage: String?,
        val details: String?,
        // App build identifier for debugging (helps distinguish stale logs from old APKs)
        val appBuild: String? = null,
        // Optional debug payloads (for on-device debugging)
        val url: String? = null,
        val payloadPreview: String? = null,
        val responsePreview: String? = null
    )
    
    /**
     * Append a delivery event
     * Called ONLY from DeliveryPipeline
     */
    fun append(entry: DeliveryLogEntry) {
        buffer.add(entry)
        // Persist for reliable debugging across restarts (best-effort)
        runCatching { repo?.append(entry) }
        
        // Trim to max size
        while (buffer.size > MAX_SIZE) {
            buffer.poll() // Remove oldest
        }
    }
    
    /**
     * Get last N entries for Debug screen
     */
    fun getRecent(limit: Int = 20): List<DeliveryLogEntry> {
        return buffer.toList().takeLast(limit)
    }
    
    /**
     * Get count of entries
     */
    fun size(): Int {
        return buffer.size
    }
    
    /**
     * Clear buffer (for testing)
     */
    fun clear() {
        buffer.clear()
        runCatching { repo?.clear() }
    }
}

