package com.example.alertsheets

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PrefsManager {
    private const val PREFS_NAME = "app_prefs_v2"
    private const val KEY_ENDPOINTS = "endpoints"
    private const val KEY_TARGET_APPS = "target_apps" // Set<String> package names

    private val gson = Gson()

    fun getEndpoints(context: Context): List<Endpoint> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ENDPOINTS, null) ?: return emptyList()
        val type = object : TypeToken<List<Endpoint>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveEndpoints(context: Context, list: List<Endpoint>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_ENDPOINTS, json).apply()
    }

    fun getTargetApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_TARGET_APPS, emptySet()) ?: emptySet()
    }
    
    fun saveTargetApps(context: Context, apps: Set<String>) {
         val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
         prefs.edit().putStringSet(KEY_TARGET_APPS, apps).apply()
    }

    // SMS Targets
    fun getSmsTargets(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("sms_targets", emptySet()) ?: emptySet()
    }

    fun saveSmsTargets(context: Context, targets: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("sms_targets", targets).apply()
    }

    // JSON Template
    fun getJsonTemplate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Default Template matching current Parser output
        val default = """
            {
              "incidentId": "{{id}}",
              "status": "{{status}}",
              "state": "{{state}}",
              "county": "{{county}}",
              "city": "{{city}}",
              "address": "{{address}}",
              "incidentType": "{{type}}",
              "incidentDetails": "{{details}}",
              "fdCodes": {{codes}},
              "originalBody": "{{original}}"
            }
        """.trimIndent()
        return prefs.getString("json_template", default) ?: default
    }

    fun saveJsonTemplate(context: Context, template: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("json_template", template).apply()
    }
    
    // --- AppConfig Methods ---
    
    fun getAppConfig(context: Context, packageName: String): AppConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("config_$packageName", null)
        return if (json != null) {
            gson.fromJson(json, AppConfig::class.java)
        } else {
            AppConfig(packageName)
        }
    }

    fun saveAppConfig(context: Context, config: AppConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(config)
        prefs.edit().putString("config_${config.packageName}", json).apply()
    }
}
