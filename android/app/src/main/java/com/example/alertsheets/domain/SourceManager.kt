package com.example.alertsheets.domain

import android.content.Context
import com.example.alertsheets.data.repositories.SourceRepository
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceType

/**
 * Central manager for all notification/SMS sources
 * 
 * Responsibilities:
 * - Find appropriate source for incoming notifications
 * - Manage source lifecycle (enable/disable)
 * - Track source statistics
 * - Provide UI-friendly source lists
 * 
 * This is a singleton accessed throughout the app
 */
class SourceManager(context: Context) {
    
    private val repository = SourceRepository(context.applicationContext)
    
    /**
     * Find source for an app notification
     */
    fun findSourceForNotification(packageName: String): Source? {
        // Try exact match first
        var source = repository.findByPackage(packageName)
        
        // If no match and enabled, try "generic-app" fallback
        if (source == null) {
            source = repository.getById("generic-app")
            if (source?.enabled != true) {
                source = null
            }
        }
        
        // Only return if enabled
        return if (source?.enabled == true) source else null
    }
    
    /**
     * Find source for an SMS message
     */
    fun findSourceForSms(sender: String): Source? {
        // Try exact sender match first
        var source = repository.findBySender(sender)
        
        // If no match, try generic SMS source
        if (source == null) {
            source = repository.getById("sms:dispatch")
            if (source?.enabled != true) {
                source = null
            }
        }
        
        // Only return if enabled
        return if (source?.enabled == true) source else null
    }
    
    /**
     * Get all sources (for UI)
     */
    fun getAllSources(): List<Source> {
        return repository.getAll()
    }
    
    /**
     * Get enabled sources only
     */
    fun getEnabledSources(): List<Source> {
        return repository.getEnabled()
    }
    
    /**
     * Get sources by type
     */
    fun getSourcesByType(type: SourceType): List<Source> {
        return repository.getAll().filter { it.type == type }
    }
    
    /**
     * Get source by ID
     */
    fun getSource(id: String): Source? {
        return repository.getById(id)
    }
    
    /**
     * Create or update source
     */
    fun saveSource(source: Source) {
        repository.save(source)
    }
    
    /**
     * Delete source
     */
    fun deleteSource(id: String) {
        repository.delete(id)
    }
    
    /**
     * Enable/disable source
     */
    fun setSourceEnabled(id: String, enabled: Boolean) {
        val source = repository.getById(id) ?: return
        repository.save(source.copy(enabled = enabled))
    }
    
    /**
     * Update source statistics after processing a notification
     */
    fun recordNotificationProcessed(sourceId: String, success: Boolean) {
        if (success) {
            repository.updateStats(
                id = sourceId,
                processed = 1,
                sent = 1
            )
        } else {
            repository.updateStats(
                id = sourceId,
                processed = 1,
                failed = 1
            )
        }
    }
    
    /**
     * Get total notifications processed today
     */
    fun getTodayStats(): Map<String, Int> {
        val sources = repository.getAll()
        return mapOf(
            "total" to sources.sumOf { it.stats.totalProcessed },
            "sent" to sources.sumOf { it.stats.totalSent },
            "failed" to sources.sumOf { it.stats.totalFailed }
        )
    }
}

