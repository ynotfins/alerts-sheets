package com.example.alertsheets

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alertsheets.data.repositories.TemplateRepository
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.Endpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SourceConfigActivity - Configure Source→Endpoint→Template bindings
 * 
 * This is the CRITICAL missing piece that allows users to:
 * - See all configured sources (apps + SMS)
 * - Edit endpoint assignment
 * - Edit template assignment  
 * - Edit parser selection
 * - Toggle autoClean
 * - Enable/disable sources
 * - Delete sources
 */
class SourceConfigActivity : AppCompatActivity() {

    private lateinit var sourceManager: SourceManager
    private lateinit var templateRepo: TemplateRepository
    private lateinit var adapter: SourceConfigAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var recycler: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var sourceCountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_source_config)
        
        sourceManager = SourceManager(applicationContext)
        templateRepo = TemplateRepository(applicationContext)
        
        recycler = findViewById(R.id.recycler_sources)
        progressBar = findViewById(R.id.progress_loading)
        emptyText = findViewById(R.id.text_empty)
        sourceCountText = findViewById(R.id.text_source_count)
        
        recycler.layoutManager = LinearLayoutManager(this)
        
        // ✅ FAB for creating new sources
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_source).setOnClickListener {
            showCreateSourceDialog()
        }
        
        loadSources()
    }
    
    private fun loadSources() {
        scope.launch {
            progressBar.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            emptyText.visibility = View.GONE
            
            val sources = withContext(Dispatchers.IO) {
                sourceManager.getAllSources()
            }
            
            if (sources.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
            } else {
                adapter = SourceConfigAdapter(
                    sources = sources.toMutableList(),
                    sourceManager = sourceManager,
                    onSourceClick = { source -> showEditDialog(source) },
                    onSourceToggle = { source, enabled -> toggleSource(source, enabled) },
                    onSourceDelete = { source -> deleteSource(source) }
                )
                recycler.adapter = adapter
                recycler.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                
                sourceCountText.text = "${sources.size} source${if (sources.size != 1) "s" else ""} configured"
            }
        }
    }
    
    private fun showEditDialog(source: Source) {
        scope.launch {
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_source, null)
            
            // Load data
            val endpoints = withContext(Dispatchers.IO) { sourceManager.getEndpoints() }
            val templates = withContext(Dispatchers.IO) { templateRepo.getAllTemplates() }
            
            // Setup spinners
            val endpointSpinner = dialogView.findViewById<Spinner>(R.id.spinner_endpoint)
            val templateSpinner = dialogView.findViewById<Spinner>(R.id.spinner_template)
            val parserSpinner = dialogView.findViewById<Spinner>(R.id.spinner_parser)
            val autoCleanSwitch = dialogView.findViewById<Switch>(R.id.switch_autoclean)
            
            // Endpoint dropdown
            val endpointNames = endpoints.map { it.name }
            val endpointAdapter = ArrayAdapter(this@SourceConfigActivity, android.R.layout.simple_spinner_item, endpointNames)
            endpointAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            endpointSpinner.adapter = endpointAdapter
            endpointSpinner.setSelection(endpoints.indexOfFirst { it.id == source.endpointId }.coerceAtLeast(0))
            
            // Template dropdown
            val templateNames = templates.map { it.name }
            val templateAdapter = ArrayAdapter(this@SourceConfigActivity, android.R.layout.simple_spinner_item, templateNames)
            templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            templateSpinner.adapter = templateAdapter
            templateSpinner.setSelection(templates.indexOfFirst { it.name == source.templateId }.coerceAtLeast(0))
            
            // Parser dropdown
            val parsers = listOf("generic", "bnn", "sms")
            val parserAdapter = ArrayAdapter(this@SourceConfigActivity, android.R.layout.simple_spinner_item, parsers)
            parserAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            parserSpinner.adapter = parserAdapter
            parserSpinner.setSelection(parsers.indexOf(source.parserId).coerceAtLeast(0))
            
            // AutoClean switch
            autoCleanSwitch.isChecked = source.autoClean
            
            AlertDialog.Builder(this@SourceConfigActivity)
                .setTitle("Configure ${source.name}")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val selectedEndpoint = endpoints[endpointSpinner.selectedItemPosition]
                    val selectedTemplate = templates[templateSpinner.selectedItemPosition]
                    val selectedParser = parsers[parserSpinner.selectedItemPosition]
                    val autoClean = autoCleanSwitch.isChecked
                    
                    val updatedSource = source.copy(
                        endpointId = selectedEndpoint.id,
                        templateId = selectedTemplate.name,  // ✅ Use template name as ID
                        parserId = selectedParser,
                        autoClean = autoClean,
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    scope.launch(Dispatchers.IO) {
                        sourceManager.saveSource(updatedSource)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SourceConfigActivity, "Source updated", Toast.LENGTH_SHORT).show()
                            loadSources() // Refresh list
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun toggleSource(source: Source, enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            sourceManager.setSourceEnabled(source.id, enabled)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SourceConfigActivity, 
                    if (enabled) "${source.name} enabled" else "${source.name} disabled", 
                    Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun deleteSource(source: Source) {
        AlertDialog.Builder(this)
            .setTitle("Delete Source?")
            .setMessage("Delete ${source.name}?\n\nNotifications from this source will no longer be processed.")
            .setPositiveButton("Delete") { _, _ ->
                scope.launch(Dispatchers.IO) {
                    sourceManager.deleteSource(source.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SourceConfigActivity, "${source.name} deleted", Toast.LENGTH_SHORT).show()
                        loadSources() // Refresh list
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show dialog to create a new source from scratch
     * This allows creating generic sources without going through App List or SMS List
     */
    private fun showCreateSourceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_source, null)
        
        val inputName = dialogView.findViewById<EditText>(R.id.input_new_source_name)
        val inputId = dialogView.findViewById<EditText>(R.id.input_new_source_id)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinner_new_source_type)
        val spinnerEndpoint = dialogView.findViewById<Spinner>(R.id.spinner_new_endpoint)
        val spinnerParser = dialogView.findViewById<Spinner>(R.id.spinner_new_parser)
        val switchAutoClean = dialogView.findViewById<Switch>(R.id.switch_new_auto_clean)
        
        // Type spinner
        val types = listOf("APP", "SMS")
        spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        // Parsers spinner
        val parsers = listOf("generic", "bnn", "sms")
        spinnerParser.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, parsers).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        // Load endpoints async
        scope.launch(Dispatchers.IO) {
            val endpoints = sourceManager.getEndpoints()
            withContext(Dispatchers.Main) {
                val endpointNames = endpoints.map { it.name }
                spinnerEndpoint.adapter = ArrayAdapter(this@SourceConfigActivity, android.R.layout.simple_spinner_item, endpointNames).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                
                AlertDialog.Builder(this@SourceConfigActivity)
                    .setTitle("Create New Source")
                    .setView(dialogView)
                    .setPositiveButton("Create") { _, _ ->
                        val name = inputName.text.toString().trim()
                        val id = inputId.text.toString().trim()
                        val type = if (spinnerType.selectedItemPosition == 0) com.example.alertsheets.domain.models.SourceType.APP else com.example.alertsheets.domain.models.SourceType.SMS
                        val selectedEndpoint = endpoints[spinnerEndpoint.selectedItemPosition]
                        val parserId = parsers[spinnerParser.selectedItemPosition]
                        val autoClean = switchAutoClean.isChecked
                        
                        if (name.isEmpty() || id.isEmpty()) {
                            Toast.makeText(this@SourceConfigActivity, "Name and ID are required", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        
                        // Create new source with default template JSON
                        val templateJson = templateRepo.getDefaultJsonForNewSource(type)
                        val newSource = Source(
                            id = id,
                            type = type,
                            name = name,
                            enabled = true,
                            templateJson = templateJson,
                            templateId = "", // No longer used
                            autoClean = autoClean,
                            parserId = parserId,
                            endpointId = selectedEndpoint.id,
                            iconColor = if (type == com.example.alertsheets.domain.models.SourceType.APP) 0xFF4A9EFF.toInt() else 0xFF00D980.toInt(),
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        
                        scope.launch(Dispatchers.IO) {
                            sourceManager.saveSource(newSource)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SourceConfigActivity, "Source created: $name", Toast.LENGTH_SHORT).show()
                                loadSources()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

