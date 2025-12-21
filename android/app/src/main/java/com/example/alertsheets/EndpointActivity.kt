package com.example.alertsheets

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alertsheets.data.repositories.EndpointRepository

/**
 * EndpointActivity - V2 Repository-based endpoint management
 * 
 * NOW USES: EndpointRepository (not PrefsManager directly)
 */
class EndpointActivity : AppCompatActivity() {

    private lateinit var adapter: EndpointsAdapter
    private lateinit var endpointRepository: EndpointRepository
    private var endpoints: MutableList<Endpoint> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ V2: Use repository
        endpointRepository = EndpointRepository(this)
        
        // Generate Layout Programmatically for speed
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "Manage Endpoints"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 32)
        }
        layout.addView(header)

        val btnAdd = Button(this).apply {
            text = "Add New Endpoint"
            setOnClickListener { showAddDialog() }
        }
        layout.addView(btnAdd)

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@EndpointActivity)
            setPadding(0, 32, 0, 0)
        }
        layout.addView(recycler)

        // ✅ V2: Load from repository
        endpoints = endpointRepository.getAll().toMutableList()
        adapter = EndpointsAdapter(endpoints, 
            onToggle = { endpoint, isEnabled ->
                endpoint.isEnabled = isEnabled
                saveEndpoints()
            },
            onDelete = { endpoint ->
                showEditDialog(endpoint)
            }
        )
        recycler.adapter = adapter
        
        setContentView(layout)
    }

    private fun saveEndpoints() {
        // ✅ V2: Save via repository
        endpointRepository.saveAll(endpoints)
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_endpoint, null)
        val inputName = view.findViewById<EditText>(R.id.input_name)
        val inputUrl = view.findViewById<EditText>(R.id.input_url)
        
        AlertDialog.Builder(this)
            .setTitle("Add Endpoint")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val name = inputName.text.toString().trim()
                val url = inputUrl.text.toString().trim()
                if (url.isNotEmpty()) {
                    val finalName = if (name.isEmpty()) "Endpoint ${endpoints.size + 1}" else name
                    endpoints.add(Endpoint(name = finalName, url = url))
                    saveEndpoints()
                    adapter.updateData(endpoints) // Fix adapter update
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEditDialog(endpoint: Endpoint) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_endpoint, null)
        val inputName = view.findViewById<EditText>(R.id.input_name)
        val inputUrl = view.findViewById<EditText>(R.id.input_url)
        
        inputName.setText(endpoint.name)
        inputUrl.setText(endpoint.url)
        
        AlertDialog.Builder(this)
            .setTitle("Edit Endpoint")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = inputName.text.toString().trim()
                val url = inputUrl.text.toString().trim()
                if (url.isNotEmpty()) {
                    val updated = endpoint.copy(
                        name = if (name.isEmpty()) "Endpoint" else name,
                        url = url
                    )
                    val index = endpoints.indexOf(endpoint)
                    if (index >= 0) {
                        endpoints[index] = updated
                    }
                    saveEndpoints()
                    adapter.updateData(endpoints)
                }
            }
            .setNeutralButton("Delete") { _, _ ->
                endpoints.remove(endpoint)
                saveEndpoints()
                adapter.updateData(endpoints)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
