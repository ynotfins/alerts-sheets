package com.example.alertsheets

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(
    private var logs: List<LogEntry>,
    private val onClick: (LogEntry) -> Unit
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun updateData(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        holder.bind(log)
    }

    override fun getItemCount() = logs.size

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val statusIcon: ImageView = itemView.findViewById(R.id.img_log_status)
        private val pkgText: TextView = itemView.findViewById(R.id.text_log_pkg)
        private val contentText: TextView = itemView.findViewById(R.id.text_log_content)
        private val timeText: TextView = itemView.findViewById(R.id.text_log_time)

        fun bind(log: LogEntry) {
            pkgText.text = log.packageName
            contentText.text = if(log.title.isNotBlank()) "${log.title}: ${log.content}" else log.content
            timeText.text = dateFormat.format(Date(log.timestamp))

            when (log.status) {
                LogStatus.SENT -> {
                    statusIcon.setColorFilter(Color.parseColor("#4CAF50")) // Green
                }
                LogStatus.FAILED -> {
                    statusIcon.setColorFilter(Color.parseColor("#F44336")) // Red
                }
                LogStatus.IGNORED -> {
                    statusIcon.setColorFilter(Color.parseColor("#9E9E9E")) // Gray
                }
                LogStatus.PENDING -> {
                    statusIcon.setColorFilter(Color.parseColor("#2196F3")) // Blue
                }
            }

            itemView.setOnClickListener { onClick(log) }
        }
    }
}
