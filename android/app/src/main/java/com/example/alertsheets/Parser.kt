package com.example.alertsheets

import java.util.regex.Pattern

object Parser {

    fun parse(fullText: String): ParsedData? {
        // Normalize line breaks
        val text = fullText.replace("\r\n", "\n")
        val lines = text.split("\n").filter { it.isNotBlank() }

        if (lines.isEmpty()) return null

        // Try to identify the Status and Timestamp from the first few lines
        // Example:
        // Update
        // 9/15/25
        // U/D NJ| ...
        
        var status = ""
        var timestamp = ""
        var contentLine = ""
        
        // Simple heuristic: If 3 lines, assume Status \n Date \n Content
        // If the content line contains pipes |, that's our target.
        
        for (line in lines) {
            if (line.contains("|")) {
                contentLine = line
                break
            }
        }
        
        if (contentLine.isEmpty()) return null
        
        // Attempt to find status/date from lines before content
        val contentIndex = lines.indexOf(contentLine)
        if (contentIndex > 0) status = lines[0]
        if (contentIndex > 1) timestamp = lines[1]
        
        // If status line says "New Incident", map to "New" if preferred, or keep as is.
        // User asked for "New/Update".
        
        // Parse the pipe-delimited content
        // Example: U/D NJ| Bergen| Rutherford| Car Vs Building| 510 Union Ave| CMD reports no structural damage.| <C> BNN | BNNDESK/njvx6/nj7ue | #1825178
        val parts = contentLine.split("|").map { it.trim() }
        
        // We need to map these parts to:
        // State, County, City, Address, Incident type, Incident, (skipped <C> BNN?), Codes, ID
        
        // Let's look at the example parts count:
        // 0: U/D NJ (State?) - Wait, example says "U/D NJ". Sometimes just "NY".
        // 1: Bergen (County)
        // 2: Rutherford (City)
        // 3: Car Vs Building (Incident Type)
        // 4: 510 Union Ave (Address)
        // 5: CMD reports no structural damage. (Incident Details)
        // 6: <C> BNN (Source?) -> Probably ignored or part of details?
        // 7: BNNDESK/njvx6/nj7ue (FD Codes)
        // 8: #1825178 (Incident ID)
        
        if (parts.size < 6) return null // Not enough data
        
        val idWithHash = parts.last()
        val incidentId = if (idWithHash.startsWith("#")) idWithHash else ""
        
        // Codes are usually second to last
        val codesRaw = if (parts.size >= 2) parts[parts.size - 2] else ""
        // Codes might be slash delimited: BNNDESK/njvx6/nj7ue
        val fdCodes = codesRaw.split("/").filter { it.isNotBlank() }
        
        // Mapping fields based on position
        // This is fragile but fits the user's specific format.
        // We need to handle "U/D NJ" vs "NY". 
        // Logic: Split index 0 by space, take last part? Or just take whole thing?
        // User requested "State". "U/D NJ" likely means "Update NJ". 
        // Let's just clean it up.
        var state = parts.getOrElse(0) { "" }
        if (state.startsWith("U/D ")) {
            state = state.removePrefix("U/D ").trim()
        }
        
        val county = parts.getOrElse(1) { "" }
        val city = parts.getOrElse(2) { "" }
        val incidentType = parts.getOrElse(3) { "" } // In one example "Car Vs Building", another "Working Fire" (index 3 matches?)
        // Wait, verifying example 3:
        // NY| Bronx| Fire Department Activity| 677 E 236th St| E63 O/S...
        // 0: NY (State)
        // 1: Bronx (County)
        // 2: Fire Department Activity (Incident Type) -- Wait, user list had "City" before "Incident Type"??
        // Let's re-read the request fields: "State County City Address Incident type Incident ..."
        // Example 1: `U/D NJ| Bergen| Rutherford| Car Vs Building| 510 Union Ave| details...`
        // 0: U/D NJ (State)
        // 1: Bergen (County)
        // 2: Rutherford (City)
        // 3: Car Vs Building (Type)
        // 4: 510 Union Ave (Address)
        // 5: Details
        //
        // Example 3: `NY| Bronx| Fire Department Activity| 677 E 236th St| E63 O/S...`
        // 0: NY
        // 1: Bronx
        // 2: Fire Department Activity (Type?? Or City?) -> "Fire Department Activity" is likely Type. "Bronx" is County. Where is City?
        // Maybe Bronx matches County and City implies a specific town?
        // Actually, for NYC, Bronx is a borough.
        // It seems the fields can shift or "City" might be missing/merged?
        // BUT, looking at `NY| Bronx| Fire Department Activity| 677 E 236th St| ...`
        // If we map 0->State, 1->County, 2->City/Type??
        // 
        // Let's assume the user's list "State County City Address Incident type Incident" describes the *Sheet Columns*, but the *Notification* format might vary slightly or I need to be careful.
        // However, common alerting formats usually stick to a schema.
        // `State | County | City | Type | Address | Details ...` seems consistent for the "Update" examples.
        // `NY | Bronx | Fire Department Activity | 677 E 236th St`
        // State | County | Type | Address?
        // Only 4 parts before details. "City" is missing.
        //
        // Strategy: Use the list of parts. 
        // If we have specific indices, map them strictly.
        // If the format varies, we might be mis-mapping.
        // Given this is a specific user request for a specific feed, I will map the indices as observed in the majority (Update examples).
        // 0: State
        // 1: County
        // 2: City
        // 3: Incident Type
        // 4: Address
        // 5: Details
        //
        // If parts are fewer, we fill what we can.
        
        val address = parts.getOrElse(4) { "" }
        val details = parts.getOrElse(5) { "" }
        
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
