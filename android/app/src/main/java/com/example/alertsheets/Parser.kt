package com.example.alertsheets

import java.util.regex.Pattern

object Parser {

    fun parse(fullText: String): ParsedData? {
        val text = fullText.replace("\r\n", "\n")
        val lines = text.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        
        val contentLine = lines.firstOrNull { it.contains("|") } ?: return null
        val parts = contentLine.split("|").map { it.trim() }
        
        // 1. Determine Status
        var status = if (lines.indexOf(contentLine) > 0) lines[0] else ""
        if (status.isBlank()) {
            if (fullText.startsWith("Update", ignoreCase = true) || fullText.contains("U/D", ignoreCase = true)) status = "Update"
            else if (fullText.startsWith("New Incident", ignoreCase = true)) status = "New Incident"
            else status = "New Incident"
        }
        
        // 2. Identify State & Location (Start Anchor)
        var state = parts.getOrElse(0) { "" }
        if (state.startsWith("U/D", ignoreCase = true)) {
            state = state.removePrefix("U/D").trim()
        }
        
        val boroughs = setOf("Queens", "Bronx", "Brooklyn", "Manhattan", "Staten Island", "New York")
        var county = ""
        var city = ""
        var startMiddleIndex = 3 // Default: State | County | City | [Start]
        
        val p1 = parts.getOrElse(1) { "" }
        val p2 = parts.getOrElse(2) { "" }
        
        if (state.equals("NY", ignoreCase = true) && boroughs.any { p1.equals(it, ignoreCase = true) }) {
            // New York Borough Case: State | Borough (City) | ...
            county = "" 
            city = p1
            startMiddleIndex = 2
        } else {
            // Standard Case: State | County | City | ...
            county = p1
            city = p2
            startMiddleIndex = 3
        }

        // 3. Identify ID & Source (End Anchor)
        // We look for the "<C> BNN" tag or similar source markers to pin the end of the content.
        var incidentId = ""
        var sourceIndex = -1
        
        // Find ID first (usually last)
        if (parts.isNotEmpty()) {
            val last = parts.last()
            val idMatcher = Pattern.compile("#?(1\\d{6})").matcher(last)
            if (idMatcher.find()) {
                 incidentId = if(last.startsWith("#")) last else "#$last"
            }
        }
        
        // Find Source Tag to anchor the Details
        for (i in parts.indices.reversed()) {
            if (parts[i].contains("<C> BNN", ignoreCase = true) || parts[i].equals("BNN", ignoreCase = true)) {
                sourceIndex = i
                break
            }
        }
        
        // Fallback: If no source tag, try to guess based on ID index
        if (sourceIndex == -1) {
             // If ID is at last index, maybe source is last-2 or last-3? 
             // Without a clear tag, we might have to rely on the "Details" heuristic (longest field)
        }

        // 4. Extract Details
        // Details is typically the field immediately preceding the Source.
        var detailsIndex = -1
        if (sourceIndex > 0) {
            detailsIndex = sourceIndex - 1
        } else {
            // Fallback: Find the longest field between startMiddle and End
            var maxLen = 0
            val searchEnd = if (parts.isNotEmpty()) parts.lastIndex - 1 else 0
            for (i in startMiddleIndex..searchEnd) {
                 if (parts[i].length > maxLen) {
                     maxLen = parts[i].length
                     detailsIndex = i
                 }
            }
        }
        
        val incidentDetails = if (detailsIndex != -1 && detailsIndex < parts.size) parts[detailsIndex] else ""
        
        // 5. Analyze Middle Fields (Address vs Type)
        // Fields between startMiddleIndex and detailsIndex
        var address = ""
        var incidentType = ""
        
        val streetSuffixes = listOf(
            "Ave", "Avenue", "St", "Street", "Rd", "Road", "Dr", "Drive", 
            "Ln", "Lane", "Pl", "Place", "Ct", "Court", "Cir", "Circle", 
            "Blvd", "Boulevard", "Way", "Ter", "Terrace", "Pkwy", "Parkway", 
            "Hwy", "Highway", "Tpke", "Turnpike", "Expy", "Expressway", "Pike"
        )
        
        if (detailsIndex > startMiddleIndex) {
            val middleParts = parts.subList(startMiddleIndex, detailsIndex)
            
            for (part in middleParts) {
                val p = part.trim()
                if (p.isEmpty()) continue
                
                // Detection Logic
                val hasDigitStart = p.firstOrNull()?.isDigit() == true
                val hasSuffix = streetSuffixes.any { p.contains(" $it", ignoreCase = true) || p.endsWith(" $it", ignoreCase = true) }
                val hasIntersection = p.contains(" & ") || p.contains(" and ", ignoreCase = true)
                
                // Short alphanumeric usually Box/Extra -> Append to Type
                // ex: QN-6738, NWK4439, 10-75
                val isBoxCode = p.length < 10 && p.any { it.isDigit() } && p.any { it.isLetter() } && (p.contains("-") || p == p.uppercase()) && !hasSuffix

                if ((hasDigitStart && hasSuffix) || hasIntersection || (hasDigitStart && !isBoxCode)) {
                    // Strong Address Signal
                    address = p
                } else if (isBoxCode) {
                    // It's a code, append to type
                    incidentType = if (incidentType.isEmpty()) p else "$incidentType ($p)"
                } else {
                    // Likely Description/Type (e.g. "Working Fire", "Tree Vs House")
                    incidentType = if (incidentType.isEmpty()) p else "$incidentType - $p"
                }
            }
        }
        
        // 6. Extract FD Codes
        // Typically after Source, or mixed in at end.
        val fdCodes = mutableListOf<String>()
        // Scan everything after Details (including Source parts if they have mixed content, and parts after Source)
        val codeStartIndex = if (detailsIndex != -1) detailsIndex + 1 else parts.lastIndex
        
        for (i in codeStartIndex until parts.size) {
            val p = parts[i]
            // Skip ID
            if (p == incidentId || p == incidentId.removePrefix("#")) continue
            
            // Clean BNN tags
            val clean = p.replace("<C> BNN", "", ignoreCase = true).replace("BNN", "", ignoreCase = true).trim()
            
            if (clean.contains("/")) {
                fdCodes.addAll(clean.split("/").map { it.trim() })
            } else if (clean.isNotBlank() && clean.length < 12 && !clean.contains(" ")) {
                 // Single code
                 fdCodes.add(clean)
            }
        }
        
        // Final Filter
        val finalCodes = fdCodes.filter { 
            it.isNotBlank() && 
            !it.equals("BNNDESK", ignoreCase = true) && 
            !it.equals("BNN", ignoreCase = true) &&
            !it.contains("BNN", ignoreCase = true) 
        }

        return ParsedData(
            status = status,
            timestamp = "", 
            incidentId = incidentId,
            state = state,
            county = county,
            city = city,
            address = address,
            incidentType = incidentType,
            incidentDetails = incidentDetails,
            originalBody = fullText,
            fdCodes = finalCodes
        )
    }
}
