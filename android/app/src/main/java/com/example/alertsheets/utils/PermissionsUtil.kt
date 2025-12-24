package com.example.alertsheets.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Permissions Utility - Centralized permission checking
 * 
 * Used by MainActivity to update dashboard tile status dynamically
 * Uses the same checks as PermissionsActivity for consistency
 */
object PermissionsUtil {
    
    private const val TAG = "PermissionsUtil"
    
    data class PermissionStatus(
        val allGranted: Boolean,
        val missing: List<String>
    )
    
    /**
     * Check all required permissions and return status
     * Returns list of missing permission names for display
     */
    fun checkAllPermissions(context: Context): PermissionStatus {
        val missing = mutableListOf<String>()
        
        // 1. Notification Listener (CRITICAL)
        if (!checkNotificationListener(context)) {
            missing.add("Notification Listener")
        }
        
        // 2. SMS Permission (if app uses SMS sources)
        if (!checkSmsPermission(context)) {
            missing.add("SMS")
        }
        
        // 3. Battery Optimization (optional but recommended)
        if (!checkBatteryOptimization(context)) {
            missing.add("Battery")
        }
        
        return PermissionStatus(
            allGranted = missing.isEmpty(),
            missing = missing
        )
    }
    
    /**
     * Check notification listener access
     * Same logic as PermissionsActivity.checkNotifListener()
     */
    fun checkNotificationListener(context: Context): Boolean {
        return try {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val packageName = context.packageName
            val serviceName = "$packageName/com.example.alertsheets.services.AlertsNotificationListener"
            val isEnabledLegacy = flat != null && (flat.contains(packageName) || flat.contains(serviceName))
            val isEnabledCompat = NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)
            val result = isEnabledLegacy || isEnabledCompat
            Log.d(TAG, "checkNotificationListener() = $result (legacy=$isEnabledLegacy, compat=$isEnabledCompat)")
            result
        } catch (e: Exception) {
            Log.e(TAG, "checkNotificationListener() failed", e)
            false
        }
    }
    
    /**
     * Check SMS permission
     * Same logic as PermissionsActivity.checkSmsPermission()
     */
    fun checkSmsPermission(context: Context): Boolean {
        return try {
            // Check if we are the default SMS app
            val defaultSmsPackage = try {
                android.provider.Telephony.Sms.getDefaultSmsPackage(context)
            } catch (e: Exception) {
                null
            }
            val isDefaultSms = defaultSmsPackage == context.packageName
            
            // Also check the permission
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            
            val result = isDefaultSms || hasPermission
            Log.d(TAG, "checkSmsPermission() = $result (isDefaultSms=$isDefaultSms, hasPermission=$hasPermission)")
            result
        } catch (e: Exception) {
            Log.e(TAG, "checkSmsPermission() failed", e)
            false
        }
    }
    
    /**
     * Check battery optimization setting
     * Same logic as PermissionsActivity.checkBattery()
     */
    fun checkBatteryOptimization(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val result = pm.isIgnoringBatteryOptimizations(context.packageName)
            Log.d(TAG, "checkBatteryOptimization() = $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "checkBatteryOptimization() failed", e)
            false
        }
    }
    
    /**
     * Format missing permissions list for display
     * Example: "Missing: SMS, Battery"
     */
    fun formatMissingList(missing: List<String>): String {
        return if (missing.isEmpty()) {
            "All granted"
        } else {
            "Missing: ${missing.joinToString(", ")}"
        }
    }
}

