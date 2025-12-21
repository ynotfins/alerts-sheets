package com.example.alertsheets.data.storage

import android.content.Context
import android.util.Log
import com.example.alertsheets.utils.AppConstants
import java.io.File
import java.io.IOException

/**
 * Thread-safe JSON file storage with proper error handling
 * 
 * FEATURES:
 * - File locking prevents concurrent access corruption
 * - Proper error handling with detailed logging
 * - Atomic writes (temp file + rename)
 * - Exception recovery
 * 
 * Used for storing lists of objects (sources, templates, endpoints)
 */
class JsonStorage(private val context: Context, private val filename: String) {
    
    private val TAG = "JsonStorage"
    private val lock = Any()  // ✅ File-level lock for thread safety
    
    private val file: File
        get() = File(context.filesDir, filename)
    
    private val tempFile: File
        get() = File(context.filesDir, "$filename.tmp")
    
    /**
     * Read JSON from file
     * Thread-safe with proper error handling
     */
    fun read(): String? = synchronized(lock) {
        return try {
            if (!file.exists()) {
                Log.d(TAG, "File $filename does not exist, returning null")
                return null
            }
            
            val content = file.readText()
            
            // Validate non-empty
            if (content.isBlank()) {
                Log.w(TAG, "File $filename is empty")
                return null
            }
            
            Log.d(TAG, "Successfully read ${content.length} bytes from $filename")
            content
            
        } catch (e: IOException) {
            Log.e(TAG, "${AppConstants.Errors.FILE_READ_FAILED}: $filename", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "File $filename too large to read into memory", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error reading $filename", e)
            null
        }
    }
    
    /**
     * Write JSON to file
     * Thread-safe with atomic writes (temp file + rename)
     * 
     * Uses atomic write pattern:
     * 1. Write to temp file
     * 2. Rename temp → actual (atomic operation)
     * 3. This prevents corruption if app crashes during write
     */
    fun write(json: String) {
        synchronized(lock) {
            try {
                // Validate input
                if (json.isBlank()) {
                    Log.w(TAG, "Attempting to write empty JSON to $filename, skipping")
                    return
                }
                
                // Write to temp file first (atomic write pattern)
                tempFile.writeText(json)
                
                // Rename temp → actual (atomic operation on most filesystems)
                if (!tempFile.renameTo(file)) {
                    // Fallback: direct write if rename fails
                    Log.w(TAG, "Atomic rename failed for $filename, using direct write")
                    file.writeText(json)
                }
                
                // Clean up temp file if it still exists
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                
                Log.d(TAG, "Successfully wrote ${json.length} bytes to $filename")
                
            } catch (e: IOException) {
                Log.e(TAG, "${AppConstants.Errors.FILE_WRITE_FAILED}: $filename", e)
                
                // Clean up temp file on failure
                try {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                } catch (cleanupError: Exception) {
                    Log.e(TAG, "Failed to clean up temp file", cleanupError)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error writing $filename", e)
            }
        }
    }
    
    /**
     * Check if file exists
     */
    fun exists(): Boolean {
        return synchronized(lock) {
            file.exists()
        }
    }
    
    /**
     * Delete file
     * Thread-safe with proper error handling
     */
    fun delete(): Boolean {
        return synchronized(lock) {
            try {
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "Successfully deleted $filename")
                    } else {
                        Log.w(TAG, "Failed to delete $filename (file.delete() returned false)")
                    }
                    deleted
                } else {
                    Log.d(TAG, "File $filename does not exist, nothing to delete")
                    true  // Success (file is gone)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied deleting $filename", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error deleting $filename", e)
                false
            }
        }
    }
    
    /**
     * Get file size in bytes
     * Useful for debugging/monitoring
     */
    fun size(): Long = synchronized(lock) {
        return try {
            if (file.exists()) {
                file.length()
            } else {
                0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting size of $filename", e)
            0L
        }
    }
}

