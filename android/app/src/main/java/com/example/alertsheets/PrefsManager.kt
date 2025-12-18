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

    private const val KEY_SMS_CONFIG_LIST = "sms_config_list"

    fun getSmsConfigList(context: Context): List<SmsTarget> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SMS_CONFIG_LIST, null) ?: return emptyList()
        val type = object : TypeToken<List<SmsTarget>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveSmsConfigList(context: Context, list: List<SmsTarget>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_SMS_CONFIG_LIST, json).apply()
    }

    fun getShouldCleanData(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("should_clean_data", false)
    }

    fun saveShouldCleanData(context: Context, shouldClean: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("should_clean_data", shouldClean).apply()
    }

    // JSON Templates
    // Deprecated single template, splitting into App and SMS

    fun getAppJsonTemplate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Fallback to old key if new key missing (Migration)
        val legacy = prefs.getString("json_template", null)

        val default =
                """
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

        return prefs.getString("json_template_app", legacy ?: default) ?: default
    }

    fun saveAppJsonTemplate(context: Context, template: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("json_template_app", template).apply()
    }

    fun getSmsJsonTemplate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Default SMS Template
        val default =
                """
            {
              "source": "sms",
              "sender": "{{sender}}",
              "message": "{{message}}",
              "time": "{{time}}",
              "timestamp": "{{timestamp}}"
            }
        """.trimIndent()
        return prefs.getString("json_template_sms", default) ?: default
    }

    fun saveSmsJsonTemplate(context: Context, template: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("json_template_sms", template).apply()
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

    // --- Master Switch & Status ---

    fun getMasterEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("master_enabled", true) // Default true
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("master_enabled", enabled).apply()
    }

    // Status: 0=Unknown, 1=Success, 2=Failed
    fun getPayloadTestStatus(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("last_payload_test_status", 0)
    }

    fun setPayloadTestStatus(context: Context, status: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("last_payload_test_status", status).apply()
    }

    private const val KEY_LAST_TEST_ID = "lastTestIncidentId"

    fun saveLastTestId(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_TEST_ID, id).apply()
    }

    fun getLastTestId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_TEST_ID, "") ?: ""
    }
}
