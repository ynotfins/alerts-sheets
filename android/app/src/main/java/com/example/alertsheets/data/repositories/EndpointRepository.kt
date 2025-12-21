package com.example.alertsheets.data.repositories

import android.content.Context
import com.example.alertsheets.domain.models.Endpoint
import com.example.alertsheets.domain.models.EndpointStats
import com.example.alertsheets.data.storage.JsonStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing Endpoints
 * Handles CRUD operations and statistics updates
 */
class EndpointRepository(private val context: Context) {
    
    private val storage = JsonStorage(context, "endpoints.json")
    private val gson = Gson()
    
    /**
     * Get all endpoints
     */
    fun getAll(): List<Endpoint> {
        val json = storage.read() ?: return getDefaultEndpoints()
        return try {
            gson.fromJson(json, object : TypeToken<List<Endpoint>>() {}.type)
        } catch (e: Exception) {
            getDefaultEndpoints()
        }
    }
    
    /**
     * Get endpoint by ID
     */
    fun getById(id: String): Endpoint? {
        return getAll().firstOrNull { it.id == id }
    }
    
    /**
     * Get enabled endpoints only
     */
    fun getEnabled(): List<Endpoint> {
        return getAll().filter { it.enabled }
    }
    
    /**
     * Save endpoint (create or update)
     */
    fun save(endpoint: Endpoint) {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == endpoint.id }
        
        if (index >= 0) {
            all[index] = endpoint.copy(updatedAt = System.currentTimeMillis())
        } else {
            all.add(endpoint)
        }
        
        storage.write(gson.toJson(all))
    }
    
    /**
     * Delete endpoint
     */
    fun delete(id: String) {
        val all = getAll().toMutableList()
        all.removeAll { it.id == id }
        storage.write(gson.toJson(all))
    }
    
    /**
     * Update endpoint statistics
     */
    fun updateStats(
        id: String,
        success: Boolean,
        responseTime: Long
    ) {
        val endpoint = getById(id) ?: return
        val stats = endpoint.stats
        
        val newAvgResponseTime = if (stats.totalRequests > 0) {
            ((stats.avgResponseTime * stats.totalRequests) + responseTime) / (stats.totalRequests + 1)
        } else {
            responseTime
        }
        
        val newStats = EndpointStats(
            totalRequests = stats.totalRequests + 1,
            totalSuccess = if (success) stats.totalSuccess + 1 else stats.totalSuccess,
            totalFailed = if (!success) stats.totalFailed + 1 else stats.totalFailed,
            avgResponseTime = newAvgResponseTime,
            lastActivity = System.currentTimeMillis()
        )
        
        save(endpoint.copy(stats = newStats))
    }
    
    /**
     * Get default endpoint (Google Apps Script)
     */
    private fun getDefaultEndpoints(): List<Endpoint> {
        // Read URL from existing PrefsManager
        val sharedPrefs = context.getSharedPreferences("alerts_to_sheets", Context.MODE_PRIVATE)
        val savedUrl = sharedPrefs.getString("endpoint_url", "")
        
        return listOf(
            Endpoint(
                id = "default-endpoint",
                name = "Google Sheets - Main",
                url = savedUrl ?: "",
                enabled = true,
                timeout = 30000,
                retryCount = 3,
                headers = emptyMap()
            )
        )
    }
}

