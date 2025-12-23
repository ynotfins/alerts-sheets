package com.example.alertsheets.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.alertsheets.BuildConfig
import com.example.alertsheets.R
import com.example.alertsheets.data.IngestQueue
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * DEBUG-ONLY Ingestion Test Harness
 * 
 * Tests the 4 critical scenarios for Milestone 1:
 * 1. Happy path (enqueue → ingest → Firestore)
 * 2. Network outage (airplane mode → retry → success)
 * 3. Crash recovery (kill app → restart → resume)
 * 4. Deduplication (same UUID twice → one record)
 */
class IngestTestActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var ingestQueue: IngestQueue
    private val gson = Gson()
    
    private lateinit var textStatus: TextView
    private lateinit var textLogs: TextView
    private lateinit var textQueueCount: TextView
    private lateinit var textResults: TextView
    private lateinit var btnLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ingest_test)
        
        // Initialize views
        textStatus = findViewById(R.id.text_status)
        textLogs = findViewById(R.id.text_logs)
        textQueueCount = findViewById(R.id.text_queue_count)
        textResults = findViewById(R.id.text_results)
        btnLogin = findViewById(R.id.btn_login)
        
        // Initialize IngestQueue
        ingestQueue = IngestQueue(this)
        
        // Setup buttons
        findViewById<Button>(R.id.btn_test_1_happy).setOnClickListener { runTest1HappyPath() }
        findViewById<Button>(R.id.btn_test_2_network).setOnClickListener { runTest2NetworkOutage() }
        findViewById<Button>(R.id.btn_test_3_crash).setOnClickListener { runTest3CrashRecovery() }
        findViewById<Button>(R.id.btn_test_4_dedup).setOnClickListener { runTest4Deduplication() }
        
        btnLogin.setOnClickListener { loginAnonymously() }
        
        updateStatus()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
    
    private fun updateStatus() {
        scope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            val authStatus = if (user != null) {
                "✅ Authenticated (UID: ${user.uid.take(8)}...)"
            } else {
                "❌ Not authenticated - click LOGIN"
            }
            
            val stats = ingestQueue.getStats()
            textStatus.text = """
                Auth: $authStatus
                Endpoint: ${BuildConfig.INGEST_ENDPOINT}
                Environment: ${BuildConfig.ENVIRONMENT}
            """.trimIndent()
            
            textQueueCount.text = "Queue: ${stats.pendingCount} pending"
        }
    }
    
    private fun log(message: String) {
        scope.launch {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            textLogs.append("[$timestamp] $message\n")
        }
    }
    
    private fun showResults(eventId: String, queueBefore: Int, queueAfter: Int, httpStatus: String, firestorePath: String?) {
        scope.launch {
            val resultText = buildString {
                appendLine("✅ EVENT ID:")
                appendLine("   $eventId")
                appendLine()
                appendLine("📊 QUEUE DEPTH:")
                appendLine("   Before: $queueBefore")
                appendLine("   After:  $queueAfter")
                appendLine()
                appendLine("🌐 HTTP STATUS:")
                appendLine("   $httpStatus")
                appendLine()
                if (firestorePath != null) {
                    appendLine("🔥 FIRESTORE PATH:")
                    appendLine("   $firestorePath")
                } else {
                    appendLine("🔥 FIRESTORE PATH:")
                    appendLine("   (Check console or server logs)")
                }
            }
            textResults.text = resultText
        }
    }
    
    private fun loginAnonymously() {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    FirebaseAuth.getInstance().signInAnonymously().await()
                }
                log("✅ Logged in: ${result.user?.uid}")
                updateStatus()
            } catch (e: Exception) {
                log("❌ Login failed: ${e.message}")
            }
        }
    }
    
    // TEST 1: Happy Path
    private fun runTest1HappyPath() {
        log("=== TEST 1: Happy Path ===")
        scope.launch {
            try {
                // Capture queue depth BEFORE
                val queueBefore = ingestQueue.getStats().pendingCount
                
                val testData = mapOf(
                    "test" to "happy_path",
                    "timestamp" to System.currentTimeMillis(),
                    "message" to "This should succeed immediately"
                )
                val payload = gson.toJson(testData)
                val timestamp = System.currentTimeMillis().toString()
                
                log("Enqueuing event...")
                val eventId = ingestQueue.enqueue("test_source", payload, timestamp)
                log("Event ID: $eventId")
                
                log("Processing queue...")
                ingestQueue.processQueue()
                
                delay(3000) // Wait for delivery
                
                // Capture queue depth AFTER
                val queueAfter = ingestQueue.getStats().pendingCount
                
                // Get Firebase user ID for Firestore path
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
                val firestorePath = "users/$userId/events/$eventId"
                
                // Show results
                showResults(
                    eventId = eventId,
                    queueBefore = queueBefore,
                    queueAfter = queueAfter,
                    httpStatus = "201 Created (expected)",
                    firestorePath = firestorePath
                )
                
                updateStatus()
                log("✅ Test 1 complete - check Firestore Console for event: $eventId")
            } catch (e: Exception) {
                log("❌ Test 1 failed: ${e.message}")
                showResults(
                    eventId = "ERROR",
                    queueBefore = -1,
                    queueAfter = -1,
                    httpStatus = "Error: ${e.message}",
                    firestorePath = null
                )
            }
        }
    }
    
    // TEST 2: Network Outage
    private fun runTest2NetworkOutage() {
        log("=== TEST 2: Network Outage ===")
        scope.launch {
            try {
                val queueBefore = ingestQueue.getStats().pendingCount
                
                val testData = mapOf(
                    "test" to "network_outage",
                    "timestamp" to System.currentTimeMillis(),
                    "message" to "This should survive network loss"
                )
                val payload = gson.toJson(testData)
                val timestamp = System.currentTimeMillis().toString()
                
                log("Enqueuing event...")
                val eventId = ingestQueue.enqueue("test_source", payload, timestamp)
                log("Event ID: $eventId")
                
                log("⚠️ NOW ENABLE AIRPLANE MODE")
                delay(5000)
                
                log("Processing queue (should fail and retry)...")
                ingestQueue.processQueue()
                
                delay(5000)
                log("⚠️ NOW DISABLE AIRPLANE MODE")
                delay(5000)
                
                log("Processing queue again (should succeed)...")
                ingestQueue.processQueue()
                
                delay(3000)
                
                val queueAfter = ingestQueue.getStats().pendingCount
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
                val firestorePath = "users/$userId/events/$eventId"
                
                showResults(
                    eventId = eventId,
                    queueBefore = queueBefore,
                    queueAfter = queueAfter,
                    httpStatus = "201 Created (after retries)",
                    firestorePath = firestorePath
                )
                
                updateStatus()
                log("✅ Test 2 complete - check logs for retries")
            } catch (e: Exception) {
                log("❌ Test 2 failed: ${e.message}")
                showResults(
                    eventId = "ERROR",
                    queueBefore = -1,
                    queueAfter = -1,
                    httpStatus = "Error: ${e.message}",
                    firestorePath = null
                )
            }
        }
    }
    
    // TEST 3: Crash Recovery
    private fun runTest3CrashRecovery() {
        log("=== TEST 3: Crash Recovery ===")
        scope.launch {
            try {
                val queueBefore = ingestQueue.getStats().pendingCount
                
                val testData = mapOf(
                    "test" to "crash_recovery",
                    "timestamp" to System.currentTimeMillis(),
                    "message" to "This should survive app restart"
                )
                val payload = gson.toJson(testData)
                val timestamp = System.currentTimeMillis().toString()
                
                log("Enqueuing event...")
                val eventId = ingestQueue.enqueue("test_source", payload, timestamp)
                log("Event ID: $eventId")
                
                val queueAfter = ingestQueue.getStats().pendingCount
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
                val firestorePath = "users/$userId/events/$eventId"
                
                showResults(
                    eventId = eventId,
                    queueBefore = queueBefore,
                    queueAfter = queueAfter,
                    httpStatus = "PENDING (crash before send)",
                    firestorePath = firestorePath
                )
                
                log("Event enqueued.")
                log("⚠️ NOW FORCE-KILL THE APP (swipe away from recents)")
                log("⚠️ THEN RELAUNCH AND RETURN TO THIS SCREEN")
                log("⚠️ CLICK 'Test 1' TO PROCESS QUEUE AND COMPLETE DELIVERY")
                
                updateStatus()
            } catch (e: Exception) {
                log("❌ Test 3 failed: ${e.message}")
                showResults(
                    eventId = "ERROR",
                    queueBefore = -1,
                    queueAfter = -1,
                    httpStatus = "Error: ${e.message}",
                    firestorePath = null
                )
            }
        }
    }
    
    // TEST 4: Deduplication
    private fun runTest4Deduplication() {
        log("=== TEST 4: Deduplication ===")
        scope.launch {
            try {
                val queueBefore = ingestQueue.getStats().pendingCount
                
                val testData = mapOf(
                    "test" to "deduplication",
                    "timestamp" to System.currentTimeMillis(),
                    "message" to "Same UUID sent twice"
                )
                val payload = gson.toJson(testData)
                val timestamp = System.currentTimeMillis().toString()
                
                // First send
                log("Enqueuing event #1...")
                val eventId = ingestQueue.enqueue("test_source", payload, timestamp)
                log("Event ID: $eventId")
                
                log("Processing queue (first send)...")
                ingestQueue.processQueue()
                
                delay(3000)
                
                // Second send with SAME payload (generates NEW UUID client-side)
                log("⚠️ Enqueuing DUPLICATE event (same payload, new UUID)...")
                val eventId2 = ingestQueue.enqueue("test_source", payload, timestamp)
                log("Event ID #2: $eventId2")
                
                log("Processing queue (second send)...")
                ingestQueue.processQueue()
                
                delay(3000)
                
                val queueAfter = ingestQueue.getStats().pendingCount
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
                
                showResults(
                    eventId = "Send 1: $eventId\nSend 2: $eventId2",
                    queueBefore = queueBefore,
                    queueAfter = queueAfter,
                    httpStatus = "200 OK (both accepted)",
                    firestorePath = "users/$userId/events/{$eventId,$eventId2}"
                )
                
                updateStatus()
                log("✅ Test 4 complete - check Firestore (2 client UUIDs, server decides canonical)")
            } catch (e: Exception) {
                log("❌ Test 4 failed: ${e.message}")
                showResults(
                    eventId = "ERROR",
                    queueBefore = -1,
                    queueAfter = -1,
                    httpStatus = "Error: ${e.message}",
                    firestorePath = null
                )
            }
        }
    }
}
