package com.example.alertsheets.domain.models

import com.example.alertsheets.utils.PayloadSerializer

/**
 * HTTP endpoint where notifications are sent
 * 
 * Examples:
 * - Google Apps Script URL
 * - Firebase Cloud Function URL
 * - Custom webhook URL
 * 
 * @property schemaVersion For migration support - increment when structure changes
 */
data class Endpoint(
    val id: String,                          // UUID
    val name: String,                        // Display name (e.g., "Google Sheets - Main")
    val url: String,                         // Full URL
    val enabled: Boolean = true,             // Master on/off switch
    val timeout: Int = 30000,                // Connection timeout in ms
    val retryCount: Int = 3,                 // Number of retry attempts on failure
    val headers: Map<String, String> = emptyMap(), // Custom headers (e.g., API keys)
    val stats: EndpointStats = EndpointStats(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = PayloadSerializer.SchemaVersion.ENDPOINTS
) {
    /**
     * Check if URL is valid
     */
    fun isValidUrl(): Boolean {
        return url.startsWith("https://") || url.startsWith("http://")
    }
}

/**
 * Usage statistics for an endpoint
 */
data class EndpointStats(
    val totalRequests: Int = 0,      // Total requests sent
    val totalSuccess: Int = 0,       // Successful responses (2xx)
    val totalFailed: Int = 0,        // Failed requests
    val avgResponseTime: Long = 0L,  // Average response time in ms
    val lastActivity: Long = 0L      // Timestamp of last request
) {
    val successRate: Float
        get() = if (totalRequests > 0) {
            (totalSuccess.toFloat() / totalRequests.toFloat()) * 100
        } else {
            100f
        }
}

