package com.example.alertsheets

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val urlInput = findViewById<EditText>(R.id.url_input)
        val saveButton = findViewById<Button>(R.id.save_button)
        val permButton = findViewById<Button>(R.id.perm_button)
        val battButton = findViewById<Button>(R.id.batt_button)
        val accButton = findViewById<Button>(R.id.acc_button)
        val statusText = findViewById<TextView>(R.id.status_text)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        urlInput.setText(prefs.getString("script_url", ""))

        saveButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                prefs.edit().putString("script_url", url).apply()
                Toast.makeText(this, "URL Saved", Toast.LENGTH_SHORT).show()
            }
        }

        permButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
        
        battButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        accButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        
        updateStatus(statusText)
    }
    
    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.status_text)
        updateStatus(statusText)
    }
    
    private fun updateStatus(textView: TextView) {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val notificationEnabled = listeners != null && listeners.contains(packageName)
        
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryIgnored = pm.isIgnoringBatteryOptimizations(packageName)
        
        val status = StringBuilder()
        status.append("Notification Access: ${if (notificationEnabled) "GRANTED" else "DENIED"}\n")
        status.append("Battery Optimization: ${if (batteryIgnored) "IGNORED" else "ACTIVE"}\n")
        
        textView.text = status.toString()
    }
}
