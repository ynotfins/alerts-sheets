package com.example.alertsheets.data.repositories

import android.content.Context
import com.example.alertsheets.domain.models.Template
import com.example.alertsheets.domain.models.RockSolidTemplates
import com.example.alertsheets.data.storage.JsonStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing Templates
 * Handles CRUD operations, preserves Rock Solid templates
 */
class TemplateRepository(private val context: Context) {
    
    private val storage = JsonStorage(context, "templates.json")
    private val gson = Gson()
    
    /**
     * Get all templates (Rock Solid + Custom)
     */
    fun getAll(): List<Template> {
        val rockSolid = RockSolidTemplates.getAll()
        val custom = getCustomTemplates()
        return rockSolid + custom
    }
    
    /**
     * Get template by ID
     */
    fun getById(id: String): Template? {
        return getAll().firstOrNull { it.id == id }
    }
    
    /**
     * Get templates available for a specific source
     */
    fun getForSource(sourceId: String): List<Template> {
        return getAll().filter { it.sourceId == null || it.sourceId == sourceId }
    }
    
    /**
     * Get custom (non-Rock Solid) templates
     */
    private fun getCustomTemplates(): List<Template> {
        val json = storage.read() ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<Template>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Save template (create or update)
     * Rock Solid templates cannot be modified
     */
    fun save(template: Template) {
        if (template.isRockSolid) {
            throw IllegalArgumentException("Cannot modify Rock Solid templates")
        }
        
        val custom = getCustomTemplates().toMutableList()
        val index = custom.indexOfFirst { it.id == template.id }
        
        if (index >= 0) {
            custom[index] = template.copy(updatedAt = System.currentTimeMillis())
        } else {
            custom.add(template)
        }
        
        storage.write(gson.toJson(custom))
    }
    
    /**
     * Delete template
     * Rock Solid templates cannot be deleted
     */
    fun delete(id: String) {
        val template = getById(id)
        if (template?.isRockSolid == true) {
            throw IllegalArgumentException("Cannot delete Rock Solid templates")
        }
        
        val custom = getCustomTemplates().toMutableList()
        custom.removeAll { it.id == id }
        storage.write(gson.toJson(custom))
    }
}

