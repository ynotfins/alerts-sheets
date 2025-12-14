package com.example.alertsheets

import android.app.AlertDialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LogActivity : AppCompatActivity() {

    private lateinit var adapter: LogAdapter
    private val repoListener = {
        runOnUiThread {
            adapter.updateData(LogRepository.getLogs())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val recycler = findViewById<RecyclerView>(R.id.recycler_logs)
        recycler.layoutManager = LinearLayoutManager(this)
        
        adapter = LogAdapter(LogRepository.getLogs()) { log ->
            showDetails(log)
        }
        recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        LogRepository.addListener(repoListener)
        // Refresh immediately in case of changes while paused
        adapter.updateData(LogRepository.getLogs())
    }

    override fun onPause() {
        super.onPause()
        LogRepository.removeListener(repoListener)
    }

    private fun showDetails(log: LogEntry) {
        val message = "Status: ${log.status}\n\nRAW JSON:\n${log.rawJson}"
        
        AlertDialog.Builder(this)
            .setTitle(log.packageName)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }
}
