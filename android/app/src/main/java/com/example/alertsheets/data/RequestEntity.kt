package com.example.alertsheets.data

data class RequestEntity(
    val id: Long = 0,
    val url: String,
    val payload: String,
    val status: String, // PENDING, SENT, FAILED
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
