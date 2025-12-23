package com.example.alertsheets

import android.app.Application
import android.util.Log
import com.example.alertsheets.domain.parsers.ParserRegistry
import com.example.alertsheets.utils.Logger
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

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
        
        // ✅ CRITICAL: Initialize Firebase FIRST
        FirebaseApp.initializeApp(this)
        Log.i(TAG, "✅ Firebase initialized (Project: ${FirebaseApp.getInstance().options.projectId})")
        
        // ✅ CRITICAL: Initialize Firebase Auth (anonymous sign-in for testing)
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i(TAG, "✅ Firebase Auth: Anonymous sign-in successful (UID: ${auth.currentUser?.uid})")
                } else {
                    Log.e(TAG, "❌ Firebase Auth: Anonymous sign-in FAILED", task.exception)
                }
            }
        } else {
            Log.i(TAG, "✅ Firebase Auth: Already signed in (UID: ${auth.currentUser?.uid})")
        }
        
        // ✅ CRITICAL: Initialize LogRepository SECOND (after Firebase, before anything logs)
        LogRepository.initialize(this)
        Log.i(TAG, "✅ LogRepository initialized")
        
        // ✅ SMOKE TEST: Add a BOOT entry to verify Activity Logs UI sees entries
        LogRepository.addLog(LogEntry(
            packageName = "com.example.alertsheets",
            title = "App Started",
            content = "AlertsToSheets V2 initialized successfully",
            status = LogStatus.SENT,
            rawJson = "{\"event\":\"boot\",\"timestamp\":\"${System.currentTimeMillis()}\"}"
        ))
        Log.i(TAG, "✅ BOOT entry added to LogRepository")
        
        // Initialize parser registry
        ParserRegistry.init()
        Log.i(TAG, "✅ Parser registry initialized")
        
        // Load logs
        val logger = Logger(applicationContext)
        logger.load()
        logger.log("📱 App started")
        
        Log.i(TAG, "✅ Application ready")
    }
    
    override fun onTerminate() {
        Log.i(TAG, "🛑 Application terminating")
        LogRepository.shutdown()
        super.onTerminate()
    }
}

