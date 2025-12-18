package com.example.alertsheets

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private val TAG = "PermissionsActivity"

    private lateinit var mainLayout: LinearLayout // Store reference to layout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainLayout =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 32, 32, 32)
                    background =
                            ContextCompat.getDrawable(
                                    this@PermissionsActivity,
                                    R.drawable.bg_card_dark
                            ) // Reusing existing bg if possible or just color
                    setBackgroundColor(android.graphics.Color.parseColor("#121212"))
                }

        buildPermissionsList()
        setContentView(mainLayout)
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission status without recreating activity
        refreshPermissions()
    }

    private fun buildPermissionsList() {
        mainLayout.removeAllViews() // Clear existing views

        val title =
                TextView(this).apply {
                    text = "Required Permissions"
                    textSize = 24f
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 0, 0, 32)
                }
        mainLayout.addView(title)

        // 1. Notification Listener
        addPermissionItem(
                mainLayout,
                "Notification Listener",
                "Required to read notifications from other apps.",
                checkNotifListener()
        ) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        // 2. SMS Permission
        addPermissionItem(
                mainLayout,
                "Read SMS",
                "Required to capture incoming SMS messages.",
                checkSmsPermission()
        ) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }

        // 3. Battery Optimization
        addPermissionItem(
                mainLayout,
                "Ignore Battery Opt",
                "Prevents the system from killing the background service.",
                checkBattery()
        ) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun refreshPermissions() {
        // Simply rebuild the entire list with updated statuses
        buildPermissionsList()
    }

    private fun addPermissionItem(
            parent: LinearLayout,
            title: String,
            desc: String,
            isGranted: Boolean,
            onClick: () -> Unit
    ) {
        val itemLayout =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 16, 0, 16)
                    setOnClickListener { onClick() }
                    background =
                            android.graphics.drawable.GradientDrawable().apply {
                                setStroke(1, android.graphics.Color.DKGRAY)
                                cornerRadius = 8f
                            }
                }

        val header =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

        val statusDot =
                TextView(this).apply {
                    text = "●"
                    textSize = 20f
                    setTextColor(
                            if (isGranted) android.graphics.Color.GREEN
                            else android.graphics.Color.RED
                    )
                    setPadding(16, 0, 16, 0)
                }

        val titleView =
                TextView(this).apply {
                    text = title + if (isGranted) " (Enabled)" else " (Missing)"
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.WHITE)
                }

        header.addView(statusDot)
        header.addView(titleView)
        itemLayout.addView(header)

        val descView =
                TextView(this).apply {
                    text = desc
                    textSize = 14f
                    setTextColor(android.graphics.Color.LTGRAY)
                    setPadding(64, 8, 16, 0)
                }
        itemLayout.addView(descView)

        parent.addView(itemLayout)

        // Margin
        val spacer =
                android.view.View(this).apply {
                    layoutParams =
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24)
                }
        parent.addView(spacer)
    }

    private fun checkNotifListener(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabledLegacy = flat != null && flat.contains(packageName)
        val isEnabledCompat =
                NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val result = isEnabledLegacy || isEnabledCompat
        Log.d(
                TAG,
                "checkNotifListener() = $result (legacy=$isEnabledLegacy, compat=$isEnabledCompat)"
        )
        return result
    }

    private fun checkSmsPermission(): Boolean {
        val result =
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "checkSmsPermission() = $result")
        return result
    }

    private fun checkBattery(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val result = pm.isIgnoringBatteryOptimizations(packageName)
        Log.d(TAG, "checkBattery() = $result")
        return result
    }

    private fun checkAccessibility(): Boolean {
        var accessibilityEnabled = 0
        val service = "$packageName/${NotificationAccessibilityService::class.java.canonicalName}"
        try {
            accessibilityEnabled =
                    Settings.Secure.getInt(
                            applicationContext.contentResolver,
                            android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
                    )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(TAG, "Error finding setting, default accessibility to not found: " + e.message)
        }
        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')

        if (accessibilityEnabled == 1) {
            val settingValue =
                    Settings.Secure.getString(
                            applicationContext.contentResolver,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        Log.d(TAG, "checkAccessibility() = TRUE (service=$service found)")
                        return true
                    }
                }
            }
        }
        Log.d(TAG, "checkAccessibility() = FALSE (service=$service not found)")
        return false
    }
}
