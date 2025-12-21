package com.example.alertsheets.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.alertsheets.R
import com.example.alertsheets.domain.DataPipeline
import com.example.alertsheets.domain.models.RawNotification
import com.example.alertsheets.ui.MainActivity

/**
 * AlertsToSheets Notification Listener - V2
 * 
 * GOD MODE FEATURES:
 * - Runs as FOREGROUND SERVICE (Android can't kill it)
 * - System app priority (sees notifications first)
 * - Maximum notification access
 * - Feeds DataPipeline for processing
 * 
 * This is the PRIMARY notification capture mechanism
 */
class AlertsNotificationListener : NotificationListenerService() {
    
    private val TAG = "NotificationListener"
    private lateinit var pipeline: DataPipeline
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "alerts_foreground"
        private const val CHANNEL_NAME = "AlertsToSheets Service"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 Service starting - GOD MODE")
        
        // Initialize pipeline
        pipeline = DataPipeline(applicationContext)
        
        // Create notification channel (Android 8+)
        createNotificationChannel()
        
        // Start as FOREGROUND SERVICE (can't be killed!)
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        
        Log.i(TAG, "✅ Service running as FOREGROUND - Android cannot kill us")
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "✅ Notification Listener CONNECTED - GOD MODE ACTIVE")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "⚠️ Notification Listener DISCONNECTED - Requesting rebind")
        
        // Request rebind (Android will reconnect us)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(android.content.ComponentName(this, AlertsNotificationListener::class.java))
        }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        try {
            val packageName = sbn.packageName
            
            // Skip our own notifications
            if (packageName == applicationContext.packageName) {
                return
            }
            
            // Extract notification data
            val notification = sbn.notification
            val extras = notification.extras
            
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text
            
            Log.d(TAG, "📥 Notification: $packageName - $title")
            
            // Create raw notification
            val raw = RawNotification.fromNotification(
                packageName = packageName,
                title = title,
                text = text,
                bigText = bigText,
                extras = mapOf(
                    "notificationId" to sbn.id.toString(),
                    "postTime" to sbn.postTime.toString()
                )
            )
            
            // Send to pipeline for processing
            pipeline.processAppNotification(packageName, raw)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // We don't care about removals, we already captured it
    }
    
    override fun onDestroy() {
        Log.w(TAG, "⚠️ Service destroyed - This should NEVER happen in GOD MODE!")
        pipeline.shutdown()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
    
    /**
     * Create notification channel (Android 8+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps AlertsToSheets running 24/7"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create persistent foreground notification
     * This keeps Android from killing us
     */
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AlertsToSheets Active")
            .setContentText("Monitoring 300+ alerts/day • GOD MODE")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }
}

