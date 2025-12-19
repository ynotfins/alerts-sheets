package com.example.alertsheets.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple HTTP client for V2
 * Sends JSON payloads to endpoints
 */
class HttpClient {
    
    private val TAG = "HttpClient"
    
    data class HttpResponse(
        val code: Int,
        val message: String,
        val body: String
    ) {
        val isSuccess: Boolean
            get() = code in 200..299
    }
    
    /**
     * Send POST request with JSON body
     */
    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        timeout: Int = 30000
    ): HttpResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlObj = URL(url)
            connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            
            // Add custom headers
            for ((key, value) in headers) {
                connection.setRequestProperty(key, value)
            }
            
            // Write body
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(body)
            writer.flush()
            writer.close()
            
            // Read response
            val code = connection.responseCode
            val message = connection.responseMessage ?: ""
            
            val responseBody = if (code in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                reader.use { it.readText() }
            } else {
                val reader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                reader.use { it.readText() }
            }
            
            Log.d(TAG, "POST $url: $code")
            HttpResponse(code, message, responseBody)
            
        } catch (e: Exception) {
            Log.e(TAG, "HTTP error", e)
            HttpResponse(0, e.message ?: "Unknown error", "")
        } finally {
            connection?.disconnect()
        }
    }
}

