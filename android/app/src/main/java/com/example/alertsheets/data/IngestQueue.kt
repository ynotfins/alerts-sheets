package com.example.alertsheets.data

import android.content.Context
import android.util.Log
import com.example.alertsheets.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ingestion Queue Manager - Milestone 1
 * 
 * Responsibilities:
 * - Enqueue events to local SQLite
 * - Retry with exponential backoff
 * - POST to Firestore /ingest endpoint
 * - Handle Firebase Auth token refresh
 * - Automatic crash recovery on app start
 * 
 * This class ensures ZERO DATA LOSS by persisting events locally
 * before attempting network delivery.
 */
class IngestQueue(private val context: Context) {

    private val db = IngestQueueDb(context)
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isProcessing = AtomicBoolean(false)

    companion object {
        private const val TAG = "IngestQueue"
        
        // Retry configuration
        private val RETRY_DELAYS = listOf(
            1000L,    // 1s
            2000L,    // 2s
            4000L,    // 4s
            8000L,    // 8s
            16000L,   // 16s
            32000L,   // 32s
            60000L    // 60s (steady state)
        )
        
        // HTTP client with timeouts
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    init {
        // Perform crash recovery on initialization
        db.recoverFromCrash()
        db.cleanupOldEntries()
        Log.i(TAG, "✅ IngestQueue initialized (pending: ${db.getPendingCount()}, endpoint: ${BuildConfig.INGEST_ENDPOINT}, env: ${BuildConfig.ENVIRONMENT})")
    }

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Enqueue event for ingestion
     * 
     * This is the PRIMARY entry point for all events.
     * Event is immediately persisted to SQLite before any network call.
     * 
     * @param sourceId Which source captured this
     * @param payload Rendered JSON payload
     * @param timestamp Capture timestamp (ISO 8601)
     * @return Generated UUID for this event
     */
    fun enqueue(sourceId: String, payload: String, timestamp: String): String {
        val uuid = UUID.randomUUID().toString()
        val appVersion = getAppVersion()
        val deviceId = getDeviceId()
        
        val enqueued = db.enqueue(
            uuid = uuid,
            sourceId = sourceId,
            payload = payload,
            timestamp = timestamp,
            deviceId = deviceId,
            appVersion = appVersion
        )
        
        if (enqueued) {
            Log.i(TAG, "📥 Enqueued: $uuid (sourceId: $sourceId)")
            // Trigger processing asynchronously
            processQueue()
        } else {
            Log.w(TAG, "⚠️ Failed to enqueue (duplicate UUID?): $uuid")
        }
        
        return uuid
    }

    /**
     * Start processing queue (idempotent, safe to call multiple times)
     */
    fun processQueue() {
        // Prevent concurrent processing
        if (isProcessing.getAndSet(true)) {
            Log.d(TAG, "⏭️ Queue already processing, skipping")
            return
        }
        
        scope.launch {
            try {
                processQueueInternal()
            } finally {
                isProcessing.set(false)
            }
        }
    }

    /**
     * Get current queue statistics
     */
    fun getStats(): QueueStats {
        return QueueStats(
            pendingCount = db.getPendingCount(),
            oldestEventAgeSec = db.getOldestEventAge()
        )
    }

    /**
     * Shutdown (cancel coroutines)
     */
    fun shutdown() {
        scope.cancel()
        Log.i(TAG, "🛑 IngestQueue shut down")
    }

    // ============================================================================
    // INTERNAL PROCESSING
    // ============================================================================

    private suspend fun processQueueInternal() {
        Log.d(TAG, "🚀 Starting queue processing")
        
        while (scope.isActive) {
            val pending = db.getPendingEvents()
            
            if (pending.isEmpty()) {
                Log.d(TAG, "✅ Queue empty, stopping")
                break
            }
            
            Log.d(TAG, "📋 Processing ${pending.size} pending events")
            
            for (event in pending) {
                if (!scope.isActive) break
                
                try {
                    val result = ingestEvent(event)
                    
                    when (result.status) {
                        IngestStatus.SUCCESS -> {
                            // Server ACK received, delete from queue
                            db.delete(event.uuid)
                            Log.i(TAG, "✅ Ingested: ${event.uuid}")
                        }
                        IngestStatus.DUPLICATE -> {
                            // Server already has this event, delete from queue
                            db.delete(event.uuid)
                            Log.i(TAG, "✅ Duplicate (already ingested): ${event.uuid}")
                        }
                        IngestStatus.RETRY -> {
                            // Transient failure, retry later
                            db.incrementRetry(event.uuid, result.error)
                            val backoff = calculateBackoff(event.retryCount)
                            Log.w(TAG, "⚠️ Retry scheduled (${event.retryCount + 1}): ${event.uuid} - ${result.error} (backoff: ${backoff}ms)")
                            delay(backoff)
                        }
                        IngestStatus.PERMANENT_FAILURE -> {
                            // Permanent failure (bad request, auth failure)
                            // Mark as failed but DON'T delete (user intervention needed)
                            db.incrementRetry(event.uuid, result.error)
                            Log.e(TAG, "❌ PERMANENT FAILURE: ${event.uuid} - ${result.error}")
                            // Skip to next event
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception processing ${event.uuid}", e)
                    db.incrementRetry(event.uuid, e.message)
                    delay(calculateBackoff(event.retryCount))
                }
            }
            
            // Small delay before checking for new events
            delay(1000)
        }
        
        Log.d(TAG, "🏁 Queue processing finished")
    }

    /**
     * Attempt to ingest single event to Firestore
     */
    private suspend fun ingestEvent(event: IngestQueueEntry): IngestResult {
        // Step 1: Get Firebase Auth token
        val idToken = try {
            getFirebaseIdToken()
        } catch (e: Exception) {
            return IngestResult(IngestStatus.RETRY, "Auth token failed: ${e.message}")
        }
        
        if (idToken == null) {
            return IngestResult(IngestStatus.PERMANENT_FAILURE, "User not authenticated")
        }
        
        // Step 2: Build request body
        val requestBody = mapOf(
            "uuid" to event.uuid,
            "sourceId" to event.sourceId,
            "payload" to event.payload,
            "timestamp" to event.timestamp,
            "deviceId" to event.deviceId,
            "appVersion" to event.appVersion
        )
        
        val json = gson.toJson(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())
        
        // Step 3: Build HTTP request
        val request = Request.Builder()
            .url(BuildConfig.INGEST_ENDPOINT)
            .post(body)
            .addHeader("Authorization", "Bearer $idToken")
            .addHeader("Content-Type", "application/json")
            .build()
        
        // Step 4: Execute request
        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                
                when (response.code) {
                    200 -> {
                        // Parse response to check if duplicate
                        val responseJson = try {
                            gson.fromJson(responseBody, Map::class.java)
                        } catch (e: Exception) {
                            emptyMap<String, Any>()
                        }
                        
                        val isDuplicate = responseJson["isDuplicate"] as? Boolean ?: false
                        
                        if (isDuplicate) {
                            IngestResult(IngestStatus.DUPLICATE, "Already ingested")
                        } else {
                            IngestResult(IngestStatus.SUCCESS, "Ingested successfully")
                        }
                    }
                    400 -> {
                        // Bad request (schema error, invalid UUID, etc.)
                        IngestResult(IngestStatus.PERMANENT_FAILURE, "Bad request: $responseBody")
                    }
                    401, 403 -> {
                        // Auth failure (token expired, invalid, etc.)
                        // Mark as RETRY (token may refresh on next attempt)
                        IngestResult(IngestStatus.RETRY, "Auth failed: HTTP ${response.code}")
                    }
                    429 -> {
                        // Rate limited
                        IngestResult(IngestStatus.RETRY, "Rate limited")
                    }
                    500, 502, 503, 504 -> {
                        // Server error or gateway error (transient)
                        IngestResult(IngestStatus.RETRY, "Server error: HTTP ${response.code}")
                    }
                    else -> {
                        // Unknown error, retry
                        IngestResult(IngestStatus.RETRY, "HTTP ${response.code}: $responseBody")
                    }
                }
            }
        } catch (e: IOException) {
            // Network error (DNS, timeout, connection refused, etc.)
            IngestResult(IngestStatus.RETRY, "Network error: ${e.message}")
        } catch (e: Exception) {
            // Unexpected error
            IngestResult(IngestStatus.RETRY, "Unexpected error: ${e.message}")
        }
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private suspend fun getFirebaseIdToken(): String? {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null) {
                Log.w(TAG, "⚠️ No Firebase user authenticated")
                return null
            }
            
            val result = user.getIdToken(false).await()
            result.token
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get Firebase ID token", e)
            null
        }
    }

    private fun calculateBackoff(retryCount: Int): Long {
        // Get delay from RETRY_DELAYS array, or use max delay if exceeded
        val delay = RETRY_DELAYS.getOrElse(retryCount) { RETRY_DELAYS.last() }
        
        // Add jitter (±20%) to prevent thundering herd
        val jitter = delay * (0.8 + Math.random() * 0.4)
        
        return jitter.toLong()
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getDeviceId(): String {
        // Use Android ID as device identifier
        return try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

enum class IngestStatus {
    SUCCESS,            // 200 OK, event ingested
    DUPLICATE,          // 200 OK, event already ingested (idempotent)
    RETRY,              // Transient failure, retry later
    PERMANENT_FAILURE   // Permanent failure, user intervention needed
}

data class IngestResult(
    val status: IngestStatus,
    val error: String? = null
)

data class QueueStats(
    val pendingCount: Int,
    val oldestEventAgeSec: Long?
)

