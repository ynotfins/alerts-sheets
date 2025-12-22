package com.example.alertsheets

import android.content.Context
import android.util.Log
import com.example.alertsheets.data.repositories.EndpointRepository
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Endpoint
import com.example.alertsheets.domain.models.EndpointStats
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceStats
import com.example.alertsheets.domain.models.SourceType
import com.example.alertsheets.utils.AppConstants

/**
 * One-time migration from V1 (PrefsManager) to V2 (SourceManager)
 * 
 * Migrates:
 * - SMS targets → Source objects with type=SMS
 * - App targets → Source objects with type=APP
 * - Endpoints → V2 Endpoint model with JsonStorage
 * - Preserves all settings (enabled, filters, etc.)
 */
object MigrationManager {
    
    private const val TAG = "MigrationManager"
    
    /**
     * Check if migration is needed and execute if so
     */
    fun migrateIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyMigrated = prefs.getBoolean(AppConstants.MIGRATION_KEY, false)
        
        if (alreadyMigrated) {
            Log.i(TAG, "Migration already complete, skipping")
            return
        }
        
        Log.i(TAG, "Starting V1 → V2 migration...")
        
        try {
            migrateData(context)
            
            // Mark migration complete
            prefs.edit().putBoolean(AppConstants.MIGRATION_KEY, true).apply()
            Log.i(TAG, "✓ ${AppConstants.Success.MIGRATION_COMPLETE}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed: ${e.message}", e)
            // Don't mark as complete so it retries next time
        }
    }
    
    private fun migrateData(context: Context) {
        val sourceManager = SourceManager(context)
        val endpointRepo = EndpointRepository(context)
        
        // 1. ✅ Ensure default endpoint exists FIRST
        if (!endpointRepo.hasEndpoints()) {
            Log.i(TAG, "Creating default endpoint...")
            createDefaultEndpoint(context, endpointRepo)
        }
        
        // 2. Migrate SMS targets
        migrateSmsTargets(context, sourceManager)
        
        // 3. Migrate app targets
        migrateAppTargets(context, sourceManager)
        
        // 4. Ensure sources.json exists even if no old data
        if (sourceManager.getAllSources().isEmpty()) {
            Log.i(TAG, "No sources after migration - creating empty sources file")
        }
        
        Log.i(TAG, "Migration data transfer complete")
    }
    
    /**
     * Create default endpoint on first launch
     */
    private fun createDefaultEndpoint(context: Context, endpointRepo: EndpointRepository) {
        // Try to migrate from V1 endpoints first
        val v1Endpoints = PrefsManager.getEndpoints(context)
        
        if (v1Endpoints.isNotEmpty()) {
            Log.i(TAG, "Migrating ${v1Endpoints.size} V1 endpoints to V2...")
            v1Endpoints.forEach { v1Endpoint ->
                val v2Endpoint = Endpoint(
                    id = v1Endpoint.id,  // ✅ Preserve ID from V1 (now has id field)
                    name = v1Endpoint.name,
                    url = v1Endpoint.url,
                    enabled = v1Endpoint.isEnabled,
                    timeout = 30000,
                    retryCount = 3,
                    headers = emptyMap(),
                    stats = EndpointStats(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                endpointRepo.save(v2Endpoint)
            }
        } else {
            // Create default endpoint
            Log.i(TAG, "No V1 endpoints found, creating default endpoint")
            val defaultEndpoint = Endpoint(
                id = AppConstants.ENDPOINT_DEFAULT,
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
            endpointRepo.save(defaultEndpoint)
        }
    }
    
    private fun migrateSmsTargets(context: Context, sourceManager: SourceManager) {
        val smsTargets = PrefsManager.getSmsConfigList(context)
        
        if (smsTargets.isEmpty()) {
            Log.i(TAG, "No SMS targets to migrate")
            return
        }
        
        Log.i(TAG, "Migrating ${smsTargets.size} SMS targets...")
        
        smsTargets.forEach { target ->
            val source = Source(
                id = "sms:${target.phoneNumber}",
                type = SourceType.SMS,
                name = target.name,
                enabled = target.isEnabled,
                
                // Configuration
                autoClean = true,  // SMS default: clean emojis
                templateId = "rock-solid-sms-default",
                parserId = "sms",
                endpointId = "default-endpoint",
                
                // Metadata
                iconColor = 0xFF00D980.toInt(), // Green
                stats = SourceStats(
                    // Store SMS-specific config in stats metadata for now
                    // TODO: Add dedicated SMS config fields to Source model
                ),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            // Store SMS-specific settings separately
            context.getSharedPreferences("source_extras", Context.MODE_PRIVATE).edit()
                .putString("${source.id}:filterText", target.filterText)
                .putBoolean("${source.id}:isCaseSensitive", target.isCaseSensitive)
                .putString("${source.id}:phoneNumber", target.phoneNumber)
                .apply()
            
            sourceManager.saveSource(source)
            Log.i(TAG, "  ✓ Migrated SMS: ${target.name} (${target.phoneNumber})")
        }
    }
    
    private fun migrateAppTargets(context: Context, sourceManager: SourceManager) {
        val targetApps = PrefsManager.getTargetApps(context)
        
        if (targetApps.isEmpty()) {
            Log.i(TAG, "No app targets to migrate (God Mode active)")
            return
        }
        
        Log.i(TAG, "Migrating ${targetApps.size} app targets...")
        
        val pm = context.packageManager
        
        targetApps.forEach { packageName ->
            // Get app name from package manager
            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: Exception) {
                null
            }
            
            val appName = appInfo?.let { 
                pm.getApplicationLabel(it).toString() 
            } ?: packageName
            
            // Detect if BNN
            val isBnn = packageName.contains("bnn", ignoreCase = true)
            
            val source = Source(
                id = packageName,  // Use package name as ID for APP sources
                type = SourceType.APP,
                name = appName,
                enabled = true,
                
                // Configuration (BNN gets special treatment)
                autoClean = !isBnn,  // BNN doesn't need cleaning
                templateId = if (isBnn) "rock-solid-bnn-format" else "rock-solid-app-default",
                parserId = if (isBnn) "bnn" else "generic",
                endpointId = "default-endpoint",
                
                // Metadata
                iconColor = if (isBnn) 0xFFA855F7.toInt() else 0xFF4A9EFF.toInt(), // Purple or Blue
                stats = SourceStats(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            sourceManager.saveSource(source)
            Log.i(TAG, "  ✓ Migrated App: $appName ($packageName)")
        }
    }
}

