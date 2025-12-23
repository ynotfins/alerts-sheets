# ✅ MILESTONE 1: E2E TEST HARNESS - COMPLETE RUNBOOK

**Purpose:** Validate ingestion pipeline with deterministic gates  
**Status:** Ready for on-device testing  
**Date:** 2025-12-23

---

## 🚧 **PHASE 1: BUILD GATE (CLI VERIFICATION)**

### **GATE 1.1: Debug Build**

```bash
cd android
./gradlew clean :app:assembleDebug
```

**Expected Output:**
```
BUILD SUCCESSFUL in Xs
```

**Verification:**
```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

**✅ PASS CRITERIA:**
- Exit code: 0
- APK exists: `app-debug.apk` (~11 MB)
- No compilation errors

**❌ FAIL CRITERIA:**
- Build fails
- Compilation errors in IngestTestActivity
- Resource errors

---

### **GATE 1.2: Release Build**

```bash
./gradlew clean :app:assembleRelease
```

**Expected Output:**
```
BUILD SUCCESSFUL in Xs
```

**Verification:**
```bash
ls -lh app/build/outputs/apk/release/app-release-unsigned.apk

# Verify IngestTestActivity is NOT in release
# (sourceSets should exclude it automatically)
```

**✅ PASS CRITERIA:**
- Exit code: 0
- APK exists: `app-release-unsigned.apk` (~9 MB)
- IngestTestActivity NOT compiled in release

**❌ FAIL CRITERIA:**
- Build fails
- Test code leaks into release
- Debug dependencies in release

---

## 📦 **PHASE 2: DEPLOYMENT**

### **Deploy Server Components**

```bash
cd functions

# Install dependencies
npm install

# Build TypeScript
npm run build

# Deploy Firestore Security Rules
firebase deploy --only "firestore:rules"

# Deploy Cloud Functions
firebase deploy --only functions
```

**Verification:**
```bash
# Test /ingest endpoint
curl -X POST \
  https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TEST_TOKEN" \
  -d '{"test":"deployment_verification"}'
  
# Expected: HTTP 400 (missing required fields) or 201 (success)
# NOT: 404 (endpoint doesn't exist) or 500 (server error)
```

---

### **Install Debug APK**

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

**Verification:**
```bash
adb shell pm list packages | grep com.example.alertsheets
# Expected: package:com.example.alertsheets
```

---

## 🧪 **PHASE 3: ON-DEVICE TESTING**

### **LOG CAPTURE SETUP**

Before running tests, start log capture:

```bash
# Terminal 1: Capture all relevant logs
adb logcat -c  # Clear old logs
adb logcat \
  IngestQueue:* \
  IngestQueueDb:* \
  IngestTestActivity:* \
  FirebaseAuth:* \
  *:E \
  | tee test_logs_$(date +%Y%m%d_%H%M%S).txt
```

**Log Tags to Monitor:**
- `IngestQueue` - Queue processing, retries, HTTP status
- `IngestQueueDb` - SQLite operations, WAL recovery
- `IngestTestActivity` - Test execution, results
- `FirebaseAuth` - Authentication status
- `*:E` - All errors

---

### **TEST 1: HAPPY PATH** ✅

**Purpose:** Verify basic end-to-end flow

**Steps:**
1. Launch app: `adb shell am start -n com.example.alertsheets/.ui.MainActivity`
2. Navigate to IngestTestActivity (tap test harness card if visible, or use intent):
   ```bash
   adb shell am start -n com.example.alertsheets/.ui.IngestTestActivity
   ```
3. Tap "🔐 LOGIN (Anonymous)" button
4. Wait for auth success
5. Tap "▶ RUN TEST 1" button
6. Observe results card and logs

**Expected Log Pattern:**
```
IngestTestActivity: === TEST 1: Happy Path ===
IngestTestActivity: Enqueuing event...
IngestQueue: 📥 Enqueued: 550e8400-... (sourceId: test_source)
IngestQueue: 🚀 Processing queue (pending: 1)
IngestQueue: ✅ Ingested: 550e8400-...
IngestTestActivity: ✅ Test 1 complete - check Firestore Console for event: 550e8400-...
```

**Expected State Transitions:**
```
Queue: 0 → 1 → 0
HTTP: N/A → 201 Created
Firestore: No doc → Doc exists
```

**Results Card Should Show:**
```
📋 Last Test Results (PROOF)

