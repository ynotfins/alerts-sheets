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
                    text = "JSON Payload Configuration"
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
                    text = "Test Payload Now"
                    background.setTint(android.graphics.Color.parseColor("#4CAF50"))
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener { performTest() }
                }

        val checkAutoTest =
                android.widget.CheckBox(this).apply {
                    text = "Auto-Test on Exit"
                    isChecked = true // Default true as per user request
                    // ID for state saving if needed, logic will just check isChecked
                    tag = "auto_test"
                }

        testLayout.addView(btnTest)
        testLayout.addView(checkAutoTest)
        layout.addView(testLayout)

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
            performTest(silent = true)
        }
    }

    private fun performTest(silent: Boolean = false) {
        val template = editJson.text.toString()
        val isApp = (radioGroupMode.checkedRadioButtonId == R.id.radio_app)

        // Dummy Data
        val json =
                if (isApp) {
                    TemplateEngine.applyGeneric(
                            template,
                            "com.test.app",
                            "Test Title",
                            "Test Content",
                            "Test Big Text Content"
                    )
                } else {
                    template.replace("{{sender}}", "Test Sender")
                            .replace("{{message}}", "Test Message Body")
                            .replace("{{time}}", "01/01/2025 12:00:00")
                            .replace("{{timestamp}}", "2025-01-01T12:00:00.000Z")
                }

        if (!silent) Toast.makeText(this, "Sending Test...", Toast.LENGTH_SHORT).show()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // Get URL
            val endpoints = PrefsManager.getEndpoints(this@AppConfigActivity)
            val url = endpoints.firstOrNull()?.url ?: ""

            if (url.isEmpty()) {
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

            val success = NetworkClient.sendSynchronous(url, json)

            if (success) {
                if (!silent)
                        runOnUiThread {
                            Toast.makeText(
                                            this@AppConfigActivity,
                                            "Test SUCCESS! Backend confirmed.",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                PrefsManager.setPayloadTestStatus(this@AppConfigActivity, 1) // Success
            } else {
                if (!silent)
                        runOnUiThread {
                            Toast.makeText(
                                            this@AppConfigActivity,
                                            "Test FAILED. Check Logs/URL.",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                PrefsManager.setPayloadTestStatus(this@AppConfigActivity, 2) // Fail
            }
        }
    }
}
