# MILESTONE 1: E2E TEST HARNESS RUNBOOK

**Purpose:** Validate ingestion pipeline WITHOUT touching DataPipeline  
**Location:** `IngestTestActivity.kt` (test harness UI)  
**Date:** 2025-12-23

---

## 🚀 **SETUP (ONE-TIME)**

### **1. Deploy Server Components**
```bash
cd functions

# Install dependencies
npm install

# Build TypeScript
npm run build

# Deploy Firestore Security Rules
firebase deploy --only firestore:rules

# Deploy Cloud Functions
firebase deploy --only functions

# Note the /ingest endpoint URL
# Should output: https://us-central1-YOUR-PROJECT.cloudfunctions.net/ingest
```

### **2. Update Client Configuration**
Edit `android/app/src/main/java/com/example/alertsheets/data/IngestQueue.kt`:
```kotlin
// Line 24: Replace YOUR-PROJECT-ID with actual Firebase project ID
private const val INGEST_ENDPOINT = "https://us-central1-YOUR-PROJECT-ID.cloudfunctions.net/ingest"
```

### **3. Enable Firebase Auth (if not already)**
- Firebase Console → Authentication → Get Started
- Enable Email/Password provider (or any provider)
- Create test user OR sign in via app

### **4. Add Test Activity to AndroidManifest.xml**
```xml
<activity
    android:name=".ui.IngestTestActivity"
    android:label="Milestone 1 Tests"
    android:exported="false" />
```

### **5. Add Button to Main Dashboard**
Add a "Test Ingestion" button to `MainActivity.kt` that launches `IngestTestActivity`.

---

## 🧪 **TEST SUITE (4 CRITICAL TESTS)**

### **TEST 1: HAPPY PATH** ✅

**Purpose:** Verify basic flow works end-to-end

**Steps:**
1. Open IngestTestActivity
2. Ensure network is ON
3. Ensure Firebase Auth is valid (check logs)
4. Click "TEST 1: Happy Path"
5. Observe logs

**Expected Behavior:**
```
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] 🧪 TEST 1: HAPPY PATH
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] 📥 Generated test event:
[HH:MM:SS]    UUID: 550e8400-e29b-41d4-a716-446655440000
[HH:MM:SS]    SourceId: test-harness
[HH:MM:SS] ✅ Enqueued to local SQLite (UUID: 550e8400...)
[HH:MM:SS] 🚀 Triggering queue processor...
[HH:MM:SS] ⏳ Queue stats: 1 pending
[HH:MM:SS] ⏳ Queue stats: 0 pending
[HH:MM:SS] ✅ Queue empty - event processed!
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] ✅ TEST 1: PASS
[HH:MM:SS]    Event ingested to Firestore
[HH:MM:SS]    Check Firestore console for document: 550e8400...
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Verification:**
1. ✅ Logs show "TEST 1: PASS"
2. ✅ Queue stats show 0 pending
3. ✅ Firestore Console → `events` collection → Document with UUID exists
4. ✅ Document fields: `uuid`, `sourceId`, `payload`, `timestamp`, `ingestedAt`

**Pass Criteria:**
- Event appears in Firestore within 10 seconds
- Queue empties after processing
- No error messages in logs

**Fail Criteria:**
- Timeout after 30 seconds
- Network errors (check connectivity)
- Auth errors (check Firebase user signed in)
- 400/401/500 errors (check server deployment)

---

### **TEST 2: NETWORK OUTAGE** ⚠️

**Purpose:** Verify retry logic works during network disruption

**Steps:**
1. Open IngestTestActivity
2. Click "TEST 2: Network Outage"
3. **IMMEDIATELY enable Airplane Mode** (Settings → Airplane Mode ON)
4. Observe logs showing retry attempts
5. After ~30 seconds, **disable Airplane Mode**
6. Observe eventual success

**Expected Behavior (Airplane Mode ON):**
```
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] 🧪 TEST 2: NETWORK OUTAGE
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] ⚠️ MANUAL STEP REQUIRED:
[HH:MM:SS]    1. Enable AIRPLANE MODE now
[HH:MM:SS]    ...
[HH:MM:SS] 📥 Generated test event: abc-123
[HH:MM:SS] ✅ Enqueued to SQLite: abc-123
[HH:MM:SS] 🚀 Triggering queue processor...
[HH:MM:SS]    (Will retry with backoff: 1s, 2s, 4s, 8s, 16s, 32s, 60s)
[HH:MM:SS] ⏳ Monitoring queue...
[HH:MM:SS]    Pending: 1, Oldest: 5s
[HH:MM:SS]    Pending: 1, Oldest: 10s
[HH:MM:SS]    Pending: 1, Oldest: 15s
...
```

**Expected Behavior (Airplane Mode OFF):**
```
[HH:MM:SS]    Pending: 1, Oldest: 45s
[HH:MM:SS] ✅ Event delivered after network restored!
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] ✅ TEST 2: PASS
[HH:MM:SS]    Retry logic working correctly
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Verification:**
1. ✅ Event stays in queue while network is down
2. ✅ "Oldest" age increases over time
3. ✅ Event delivers within 10 seconds after network restored
4. ✅ Firestore document appears