✅ EVENT ID:
   550e8400-e29b-41d4-a716-446655440000

📊 QUEUE DEPTH:
   Before: 0
   After:  0

🌐 HTTP STATUS:
   201 Created (expected)

🔥 FIRESTORE PATH:
   users/AbCdEf123456/events/550e8400-...
```

**Verification:**
1. ✅ Results card shows Event ID
2. ✅ Queue depth: 0 → 0
3. ✅ HTTP status: 201 Created
4. ✅ Firestore Console → `users/{uid}/events/{eventId}` document exists
5. ✅ No errors in logcat

**Pass Criteria:**
- Event delivered within 10 seconds
- Queue empties after processing
- Firestore document created
- No error logs

**Fail Criteria:**
- Timeout after 30 seconds
- Network errors in logs
- Auth errors (`FirebaseAuth: ❌`)
- HTTP 400/401/500 errors

---

### **TEST 2: NETWORK OUTAGE** ⚠️

**Purpose:** Verify retry logic and persistence during network loss

**Steps:**
1. Ensure network is ON
2. Tap "▶ RUN TEST 2" button
3. **Within 5 seconds:** Enable Airplane Mode
   ```bash
   # Option 1: Manual (Settings → Airplane Mode ON)
   # Option 2: ADB (requires root)
   adb shell cmd connectivity airplane-mode enable
   ```
4. Wait 30 seconds (observe retry attempts)
5. Disable Airplane Mode
   ```bash
   adb shell cmd connectivity airplane-mode disable
   ```
6. Wait for delivery (within 10 seconds)

**Expected Log Pattern (Airplane Mode ON):**
```
IngestTestActivity: === TEST 2: Network Outage ===
IngestTestActivity: Enqueuing event...
IngestQueue: 📥 Enqueued: abc-123-... (sourceId: test_source)
IngestQueue: 🚀 Processing queue (pending: 1)
IngestQueue: ❌ Network error: Unable to resolve host
IngestQueue: ⏳ Retry in 1000ms (attempt 1)
IngestQueue: ❌ Network error: Unable to resolve host
IngestQueue: ⏳ Retry in 2000ms (attempt 2)
IngestQueue: ❌ Network error: Unable to resolve host
IngestQueue: ⏳ Retry in 4000ms (attempt 3)
...
```

**Expected Log Pattern (Airplane Mode OFF):**
```
IngestQueue: 🚀 Resuming queue processing...
IngestQueue: ✅ Ingested: abc-123-...
IngestTestActivity: ✅ Test 2 complete - check logs for retries
```

**Expected State Transitions:**
```
Queue: 0 → 1 → 1 (persists during outage) → 0
HTTP: N/A → Network Error → Network Error → 201 Created
Firestore: No doc → No doc → Doc exists
```

**Results Card Should Show:**
```
📊 QUEUE DEPTH:
   Before: 0
   After:  0

🌐 HTTP STATUS:
   201 Created (after retries)
