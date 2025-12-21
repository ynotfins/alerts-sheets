package com.example.alertsheets

import android.content.Context
import android.util.Log
import com.example.alertsheets.data.repositories.EndpointRepository
import com.example.alertsheets.domain.models.Endpoint
import com.example.alertsheets.utils.AppConstants
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Network client with robust error handling
 * 
 * FEATURES:
 * - Parallel endpoint sending
 * - Timeout handling
 * - Retry logic (optional)
 * - Detailed error logging
 * - Graceful degradation
 * 
 * V2: Uses EndpointRepository for cleaner architecture
 */
object NetworkClient {
    
    private val TAG = "NetworkClient"
    
    // ✅ Configure timeouts to prevent hanging
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Send JSON to all enabled endpoints
     * 
     * ERROR HANDLING:
     * - Network failures → logged, returns false
     * - Timeout → logged, returns false
     * - Invalid URL → logged, returns false
     * - Partial success → returns true if ANY endpoint succeeded
     */
    suspend fun sendJson(context: Context, jsonString: String): Boolean {
        // Validate input
        if (jsonString.isBlank()) {
            Log.w(TAG, "Attempted to send empty JSON, skipping")
            return false
        }
        
        // ✅ V2: Use repository
        val endpointRepository = EndpointRepository(context)
        val endpoints = endpointRepository.getEnabled()

        if (endpoints.isEmpty()) {
            Log.e(TAG, "No active endpoints configured")
            return false
        }

        // Send to all in parallel with error handling
        return withContext(Dispatchers.IO) {
            try {
                val body = jsonString.toRequestBody(JSON)
                val jobs = endpoints.map { endpoint ->
                    async {
                        sendToEndpoint(endpoint, body, jsonString.take(200))
                    }
                }
                val results = jobs.awaitAll()
                
                val successCount = results.count { it }
                val failCount = results.size - successCount
                
                Log.d(TAG, "Batch send complete: $successCount success, $failCount failed")
                results.any { it }  // ✅ Return true if ANY succeeded
                
            } catch (e: Exception) {
                Log.e(TAG, "Batch send failed", e)
                false
            }
        }
    }
    
    /**
     * Send to single endpoint with detailed error handling
     */
    private fun sendToEndpoint(
        endpoint: Endpoint,
        body: okhttp3.RequestBody,
        jsonPreview: String
    ): Boolean {
        return try {
            val request = Request.Builder()
                .url(endpoint.url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(200) ?: "No error body"
                    Log.e(
                        TAG,
                        "Failed sending to ${endpoint.name}: HTTP ${response.code}\n" +
                        "Error: $errorBody\n" +
                        "Payload: $jsonPreview"
                    )
                    false
                } else {
                    Log.d(TAG, "${AppConstants.Success.NETWORK_SEND_SUCCESS} to ${endpoint.name}")
                    true
                }
            }
            
        } catch (e: UnknownHostException) {
            Log.e(TAG, "DNS resolution failed for ${endpoint.name}: ${endpoint.url}", e)
            false
            
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout sending to ${endpoint.name} (waited 15s)", e)
            false
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error sending to ${endpoint.name}", e)
            false
            
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid URL for ${endpoint.name}: ${endpoint.url}", e)
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "${AppConstants.Errors.NETWORK_SEND_FAILED} to ${endpoint.name}", e)
            false
        }
    }

    /**
     * Synchronous send (for compatibility)
     * WARNING: Do NOT call from main thread
     */
    fun sendSynchronous(url: String, jsonPayload: String): Boolean {
        if (jsonPayload.isBlank()) {
            Log.w(TAG, "Attempted to send empty JSON synchronously, skipping")
            return false
        }
        
        try {
            val body = jsonPayload.toRequestBody(JSON)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Sync send failed: HTTP ${response.code}")
                }
                return response.isSuccessful
            }
            
        } catch (e: UnknownHostException) {
            Log.e(TAG, "DNS resolution failed for $url", e)
            return false
            
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Sync send timeout for $url", e)
            return false
            
        } catch (e: IOException) {
            Log.e(TAG, "Sync network error for $url", e)
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync send error for $url", e)
            return false
        }
    }

    /**
     * Send any data object (serialized to JSON)
     */
    suspend fun sendData(context: Context, data: Any): Boolean {
        return try {
            val jsonString = gson.toJson(data)
            sendJson(context, jsonString)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to serialize data to JSON", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data", e)
            false
        }
    }
    
    /**
     * Send verification ping
     */
    suspend fun sendVerificationPing(context: Context): Boolean {
        val pingJson = """{"type": "verify", "timestamp": ${System.currentTimeMillis()}}"""
        return sendJson(context, pingJson)
    }
}
