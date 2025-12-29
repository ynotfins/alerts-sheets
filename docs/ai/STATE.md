# PROJECT STATE - Single Source of Truth

**Last Updated:** 2025-12-29 00:35 UTC (Session 7: End-to-End Shared Secret Auth)  
**Branch:** `fix/wiring-sources-endpoints`  
**Status:** 🟡 Auth Implemented - Secret Configuration Required (Both Android + Cloud Functions)

---

## 🎯 SESSION 7 SUMMARY: Complete Shared Secret Auth

### What Was Done
✅ **Android:** Shared secret loaded from `local.properties` → added to Authorization header for ingest URLs
✅ **Cloud Functions:** Replaced Firebase ID token validation with shared secret validation
✅ **Evidence Capture:** Documented current config state (sources pointing to wrong endpoints)
✅ **Build Verification:** Both Android APK and Cloud Functions build successfully

### Critical Discovery from Phase 0 Evidence
**Problem:** SMS sources configured to point to **disabled** Apps Script endpoint!
- SMS source `+1 888-660-1455`: → Apps Script endpoint (disabled)
- SMS source `+1 561-419-3784`: → Apps Script endpoint (disabled)
- BNN app source: → Firestore ingest endpoint (enabled)
- **Result:** SMS not being delivered because endpoint is disabled

### What's Needed Before Testing
1. ✅ Code complete (Android + Cloud Functions)
2. ⏳ **Configure `INGEST_SHARED_SECRET` in android/local.properties**
3. ⏳ **Configure `INGEST_SHARED_SECRET` in functions/.env.local** (or Firebase config)
4. ⏳ **Deploy updated Cloud Functions**
5. ⏳ **Fix SMS source endpoint assignments** (point to Firestore ingest)

---

## 📊 PHASE 0 EVIDENCE (Captured 2025-12-29 00:30 UTC)

### App Files Directory Listing
```bash
$ adb shell run-as com.example.alertsheets ls -la files

total 46
drwxrwx--x 6 u0_a696 u0_a696 3452 2025-12-28 23:52 .
drwx------ 7 u0_a696 u0_a696 3452 2025-12-24 13:29 ..
-rw------- 1 u0_a696 u0_a696  751 2025-12-28 17:45 endpoints.json
-rw------- 1 u0_a696 u0_a696 1239 2025-12-28 23:52 logs.json
-rw------- 1 u0_a696 u0_a696 2690 2025-12-28 20:51 sources.json
drwx------ 2 u0_a696 u0_a696 3452 2025-12-29 00:29 datastore
```

### Current Source Configuration
```json
[
  {
    "id": "sms:+1 888-660-1455",
    "name": "SMS Adjust Leads Firestore Database",
    "type": "SMS",
    "enabled": true,
    "endpointIds": ["b0040999-d963-4b57-aaf4-f6d2301bad72"],  // ❌ Apps Script (disabled!)
    "templateJson": "{ ... }"
  },
  {
    "id": "us.bnn.newsapp",
    "name": "BNN",
    "type": "APP",
    "enabled": true,
    "endpointIds": ["endpoint-1766609309063"],  // ✅ Firestore ingest (enabled)
    "parserId": "bnn"
  },
  {
    "id": "sms:+1 561-419-3784",
    "name": "SMS 3784",
    "type": "SMS",
    "enabled": true,
    "endpointIds": ["b0040999-d963-4b57-aaf4-f6d2301bad72"],  // ❌ Apps Script (disabled!)
    "templateJson": "{ ... }"
  }
]
```

**Analysis:**
- ❌ **2 SMS sources** pointing to disabled Apps Script endpoint
- ✅ **1 APP source** (BNN) pointing to enabled Firestore ingest
- **Consequence:** Real SMS will match source but fail delivery (disabled endpoint)

### Current Endpoint Configuration
```json
[
  {
    "id": "b0040999-d963-4b57-aaf4-f6d2301bad72",
    "name": "Google Apps Script",
    "enabled": false,  // ❌ DISABLED
    "url": "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec",
    "stats": {
      "totalRequests": 0,
      "totalSuccess": 0,
      "totalFailed": 0
    }
  },
  {
    "id": "endpoint-1766609309063",
    "name": "Firestore Ingest Function",
    "enabled": true,  // ✅ ENABLED
    "url": "https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest",
    "stats": {
      "totalRequests": 18,
      "totalSuccess": 0,
      "totalFailed": 18,  // ❌ All 18 requests failed (401 auth errors)
      "avgResponseTime": 1747,
      "lastActivity": 1766961951364
    }
  }
]
```

