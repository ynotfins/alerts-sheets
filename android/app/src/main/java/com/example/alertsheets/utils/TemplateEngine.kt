package com.example.alertsheets.utils

import android.util.Log
import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.Template
import com.example.alertsheets.domain.models.Source
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Template Engine for V2
 * 
 * Key differences from v1:
 * - Per-source auto-clean (not global!)
 * - Generic variable replacement (works with any template)
 * - Safe escaping via Gson (handles all unicode/emoji correctly)
 * - JSON validation before output
 */
object TemplateEngine {
    
    private const val TAG = "TemplateEngine"
    private val gson = Gson()
    
    /**
     * Result of template application
     */
    sealed class TemplateResult {
        data class Success(val json: String) : TemplateResult()
        data class InvalidJson(val json: String, val error: String) : TemplateResult()
        data class Error(val message: String) : TemplateResult()
    }
    
    /**
     * Apply template string with parsed data (safe version)
     * Returns TemplateResult indicating success or failure
     * 
     * @param templateContent The JSON template string
     * @param data Parsed notification data
     * @param source Source configuration (contains autoClean setting)
     */
    fun applySafe(templateContent: String, data: ParsedData, source: Source): TemplateResult {
        return try {
            val variableMap = data.toVariableMap()
            val result = applyVariables(templateContent, variableMap, source.autoClean)
            validateJson(result)
        } catch (e: Exception) {
            Log.e(TAG, "Template application failed", e)
            TemplateResult.Error("Template error: ${e.message}")
        }
    }
    
    /**
     * Apply template string with parsed data
     * Auto-clean is applied based on source configuration
     * 
     * @param templateContent The JSON template string
     * @param data Parsed notification data
     * @param source Source configuration (contains autoClean setting)
     * @return JSON string (may be invalid if template/data has issues)
     * 
     * NOTE: Prefer applySafe() for production use
     */
    fun apply(templateContent: String, data: ParsedData, source: Source): String {
        val variableMap = data.toVariableMap()
        return applyVariables(templateContent, variableMap, source.autoClean)
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
     * Apply template with generic variables (safe version)
     */
    fun applyGenericSafe(
        template: String,
        variables: Map<String, String>,
        autoClean: Boolean = false
    ): TemplateResult {
        return try {
            val result = applyVariables(template, variables, autoClean)
            validateJson(result)
        } catch (e: Exception) {
            Log.e(TAG, "Template application failed", e)
            TemplateResult.Error("Template error: ${e.message}")
        }
    }
    
    /**
     * Validate that a string is valid JSON
     * 
     * @param json The string to validate
     * @return TemplateResult.Success if valid, TemplateResult.InvalidJson if not
     */
    fun validateJson(json: String): TemplateResult {
        return try {
            JsonParser.parseString(json)
            TemplateResult.Success(json)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Invalid JSON produced: ${e.message}\nJSON: ${json.take(500)}")
            TemplateResult.InvalidJson(json, e.message ?: "JSON syntax error")
        }
    }
    
    /**
     * Check if a string is valid JSON
     */
    fun isValidJson(json: String): Boolean {
        return try {
            JsonParser.parseString(json)
            true
        } catch (e: JsonSyntaxException) {
            false
        }
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
                
                // Special handling: if value looks like JSON (array/object), don't escape
                // This allows {{fdCodes}} to be replaced with ["code1", "code2"]
                val finalValue = if (isJsonValue(cleanValue)) {
                    cleanValue
                } else {
                    // Escape for JSON string context
                    escapeForJson(cleanValue)
                }
                
                result = result.replace(placeholder, finalValue)
            }
        }
        
        return result
    }
    
    /**
     * Check if a string looks like a JSON value (array or object)
     * Used to avoid double-escaping pre-serialized JSON
     */
    private fun isJsonValue(value: String): Boolean {
        val trimmed = value.trim()
        return (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
               (trimmed.startsWith("{") && trimmed.endsWith("}"))
    }
    
    /**
     * Escape string for JSON string context using Gson
     * 
     * This is safer than manual replacement because Gson handles:
     * - All unicode characters correctly
     * - Surrogate pairs (emoji)
     * - Control characters
     * - Edge cases like lone surrogates
     * 
     * NOTE: Gson.toJson() adds surrounding quotes, so we strip them
     */
    private fun escapeForJson(s: String): String {
        val quoted = gson.toJson(s)
        // Remove surrounding quotes added by Gson
        return if (quoted.length >= 2 && quoted.startsWith("\"") && quoted.endsWith("\"")) {
            quoted.substring(1, quoted.length - 1)
        } else {
            quoted
        }
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

