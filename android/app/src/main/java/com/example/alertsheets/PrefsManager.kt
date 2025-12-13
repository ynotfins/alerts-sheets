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
