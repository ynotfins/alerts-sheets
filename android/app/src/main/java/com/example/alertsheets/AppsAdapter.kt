package com.example.alertsheets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppsAdapter(
    private var apps: MutableList<android.content.pm.ApplicationInfo>,
    private val selectedApps: MutableSet<String>,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.text_app_name)
        val packageName: TextView = view.findViewById(R.id.text_package_name)
        val checkBox: android.widget.CheckBox = view.findViewById(R.id.checkbox_app)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_configured_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val info = apps[position]
        val pkg = info.packageName
        
        holder.packageName.text = pkg
        holder.appName.text = try {
            info.loadLabel(holder.itemView.context.packageManager).toString()
        } catch(e: Exception) { pkg }
        
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedApps.contains(pkg)
        
        holder.itemView.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked
            onToggle(pkg, holder.checkBox.isChecked)
        }
        
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
             // If clicked directly (though clickable=false in XML usually good for list items, but user might tab)
             onToggle(pkg, isChecked)
        }
    }

    override fun getItemCount() = apps.size
    
    // Removed updateData simple string list, assuming list is mutable and passed by ref or we simply notify
    // AppsListActivity clears and addsAll to the list passed in constructor, then notifyDataSetChanged.
    // So distinct updateData not strictly needed if we share reference.
}
