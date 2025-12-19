package com.example.alertsheets.domain.parsers

import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.RawNotification
import com.example.alertsheets.domain.models.Source

/**
 * SMS message parser
 * Handles SMS messages from any sender
 */
class SmsParser : Parser {
    
    override val id = "sms"
    override val name = "SMS Message Parser"
    
    override fun canParse(source: Source, raw: RawNotification): Boolean {
        // SMS notifications have sender
        return raw.packageName == "sms" && raw.sender != null
    }
    
    override fun parse(raw: RawNotification): ParsedData? {
        if (raw.sender == null || raw.text.isBlank()) return null
        
        return ParsedData(
            incidentId = "SMS-${System.currentTimeMillis()}",
            state = "",
            county = "",
            city = "",
            address = raw.sender, // Show sender in Address column
            incidentType = "SMS Message",
            incidentDetails = raw.text,
            fdCodes = emptyList(),
            timestamp = "", // Will be set by pipeline
            originalBody = "From: ${raw.sender}\n${raw.text}"
        )
    }
}