**Analysis:**
- Firestore ingest: **18 requests, 0 success, 18 failed**
- All failures due to 401 authentication errors (before shared secret implementation)
- Apps Script endpoint disabled (no recent activity)

---

## 📦 FILES CHANGED (Session 7)

### Android (2 files - Already Complete from Session 6)
1. **`android/app/build.gradle`** (+6 lines)
   - Added `INGEST_SHARED_SECRET` BuildConfig field
   - Loads from `local.properties` or environment variable

2. **`android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt`** (~35 lines)
   - URL-based detection for ingest endpoints
   - Adds `Authorization: Bearer <secret>` header automatically
   - Logs secret length (never value)
   - Fails early with `auth_missing` event if secret not configured

### Cloud Functions (1 file - NEW)
3. **`functions/src/index.ts`** (~30 lines changed)
   - **Replaced Firebase ID token validation with shared secret validation**
   - Checks `Authorization: Bearer <secret>` header
   - Validates against `process.env.INGEST_SHARED_SECRET` or `BNN_SHARED_SECRET`
   - Returns clear error messages:
     - 401: "Missing Authorization header" (if no header)
     - 401: "Invalid secret" (if wrong secret)
     - 500: "Server configuration error" (if secret not configured on server)
   - Removed `userId` references (no longer using Firebase Auth)

---

## 🔧 REQUIRED CONFIGURATION

### Step 1: Generate Shared Secret

```bash
# Generate a secure random secret (32 bytes = 64 hex characters)
openssl rand -hex 32

# Example output:
# a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2

# Copy this value - you'll need it for both Android and Cloud Functions
```

### Step 2: Configure Android

**Edit `android/local.properties`:**
```properties
# SDK path (existing)
sdk.dir=C:\\Users\\ynotf\\AppData\\Local\\Android\\Sdk

# Sentry DSN (existing, optional)
sentryDsn=https://your-key@o0.ingest.sentry.io/0

# ✅ ADD THIS LINE:
INGEST_SHARED_SECRET=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2
```

**Important:**
- Use the SAME secret value generated in Step 1
- `local.properties` is gitignored (safe for secrets)
- Must rebuild Android app after adding secret

### Step 3: Configure Cloud Functions

**Option A: Using `.env.local` (Recommended for local testing)**

Create/edit `functions/.env.local`:
```bash
# Firestore Ingest Shared Secret
# Must match INGEST_SHARED_SECRET in android/local.properties
INGEST_SHARED_SECRET=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2

# OR use legacy name (code checks both):
BNN_SHARED_SECRET=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2
```

**Option B: Using Firebase Functions Config (For production deployment)**

```bash
cd functions

# Set secret in Firebase
firebase functions:config:set ingest.shared_secret="a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2"

# Verify it was set
firebase functions:config:get

# Deploy
firebase deploy --only functions
```

**Note:** Cloud Functions code checks both `INGEST_SHARED_SECRET` and `BNN_SHARED_SECRET` env vars (backward compatibility).

### Step 4: Deploy Cloud Functions

```bash
cd D:\github\alerts-sheets\functions

# Build
npm run build

# Deploy
firebase deploy --only functions:ingest

# Expected output:
# ✔  Deploy complete!
# Function URL (ingest(us-central1)): https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest
```

### Step 5: Rebuild Android

```bash
cd D:\github\alerts-sheets\android

# Clean rebuild (ensures BuildConfig updated)
.\gradlew.bat clean :app:assembleDebug

# Install
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

---

## 🧪 VERIFICATION CHECKLIST

### Pre-Flight Checks
```bash
# ✅ Verify Android secret configured
notepad android\local.properties
# Should contain: INGEST_SHARED_SECRET=<your-secret>

# ✅ Verify Cloud Functions secret configured
# If using .env.local:
notepad functions\.env.local
# Should contain: INGEST_SHARED_SECRET=<same-secret>

# If using Firebase config:
firebase functions:config:get
# Should show: ingest.shared_secret: "<your-secret>"

# ✅ Verify secrets MATCH (critical!)
# Android local.properties and Cloud Functions env must have identical values
```

### Test 1: Lab Dirty Test (Without SMS)
```bash
# Clear logs
adb logcat -c

# Start monitoring
adb logcat -v time -s DeliveryPipeline:V ReliableHttpSender:V StructuredLogger:V *:E

# In app:
# 1. Open AppConfigActivity (from main screen)
# 2. Tap "🔥 Dirty Test (Emoji SMS)" button

