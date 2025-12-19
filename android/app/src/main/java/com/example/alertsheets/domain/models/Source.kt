package com.example.alertsheets.domain.models

/**
 * Represents a notification or SMS source that can be monitored.
 * Each source has its own configuration, template, and settings.
 * 
 * Examples:
 * - App: Source(id="com.example.bnn", type=APP, name="BNN Alerts", ...)
 * - SMS: Source(id="sms:+15551234567", type=SMS, name="Dispatch SMS", ...)
 */
data class Source(
    val id: String,                           // Package name or "sms:phoneNumber"
    val type: SourceType,                     // APP or SMS
    val name: String,                         // Display name
    val enabled: Boolean = true,              // Master on/off switch
    val templateId: String,                   // Reference to Template.id
    val autoClean: Boolean = false,           // Remove emojis/symbols for THIS source
    val parserId: String = "generic",         // "bnn", "generic", or "sms"
    val endpointId: String,                   // Reference to Endpoint.id
    val iconColor: Int = 0xFF4A9EFF.toInt(), // For UI display
    val stats: SourceStats = SourceStats(),   // Usage statistics
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if this source matches a notification package name
     */
    fun matchesPackage(packageName: String): Boolean {
        return type == SourceType.APP && id == packageName
    }
    
    /**
     * Check if this source matches an SMS sender
     */
    fun matchesSender(sender: String): Boolean {
        return type == SourceType.SMS && id == "sms:$sender"
    }
}

/**
 * Type of source
 */
enum class SourceType {
    APP,  // Notification from installed app
    SMS   // SMS message from phone number
}

/**
 * Usage statistics for a source
 */
data class SourceStats(
    val totalProcessed: Int = 0,    // Total notifications received
    val totalSent: Int = 0,         // Successfully sent to endpoint
    val totalFailed: Int = 0,       // Failed to send
    val lastActivity: Long = 0L     // Timestamp of last notification
) {
    val successRate: Float
        get() = if (totalProcessed > 0) {
            (totalSent.toFloat() / totalProcessed.toFloat()) * 100
        } else {
            100f
        }
}

