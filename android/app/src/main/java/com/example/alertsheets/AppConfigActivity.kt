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
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AppConfigActivity : AppCompatActivity() {

    private lateinit var editJson: EditText
    private lateinit var spinnerTemplate: Spinner
    private lateinit var btnSave: Button
    private lateinit var radioGroupMode: RadioGroup

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
        val layout =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 32, 32, 32)
                }

        // Header
        val title =
                TextView(this).apply {
                    text = "JSON Payload Config (v2 DEBUG)"
                    textSize = 20f
                    setPadding(0, 0, 0, 24)
                }
        layout.addView(title)

        // MODE SELECTION
        val labelMode =
                TextView(this).apply {
                    text = "Select Configuration Mode:"
                    setPadding(0, 0, 0, 8)
                }
        layout.addView(labelMode)

        radioGroupMode = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }

        val radioApp =
                RadioButton(this).apply {
                    text = "App Notifications"
                    id = R.id.radio_app
                    isChecked = true
                }
        val radioSms =
                RadioButton(this).apply {
                    text = "SMS Messages"
                    id = R.id.radio_sms
                }

        radioGroupMode.addView(radioApp)
        radioGroupMode.addView(radioSms)
        layout.addView(radioGroupMode)

        // Template Selector
        val labelTemplate =
                TextView(this).apply {
                    text = "Load Preset Template:"
                    setPadding(0, 16, 0, 8)
                }
        layout.addView(labelTemplate)

        spinnerTemplate = Spinner(this)
        // Default items for App
        val adapter =
                ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_item,
                        listOf("Select...", "Default App", "BNN Format")
                )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTemplate.adapter = adapter
        layout.addView(spinnerTemplate)

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
                        editJson.setText(cleaned)
                        Toast.makeText(
                                        this@AppConfigActivity,
                                        "Removed Emojis/Symbols",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
        layout.addView(btnClean)

        // Save Button
        btnSave =
                Button(this).apply {
                    text = "Save Configuration"
                    setOnClickListener { save() }
                }
        layout.addView(btnSave)

        // TEST SECTION
        val testLayout =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 24, 0, 0)
                }

        val btnTest =
                Button(this).apply {
                    text = "Test New Incident"
                    background.setTint(android.graphics.Color.parseColor("#4CAF50"))
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener { performTest(isUpdate = false) }
                }

        val btnTestUpdate =
                Button(this).apply {
                    text = "Test Update"
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

        val checkAutoTest =
                android.widget.CheckBox(this).apply {
                    text = "Auto-Test on Exit"
                    isChecked = true // Default true as per user request
                    // ID for state saving if needed, logic will just check isChecked
                    tag = "auto_test"
                }

        testLayout.addView(btnTest)
        testLayout.addView(btnTestUpdate)
        layout.addView(testLayout)
        layout.addView(checkAutoTest) // Move checkbox below buttons

        // Logic

        // Load initial
        loadConfig(isAppMode = true)

        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            val isApp = (checkedId == R.id.radio_app)
            loadConfig(isApp)
            updateUIForMode(isApp)
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
                        if (position == 0) return

                        // Determine Mode based on Radio
                        val isApp = (radioGroupMode.checkedRadioButtonId == R.id.radio_app)

                        if (isApp) {
                            when (position) {
                                1 -> editJson.setText(getAppTemplate())
                                2 -> editJson.setText(getBnnTemplate())
                            }
                        } else {
                            // SMS Mode
                            when (position) {
                                1 -> editJson.setText(getSmsTemplate())
                            }
                        }
                        spinnerTemplate.setSelection(0) // Reset
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }

        setContentView(layout)
    }

    private fun updateUIForMode(isApp: Boolean) {
        // Update Spinner content
        val items =
                if (isApp) {
                    listOf("Select Preset...", "Default App", "BNN Format")
                } else {
                    listOf("Select Preset...", "Default SMS")
                }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTemplate.adapter = adapter
    }

    private fun loadConfig(isAppMode: Boolean) {
        if (isAppMode) {
            editJson.setText(PrefsManager.getAppJsonTemplate(this))
        } else {
            editJson.setText(PrefsManager.getSmsJsonTemplate(this))
        }
    }

    private fun save() {
        val newJson = editJson.text.toString()
        val isAppMode = (radioGroupMode.checkedRadioButtonId == R.id.radio_app)

        // Validate JSON
        try {
            // Simple check
            if (!newJson.trim().startsWith("{")) throw Exception("Must start with {")

            if (isAppMode) {
                PrefsManager.saveAppJsonTemplate(this, newJson)
                Toast.makeText(this, "Saved App Config!", Toast.LENGTH_SHORT).show()
            } else {
                PrefsManager.saveSmsJsonTemplate(this, newJson)
                Toast.makeText(this, "Saved SMS Config!", Toast.LENGTH_SHORT).show()
            }
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

                    // 1. Construct Mock BNN Message
                    // User Request: Always bar between incident and source. <C> BNN
                    val mockBnn =
                            "$statusPrefix NJ | Test County | Test City | 888 Test Ave | TEST-TYPE | Testing $testType - Columns should fill. | <C> BNN | E-1/L-1 | nj312/njn751/nyl785/pa547 | $incidentId"

                    android.util.Log.i("TEST", "Mock BNN: $mockBnn")

                    // 2. Run through REAL Parser
                    val parsed = Parser.parse(mockBnn)

                    if (parsed != null) {
                        // 3. Serialize exactly like NotificationService
                        val timestamped =
                                parsed.copy(
                                        timestamp = TemplateEngine.getTimestamp(),
                                        status = testType
                                )
                        val jsonPayload = com.google.gson.Gson().toJson(timestamped)
                        android.util.Log.i("TEST", "Parsed successfully")
                        android.util.Log.i("TEST", "Incident ID in JSON: ${timestamped.incidentId}")
                        android.util.Log.i("TEST", "Status: ${timestamped.status}")

                        jsonPayload
                    } else {
                        // Fallback (Should not happen with valid mock)
                        TemplateEngine.applyGeneric(
                                template,
                                "com.test.bnn",
                                "Test Title",
                                "Test Text",
                                mockBnn
                        )
                    }
                } else {
                    // SMS Mode - More realistic test message
                    val testSender = "+1-555-0123"
                    val testMessage = "ALERT: Fire reported at 123 Main St. Units responding. This is a test SMS message with more realistic content to verify full text appears in spreadsheet."
                    
                    TemplateEngine.applyGeneric(
                                    template,
                                    "sms", // pkg
                                    testSender, // title/sender
                                    testMessage, // text/message
                                    ""
                            )
                }

        if (!silent) {
            val message = if (isApp && isUpdate) "Sending Update Test..." else "Sending New Test..."
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
                        if (!silent)
                                runOnUiThread {
                                    Toast.makeText(
                                                    this@AppConfigActivity,
                                                    "Test SUCCESS! Status: $statusCode",
                                                    Toast.LENGTH_LONG
                                            )
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
        }
    }
}
