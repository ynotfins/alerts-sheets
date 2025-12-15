package com.example.alertsheets.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class QueueDbHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "alert_sheets_queue.db"
        const val DATABASE_VERSION = 2
        const val TABLE_REQUESTS = "request_queue"

        const val COL_ID = "id"
        const val COL_URL = "url"
        const val COL_PAYLOAD = "payload"
        const val COL_STATUS = "status"
        const val COL_RETRY_COUNT = "retryCount"
        const val COL_CREATED_AT = "createdAt"
        const val COL_LOG_ID = "logId"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable =
                """
            CREATE TABLE $TABLE_REQUESTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_URL TEXT NOT NULL,
                $COL_PAYLOAD TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL,
                $COL_RETRY_COUNT INTEGER DEFAULT 0,
                $COL_CREATED_AT INTEGER,
                $COL_LOG_ID TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_REQUESTS")
        onCreate(db)
    }

    // --- DAO-like Methods ---

    fun insertRequest(url: String, payload: String, logId: String): Long {
        val db = writableDatabase
        val values =
                ContentValues().apply {
                    put(COL_URL, url)
                    put(COL_PAYLOAD, payload)
                    put(COL_STATUS, "PENDING")
                    put(COL_RETRY_COUNT, 0)
                    put(COL_CREATED_AT, System.currentTimeMillis())
                    put(COL_LOG_ID, logId)
                }
        return db.insert(TABLE_REQUESTS, null, values)
    }

    fun getPendingRequests(): List<RequestEntity> {
        val list = mutableListOf<RequestEntity>()
        val db = readableDatabase
        val cursor =
                db.query(
                        TABLE_REQUESTS,
                        null,
                        "$COL_STATUS = ?",
                        arrayOf("PENDING"),
                        null,
                        null,
                        "$COL_CREATED_AT ASC"
                )

        cursor.use {
            while (it.moveToNext()) {
                val logIdIdx = it.getColumnIndex(COL_LOG_ID)
                val logId = if (logIdIdx != -1) it.getString(logIdIdx) else ""

                list.add(
                        RequestEntity(
                                id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                                url = it.getString(it.getColumnIndexOrThrow(COL_URL)),
                                payload = it.getString(it.getColumnIndexOrThrow(COL_PAYLOAD)),
                                status = it.getString(it.getColumnIndexOrThrow(COL_STATUS)),
                                retryCount = it.getInt(it.getColumnIndexOrThrow(COL_RETRY_COUNT)),
                                createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CREATED_AT)),
                                logId = logId
                        )
                )
            }
        }
        return list
    }

    fun updateRequestStatus(id: Long, status: String, retryCount: Int) {
        val db = writableDatabase
        val values =
                ContentValues().apply {
                    put(COL_STATUS, status)
                    put(COL_RETRY_COUNT, retryCount)
                }
        db.update(TABLE_REQUESTS, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun deleteRequest(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_REQUESTS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getPendingCount(): Int {
        val db = readableDatabase
        val cursor =
                db.rawQuery(
                        "SELECT COUNT(*) FROM $TABLE_REQUESTS WHERE $COL_STATUS = 'PENDING'",
                        null
                )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }
}
