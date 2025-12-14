package com.example.alertsheets

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object NetworkClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun sendJson(context: Context, jsonString: String): Boolean {
        // Get ALL endpoints that are enabled
        val endpoints = PrefsManager.getEndpoints(context).filter { it.isEnabled }

        if (endpoints.isEmpty()) {
            Log.e("NetworkClient", "No active endpoints configured")
            return false
        }

        // Send to all in parallel
        return withContext(Dispatchers.IO) {
            val body = jsonString.toRequestBody(JSON)
            val jobs = endpoints.map { endpoint ->
                async {
                    try {
                        val request = Request.Builder()
                            .url(endpoint.url)
                            .post(body)
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                Log.e("NetworkClient", "Failed sending to ${endpoint.name}: $response")
                                false
                            } else {
                                Log.d("NetworkClient", "Success sending to ${endpoint.name}")
                                true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NetworkClient", "Error sending to ${endpoint.name}", e)
                        false
                    }
                }
            }
            val results = jobs.awaitAll()
            results.any { it }
        }
    }

    suspend fun sendData(context: Context, data: Any): Boolean {
        val jsonString = gson.toJson(data)
        return sendJson(context, jsonString)
    }
    suspend fun sendVerificationPing(context: Context): Boolean {
        val pingJson = "{\"type\": \"verify\"}"
        return sendJson(context, pingJson)
    }
}
