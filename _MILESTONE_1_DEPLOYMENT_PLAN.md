# 🚀 MILESTONE 1: DEPLOYMENT PLAN - DUAL-WRITE WITH KILL SWITCH

**Status:** ⏸️ **BLOCKED** - Waiting for all 4 harness tests to pass  
**Date:** 2025-12-23

---

## ⚠️ **PREREQUISITE GATE**

**ALL 4 TESTS MUST PASS BEFORE PROCEEDING:**

- [ ] Test 1: Happy Path - PASS
- [ ] Test 2: Network Outage - PASS
- [ ] Test 3: Crash Recovery - PASS
- [ ] Test 4: Deduplication - PASS

**DO NOT** integrate into DataPipeline until all tests pass consistently.

---

## 🎯 **INTEGRATION STRATEGY: DUAL-WRITE**

### **Concept**

```
Existing Flow (UNTOUCHED):
NotificationListener → DataPipeline → NetworkClient → Sheets URL

New Flow (ADDED):
NotificationListener → DataPipeline → NetworkClient → Sheets URL
                                   └→ IngestQueue → Cloud Function → Firestore
```

**Hard Requirements:**
1. ✅ **Failures in new path NEVER block existing delivery**
2. ✅ **Kill switch can disable new path without code changes**
3. ✅ **Both paths log independently**
4. ✅ **Existing path takes priority (runs first)**

---

## 🔧 **KILL SWITCH IMPLEMENTATION**

### **Option 1: SharedPreferences Toggle (Recommended)**

**Pros:**
- ✅ No network dependency
- ✅ Instant toggle
- ✅ Survives app restarts
- ✅ Can be controlled via debug menu or remote config

**Implementation:**

**File:** `android/app/src/main/java/com/example/alertsheets/config/FeatureFlags.kt`

```kotlin
package com.example.alertsheets.config

import android.content.Context
import android.content.SharedPreferences

object FeatureFlags {
    private const val PREFS_NAME = "feature_flags"
    private const val KEY_INGEST_ENABLED = "ingest_pipeline_enabled"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Check if the new ingestion pipeline is enabled.
     * Default: false (safe default - disabled until proven stable)
     */
    fun isIngestPipelineEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_INGEST_ENABLED, false)
    }
    
    /**
     * Enable the new ingestion pipeline.
     * Call this after all tests pass and you're ready for dual-write.
     */
    fun enableIngestPipeline(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_INGEST_ENABLED, true).apply()
    }
    
    /**
     * KILL SWITCH: Disable the new ingestion pipeline immediately.
     * Use this if bugs are discovered in production.
     */
    fun disableIngestPipeline(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_INGEST_ENABLED, false).apply()
    }
}
```

### **Option 2: Firebase Remote Config (Advanced)**

**Pros:**
- ✅ No app update required
- ✅ Can rollout to % of users (staged rollout)
- ✅ Can toggle instantly from Firebase Console

**Cons:**
- ❌ Requires network (but cached)
- ❌ More complex setup

**Implementation:** (Optional - use if needed)

```kotlin
// Add dependency to build.gradle:
// implementation 'com.google.firebase:firebase-config-ktx'

val remoteConfig = Firebase.remoteConfig
remoteConfig.setConfigSettingsAsync(
    remoteConfigSettings {
        minimumFetchIntervalInSeconds = 3600 // 1 hour
    }
)

// Set defaults
remoteConfig.setDefaultsAsync(mapOf(
    "ingest_pipeline_enabled" to false
))

// Fetch and activate
remoteConfig.fetchAndActivate()
    .addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val enabled = remoteConfig.getBoolean("ingest_pipeline_enabled")
            Log.i("FeatureFlags", "Ingest pipeline enabled: $enabled")
        }
    }
```

---

## 🔀 **DATAPIPELINE INTEGRATION**

### **File:** `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`

**Current Structure:**
```kotlin
class DataPipeline(private val context: Context) {
    
    suspend fun processNotification(notification: RawNotification) {
        // Existing flow
        val parsedData = parser.parse(notification)
        val payload = templateEngine.render(parsedData)
        networkClient.post(endpoint.url, payload) // Sheets URL
    }
}
```

