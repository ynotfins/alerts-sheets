package com.example.alertsheets

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import java.io.File

class LogActivity : AppCompatActivity() {

    private lateinit var adapter: LogAdapter
    private lateinit var searchBox: EditText
    private lateinit var btnExportLast10: Button
    private lateinit var btnCopyLast10: Button
    private var allLogs: List<LogEntry> = emptyList()
    
    private val repoListener = {
        runOnUiThread {
            allLogs = LogRepository.getLogs()
            Log.v("Logs", "LogRepository updated, reloading UI: ${allLogs.size} entries")
            filterLogs(searchBox.text.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val recycler = findViewById<RecyclerView>(R.id.recycler_logs)
        searchBox = findViewById(R.id.search_box)
        btnExportLast10 = findViewById(R.id.btn_export_last10)
        btnCopyLast10 = findViewById(R.id.btn_copy_last10)
        
        recycler.layoutManager = LinearLayoutManager(this)
        
        allLogs = LogRepository.getLogs()
        Log.v("Logs", "LogActivity onCreate: ${allLogs.size} log entries loaded")
        adapter = LogAdapter(allLogs) { log ->
            showDetails(log)
        }
        recycler.adapter = adapter
        
        // Setup search
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterLogs(s.toString())
            }
        })
        
        // Export last 10 button
        btnExportLast10.setOnClickListener {
            exportLast10Logs()
        }
        
        // Copy last 10 button
        btnCopyLast10.setOnClickListener {
            copyLast10ToClipboard()
        }
    }

    override fun onResume() {
        super.onResume()
        LogRepository.addListener(repoListener)
        // Refresh immediately in case of changes while paused
        allLogs = LogRepository.getLogs()
        Log.v("Logs", "LogActivity onResume: ${allLogs.size} log entries")
        filterLogs(searchBox.text.toString())
    }

    override fun onPause() {
        super.onPause()
        LogRepository.removeListener(repoListener)
    }

    private fun filterLogs(query: String) {
        if (query.isBlank()) {
            adapter.updateData(allLogs)
            return
        }
        
        val filtered = allLogs.filter { log ->
            log.packageName.contains(query, ignoreCase = true) ||
            log.title.contains(query, ignoreCase = true) ||
            log.content.contains(query, ignoreCase = true) ||
            log.status.name.contains(query, ignoreCase = true)
        }
        
        adapter.updateData(filtered)
    }

    private fun showDetails(log: LogEntry) {
        val message = "Status: ${log.status}\n\nRAW JSON:\n${log.rawJson}"
        
        AlertDialog.Builder(this)
            .setTitle(log.packageName)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }
    
    // ✅ Export last 10 logs to file (reuse pattern from DebugActivity)
    private fun exportLast10Logs() {
        try {
            val logs = allLogs.take(10)
            
            if (logs.isEmpty()) {
                Toast.makeText(this, "No logs to export", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Convert to NDJSON
            val gson = Gson()
            val ndjson = logs.joinToString("\n") { gson.toJson(it) }
            
            // Write to file
            val file = File(cacheDir, "notification_logs_last10.ndjson")
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
                putExtra(Intent.EXTRA_SUBJECT, "Notification Logs (Last 10)")
                putExtra(Intent.EXTRA_TEXT, "Last 10 notification logs from AlertsToSheets")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share Last 10 Logs"))
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // ✅ Copy last 10 logs to clipboard
    private fun copyLast10ToClipboard() {
        val logs = allLogs.take(10)
        
        if (logs.isEmpty()) {
            Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show()
            return
        }
        
        val gson = Gson()
        val ndjson = logs.joinToString("\n") { gson.toJson(it) }
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Last 10 Notification Logs", ndjson)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "Last 10 logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
