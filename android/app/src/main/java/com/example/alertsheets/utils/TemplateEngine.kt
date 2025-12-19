package com.example.alertsheets.utils

import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.Template
import com.example.alertsheets.domain.models.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Template Engine for V2
 * 
 * Key differences from v1:
 * - Per-source auto-clean (not global!)
 * - Generic variable replacement (works with any template)
 * - Better escaping
 */
object TemplateEngine {
    
    /**
     * Apply template with parsed data
     * Auto-clean is applied based on source configuration
     */
    fun apply(template: Template, data: ParsedData, source: Source): String {
        val variableMap = data.toVariableMap()
        return applyVariables(template.content, variableMap, source.autoClean)
    }
    
    /**
     * Apply template with generic variables (for testing)
     */
    fun applyGeneric(
        template: String,
        variables: Map<String, String>,
        autoClean: Boolean = false
    ): String {
        return applyVariables(template, variables, autoClean)
    }
    
    /**
     * Core variable replacement logic
     * 
     * @param template JSON template with {{variables}}
     * @param variables Map of variable name to value
     * @param autoClean If true, clean emojis/special chars before replacement
     */
    private fun applyVariables(
        template: String,
        variables: Map<String, String>,
        autoClean: Boolean
    ): String {
        var result = template
        
        // Add timestamp variables
        val allVariables = variables + mapOf(
            "time" to getTime(),
            "timestamp" to getTimestamp()
        )
        
        // Replace each variable
        for ((key, value) in allVariables) {
            val placeholder = "{{$key}}"
            if (result.contains(placeholder)) {
                // Apply cleaning if enabled for this source
                val cleanValue = if (autoClean) cleanText(value) else value
                // Escape for JSON
                val escapedValue = escape(cleanValue)
                result = result.replace(placeholder, escapedValue)
            }
        }
        
        return result
    }
    
    /**
     * Escape string for JSON
     * Handles quotes, backslashes, newlines, control characters
     */
    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
    }
    
    /**
     * Clean text by removing emojis and non-standard characters
     * Keeps ASCII, common punctuation, and basic Latin characters
     * 
     * This is applied PER-SOURCE based on Source.autoClean setting
     */
    fun cleanText(input: String): String {
        // Remove surrogates (emojis)
        val cleaned = input.replace(Regex("[\uD800-\uDFFF]"), "")
            // Remove non-ASCII symbols
            .replace(Regex("[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}&&[^\\p{Ascii}]]"), "")
            // Remove control chars except \n, \r, \t
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")
        return cleaned.trim()
    }
    
    /**
     * Get current time (24-hour format)
     */
    fun getTime(): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }
    
    /**
     * Get human-readable timestamp (12-hour AM/PM format)
     */
    fun getTimestamp(): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)
        return sdf.format(Date())
    }
}

