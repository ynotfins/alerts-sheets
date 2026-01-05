package com.example.alertsheets.data.repositories

import android.content.Context
import android.util.Log
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceStats
import com.example.alertsheets.domain.models.SourceType
import com.example.alertsheets.data.storage.JsonStorage
import com.example.alertsheets.utils.AppConstants
import com.example.alertsheets.utils.SmsSenderNormalizer
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
            
            val normalized = normalizeAndDedupeSources(sources)
            if (normalized.size != sources.size || normalized.map { it.id } != sources.map { it.id }) {
                // Best-effort persist so the app stops oscillating between duplicate IDs across screens.
                runCatching { storage.write(gson.toJson(normalized)) }
                Log.w(TAG, "Normalized/deduped sources: ${sources.size} -> ${normalized.size}")
            } else {
                Log.d(TAG, "Successfully loaded ${sources.size} sources")
            }
            normalized
            
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
     * Enforce invariants:
     * - SMS sources must be unique per canonical sender (sms:+1XXXXXXXXXX).
     * - If duplicates exist, merge them into one Source so UI edits don't "fight".
     */
    private fun normalizeAndDedupeSources(sources: List<Source>): List<Source> {
        if (sources.isEmpty()) return sources

        val result = mutableListOf<Source>()
        val smsByCanonical = linkedMapOf<String, Source>()

        sources.forEach { src ->
            if (src.type != SourceType.SMS) {
                // APP sources: drop invalid IDs created by old Lab bug (UUID / non-package IDs).
                if (src.type == SourceType.APP) {
                    val isUuid = Regex("^[0-9a-fA-F]{8}-").containsMatchIn(src.id)
                    val isLikelyPackage = src.id.contains(".") && !src.id.startsWith("sms:", ignoreCase = true)
                    if (!isLikelyPackage || isUuid) {
                        Log.w(TAG, "Dropping invalid APP source id='${src.id}' name='${src.name}' (won't ever match notifications)")
                        return@forEach
                    }

                    // Auto-heal parserId if template clearly expects BNN fields.
                    val t = src.templateJson.lowercase()
                    val looksLikeBnnTemplate = t.contains("{{incidentid}}") || t.contains("{{fdcodes}}") || t.contains("{{originalbody}}")
                    val looksLikeBnnPackage = src.id.contains("bnn", ignoreCase = true)
                    val healed = if (looksLikeBnnTemplate || looksLikeBnnPackage) {
                        if (src.parserId != "bnn") src.copy(parserId = "bnn") else src
                    } else src
                    result.add(healed)
                    return@forEach
                }

                result.add(src)
                return@forEach
            }

            val canonicalId = SmsSenderNormalizer.toCanonicalSourceId(src.id)
            val existing = smsByCanonical[canonicalId]
            if (existing == null) {
                // normalize ID to canonical
                smsByCanonical[canonicalId] = if (src.id == canonicalId) src else src.copy(id = canonicalId)
            } else {
                // merge duplicate SMS sources for same sender
                val mergedEndpoints = (existing.endpointIds + src.endpointIds).distinct()
                val merged = existing.copy(
                    enabled = existing.enabled || src.enabled,
                    name = if (existing.updatedAt >= src.updatedAt) existing.name else src.name,
                    templateJson = if (existing.updatedAt >= src.updatedAt) existing.templateJson else src.templateJson,
                    endpointIds = mergedEndpoints,
                    updatedAt = maxOf(existing.updatedAt, src.updatedAt),
                    createdAt = minOf(existing.createdAt, src.createdAt)
                )
                smsByCanonical[canonicalId] = merged
            }
        }

        result.addAll(smsByCanonical.values)
        return result
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
     * Uses normalized matching and falls back to last-10-digits if exact match fails
     */
    fun findBySender(sender: String): Source? {
        val smsSources = getAll().filter { it.type == SourceType.SMS }
        
        // Try exact normalized match first
        val exactMatch = smsSources.firstOrNull { it.matchesSender(sender) }
        if (exactMatch != null) return exactMatch
        
        // Fallback: Try last 10 digits (only if unambiguous)
        return com.example.alertsheets.utils.SmsSenderNormalizer.findBySuffix(
            candidates = smsSources,
            targetSender = sender,
            suffixLength = 10,
            getId = { it.id }
        )
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

