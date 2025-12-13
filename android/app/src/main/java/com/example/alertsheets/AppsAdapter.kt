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
        val name: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pkg = apps[position]
        holder.name.text = pkg // In real app, load label/icon
        holder.itemView.setOnClickListener { onClick(pkg) }
    }

    override fun getItemCount() = apps.size
    
    fun updateData(newList: List<String>) {
        apps = newList
        notifyDataSetChanged()
    }
}