**New Structure (Dual-Write):**
```kotlin
class DataPipeline(private val context: Context) {
    
    private val ingestQueue by lazy { IngestQueue(context) }
    
    suspend fun processNotification(notification: RawNotification) {
        // EXISTING PATH (PRIORITY #1 - MUST SUCCEED)
        try {
            val parsedData = parser.parse(notification)
            val payload = templateEngine.render(parsedData)
            networkClient.post(endpoint.url, payload) // Sheets URL
            
            logRepository.log(
                status = LogStatus.SUCCESS,
                message = "Delivered to Sheets: ${endpoint.url}",
                sourceId = source.id
            )
        } catch (e: Exception) {
            logRepository.log(
                status = LogStatus.ERROR,
                message = "Sheets delivery failed: ${e.message}",
                sourceId = source.id
            )
            // CRITICAL: Don't rethrow - existing path must complete
        }
        
        // NEW PATH (OPTIONAL - KILL SWITCH CONTROLLED)
        if (FeatureFlags.isIngestPipelineEnabled(context)) {
            try {
                val eventId = ingestQueue.enqueue(
                    sourceId = source.id,
                    payload = payload, // Same payload as Sheets
                    timestamp = System.currentTimeMillis().toString()
                )
                
                ingestQueue.processQueue() // Trigger async processing
                
                logRepository.log(
                    status = LogStatus.INFO,
                    message = "Enqueued for ingestion: $eventId",
                    sourceId = source.id
                )
            } catch (e: Exception) {
                // NEW PATH FAILURE MUST NOT BLOCK EXISTING PATH
                logRepository.log(
                    status = LogStatus.WARNING,
                    message = "Ingest enqueue failed (non-blocking): ${e.message}",
                    sourceId = source.id
                )
                // Don't rethrow - new path is optional
            }
        }
    }
}
```

**Key Safety Features:**
1. ✅ Existing path runs first and completes
2. ✅ New path wrapped in try-catch (failures isolated)
3. ✅ Kill switch checked before new path
4. ✅ Independent logging for both paths
5. ✅ No exceptions thrown from new path

---

## 📊 **LOGGING STRATEGY**

### **Log Levels by Path**

| Path | Success | Failure | Level |
|------|---------|---------|-------|
| **Existing (Sheets)** | `SUCCESS: Delivered to Sheets` | `ERROR: Sheets delivery failed` | ERROR |
| **New (Ingest)** | `INFO: Enqueued for ingestion` | `WARNING: Ingest enqueue failed (non-blocking)` | WARNING |

### **Expected Logs (Dual-Write Enabled)**

**Happy Path:**
```
LogRepository: ✅ SUCCESS: Delivered to Sheets: https://...
LogRepository: ℹ️ INFO: Enqueued for ingestion: abc-123-...
IngestQueue: 📥 Enqueued: abc-123-... (sourceId: my-source)
IngestQueue: ✅ Ingested: abc-123-...
```

**Sheets Fails, Ingest Succeeds:**
```
LogRepository: ❌ ERROR: Sheets delivery failed: Network timeout
LogRepository: ℹ️ INFO: Enqueued for ingestion: def-456-...
IngestQueue: ✅ Ingested: def-456-...
```

**Sheets Succeeds, Ingest Fails:**
```
LogRepository: ✅ SUCCESS: Delivered to Sheets: https://...
LogRepository: ⚠️ WARNING: Ingest enqueue failed (non-blocking): Auth error
```

**Ingest Disabled (Kill Switch):**
```
LogRepository: ✅ SUCCESS: Delivered to Sheets: https://...
(No ingest logs - feature disabled)
```

---

## 🧪 **POST-INTEGRATION TESTING**

### **Test Suite**

**Test 1: Both Paths Succeed**
1. Enable kill switch: `FeatureFlags.enableIngestPipeline(context)`
2. Trigger notification
3. Verify:
   - ✅ Sheets URL receives payload
   - ✅ Firestore receives event
   - ✅ Activity logs show both successes

**Test 2: Sheets Fails, Ingest Succeeds**
1. Set invalid Sheets URL
2. Trigger notification
3. Verify:
   - ❌ Sheets error logged
   - ✅ Firestore receives event
   - ✅ No app crash

**Test 3: Sheets Succeeds, Ingest Fails**
1. Disable Firebase Auth
2. Trigger notification
3. Verify:
   - ✅ Sheets URL receives payload
   - ❌ Ingest warning logged (non-blocking)
   - ✅ No app crash

