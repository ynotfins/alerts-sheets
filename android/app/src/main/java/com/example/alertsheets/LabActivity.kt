package com.example.alertsheets

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
 */
class LabActivity : AppCompatActivity() {

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
    private var selectedIcon = "notification"
    private var selectedColor = 0xFF4A9EFF.toInt()
    private var selectedEndpointIds = mutableListOf<String>()
    private var lastTestJson: String? = null // For duplicate test
    
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_source, null)
        val inputNumber = dialogView.findViewById<EditText>(R.id.input_new_source_id)
        inputNumber.hint = "Phone number (e.g., +1234567890)"
        
        AlertDialog.Builder(this)
            .setTitle("Configure SMS Source")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val number = inputNumber.text.toString().trim()
                if (number.isNotEmpty()) {
                    sourceId = "sms:$number"
                    Toast.makeText(this, "SMS source configured: $number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        val json = if (isDuplicate && lastTestJson != null) {
            lastTestJson!!
        } else {
            inputJson.text.toString().trim()
        }
        
        if (json.isEmpty() || !json.startsWith("{")) {
            Toast.makeText(this, "Enter valid JSON first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedEndpointIds.isEmpty()) {
            Toast.makeText(this, "Select at least one endpoint", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show preview dialog
        val preview = TextView(this).apply {
            text = json
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#2C2C2E"))
        }
        
        val scrollView = ScrollView(this).apply {
            addView(preview)
        }
        
        AlertDialog.Builder(this)
            .setTitle("📋 JSON Payload Preview")
            .setView(scrollView)
            .setPositiveButton("✓ Send") { _, _ ->
                lastTestJson = json // Save for duplicate test
                sendTestPayload(json)
            }
            .setNegativeButton("✗ Cancel", null)
            .show()
    }
    
    private fun performDirtyTest() {
        val dirtyJson = """
        {
          "source": "dirty-test",
          "message": "🔥 Emoji test: 😀😃😄😁 🚀🎉 ⭐✨ with symbols: ™®© special chars: \"quoted\" and 'single'",
          "timestamp": "${System.currentTimeMillis()}"
        }
        """.trimIndent()
        
        inputJson.setText(dirtyJson)
        Toast.makeText(this, "🔥 Dirty test loaded! Review and send.", Toast.LENGTH_SHORT).show()
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
        
        val id = sourceId ?: UUID.randomUUID().toString()
        
        val source = Source(
            id = id,
            type = type,
            name = name,
            enabled = true,
            autoClean = checkAutoClean.isChecked,
            templateJson = json,
            templateId = "", // Deprecated
            parserId = "generic",
            endpointIds = selectedEndpointIds.toList(),
            iconName = selectedIcon,
            iconColor = selectedColor,
            cardColor = selectedColor,
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
                    inputName.setText(src.name)
                    when (src.type) {
                        SourceType.APP -> radioGroup.check(R.id.radio_app)
                        SourceType.SMS -> radioGroup.check(R.id.radio_sms)
                    }
                    inputJson.setText(src.templateJson)
                    checkAutoClean.isChecked = src.autoClean
                    selectedEndpointIds.clear()
                    selectedEndpointIds.addAll(src.endpointIds)
                    selectedIcon = src.iconName ?: "notification"
                    selectedColor = src.cardColor
                    
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
