package com.example.alertsheets.utils

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * SMS Role Manager for V2
 * 
 * Handles requesting ROLE_SMS (default SMS app)
 * This grants MAXIMUM SMS privileges:
 * - First to receive ALL SMS
 * - Full read/write access to SMS database
 * - Can intercept, modify, or block SMS
 */
object SmsRoleManager {
    
    /**
     * Check if app is the default SMS app (has ROLE_SMS)
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses RoleManager
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            // Pre-Android 10
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }
    
    /**
     * Request to become default SMS app
     * Returns intent to show system dialog
     */
    fun requestSmsRole(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses RoleManager
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                null
            }
        } else {
            // Pre-Android 10 uses different method
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }
    
    /**
     * Get status text for UI
     */
    fun getStatusText(context: Context): String {
        return if (isDefaultSmsApp(context)) {
            "✅ ROLE_SMS: Active (Maximum Privilege)"
        } else {
            "⚠️ ROLE_SMS: Not Set (Reduced SMS Access)"
        }
    }
    
    /**
     * Get description for UI
     */
    fun getDescription(): String {
        return """
            ROLE_SMS grants maximum SMS privileges:
            • First to receive ALL SMS
            • Full read/write SMS database access
            • Can intercept or block SMS
            • Highest priority over all apps
            
            CRITICAL for dispatch SMS monitoring!
        """.trimIndent()
    }
}

