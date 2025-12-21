package com.example.alertsheets.data.repositories

import android.content.Context
import android.util.Log
import com.example.alertsheets.Endpoint
import com.example.alertsheets.PrefsManager
import com.example.alertsheets.utils.AppConstants

/**
 * Repository for managing Endpoints
 * 
 * PHASE 2: Facade pattern over PrefsManager
 * - Provides consistent API with other repositories
 * - Easy to migrate to JSON storage later
 * - Centralizes endpoint logic
 * 
 * FUTURE: Migrate to JsonStorage like SourceRepository
 */
class EndpointRepository(private val context: Context) {
    
    private val TAG = "EndpointRepository"
    
    /**
     * Get all endpoints
     */
    fun getAll(): List<Endpoint> {
        return try {
            PrefsManager.getEndpoints(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load endpoints", e)
            emptyList()
        }
    }
    
    /**
     * Get enabled endpoints only
     */
    fun getEnabled(): List<Endpoint> {
        return try {
            PrefsManager.getEndpoints(context).filter { it.isEnabled }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load enabled endpoints", e)
            emptyList()
        }
    }
    
    /**
     * Get endpoint by URL (closest match to ID)
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
     * 
     * Note: Current implementation doesn't support true update
     * (no ID field in Endpoint model). Just saves the entire list.
     */
    fun save(endpoint: Endpoint) {
        try {
            val all = getAll().toMutableList()
            
            // Check if endpoint with same URL exists
            val index = all.indexOfFirst { it.url == endpoint.url }
            
            if (index >= 0) {
                // Update existing
                all[index] = endpoint
                Log.d(TAG, "Updating endpoint: ${endpoint.name}")
            } else {
                // Add new
                all.add(endpoint)
                Log.d(TAG, "Creating new endpoint: ${endpoint.name}")
            }
            
            PrefsManager.saveEndpoints(context, all)
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
            PrefsManager.saveEndpoints(context, endpoints)
            Log.d(TAG, "Saved ${endpoints.size} endpoints")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save endpoints", e)
        }
    }
    
    /**
     * Delete endpoint by URL
     */
    fun deleteByUrl(url: String) {
        try {
            val all = getAll().toMutableList()
            val removed = all.removeAll { it.url == url }
            
            if (!removed) {
                Log.w(TAG, "Attempted to delete non-existent endpoint: $url")
                return
            }
            
            PrefsManager.saveEndpoints(context, all)
            Log.d(TAG, "Endpoint deleted: $url")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete endpoint: $url", e)
        }
    }
    
    /**
     * Get default endpoint
     * Returns the first enabled endpoint or null
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
}