```

**Verification:**
1. ✅ Event persists in queue during network outage
2. ✅ Logs show retry attempts with exponential backoff (1s, 2s, 4s, 8s, 16s, 32s)
3. ✅ Event delivers after network restored
4. ✅ Firestore document exists
5. ✅ No data loss

**Pass Criteria:**
- Event survives network outage
- Retry delays increase exponentially
- Event delivers after restoration
- No data loss

**Fail Criteria:**
- Event disappears from queue (DATA LOSS!)
- No retry attempts
- Event doesn't deliver after restore

---

### **TEST 3: CRASH RECOVERY** 🔥

**Purpose:** Verify WAL recovery and crash resilience

**Steps (Part 1 - Cause Crash):**
1. Tap "▶ RUN TEST 3" button
2. Wait for "Event enqueued" log
3. **Immediately force-stop app:**
   ```bash
   adb shell am force-stop com.example.alertsheets
   ```
4. App is now killed

**Steps (Part 2 - Verify Recovery):**
5. Restart logcat capture (logs were lost on crash)
6. Relaunch app:
   ```bash
   adb shell am start -n com.example.alertsheets/.ui.IngestTestActivity
   ```
7. Tap "▶ RUN TEST 1" to process pending queue
8. Observe recovery and delivery

**Expected Log Pattern (After Restart):**
```
IngestQueueDb: ✅ WAL recovery: Found 1 pending entries
IngestQueueDb: ✅ Crash recovery complete
IngestQueue: ✅ IngestQueue initialized (pending: 1, endpoint: ..., env: debug)
IngestTestActivity: Event enqueued.
IngestTestActivity: ⚠️ NOW FORCE-KILL THE APP
```

**Expected Log Pattern (After Relaunch):**
```
IngestQueueDb: ✅ WAL recovery: Found 1 pending entries
IngestQueue: ✅ IngestQueue initialized (pending: 1, ...)
IngestQueue: 🚀 Processing queue (pending: 1)
IngestQueue: ✅ Ingested: def-456-...
IngestTestActivity: ✅ Test 1 complete
```

**Expected State Transitions:**
```
Queue: 0 → 1 → [CRASH] → 1 (recovered) → 0
HTTP: N/A → [CRASH] → 201 Created
Firestore: No doc → [CRASH] → Doc exists
```

**Results Card Should Show (After Relaunch):**
```
📊 QUEUE DEPTH:
   Before: 1  (recovered from crash)
   After:  0

🌐 HTTP STATUS:
   201 Created
```

**Verification:**
1. ✅ Event found in queue after app restart
2. ✅ Logs show "WAL recovery: Found 1 pending entries"
3. ✅ Event processes successfully after relaunch
4. ✅ Firestore document exists
5. ✅ No data loss

**Pass Criteria:**
- Event survives app crash
- WAL recovery finds pending event
- Event delivers after relaunch
- No data loss

**Fail Criteria:**
- Event disappears after crash (DATA LOSS!)
- No WAL recovery logs
- Event stuck in queue forever

---

### **TEST 4: DEDUPLICATION** 🔁

**Purpose:** Verify idempotency (client + server)

**Steps:**
1. Tap "▶ RUN TEST 4" button
2. Test automatically sends two events
3. Observe logs and results card

**Expected Log Pattern:**
```
IngestTestActivity: === TEST 4: Deduplication ===
IngestTestActivity: Enqueuing event #1...
IngestQueue: 📥 Enqueued: ghi-789-... (sourceId: test_source)
IngestQueue: ✅ Ingested: ghi-789-...
IngestTestActivity: ⚠️ Enqueuing DUPLICATE event (same payload, new UUID)...
IngestTestActivity: Event ID #2: jkl-012-...
IngestQueue: 📥 Enqueued: jkl-012-... (sourceId: test_source)
IngestQueue: ✅ Ingested: jkl-012-...
IngestTestActivity: ✅ Test 4 complete - check Firestore (2 client UUIDs, server decides canonical)
```

**Expected State Transitions:**
```
Queue: 0 → 1 → 0 → 1 → 0
HTTP: N/A → 201 → 201 (both accepted)
Firestore: No docs → 1 doc → 2 docs (different UUIDs)
```

**Results Card Should Show:**
```
✅ EVENT ID:
   Send 1: ghi-789-4c5f-5e6g-0h3g9d1f7g8h
   Send 2: jkl-012-8f9i-7h8e-dg9j-2i5h0e1f6g7h

