package com.example.alertsheets

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AppConfigActivity : AppCompatActivity() {

    private lateinit var config: AppConfig
    private lateinit var editJson: EditText
    private lateinit var spinnerTemplate: Spinner
    private lateinit var btnSave: Button
    
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
        val packageName = intent.getStringExtra("package_name") ?: return
        val appName = intent.getStringExtra("app_name") ?: packageName
        config = PrefsManager.getAppConfig(this, packageName)
        
        val title = TextView(this).apply {
            text = "Config: $appName"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)

        // Template Selector
        val labelTemplate = TextView(this).apply { text = "Load Template:" }
        layout.addView(labelTemplate)
        
        spinnerTemplate = Spinner(this)
        val templates = listOf("Select...", "App Notification", "SMS Message", "Simple BNN")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, templates)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTemplate.adapter = adapter
        layout.addView(spinnerTemplate)
        
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
            setText(PrefsManager.getJsonTemplate(this@AppConfigActivity))
        }
        layout.addView(editJson)
        
        // Helper Chips / Legend
        val labelHelp = TextView(this).apply { 
            text = "Available Variables: ${appVariables.joinToString(", ")}" 
            textSize = 12f
            setPadding(0, 16, 0, 16)
            setTextColor(android.graphics.Color.DKGRAY)
        }
        layout.addView(labelHelp)

        // Save Button
        btnSave = Button(this).apply {
            text = "Save Configuration"
            setOnClickListener {
                save()
            }
        }
        layout.addView(btnSave)

        // Template Selection Logic
        spinnerTemplate.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                when (position) {
                    1 -> editJson.setText(getAppTemplate()) // App
                    2 -> editJson.setText(getSmsTemplate()) // SMS
                    3 -> editJson.setText(getBnnTemplate()) // BNN
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        setContentView(layout)
    }

    private fun save() {
        val newJson = editJson.text.toString()
        // Validate JSON
        try {
            // Simple check
            if (!newJson.trim().startsWith("{")) throw Exception("Must start with {")
            PrefsManager.saveJsonTemplate(this, newJson)
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
            finish()
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
  "timestamp": "{{time}}"
}
        """.trimIndent()
    }
    
    private fun getSmsTemplate(): String {
        return """
{
  "source": "sms",
  "sender": "{{sender}}",
  "message": "{{message}}",
  "timestamp": "{{time}}"
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
  "codes": {{codes}}
}
         """.trimIndent()
    }
}
