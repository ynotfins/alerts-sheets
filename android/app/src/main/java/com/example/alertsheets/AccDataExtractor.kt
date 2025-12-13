package com.example.alertsheets

import android.content.Context
import android.view.accessibility.AccessibilityEvent

object AccDataExtractor {
    fun extract(event: AccessibilityEvent, config: AppConfig): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        
        // Accessibility events are limited compared to SBN.
        // We primarily have "Text".
        val textContent = event.text.joinToString("\n")

        config.mappings.forEach { mapping ->
            val value = when (mapping.sourceField) {
                NotificationField.TEXT, 
                NotificationField.BIG_TEXT, 
                NotificationField.TITLE -> textContent // AccService mostly sees flattened text
                NotificationField.PACKAGE_NAME -> event.packageName?.toString()
                NotificationField.TIMESTAMP -> event.eventTime // Relative time, not wall clock
                else -> null
            }
            // For now, map most text fields to the main content since we can't easily distinguish title/body in AccEvent
            if (value != null) {
                data[mapping.targetKey] = value
            }
        }

        config.staticFields.forEach { staticField ->
            data[staticField.key] = staticField.value
        }
        
        return data
    }
}
