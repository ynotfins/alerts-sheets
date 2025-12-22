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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceType
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * SmsConfigActivity - V2 Source-based SMS configuration
 * 
 * ✅ NOW USES SOURCEMANAGER (not PrefsManager)
 * Creates Source objects with type=SMS
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

        // ✅ V2: Use SourceManager
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
        
        // Populate if edit
        if (source != null) {
            inputNumber.setText(source.id.removePrefix("sms:"))
            inputName.setText(source.name)
            
            // Get filter from extras
            val extras = getSharedPreferences("source_extras", MODE_PRIVATE)
            inputFilter.setText(extras.getString("${source.id}:filterText", ""))
            checkCase.isChecked = extras.getBoolean("${source.id}:isCaseSensitive", false)
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
                
                // ✅ CRITICAL: Get first available endpoint OR FAIL
                val firstEndpointId = sourceManager.getFirstEndpointId()
                if (firstEndpointId == null) {
                    Toast.makeText(
                        this,
                        "⚠️ No endpoints configured! Create an endpoint first on the Endpoints page.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                
                // ✅ V2: Create or update Source
                // ✅ Get default template JSON for SMS
                val templateRepo = com.example.alertsheets.data.repositories.TemplateRepository(this@SmsConfigActivity)
                val defaultSmsTemplate = templateRepo.getSmsTemplate()
                
                val newSource = if (source == null) {
                    // New SMS source
                    Source(
                        id = "sms:$number",
                        type = SourceType.SMS,
                        name = name,
                        enabled = true,
                        autoClean = true,  // SMS default: clean emojis
                        templateJson = defaultSmsTemplate,  // ✅ Store template JSON directly
                        templateId = "rock-solid-sms-default",  // DEPRECATED
                        parserId = "sms",
                        endpointIds = listOf(firstEndpointId),  // ✅ FAN-OUT: Start with 1 endpoint
                        iconColor = 0xFF00D980.toInt(), // Green
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    // Update existing - preserve endpointIds
                    source.copy(
                        id = "sms:$number",  // Allow changing number
                        name = name,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                
                if (!newSource.isValid()) {
                    Toast.makeText(
                        this,
                        "⚠️ Source validation failed: must have at least one endpoint",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                
                // Save filter settings to extras (since Source model doesn't have them yet)
                getSharedPreferences("source_extras", MODE_PRIVATE).edit()
                    .putString("${newSource.id}:filterText", filter)
                    .putBoolean("${newSource.id}:isCaseSensitive", caseSensitive)
                    .putString("${newSource.id}:phoneNumber", number)
                    .apply()
                
                sourceManager.saveSource(newSource)
                loadSources()
                
                Toast.makeText(this, "SMS Source saved", Toast.LENGTH_SHORT).show()
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
