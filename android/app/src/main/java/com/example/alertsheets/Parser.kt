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
        
        // 2. FD Codes: Always lowercase, slashed, after source (BNN/BNNDESK).
        // They look like "njvx6/nj7ue".
        var fdCodes = listOf<String>()
        var codesIndex = -1
        
        // Search for the codes field (usually before ID)
        if (idIndex != -1) {
            // Check immediate predecessor for codes
            // But sometimes Source is between them? "BNN | BNNDESK/..."
            // Let's just look for a part that contains "/" and looks like codes.
            for (i in parts.indices.reversed()) {
                if (i == idIndex) continue
                if (parts[i].contains("/") && parts[i].all { it.isLowerCase() || it.isDigit() || it == '/' }) {
                    fdCodes = parts[i].split("/").filter { it.isNotBlank() }
                    codesIndex = i
                    break
                }
            }
        }
        
        // 3. Map the Standard Fields (State, County, City, Address, Type, Details)
        // We assume the first 6 indices correspond to these if available, considering "Source" might be in there.
        // Schema Request: State, County, City, Address, Incident Type, Incident Details.
        // Typical BNN pipe: Status/State | County | City | Type | Address | Details | Source | Codes | ID
        
        // Let's safe-get based on indices, but handle "Status/State" (U/D NJ) cleanup.
        
        var state = parts.getOrElse(0) { "" }
        if (state.startsWith("U/D ")) state = state.removePrefix("U/D ").trim()
        
        val county = parts.getOrElse(1) { "" }
        val city = parts.getOrElse(2) { "" }
        
        // Indices 3 and 4 can swap based on format "Type | Address" vs "Address | Type".
        // Address usually starts with a number. Type is text.
        val p3 = parts.getOrElse(3) { "" }
        val p4 = parts.getOrElse(4) { "" }
        
        var address = ""
        var incidentType = ""
        
        if (p3.firstOrNull()?.isDigit() == true) {
            address = p3
            incidentType = p4
        } else {
            incidentType = p3
            address = p4
        }
        
        val details = parts.getOrElse(5) { "" }
        
        // Verify if details is actually Source (BNN)
        // If details == "BNN", then details might be empty or in index 6?
        // Let's just grab index 5 as details for now, user can map it.
        
        return ParsedData(
            status = status, // Extracted from line 0 typically "Update" or "New Incident"
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
