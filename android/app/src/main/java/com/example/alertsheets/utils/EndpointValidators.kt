package com.example.alertsheets.utils

import com.example.alertsheets.domain.models.Endpoint

/**
 * Endpoint validation helpers
 * 
 * Ensures consistent validation logic across UI and pipeline:
 * - UI: Prevents selecting invalid endpoints
 * - Pipeline: Filters out invalid endpoints before delivery
 * 
 * Hard Invariants:
 * - Never allow disabled endpoints
 * - Never allow endpoints with placeholder URLs ("YOUR_SCRIPT_ID")
 * - Never allow endpoints with blank URLs
 */
object EndpointValidators {
    
    /**
     * Check if URL is valid for delivery
     * 
     * Valid URL must:
     * - Start with http:// or https://
     * - NOT contain placeholder text "YOUR_SCRIPT_ID"
     * - NOT be blank/empty
     */
    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (url.contains("YOUR_SCRIPT_ID", ignoreCase = true)) return false
        return true
    }
    
    /**
     * Check if endpoint is selectable in UI / usable in pipeline
     * 
     * Selectable endpoint must be:
     * - enabled == true
     * - url is valid (see isValidUrl)
     */
    fun isSelectable(endpoint: Endpoint): Boolean {
        return endpoint.enabled && isValidUrl(endpoint.url)
    }
    
    /**
     * Get human-readable reason why endpoint is not selectable
     * 
     * Returns:
     * - "Disabled" if endpoint.enabled == false
     * - "Needs URL" if URL is invalid
     * - null if endpoint is selectable
     */
    fun getReason(endpoint: Endpoint): String? {
        if (!endpoint.enabled) return "Disabled"
        if (!isValidUrl(endpoint.url)) return "Needs URL"
        return null
    }
    
    /**
     * Get display label for endpoint
     * 
     * Examples:
     * - "Firestore Ingest Function" (if selectable)
     * - "Google Apps Script [DISABLED]" (if disabled)
     * - "Test Endpoint [NEEDS URL]" (if invalid URL)
     */
    fun getDisplayLabel(endpoint: Endpoint): String {
        val reason = getReason(endpoint)
        return if (reason != null) {
            "${endpoint.name} [${reason.uppercase()}]"
        } else {
            endpoint.name
        }
    }
    
    /**
     * Filter a list of endpoints to only selectable ones
     * 
     * Used by DeliveryPipeline to ensure only valid endpoints are attempted
     */
    fun filterSelectable(endpoints: List<Endpoint>): List<Endpoint> {
        return endpoints.filter { isSelectable(it) }
    }
    
    /**
     * Count how many endpoints are selectable
     * 
     * Used for logging and validation
     */
    fun countSelectable(endpoints: List<Endpoint>): Int {
        return endpoints.count { isSelectable(it) }
    }
    
    /**
     * Get validation summary for logging
     * 
     * Returns map with:
     * - "total": Total endpoints
     * - "enabled": Endpoints with enabled==true
     * - "validUrl": Endpoints with valid URL
     * - "selectable": Endpoints that are both enabled AND have valid URL
     */
    fun getValidationSummary(endpoints: List<Endpoint>): Map<String, Int> {
        return mapOf(
            "total" to endpoints.size,
            "enabled" to endpoints.count { it.enabled },
            "validUrl" to endpoints.count { isValidUrl(it.url) },
            "selectable" to countSelectable(endpoints)
        )
    }
}

