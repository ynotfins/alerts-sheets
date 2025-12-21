package com.example.alertsheets.data.repositories

import android.content.Context
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceStats
import com.example.alertsheets.domain.models.SourceType
import com.example.alertsheets.data.storage.JsonStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing Sources
 * Handles CRUD operations and statistics updates
 */
class SourceRepository(private val context: Context) {
    
    private val storage = JsonStorage(context, "sources.json")
    private val gson = Gson()
    
    /**
     * Get all sources
     */
    fun getAll(): List<Source> {
        val json = storage.read() ?: return getDefaultSources()
        return try {
            gson.fromJson(json, object : TypeToken<List<Source>>() {}.type)
        } catch (e: Exception) {
            getDefaultSources()
        }
    }
    
    /**
     * Get source by ID
     */
    fun getById(id: String): Source? {
        return getAll().firstOrNull { it.id == id }
    }
    
    /**
     * Find source by package name (for app notifications)
     */
    fun findByPackage(packageName: String): Source? {
        return getAll().firstOrNull { it.matchesPackage(packageName) }
    }
    
    /**
     * Find source by SMS sender
     */
    fun findBySender(sender: String): Source? {
        return getAll().firstOrNull { it.matchesSender(sender) }
    }
    
    /**
     * Get enabled sources only
     */
    fun getEnabled(): List<Source> {
        return getAll().filter { it.enabled }
    }
    
    /**
     * Save source (create or update)
     */
    fun save(source: Source) {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == source.id }
        
        if (index >= 0) {
            all[index] = source.copy(updatedAt = System.currentTimeMillis())
        } else {
            all.add(source)
        }
        
        storage.write(gson.toJson(all))
    }
    
    /**
     * Delete source
     */
    fun delete(id: String) {
        val all = getAll().toMutableList()
        all.removeAll { it.id == id }
        storage.write(gson.toJson(all))
    }
    
    /**
     * Update source statistics
     */
    fun updateStats(
        id: String,
        processed: Int? = null,
        sent: Int? = null,
        failed: Int? = null
    ) {
        val source = getById(id) ?: return
        val stats = source.stats
        
        val newStats = SourceStats(
            totalProcessed = stats.totalProcessed + (processed ?: 0),
            totalSent = stats.totalSent + (sent ?: 0),
            totalFailed = stats.totalFailed + (failed ?: 0),
            lastActivity = System.currentTimeMillis()
        )
        
        save(source.copy(stats = newStats))
    }
    
    /**
     * Get default sources (BNN + Generic)
     */
    private fun getDefaultSources(): List<Source> {
        return listOf(
            Source(
                id = "com.example.bnn",
                type = SourceType.APP,
                name = "BNN Alerts",
                enabled = true,
                templateId = "rock-solid-bnn-format",
                autoClean = false,  // BNN doesn't need cleaning
                parserId = "bnn",
                endpointId = "default-endpoint",
                iconColor = 0xFFA855F7.toInt() // Purple
            ),
            Source(
                id = "generic-app",
                type = SourceType.APP,
                name = "All Other Apps",
                enabled = false,
                templateId = "rock-solid-app-default",
                autoClean = true,  // Generic apps might have emojis
                parserId = "generic",
                endpointId = "default-endpoint",
                iconColor = 0xFF4A9EFF.toInt() // Blue
            ),
            Source(
                id = "sms:dispatch",
                type = SourceType.SMS,
                name = "Dispatch SMS",
                enabled = true,
                templateId = "rock-solid-sms-default",
                autoClean = true,  // SMS often has emojis
                parserId = "sms",
                endpointId = "default-endpoint",
                iconColor = 0xFF00D980.toInt() // Green
            )
        )
    }
}