**Test 4: Kill Switch OFF**
1. Disable kill switch: `FeatureFlags.disableIngestPipeline(context)`
2. Trigger notification
3. Verify:
   - ✅ Sheets URL receives payload
   - ❌ No ingest logs
   - ✅ Firestore does NOT receive event

**Test 5: Kill Switch Toggle**
1. Enable kill switch
2. Trigger notification (should enqueue)
3. Disable kill switch
4. Trigger notification (should NOT enqueue)
5. Verify logs show different behavior

---

## 🚨 **ROLLBACK PLAN**

### **Emergency Rollback (Instant)**

```kotlin
// Option 1: Kill switch (no deploy)
FeatureFlags.disableIngestPipeline(context)
// New path stops immediately, existing path unaffected

// Option 2: Debug menu toggle
// Add to LabActivity or PermissionsActivity:
Button("Disable Ingest Pipeline") {
    FeatureFlags.disableIngestPipeline(context)
    Toast.makeText(context, "Ingest pipeline disabled", Toast.LENGTH_SHORT).show()
}
```

### **Code Rollback (if kill switch fails)**

```kotlin
// Remove dual-write code from DataPipeline.kt
// Keep only existing Sheets delivery path
// Deploy new APK
```

### **Verification After Rollback**

```bash
# Check logs - should see no ingest activity
adb logcat | grep "Enqueued for ingestion"
# Expected: No results

# Check Firestore - no new events
# Firebase Console → Firestore → events collection
# Expected: No new documents after rollback time
```

---

## 📋 **DEPLOYMENT CHECKLIST**

### **Pre-Deployment**

- [ ] All 4 harness tests pass
- [ ] Test logs saved and reviewed
- [ ] `FeatureFlags.kt` added to project
- [ ] DataPipeline integration code reviewed
- [ ] Kill switch tested in debug builds
- [ ] Rollback plan documented
- [ ] Team notified of deployment

### **Deployment Steps**

1. [ ] Merge dual-write code to main branch
2. [ ] Build release APK: `./gradlew :app:assembleRelease`
3. [ ] Install on test device
4. [ ] Verify kill switch is OFF by default
5. [ ] Test existing Sheets delivery (should work)
6. [ ] Enable kill switch: `FeatureFlags.enableIngestPipeline(context)`
7. [ ] Test dual-write (both Sheets + Firestore)
8. [ ] Monitor logs for 24 hours
9. [ ] Check Firestore for new events
10. [ ] If issues: disable kill switch immediately

### **Post-Deployment Monitoring**

**Day 1 (First 24 hours):**
- [ ] Monitor logcat for errors every 4 hours
- [ ] Check Firestore event count vs expected
- [ ] Verify Sheets delivery still works
- [ ] Test kill switch toggle

**Week 1:**
- [ ] Daily log review
- [ ] Compare Sheets vs Firestore event counts
- [ ] Monitor for any data loss reports
- [ ] Performance check (app responsiveness)

**Month 1:**
- [ ] Weekly Firestore data audit
- [ ] Confirm no silent failures
- [ ] Evaluate staged rollout to 100% of sources

---

## 📊 **SUCCESS CRITERIA**

**Deployment is successful if:**

✅ **Zero Data Loss**
- All events delivered to Sheets (existing path)
- New events enqueued to Firestore (new path)
- No events lost due to new path failures

✅ **Isolation**
- New path failures don't block existing delivery
- Kill switch works instantly
- Rollback is clean

✅ **Observability**
- All paths logged independently
- Queue stats visible
- Firestore events queryable

✅ **Performance**
- No noticeable latency increase
- App remains responsive
- Battery impact negligible

---

## 🚀 **FUTURE PHASES (POST-DUAL-WRITE)**

### **Phase 2: Server-Side Fanout**
- Move fanout logic to Cloud Function
- Client sends once, server delivers to multiple endpoints
- Reduces client battery/network usage

### **Phase 3: Delivery Receipts**
- Cloud Function confirms delivery per endpoint
- Client queries for delivery status
- Retry only failed endpoints

### **Phase 4: Replace Existing Path**
- Once new path proven stable (3+ months)
- Remove direct Sheets delivery from client
- All delivery via Firestore ingestion

---

**Status:** ⏸️ **BLOCKED**  
**Blocker:** Waiting for all 4 harness tests to pass  
**Next:** Run on-device tests and capture logs  
**After Tests Pass:** Implement FeatureFlags + DataPipeline dual-write


