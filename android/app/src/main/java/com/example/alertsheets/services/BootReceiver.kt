package com.example.alertsheets.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Boot Receiver - V2
 * 
 * Auto-starts AlertsToSheets after device boot/reboot
 * Critical for 24/7 monitoring
 */
class BootReceiver : BroadcastReceiver() {
    
    private val TAG = "BootReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "🔄 Boot/Update detected - Starting service")
                startNotificationListener(context)
            }
        }
    }
    
    /**
     * Start the notification listener service
     */
    private fun startNotificationListener(context: Context) {
        try {
            val serviceIntent = Intent(context, AlertsNotificationListener::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8+ requires startForegroundService
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.i(TAG, "✅ Notification listener started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
        }
    }
}

