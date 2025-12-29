package com.example.alertsheets.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Golden Path: Single HTTP sender implementation
 * 
 * RULES:
 * - Uses OkHttp (only implementation)
 * - Never throws uncaught exceptions
 * - Returns structured HttpResult with all details
 * - Enforces 15-second timeout
 */
object ReliableHttpSender {
    
    private val TAG = "ReliableHttpSender"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    data class HttpResult(
        val success: Boolean,
        val httpCode: Int,
        val latencyMs: Long,
        val errorClass: String?,
        val errorMessage: String?,
        val responseBody: String? = null  // ✅ Add response body for inspection
    )
    
    /**
     * POST JSON to endpoint
     * NEVER throws exceptions - always returns HttpResult
     */
    suspend fun postJson(
        endpointUrl: String,
        headers: Map<String, String> = emptyMap(),
        bodyJson: String
    ): HttpResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val body = bodyJson.toRequestBody(JSON)
            val requestBuilder = Request.Builder().url(endpointUrl).post(body)
            
            // Add headers
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            
            val request = requestBuilder.build()
            
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    HttpResult(
                        success = true,
                        httpCode = response.code,
                        latencyMs = latency,
                        errorClass = null,
                        errorMessage = null,
                        responseBody = responseBody.take(500)  // ✅ First 500 chars
                    )
                } else {
                    HttpResult(
                        success = false,
                        httpCode = response.code,
                        latencyMs = latency,
                        errorClass = "HttpError",
                        errorMessage = responseBody.take(200),
                        responseBody = responseBody.take(500)
                    )
                }
            }
            
        } catch (e: UnknownHostException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "DNS resolution failed: ${e.message}")
            HttpResult(
                success = false,
                httpCode = 0,
                latencyMs = latency,
                errorClass = "DNSError",
                errorMessage = "DNS resolution failed: ${e.message}"
            )
            
        } catch (e: SocketTimeoutException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "Timeout after 15s: ${e.message}")
            HttpResult(
                success = false,
                httpCode = 0,
                latencyMs = latency,
                errorClass = "TimeoutError",
                errorMessage = "Timeout after 15s"
            )
            
        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "IO error: ${e.message}")
            HttpResult(
                success = false,
                httpCode = 0,
                latencyMs = latency,
                errorClass = "IOException",
                errorMessage = e.message?.take(200) ?: "Unknown IO error"
            )
            
        } catch (e: IllegalArgumentException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "Invalid URL: ${e.message}")
            HttpResult(
                success = false,
                httpCode = 0,
                latencyMs = latency,
                errorClass = "InvalidURL",
                errorMessage = "Invalid URL: ${e.message}"
            )
            
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            HttpResult(
                success = false,
                httpCode = 0,
                latencyMs = latency,
                errorClass = e.javaClass.simpleName,
                errorMessage = e.message?.take(200) ?: "Unknown error"
            )
        }
    }
}

