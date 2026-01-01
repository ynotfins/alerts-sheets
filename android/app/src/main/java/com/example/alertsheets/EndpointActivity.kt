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
import com.example.alertsheets.domain.models.Endpoint
import com.example.alertsheets.domain.models.EndpointStats

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
            onToggle = { position, isEnabled ->
                // ✅ Use position-based indexing with bounds check
                if (position < 0 || position >= endpoints.size) {
                    android.util.Log.e("EndpointActivity", "Invalid position: $position (size=${endpoints.size})")
                    return@EndpointsAdapter
                }
                
                try {
                    val endpoint = endpoints[position]
                    
                    // ✅ Log BEFORE state for verification
                    android.util.Log.d("EndpointActivity", "Endpoint toggle: name=${endpoint.name}, enabled_before=${endpoint.enabled}, enabled_after=$isEnabled, position=$position")
                    
                    val updated = endpoint.copy(enabled = isEnabled, updatedAt = System.currentTimeMillis())
                    endpoints[position] = updated
                    
                    android.util.Log.d("EndpointActivity", "Calling saveEndpoints() for: ${endpoint.name}")
                    saveEndpoints()
                    
                    // ✅ Notify adapter about change (prevents screen close)
                    adapter.notifyItemChanged(position)
                    
                    // ✅ Show Toast with NEW state (updated.enabled == isEnabled)
                    android.widget.Toast.makeText(
                        this@EndpointActivity,
                        "${updated.name} ${if (updated.enabled) "enabled" else "disabled"}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    android.util.Log.d("EndpointActivity", "Toggle complete: ${updated.name} now enabled=${updated.enabled}, screen should stay open")
                } catch (e: Exception) {
                    android.util.Log.e("EndpointActivity", "Error toggling endpoint", e)
                    android.widget.Toast.makeText(
                        this@EndpointActivity,
                        "Error updating endpoint: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
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
        android.util.Log.d("EndpointActivity", "saveEndpoints() called - saving ${endpoints.size} endpoints")
        endpointRepository.saveAll(endpoints)
        android.util.Log.d("EndpointActivity", "saveEndpoints() complete - NO finish() called")
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
                    val newEndpoint = Endpoint(
                        id = "endpoint-${System.currentTimeMillis()}",
                        name = finalName,
                        url = url,
                        enabled = true,
                        timeout = 30000,
                        retryCount = 3,
                        headers = emptyMap(),
                        stats = EndpointStats(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    endpoints.add(newEndpoint)
                    saveEndpoints()
                    adapter.updateData(endpoints)
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
