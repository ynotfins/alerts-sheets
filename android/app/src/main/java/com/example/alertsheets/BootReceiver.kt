package com.example.alertsheets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Boot Receiver - Ensures NotificationService starts automatically after device reboot
 * 
 * CRITICAL for 24/7 operation: Without this, the service won't restart after reboot
 * and notifications will be missed until the user manually opens the app.
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i("BootReceiver", "Device booted/app updated. Starting NotificationService...")
                
                try {
                    // Start the notification listener service
                    val serviceIntent = Intent(context, NotificationService::class.java)
                    
                    // Use startForegroundService for Android 8+
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    
                    Log.i("BootReceiver", "✓ NotificationService started successfully")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start NotificationService", e)
                }
            }
        }
    }
}

