package com.example.alertsheets

import android.app.Application
import android.util.Log
import com.example.alertsheets.domain.parsers.ParserRegistry
import com.example.alertsheets.utils.Logger

/**
 * AlertsToSheets Application - V2
 * 
 * Initializes global components on app start
 */
class AlertsApplication : Application() {
    
    private val TAG = "AlertsApp"
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 AlertsToSheets V2 starting - GOD MODE")
        
        // Initialize parser registry
        ParserRegistry.init()
        Log.i(TAG, "✅ Parser registry initialized")
        
        // Load logs
        val logger = Logger(applicationContext)
        logger.load()
        logger.log("📱 App started")
        
        Log.i(TAG, "✅ Application ready")
    }
}