**Pass Criteria:**
- Event persists in queue during network outage
- Event delivers after network restoration
- No data loss

**Fail Criteria:**
- Event disappears from queue during outage (data loss!)
- Event doesn't deliver after network restored

---

### **TEST 3: CRASH RECOVERY** 🔥

**Purpose:** Verify events survive app crashes

**Steps (Part 1 - Setup Crash):**
1. Open IngestTestActivity
2. Click "TEST 3: Crash Recovery"
3. Logs will show: "Enqueueing test event for crash recovery test..."
4. Wait for confirmation: "Event enqueued: xyz-789"
5. **IMMEDIATELY:** Settings → Apps → AlertsToSheets → Force Stop
6. App is now killed

**Steps (Part 2 - Verify Recovery):**
7. Relaunch app
8. Navigate to IngestTestActivity
9. Click "TEST 3: Crash Recovery" again
10. Observe logs

**Expected Behavior (After Relaunch):**
```
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] 🧪 TEST 3: CRASH RECOVERY
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] ✅ CRASH RECOVERY DETECTED!
[HH:MM:SS]    Found 1 pending event(s) from previous session
[HH:MM:SS]    Oldest event: 45s ago
[HH:MM:SS] 🚀 Resuming queue processor...
[HH:MM:SS]    Pending: 1
[HH:MM:SS]    Pending: 0
[HH:MM:SS] ✅ All recovered events processed!
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] ✅ TEST 3: PASS
[HH:MM:SS]    Crash recovery working correctly
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Verification:**
1. ✅ Event found in queue after app restart
2. ✅ Event processes successfully
3. ✅ Firestore document appears
4. ✅ Queue empties

**Pass Criteria:**
- Event persists across app crash
- Event processes after relaunch
- No data loss

**Fail Criteria:**
- Event disappears after crash (DATA LOSS!)
- Event stuck in queue forever

---

### **TEST 4: DEDUPLICATION** 🔁

**Purpose:** Verify idempotency (same UUID twice → one record)

**Steps:**
1. Open IngestTestActivity
2. Ensure network is ON
3. Click "TEST 4: Deduplication"
4. Observe logs (test sends same UUID twice)
5. Check Firestore Console

**Expected Behavior:**
```
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] 🧪 TEST 4: DEDUPLICATION
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] 📥 Generated test event: def-456
[HH:MM:SS] 🚀 SEND #1...
[HH:MM:SS] ✅ Enqueued #1: def-456
[HH:MM:SS] 🚀 SEND #2 (SAME UUID)...
[HH:MM:SS]    ⚠️ SQLite should reject duplicate (CONFLICT_IGNORE)
[HH:MM:SS]    UUID #2: def-456 (should be same as #1)
[HH:MM:SS] ✅ Client-side dedup: SQLite rejected duplicate
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[HH:MM:SS] ✅ TEST 4: PASS (CLIENT-SIDE)
[HH:MM:SS]    Check Firestore console:
[HH:MM:SS]    - Should have EXACTLY ONE document: def-456
[HH:MM:SS]    - isDuplicate field should be true on 2nd attempt
[HH:MM:SS] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Verification:**
1. ✅ Logs show "Client-side dedup: SQLite rejected duplicate"
2. ✅ Firestore Console → `events` collection → EXACTLY ONE document with UUID `def-456`
3. ✅ If server receives duplicate POST, response has `isDuplicate: true`

