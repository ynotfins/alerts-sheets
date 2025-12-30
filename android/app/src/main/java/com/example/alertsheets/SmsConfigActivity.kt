package com.example.alertsheets

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceType
import com.example.alertsheets.utils.EndpointValidators
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * SmsConfigActivity - V3 with Endpoint Selection + Template Guardrails
 * 
 * ✅ Enforces endpoint validity (enabled + valid URL)
 * ✅ Detects template bleed-through (SMS source with APP template)
 * ✅ Prevents saving invalid configurations
 */
class SmsConfigActivity : AppCompatActivity() {

    private lateinit var sourceManager: SourceManager
    private lateinit var adapter: SmsSourceAdapter
    private var smsSources: MutableList<Source> = mutableListOf()
    
    // For contact picker dialog
    private var currentNumberInput: EditText? = null
    private var currentNameInput: EditText? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processContactUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_config)

        sourceManager = SourceManager(applicationContext)
        smsSources = sourceManager.getSourcesByType(SourceType.SMS).toMutableList()

        val recycler = findViewById<RecyclerView>(R.id.recycler_sms)
        recycler.layoutManager = LinearLayoutManager(this)
        
        adapter = SmsSourceAdapter(smsSources, 
            onEdit = { source -> showAddEditDialog(source) },
            onToggle = { source, isEnabled ->
                val updated = source.copy(
                    enabled = isEnabled,
                    updatedAt = System.currentTimeMillis()
                )
                sourceManager.saveSource(updated)
                loadSources()
            }
        )
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            showAddEditDialog(null)
        }
    }
    
    private fun loadSources() {
        smsSources.clear()
        smsSources.addAll(sourceManager.getSourcesByType(SourceType.SMS))
        adapter.updateData(smsSources)
    }
    
    private fun showAddEditDialog(source: Source?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_sms, null)
        
        val inputNumber = view.findViewById<EditText>(R.id.input_number)
        val inputName = view.findViewById<EditText>(R.id.input_name)
        val inputFilter = view.findViewById<EditText>(R.id.input_filter)
        val checkCase = view.findViewById<CheckBox>(R.id.check_case)
        val btnPick = view.findViewById<ImageButton>(R.id.btn_pick_contact)
        val templateWarningBanner = view.findViewById<LinearLayout>(R.id.template_warning_banner)
        val btnFixTemplate = view.findViewById<Button>(R.id.btn_fix_template)
        val containerEndpoints = view.findViewById<LinearLayout>(R.id.container_endpoints)
        val textEndpointHint = view.findViewById<TextView>(R.id.text_endpoint_hint)
        
        // Track selected endpoint IDs
        val selectedEndpointIds = mutableSetOf<String>()
        val endpointCheckBoxes = mutableMapOf<String, CheckBox>()
        
        // Load all endpoints and populate checkboxes
        val allEndpoints = sourceManager.getEndpoints()
        if (allEndpoints.isEmpty()) {
            // No endpoints configured at all
            containerEndpoints.addView(TextView(this).apply {
                text = "⚠️ No endpoints configured! Go to Endpoints page first."
                textSize = 14f
                setTextColor(0xFFFF5722.toInt())
                setPadding(0, 8, 0, 8)
            })
        } else {
            allEndpoints.forEach { endpoint ->
                val isSelectable = EndpointValidators.isSelectable(endpoint)
                val displayLabel = EndpointValidators.getDisplayLabel(endpoint)
                
                val checkBox = CheckBox(this).apply {
                    text = displayLabel
                    isEnabled = isSelectable
                    
                    // Set colors
                    if (!isSelectable) {
                        setTextColor(0xFF888888.toInt()) // Gray for disabled
                    } else {
                        setTextColor(0xFF000000.toInt()) // Black for enabled
                    }
                    
                    // Check if this endpoint was previously selected
                    if (source != null && source.endpointIds.contains(endpoint.id)) {
                        isChecked = isSelectable // Only check if still selectable
                        if (isSelectable) {
                            selectedEndpointIds.add(endpoint.id)
                        }
                    }
                    
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedEndpointIds.add(endpoint.id)
                        } else {
                            selectedEndpointIds.remove(endpoint.id)
                        }
                        // Update hint color based on whether any selected endpoint is valid
                        val enabledSelected = selectedEndpointIds.any { id ->
                            allEndpoints.find { it.id == id }?.let { EndpointValidators.isSelectable(it) } == true
                        }
                        textEndpointHint.setTextColor(if (enabledSelected) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt())
                    }
                }
                
                containerEndpoints.addView(checkBox)
                endpointCheckBoxes[endpoint.id] = checkBox
            }
        }
        
        // Populate if edit
        var currentTemplateJson = ""
        if (source != null) {
            inputNumber.setText(source.id.removePrefix("sms:"))
            inputName.setText(source.name)
            currentTemplateJson = source.templateJson
            
            // Get filter from extras
            val extras = getSharedPreferences("source_extras", MODE_PRIVATE)
            inputFilter.setText(extras.getString("${source.id}:filterText", ""))
            checkCase.isChecked = extras.getBoolean("${source.id}:isCaseSensitive", false)
            
            // ✅ Check for template bleed-through
            if (isAppTemplate(currentTemplateJson)) {
                templateWarningBanner.visibility = View.VISIBLE
            }
        }
        
        // Store refs for picker callback
        currentNumberInput = inputNumber
        currentNameInput = inputName
        
        btnPick.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 101)
            }
        }
        
        // ✅ Fix Template button
        btnFixTemplate.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset to SMS Template?")
                .setMessage("This will replace the current template with the default SMS template:\n\n{{sender}}\n{{message}}\n{{time}}")
                .setPositiveButton("Reset") { _, _ ->
                    val templateRepo = com.example.alertsheets.data.repositories.TemplateRepository(this@SmsConfigActivity)
                    currentTemplateJson = templateRepo.getSmsTemplate()
                    templateWarningBanner.visibility = View.GONE
                    Toast.makeText(this, "Template reset to SMS default", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        val title = if (source == null) "Add SMS Source" else "Edit SMS Source"
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val number = inputNumber.text.toString().trim()
                if (number.isEmpty()) {
                    Toast.makeText(this, "Phone Number is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val name = inputName.text.toString().trim().ifEmpty { "Unknown" }
                val filter = inputFilter.text.toString()
                val caseSensitive = checkCase.isChecked
                
                // ✅ Validate endpoint selection using EndpointValidators
                val selectedEndpoints = selectedEndpointIds.mapNotNull { id ->
                    allEndpoints.find { it.id == id }
                }
                val enabledSelectedEndpoints = EndpointValidators.filterSelectable(selectedEndpoints).map { it.id }
                
                if (enabledSelectedEndpoints.isEmpty()) {
                    Toast.makeText(
                        this,
                        "⚠️ Select at least one ENABLED endpoint\n\n" +
                        "Disabled endpoints cannot be selected.\n" +
                        "Configure endpoints on the Endpoints page.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                
                // ✅ Get template (use fixed template if user clicked fix, otherwise preserve existing)
                val templateRepo = com.example.alertsheets.data.repositories.TemplateRepository(this@SmsConfigActivity)
                val finalTemplateJson = if (currentTemplateJson.isBlank()) {
                    templateRepo.getSmsTemplate()
                } else {
                    currentTemplateJson
                }
                
                // ✅ Final check: If still using APP template, confirm with user
                if (isAppTemplate(finalTemplateJson)) {
                    AlertDialog.Builder(this)
                        .setTitle("⚠️ Template Mismatch")
                        .setMessage("This SMS source is using an App template.\n\n" +
                                   "This may cause {{package}}, {{title}} placeholders to appear instead of actual SMS content.\n\n" +
                                   "Save anyway?")
                        .setPositiveButton("Save Anyway") { _, _ ->
                            saveSource(source, number, name, filter, caseSensitive, enabledSelectedEndpoints, finalTemplateJson)
                        }
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Fix Template") { _, _ ->
                            // Reopen dialog with fixed template
                            currentTemplateJson = templateRepo.getSmsTemplate()
                            showAddEditDialog(source)
                        }
                        .show()
                    return@setPositiveButton
                }
                
                // All checks passed, save
                saveSource(source, number, name, filter, caseSensitive, enabledSelectedEndpoints, finalTemplateJson)
            }
            .setNegativeButton("Cancel", null)
            
        if (source != null) {
            dialog.setNeutralButton("Delete") { _, _ ->
                sourceManager.deleteSource(source.id)
                loadSources()
                Toast.makeText(this, "SMS Source deleted", Toast.LENGTH_SHORT).show()
            }
        }
            
        dialog.show()
    }
    
    /**
     * Check if template JSON contains APP schema fields
     */
    private fun isAppTemplate(templateJson: String): Boolean {
        return templateJson.contains("{{package}}", ignoreCase = true) ||
               templateJson.contains("{{title}}", ignoreCase = true) ||
               templateJson.contains("{{text}}", ignoreCase = true) ||
               templateJson.contains("{{bigText}}", ignoreCase = true)
    }
    
    /**
     * Save SMS source with validation
     */
    private fun saveSource(
        source: Source?,
        number: String,
        name: String,
        filter: String,
        caseSensitive: Boolean,
        endpointIds: List<String>,
        templateJson: String
    ) {
        val newSource = if (source == null) {
            // New SMS source
            Source(
                id = "sms:$number",
                type = SourceType.SMS,
                name = name,
                enabled = true,
                autoClean = true,
                templateJson = templateJson,
                templateId = "rock-solid-sms-default",
                parserId = "sms",
                endpointIds = endpointIds,
                iconColor = 0xFF00D980.toInt(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        } else {
            // Update existing
            source.copy(
                id = "sms:$number",
                name = name,
                templateJson = templateJson,
                endpointIds = endpointIds,
                updatedAt = System.currentTimeMillis()
            )
        }
        
        if (!newSource.isValid()) {
            Toast.makeText(
                this,
                "⚠️ Source validation failed: must have at least one endpoint",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Save filter settings
        getSharedPreferences("source_extras", MODE_PRIVATE).edit()
            .putString("${newSource.id}:filterText", filter)
            .putBoolean("${newSource.id}:isCaseSensitive", caseSensitive)
            .putString("${newSource.id}:phoneNumber", number)
            .apply()
        
        sourceManager.saveSource(newSource)
        loadSources()
        
        Toast.makeText(this, "SMS Source saved ✓", Toast.LENGTH_SHORT).show()
    }
    
    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }
    
    private fun processContactUri(uri: Uri) {
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                if (nameIndex >= 0 && numberIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    val number = cursor.getString(numberIndex)
                    
                    currentNumberInput?.setText(number)
                    currentNameInput?.setText(name)
                }
            }
        } finally {
            cursor?.close()
        }
    }
}
