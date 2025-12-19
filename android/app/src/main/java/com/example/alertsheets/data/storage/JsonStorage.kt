package com.example.alertsheets.data.storage

import android.content.Context
import java.io.File

/**
 * Simple JSON file storage
 * Used for storing lists of objects (sources, templates, endpoints)
 */
class JsonStorage(private val context: Context, private val filename: String) {
    
    private val file: File
        get() = File(context.filesDir, filename)
    
    /**
     * Read JSON from file
     */
    fun read(): String? {
        return try {
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Write JSON to file
     */
    fun write(json: String) {
        try {
            file.writeText(json)
        } catch (e: Exception) {
            // Log error
        }
    }
    
    /**
     * Check if file exists
     */
    fun exists(): Boolean {
        return file.exists()
    }
    
    /**
     * Delete file
     */
    fun delete() {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // Log error
        }
    }
}

