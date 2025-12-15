package com.example.alertsheets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SmsTargetAdapter(
    private var targets: List<SmsTarget>,
    private val onEdit: (SmsTarget) -> Unit,
    private val onToggle: (SmsTarget, Boolean) -> Unit
) : RecyclerView.Adapter<SmsTargetAdapter.ViewHolder>() {

    fun updateData(newTargets: List<SmsTarget>) {
        targets = newTargets
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms_target, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val target = targets[position]
        holder.bind(target)
    }

    override fun getItemCount() = targets.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_name)
        private val numberText: TextView = itemView.findViewById(R.id.text_number)
        private val filterText: TextView = itemView.findViewById(R.id.text_filter)
        private val editBtn: ImageView = itemView.findViewById(R.id.btn_edit)
        private val enableSwitch: Switch = itemView.findViewById(R.id.switch_enable)

        fun bind(target: SmsTarget) {
            nameText.text = if (target.name.isNotEmpty()) target.name else "Unknown"
            numberText.text = target.phoneNumber
            
            enableSwitch.setOnCheckedChangeListener(null)
            enableSwitch.isChecked = target.isEnabled
            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(target, isChecked)
            }
            
            editBtn.setOnClickListener { onEdit(target) }

            if (target.filterText.isNotEmpty()) {
                filterText.visibility = View.VISIBLE
                val case = if (target.isCaseSensitive) "Case Sensitive" else "IgnoreCase"
                filterText.text = "Filter: \"${target.filterText}\" ($case)"
            } else {
                filterText.visibility = View.GONE
            }
        }
    }
}
