package com.example.alertsheets.domain.models

/**
 * Raw notification data captured from system
 * Before parsing and transformation
 */
data class RawNotification(
    val packageName: String,            // App package name or "sms"
    val title: String,                  // Notification title
    val text: String,                   // Notification text (small text)
    val bigText: String = "",           // Expanded text (if available)
    val fullText: String = "",          // Combined text for parsing
    val sender: String? = null,         // SMS sender phone number
    val timestamp: Long = System.currentTimeMillis(),
    val extras: Map<String, String> = emptyMap() // Additional metadata
) {
    companion object {
        /**
         * Create from Android StatusBarNotification
         */
        fun fromNotification(
            packageName: String,
            title: String,
            text: String,
            bigText: String,
            extras: Map<String, String> = emptyMap()
        ): RawNotification {
            val fullText = if (bigText.isNotEmpty()) bigText else text
            return RawNotification(
                packageName = packageName,
                title = title,
                text = text,
                bigText = bigText,
                fullText = fullText,
                sender = null,
                timestamp = System.currentTimeMillis(),
                extras = extras
            )
        }
        
        /**
         * Create from SMS
         */
        fun fromSms(
            sender: String,
            message: String
        ): RawNotification {
            return RawNotification(
                packageName = "sms",
                title = "SMS from $sender",
                text = message,
                bigText = message,
                fullText = message,
                sender = sender,
                timestamp = System.currentTimeMillis(),
                extras = emptyMap()
            )
        }
    }
}

