package com.example.alertsheets.data.repositories

import android.content.Context
import android.util.Log
import com.example.alertsheets.JsonTemplate
import com.example.alertsheets.PrefsManager
import com.example.alertsheets.TemplateMode
import com.example.alertsheets.utils.AppConstants

/**
 * Repository for managing JSON Templates
 * 
 * PHASE 2: Facade pattern over PrefsManager
 * - Provides consistent API with other repositories  
 * - Easy to migrate to JSON storage later
 * - Centralizes template logic
 * 
 * FUTURE: Migrate to JsonStorage with Template model
 */
class TemplateRepository(private val context: Context) {
    
    private val TAG = "TemplateRepository"
    
    /**
     * Get template by ID
     * Maps old PrefsManager template IDs to actual templates
     */
    fun getById(templateId: String): String? {
        return try {
            when (templateId) {
                AppConstants.TEMPLATE_BNN ->  {
                    // BNN uses its own parser, doesn't need a template
                    // But return a fallback just in case
                    PrefsManager.getAppJsonTemplate(context)
                }
                AppConstants.TEMPLATE_APP_DEFAULT -> {
                    PrefsManager.getAppJsonTemplate(context)
                }
                AppConstants.TEMPLATE_SMS_DEFAULT -> {
                    PrefsManager.getSmsJsonTemplate(context)
                }
                else -> {
                    // Try to find custom template by ID
                    // For now, fall back to app template
                    Log.w(TAG, "Unknown template ID: $templateId, using app default")
                    PrefsManager.getAppJsonTemplate(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get template: $templateId", e)
            null
        }
    }
    
    /**
     * Get app notification template
     */
    fun getAppTemplate(): String {
        return try {
            PrefsManager.getAppJsonTemplate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app template, using fallback", e)
            getFallbackAppTemplate()
        }
    }
    
    /**
     * Get SMS template
     */
    fun getSmsTemplate(): String {
        return try {
            PrefsManager.getSmsJsonTemplate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SMS template, using fallback", e)
            getFallbackSmsTemplate()
        }
    }
    
    /**
     * Save app template
     */
    fun saveAppTemplate(template: String) {
        try {
            PrefsManager.saveAppJsonTemplate(context, template)
            Log.d(TAG, "App template saved")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save app template", e)
        }
    }
    
    /**
     * Save SMS template
     */
    fun saveSmsTemplate(template: String) {
        try {
            PrefsManager.saveSmsJsonTemplate(context, template)
            Log.d(TAG, "SMS template saved")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save SMS template", e)
        }
    }
    
    /**
     * Get all Rock Solid templates (immutable defaults)
     */
    fun getRockSolidTemplates(): List<JsonTemplate> {
        return try {
            listOf(
                PrefsManager.getRockSolidAppTemplate(),
                PrefsManager.getRockSolidSmsTemplate(),
                PrefsManager.getRockSolidBnnTemplate()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Rock Solid templates", e)
            emptyList()
        }
    }
    
    /**
     * Get all user-created templates
     */
    fun getUserTemplates(): List<JsonTemplate> {
        return try {
            PrefsManager.getUserTemplates(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user templates", e)
            emptyList()
        }
    }
    
    /**
     * Get all templates (Rock Solid + User)
     */
    fun getAllTemplates(): List<JsonTemplate> {
        return try {
            getRockSolidTemplates() + getUserTemplates()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all templates", e)
            getRockSolidTemplates()  // Fallback to at least Rock Solid
        }
    }
    
    /**
     * Save user template
     */
    fun saveUserTemplate(template: JsonTemplate) {
        try {
            PrefsManager.saveUserTemplate(context, template)
            Log.d(TAG, "User template saved: ${template.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user template: ${template.name}", e)
        }
    }
    
    /**
     * Delete user template
     */
    fun deleteUserTemplate(templateName: String) {
        try {
            PrefsManager.deleteUserTemplate(context, templateName)
            Log.d(TAG, "User template deleted: $templateName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user template: $templateName", e)
        }
    }
    
    /**
     * Get template by name (searches both Rock Solid and User templates)
     */
    fun getByName(name: String): JsonTemplate? {
        return try {
            getAllTemplates().firstOrNull { it.name == name }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get template by name: $name", e)
            null
        }
    }
    
    /**
     * Get templates by mode (APP or SMS)
     */
    fun getByMode(mode: TemplateMode): List<JsonTemplate> {
        return try {
            getAllTemplates().filter { it.mode == mode }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get templates by mode: $mode", e)
            emptyList()
        }
    }
    
    // ============================================================
    // FALLBACK TEMPLATES (Hard-coded for safety)
    // ============================================================
    
    private fun getFallbackAppTemplate(): String {
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
    
    private fun getFallbackSmsTemplate(): String {
        return """
{
  "source": "sms",
  "sender": "{{sender}}",
  "body": "{{body}}",
  "time": "{{time}}",
  "timestamp": "{{timestamp}}"
}
        """.trimIndent()
    }
    
    /**
     * Get a default template JSON for creating a new source.
     * Returns a clean starting point based on the source type.
     */
    fun getDefaultJsonForNewSource(sourceType: com.example.alertsheets.domain.models.SourceType): String {
        return when (sourceType) {
            com.example.alertsheets.domain.models.SourceType.APP -> getFallbackAppTemplate()
            com.example.alertsheets.domain.models.SourceType.SMS -> getFallbackSmsTemplate()
        }
    }
}
