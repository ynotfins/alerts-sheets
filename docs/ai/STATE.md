# PROJECT STATE - Single Source of Truth

**Last Updated:** 2025-12-28 (Session 5 FINAL: URL-Based Auth Detection)  
**Branch:** `fix/wiring-sources-endpoints`  
**Status:** 🟡 Auth Implementation Complete - Firebase Console Configuration Required

---

## 🚀 SESSION 5 FINAL CHANGES: URL-Based Auth Detection

### Implementation Summary
✅ **Automatic auth detection** - No manual configuration required
✅ **URL-based matching** - Checks endpoint URL and name for "cloudfunctions.net/ingest" or "Firestore Ingest"
✅ **Apps Script protected** - Only Firebase Cloud Functions get auth headers
✅ **No token logging** - Security preserved
✅ **Masked phone numbers** - SMS sources show ***XX in UI

### Key Change from Previous Approach
- ❌ **REMOVED:** `authType` field requirement (was too manual)
- ✅ **ADDED:** Automatic URL/name-based detection
- ✅ **RESULT:** Zero configuration needed - auth added automatically for Firestore endpoints

---

## 📦 FILES CHANGED (Session 5 Final)

### Modified Files (3):

1. **android/app/src/main/java/com/example/alertsheets/AlertsApplication.kt**
   - Enhanced Firebase Auth initialization logging
   - Logs: `auth_ready=true/false`, `uid_present=true/false`, `uid_masked=XXX***XXX`
   - Anonymous sign-in on app start if no user

2. **android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt**
   - **Changed auth condition from `authType` field to URL/name detection:**
     ```kotlin
     val needsAuth = endpoint.url.contains("cloudfunctions.net/ingest", ignoreCase = true) ||
                    endpoint.name.contains("Firestore Ingest", ignoreCase = true)
     ```
   - **Real SMS path (deliverSmsEvent):** Checks URL, adds Authorization header automatically
   - **Test button path (deliverTestEventWithAuth):** Same URL-based logic
   - **Fail early if token missing:** Logs `auth_missing` event and aborts request

3. **android/app/src/main/java/com/example/alertsheets/SmsSourceAdapter.kt**
   - Masks phone numbers: `***67` instead of full number
   - Contact names still displayed in full

### Unchanged from Session 5 (Already Implemented):
- `AuthTokenProvider.kt` - Token retrieval logic
- `Endpoint.kt` - AuthType enum (still exists but not used)
- `AppConfigActivity.kt` - Test button wiring
- `ReliableHttpSender.kt` - Response body capture

---

## ⚠️ FIREBASE CONFIGURATION ISSUE DETECTED

### Current Status
```bash
# Logcat output from app start:
12-28 21:48:46.345 I AlertsApp: 🔐 Firebase Auth: No user found, signing in anonymously...
12-28 21:48:46.651 E AlertsApp: ❌ Firebase Auth: auth_ready=false error=An internal error has occurred. [ CONFIGURATION_NOT_FOUND ]
```

### Root Cause
Firebase Auth is **not enabled** in Firebase Console OR `google-services.json` is misconfigured.

### Fix Required (User Action)
1. **Enable Firebase Authentication:**
   - Go to: https://console.firebase.google.com/project/alerts-sheets-bb09c/authentication
   - Enable "Anonymous" sign-in method
   - Click "Save"

2. **Verify google-services.json:**
   ```bash
   # Check if file exists
   ls android/app/google-services.json
   
   # Verify project_id matches
   cat android/app/google-services.json | findstr "project_id"
   # Should show: "project_id": "alerts-sheets-bb09c"
   ```

3. **Rebuild after enabling Auth:**
   ```bash
   cd android
   .\gradlew.bat clean
   .\gradlew.bat :app:assembleDebug
   adb install -r .\app\build\outputs\apk\debug\app-debug.apk
   ```

---

## ✅ BUILD EVIDENCE (Session 5 Final)

```bash
# Command
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug

# Output
> Task :app:compileDebugKotlin
> Task :app:packageDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL in 2s
42 actionable tasks: 8 executed, 34 up-to-date

# APK
D:\github\alerts-sheets\android\app\build\outputs\apk\debug\app-debug.apk

# Installed
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
Performing Streamed Install
Success

# Launched
adb shell am force-stop com.example.alertsheets
adb shell am start -n com.example.alertsheets/.ui.MainActivity
Starting: Intent { cmp=com.example.alertsheets/.ui.MainActivity }
```

---

## 🧪 VERIFICATION STEPS (After Firebase Auth Enabled)

