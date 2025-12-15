package com.example.alertsheets

import android.content.Context
import android.util.Log
import com.example.alertsheets.data.QueueDbHelper
import com.example.alertsheets.data.RequestEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object QueueProcessor {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isProcessing = AtomicBoolean(false)

    fun enqueue(context: Context, url: String, payload: String) {
        scope.launch {
            val db = QueueDbHelper(context)
            val id = db.insertRequest(url, payload)
            Log.d("QueueProcessor", "Enqueued request ID: $id")
            db.close()
            
            // Trigger processing
            processQueue(context)
        }
    }

    private fun processQueue(context: Context) {
        if (isProcessing.getAndSet(true)) return // Already running

        scope.launch {
            try {
                // Keep DbHelper open? Better to open/close or keep singleton?
                // Helper is lightweight.
                val db = QueueDbHelper(context)
                
                while (true) {
                    val pending = db.getPendingRequests()
                    if (pending.isEmpty()) break

                    for (req in pending) {
                        try {
                            Log.d("QueueProcessor", "Processing request ID: ${req.id}")

                            val success = NetworkClient.sendSynchronous(req.url, req.payload)
                            
                            if (success) {
                                Log.d("QueueProcessor", "Request ID: ${req.id} SUCCESS")
                                db.deleteRequest(req.id)
                            } else {
                                Log.d("QueueProcessor", "Request ID: ${req.id} FAILED")
                                handleFailure(db, req)
                            }
                        } catch (e: Exception) {
                            Log.e("QueueProcessor", "Error processing request ${req.id}", e)
                            handleFailure(db, req)
                        }
                        
                        // Small delay between requests to be nice to the sheet
                        delay(200) 
                    }
                    
                    // Check again if new items arrived
                    if (db.getPendingCount() == 0) break
                }
                db.close()
            } catch (e: Exception) {
                Log.e("QueueProcessor", "Fatal queue error", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private suspend fun handleFailure(db: QueueDbHelper, req: RequestEntity) {
        if (req.retryCount >= 10) {
            // Give up
            db.deleteRequest(req.id) 
            Log.e("QueueProcessor", "Request ${req.id} max retries reached. Dropping.")
             LogRepository.addLog(LogEntry(
                packageName = "System",
                title = "Queue Failure",
                content = "Dropped request after 10 retries",
                status = LogStatus.FAILED,
                rawJson = req.payload
            ))
        } else {
            // Update retry count
            db.updateRequestStatus(req.id, "PENDING", req.retryCount + 1)
            // Wait a bit before retrying
            delay(1000 * (req.retryCount + 1).toLong())
        }
    }
}
