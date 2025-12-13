package com.example.alertsheets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppsAdapter(
    private var apps: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.text_app_name)
        val packageName: TextView = view.findViewById(R.id.text_package_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_configured_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pkg = apps[position]
        holder.packageName.text = pkg
        
        // Try to load label
        try {
            val pm = holder.itemView.context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            holder.appName.text = pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            holder.appName.text = pkg
        }

        holder.itemView.setOnClickListener { onClick(pkg) }
    }

    override fun getItemCount() = apps.size
    
    fun updateData(newList: List<String>) {
        apps = newList
        notifyDataSetChanged()
    }
}
