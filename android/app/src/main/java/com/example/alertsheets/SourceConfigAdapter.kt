package com.example.alertsheets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceType

class SourceConfigAdapter(
    private val sources: MutableList<Source>,
    private val sourceManager: SourceManager,
    private val onSourceClick: (Source) -> Unit,
    private val onSourceToggle: (Source, Boolean) -> Unit,
    private val onSourceDelete: (Source) -> Unit
) : RecyclerView.Adapter<SourceConfigAdapter.SourceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_source, parent, false)
        return SourceViewHolder(view)
    }

    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        holder.bind(sources[position])
    }

    override fun getItemCount() = sources.size

    inner class SourceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: View = itemView.findViewById(R.id.view_source_icon)
        private val nameText: TextView = itemView.findViewById(R.id.text_source_name)
        private val typeText: TextView = itemView.findViewById(R.id.text_source_type)
        private val endpointText: TextView = itemView.findViewById(R.id.text_endpoint)
        private val templateText: TextView = itemView.findViewById(R.id.text_template)
        private val parserText: TextView = itemView.findViewById(R.id.text_parser)
        private val statsText: TextView = itemView.findViewById(R.id.text_stats)
        private val enabledSwitch: SwitchCompat = itemView.findViewById(R.id.switch_enabled)

        fun bind(source: Source) {
            // Icon color
            iconView.backgroundTintList = android.content.res.ColorStateList.valueOf(source.iconColor)
            
            // Name and type
            nameText.text = source.name
            typeText.text = when (source.type) {
                SourceType.APP -> "APP · ${source.id}"
                SourceType.SMS -> "SMS · ${source.id.removePrefix("sms:")}"
            }
            
            // Get endpoint names (fan-out: show all endpoints)
            val endpointNames = source.endpointIds.mapNotNull { endpointId ->
                sourceManager.getEndpointById(endpointId)?.name
            }.joinToString(", ")
            endpointText.text = if (endpointNames.isNotEmpty()) {
                "$endpointNames (${source.endpointIds.size})"
            } else {
                "⚠️ No endpoints configured"
            }
            
            // Template (just show ID for now - could be improved)
            templateText.text = source.templateId
            
            // Parser
            parserText.text = source.parserId
            
            // Stats
            val stats = source.stats
            statsText.text = "${stats.totalSent} sent / ${stats.totalProcessed} total" +
                if (stats.totalFailed > 0) " (${stats.totalFailed} failed)" else ""
            
            // Enabled switch
            enabledSwitch.setOnCheckedChangeListener(null) // Remove old listener
            enabledSwitch.isChecked = source.enabled
            enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onSourceToggle(source, isChecked)
            }
            
            // Click to edit
            itemView.setOnClickListener {
                onSourceClick(source)
            }
            
            // Long click to delete
            itemView.setOnLongClickListener {
                onSourceDelete(source)
                true
            }
        }
    }
}

