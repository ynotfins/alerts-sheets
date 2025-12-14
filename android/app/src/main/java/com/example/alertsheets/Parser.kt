package com.example.alertsheets

import java.util.regex.Pattern

object Parser {

    fun parse(fullText: String): ParsedData? {
        val text = fullText.replace("\r\n", "\n")
        val lines = text.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        
        val contentLine = lines.firstOrNull { it.contains("|") } ?: return null
        val parts = contentLine.split("|").map { it.trim() }
        
        var status = if (lines.indexOf(contentLine) > 0) lines[0] else ""
        if (status.isBlank() && fullText.startsWith("Update")) status = "Update"
        if (status.isBlank() && fullText.startsWith("New Incident")) status = "New Incident"
        
        // --- 1. State & Location Parsing ---
        // Ex: "NJ", "PA", "NY", "U/D NY"
        var state = parts.getOrElse(0) { "" }
        if (state.startsWith("U/D", ignoreCase = true)) {
            state = state.removePrefix("U/D").trim()
            if (status.isBlank()) status = "Update"
        }
        
        // Borough Check for NY
        // List of boroughs where county is skipped/implied
        val boroughs = setOf("Queens", "Bronx", "Brooklyn", "Manhattan", "Staten Island", "New York")
        var county = ""
        var city = ""
        var offset = 1

        val loc1 = parts.getOrElse(1) { "" }
        val loc2 = parts.getOrElse(2) { "" }

        if (state.equals("NY", ignoreCase = true) && boroughs.any { loc1.equals(it, ignoreCase = true) }) {
            // "NY | Queens | Type..." -> County skipped/merged
            county = "" // Or use loc1 as county if preferred, but user said "missing county field"
            city = loc1
            offset = 2
        } else {
            // Standard: State | County | City
            county = loc1
            city = loc2
            offset = 3
        }

        // --- 2. Anchor from End (ID, Codes, Source) ---
        var idIndex = -1
        var incidentId = ""
        
        // Find ID (last item usually)
        if (parts.isNotEmpty()) {
            val last = parts.last()
            if (last.startsWith("#") || (last.length > 5 && last.all { it.isDigit() })) {
                incidentId = if(last.startsWith("#")) last else "#$last"
                idIndex = parts.lastIndex
            }
        }
        
        // Find Incident Details vs Middle Fields
        // Scan backwards from ID (or end) to Offset
        // Look for the "Source" tag "<C> BNN" or split by length
        
        var sourceIndex = -1
        var codesIndex = -1
        var incidentIndex = -1
        val endIndex = if (idIndex != -1) idIndex - 1 else parts.lastIndex
        
        // Heuristic: Incident details is usually the longest content block before the tags
        var maxLen = 0
        var maxLenIndex = -1
        
        for (i in offset..endIndex) {
            val p = parts[i]
            if (p.contains("BNN", ignoreCase = true) || p.contains("<C>", ignoreCase = true)) {
                sourceIndex = i
            }
            // Codes often have slashes or look like "ny123"
            if (p.contains("/") || (p.length < 10 && p.matches(Regex(".*\\d.*")) && !p.contains(" "))) {
                 // Likely codes, but careful not to confuse with Box Number (e.g. QN-5610)
                 // Codes are usually very late in the string
                 if (i > offset + 2) codesIndex = i 
            }
            
            if (p.length > maxLen && !p.contains("BNN") && !p.contains("#")) {
                maxLen = p.length
                maxLenIndex = i
            }
        }
        
        // Assume longest part is the Incident Details
        if (maxLen > 20) {
             incidentIndex = maxLenIndex
        }
        
        // --- 3. Extract Middle Fields (Type, Address, Box) ---
        // Everything between 'offset' and 'incidentIndex'
        var address = ""
        var incidentType = ""
        var extra = "" // Box codes etc
        
        val middleEnd = if (incidentIndex != -1) incidentIndex else endIndex
        
        if (middleEnd > offset) {
            val middleParts = parts.subList(offset, middleEnd)
            
            for (part in middleParts) {
                // Heuristic: Address starts with Digit
                if (part.matches(Regex("^\\d+.*"))) {
                     if (part.length < 10 && part.contains("-")) {
                         // Likely Box Number (QN-5610) -> Add to Type or Extra
                         extra = if(extra.isEmpty()) part else "$extra $part"
                     } else {
                         address = part
                     }
                } else {
                     // Likely Incident Type (Text)
                     // Check if it's strictly alphanumeric codes
                     if (part.matches(Regex("^[A-Z]+\\d+$"))) {
                         extra = if(extra.isEmpty()) part else "$extra $part"
                     } else {
                         incidentType = if(incidentType.isEmpty()) part else "$incidentType - $part"
                     }
                }
            }
        }
        
        val details = if (incidentIndex != -1) parts[incidentIndex] else ""
        
        // Append Box/Extra to Type for clarity
        if (extra.isNotEmpty()) {
            incidentType = if (incidentType.isNotEmpty()) "$incidentType ($extra)" else extra
        }
        
        // Extracted Codes
        var fdCodesList = listOf<String>()
        // If we found a block with slashes, strip it
        for (i in offset..parts.lastIndex) {
             if (i == incidentIndex) continue
             if (parts[i].contains("/") && i != offset) { // Avoid city/county if they had slashes (rare)
                  fdCodesList = parts[i].split("/").map { it.trim() }
                         .filter { it.isNotBlank() && !it.equals("BNN", ignoreCase = true) && !it.equals("BNNDESK", ignoreCase = true) && !it.contains("BNN", ignoreCase = true) }
             }
        }

        return ParsedData(
            status = status,
            timestamp = "", // Server handles time
            incidentId = incidentId,
            state = state,
            county = county,
            city = city,
            address = address,
            incidentType = incidentType,
            incidentDetails = details,
            originalBody = fullText,
            fdCodes = fdCodesList
        )
    }
}