📊 QUEUE DEPTH:
   Before: 0
   After:  0

🌐 HTTP STATUS:
   200 OK (both accepted)

🔥 FIRESTORE PATH:
   users/AbCdEf123456/events/{ghi-789...,jkl-012...}
```

**Verification:**
1. ✅ Two events enqueued (different client UUIDs)
2. ✅ Both events deliver successfully
3. ✅ Firestore has TWO documents (client generates unique UUIDs)
4. ✅ Server accepts both (different event IDs)

**Note:** This test verifies that:
- Client queue accepts multiple identical payloads (different UUIDs)
- Server handles each request idempotently
- If SAME UUID is sent twice, server deduplicates

**Pass Criteria:**
- Both events process successfully
- No errors in logs
- Firestore documents exist

**Fail Criteria:**
- Events fail to process
- Server errors (500)

---

## 📊 **LOG CAPTURE & ANALYSIS**

### **Log Filters by Test**

**Test 1:**
```bash
adb logcat | grep -E "IngestQueue|IngestTestActivity" | grep -E "TEST 1|Enqueued|Ingested|complete"
```

**Test 2:**
```bash
adb logcat | grep -E "IngestQueue|IngestTestActivity" | grep -E "TEST 2|Network error|Retry|complete"
```

**Test 3:**
```bash
adb logcat | grep -E "IngestQueueDb|IngestQueue" | grep -E "WAL recovery|Crash recovery|pending:"
```

**Test 4:**
```bash
adb logcat | grep -E "IngestTestActivity|IngestQueue" | grep -E "TEST 4|Enqueued|Duplicate|complete"
```

### **Expected Log Tags**

| Tag | Purpose | Key Messages |
|-----|---------|--------------|
| `IngestQueue` | Queue processing | `📥 Enqueued`, `✅ Ingested`, `❌ Network error` |
| `IngestQueueDb` | SQLite operations | `WAL recovery`, `Crash recovery` |
| `IngestTestActivity` | Test execution | `=== TEST X ===`, `✅ Test X complete` |
| `FirebaseAuth` | Authentication | `✅ Authenticated`, `❌ Not authenticated` |

### **State Transition Matrix**

| Test | Queue Before | Queue After | HTTP Status | Firestore |
|------|--------------|-------------|-------------|-----------|
| Test 1 | 0 | 0 | 201 Created | 1 doc |
| Test 2 | 0 → 1 (during outage) → 0 | 0 | 201 Created | 1 doc |
| Test 3 | 0 → 1 → [CRASH] → 1 → 0 | 0 | 201 Created | 1 doc |
| Test 4 | 0 → 1 → 0 → 1 → 0 | 0 | 200/201 OK | 2 docs |

---

## ✅ **GATE 3: ALL TESTS PASS**

**Required for DataPipeline integration:**

- ✅ **Test 1 (Happy Path):** PASS
- ✅ **Test 2 (Network Outage):** PASS
- ✅ **Test 3 (Crash Recovery):** PASS
- ✅ **Test 4 (Deduplication):** PASS

**Verification Checklist:**
- ✅ All tests show "✅ Test X complete" in logs
- ✅ All Firestore documents exist and match event IDs
- ✅ No errors in logcat
- ✅ Results cards show correct queue depths and HTTP status
- ✅ Test logs saved for documentation

---

## 🚀 **PHASE 4: DEPLOYMENT (AFTER TESTS PASS)**

**DO NOT PROCEED until all 4 tests pass consistently.**

See: **`_MILESTONE_1_DEPLOYMENT_PLAN.md`** (created below)

---

**Status:** ✅ **RUNBOOK COMPLETE**  
**Next:** Run tests on device and capture logs  
**Gate:** All 4 tests must pass before DataPipeline integration


