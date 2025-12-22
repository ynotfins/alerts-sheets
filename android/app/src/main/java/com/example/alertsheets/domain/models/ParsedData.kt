package com.example.alertsheets.domain.models

import com.google.gson.Gson

/**
 * Parsed notification data
 * After parsing but before JSON transformation
 * 
 * This matches the existing ParsedData structure from v1
 */
data class ParsedData(
    var incidentId: String = "",           // Unique incident identifier (e.g., "#12345")
    var state: String = "",                // State (e.g., "NY", "NJ")
    var county: String = "",               // County (e.g., "Kings", "Bergen")
    var city: String = "",                 // City (e.g., "Brooklyn", "Manhattan")
    var address: String = "",              // Street address
    var incidentType: String = "",         // Type of incident (e.g., "STRUCTURE FIRE")
    var incidentDetails: String = "",      // Additional details
    var fdCodes: List<String> = emptyList(), // Fire department codes
    var timestamp: String = "",            // Human-readable timestamp
    var originalBody: String = ""          // Original notification text (for Column J)
) {
    companion object {
        // Thread-safe Gson instance for JSON serialization
        private val gson = Gson()
    }
    
    /**
     * Convert to map for template variable replacement
     * 
     * NOTE: fdCodes is serialized as a proper JSON array using Gson.
     * This replaces the previous manual string concatenation which was
     * unsafe for values containing quotes, backslashes, or unicode.
     */
    fun toVariableMap(): Map<String, String> {
        return mapOf(
            "incidentId" to incidentId,
            "state" to state,
            "county" to county,
            "city" to city,
            "address" to address,
            "incidentType" to incidentType,
            "incidentDetails" to incidentDetails,
            // ✅ Safe: Gson properly escapes quotes, backslashes, unicode
            "fdCodes" to gson.toJson(fdCodes),
            "timestamp" to timestamp,
            "originalBody" to originalBody
        )
    }
}

