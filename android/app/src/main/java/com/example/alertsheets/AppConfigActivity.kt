package com.example.alertsheets

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.alertsheets.data.repositories.TemplateRepository
import com.example.alertsheets.utils.TemplateEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AppConfigActivity : AppCompatActivity() {

    private lateinit var editJson: EditText
    private lateinit var spinnerSource: Spinner  // ✅ NEW: Source selector
    private lateinit var spinnerTemplate: Spinner
    private lateinit var btnSave: Button
    private lateinit var radioGroupMode: RadioGroup
    private lateinit var templateRepository: TemplateRepository
    private lateinit var sourceManager: com.example.alertsheets.domain.SourceManager  // ✅ NEW
    
    private var currentSource: com.example.alertsheets.domain.models.Source? = null  // ✅ NEW: Currently selected source

    // Available Variables for User Reference
    private val appVariables =
            listOf(
                    "{{package}}",
                    "{{title}}",
                    "{{text}}",
                    "{{bigText}}",
                    "{{time}}",
                    "{{sender}}",
                    "{{message}}" // Combined list for legend
            )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ V2: Initialize repositories
        templateRepository = TemplateRepository(this)
        sourceManager = com.example.alertsheets.domain.SourceManager(this)
        
        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        val layout =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 32, 32, 32)
                }

        // Header
        val title =
                TextView(this).apply {
                    text = "JSON Payload Editor"
                    textSize = 20f
                    setPadding(0, 0, 0, 24)
                }
        layout.addView(title)

        // ✅ NEW: Source selector (PRIMARY workflow)
        val labelSource =
                TextView(this).apply {
                    text = "Select Source to Edit:"
                    setPadding(0, 0, 0, 8)
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#FF6200EE"))
                }
        layout.addView(labelSource)
        
        spinnerSource = Spinner(this)
        loadSources() // ✅ Load all sources into spinner
        layout.addView(spinnerSource)
        
        val divider1 = TextView(this).apply {
            text = "━━━━━━━━━━━━━━━━━━━━━━━━━"
            textSize = 12f
            setPadding(0, 16, 0, 16)
            setTextColor(android.graphics.Color.GRAY)
        }
        layout.addView(divider1)

        // Template Management Buttons
        val templateButtonLayout =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 16)
                }

        val btnSaveNewTemplate =
                Button(this).apply {
                    text = "+ Save as New"
                    background.setTint(android.graphics.Color.parseColor("#4CAF50"))
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener { showSaveTemplateDialog() }
                    layoutParams =
                            LinearLayout.LayoutParams(
                                            0,
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            1f
                                    )
                }
        templateButtonLayout.addView(btnSaveNewTemplate)

        val btnDeleteTemplate =
                Button(this).apply {
                    text = "🗑️ Delete"
                    background.setTint(android.graphics.Color.parseColor("#F44336"))
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener { deleteCurrentTemplate() }
                    layoutParams =
                            LinearLayout.LayoutParams(
                                            0,
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            1f
                                    )
                                    .apply { setMargins(16, 0, 0, 0) }
                }
        templateButtonLayout.addView(btnDeleteTemplate)
        layout.addView(templateButtonLayout)

        // Clean Data Option
        val checkClean =
                android.widget.CheckBox(this).apply {
                    text = "Auto-Clean Emojis/Symbols (Global)"
                    isChecked = PrefsManager.getShouldCleanData(this@AppConfigActivity)
                    setOnCheckedChangeListener { _, isChecked ->
                        PrefsManager.saveShouldCleanData(this@AppConfigActivity, isChecked)
                    }
                    setPadding(0, 16, 0, 16)
                }
        layout.addView(checkClean)

        // JSON Editor
        val labelJson =
                TextView(this).apply {
                    text = "JSON Payload (Editable):"
                    setPadding(0, 24, 0, 8)
                }
        layout.addView(labelJson)

        editJson =
                EditText(this).apply {
                    hint = "{\n  \"key\": \"{{val}}\"\n}"
                    setLines(12)
                    gravity = android.view.Gravity.TOP
                    background =
                            android.graphics.drawable.GradientDrawable().apply {
                                setStroke(2, android.graphics.Color.GRAY)
                                cornerRadius = 8f
                            }
                    setPadding(16, 16, 16, 16)
                }
        layout.addView(editJson)

        // Helper Chips / Legend
        val labelHelp =
                TextView(this).apply {
                    text = "Variables: ${appVariables.joinToString(", ")}"
                    textSize = 12f
                    setPadding(0, 8, 0, 8)
                    setTextColor(android.graphics.Color.DKGRAY)
                }
        layout.addView(labelHelp)

        // Clean Valid Checkbox
        val btnClean =
                Button(this).apply {
                    text = "Clean Invalid Chars (Test)"
                    setOnClickListener {
                        val current = editJson.text.toString()
                        val cleaned = TemplateEngine.cleanText(current)
                        editJson.setText(cleaned as CharSequence)
                        Toast.makeText(
                                        this@AppConfigActivity,
                                        "Removed Emojis/Symbols",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
        layout.addView(btnClean)

        // Save Button (Keep simple - just saves to prefs for legacy compat)
        btnSave =
                Button(this).apply {
                    text = "Apply Changes"
                    setOnClickListener { save() }
                }
        layout.addView(btnSave)

        // TEST SECTION
        val testLayout =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL  // ✅ Changed to vertical for better layout
                    setPadding(0, 24, 0, 0)
                }
        
        // First row: Test buttons
        val testRowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnTest =
                Button(this).apply {
                    text = "Test New Notification"
                    background.setTint(android.graphics.Color.parseColor("#4CAF50"))
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener { performTest(isUpdate = false) }
                }

        val btnTestUpdate =
                Button(this).apply {
                    text = "Test - Same as Previous"
                    background.setTint(android.graphics.Color.parseColor("#FF9800"))
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener { performTest(isUpdate = true) }
                    // Add margin
                    layoutParams =
                            LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    .apply { setMargins(16, 0, 0, 0) }
                }

        testRowLayout.addView(btnTest)
        testRowLayout.addView(btnTestUpdate)
        
        // ✅ NEW: Dirty Test button (emoji-rich SMS)
        val btnDirtyTest =
                Button(this).apply {
                    text = "🔥 Dirty Test (Emoji SMS)"
                    background.setTint(android.graphics.Color.parseColor("#E91E63"))  // Pink
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener { performDirtyTest() }
                    layoutParams =
                            LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    .apply { setMargins(0, 12, 0, 0) }
                }

        val checkAutoTest =
                android.widget.CheckBox(this).apply {
                    text = "Auto-Test on Exit"
                    isChecked = true // Default true as per user request
                    // ID for state saving if needed, logic will just check isChecked
                    tag = "auto_test"
                }

        testLayout.addView(testRowLayout)
        testLayout.addView(btnDirtyTest)  // ✅ Add dirty test button
        layout.addView(testLayout)
        layout.addView(checkAutoTest) // Move checkbox below buttons

        // Logic
        
        // ✅ FIX: Prevent race condition by setting listener AFTER initial load
        var isInitializing = true

        // Load initial - restore last mode
        val lastMode = PrefsManager.getLastConfigMode(this) // "APP" or "SMS"
        val isAppMode = (lastMode == "APP")
        
        if (isAppMode) {
            radioGroupMode.check(R.id.radio_app)
        } else {
            radioGroupMode.check(R.id.radio_sms)
        }
        
        loadConfig(isAppMode)
        
        // ✅ FIX: Set initialization complete BEFORE setting listener
        isInitializing = false

        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            // ✅ FIX: Ignore listener events during initialization
            if (isInitializing) return@setOnCheckedChangeListener
            
            val isApp = (checkedId == R.id.radio_app)
            // Save mode preference
            PrefsManager.saveLastConfigMode(this, if (isApp) "APP" else "SMS")
            loadConfig(isApp)
            loadTemplatesForCurrentMode() // Reload templates when mode changes
        }

        // Template Selection Logic
        spinnerTemplate.onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            view: android.view.View?,
                            position: Int,
                            id: Long
                    ) {
                        val selected = parent?.getItemAtPosition(position) as? JsonTemplate
                        selected?.let {
                            editJson.setText(it.content)
                            // Save as active template
                            PrefsManager.setActiveTemplateName(this@AppConfigActivity, it.mode, it.name)
                        }
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }

        // Wrap in ScrollView for proper scrolling
        val scrollView = ScrollView(this)
        scrollView.addView(layout)
        setContentView(scrollView)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadTemplatesForCurrentMode() {
        val isApp = (radioGroupMode.checkedRadioButtonId == R.id.radio_app)
        val mode = if (isApp) TemplateMode.APP else TemplateMode.SMS
        val templates = templateRepository.getByMode(mode)
        
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            templates
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTemplate.adapter = adapter
        
        // Try to restore last selected template
        val activeTemplateName = PrefsManager.getActiveTemplateName(this, mode)
        val activeIndex = templates.indexOfFirst { it.name == activeTemplateName }
        if (activeIndex >= 0) {
            spinnerTemplate.setSelection(activeIndex)
        }
    }

    private fun loadConfig(isAppMode: Boolean) {
        if (isAppMode) {
            editJson.setText(templateRepository.getAppTemplate())
        } else {
            editJson.setText(templateRepository.getSmsTemplate())
        }
    }

    private fun save() {
        val newJson = editJson.text.toString()
        
        // ✅ NEW: Save to the currently selected source's templateJson
        val source = currentSource
        if (source == null) {
            Toast.makeText(this, "No source selected", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate JSON
        try {
            // Simple check
            if (!newJson.trim().startsWith("{")) throw Exception("Must start with {")

            // ✅ Update source's templateJson
            val updatedSource = source.copy(
                templateJson = newJson,
                updatedAt = System.currentTimeMillis()
            )
            sourceManager.saveSource(updatedSource)
            currentSource = updatedSource  // Update local reference
            
            Toast.makeText(this, "✅ Saved template for ${source.name}!", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid JSON: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Templates
    private fun getAppTemplate(): String {
        return """
{
  "source": "app",
  "package": "{{package}}",
  "title": "{{title}}",
  "text": "{{text}}",
  "bigText": "{{bigText}}",
  "time": "{{time}}",
  "timestamp": "{{timestamp}}"
}
        """.trimIndent()
    }

    private fun getSmsTemplate(): String {
        return """
{
  "source": "sms",
  "sender": "{{sender}}",
  "message": "{{message}}",
  "time": "{{time}}",
  "timestamp": "{{timestamp}}"
}
        """.trimIndent()
    }

    private fun getBnnTemplate(): String {
        return """
{
  "//": "Standard BNN Output",
  "incidentId": "{{id}}",
  "status": "{{status}}",
  "state": "{{state}}",
  "county": "{{county}}",
  "city": "{{city}}",
  "type": "{{type}}",
  "address": "{{address}}",
  "details": "{{details}}",
  "originalBody": "{{original}}",
  "codes": {{codes}},
  "timestamp": "{{timestamp}}"
}
         """.trimIndent()
    }

    override fun onPause() {
        super.onPause()

        // Auto-Test logic: Check if checkbox with tag "auto_test" is checked
        // Since we didn't store the Ref (my bad), we iterate or find
        val root =
                findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as?
                        LinearLayout
        // Need to find the checkbox. It was added to `testLayout` which was added to `layout`.
        // Let's iterate recursively or just assume default TRUE if not found (safer to assume user
        // wants test)
        // Or find view by tag
        val checkBox = root?.findViewWithTag<android.widget.CheckBox>("auto_test")

        if (checkBox != null && checkBox.isChecked) {
            performTest(isUpdate = false, silent = true)
        }
    }

    private fun performTest(isUpdate: Boolean = false, silent: Boolean = false) {
        val template = editJson.text.toString()
        val isApp = (radioGroupMode.checkedRadioButtonId == R.id.radio_app)

        // Realistic BNN-like test data with TEST marker and unique ID
        // Realistic BNN Parsing Pipeline Verification
        // Matches parsing.md spec: U/D NJ | Bergen | Teaneck | 123 Main St | Structure Fire | 2nd
        // Alarm declared <C> BNN #1844695

        val json =
                if (isApp) {
                    val incidentId: String
                    val statusPrefix: String
                    val testType: String

                    if (isUpdate) {
                        // REUSE last test ID for update
                        val lastId = PrefsManager.getLastTestId(this)
                        if (lastId.isEmpty()) {
                            runOnUiThread {
                                Toast.makeText(
                                                this,
                                                "No previous test! Send NEW incident first.",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                            return
                        }
                        incidentId = lastId
                        statusPrefix = "U/D"
                        testType = "Update"
                        android.util.Log.i("TEST", "=== TEST UPDATE ===")
                        android.util.Log.i("TEST", "Reusing ID: $incidentId")
                    } else {
                        // GENERATE new unique ID
                        val uniqueId = "1${System.currentTimeMillis().toString().takeLast(6)}"
                        incidentId = "#$uniqueId"
                        statusPrefix = "N/D"
                        testType = "New Incident"

                        // Save for next update test
                        PrefsManager.saveLastTestId(this, incidentId)

                        android.util.Log.i("TEST", "=== TEST NEW INCIDENT ===")
                        android.util.Log.i("TEST", "Generated ID: $incidentId")
                        android.util.Log.i("TEST", "Saved to prefs for update test")
                    }

                    // 1. Construct Mock BNN Message with RANDOMIZED address
                    // User Request: Always bar between incident and source. <C> BNN
                    val streetNumber = (100..999).random()
                    val streets = listOf("Test Ave", "Sample St", "Demo Road", "Trial Lane", "Mock Drive")
                    val randomAddress = "$streetNumber ${streets.random()}"
                    
                    val mockBnn =
                            "$statusPrefix NJ | Test County | Test City | $randomAddress | TEST-TYPE | Testing $testType - Columns should fill. | <C> BNN | E-1/L-1 | nj312/njn751/nyl785/pa547 | $incidentId"

                    android.util.Log.i("TEST", "Mock BNN: $mockBnn")

                    // 2. Run through REAL BnnParser
                    val bnnParser = com.example.alertsheets.domain.parsers.ParserRegistry.get("bnn")
                    val parsed = bnnParser?.parse(
                        com.example.alertsheets.domain.models.RawNotification(
                            packageName = "us.bnn.newsapp",
                            title = "BNN Alert",
                            text = mockBnn,
                            bigText = mockBnn
                        )
                    )

                    if (parsed != null) {
                        // 3. Serialize exactly like NotificationService
                        val timestamped =
                                parsed.copy(
                                        timestamp = TemplateEngine.getTimestamp(),
                                        incidentDetails = "$testType - ${parsed.incidentDetails}"
                                )
                        val jsonPayload = com.google.gson.Gson().toJson(timestamped)
                        android.util.Log.i("TEST", "Parsed successfully")
                        android.util.Log.i("TEST", "Incident ID in JSON: ${timestamped.incidentId}")
                        android.util.Log.i("TEST", "Details: ${timestamped.incidentDetails}")

                        jsonPayload
                    } else {
                        // Fallback (Should not happen with valid mock)
                        val fallbackVars = mapOf(
                            "package" to "com.test.bnn",
                            "title" to "Test Title",
                            "text" to "Test Text",
                            "bigText" to mockBnn,
                            "timestamp" to TemplateEngine.getTimestamp()
                        )
                        TemplateEngine.applyGeneric(
                                template,
                                fallbackVars,
                                false
                        )
                    }
                } else {
                    // SMS Mode - More realistic test message
                    val testSender = "+1-555-0123"
                    val testMessage = "ALERT: Fire reported at 123 Main St. Units responding. This is a test SMS message with more realistic content to verify full text appears in spreadsheet."
                    
                    val smsVars = mapOf(
                        "sender" to testSender,
                        "message" to testMessage,
                        "body" to testMessage,
                        "time" to android.text.format.DateFormat.format("MM/dd/yyyy h:mm a", System.currentTimeMillis()).toString(),
                        "timestamp" to TemplateEngine.getTimestamp()
                    )
                    val jsonPayload = TemplateEngine.applyGeneric(
                                    template,
                                    smsVars,
                                    false
                            )
                    jsonPayload
                }

        // Show JSON preview dialog (unless silent auto-test)
        if (!silent) {
            showJsonPreviewDialog(json, isUpdate, silent) {
                // User confirmed - proceed with sending
                sendTestPayload(json, isUpdate, silent)
            }
        } else {
            // Silent mode - send directly
            sendTestPayload(json, isUpdate, silent)
        }
    }

    private fun sendTestPayload(json: String, isUpdate: Boolean, silent: Boolean) {
        if (!silent) {
            val message = if (isUpdate) "Sending Update Test..." else "Sending New Test..."
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // Get URL
            val endpoints = PrefsManager.getEndpoints(this@AppConfigActivity)
            val url = endpoints.firstOrNull()?.url ?: ""

            if (url.isEmpty()) {
                android.util.Log.e("TEST", "FAILED: No endpoint configured")
                if (!silent)
                        runOnUiThread {
                            Toast.makeText(
                                            this@AppConfigActivity,
                                            "No Endpoint URL Configured!",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                PrefsManager.setPayloadTestStatus(this@AppConfigActivity, 2) // Fail
                return@launch
            }

            // Log request details
            android.util.Log.i("TEST", "=== Test Request ===")
            android.util.Log.i("TEST", "Endpoint: $url")
            android.util.Log.i("TEST", "JSON Payload: $json")

            try {
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = okhttp3.Request.Builder().url(url).post(body).build()

                val client = okhttp3.OkHttpClient()
                client.newCall(request).execute().use { response ->
                    val statusCode = response.code
                    val responseBody = response.body?.string() ?: ""

                    android.util.Log.i("TEST", "HTTP Status: $statusCode")
                    android.util.Log.i("TEST", "Response Body: $responseBody")

                    val success = response.isSuccessful

                    if (success) {
                        // Parse response to extract what was saved
                        val responseData = try {
                            val jsonResponse = org.json.JSONObject(responseBody)
                            when {
                                jsonResponse.has("id") -> "ID: ${jsonResponse.getString("id")}"
                                jsonResponse.has("sender") -> "From: ${jsonResponse.getString("sender")}"
                                else -> "Saved"
                            }
                        } catch (e: Exception) {
                            "OK"
                        }
                        
                        if (!silent)
                                runOnUiThread {
                                    // Show detailed success message with response data
                                    val message = "✓ Test SUCCESS (HTTP $statusCode)\n$responseData\n\nResponse: $responseBody"
                                    
                                    // Create custom toast with scrollable view for long responses
                                    AlertDialog.Builder(this@AppConfigActivity)
                                        .setTitle("✓ Test Successful")
                                        .setMessage("HTTP $statusCode\n\n$responseData\n\nFull Response:\n$responseBody")
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                        PrefsManager.setPayloadTestStatus(this@AppConfigActivity, 1)
                    } else {
                        android.util.Log.e("TEST", "FAILED with status $statusCode: $responseBody")
                        if (!silent)
                                runOnUiThread {
                                    Toast.makeText(
                                                    this@AppConfigActivity,
                                                    "Test FAILED: HTTP $statusCode",
                                                    Toast.LENGTH_LONG
                                            )
                                            .show()
                                }
                        PrefsManager.setPayloadTestStatus(this@AppConfigActivity, 2)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TEST", "EXCEPTION: ${e.message}", e)
                if (!silent)
                        runOnUiThread {
                            Toast.makeText(
                                            this@AppConfigActivity,
                                            "Test FAILED: ${e.message}",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                PrefsManager.setPayloadTestStatus(this@AppConfigActivity, 2)
            }
        } // End launch coroutine
    } // End sendTestPayload

    private fun showSaveTemplateDialog() {
        val template = editJson.text.toString()
        val isApp = (radioGroupMode.checkedRadioButtonId == R.id.radio_app)
        val mode = if (isApp) TemplateMode.APP else TemplateMode.SMS
        
        if (template.isEmpty()) {
            Toast.makeText(this, "Cannot save empty template", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show dialog to enter template name
        val input = EditText(this).apply {
            hint = "Enter template name"
            setPadding(32, 32, 32, 32)
        }
        
        AlertDialog.Builder(this)
                .setTitle("💾 Save Custom Template")
                .setMessage("Give your template a name:")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Template name cannot be empty", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    if (name.startsWith("🪨")) {
                        Toast.makeText(this, "Cannot use 🪨 - reserved for Rock Solid templates", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    val newTemplate = JsonTemplate(
                        name = name,
                        content = template,
                        isRockSolid = false,
                        mode = mode
                    )
                    
                    templateRepository.saveUserTemplate(newTemplate)
                    loadTemplatesForCurrentMode() // Refresh spinner
                    
                    Toast.makeText(this@AppConfigActivity, "✓ Template '$name' saved!", Toast.LENGTH_LONG).show()
                    android.util.Log.i("AppConfigActivity", "Custom template saved: $name (${mode.name})")
                }
                .setNegativeButton("Cancel", null)
                .show()
    }

    private fun deleteCurrentTemplate() {
        val selected = spinnerTemplate.selectedItem as? JsonTemplate
        
        if (selected == null) {
            Toast.makeText(this, "No template selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selected.isRockSolid) {
            Toast.makeText(this, "🪨 Rock Solid templates cannot be deleted", Toast.LENGTH_LONG).show()
            return
        }
        
        AlertDialog.Builder(this)
                .setTitle("Delete Template?")
                .setMessage("Are you sure you want to delete '${selected.name}'?")
                .setPositiveButton("Delete") { _, _ ->
                    templateRepository.deleteUserTemplate(selected.name)
                    loadTemplatesForCurrentMode() // Refresh spinner
                    Toast.makeText(this@AppConfigActivity, "Template deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
    }


    private fun showJsonPreviewDialog(json: String, isUpdate: Boolean, silent: Boolean, onConfirm: () -> Unit) {
        // Create scrollable text view for JSON
        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            text = json
            textSize = 12f
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scrollView.addView(textView)
        
        AlertDialog.Builder(this)
                .setTitle("📋 JSON Payload Preview")
                .setMessage("Review the JSON that will be sent to your endpoint:")
                .setView(scrollView)
                .setPositiveButton("✓ Send") { _, _ ->
                    onConfirm()
                }
                .setNegativeButton("✗ Cancel", null)
                .setCancelable(true)
                .show()
    }
    
    /**
     * ✅ NEW: Dirty Test with emoji-rich SMS
     * Tests auto-clean functionality with real-world emoji-heavy notification
     */
    private fun performDirtyTest() {
        val template = editJson.text.toString()
        val isApp = (radioGroupMode.checkedRadioButtonId == R.id.radio_app)
        
        // ✅ RANDOMIZE: Generate unique address for each dirty test
        val streetNumber = (10..99).random()
        val streets = listOf("Grand Avenue", "Valley Road", "Summit Street", "Hillside Drive", "Mountain View")
        val randomStreet = streets.random()
        val randomAddress = "$streetNumber $randomStreet"
        val cities = listOf("Cedar Knolls", "Parsippany", "Morris Plains", "Denville", "Boonton")
        val randomCity = cities.random()
        val randomAlertId = (200000..299999).random()
        
        // Real-world emoji-rich SMS with randomized data
        val dirtyMessage = """
🔥 New Fire Alert in Middlesex County

📍 $randomAddress, $randomCity, NJ 07927, USA
🗺️ https://maps.google.com/?q=$randomAddress,+$randomCity,+NJ
📋 Residential Fire - Fire with smoke condition reported; occupants advised to evacuate.
ℹ️ https://www.adjustleads.com/app/alerts/$randomAlertId
        """.trimIndent()
        
        val json = if (isApp) {
            // Treat as generic app notification (like AdjustLeads)
            val appVars = mapOf(
                "package" to "com.adjustleads.app",
                "title" to "🔥 New Fire Alert",
                "text" to dirtyMessage,
                "bigText" to dirtyMessage,
                "time" to android.text.format.DateFormat.format("MM/dd/yyyy h:mm a", System.currentTimeMillis()).toString(),
                "timestamp" to TemplateEngine.getTimestamp()
            )
            TemplateEngine.applyGeneric(
                template,
                appVars,
                false
            )
        } else {
            // Treat as SMS
            val smsVars = mapOf(
                "sender" to "AdjustLeadsTest",
                "message" to dirtyMessage,
                "body" to dirtyMessage,
                "time" to android.text.format.DateFormat.format("MM/dd/yyyy h:mm a", System.currentTimeMillis()).toString(),
                "timestamp" to TemplateEngine.getTimestamp()
            )
            TemplateEngine.applyGeneric(
                template,
                smsVars,
                false
            )
        }
        
        // Show preview
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("🔥 Dirty Test JSON")
                .setMessage("Sending emoji-rich notification to test auto-clean:\n\n${json.take(500)}...")
                .setPositiveButton("Send") { _, _ ->
                    sendTestPayload(json, isUpdate = false, silent = false)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    /**
     * ✅ NEW: Load all sources into the spinner
     */
    private fun loadSources() {
        CoroutineScope(Dispatchers.Main).launch {
            val sources = sourceManager.getAllSources()
            
            if (sources.isEmpty()) {
                Toast.makeText(this@AppConfigActivity, 
                    "No sources configured. Add apps or SMS from main menu first.", 
                    Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            
            val sourceNames = sources.map { "${it.name} (${it.type.name})" }
            val adapter = ArrayAdapter(this@AppConfigActivity, android.R.layout.simple_spinner_item, sourceNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerSource.adapter = adapter
            
            // When source selection changes, load its template JSON
            spinnerSource.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    currentSource = sources[position]
                    loadSourceTemplate()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            
            // Auto-select first source
            if (sources.isNotEmpty()) {
                currentSource = sources[0]
                loadSourceTemplate()
            }
        }
    }
    
    /**
     * ✅ NEW: Load the selected source's template JSON into the editor
     */
    private fun loadSourceTemplate() {
        currentSource?.let { source ->
            editJson.setText(source.templateJson)
        }
    }
}
