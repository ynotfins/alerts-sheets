package com.example.alertsheets.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Provides authentication tokens for HTTP requests
 * 
 * SECURITY:
 * - Tokens are NEVER logged
 * - Anonymous auth used as fallback
 * - Tokens refreshed automatically by Firebase SDK
 */
object AuthTokenProvider {
    
    private val TAG = "AuthTokenProvider"
    private val auth = FirebaseAuth.getInstance()
    
    /**
     * Get Firebase ID token for Authorization header
     * 
     * Flow:
     * 1. Check if user is signed in
     * 2. If not, sign in anonymously
     * 3. Get ID token (forceRefresh=false for performance)
     * 4. Return token or null on failure
     * 
     * @return Firebase ID token or null if auth fails
     */
    suspend fun getFirebaseIdToken(): String? {
        return try {
            // Step 1: Ensure user is signed in
            var user = auth.currentUser
            
            if (user == null) {
                Log.d(TAG, "No user signed in, attempting anonymous auth")
                val result = auth.signInAnonymously().await()
                user = result.user
                
                if (user == null) {
                    Log.e(TAG, "Anonymous sign-in failed: user is null")
                    return null
                }
                
                Log.d(TAG, "Anonymous auth successful: ${user.uid}")
            }
            
            // Step 2: Get ID token (not forcing refresh for performance)
            val tokenResult = user.getIdToken(false).await()
            val token = tokenResult.token
            
            if (token == null) {
                Log.e(TAG, "Token is null after getIdToken call")
                return null
            }
            
            // ✅ Token obtained successfully
            // DO NOT log token - security risk
            Log.d(TAG, "Token obtained successfully (length=${token.length})")
            return token
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Firebase ID token: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Check if user is signed in (for debugging)
     */
    fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }
    
    /**
     * Get masked UID for logging (safe for logs)
     */
    fun getMaskedUid(): String {
        val uid = auth.currentUser?.uid ?: return "none"
        return if (uid.length >= 4) {
            "${uid.take(2)}***${uid.takeLast(2)}"
        } else {
            "***"
        }
    }
}

