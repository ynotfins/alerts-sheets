package com.example.alertsheets.utils

/**
 * Normalizes SMS sender identifiers for consistent matching
 * 
 * Problem: SMS sources are stored as "sms:+1 888-660-1455" but incoming 
 * senders may be "+18886601455", "888-660-1455", or other formats.
 * 
 * Solution: Strip "sms:" prefix and keep only digits for comparison.
 */
object SmsSenderNormalizer {
    
    /**
     * Normalize to comparable digits only
     * 
     * Examples:
     * - "sms:+1 888-660-1455" → "18886601455"
     * - "+1-888-660-1455" → "18886601455"
     * - "888-660-1455" → "8886601455"
     * - "sms:dispatch" → "dispatch" (keeps non-digit sources as-is)
     */
    fun normalize(input: String): String {
        // Remove "sms:" prefix if present
        val withoutPrefix = input.removePrefix("sms:")
        
        // Extract only digits
        val digitsOnly = withoutPrefix.filter { it.isDigit() }
        
        // If no digits, return original (for generic sources like "sms:dispatch")
        return digitsOnly.ifEmpty { withoutPrefix.trim() }
    }
    
    /**
     * Try matching by last N digits (fallback for country code mismatches)
     * 
     * Returns the unique match if exactly one source matches the suffix.
     * Returns null if zero or multiple matches (ambiguous).
     */
    fun <T> findBySuffix(
        candidates: List<T>,
        targetSender: String,
        suffixLength: Int = 10,
        getId: (T) -> String
    ): T? {
        val targetDigits = normalize(targetSender)
        if (targetDigits.length < suffixLength) return null
        
        val targetSuffix = targetDigits.takeLast(suffixLength)
        
        val matches = candidates.filter { candidate ->
            val candidateDigits = normalize(getId(candidate))
            candidateDigits.endsWith(targetSuffix) && candidateDigits.length >= suffixLength
        }
        
        // Only return if exactly one match (unambiguous)
        return if (matches.size == 1) matches.first() else null
    }
    
    /**
     * Get sender "shape" for logging (no PII)
     * 
     * Examples:
     * - "+18886601455" → "hasPlus=true digitsLen=11"
     * - "888-660-1455" → "hasPlus=false digitsLen=10"
     */
    fun getSenderShape(sender: String): String {
        val hasPlus = sender.startsWith("+")
        val digitsLen = sender.filter { it.isDigit() }.length
        return "hasPlus=$hasPlus digitsLen=$digitsLen"
    }
}

