package com.example.alertsheets.domain.parsers

import android.util.Log
import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.RawNotification
import com.example.alertsheets.domain.models.Source
import java.util.regex.Pattern

/**
 * BNN Incident Parser
 * Handles pipe-delimited BNN notification format
 * 
 * Ported from v1 Parser.kt with all verified fixes:
 * - Fix 1: Ensures # prefix for incident ID
 * - Fix 2: NYC boroughs function as both county and city
 * - Fix 3: Strong FD code filtering
 */
class BnnParser : Parser {
    
    override val id = "bnn"
    override val name = "BNN Incident Parser"
    
    private val TAG = "BnnParser"
    
    override fun canParse(source: Source, raw: RawNotification): Boolean {
        // BNN notifications have pipe-delimited format
        return raw.fullText.contains("|")
    }
    
    override fun parse(raw: RawNotification): ParsedData? {
        val fullText = raw.fullText
        
        if (fullText.isBlank()) return null
        
        try {
            val text = fullText.replace("\r\n", "\n")
            val lines = text.split("\n").filter { it.isNotBlank() }
            
            if (lines.isEmpty()) {
                Log.w(TAG, "Empty text lines, cannot parse.")
                return null
            }
            
            // Find the main content line (pipe delimited)
            val contentLine = lines.firstOrNull { it.contains("|") }
            if (contentLine == null) {
                Log.w(TAG, "No pipe delimiter found in: $fullText")
                return null
            }
            
            val contentLineIndex = lines.indexOf(contentLine)
            val parts = contentLine.split("|").map { it.trim() }
            Log.d(TAG, "Parsing ${parts.size} segments.")
            
            // --- 1. Determine Status ---
            var status = "New Incident"
            val p0 = parts.getOrElse(0) { "" }.trim()
            
            if (p0.startsWith("U/D", ignoreCase = true) ||
                p0.startsWith("Update", ignoreCase = true)
            ) {
                status = "Update"
            } else if (p0.startsWith("N/D", ignoreCase = true) ||
                p0.startsWith("New", ignoreCase = true)
            ) {
                status = "New Incident"
            } else if (contentLineIndex > 0) {
                // Check previous lines
                val prevLines = lines.subList(0, contentLineIndex)
                for (line in prevLines) {
                    val l = line.trim()
                    if (l.equals("Update", ignoreCase = true) ||
                        l.startsWith("U/D", ignoreCase = true)
                    ) {
                        status = "Update"
                        break
                    }
                }
            }
            
            // --- 2. Identify State & Location ---
            var state = p0
            // Clean prefixes from state string
            if (state.startsWith("U/D", ignoreCase = true)) {
                val split = state.split(" ", limit = 2)
                state = if (split.size > 1) split[1].trim() else state.removePrefix("U/D").trim()
            } else if (state.startsWith("N/D", ignoreCase = true)) {
                val split = state.split(" ", limit = 2)
                state = if (split.size > 1) split[1].trim() else state.removePrefix("N/D").trim()
            } else if (state.startsWith("Update", ignoreCase = true)) {
                val split = state.split(" ", limit = 2)
                state = if (split.size > 1) split[1].trim() else state.removePrefix("Update").trim()
            }
            
            // Further sanitization
            state = state.replace("U/D", "", ignoreCase = true).trim()
            
            // Boroughs
            val boroughs = setOf("Queens", "Bronx", "Brooklyn", "Manhattan", "Staten Island", "New York")
            var county = ""
            var city = ""
            var startMiddleIndex = 3
            
            val p1 = parts.getOrElse(1) { "" }.trim()
            val p2 = parts.getOrElse(2) { "" }.trim()
            
            if (state.equals("NY", ignoreCase = true) &&
                boroughs.any { p1.equals(it, ignoreCase = true) }
            ) {
                // Fix 2: NYC boroughs function as both County and City
                county = p1
                city = p1
                startMiddleIndex = 2
            } else {
                county = p1
                city = p2
                startMiddleIndex = 3
            }
            
            // --- 3. Identify ID & Source ---
            var incidentId = ""
            var sourceIndex = -1
            
            // Find ID (Hash + 7 digits)
            for (i in parts.indices.reversed()) {
                val p = parts[i]
                val idMatcher = Pattern.compile("(#?1\\d{6})").matcher(p)
                if (idMatcher.find()) {
                    incidentId = idMatcher.group(1) ?: ""
                    // Fix 1: Ensure Hash is present!
                    if (!incidentId.startsWith("#")) {
                        incidentId = "#$incidentId"
                    }
                    break
                }
            }
            
            // Fallback ID
            if (incidentId.isEmpty()) {
                val hash = Math.abs(fullText.hashCode())
                incidentId = "#${hash.toString().takeLast(7).padStart(7, '0')}"
            }
            
            // Find Source Tag
            for (i in parts.indices.reversed()) {
                if (parts[i].contains("<C> BNN", ignoreCase = true) ||
                    parts[i].equals("BNN", ignoreCase = true)
                ) {
                    sourceIndex = i
                    break
                }
            }
            
            // --- 4. Extract Details ---
            var detailsIndex = -1
            if (sourceIndex > 0) {
                detailsIndex = sourceIndex - 1
            } else {
                // Heuristic: Longest field between startMiddle and End
                var maxLen = 0
                val searchEnd = if (parts.isNotEmpty()) parts.lastIndex else 0
                for (i in startMiddleIndex..searchEnd) {
                    if (parts[i].contains(incidentId) && parts[i].length < 15) continue
                    
                    if (parts[i].length > maxLen) {
                        maxLen = parts[i].length
                        detailsIndex = i
                    }
                }
            }
            val incidentDetails = if (detailsIndex != -1 && detailsIndex < parts.size) parts[detailsIndex] else ""
            
            // --- 5. Middle Fields (Address/Type) ---
            var address = ""
            var incidentType = ""
            
            try {
                if (detailsIndex > startMiddleIndex) {
                    val middleParts = parts.subList(startMiddleIndex, detailsIndex)
                    
                    val streetSuffixes = listOf(
                        "Ave", "St", "Rd", "Dr", "Ln", "Pl", "Ct", "Cir", "Blvd", "Way",
                        "Ter", "Pkwy", "Hwy", "Tpke", "Expy", "Pike", "Avenue", "Street",
                        "Road", "Drive", "Lane", "Place", "Court", "Circle", "Boulevard",
                        "Terrace", "Parkway", "Highway", "Turnpike", "Expressway"
                    )
                    
                    val typeKeywords = listOf(
                        "Fire", "Alarm", "MVA", "Crash", "Accident", "Rescue", "EMS",
                        "Gas", "Leak", "Smoke", "Odor", "Police", "Activity", "Standby",
                        "Detail", "Collapse", "HazMat", "Invest", "Shot", "Stab", "Medical"
                    )
                    
                    for (part in middleParts) {
                        val p = part.trim()
                        if (p.isEmpty()) continue
                        
                        val hasDigitStart = p.firstOrNull()?.isDigit() == true
                        val hasSuffix = streetSuffixes.any {
                            p.contains(" $it", ignoreCase = true) || p.endsWith(it, ignoreCase = true)
                        }
                        val hasIntersection = p.contains("&") || p.contains(" and ", ignoreCase = true)
                        
                        val isCode = p.length < 15 &&
                            p.any { it.isDigit() } &&
                            (p.contains("-") || p == p.uppercase()) &&
                            !hasSuffix
                        
                        val looksLikeAddress = (hasDigitStart && hasSuffix) ||
                            hasIntersection ||
                            (hasDigitStart && !isCode)
                        val looksLikeType = typeKeywords.any { p.contains(it, ignoreCase = true) }
                        
                        if (looksLikeAddress && !looksLikeType) {
                            address = if (address.isEmpty()) p else "$address, $p"
                        } else if (looksLikeType) {
                            incidentType = if (incidentType.isEmpty()) p else "$incidentType - $p"
                        } else {
                            // Ambiguous
                            if (address.isEmpty() && incidentType.isNotEmpty()) {
                                address = p
                            } else if (address.isNotEmpty() && incidentType.isEmpty()) {
                                incidentType = p
                            } else {
                                if (p.length < 20)
                                    incidentType = if (incidentType.isEmpty()) p else "$incidentType - $p"
                                else address = if (address.isEmpty()) p else "$address, $p"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in middle field parsing: ${e.message}")
            }
            
            // --- 6. FD Codes ---
            val fdCodes = mutableListOf<String>()
            
            try {
                val codeSearchStart = if (detailsIndex != -1) detailsIndex + 1 else startMiddleIndex
                
                for (i in codeSearchStart until parts.size) {
                    val section = parts[i]
                    val cleanSection = section.replace("<C> BNN", " ", ignoreCase = true)
                        .replace("BNN", " ", ignoreCase = true)
                        .trim()
                    
                    val tokens = cleanSection.split("/", " ").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    for (token in tokens) {
                        // Fix 3: Strong filtering
                        val lowerToken = token.lowercase()
                        
                        if (lowerToken.contains("bnndesk")) continue
                        if (lowerToken.contains("desk")) continue
                        if (lowerToken.contains("bnn")) continue
                        if (lowerToken.contains("<c>")) continue
                        if (lowerToken == "|") continue
                        
                        // ID checks
                        if (token == incidentId ||
                            token == "#$incidentId" ||
                            "#$token" == incidentId
                        ) continue
                        if (token.contains("#") && token.length > 5) continue
                        
                        // Skip pure numbers or empty
                        if (lowerToken.isEmpty() || lowerToken.all { it.isDigit() }) continue
                        
                        if (token.length > 20) continue // Too long to be a code
                        
                        fdCodes.add(token)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error extracting FD codes: ${e.message}")
            }
            
            Log.d(TAG, "✓ Parsed: ID=$incidentId, State=$state, City=$city")
            
            return ParsedData(
                incidentId = incidentId,
                state = state,
                county = county,
                city = city,
                address = address,
                incidentType = incidentType.trim(' ', '-'),
                incidentDetails = incidentDetails,
                originalBody = fullText,
                fdCodes = fdCodes.distinct(),
                timestamp = "" // Will be set by pipeline
            )
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL PARSING ERROR: ${e.message}", e)
            return null
        }
    }
}

