package com.example.alertsheets

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            // 1. App Filter Check
            val targetApps = PrefsManager.getTargetApps(this)
            if (targetApps.isNotEmpty() && event.packageName != null && !targetApps.contains(event.packageName.toString())) {
                return
            }

            // 2. Dynamic Config Check
            val pkgName = event.packageName?.toString() ?: ""
            val config = PrefsManager.getAppConfig(this, pkgName)
            
            if (config.mappings.isNotEmpty() || config.staticFields.isNotEmpty()) {
                 val dynamicData = AccDataExtractor.extract(event, config)
                 if (DeDuplicator.shouldProcess(dynamicData.toString())) {
                     scope.launch {
                         NetworkClient.sendData(this@NotificationAccessibilityService, dynamicData)
                     }
                 }
                 return
            }

            val textList = event.text
            if (textList.isNotEmpty()) {
                val sb = StringBuilder()
                for (charSequence in textList) {
                    sb.append(charSequence)
                    sb.append("\n")
                }
                
                val content = sb.toString()
                Log.d("AccService", "Captured: $content")
                
                // Reuse existing parser logic
                if (content.contains("|")) {
                    if (DeDuplicator.shouldProcess(content)) {
                        val parsed = Parser.parse(content)
                        if (parsed != null) {
                            Log.d("AccService", "Parsed valid data: ${parsed.incidentId}")
                            scope.launch {
                                NetworkClient.sendData(this@NotificationAccessibilityService, parsed)
                            }
                        }
                    } else {
                        Log.d("AccService", "Duplicate ignored")
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.e("AccService", "Interrupted")
    }
}
