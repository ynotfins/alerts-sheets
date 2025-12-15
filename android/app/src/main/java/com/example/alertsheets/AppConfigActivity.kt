package com.example.alertsheets

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
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

class AppConfigActivity : AppCompatActivity() {

    private lateinit var editJson: EditText
    private lateinit var spinnerTemplate: Spinner
    private lateinit var btnSave: Button
    private lateinit var radioGroupMode: RadioGroup
    
    // Available Variables for User Reference
    private val appVariables = listOf(
        "{{package}}", "{{title}}", "{{text}}", "{{bigText}}", "{{time}}"
    )
    private val smsVariables = listOf(
        "{{sender}}", "{{message}}", "{{time}}"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Header
        val title = TextView(this).apply {
            text = "JSON Payload Configuration"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)
        
        // MODE SELECTION
        val labelMode = TextView(this).apply { text = "Select Configuration Mode:" ; setPadding(0, 0, 0, 8) }
        layout.addView(labelMode)
        
        radioGroupMode = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        
        val radioApp = RadioButton(this).apply {
            text = "App Notifications"
            id = 100
            isChecked = true
        }
        val radioSms = RadioButton(this).apply {
            text = "SMS Messages"
            id = 101
        }
        
        radioGroupMode.addView(radioApp)
        radioGroupMode.addView(radioSms)
        layout.addView(radioGroupMode)
        
        // Template Selector
        val labelTemplate = TextView(this).apply { text = "Load Preset Template:" ; setPadding(0, 16, 0, 8) }
        layout.addView(labelTemplate)
        
        spinnerTemplate = Spinner(this)
        // Default items for App
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Select...", "Default App", "BNN Format"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTemplate.adapter = adapter
        layout.addView(spinnerTemplate)
        
        // Clean Data Option
        val checkClean = android.widget.CheckBox(this).apply {
            text = "Auto-Clean Emojis/Symbols (Global)"
            isChecked = PrefsManager.getShouldCleanData(this@AppConfigActivity)
            setOnCheckedChangeListener { _, isChecked ->
                PrefsManager.saveShouldCleanData(this@AppConfigActivity, isChecked)
            }
            setPadding(0, 16, 0, 16)
        }
        layout.addView(checkClean)

        // JSON Editor
        val labelJson = TextView(this).apply { text = "JSON Payload (Editable):" ; setPadding(0, 24, 0, 8) }
        layout.addView(labelJson)
        
        editJson = EditText(this).apply {
            hint = "{\n  \"key\": \"{{val}}\"\n}"
            setLines(12)
            gravity = android.view.Gravity.TOP
            background = android.graphics.drawable.GradientDrawable().apply {
                setStroke(2, android.graphics.Color.GRAY)
                cornerRadius = 8f
            }
            setPadding(16, 16, 16, 16)
        }
        layout.addView(editJson)
        
        // Helper Chips / Legend
        val labelHelp = TextView(this).apply { 
            text = "Variables: ${appVariables.joinToString(", ")}" 
            textSize = 12f
            setPadding(0, 8, 0, 8)
            setTextColor(android.graphics.Color.DKGRAY)
        }
        layout.addView(labelHelp)
        
        // Clean Valid Checkbox
        val btnClean = Button(this).apply {
            text = "Clean Invalid Chars (Test)"
            setOnClickListener {
                val current = editJson.text.toString()
                val cleaned = TemplateEngine.cleanText(current)
                editJson.setText(cleaned)
                Toast.makeText(this@AppConfigActivity, "Removed Emojis/Symbols", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(btnClean)

        // Save Button
        btnSave = Button(this).apply {
            text = "Save Configuration"
            setOnClickListener {
                save()
            }
        }
        layout.addView(btnSave)

        // Logic
        
        // Load initial
        loadConfig(isAppMode = true)
        
        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            val isApp = (checkedId == 100)
            loadConfig(isApp)
            updateUIForMode(isApp)
        }

        // Template Selection Logic
        spinnerTemplate.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position == 0) return
                
                // Determine Mode based on Radio
                val isApp = (radioGroupMode.checkedRadioButtonId == 100)
                
                if (isApp) {
                     when(position) {
                         1 -> editJson.setText(getAppTemplate())
                         2 -> editJson.setText(getBnnTemplate())
                     }
                } else {
                    // SMS Mode
                    when(position) {
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
        val items = if (isApp) {
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
        val isAppMode = (radioGroupMode.checkedRadioButtonId == 100)
        
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
  "time": "{{time}}"
}
        """.trimIndent()
    }
    
    private fun getSmsTemplate(): String {
        return """
{
  "source": "sms",
  "sender": "{{sender}}",
  "message": "{{message}}",
  "time": "{{time}}"
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
  "codes": {{codes}}
}
         """.trimIndent()
    }
}
