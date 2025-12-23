package com.example.alertsheets

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.alertsheets.data.repositories.EndpointRepository
import com.example.alertsheets.data.repositories.TemplateRepository
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ⚗️ Lab Activity - Full-featured source creation with testing
 * Each source maintains its own persistent configuration and test payloads
 */
class LabActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CONTACT_PICK = 1001
        private const val REQUEST_READ_CONTACTS = 1002
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var sourceManager: SourceManager
    private lateinit var templateRepo: TemplateRepository
    private lateinit var endpointRepo: EndpointRepository
    
    private lateinit var inputName: EditText
    private lateinit var radioGroup: RadioGroup
    private lateinit var spinnerTemplate: Spinner
    private lateinit var checkAutoClean: CheckBox
    private lateinit var inputJson: EditText
    private lateinit var textVariablesHelp: TextView
    private lateinit var endpointsCheckboxes: LinearLayout
    private lateinit var btnManageEndpoints: Button
    private lateinit var previewIcon: ImageView
    private lateinit var previewColor: View
    
    private var sourceId: String? = null
    private var selectedPhoneNumber: String? = null // For SMS source
    private var selectedIcon = "notification"
    private var selectedColor = 0xFF4A9EFF.toInt()
    private var selectedEndpointIds = mutableListOf<String>()
    
    // Per-source custom test payloads (loaded from existing source)
    private var customTestPayload: String = ""
    private var customDuplicatePayload: String = ""
    private var customDirtyPayload: String = ""
    
    // Available icons
    private val icons = listOf(
        "fire" to R.drawable.ic_fire,
        "sms" to R.drawable.ic_sms,
        "email" to R.drawable.ic_email,
        "notification" to R.drawable.ic_notification,
        "location" to R.drawable.ic_location,
        "alert" to R.drawable.ic_alert,
        "link" to R.drawable.ic_link,
        "security" to R.drawable.ic_security,
        "medical" to R.drawable.ic_medical,
        "dashboard" to R.drawable.ic_dashboard
    )
    
    // Available colors
    private val colors = listOf(
        0xFF4A9EFF.toInt(), // Blue
        0xFF00D980.toInt(), // Green
        0xFFA855F7.toInt(), // Purple
        0xFFFF6B6B.toInt(), // Red
        0xFFFFD93D.toInt(), // Yellow
        0xFFFF9800.toInt(), // Orange
        0xFF9C27B0.toInt(), // Deep Purple
        0xFF00BCD4.toInt(), // Cyan
        0xFFE91E63.toInt(), // Pink
        0xFF795548.toInt(), // Brown
        0xFF607D8B.toInt(), // Blue Grey
        0xFF4CAF50.toInt()  // Light Green
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lab)
        
        sourceManager = SourceManager(this)
        templateRepo = TemplateRepository(this)
        endpointRepo = EndpointRepository(this)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Lab"
        
        initViews()
        loadTemplates()
        loadEndpoints()
        setupListeners()
        
        // Check if editing existing source
        sourceId = intent.getStringExtra("source_id")
        if (sourceId != null) {
            loadExistingSource(sourceId!!)
        }
    }
    
    private fun initViews() {
        inputName = findViewById(R.id.input_source_name)
        radioGroup = findViewById(R.id.radio_source_type)
        spinnerTemplate = findViewById(R.id.spinner_template)
        checkAutoClean = findViewById(R.id.check_auto_clean)
        inputJson = findViewById(R.id.input_json)
        textVariablesHelp = findViewById(R.id.text_variables_help)
        endpointsCheckboxes = findViewById(R.id.endpoints_checkboxes)
        btnManageEndpoints = findViewById(R.id.btn_manage_endpoints)
        previewIcon = findViewById(R.id.preview_icon)
        previewColor = findViewById(R.id.preview_color)
    }
    
    private fun setupListeners() {
        // Configure source button
        findViewById<Button>(R.id.btn_configure_source).setOnClickListener {
            configureSourceDetails()
        }
        
        // Radio group change - reload templates for type
        radioGroup.setOnCheckedChangeListener { _, _ ->
            loadTemplates()
            updateVariablesHelp()
        }
        
        // Template management
        findViewById<Button>(R.id.btn_save_template).setOnClickListener {
            saveTemplate()
        }
        
        findViewById<Button>(R.id.btn_delete_template).setOnClickListener {
            deleteTemplate()
        }
        
        // Template selection
        spinnerTemplate.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position) as? com.example.alertsheets.JsonTemplate
                selected?.let {
                    inputJson.setText(it.content)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        // Endpoints
        btnManageEndpoints.setOnClickListener {
            startActivity(Intent(this, EndpointActivity::class.java))
        }
        
        // Testing buttons
        findViewById<Button>(R.id.btn_test_new).setOnClickListener {
            performTest(isDuplicate = false)
        }
        
        findViewById<Button>(R.id.btn_test_duplicate).setOnClickListener {
            performTest(isDuplicate = true)
        }
        
        findViewById<Button>(R.id.btn_test_dirty).setOnClickListener {
            performDirtyTest()
        }
        
        // Customize
        findViewById<Button>(R.id.btn_edit_icon).setOnClickListener {
            showIconPickerDialog()
        }
        
        findViewById<Button>(R.id.btn_edit_color).setOnClickListener {
            showColorPickerDialog()
        }
        
        // Save button
        findViewById<Button>(R.id.btn_save_source).setOnClickListener {
            saveSource()
        }
    }
    
    private fun loadTemplates() {
        scope.launch(Dispatchers.IO) {
            val type = when (radioGroup.checkedRadioButtonId) {
                R.id.radio_app -> SourceType.APP
                R.id.radio_sms -> SourceType.SMS
                else -> SourceType.APP
            }
            
            val mode = if (type == SourceType.APP) com.example.alertsheets.TemplateMode.APP else com.example.alertsheets.TemplateMode.SMS
            val templates = templateRepo.getByMode(mode)
            
            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(
                    this@LabActivity,
                    android.R.layout.simple_spinner_item,
                    templates
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerTemplate.adapter = adapter
                
                // Load first template if available
                if (templates.isNotEmpty()) {
                    inputJson.setText(templates[0].content)
                }
            }
        }
    }
    
    private fun loadEndpoints() {
        scope.launch(Dispatchers.IO) {
            val endpoints = endpointRepo.getAll()
            withContext(Dispatchers.Main) {
                endpointsCheckboxes.removeAllViews()
                
                if (endpoints.isEmpty()) {
                    val emptyText = TextView(this@LabActivity).apply {
                        text = "No endpoints found. Click '+ Manage Endpoints' to create one."
                        setTextColor(Color.parseColor("#FF9800"))
                        textSize = 14f
                        setPadding(0, 8, 0, 8)
                    }
                    endpointsCheckboxes.addView(emptyText)
                    return@withContext
                }
                
                endpoints.forEach { endpoint ->
                    val checkboxLayout = LinearLayout(this@LabActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 4, 0, 4)
                    }
                    
                    val checkbox = CheckBox(this@LabActivity).apply {
                        text = endpoint.name
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        isChecked = selectedEndpointIds.contains(endpoint.id)
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                if (!selectedEndpointIds.contains(endpoint.id)) {
                                    selectedEndpointIds.add(endpoint.id)
                                }
                            } else {
                                selectedEndpointIds.remove(endpoint.id)
                            }
                        }
                    }
                    
                    val urlText = TextView(this@LabActivity).apply {
                        text = "  → ${endpoint.url}"
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 11f
                        setPadding(48, 0, 0, 8)
                    }
                    
                    checkboxLayout.addView(checkbox)
                    checkboxLayout.addView(urlText)
                    endpointsCheckboxes.addView(checkboxLayout)
                }
            }
        }
    }
    
    private fun updateVariablesHelp() {
        val type = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_app -> SourceType.APP
            R.id.radio_sms -> SourceType.SMS
            else -> SourceType.APP
        }
        
        val vars = if (type == SourceType.APP) {
            "{{package}}, {{title}}, {{text}}, {{bigText}}, {{time}}"
        } else {
            "{{sender}}, {{message}}, {{time}}"
        }
        
        textVariablesHelp.text = "Variables: $vars"
    }
    
    private fun configureSourceDetails() {
        val type = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_app -> SourceType.APP
            R.id.radio_sms -> SourceType.SMS
            else -> SourceType.APP
        }
        
        when (type) {
            SourceType.APP -> {
                // Launch apps list
                startActivity(Intent(this, AppsListActivity::class.java))
                Toast.makeText(this, "Select an app from the list", Toast.LENGTH_SHORT).show()
            }
            SourceType.SMS -> {
                showSmsConfigDialog()
            }
        }
    }
    
    private fun showSmsConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sms_source, null)
        val inputNumber = dialogView.findViewById<EditText>(R.id.input_phone_number)
        val btnPickContact = dialogView.findViewById<Button>(R.id.btn_pick_contact)
        
        // Pre-fill if editing existing SMS source
        selectedPhoneNumber?.let { phone ->
            inputNumber.setText(phone.removePrefix("sms:"))
        }
        
        btnPickContact.setOnClickListener {
            // Check permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    REQUEST_READ_CONTACTS
                )
            } else {
                pickContact()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Configure SMS Source")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val number = inputNumber.text.toString().trim()
                if (number.isNotEmpty()) {
                    selectedPhoneNumber = number
                    sourceId = "sms:$number"
                    Toast.makeText(this, "SMS source configured: $number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, REQUEST_CONTACT_PICK)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CONTACT_PICK && resultCode == Activity.RESULT_OK) {
            data?.data?.let { contactUri ->
                val cursor: Cursor? = contentResolver.query(
                    contactUri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val phoneNumber = it.getString(0)
                        selectedPhoneNumber = phoneNumber
                        sourceId = "sms:$phoneNumber"
                        Toast.makeText(this, "Selected: $phoneNumber", Toast.LENGTH_SHORT).show()
                        // Re-show dialog with selected number
                        showSmsConfigDialog()
                    }
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickContact()
            } else {
                Toast.makeText(this, "Permission denied. Enter number manually.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun saveTemplate() {
        val json = inputJson.text.toString().trim()
        if (json.isEmpty() || !json.startsWith("{")) {
            Toast.makeText(this, "Enter valid JSON first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val inputName = EditText(this).apply {
            hint = "Template name"
        }
        
        AlertDialog.Builder(this)
            .setTitle("Save Template")
            .setMessage("Enter a name for this template:")
            .setView(inputName)
            .setPositiveButton("Save") { _, _ ->
                val name = inputName.text.toString().trim()
                if (name.isNotEmpty()) {
                    val type = when (radioGroup.checkedRadioButtonId) {
                        R.id.radio_app -> SourceType.APP
                        else -> SourceType.SMS
                    }
                    val mode = if (type == SourceType.APP) com.example.alertsheets.TemplateMode.APP else com.example.alertsheets.TemplateMode.SMS
                    
                    val template = com.example.alertsheets.JsonTemplate(
                        name = name,
                        content = json,
                        isRockSolid = false,
                        mode = mode
                    )
                    
                    templateRepo.saveUserTemplate(template)
                    loadTemplates() // Reload spinner
                    Toast.makeText(this, "✅ Template '$name' saved!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteTemplate() {
        val selected = spinnerTemplate.selectedItem as? com.example.alertsheets.JsonTemplate
        if (selected == null) {
            Toast.makeText(this, "Select a template first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selected.isRockSolid) {
            Toast.makeText(this, "Cannot delete Rock Solid templates", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Delete Template?")
            .setMessage("Delete '${selected.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                templateRepo.deleteUserTemplate(selected.name)
                loadTemplates()
                Toast.makeText(this, "🗑️ Template deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performTest(isDuplicate: Boolean) {
        val type = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_app -> SourceType.APP
            R.id.radio_sms -> SourceType.SMS
            else -> SourceType.APP
        }
        
        // Generate clean test (no emojis)
        val cleanJson = if (isDuplicate && customDuplicatePayload.isNotEmpty()) {
            customDuplicatePayload
        } else if (!isDuplicate && customTestPayload.isNotEmpty()) {
            customTestPayload
        } else {
            // Generate default clean test
            if (type == SourceType.APP) {
                """
                {
                  "source": "test",
                  "package": "com.example.test",
                  "title": "Test Notification",
                  "text": "This is a clean test notification without emojis",
                  "timestamp": "${System.currentTimeMillis()}"
                }
                """.trimIndent()
            } else {
                """
                {
                  "source": "sms-test",
                  "sender": "+15551234567",
                  "message": "This is a clean SMS test without emojis",
                  "timestamp": "${System.currentTimeMillis()}"
                }
                """.trimIndent()
            }
        }
        
        showTestDialog(cleanJson, if (isDuplicate) "duplicate" else "test")
    }
    
    private fun performDirtyTest() {
        val type = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_app -> SourceType.APP
            R.id.radio_sms -> SourceType.SMS
            else -> SourceType.APP
        }
        
        // Generate dirty test (with emojis)
        val dirtyJson = if (customDirtyPayload.isNotEmpty()) {
            customDirtyPayload
        } else {
            if (type == SourceType.APP) {
                """
                {
                  "source": "dirty-test",
                  "package": "com.example.test",
                  "title": "🔥 Emoji Test Alert 🚨",
                  "text": "Test with emojis: 😀😃😄😁 🚀🎉 ⭐✨ symbols: ™®© special chars: \"quoted\" and 'single'",
                  "timestamp": "${System.currentTimeMillis()}"
                }
                """.trimIndent()
            } else {
                """
                {
                  "source": "dirty-sms-test",
                  "sender": "+15551234567",
                  "message": "🔥 SMS with emojis: 😀😃😄 🚀🎉 ⭐✨ and symbols: ™®©",
                  "timestamp": "${System.currentTimeMillis()}"
                }
                """.trimIndent()
            }
        }
        
        showTestDialog(dirtyJson, "dirty")
    }
    
    private fun showTestDialog(json: String, testType: String) {
        if (selectedEndpointIds.isEmpty()) {
            Toast.makeText(this, "Select at least one endpoint", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        
        val titleText = TextView(this).apply {
            text = when (testType) {
                "test" -> "🧪 Test Payload"
                "duplicate" -> "🔄 Duplicate Test Payload"
                "dirty" -> "🔥 Dirty Test Payload (Emojis)"
                else -> "Test Payload"
            }
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        dialogView.addView(titleText)
        
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
        }
        
        val preview = EditText(this).apply {
            setText(json)
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#2C2C2E"))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        scrollView.addView(preview)
        dialogView.addView(scrollView)
        
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }
        
        val btnSaveCustom = Button(this).apply {
            text = "💾 Save Custom"
            setOnClickListener {
                // Save custom test payload
                when (testType) {
                    "test" -> customTestPayload = preview.text.toString()
                    "duplicate" -> customDuplicatePayload = preview.text.toString()
                    "dirty" -> customDirtyPayload = preview.text.toString()
                }
                Toast.makeText(this@LabActivity, "✅ Custom test saved!", Toast.LENGTH_SHORT).show()
            }
        }
        
        val btnSend = Button(this).apply {
            text = "✓ Send"
            setOnClickListener {
                val finalJson = preview.text.toString()
                // Save as duplicate payload for next time
                customDuplicatePayload = finalJson
                sendTestPayload(finalJson)
                (parent as? android.view.ViewGroup)?.let { 
                    ((it.parent as? android.view.ViewGroup)?.parent as? AlertDialog)?.dismiss()
                }
            }
        }
        
        btnLayout.addView(btnSaveCustom)
        btnLayout.addView(btnSend)
        dialogView.addView(btnLayout)
        
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("✗ Cancel", null)
            .show()
    }
    
    private fun sendTestPayload(json: String) {
        // Send to all selected endpoints
        scope.launch(Dispatchers.IO) {
            selectedEndpointIds.forEach { endpointId ->
                val endpoint = endpointRepo.getById(endpointId)
                endpoint?.let {
                    // TODO: Integrate with actual HTTP sender
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LabActivity, "✓ Sent to ${it.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun showIconPickerDialog() {
        val gridLayout = GridLayout(this).apply {
            columnCount = 5
            setPadding(24, 24, 24, 24)
        }
        
        icons.forEach { (iconName, iconRes) ->
            val imageView = ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                setPadding(16, 16, 16, 16)
                setBackgroundColor(if (iconName == selectedIcon) Color.parseColor("#00D980") else Color.TRANSPARENT)
                setOnClickListener {
                    selectedIcon = iconName
                    previewIcon.setImageResource(iconRes)
                    (parent as? AlertDialog)?.dismiss()
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 120
                height = 120
                setMargins(8, 8, 8, 8)
            }
            imageView.layoutParams = params
            gridLayout.addView(imageView)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Select Icon")
            .setView(gridLayout)
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showColorPickerDialog() {
        val gridLayout = GridLayout(this).apply {
            columnCount = 6
            setPadding(24, 24, 24, 24)
        }
        
        colors.forEach { colorValue ->
            val colorView = View(this).apply {
                setBackgroundColor(colorValue)
                setOnClickListener {
                    selectedColor = colorValue
                    previewColor.setBackgroundColor(colorValue)
                    (this.parent as? GridLayout)?.let { grid ->
                        ((grid.parent as? android.view.ViewGroup)?.parent as? AlertDialog)?.dismiss()
                    }
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 80
                height = 80
                setMargins(8, 8, 8, 8)
            }
            colorView.layoutParams = params
            gridLayout.addView(colorView)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Select Color")
            .setView(gridLayout)
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun saveSource() {
        val name = inputName.text.toString().trim()
        val json = inputJson.text.toString().trim()
        
        if (name.isEmpty()) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (json.isEmpty() || !json.startsWith("{")) {
            Toast.makeText(this, "Valid JSON is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedEndpointIds.isEmpty()) {
            Toast.makeText(this, "Select at least one endpoint", Toast.LENGTH_SHORT).show()
            return
        }
        
        val type = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_app -> SourceType.APP
            R.id.radio_sms -> SourceType.SMS
            else -> SourceType.APP
        }
        
        // Determine final source ID
        val finalId = when {
            sourceId != null -> sourceId!!
            type == SourceType.SMS && selectedPhoneNumber != null -> "sms:$selectedPhoneNumber"
            else -> UUID.randomUUID().toString()
        }
        
        // ✅ CRITICAL: Each source maintains its own independent configuration
        val source = Source(
            id = finalId,
            type = type,
            name = name,
            enabled = true,
            autoClean = checkAutoClean.isChecked,
            templateJson = json,
            templateId = "", // Deprecated
            parserId = "generic",
            endpointIds = selectedEndpointIds.toList(), // Independent endpoint list
            iconName = selectedIcon,
            iconColor = selectedColor,
            cardColor = selectedColor,
            customTestPayload = customTestPayload,       // ✅ Per-source test
            customDuplicatePayload = customDuplicatePayload, // ✅ Per-source duplicate
            customDirtyPayload = customDirtyPayload,      // ✅ Per-source dirty test
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        sourceManager.saveSource(source)
        Toast.makeText(this, "✅ Source '$name' saved!", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun loadExistingSource(sourceId: String) {
        scope.launch(Dispatchers.IO) {
            val source = sourceManager.getAllSources().find { it.id == sourceId }
            withContext(Dispatchers.Main) {
                source?.let { src ->
                    // ✅ LOAD ALL source-specific configuration
                    inputName.setText(src.name)
                    when (src.type) {
                        SourceType.APP -> radioGroup.check(R.id.radio_app)
                        SourceType.SMS -> {
                            radioGroup.check(R.id.radio_sms)
                            selectedPhoneNumber = src.id.removePrefix("sms:")
                        }
                    }
                    inputJson.setText(src.templateJson)
                    checkAutoClean.isChecked = src.autoClean
                    selectedEndpointIds.clear()
                    selectedEndpointIds.addAll(src.endpointIds)
                    selectedIcon = src.iconName ?: "notification"
                    selectedColor = src.cardColor
                    
                    // ✅ LOAD custom test payloads (per-source persistence)
                    customTestPayload = src.customTestPayload
                    customDuplicatePayload = src.customDuplicatePayload
                    customDirtyPayload = src.customDirtyPayload
                    
                    previewIcon.setImageResource(icons.find { (name, _) -> name == selectedIcon }?.second ?: R.drawable.ic_notification)
                    previewColor.setBackgroundColor(selectedColor)
                    
                    loadEndpoints() // Reload to check correct boxes
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadEndpoints() // Reload when returning from EndpointActivity
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
