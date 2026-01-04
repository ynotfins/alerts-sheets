package com.example.alertsheets.data.repositories

import android.content.Context
import android.util.Log
import com.example.alertsheets.data.storage.JsonStorage
import com.example.alertsheets.utils.AppConstants
import com.example.alertsheets.utils.DeliveryLogBuffer
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * Persistent storage for delivery logs shown on the Debug screen.
 *
 * Why: DeliveryLogBuffer is in-memory only and is lost on process death.
 * This repository keeps last N entries across restarts so debugging is reliable.
 */
class DeliveryLogRepository(context: Context) {
    private val tag = "DeliveryLogRepository"
    private val gson = Gson()
    private val storage = JsonStorage(context.applicationContext, AppConstants.FILE_DELIVERY_LOGS)

    private val maxEntries = 500

    fun getAll(): List<DeliveryLogBuffer.DeliveryLogEntry> {
        val json = storage.read() ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DeliveryLogBuffer.DeliveryLogEntry>>() {}.type
            gson.fromJson<List<DeliveryLogBuffer.DeliveryLogEntry>>(json, type) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            Log.e(tag, "Corrupt delivery logs JSON, returning empty", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Failed to read delivery logs", e)
            emptyList()
        }
    }

    fun getRecent(limit: Int = 50): List<DeliveryLogBuffer.DeliveryLogEntry> {
        val all = getAll()
        if (all.isEmpty()) return emptyList()
        return all.takeLast(limit)
    }

    fun append(entry: DeliveryLogBuffer.DeliveryLogEntry) {
        try {
            val all = getAll().toMutableList()
            all.add(entry)
            val trimmed = if (all.size > maxEntries) all.takeLast(maxEntries) else all
            storage.write(gson.toJson(trimmed))
        } catch (e: Exception) {
            Log.e(tag, "Failed to append delivery log", e)
        }
    }

    fun clear() {
        runCatching { storage.delete() }
    }
}


