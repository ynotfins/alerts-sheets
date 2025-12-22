package com.example.alertsheets.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * Centralized payload serialization utility
 * 
 * Provides safe JSON serialization for all payloads sent to endpoints.
 * Uses Gson with strict configuration to ensure:
 * - Proper unicode/emoji handling
 * - No HTML escaping (Apps Script doesn't need it)
 * - Null safety
 * - Schema versioning for stored data
 * 
 * @since Step A refactor (Dec 2024)
 */
object PayloadSerializer {
    
    private const val TAG = "PayloadSerializer"
    
    /**
     * Schema versions for stored data
     * Increment when changing the structure of persisted objects
     */
    object SchemaVersion {
        const val ENDPOINTS = 2          // List<Endpoint> in SharedPreferences
        const val SMS_TARGETS = 2        // List<SmsTarget> in SharedPreferences
        const val TEMPLATES = 2          // List<JsonTemplate> in SharedPreferences
        const val APP_CONFIGS = 2        // AppConfig objects in SharedPreferences
        const val LOG_ENTRIES = 1        // List<LogEntry> in SharedPreferences
        const val SOURCES = 1            // V2 Source system
    }
    
    /**
     * Gson instance configured for safe payload serialization
     * 
     * Configuration:
     * - disableHtmlEscaping: Don't escape < > & (not needed for JSON APIs)
     * - serializeNulls: Include null fields explicitly
     * - Standard date format
     */
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .serializeNulls()
        .create()
    
    /**
     * Gson instance for pretty-printed output (debugging/UI display)
     */
    private val gsonPretty: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .serializeNulls()
        .setPrettyPrinting()
        .create()
    
    /**
     * Serialize any object to JSON string
     * 
     * @param obj The object to serialize
     * @return JSON string representation
     */
    fun toJson(obj: Any?): String {
        return try {
            gson.toJson(obj)
        } catch (e: Exception) {
            Log.e(TAG, "Serialization failed", e)
            "{\"error\": \"serialization_failed\"}"
        }
    }
    
    /**
     * Serialize object to pretty-printed JSON (for UI/debugging)
     */
    fun toPrettyJson(obj: Any?): String {
        return try {
            gsonPretty.toJson(obj)
        } catch (e: Exception) {
            Log.e(TAG, "Pretty serialization failed", e)
            "{\n  \"error\": \"serialization_failed\"\n}"
        }
    }
    
    /**
     * Deserialize JSON string to object
     * 
     * @param json The JSON string
     * @param clazz The target class
     * @return Deserialized object or null if failed
     */
    fun <T> fromJson(json: String, clazz: Class<T>): T? {
        return try {
            gson.fromJson(json, clazz)
        } catch (e: Exception) {
            Log.e(TAG, "Deserialization failed for ${clazz.simpleName}", e)
            null
        }
    }
    
    /**
     * Validate that a string is valid JSON
     * 
     * @param json The string to validate
     * @return true if valid JSON, false otherwise
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
     * Validate JSON and return detailed result
     * 
     * @param json The string to validate
     * @return ValidationResult with success status and error details
     */
    fun validateJson(json: String): ValidationResult {
        return try {
            JsonParser.parseString(json)
            ValidationResult(true, null)
        } catch (e: JsonSyntaxException) {
            ValidationResult(false, e.message)
        }
    }
    
    /**
     * Escape a string for safe inclusion in JSON string context
     * Uses Gson internally for proper unicode handling
     * 
     * @param value The string to escape
     * @return Escaped string (without surrounding quotes)
     */
    fun escapeJsonString(value: String): String {
        val quoted = gson.toJson(value)
        // Remove surrounding quotes added by Gson
        return if (quoted.length >= 2 && quoted.startsWith("\"") && quoted.endsWith("\"")) {
            quoted.substring(1, quoted.length - 1)
        } else {
            quoted
        }
    }
    
    /**
     * Create a versioned wrapper for stored data
     * This allows migration logic to detect schema changes
     * 
     * @param data The data to wrap
     * @param version The schema version
     * @return VersionedData wrapper
     */
    fun <T> wrapWithVersion(data: T, version: Int): VersionedData<T> {
        return VersionedData(version, data)
    }
    
    /**
     * Result of JSON validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val error: String?
    )
    
    /**
     * Wrapper for versioned data storage
     * 
     * When stored data structure changes, increment the version and
     * add migration logic in MigrationManager.
     */
    data class VersionedData<T>(
        val schemaVersion: Int,
        val data: T
    )
    
    /**
     * Get the shared Gson instance
     * Use this when you need direct Gson access (e.g., for TypeToken)
     */
    fun getGson(): Gson = gson
}

