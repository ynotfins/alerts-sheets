package com.example.alertsheets.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface RequestDao {
    @Insert
    suspend fun insert(request: RequestEntity): Long

    @Query("SELECT * FROM request_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingRequests(): List<RequestEntity>

    @Update
    suspend fun update(request: RequestEntity)

    @Delete
    suspend fun delete(request: RequestEntity)

    @Query("SELECT COUNT(*) FROM request_queue WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int
}
