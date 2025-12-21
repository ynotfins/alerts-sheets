package com.example.alertsheets.utils

import android.content.Context
import android.util.Log
import com.example.alertsheets.data.storage.JsonStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple logger for V2
 * Stores logs in memory and persists to file
 */
class Logger(private val context: Context) {
    
    private val TAG = "Logger"
    private val storage = JsonStorage(context, "logs.json")
    private val gson = Gson()
    private val maxLogs = 1000 // Keep last 1000 logs
    
    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val message: String
    ) {
        fun toDisplayString(): String {
            val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.US)
            val time = sdf.format(Date(timestamp))
            return "[$time] $level: $message"
        }
    }
    
    private val logs = mutableListOf<LogEntry>()
    
    /**
     * Log info message
     */
    fun log(message: String) {
        val entry = LogEntry(System.currentTimeMillis(), "INFO", message)
        logs.add(entry)
        Log.i(TAG, message)
        trimAndSave()
    }
    
    /**
     * Log error message
     */
    fun error(message: String) {
        val entry = LogEntry(System.currentTimeMillis(), "ERROR", message)
        logs.add(entry)
        Log.e(TAG, message)
        trimAndSave()
    }
    
    /**
     * Get all logs
     */
    fun getLogs(): List<LogEntry> {
        return logs.toList()
    }
    
    /**
     * Get recent logs (last N)
     */
    fun getRecentLogs(count: Int = 50): List<LogEntry> {
        return logs.takeLast(count)
    }
    
    /**
     * Clear all logs
     */
    fun clear() {
        logs.clear()
        storage.delete()
    }
    
    /**
     * Trim logs to max size and save
     */
    private fun trimAndSave() {
        if (logs.size > maxLogs) {
            val toRemove = logs.size - maxLogs
            repeat(toRemove) {
                logs.removeAt(0)
            }
        }
        
        try {
            storage.write(gson.toJson(logs))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save logs", e)
        }
    }
    
    /**
     * Load logs from file
     */
    fun load() {
        try {
            val json = storage.read()
            if (json != null) {
                val loaded = gson.fromJson<List<LogEntry>>(
                    json,
                    object : TypeToken<List<LogEntry>>() {}.type
                )
                logs.clear()
                logs.addAll(loaded)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load logs", e)
        }
    }
}

