package com.example.alertsheets

import android.content.Context
import android.util.Log
import com.example.alertsheets.data.AppDatabase
import com.example.alertsheets.data.RequestEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object QueueProcessor {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isProcessing = AtomicBoolean(false)

    fun enqueue(context: Context, url: String, payload: String) {
        scope.launch {
            val dao = AppDatabase.getDatabase(context).requestDao()
            val entity = RequestEntity(
                url = url,
                payload = payload,
                status = "PENDING"
            )
            val id = dao.insert(entity)
            Log.d("QueueProcessor", "Enqueued request ID: $id")
            
            // Trigger processing
            processQueue(context)
        }
    }

    private fun processQueue(context: Context) {
        if (isProcessing.getAndSet(true)) return // Already running

        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).requestDao()
                
                while (true) {
                    val pending = dao.getPendingRequests()
                    if (pending.isEmpty()) break

                    for (req in pending) {
                        try {
                            Log.d("QueueProcessor", "Processing request ID: ${req.id}")
                            
                            // Log status update (Using existing LogRepository)
                            // We might want to link the DB ID to the LogEntry ID in future, 
                            // but for now we just log the attempt.

                            val success = NetworkClient.sendSynchronous(req.url, req.payload)
                            
                            if (success) {
                                Log.d("QueueProcessor", "Request ID: ${req.id} SUCCESS")
                                dao.delete(req) // Remove on success
                                
                                // Update UI Log (if possible/relevant)?
                                // We are relying on LogRepository for UI updates, which NetworkClient handled before.
                                // We need to ensure logs reflect this async nature.
                            } else {
                                Log.d("QueueProcessor", "Request ID: ${req.id} FAILED")
                                handleFailure(dao, req)
                            }
                        } catch (e: Exception) {
                            Log.e("QueueProcessor", "Error processing request ${req.id}", e)
                            handleFailure(dao, req)
                        }
                        
                        // Small delay between requests to be nice to the sheet
                        delay(200) 
                    }
                    
                    // Check again if new items arrived
                    if (dao.getPendingCount() == 0) break
                }
            } catch (e: Exception) {
                Log.e("QueueProcessor", "Fatal queue error", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private suspend fun handleFailure(dao: com.example.alertsheets.data.RequestDao, req: RequestEntity) {
        if (req.retryCount >= 10) {
            // Give up
            dao.delete(req) // Or move to a "DEAD_LETTER" table
            Log.e("QueueProcessor", "Request ${req.id} max retries reached. Dropping.")
             LogRepository.addLog(LogEntry(
                packageName = "System",
                title = "Queue Failure",
                content = "Dropped request after 10 retries",
                status = LogStatus.FAILED,
                rawJson = req.payload
            ))
        } else {
            // Backoff logic could go here
            val updated = req.copy(
                retryCount = req.retryCount + 1,
                // Make status FAILED but keep in PENDING list? 
                // Actually our query is "WHERE status = 'PENDING'".
                // If we fail, we should probably keep it PENDING unless we want to pause it.
                // For simplicity, we keep PENDING but maybe add a 'nextRetryTime' in future.
                // For now, simple retry loop.
            )
            dao.update(updated)
            // Wait a bit before retrying the same item immediately if loop continues
            delay(1000 * (req.retryCount + 1).toLong())
        }
    }
}
