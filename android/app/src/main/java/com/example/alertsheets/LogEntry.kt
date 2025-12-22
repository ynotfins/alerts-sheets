package com.example.alertsheets

import com.example.alertsheets.utils.PayloadSerializer
import java.util.UUID

enum class LogStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    IGNORED
}

/**
 * Activity log entry
 * 
 * @property schemaVersion For migration support - increment when structure changes
 */
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val title: String,
    val content: String,
    var status: LogStatus = LogStatus.PENDING,
    val rawJson: String = "",
    val schemaVersion: Int = PayloadSerializer.SchemaVersion.LOG_ENTRIES
)
