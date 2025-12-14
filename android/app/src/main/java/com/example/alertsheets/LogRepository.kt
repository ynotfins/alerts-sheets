package com.example.alertsheets

import android.os.Handler
import android.os.Looper

object LogRepository {
    private val logs = mutableListOf<LogEntry>()
    private const val MAX_LOGS = 50
    private val listeners = mutableListOf<() -> Unit>()

    fun addLog(entry: LogEntry) {
        synchronized(logs) {
            logs.add(0, entry)
            if (logs.size > MAX_LOGS) {
                logs.removeAt(logs.lastIndex)
            }
        }
        notifyListeners()
    }

    fun updateStatus(id: String, newStatus: LogStatus) {
        synchronized(logs) {
            logs.find { it.id == id }?.let {
                it.status = newStatus
            }
        }
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
}
