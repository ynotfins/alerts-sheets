package com.example.alertsheets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EndpointsAdapter(
    private var endpoints: List<Endpoint>,
    private val onToggle: (Endpoint, Boolean) -> Unit,
    private val onDelete: (Endpoint) -> Unit // Will add delete on long press later
) : RecyclerView.Adapter<EndpointsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_name)
        val url: TextView = view.findViewById(R.id.text_url)
        val switchEnabled: Switch = view.findViewById(R.id.switch_enabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_endpoint, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = endpoints[position]
        holder.name.text = item.name
        holder.url.text = item.url
        holder.switchEnabled.setOnCheckedChangeListener(null) // Reset listener
        holder.switchEnabled.isChecked = item.isEnabled
        
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggle(item, isChecked)
        }
        
        holder.itemView.setOnLongClickListener {
            onDelete(item)
            true
        }
    }

    override fun getItemCount() = endpoints.size
    
    fun updateData(newList: List<Endpoint>) {
        endpoints = newList
        notifyDataSetChanged()
    }
}