# Expected logcat output:
# DeliveryPipeline: ✓ Authorization header added for ingest endpoint (secret length=64)
# DeliveryPipeline: 📤 Sending to: Firestore Ingest Function...
# ReliableHttpSender: POST https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest
# DeliveryPipeline: ✅ HTTP OK | code=200 latency=XXXms

# Expected dialog:
# Title: "Test Result"
# Content: "✓ Test SUCCESS\nHTTP 200 (XXXms)\nStatus: ✓ CONFIRMED\n\nResponse preview:\n{\"status\":\"ok\",...}"

# ❌ If you see:
# - "secret length=0" → Secret not configured in local.properties
# - "code=401" → Secret mismatch OR Cloud Function not deployed
# - "auth_missing" event → Secret empty in BuildConfig
```

### Test 2: Fix SMS Source Routing
**Problem:** SMS sources currently point to disabled Apps Script endpoint

**Fix in App UI:**
```
1. Open MainActivity
2. Tap "Sources" tile
3. Select "SMS Adjust Leads" source
4. Tap "Endpoints" section
5. Uncheck "Google Apps Script" (disabled)
6. Check "Firestore Ingest Function" (enabled)
7. Tap "Save"

Repeat for other SMS sources.
```

**OR manually edit via adb (faster):**
```bash
# Download current config
adb shell run-as com.example.alertsheets cat files/sources.json > sources_backup.json

# Edit sources.json:
# Change all "endpointIds": ["b0040999-..."] 
# To: "endpointIds": ["endpoint-1766609309063"]

# Push back
adb push sources_edited.json /sdcard/sources.json
adb shell run-as com.example.alertsheets cp /sdcard/sources.json files/sources.json

# Restart app
adb shell am force-stop com.example.alertsheets
adb shell am start -n com.example.alertsheets/.ui.MainActivity
```

### Test 3: Real SMS Delivery
```bash
# Prerequisites:
# - SMS source pointing to Firestore ingest endpoint (see Test 2)
# - Shared secret configured in both Android and Cloud Functions
# - Cloud Functions deployed

# Clear logs
adb logcat -c

# Start monitoring
adb logcat -v time -s DeliveryPipeline:V ReliableHttpSender:V StructuredLogger:V *:E

# Send SMS from configured number (e.g., +1 888-660-1455)

# Expected logcat output:
# DeliveryPipeline: 📨 SMS event | sender=***55 message_len=XX
# DeliveryPipeline: ✓ Source matched: SMS Adjust Leads Firestore Database
# DeliveryPipeline: ✓ Endpoint selected: Firestore Ingest Function
# DeliveryPipeline: ✓ Authorization header added for ingest endpoint (secret length=64)
# DeliveryPipeline: 📤 Sending to: Firestore Ingest Function (https://us-central1-...)
# ReliableHttpSender: POST (with Authorization header)
# DeliveryPipeline: ✅ HTTP OK | code=200 latency=XXXms
# StructuredLogger: {"event":"http_ok","code":200,...}

# Check Firestore Console:
# https://console.firebase.google.com/project/alerts-sheets-bb09c/firestore/data
# Expected: New document in /alerts collection with SMS data
```

### Test 4: Apps Script Path (Verify Unchanged)
```bash
# If you have Apps Script endpoint enabled and configured:

# Monitor logs
adb logcat -c
adb logcat -v time -s DeliveryPipeline:V

# Send notification/SMS routed to Apps Script endpoint

# Expected logcat output:
# DeliveryPipeline: Endpoint does not require auth (Apps Script or other)
# DeliveryPipeline: 📤 Sending to: Google Apps Script...
# [NO Authorization header added]
# DeliveryPipeline: ✅ HTTP OK | code=200

# Verify: No "Authorization header added" log
```

---

## 🚨 TROUBLESHOOTING

### Issue 1: Still Getting 401 Errors

**Symptoms:**
```
DeliveryPipeline: ❌ HTTP FAIL | code=401
Response: "Unauthorized. Invalid secret."
```

**Root Causes:**
1. **Secret mismatch** - Android and Cloud Functions using different values
2. **Cloud Functions not deployed** - Still running old code
3. **Typo in secret** - Extra spaces, wrong case, etc.

**Debug Steps:**
```bash
# A) Verify Android secret
adb logcat -d -s DeliveryPipeline:* | Select-String "secret length"
# Expected: "secret length=64" (or whatever your secret length is)
# If you see "secret length=0" → Not configured in local.properties

# B) Verify Cloud Functions deployed
firebase functions:log --only ingest --limit 10
# Look for "✅ Authentication successful" logs

