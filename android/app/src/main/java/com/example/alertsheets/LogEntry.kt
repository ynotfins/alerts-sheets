package com.example.alertsheets

import java.util.UUID

enum class LogStatus {
    PENDING,
    SENT,
    FAILED,
    IGNORED
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val title: String,
    val content: String,
    var status: LogStatus = LogStatus.PENDING,
    val rawJson: String = ""
)
