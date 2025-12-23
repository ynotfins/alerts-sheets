package com.example.alertsheets

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
 * ⚗️ Lab Activity - All-in-one source creation
 * 
 * Everything needed to create a complete source in ONE page:
 * 1. Name it
 * 2. Pick type (App/SMS/Email)
 * 3. Edit JSON payload
 * 4. Select endpoint
 * 5. Customize icon + color
 * → Creates a card on home screen
 */
class LabActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var sourceManager: SourceManager
    private lateinit var templateRepo: TemplateRepository
    private lateinit var endpointRepo: EndpointRepository
    
    private lateinit var inputName: EditText
    private lateinit var radioGroup: RadioGroup
    private lateinit var inputJson: EditText
    private lateinit var spinnerEndpoint: Spinner
    private lateinit var btnManageEndpoints: Button
    private lateinit var iconGrid: GridLayout
    private lateinit var colorGrid: GridLayout
    
    private var selectedIcon = "notification"
    private var selectedColor = 0xFF4A9EFF.toInt()
    private var sourceId: String? = null  // For edit mode
    
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
        loadEndpoints()
        setupIconGrid()
        setupColorGrid()
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
        inputJson = findViewById(R.id.input_json)
        spinnerEndpoint = findViewById(R.id.spinner_endpoint)
        btnManageEndpoints = findViewById(R.id.btn_manage_endpoints)
        iconGrid = findViewById(R.id.icon_grid)
        colorGrid = findViewById(R.id.color_grid)
    }
    
    private fun setupListeners() {
        // Configure source button
        findViewById<Button>(R.id.btn_configure_source).setOnClickListener {
            configureSourceDetails()
        }
        
        // Radio group change
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            updateTemplateForType()
        }
        
        // Save button
        findViewById<Button>(R.id.btn_save_source).setOnClickListener {
            saveSource()
        }
        
        // Manage Endpoints button
        btnManageEndpoints.setOnClickListener {
            startActivity(Intent(this, EndpointActivity::class.java))
        }
    }
    
    private fun updateTemplateForType() {
        val type = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_app -> SourceType.APP
            R.id.radio_sms -> SourceType.SMS
            else -> SourceType.APP
        }
        
        // Load default template for this type
        val defaultJson = templateRepo.getDefaultJsonForNewSource(type)
        if (inputJson.text.toString().isEmpty() || inputJson.text.toString() == "{}") {
            inputJson.setText(defaultJson)
        }
    }
    
    private fun configureSourceDetails() {
        val type = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_app -> SourceType.APP
            R.id.radio_sms -> SourceType.SMS
            else -> SourceType.APP
        }
        
        when (type) {
            SourceType.APP -> {
                // Show app selector
                val intent = Intent(this, AppsListActivity::class.java)
                startActivity(intent)
            }
            SourceType.SMS -> {
                // Show SMS config dialog
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
                    // Generate sourceId for SMS
                    sourceId = "sms:$number"
                    Toast.makeText(this, "SMS source configured: $number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun loadEndpoints() {
        scope.launch(Dispatchers.IO) {
            val endpoints = endpointRepo.getAll()
            withContext(Dispatchers.Main) {
                if (endpoints.isEmpty()) {
                    Toast.makeText(this@LabActivity, 
                        "⚠️ No endpoints found. Click '+ Manage' to create one.", 
                        Toast.LENGTH_LONG).show()
                    // Don't finish - let user create endpoint
                    return@withContext
                }
                
                val endpointNames = endpoints.map { it.name }
                val adapter = ArrayAdapter(this@LabActivity, android.R.layout.simple_spinner_item, endpointNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerEndpoint.adapter = adapter
            }
        }
    }
    
    private fun setupIconGrid() {
        icons.forEach { (iconName, iconRes) ->
            val imageView = ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                setPadding(16, 16, 16, 16)
                setBackgroundColor(if (iconName == selectedIcon) Color.parseColor("#00D980") else Color.TRANSPARENT)
                setOnClickListener {
                    selectedIcon = iconName
                    refreshIconGrid()
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            }
            imageView.layoutParams = params
            iconGrid.addView(imageView)
        }
    }
    
    private fun refreshIconGrid() {
        for (i in 0 until iconGrid.childCount) {
            val view = iconGrid.getChildAt(i) as ImageView
            val iconName = icons[i].first
            view.setBackgroundColor(if (iconName == selectedIcon) Color.parseColor("#00D980") else Color.TRANSPARENT)
        }
    }
    
    private fun setupColorGrid() {
        colors.forEach { color ->
            val view = CardView(this).apply {
                setCardBackgroundColor(color)
                radius = 8f
                elevation = if (color == selectedColor) 8f else 2f
                setOnClickListener {
                    selectedColor = color
                    refreshColorGrid()
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = 80
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            }
            view.layoutParams = params
            colorGrid.addView(view)
        }
    }
    
    private fun refreshColorGrid() {
        for (i in 0 until colorGrid.childCount) {
            val view = colorGrid.getChildAt(i) as CardView
            val color = colors[i]
            view.elevation = if (color == selectedColor) 8f else 2f
        }
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
        
        val type = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_app -> SourceType.APP
            R.id.radio_sms -> SourceType.SMS
            else -> SourceType.APP
        }
        
        // Generate ID if not set
        val finalId = sourceId ?: UUID.randomUUID().toString()
        
        scope.launch(Dispatchers.IO) {
            val endpoints = endpointRepo.getAll()
            val selectedEndpoint = endpoints[spinnerEndpoint.selectedItemPosition]
            
            val source = Source(
                id = finalId,
                type = type,
                name = name,
                enabled = true,
                templateJson = json,
                templateId = "",
                autoClean = type == SourceType.SMS,
                parserId = if (type == SourceType.SMS) "sms" else "generic",
                endpointIds = listOf(selectedEndpoint.id),  // ✅ FAN-OUT: Use list
                iconColor = selectedColor,
                iconName = selectedIcon,
                cardColor = selectedColor,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            sourceManager.saveSource(source)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@LabActivity, "✅ Source created: $name", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun loadExistingSource(id: String) {
        scope.launch(Dispatchers.IO) {
            val source = sourceManager.getSource(id)
            withContext(Dispatchers.Main) {
                source?.let {
                    inputName.setText(it.name)
                    inputJson.setText(it.templateJson)
                    selectedIcon = it.iconName
                    selectedColor = it.cardColor
                    
                    when (it.type) {
                        SourceType.APP -> radioGroup.check(R.id.radio_app)
                        SourceType.SMS -> radioGroup.check(R.id.radio_sms)
                    }
                    
                    refreshIconGrid()
                    refreshColorGrid()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reload endpoints when returning from EndpointActivity
        loadEndpoints()
    }
}

