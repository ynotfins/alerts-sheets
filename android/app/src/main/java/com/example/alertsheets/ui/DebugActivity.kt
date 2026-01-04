package com.example.alertsheets.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alertsheets.BuildConfig
import com.example.alertsheets.R
import com.example.alertsheets.data.repositories.DeliveryLogRepository
import com.example.alertsheets.utils.StructuredLogger
import java.io.File

/**
 * Debug screen showing last 20 deliveries
 * Only accessible in debug builds
 * 
 * Features:
 * - View recent structured logs
 * - Share logs as text file
 * - Copy NDJSON to clipboard
 */
class DebugActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DebugLogsAdapter
    private lateinit var btnShare: Button
    private lateinit var btnCopy: Button
    private lateinit var btnExportLast10: Button
    private lateinit var btnCopyLast10: Button
    private lateinit var tvStatus: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Security: Only allow in debug builds
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }
        
        setContentView(R.layout.activity_debug)
        
        title = "Debug Logs"
        
        // ✅ Add Build ID to action bar subtitle
        supportActionBar?.subtitle = "Build: v${BuildConfig.VERSION_NAME} ${BuildConfig.GIT_SHA}"
        
        recyclerView = findViewById(R.id.recyclerViewLogs)
        btnShare = findViewById(R.id.btnShareLogs)
        btnCopy = findViewById(R.id.btnCopyLogs)
        btnExportLast10 = findViewById(R.id.btnExportLast10)
        btnCopyLast10 = findViewById(R.id.btnCopyLast10)
        tvStatus = findViewById(R.id.tvDebugStatus)
        
        // Setup RecyclerView (newest-first)
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true
            stackFromEnd = true
        }
        adapter = DebugLogsAdapter()
        recyclerView.adapter = adapter
        
        // Load logs
        loadLogs()
        
        // Share all logs button
        btnShare.setOnClickListener {
            shareAllLogs()
        }
        
        // Copy all logs button
        btnCopy.setOnClickListener {
            copyAllLogsToClipboard()
        }
        
        // ✅ Export last 10 logs button
        btnExportLast10.setOnClickListener {
            exportLast10Logs()
        }
        
        // ✅ Copy last 10 logs button
        btnCopyLast10.setOnClickListener {
            copyLast10ToClipboard()
        }
    }
    
    private fun loadLogs() {
        // Ensure persistence is initialized
        com.example.alertsheets.utils.DeliveryLogBuffer.init(applicationContext)

        // Read from persistent storage first (survives restarts)
        val repo = DeliveryLogRepository(applicationContext)
        val logs = repo.getRecent(limit = 50)
        
        // Convert DeliveryLogBuffer entries to StructuredLogger.LogEntry format for adapter
        val structuredLogs = logs.map { entry ->
            StructuredLogger.LogEntry(
                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    .format(java.util.Date(entry.timestamp)),
                level = when (entry.event) {
                    "http_fail", "test_http_fail" -> "ERROR"
                    "source_ignored" -> "WARN"
                    else -> "INFO"
                },
                source_id = entry.sourceId,
                endpoint_id = entry.endpointId,
                alert_id = entry.alertId,
                event = entry.event,
                details = buildString {
                    append(entry.details ?: "httpCode=${entry.httpCode} latencyMs=${entry.latencyMs}")
                    entry.url?.let { append("\nurl=").append(it) }
                    entry.payloadPreview?.let { append("\npayload=").append(it) }
                    entry.responsePreview?.let { append("\nresponse=").append(it) }
                }
            )
        }
        
        adapter.setLogs(structuredLogs.reversed()) // reverseLayout expects newest at top
        
        tvStatus.text = "Showing ${logs.size} real deliveries from pipeline"
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }
    
    private fun copyAllLogsToClipboard() {
        val ndjson = StructuredLogger.exportNDJSON()
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Debug Logs (NDJSON)", ndjson)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "All logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    // ✅ Export last 10 logs to file
    private fun exportLast10Logs() {
        try {
            val logs = com.example.alertsheets.utils.DeliveryLogBuffer.getRecent(10)
            
            if (logs.isEmpty()) {
                Toast.makeText(this, "No logs to export", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Convert to NDJSON
            val gson = com.google.gson.Gson()
            val ndjson = logs.joinToString("\n") { gson.toJson(it) }
            
            // Write to file
            val file = File(cacheDir, "delivery_logs_last10.ndjson")
            file.writeText(ndjson)
            
            // Share via FileProvider
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Delivery Logs (Last 10)")
                putExtra(Intent.EXTRA_TEXT, "Last 10 delivery logs from AlertsToSheets")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share Last 10 Logs"))
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // ✅ Copy last 10 logs to clipboard
    private fun copyLast10ToClipboard() {
        val logs = com.example.alertsheets.utils.DeliveryLogBuffer.getRecent(10)
        
        if (logs.isEmpty()) {
            Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show()
            return
        }
        
        val gson = com.google.gson.Gson()
        val ndjson = logs.joinToString("\n") { gson.toJson(it) }
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Last 10 Delivery Logs", ndjson)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "Last 10 logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun shareAllLogs() {
        try {
            val ndjson = StructuredLogger.exportNDJSON()
            
            // Write to file
            val file = File(cacheDir, "alerts_debug_logs.ndjson")
            file.writeText(ndjson)
            
            // Share via FileProvider
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "AlertsToSheets Debug Logs")
                putExtra(Intent.EXTRA_TEXT, "NDJSON structured logs from AlertsToSheets")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share Debug Logs"))
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share logs: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