### Step A: Confirm Auth Initialization
```bash
# Clear logs and restart app
adb logcat -c
adb shell am force-stop com.example.alertsheets
adb shell am start -n com.example.alertsheets/.ui.MainActivity

# Wait 3 seconds, then check logs
adb logcat -d -s AlertsApp:* | Select-String -Pattern "auth_ready|uid"

# Expected output (AFTER Firebase Auth enabled):
# AlertsApp: 🔐 Firebase Auth: No user found, signing in anonymously...
# AlertsApp: ✅ Firebase Auth: auth_ready=true uid_present=true uid_masked=abcd***wxyz
```

### Step B: Test with Dirty Test Button
```bash
# Start monitoring logs
adb logcat -c
adb logcat -v time -s DeliveryPipeline:V AuthTokenProvider:V ReliableHttpSender:V *:E

# In app:
# 1. Open AppConfigActivity
# 2. Tap "🔥 Dirty Test (Emoji SMS)" button
# 3. Observe dialog and logcat

# Expected logcat output:
# DeliveryPipeline: Endpoint requires Firebase auth (URL/name match), fetching token...
# AuthTokenProvider: Token obtained successfully (length=XXX)
# DeliveryPipeline: ✓ Authorization header added (token length=XXX)
# ReliableHttpSender: [HTTP POST with Authorization: Bearer <token>]
# DeliveryPipeline: ✅ HTTP OK | code=200 latency=XXXms

# Expected dialog:
# Title: "Test Result"
# Content: "✓ Test SUCCESS\nHTTP 200 (XXXms)\nStatus: ✓ CONFIRMED\n\nResponse preview:\n{...}"
```

### Step C: Test with Real SMS
```bash
# Start monitoring
adb logcat -c
adb logcat -v time -s DeliveryPipeline:V AuthTokenProvider:V ReliableHttpSender:V StructuredLogger:V *:E

# Send SMS from configured source to your device

# Expected logcat output:
# DeliveryPipeline: 📨 SMS event | sender=***XX message_len=XX
# DeliveryPipeline: ✓ Source matched: [source_name]
# DeliveryPipeline: ✓ Endpoint selected: [endpoint_name]
# DeliveryPipeline: Endpoint requires Firebase auth (URL/name match), fetching token...
# AuthTokenProvider: Token obtained successfully (length=XXX)
# DeliveryPipeline: ✓ Authorization header added (token length=XXX)
# DeliveryPipeline: ✅ HTTP OK | code=200 latency=XXXms

# Expected result:
# - HTTP 200 (not 401)
# - Debug screen shows new entry with code=200
# - Firestore console shows new document in /alerts collection
```

### Step D: Verify Debug Screen
```bash
# Open Debug screen
adb shell am start -n com.example.alertsheets/.ui.DebugActivity

# Expected display:
# - Recent deliveries shown (test + real SMS if sent)
# - HTTP codes visible (200, not 401)
# - Latency displayed
# - Response snippets in details
```

---

## 🔍 VERIFICATION COMMANDS (Copy/Paste)

### Check Firebase Auth Status
```bash
adb logcat -d -s AlertsApp:* | Select-String "auth_ready"
```

### Check Auth Token Usage
```bash
adb logcat -d -s DeliveryPipeline:* AuthTokenProvider:* | Select-String "token|auth"
# Note: Token VALUE should NEVER appear, only "token length=XXX"
```

### Check HTTP Requests
```bash
adb logcat -d -s ReliableHttpSender:* DeliveryPipeline:* | Select-String "http_attempt|http_ok|http_fail"
```

### Check for 401 Errors (Should be NONE after fix)
```bash
adb logcat -d | Select-String "401"
```

---

## 📊 IMPLEMENTATION SUMMARY

### What Works NOW (Without Firebase Auth Enabled)
✅ Build successful
✅ App launches without crashes
✅ URL-based auth detection logic implemented
✅ AuthTokenProvider exists and ready
✅ Masked phone numbers in SMS sources
✅ Test buttons wired to DeliveryPipeline
✅ Response snippets captured and logged

### What NEEDS Firebase Console Action
⚠️ **Firebase Auth must be enabled in Console**
- Go to Firebase Console → Authentication → Sign-in method
- Enable "Anonymous" provider
- Click "Save"

### What Will Work AFTER Firebase Auth Enabled
✅ Anonymous sign-in on app start
✅ ID token retrieval for Cloud Functions
✅ Authorization headers added automatically
✅ HTTP 200 responses (not 401)
✅ Firestore documents created successfully

---

## 🔗 CRITICAL FILES (Session 5 Final)

### Auth Infrastructure
- `android/app/src/main/java/com/example/alertsheets/AlertsApplication.kt` - Auth initialization
- `android/app/src/main/java/com/example/alertsheets/data/AuthTokenProvider.kt` - Token retrieval
- `android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt` - URL-based auth injection

