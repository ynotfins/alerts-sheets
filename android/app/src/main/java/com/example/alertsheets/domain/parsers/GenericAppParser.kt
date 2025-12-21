package com.example.alertsheets.domain.parsers

import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.RawNotification
import com.example.alertsheets.domain.models.Source

/**
 * Generic app notification parser
 * Handles any app that doesn't have a special parser
 */
class GenericAppParser : Parser {
    
    override val id = "generic"
    override val name = "Generic App Parser"
    
    override fun canParse(source: Source, raw: RawNotification): Boolean {
        // Can parse any app notification
        return raw.packageName != "sms"
    }
    
    override fun parse(raw: RawNotification): ParsedData? {
        if (raw.fullText.isBlank()) return null
        
        // Simple parsing for non-BNN apps
        return ParsedData(
            incidentId = "#${System.currentTimeMillis()}",
            state = "",
            county = "",
            city = "",
            address = "",
            incidentType = raw.title,
            incidentDetails = raw.text,
            fdCodes = emptyList(),
            timestamp = "", // Will be set by pipeline
            originalBody = raw.fullText
        )
    }
}

