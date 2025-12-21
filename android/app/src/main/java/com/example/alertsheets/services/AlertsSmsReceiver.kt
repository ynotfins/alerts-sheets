package com.example.alertsheets.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.example.alertsheets.domain.DataPipeline
import com.example.alertsheets.domain.models.RawNotification

/**
 * AlertsToSheets SMS Receiver - V2
 * 
 * GOD MODE FEATURES:
 * - Priority MAX (2147483647) - highest possible
 * - ROLE_SMS compatible (receives SMS first if default app)
 * - Full SMS/MMS/WAP_PUSH support
 * - Feeds DataPipeline for processing
 * 
 * This is the PRIMARY SMS capture mechanism
 */
class AlertsSmsReceiver : BroadcastReceiver() {
    
    private val TAG = "SmsReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
                Telephony.Sms.Intents.SMS_DELIVER_ACTION -> {
                    handleSms(context, intent)
                }
                Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION -> {
                    handleWapPush(context, intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }
    
    /**
     * Handle incoming SMS/MMS
     */
    private fun handleSms(context: Context, intent: Intent) {
        // Extract SMS messages
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } else {
            // Fallback for older Android
            extractMessagesLegacy(intent)
        }
        
        if (messages == null || messages.isEmpty()) {
            Log.w(TAG, "No SMS messages found in intent")
            return
        }
        
        // Combine message parts (SMS can be split into multiple parts)
        val sender = messages[0].originatingAddress ?: "Unknown"
        val fullMessage = messages.joinToString("") { it.messageBody ?: "" }
        
        Log.i(TAG, "📩 SMS from: $sender")
        
        // Create raw notification
        val raw = RawNotification.fromSms(
            sender = sender,
            message = fullMessage
        )
        
        // Send to pipeline
        val pipeline = DataPipeline(context.applicationContext)
        pipeline.processSms(sender, raw)
    }
    
    /**
     * Handle WAP push messages (MMS notification)
     */
    private fun handleWapPush(context: Context, intent: Intent) {
        Log.i(TAG, "📱 WAP Push received (MMS notification)")
        
        // WAP push typically indicates incoming MMS
        // We'll capture the notification but not the MMS content
        // (MMS content requires additional processing)
        
        val mimeType = intent.type
        Log.d(TAG, "WAP Push MIME type: $mimeType")
        
        // Create generic MMS notification
        val raw = RawNotification.fromSms(
            sender = "MMS",
            message = "MMS received (type: $mimeType)"
        )
        
        val pipeline = DataPipeline(context.applicationContext)
        pipeline.processSms("MMS", raw)
    }
    
    /**
     * Extract SMS messages (legacy method for pre-KitKat)
     */
    @Suppress("DEPRECATION")
    private fun extractMessagesLegacy(intent: Intent): Array<SmsMessage>? {
        val bundle = intent.extras ?: return null
        val pdus = bundle.get("pdus") as? Array<*> ?: return null
        val format = bundle.getString("format")
        
        return pdus.mapNotNull { pdu ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating SMS message", e)
                null
            }
        }.toTypedArray()
    }
}

