package com.example.alertsheets.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Enhanced Queue Database Helper - Milestone 1
 * 
 * Features:
 * - Write-Ahead Logging (WAL mode) for crash recovery
 * - UUID-based deduplication
 * - Retry tracking with timestamps
 * - Automatic cleanup of old entries
 * - Crash recovery on app restart
 * 
 * This is the PRIMARY durability mechanism for ensuring zero data loss.
 */
class IngestQueueDb(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "IngestQueueDb"
        const val DATABASE_NAME = "alerts_ingestion_queue.db"
        const val DATABASE_VERSION = 1
        const val TABLE_QUEUE = "ingestion_queue"

        // Columns
        const val COL_UUID = "uuid"                  // Primary key
        const val COL_SOURCE_ID = "sourceId"         // Which source
        const val COL_PAYLOAD = "payload"            // Final JSON
        const val COL_TIMESTAMP = "timestamp"        // Capture time (ISO 8601)
        const val COL_DEVICE_ID = "deviceId"         // Device identifier
        const val COL_APP_VERSION = "appVersion"     // App version
        const val COL_STATUS = "status"              // PENDING, SENT, FAILED
        const val COL_RETRY_COUNT = "retryCount"     // How many times retried
        const val COL_LAST_ATTEMPT_AT = "lastAttemptAt"  // Last retry timestamp
        const val COL_CREATED_AT = "createdAt"       // When queued (ms since epoch)
        const val COL_ERROR_MESSAGE = "errorMessage" // Last error (if any)
        
        // Constants
        const val MAX_AGE_DAYS = 7                   // Delete entries older than 7 days
        const val MAX_RETRY_COUNT = 1000             // Effectively unlimited (client-side)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Enable WAL mode for better crash recovery
        db.execSQL("PRAGMA journal_mode=WAL")
        
        val createTable = """
            CREATE TABLE $TABLE_QUEUE (
                $COL_UUID TEXT PRIMARY KEY,
                $COL_SOURCE_ID TEXT NOT NULL,
                $COL_PAYLOAD TEXT NOT NULL,
                $COL_TIMESTAMP TEXT NOT NULL,
                $COL_DEVICE_ID TEXT,
                $COL_APP_VERSION TEXT,
                $COL_STATUS TEXT NOT NULL DEFAULT 'PENDING',
                $COL_RETRY_COUNT INTEGER DEFAULT 0,
                $COL_LAST_ATTEMPT_AT INTEGER,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_ERROR_MESSAGE TEXT
            )
        """.trimIndent()
        
        db.execSQL(createTable)
        
        // Create index on status for fast pending queries
        db.execSQL("CREATE INDEX idx_status ON $TABLE_QUEUE($COL_STATUS)")
        
        // Create index on createdAt for cleanup queries
        db.execSQL("CREATE INDEX idx_created_at ON $TABLE_QUEUE($COL_CREATED_AT)")
        
        Log.i(TAG, "✅ Database created with WAL mode enabled")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Upgrading database from version $oldVersion to $newVersion")
        // For now, just recreate (in production, use migrations)
        db.execSQL("DROP TABLE IF EXISTS $TABLE_QUEUE")
        onCreate(db)
    }
    
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Enable WAL mode (also done in onCreate for safety)
        db.execSQL("PRAGMA journal_mode=WAL")
    }

    // ============================================================================
    // WRITE OPERATIONS
    // ============================================================================

    /**
     * Enqueue event for ingestion
     * 
     * @param uuid Client-generated UUID (must be unique)
     * @param sourceId Which source captured this event
     * @param payload Final JSON payload (rendered template)
     * @param timestamp Capture timestamp (ISO 8601)
     * @param deviceId Device identifier
     * @param appVersion App version
     * @return true if inserted, false if duplicate UUID
     */
    fun enqueue(
        uuid: String,
        sourceId: String,
        payload: String,
        timestamp: String,
        deviceId: String = "unknown",
        appVersion: String = "unknown"
    ): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_UUID, uuid)
                put(COL_SOURCE_ID, sourceId)
                put(COL_PAYLOAD, payload)
                put(COL_TIMESTAMP, timestamp)
                put(COL_DEVICE_ID, deviceId)
                put(COL_APP_VERSION, appVersion)
                put(COL_STATUS, "PENDING")
                put(COL_RETRY_COUNT, 0)
                put(COL_CREATED_AT, System.currentTimeMillis())
            }
            
            val rowId = db.insertWithOnConflict(
                TABLE_QUEUE,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE // Ignore if UUID already exists
            )
            
            if (rowId == -1L) {
                Log.w(TAG, "⚠️ Duplicate UUID ignored: $uuid")
                false
            } else {
                Log.d(TAG, "✅ Enqueued event: $uuid (sourceId: $sourceId)")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to enqueue event: $uuid", e)
            false
        }
    }

    /**
     * Mark event as successfully sent
     * 
     * @param uuid Event UUID
     */
    fun markSent(uuid: String) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_STATUS, "SENT")
            }
            val updated = db.update(TABLE_QUEUE, values, "$COL_UUID = ?", arrayOf(uuid))
            if (updated > 0) {
                Log.d(TAG, "✅ Marked as SENT: $uuid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to mark as sent: $uuid", e)
        }
    }

    /**
     * Increment retry count and update last attempt timestamp
     * 
     * @param uuid Event UUID
     * @param errorMessage Error message (optional)
     */
    fun incrementRetry(uuid: String, errorMessage: String? = null) {
        try {
            val db = writableDatabase
            
            // Get current retry count
            val cursor = db.query(
                TABLE_QUEUE,
                arrayOf(COL_RETRY_COUNT),
                "$COL_UUID = ?",
                arrayOf(uuid),
                null,
                null,
                null
            )
            
            val newRetryCount = cursor.use {
                if (it.moveToFirst()) {
                    it.getInt(it.getColumnIndexOrThrow(COL_RETRY_COUNT)) + 1
                } else {
                    1
                }
            }
            
            val values = ContentValues().apply {
                put(COL_RETRY_COUNT, newRetryCount)
                put(COL_LAST_ATTEMPT_AT, System.currentTimeMillis())
                if (errorMessage != null) {
                    put(COL_ERROR_MESSAGE, errorMessage)
                }
            }
            
            db.update(TABLE_QUEUE, values, "$COL_UUID = ?", arrayOf(uuid))
            Log.d(TAG, "⚠️ Retry #$newRetryCount for $uuid${if (errorMessage != null) ": $errorMessage" else ""}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to increment retry: $uuid", e)
        }
    }

    /**
     * Delete event from queue (after successful ACK from server)
     * 
     * @param uuid Event UUID
     */
    fun delete(uuid: String) {
        try {
            val db = writableDatabase
            val deleted = db.delete(TABLE_QUEUE, "$COL_UUID = ?", arrayOf(uuid))
            if (deleted > 0) {
                Log.d(TAG, "🗑️ Deleted from queue: $uuid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete: $uuid", e)
        }
    }

    // ============================================================================
    // READ OPERATIONS
    // ============================================================================

    /**
     * Get all pending events (ordered by creation time, oldest first)
     * 
     * @return List of pending events
     */
    fun getPendingEvents(): List<IngestQueueEntry> {
        val list = mutableListOf<IngestQueueEntry>()
        
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_QUEUE,
                null,
                "$COL_STATUS = ?",
                arrayOf("PENDING"),
                null,
                null,
                "$COL_CREATED_AT ASC" // Oldest first (FIFO)
            )

            cursor.use {
                while (it.moveToNext()) {
                    list.add(IngestQueueEntry(
                        uuid = it.getString(it.getColumnIndexOrThrow(COL_UUID)),
                        sourceId = it.getString(it.getColumnIndexOrThrow(COL_SOURCE_ID)),
                        payload = it.getString(it.getColumnIndexOrThrow(COL_PAYLOAD)),
                        timestamp = it.getString(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                        deviceId = it.getString(it.getColumnIndexOrThrow(COL_DEVICE_ID)) ?: "unknown",
                        appVersion = it.getString(it.getColumnIndexOrThrow(COL_APP_VERSION)) ?: "unknown",
                        status = it.getString(it.getColumnIndexOrThrow(COL_STATUS)),
                        retryCount = it.getInt(it.getColumnIndexOrThrow(COL_RETRY_COUNT)),
                        lastAttemptAt = it.getLongOrNull(it.getColumnIndexOrThrow(COL_LAST_ATTEMPT_AT)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CREATED_AT)),
                        errorMessage = it.getStringOrNull(it.getColumnIndexOrThrow(COL_ERROR_MESSAGE))
                    ))
                }
            }
            
            Log.d(TAG, "📋 Retrieved ${list.size} pending events")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get pending events", e)
        }
        
        return list
    }

    /**
     * Get count of pending events
     * 
     * @return Number of pending events
     */
    fun getPendingCount(): Int {
        return try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_QUEUE WHERE $COL_STATUS = 'PENDING'",
                null
            )
            cursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get pending count", e)
            0
        }
    }

    /**
     * Get age of oldest pending event (seconds)
     * 
     * @return Seconds since oldest event was queued, or null if no pending events
     */
    fun getOldestEventAge(): Long? {
        return try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT MIN($COL_CREATED_AT) FROM $TABLE_QUEUE WHERE $COL_STATUS = 'PENDING'",
                null
            )
            cursor.use {
                if (it.moveToFirst() && !it.isNull(0)) {
                    val oldestTime = it.getLong(0)
                    (System.currentTimeMillis() - oldestTime) / 1000 // Convert to seconds
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get oldest event age", e)
            null
        }
    }

    // ============================================================================
    // MAINTENANCE OPERATIONS
    // ============================================================================

    /**
     * Clean up old entries (older than MAX_AGE_DAYS)
     * Should be called on app start for housekeeping
     * 
     * @return Number of entries deleted
     */
    fun cleanupOldEntries(): Int {
        return try {
            val db = writableDatabase
            val cutoffTime = System.currentTimeMillis() - (MAX_AGE_DAYS * 24 * 60 * 60 * 1000L)
            val deleted = db.delete(
                TABLE_QUEUE,
                "$COL_CREATED_AT < ?",
                arrayOf(cutoffTime.toString())
            )
            if (deleted > 0) {
                Log.i(TAG, "🧹 Cleaned up $deleted old entries (>$MAX_AGE_DAYS days)")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cleanup old entries", e)
            0
        }
    }

    /**
     * Recover from crash: Mark all "in-flight" events back to PENDING
     * Call this on app start
     */
    fun recoverFromCrash() {
        try {
            val db = writableDatabase
            // Any events that were being processed when app crashed
            // should be marked back to PENDING for retry
            // (In current design, status is only PENDING or SENT, so this is a no-op,
            //  but keeping for future "PROCESSING" state)
            val updated = db.rawQuery(
                "UPDATE $TABLE_QUEUE SET $COL_STATUS = 'PENDING' WHERE $COL_STATUS NOT IN ('PENDING', 'SENT')",
                null
            )
            updated.close()
            Log.i(TAG, "✅ Crash recovery complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed crash recovery", e)
        }
    }

    // ============================================================================
    // HELPER EXTENSIONS
    // ============================================================================

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (isNull(columnIndex)) null else getLong(columnIndex)
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (isNull(columnIndex)) null else getString(columnIndex)
    }
}

/**
 * Data class representing a queued event
 */
data class IngestQueueEntry(
    val uuid: String,
    val sourceId: String,
    val payload: String,
    val timestamp: String,
    val deviceId: String,
    val appVersion: String,
    val status: String,
    val retryCount: Int,
    val lastAttemptAt: Long?,
    val createdAt: Long,
    val errorMessage: String?
)

