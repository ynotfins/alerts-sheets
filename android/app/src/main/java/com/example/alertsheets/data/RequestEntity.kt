package com.example.alertsheets.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "request_queue")
data class RequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val payload: String,
    val status: String, // PENDING, SENT, FAILED
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
