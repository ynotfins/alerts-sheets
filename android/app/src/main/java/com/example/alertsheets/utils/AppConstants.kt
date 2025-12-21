package com.example.alertsheets.utils

/**
 * Central constants for AlertsToSheets V2
 * 
 * BENEFITS:
 * - Single source of truth for magic strings
 * - Easy refactoring (change once, updates everywhere)
 * - Prevents typos (compile-time checking)
 * - Better IDE autocomplete
 * 
 * USAGE:
 * Instead of: prefs.getString("app_prefs_v2", ...)
 * Use:        prefs.getString(AppConstants.PREFS_NAME, ...)
 */
object AppConstants {
    
    // ============================================================
    // SHARED PREFERENCES
    // ============================================================
    
    /** Main SharedPreferences file name */
    const val PREFS_NAME = "app_prefs_v2"
    
    /** Extra source configuration (filters, case sensitivity, etc.) */
    const val PREFS_SOURCE_EXTRAS = "source_extras"
    
    /** SharedPreferences keys */
    object PrefsKeys {
        const val ENDPOINTS = "endpoints"
        const val TARGET_APPS = "target_apps"
        const val SMS_TARGETS = "sms_targets"
        const val SMS_CONFIG_LIST = "sms_config_list"
        const val SHOULD_CLEAN_DATA = "should_clean_data"
        const val LAST_CONFIG_MODE = "last_config_mode"
        const val MASTER_ENABLED = "master_enabled"
        const val JSON_TEMPLATE = "json_template"
        const val APP_JSON_TEMPLATE = "app_json_template"
        const val SMS_JSON_TEMPLATE = "sms_json_template"
        const val PAYLOAD_TEST_STATUS = "payload_test_status"
    }
    
    // ============================================================
    // MIGRATION
    // ============================================================
    
    /** Migration completion flag */
    const val MIGRATION_KEY = "v2_migration_complete"
    
    // ============================================================
    // TEMPLATE IDs
    // ============================================================
    
    /** BNN-specific incident parser template */
    const val TEMPLATE_BNN = "rock-solid-bnn-format"
    
    /** Default SMS template */
    const val TEMPLATE_SMS_DEFAULT = "rock-solid-sms-default"
    
    /** Default generic app notification template */
    const val TEMPLATE_APP_DEFAULT = "rock-solid-app-default"
    
    // ============================================================
    // PARSER IDs
    // ============================================================
    
    /** BNN incident parser */
    const val PARSER_BNN = "bnn"
    
    /** Generic app notification parser */
    const val PARSER_GENERIC = "generic"
    
    /** SMS message parser */
    const val PARSER_SMS = "sms"
    
    // ============================================================
    // ENDPOINT IDs
    // ============================================================
    
    /** Default endpoint (Google Apps Script webhook) */
    const val ENDPOINT_DEFAULT = "default-endpoint"
    
    // ============================================================
    // FILE STORAGE
    // ============================================================
    
    /** Sources JSON file name */
    const val FILE_SOURCES = "sources.json"
    
    /** Templates JSON file name */
    const val FILE_TEMPLATES = "templates.json"
    
    /** Endpoints JSON file name */
    const val FILE_ENDPOINTS = "endpoints.json"
    
    /** Activity logs JSON file name */
    const val FILE_LOGS = "logs.json"
    
    // ============================================================
    // PACKAGE NAMES
    // ============================================================
    
    /** SMS package identifier */
    const val PKG_SMS = "com.android.sms"
    
    /** App package name (for self-filtering) */
    const val PKG_SELF = "com.example.alertsheets"
    
    // ============================================================
    // NOTIFICATION CHANNEL IDs
    // ============================================================
    
    /** Foreground service notification channel */
    const val CHANNEL_FOREGROUND = "alerts_foreground"
    
    /** Service notification channel */
    const val CHANNEL_SERVICE = "service_channel"
    
    // ============================================================
    // LOG LIMITS
    // ============================================================
    
    /** Maximum number of logs to keep */
    const val MAX_LOGS = 1000
    
    /** Maximum number of duplicate entries in DeDuplicator */
    const val MAX_DEDUP_ENTRIES = 500
    
    // ============================================================
    // DEFAULT COLORS (ARGB)
    // ============================================================
    
    /** Default color for SMS sources (green) */
    const val COLOR_SMS = 0xFF00D980.toInt()
    
    /** Default color for BNN sources (purple) */
    const val COLOR_BNN = 0xFFA855F7.toInt()
    
    /** Default color for generic app sources (blue) */
    const val COLOR_APP = 0xFF4A9EFF.toInt()
    
    // ============================================================
    // UI MODES
    // ============================================================
    
    /** App notification mode */
    const val MODE_APP = "APP"
    
    /** SMS mode */
    const val MODE_SMS = "SMS"
    
    // ============================================================
    // NOTIFICATION IDs
    // ============================================================
    
    /** Foreground service notification ID */
    const val NOTIFICATION_ID_FOREGROUND = 1
    
    // ============================================================
    // INTENT ACTIONS
    // ============================================================
    
    /** Quick boot power on action */
    const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    
    // ============================================================
    // SOURCE EXTRAS KEYS (per-source settings)
    // ============================================================
    
    object SourceExtras {
        /** SMS filter text key pattern: {sourceId}:filterText */
        const val FILTER_TEXT_SUFFIX = ":filterText"
        
        /** SMS case sensitivity key pattern: {sourceId}:isCaseSensitive */
        const val CASE_SENSITIVE_SUFFIX = ":isCaseSensitive"
        
        /** SMS phone number key pattern: {sourceId}:phoneNumber */
        const val PHONE_NUMBER_SUFFIX = ":phoneNumber"
    }
    
    // ============================================================
    // ERROR MESSAGES
    // ============================================================
    
    object Errors {
        const val CORRUPT_SOURCES_JSON = "Corrupt sources.json detected, resetting to empty"
        const val CORRUPT_TEMPLATES_JSON = "Corrupt templates.json detected, using defaults"
        const val CORRUPT_ENDPOINTS_JSON = "Corrupt endpoints.json detected, resetting to empty"
        const val NETWORK_SEND_FAILED = "Network send failed"
        const val JSON_PARSE_FAILED = "JSON parsing failed"
        const val FILE_READ_FAILED = "File read operation failed"
        const val FILE_WRITE_FAILED = "File write operation failed"
    }
    
    // ============================================================
    // SUCCESS MESSAGES
    // ============================================================
    
    object Success {
        const val MIGRATION_COMPLETE = "V1 → V2 migration complete"
        const val SOURCE_SAVED = "Source saved successfully"
        const val SOURCE_DELETED = "Source deleted successfully"
        const val NETWORK_SEND_SUCCESS = "Data sent successfully"
    }
}

