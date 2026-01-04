package com.example.alertsheets.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.alertsheets.R
import com.example.alertsheets.utils.StructuredLogger

class DebugLogsAdapter : RecyclerView.Adapter<DebugLogsAdapter.LogViewHolder>() {
    
    private var logs: List<StructuredLogger.LogEntry> = emptyList()
    
    fun setLogs(newLogs: List<StructuredLogger.LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_debug_log, parent, false)
        return LogViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }
    
    override fun getItemCount() = logs.size
    
    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvLogTimestamp)
        private val tvEvent: TextView = itemView.findViewById(R.id.tvLogEvent)
        private val tvLevel: TextView = itemView.findViewById(R.id.tvLogLevel)
        private val tvSourceId: TextView = itemView.findViewById(R.id.tvLogSourceId)
        private val tvEndpointId: TextView = itemView.findViewById(R.id.tvLogEndpointId)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvLogDetails)
        
        fun bind(log: StructuredLogger.LogEntry) {
            tvTimestamp.text = log.timestamp.substringAfter("T").substringBefore(".")
            tvEvent.text = log.event
            tvLevel.text = log.level
            tvSourceId.text = log.source_id ?: "-"
            tvEndpointId.text = log.endpoint_id ?: "-"
            tvDetails.text = log.details ?: "-"
            
            // Color code by level
            when (log.level) {
                "ERROR" -> tvLevel.setTextColor(Color.RED)
                "WARN" -> tvLevel.setTextColor(Color.parseColor("#FFA500")) // Orange
                else -> tvLevel.setTextColor(Color.parseColor("#4CAF50")) // Green
            }
            
            // Color code by event
            when (log.event) {
                "http_ok", "success" -> tvEvent.setTextColor(Color.parseColor("#4CAF50"))
                "http_fail" -> tvEvent.setTextColor(Color.RED)
                "retry_scheduled" -> tvEvent.setTextColor(Color.parseColor("#FFA500"))
                else -> tvEvent.setTextColor(Color.WHITE) // high contrast for dark mode
            }

            // Ensure details are readable in dark mode
            tvTimestamp.setTextColor(Color.parseColor("#E0E0E0"))
            tvSourceId.setTextColor(Color.parseColor("#E0E0E0"))
            tvEndpointId.setTextColor(Color.parseColor("#E0E0E0"))
            tvDetails.setTextColor(Color.parseColor("#D0D0D0"))
        }
    }
}

