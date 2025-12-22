package com.example.alertsheets.data.repositories

import android.content.Context
import android.util.Log
import com.example.alertsheets.data.storage.JsonStorage
import com.example.alertsheets.domain.models.Endpoint
import com.example.alertsheets.domain.models.EndpointStats
import com.example.alertsheets.utils.AppConstants
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.IOException

/**
 * Repository for managing Endpoints - V2 Migrated
 * 
 * ✅ NOW USES:
 * - JsonStorage (not PrefsManager!)
 * - domain.models.Endpoint (V2 with id, stats, etc.)
 * - Proper error handling
 * - Default endpoint creation
 * 
 * STORAGE: endpoints.json
 */
class EndpointRepository(private val context: Context) {
    
    private val TAG = "EndpointRepository"
    private val storage = JsonStorage(context, AppConstants.FILE_ENDPOINTS)
    private val gson = Gson()
    
    /**
     * Get all endpoints
     */
    fun getAll(): List<Endpoint> {
        return try {
            val json = storage.read()
            if (json == null) {
                Log.i(TAG, "No endpoints file found, creating default")
                val defaults = listOf(createDefaultEndpoint())
                saveAll(defaults)
                return defaults
            }
            
            val type = object : TypeToken<List<Endpoint>>() {}.type
            gson.fromJson<List<Endpoint>>(json, type) ?: emptyList()
            
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse endpoints JSON", e)
            emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read endpoints", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading endpoints", e)
            emptyList()
        }
    }
    
    /**
     * Get endpoint by ID
     */
    fun getById(endpointId: String): Endpoint? {
        return try {
            getAll().firstOrNull { it.id == endpointId }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find endpoint by ID: $endpointId", e)
            null
        }
    }
    
    /**
     * Get enabled endpoints only
     */
    fun getEnabled(): List<Endpoint> {
        return try {
            getAll().filter { it.enabled }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load enabled endpoints", e)
            emptyList()
        }
    }
    
    /**
     * Get endpoint by URL (for backward compatibility)
     */
    fun getByUrl(url: String): Endpoint? {
        return try {
            getAll().firstOrNull { it.url == url }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find endpoint by URL: $url", e)
            null
        }
    }
    
    /**
     * Save endpoint (create or update)
     */
    fun save(endpoint: Endpoint) {
        try {
            val all = getAll().toMutableList()
            
            // Check if endpoint exists
            val index = all.indexOfFirst { it.id == endpoint.id }
            
            if (index >= 0) {
                // Update existing
                all[index] = endpoint.copy(updatedAt = System.currentTimeMillis())
                Log.d(TAG, "Updating endpoint: ${endpoint.name}")
            } else {
                // Add new
                all.add(endpoint)
                Log.d(TAG, "Creating new endpoint: ${endpoint.name}")
            }
            
            saveAll(all)
            Log.d(TAG, "Endpoint saved successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save endpoint: ${endpoint.name}", e)
        }
    }
    
    /**
     * Save all endpoints (bulk update)
     */
    fun saveAll(endpoints: List<Endpoint>) {
        try {
            val json = gson.toJson(endpoints)
            storage.write(json)
            Log.d(TAG, "Saved ${endpoints.size} endpoints")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save endpoints", e)
        }
    }
    
    /**
     * Delete endpoint by ID
     */
    fun deleteById(endpointId: String) {
        try {
            val all = getAll().toMutableList()
            val removed = all.removeAll { it.id == endpointId }
            
            if (!removed) {
                Log.w(TAG, "Attempted to delete non-existent endpoint: $endpointId")
                return
            }
            
            saveAll(all)
            Log.d(TAG, "Endpoint deleted: $endpointId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete endpoint: $endpointId", e)
        }
    }
    
    /**
     * Get default endpoint (first enabled)
     */
    fun getDefault(): Endpoint? {
        return try {
            getEnabled().firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get default endpoint", e)
            null
        }
    }
    
    /**
     * Check if any endpoints are configured
     */
    fun hasEndpoints(): Boolean {
        return try {
            getAll().isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for endpoints", e)
            false
        }
    }
    
    /**
     * Check if any enabled endpoints exist
     */
    fun hasEnabledEndpoints(): Boolean {
        return try {
            getEnabled().isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for enabled endpoints", e)
            false
        }
    }
    
    /**
     * Update endpoint statistics
     */
    fun updateStats(endpointId: String, success: Boolean, responseTime: Long) {
        try {
            val endpoint = getById(endpointId) ?: return
            
            val newStats = endpoint.stats.copy(
                totalRequests = endpoint.stats.totalRequests + 1,
                totalSuccess = if (success) endpoint.stats.totalSuccess + 1 else endpoint.stats.totalSuccess,
                totalFailed = if (!success) endpoint.stats.totalFailed + 1 else endpoint.stats.totalFailed,
                avgResponseTime = ((endpoint.stats.avgResponseTime * endpoint.stats.totalRequests) + responseTime) / (endpoint.stats.totalRequests + 1),
                lastActivity = System.currentTimeMillis()
            )
            
            save(endpoint.copy(stats = newStats))
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update stats for endpoint: $endpointId", e)
        }
    }
    
    /**
     * Create default endpoint for first launch
     * ✅ Uses UUID for proper identity
     */
    private fun createDefaultEndpoint(): Endpoint {
        return Endpoint(
            id = java.util.UUID.randomUUID().toString(),  // ✅ ALWAYS use UUID
            name = "Google Apps Script",
            url = "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec",
            enabled = true,
            timeout = 30000,
            retryCount = 3,
            headers = emptyMap(),
            stats = EndpointStats(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
