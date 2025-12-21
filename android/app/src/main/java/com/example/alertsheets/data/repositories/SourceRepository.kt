package com.example.alertsheets.data.repositories

import android.content.Context
import android.util.Log
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceStats
import com.example.alertsheets.domain.models.SourceType
import com.example.alertsheets.data.storage.JsonStorage
import com.example.alertsheets.utils.AppConstants
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing Sources
 * Handles CRUD operations and statistics updates with robust error handling
 */
class SourceRepository(private val context: Context) {
    
    private val TAG = "SourceRepository"
    private val storage = JsonStorage(context, AppConstants.FILE_SOURCES)
    private val gson = Gson()
    
    /**
     * Get all sources with robust error handling
     * 
     * ERROR RECOVERY:
     * - Missing file → return empty list
     * - Corrupt JSON → log error, return empty list
     * - Parse error → log error, return empty list
     * 
     * NEVER crashes, always returns valid list
     */
    fun getAll(): List<Source> {
        val json = storage.read() ?: run {
            Log.d(TAG, "No sources.json found, returning empty list")
            return emptyList()  // ✅ NO HARDCODED DEFAULTS
        }
        
        return try {
            val sources: List<Source> = gson.fromJson(json, object : TypeToken<List<Source>>() {}.type)
            
            if (sources == null) {
                Log.w(TAG, "Parsed sources.json as null, returning empty list")
                return emptyList()
            }
            
            Log.d(TAG, "Successfully loaded ${sources.size} sources")
            sources
            
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "${AppConstants.Errors.CORRUPT_SOURCES_JSON}: ${e.message}", e)
            // TODO: Could backup corrupt file for debugging
            emptyList()
            
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Invalid JSON structure in sources.json", e)
            emptyList()
            
        } catch (e: Exception) {
            Log.e(TAG, "${AppConstants.Errors.JSON_PARSE_FAILED}: sources.json", e)
            emptyList()
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
     * Save source (create or update) with error handling
     * 
     * ERROR RECOVERY:
     * - If write fails, logs error but doesn't crash
     * - Previous data remains intact if write fails (atomic writes in JsonStorage)
     */
    fun save(source: Source) {
        try {
            val all = getAll().toMutableList()
            val index = all.indexOfFirst { it.id == source.id }
            
            if (index >= 0) {
                all[index] = source.copy(updatedAt = System.currentTimeMillis())
                Log.d(TAG, "Updating existing source: ${source.id}")
            } else {
                all.add(source)
                Log.d(TAG, "Creating new source: ${source.id}")
            }
            
            storage.write(gson.toJson(all))
            Log.d(TAG, AppConstants.Success.SOURCE_SAVED)
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory saving source: ${source.id}", e)
            // Cannot proceed, but at least we logged it
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save source: ${source.id}", e)
            // Write failed, but old data is intact (atomic writes)
        }
    }
    
    /**
     * Delete source with error handling
     */
    fun delete(id: String) {
        try {
            val all = getAll().toMutableList()
            val removed = all.removeAll { it.id == id }
            
            if (!removed) {
                Log.w(TAG, "Attempted to delete non-existent source: $id")
                return
            }
            
            storage.write(gson.toJson(all))
            Log.d(TAG, "${AppConstants.Success.SOURCE_DELETED}: $id")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete source: $id", e)
        }
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
        try {
            val source = getById(id)
            if (source == null) {
                Log.w(TAG, "Cannot update stats for non-existent source: $id")
                return
            }
            
            val stats = source.stats
            
            val newStats = SourceStats(
                totalProcessed = stats.totalProcessed + (processed ?: 0),
                totalSent = stats.totalSent + (sent ?: 0),
                totalFailed = stats.totalFailed + (failed ?: 0),
                lastActivity = System.currentTimeMillis()
            )
            
            save(source.copy(stats = newStats))
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update stats for source: $id", e)
        }
    }
    
    // ✅ REMOVED: getDefaultSources()
    // No more hardcoded defaults. Sources are created ONLY by:
    // 1. Migration (from V1 PrefsManager data)
    // 2. User adding apps/SMS through UI
    // 3. Manual Source creation
    //
    // This ensures:
    // - No phantom sources that can't be deleted
    // - Dashboard shows accurate counts
    // - User has full control over what's monitored
}

