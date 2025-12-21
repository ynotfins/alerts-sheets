package com.example.alertsheets

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.alertsheets.domain.models.Source

/**
 * SmsSourceAdapter - V2 adapter for Source objects with type=SMS
 */
class SmsSourceAdapter(
    private var sources: List<Source>,
    private val onEdit: (Source) -> Unit,
    private val onToggle: (Source, Boolean) -> Unit
) : RecyclerView.Adapter<SmsSourceAdapter.ViewHolder>() {

    fun updateData(newSources: List<Source>) {
        sources = newSources
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms_target, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val source = sources[position]
        holder.bind(source)
    }

    override fun getItemCount() = sources.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_name)
        private val numberText: TextView = itemView.findViewById(R.id.text_number)
        private val filterText: TextView = itemView.findViewById(R.id.text_filter)
        private val editBtn: ImageView = itemView.findViewById(R.id.btn_edit)
        private val enableSwitch: Switch = itemView.findViewById(R.id.switch_enable)

        fun bind(source: Source) {
            nameText.text = source.name
            numberText.text = source.id.removePrefix("sms:")
            
            enableSwitch.setOnCheckedChangeListener(null)
            enableSwitch.isChecked = source.enabled
            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(source, isChecked)
            }
            
            editBtn.setOnClickListener { onEdit(source) }

            // Get filter from extras (stored separately for now)
            val extras = itemView.context.getSharedPreferences("source_extras", Context.MODE_PRIVATE)
            val filter = extras.getString("${source.id}:filterText", "") ?: ""
            val caseSensitive = extras.getBoolean("${source.id}:isCaseSensitive", false)
            
            if (filter.isNotEmpty()) {
                filterText.visibility = View.VISIBLE
                val caseLabel = if (caseSensitive) "Case Sensitive" else "Ignore Case"
                filterText.text = "Filter: \"$filter\" ($caseLabel)"
            } else {
                filterText.visibility = View.GONE
            }
        }
    }
}