**Pass Criteria:**
- Only ONE document in Firestore (not two)
- SQLite prevents duplicate enqueue
- Server responds with isDuplicate=true if duplicate POST received

**Fail Criteria:**
- TWO documents in Firestore (idempotency broken!)
- SQLite allows duplicate UUIDs

---

## 📊 **PASS/FAIL SUMMARY TABLE**

| Test | Pass Criteria | Fail Criteria | Time Limit |
|------|---------------|---------------|------------|
| **Test 1: Happy Path** | Event in Firestore <10s | Timeout >30s, errors | 30s |
| **Test 2: Network Outage** | Event survives outage, delivers after restore | Event lost during outage | 2min |
| **Test 3: Crash Recovery** | Event survives crash, delivers after relaunch | Event lost after crash | N/A |
| **Test 4: Deduplication** | ONE document in Firestore | TWO+ documents | 15s |

---

## 🐛 **TROUBLESHOOTING**

### **Issue: "User not authenticated" error**
**Solution:** Ensure Firebase Auth is enabled and user is signed in
```kotlin
// Check in logs:
[HH:MM:SS] Firebase Auth: ⚠️ NOT AUTHENTICATED
// Fix: Sign in via app or Firebase Console
```

### **Issue: "Network error: Failed to connect"**
**Solution:** Check `INGEST_ENDPOINT` URL is correct
```kotlin
// IngestQueue.kt line 24
private const val INGEST_ENDPOINT = "https://us-central1-YOUR-PROJECT.cloudfunctions.net/ingest"
// Verify: Should match your Firebase project region + ID
```

### **Issue: "HTTP 401 Unauthorized"**
**Solution:** Firebase Auth token expired or invalid
```kotlin
// Logout and login again to refresh token
FirebaseAuth.getInstance().signOut()
// Then sign in again
```

### **Issue: "HTTP 500 Internal Server Error"**
**Solution:** Check Cloud Function logs
```bash
firebase functions:log
# Look for errors in /ingest function
```

### **Issue: Queue stats never update**
**Solution:** Check IngestQueue initialization
```kotlin
// Ensure IngestQueue was created:
private lateinit var ingestQueue: IngestQueue
ingestQueue = IngestQueue(applicationContext)
```

---

## ✅ **ALL TESTS PASS CRITERIA**

**Before proceeding to DataPipeline integration, ALL 4 tests must pass:**

- ✅ **Test 1 (Happy Path):** Events ingest successfully
- ✅ **Test 2 (Network Outage):** Events survive network failures
- ✅ **Test 3 (Crash Recovery):** Events survive app crashes
- ✅ **Test 4 (Deduplication):** No duplicate records in Firestore

**Only after all tests pass → Proceed to dual-write integration**

---

## 🚀 **NEXT STEP AFTER TESTS PASS**

See: `_MILESTONE_1_INTEGRATION_PLAN.md` (to be created after tests pass)

**DO NOT** integrate into DataPipeline until all 4 tests pass consistently.

---

**Status:** 📝 **RUNBOOK COMPLETE**  
**Ready For:** Deployment + Testing