# C) Test Cloud Function directly with curl
curl -X POST https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest \
  -H "Authorization: Bearer YOUR_SECRET_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "uuid": "test-' + (new Date()).getTime() + '",
    "sourceId": "test",
    "payload": "{\"test\":true}",
    "timestamp": "' + (new Date()).toISOString() + '"
  }'

# Expected: {"status":"ok",...}
# If 401: Secret wrong or not configured on server
```

**Fix:**
1. Verify secrets match EXACTLY (copy-paste, don't retype)
2. Redeploy Cloud Functions: `firebase deploy --only functions:ingest`
3. Rebuild Android: `.\gradlew.bat clean assembleDebug`

---

### Issue 2: auth_missing Event

**Symptoms:**
```
DeliveryPipeline: ❌ INGEST_SHARED_SECRET is empty!
StructuredLogger: {"event":"auth_missing",...}
```

**Root Cause:**
Secret not configured in `android/local.properties`

**Fix:**
```bash
# 1. Add secret
notepad android\local.properties
# Add line: INGEST_SHARED_SECRET=your-secret-here

# 2. Clean rebuild (REQUIRED - BuildConfig needs regeneration)
cd android
.\gradlew.bat clean :app:assembleDebug

# 3. Reinstall
adb install -r .\app\build\outputs\apk\debug\app-debug.apk

# 4. Verify
adb logcat -c
adb logcat -s DeliveryPipeline:*
# Should now show: "secret length=XX"
```

---

### Issue 3: SMS Not Delivering (Endpoint Disabled)

**Symptoms:**
- SMS arrives on device
- No delivery logs appear
- OR logs show "endpoint disabled"

**Root Cause:**
SMS source pointing to disabled Apps Script endpoint (see Phase 0 Evidence)

**Fix:**
See **Test 2: Fix SMS Source Routing** above

---

### Issue 4: Server Configuration Error (500)

**Symptoms:**
```
DeliveryPipeline: ❌ HTTP FAIL | code=500
Response: "Server configuration error"
```

**Root Cause:**
Cloud Function deployed without `INGEST_SHARED_SECRET` env var

**Fix:**
```bash
cd functions

# Option A: Set in Firebase config
firebase functions:config:set ingest.shared_secret="your-secret-here"
firebase deploy --only functions:ingest

# Option B: Use .env.local for local emulator
echo "INGEST_SHARED_SECRET=your-secret-here" > .env.local
firebase emulators:start --only functions
```

---

## 📋 CURRENT STATUS SUMMARY

### ✅ Complete
- [x] Android shared secret implementation
- [x] Cloud Functions shared secret validation
- [x] Build verification (both Android and Cloud Functions)
- [x] Documentation complete
- [x] Troubleshooting guide

### ⏳ Pending User Action
- [ ] Generate shared secret (`openssl rand -hex 32`)
- [ ] Add secret to `android/local.properties`
- [ ] Add secret to `functions/.env.local` OR Firebase config
- [ ] Deploy Cloud Functions (`firebase deploy --only functions:ingest`)
- [ ] Rebuild Android (`gradlew clean assembleDebug`)
- [ ] Fix SMS source endpoint assignments (point to Firestore ingest)

### 🎯 Expected After Configuration
- [ ] Lab Dirty Test → HTTP 200 (not 401)
- [ ] Real SMS → HTTP 200, document in Firestore
- [ ] Apps Script path unchanged (no auth header)
- [ ] Debug screen shows successful deliveries
- [ ] Firestore console shows new documents

---

## 📞 RESOURCES

- **Firebase Console:** https://console.firebase.google.com/project/alerts-sheets-bb09c
- **Firestore Data:** https://console.firebase.google.com/project/alerts-sheets-bb09c/firestore/data
- **Cloud Functions Logs:** `firebase functions:log --only ingest`
- **Workspace:** `D:\github\alerts-sheets`
- **Device:** R5CX20WL15P (Samsung, Android SDK 34)

---

## 📝 NEXT SESSION PRIORITIES

1. **P0:** Configure shared secret (generate + add to both Android and Cloud Functions)
2. **P0:** Deploy Cloud Functions with new auth
3. **P0:** Fix SMS source routing (point to Firestore ingest endpoint)
4. **P1:** Test end-to-end (SMS → Firestore)
5. **P2:** Implement Lab test button fixes (show real response dialog)
6. **P2:** Add "Copy Build Info" + "Export last 10 logs" features

---

**Status:** Code complete, waiting for secret configuration + deployment.
