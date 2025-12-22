package com.example.alertsheets

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object LogRepository {
    private val logs = mutableListOf<LogEntry>()
    private const val MAX_LOGS = 200
    private val listeners = mutableListOf<() -> Unit>()
    private val gson = Gson()
    private const val PREFS_NAME = "log_prefs"
    private const val KEY_LOGS = "saved_logs"
    private var context: Context? = null
    
    // ✅ Managed scope instead of GlobalScope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(ctx: Context) {
        context = ctx.applicationContext
        loadLogs()
    }
    
    fun shutdown() {
        scope.cancel()
    }

    fun addLog(entry: LogEntry) {
        synchronized(logs) {
            logs.add(0, entry)
            if (logs.size > MAX_LOGS) {
                logs.removeAt(logs.lastIndex)
            }
        }
        saveLogs()
        notifyListeners()
    }

    fun updateStatus(id: String, newStatus: LogStatus) {
        synchronized(logs) {
            logs.find { it.id == id }?.let {
                it.status = newStatus
            }
        }
        saveLogs()
        notifyListeners()
    }

    fun getLogs(): List<LogEntry> {
        return synchronized(logs) {
            // Return copy to avoid concurrent mod exceptions in UI
            ArrayList(logs)
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        Handler(Looper.getMainLooper()).post {
            listeners.forEach { it.invoke() }
        }
    }

    private fun saveLogs() {
        val ctx = context ?: return
        // Save in background to avoid blocking UI
        scope.launch {
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val logsToSave = synchronized(logs) { ArrayList(logs) }
                val json = gson.toJson(logsToSave)
                prefs.edit().putString(KEY_LOGS, json).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadLogs() {
        val ctx = context ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_LOGS, null) ?: return
            val type = object : TypeToken<List<LogEntry>>() {}.type
            val loadedLogs: List<LogEntry> = gson.fromJson(json, type)
            synchronized(logs) {
                logs.clear()
                logs.addAll(loadedLogs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
