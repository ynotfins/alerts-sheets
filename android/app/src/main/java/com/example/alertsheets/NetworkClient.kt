package com.example.alertsheets

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object NetworkClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun sendData(context: Context, data: ParsedData): Boolean {
        // Get URL from SharedPreferences
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("script_url", null)

        if (url.isNullOrBlank()) {
            Log.e("NetworkClient", "No URL configured")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = gson.toJson(data)
                val body = jsonBody.toRequestBody(JSON)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("NetworkClient", "Unexpected code $response")
                        return@use false
                    }
                    Log.d("NetworkClient", "Success: ${response.body?.string()}")
                    return@use true
                }
            } catch (e: Exception) {
                Log.e("NetworkClient", "Error sending data", e)
                return@withContext false
            }
        }
    }
}
