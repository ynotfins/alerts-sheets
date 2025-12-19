package com.example.alertsheets.domain.models

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
    /**
     * Convert to map for template variable replacement
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
            "fdCodes" to fdCodes.joinToString("\", \"", "\"", "\""),
            "timestamp" to timestamp,
            "originalBody" to originalBody
        )
    }
}