### URL-Based Auth Detection Logic
```kotlin
// In DeliveryPipeline.kt (line ~258)
val needsAuth = endpoint.url.contains("cloudfunctions.net/ingest", ignoreCase = true) ||
               endpoint.name.contains("Firestore Ingest", ignoreCase = true)

if (needsAuth) {
    val token = AuthTokenProvider.getFirebaseIdToken()
    if (token != null) {
        headers["Authorization"] = "Bearer $token"
    } else {
        // Fail early with auth_missing event
        return@launch
    }
}
```

---

## 📋 NEXT ACTIONS (Priority Order)

### P0: Enable Firebase Auth (USER ACTION REQUIRED)
```
1. Go to: https://console.firebase.google.com/project/alerts-sheets-bb09c/authentication
2. Click "Sign-in method" tab
3. Click "Anonymous" provider
4. Toggle "Enable" switch
5. Click "Save"
```

### P1: Rebuild and Test After Auth Enabled
```bash
cd D:\github\alerts-sheets\android
.\gradlew.bat clean assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.example.alertsheets
adb logcat -c
adb shell am start -n com.example.alertsheets/.ui.MainActivity

# Wait 3 seconds, then check:
adb logcat -d -s AlertsApp:* | Select-String "auth_ready"
# Expected: "auth_ready=true uid_present=true"
```

### P2: Test Dirty Test Button
```
1. Open AppConfigActivity in app
2. Tap "🔥 Dirty Test" button
3. Verify dialog shows HTTP 200 (not 401)
4. Check Debug screen shows entry with code=200
```

### P3: Test Real SMS Delivery
```
1. Send SMS from configured source
2. Check logcat for "http_ok code=200"
3. Check Firestore console for new /alerts document
4. Check Debug screen shows delivery
```

### P4: Commit Changes
```bash
cd D:\github\alerts-sheets
git add android/app/src/main/java/com/example/alertsheets/AlertsApplication.kt
git add android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt
git add android/app/src/main/java/com/example/alertsheets/SmsSourceAdapter.kt
git add docs/ai/STATE.md

git commit -m "fix: url-based firebase auth for firestore ingest (no manual config)

URL-Based Auth Detection:
- Automatically detects Firestore ingest endpoints by URL/name matching
- Adds Firebase ID token Authorization header when needed
- Apps Script endpoints remain unauthenticated (no auth header)
- No manual authType configuration required

Auth Flow:
- App starts → Firebase Auth anonymous sign-in
- SMS arrives → DeliveryPipeline checks endpoint URL
- If URL contains 'cloudfunctions.net/ingest' → add auth header
- If URL is Apps Script → no auth header
- Token fetch failure → abort with auth_missing event

Enhanced Logging:
- auth_ready status on app start
- uid_present masked as XXX***XXX
- tokenLength logged (never token value)
- Response snippets (120 chars) in all paths

SMS Contact UX:
- Masked phone numbers in UI (***XX)
- Full number preserved internally

Requires: Firebase Authentication enabled in Console (Anonymous provider)

Files: 3 modified
LOC: ~80 lines
"

git log --oneline -1
```

---

## 🚨 KNOWN ISSUES

### Issue 1: Firebase Auth Not Enabled
**Status:** ⚠️ BLOCKING  
**Impact:** Cannot get ID tokens → 401 errors continue  
**Fix:** Enable Anonymous Auth in Firebase Console (see P0 above)  
**Evidence:**
```
E AlertsApp: ❌ Firebase Auth: auth_ready=false error=An internal error has occurred. [ CONFIGURATION_NOT_FOUND ]
```

### Issue 2: google-services.json Validation
**Status:** ⚠️ VERIFY NEEDED  
**Impact:** Firebase services may not initialize correctly  
**Fix:** Verify file exists at `android/app/google-services.json` and project_id matches  
**Commands:**
```bash
ls android/app/google-services.json
cat android/app/google-services.json | findstr "project_id"
```

---

## 📞 RESOURCES

- **Firebase Console:** https://console.firebase.google.com/project/alerts-sheets-bb09c
- **Authentication Settings:** https://console.firebase.google.com/project/alerts-sheets-bb09c/authentication/providers
- **Firestore Data:** https://console.firebase.google.com/project/alerts-sheets-bb09c/firestore/data
- **Cloud Functions:** https://console.firebase.google.com/project/alerts-sheets-bb09c/functions
- **Workspace:** `D:\github\alerts-sheets`
- **Device:** R5CX20WL15P (Samsung, Android SDK 34)

---

**Status:** Implementation complete. Waiting for Firebase Console configuration before verification.
