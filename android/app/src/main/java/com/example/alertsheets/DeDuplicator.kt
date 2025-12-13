package com.example.alertsheets

import android.util.LruCache

object DeDuplicator {
    // Cache of recent message hashes to prevent processing the same message from multiple sources 
    // (Accessibility + NotificationListener)
    private val cache = LruCache<String, Long>(100)
    private const val DEBOUNCE_TIME_MS = 2000 // 2 seconds

    fun shouldProcess(content: String): Boolean {
        val hash = content.hashCode().toString()
        val now = System.currentTimeMillis()
        
        val lastSeen = cache.get(hash)
        if (lastSeen != null) {
            if (now - lastSeen < DEBOUNCE_TIME_MS) {
                // Too soon, ignore duplicate
                return false
            }
        }
        
        cache.put(hash, now)
        return true
    }
}
