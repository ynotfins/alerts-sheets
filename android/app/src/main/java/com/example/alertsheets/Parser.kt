package com.example.alertsheets

import java.util.regex.Pattern

object Parser {

    fun parse(fullText: String): ParsedData? {
        // Normalize line breaks
        val text = fullText.replace("\r\n", "\n")
        val lines = text.split("\n").filter { it.isNotBlank() }

        if (lines.isEmpty()) return null
        
        // Find the content line (contains pipes)
        val contentLine = lines.firstOrNull { it.contains("|") } ?: return null
        
        // Attempt to find status/date from lines before content
        var status = ""
        var timestamp = ""
        val contentIndex = lines.indexOf(contentLine)
        if (contentIndex > 0) status = lines[0]
        if (contentIndex > 1) timestamp = lines[1]
        
        val parts = contentLine.split("|").map { it.trim() }
        
        // BNN Format Strategy:
        // We anchor off the known distinct fields at the end: ID and FD Codes.
        
        // 1. Alert ID: Always 7 digit number starting with 1, usually with # prefix or just at the end.
        // Regex: #?1\d{6}
        val idPattern = Pattern.compile("#?(1\\d{6})")
        var incidentId = ""
        var idIndex = -1
        
        // Scan from end to start to find ID
        for (i in parts.indices.reversed()) {
            val matcher = idPattern.matcher(parts[i])
            if (matcher.find()) {
                incidentId = matcher.group(1) ?: parts[i]
                if (!incidentId.startsWith("#")) incidentId = "#$incidentId"
                idIndex = i
                break
            }
        }
        
        // 2. FD Codes: Separated by slashes. Can be mixed case.
        // Example: BNNDESK/njvx6/nj7ue
        var fdCodes = listOf<String>()
        var codesIndex = -1
        
        // Search for the codes field (usually before ID)
        if (idIndex != -1) {
            // Check immediate predecessor for codes or search backwards
            for (i in parts.indices.reversed()) {
                if (i == idIndex) continue
                // It must contain a slash to be a list of codes, or looks like a short code
                // Relaxed check: contains / or matches typical code pattern
                if (parts[i].contains("/") || (parts[i].length < 10 && parts[i].any { it.isDigit() })) {
                    // Split and clean
                    val candidates = parts[i].split("/").map { it.trim() }
                    
                    // Filter out BNN, BNNDESK, empty, or pure punctuation
                    val validCodes = candidates.filter { code ->
                        val lower = code.lowercase()
                        lower.isNotBlank() && 
                        lower != "bnn" && 
                        lower != "bnndesk" &&
                        lower != "<c>" && 
                        !lower.contains("bnn") // catch desk if specific
                    }
                    
                    if (validCodes.isNotEmpty()) {
                        fdCodes = validCodes
                        codesIndex = i
                        break
                    }
                }
            }
        }
        
        // 3. Map the Standard Fields (State, County, City, Address, Type, Details)
        
        var state = parts.getOrElse(0) { "" }
        if (state.startsWith("U/D ")) state = state.removePrefix("U/D ").trim()
        
        val county = parts.getOrElse(1) { "" }
        val city = parts.getOrElse(2) { "" }
        
        val p3 = parts.getOrElse(3) { "" }
        val p4 = parts.getOrElse(4) { "" }
        
        var address = ""
        var incidentType = ""
        
        // Contextual Guess: Address usually starts with digit
        if (p3.firstOrNull()?.isDigit() == true) {
            address = p3
            incidentType = p4
        } else {
            incidentType = p3
            address = p4
        }
        
        // Details: Usually index 5, but if we found codes at 5, it might be earlier?
        // If codesIndex is 5, details might be 4?
        // Let's stick to safe get for now details is everything else.
        var details = parts.getOrElse(5) { "" }
        
        // Cleanup: If Details is just the code string or Source, clear it.
        if (details.contains("/") && details.contains("BNN")) {
             // likely misidentified codes as details
             // check if we have a real details string earlier
        }
        
        // Final cleanup of codes (User said "remove BNN and BNNDESK")
        fdCodes = fdCodes.filter { !it.equals("bnn", ignoreCase = true) && !it.equals("bnndesk", ignoreCase = true) }
        
        return ParsedData(
            status = status,
            timestamp = timestamp,
            incidentId = incidentId,
            state = state,
            county = county,
            city = city,
            address = address,
            incidentType = incidentType,
            incidentDetails = details,
            originalBody = fullText,
            fdCodes = fdCodes
        )
    }
}
