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
    private lateinit var mappingsContainer: LinearLayout
    private lateinit var staticContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_config)

        val packageName = intent.getStringExtra("package_name") ?: return
        val appName = intent.getStringExtra("app_name") ?: packageName
        
        config = PrefsManager.getAppConfig(this, packageName)

        findViewById<TextView>(R.id.text_app_name).text = "Config: $appName"
        mappingsContainer = findViewById(R.id.mappings_container)
        staticContainer = findViewById(R.id.static_container)
        
        findViewById<Button>(R.id.btn_add_mapping).setOnClickListener {
            showAddMappingDialog()
        }
        
        findViewById<Button>(R.id.btn_add_static).setOnClickListener {
            showAddStaticDialog()
        }
        
        refreshUI()
    }
    
    private fun refreshUI() {
        mappingsContainer.removeAllViews()
        staticContainer.removeAllViews()
        
        config.mappings.forEach { mapping ->
            val view = LayoutInflater.from(this).inflate(R.layout.item_mapping, mappingsContainer, false)
            view.findViewById<TextView>(R.id.text_source).text = mapping.sourceField.displayName
            view.findViewById<TextView>(R.id.text_target).text = mapping.targetKey
            view.findViewById<android.view.View>(R.id.btn_delete).setOnClickListener {
                config.mappings.remove(mapping)
                save()
                refreshUI()
            }
            mappingsContainer.addView(view)
        }
        
        config.staticFields.forEach { stat ->
            val view = LayoutInflater.from(this).inflate(R.layout.item_mapping, staticContainer, false)
            view.findViewById<TextView>(R.id.text_source).text = "${stat.key} (Static)"
            view.findViewById<TextView>(R.id.text_target).text = stat.value
            view.findViewById<android.view.View>(R.id.btn_delete).setOnClickListener {
                config.staticFields.remove(stat)
                save()
                refreshUI()
            }
            staticContainer.addView(view)
        }
    }
    
    private fun save() {
        PrefsManager.saveAppConfig(this, config)
    }

    private fun showAddMappingDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_mapping, null)
        val spinner = view.findViewById<Spinner>(R.id.spinner_source)
        val inputKey = view.findViewById<EditText>(R.id.input_target_key)
        
        val fields = NotificationField.values()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fields.map { it.displayName })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        AlertDialog.Builder(this)
            .setTitle("Add Mapping")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val selectedField = fields[spinner.selectedItemPosition]
                val key = inputKey.text.toString().trim()
                if (key.isNotEmpty()) {
                    config.mappings.add(FieldMapping(selectedField, key))
                    save()
                    refreshUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddStaticDialog() {
        // Recycle the same dialog layout but adapt inputs if possible, or make a new small one.
        // For simplicity, let's make a quick dynamic dialog logic.
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val inputKey = EditText(this).apply { hint = "JSON Key (e.g. source)" }
        val inputValue = EditText(this).apply { hint = "Value (e.g. android)" }
        layout.addView(inputKey)
        layout.addView(inputValue)

        AlertDialog.Builder(this)
            .setTitle("Add Constant")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val key = inputKey.text.toString().trim()
                val value = inputValue.text.toString().trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    config.staticFields.add(StaticField(key, value))
                    save()
                    refreshUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
