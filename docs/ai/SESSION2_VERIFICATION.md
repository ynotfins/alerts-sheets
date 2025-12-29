# SESSION 2 VERIFICATION COMMANDS

**Date:** 2025-12-26  
**Purpose:** Verify SMS matching fix, structured logging, and Firestore ingest

---

## 📋 VERIFICATION CHECKLIST

### Step 1: Clear Logs and Launch App
```powershell
adb logcat -c
adb shell am force-stop com.example.alertsheets
adb shell am start -n com.example.alertsheets/.ui.MainActivity
```

### Step 2: Monitor Structured Logs
```powershell
# In separate terminal, monitor all structured logs
adb logcat -v time -s StructuredLogger:V DataPipeline:V Pipe:V IngestQueue:V NetworkClient:V TEST:V *:E
```

### Step 3: Test Dirty Test (Manual Test Payload)
1. Open app → Lab → Open "AppConfig" (or similar test activity)
2. Tap "🔥 Dirty Test (Emoji SMS)" button
3. Confirm send

**Expected Logs:**
```
StructuredLogger: {"timestamp":"...","level":"INFO","source_id":"manual_test","endpoint_id":"test_endpoint","alert_id":"test_123...","event":"test_attempt_started","details":"isUpdate=false url=https://..."}
StructuredLogger: {"timestamp":"...","level":"INFO","source_id":"manual_test","endpoint_id":"test_endpoint","alert_id":"test_123...","event":"test_http_ok","details":"code=200"}
```

**Verify:**
- ✅ Debug screen shows logs > 0 (tap purple "Debug" card)
- ✅ Logcat shows `test_attempt_started` and `test_http_ok`

---

### Step 4: Test SMS Matching
**Pre-requisite:** SMS source configured with ID: `"sms:+1 888-660-1455"`

**Send test SMS:**
```powershell
# From another phone or emulator, send SMS to device
# Sender format can be any of: "+18886601455", "888-660-1455", "18886601455"
```

**Expected Logs:**
```
Pipe: SMS from +18886601455 -> source SMS Firestore Database
StructuredLogger: {"timestamp":"...","level":"INFO","source_id":"sms:+1 888-660-1455","endpoint_id":"endpoint-1766609309063","alert_id":"...","event":"attempt_started","details":"endpointName=Firestore Ingest Function"}
StructuredLogger: {"timestamp":"...","level":"INFO","source_id":"sms:+1 888-660-1455","endpoint_id":"endpoint-1766609309063","alert_id":"...","event":"http_ok","details":"code=200 latency=123ms"}
```

**If SMS IGNORED (old bug):**
```
Pipe: No source configured for SMS from +18886601455, ignoring
StructuredLogger: {"timestamp":"...","level":"INFO","event":"sms_ignored","details":"reason=no_matching_source hasPlus=true digitsLen=11"}
```

**Verify:**
- ✅ SMS NOT marked IGNORED (should match normalized digits)
- ✅ Logcat shows `SMS from ... -> source SMS Firestore Database`
- ✅ Structured logs show `attempt_started` + `http_ok`

---

### Step 5: Test Firestore Ingest
**Pre-requisite:** One source has `enableFirestoreIngest: true` + `ENABLE_FIRESTORE_INGEST=true` (debug builds)

**Expected Logs:**
```
DataPipeline: ✅ Firestore enqueue success for sms:+1 888-660-1455
StructuredLogger: {"timestamp":"...","level":"INFO","source_id":"sms:+1 888-660-1455","alert_id":"...","event":"ingest_queued","details":"uuid=pending"}
IngestQueue: Enqueued alert for source: sms:+1 888-660-1455
```

**If Firestore SKIPPED:**
```
StructuredLogger: {"timestamp":"...","level":"INFO","source_id":"...","endpoint_id":"firestore_ingest","alert_id":"...","event":"firestore_ingest_skipped","details":"reason=per_source_off"}
```

**Verify:**
- ✅ Logcat shows `ingest_queued` event
- ✅ Firebase Console → Firestore → `/alerts` collection has new document
- ✅ If skipped, reason is logged (`global_flag_off` or `per_source_off`)

---

### Step 6: Check Debug Screen
1. Open app → Tap purple "Debug" card
2. Verify logs list is populated

**Expected:**
- Last 20 structured logs visible
- Color-coded by level (INFO=green, ERROR=red, WARN=yellow)
- Each log shows: timestamp, event, IDs (source_id, endpoint_id, alert_id)
- "Share Logs" button works (creates NDJSON file)

---

### Step 7: Check Firestore Console
1. Open: https://console.firebase.google.com/project/alerts-sheets-bb09c/firestore/data
2. Navigate to `/alerts` collection
3. Verify documents exist with:
   - `sourceId`: `"sms:+1 888-660-1455"` (or configured source)
   - `payload`: JSON string
   - `timestamp`: ISO8601

**If Empty:**
- Check IngestQueue logs for errors (503, auth failure, etc.)
- Verify anonymous sign-in succeeded in AlertsApplication logs
- Check server-side feature flag: `functions/.env.local` → `FIRESTORE_INGEST_ENABLED=true`

---

## 📊 EVIDENCE COLLECTION

### Capture Full Logs
```powershell
# Run for 30 seconds after triggering tests
adb logcat -v time -s StructuredLogger:V DataPipeline:V Pipe:V IngestQueue:V NetworkClient:V TEST:V *:E > session2_verification.log
```

### Check Source Configuration
```powershell
adb shell run-as com.example.alertsheets cat files/sources.json | ConvertFrom-Json | Format-List
```

### Check Endpoint Configuration
```powershell
adb shell run-as com.example.alertsheets cat files/endpoints.json | ConvertFrom-Json | Format-List
```

---

## 🐛 DEBUGGING COMMANDS

### If SMS Still Ignored
```powershell
# Check exact source ID and sender format
adb shell run-as com.example.alertsheets cat files/sources.json | Select-String "sms:"
adb logcat -s Pipe:V | Select-String "SMS from"

# Check normalization (add test log in code):
# SmsSenderNormalizer.normalize("sms:+1 888-660-1455") → "18886601455"
# SmsSenderNormalizer.normalize("+18886601455") → "18886601455"
```

### If Dirty Test Logs Missing
```powershell
# Check StructuredLogger buffer
adb logcat -s StructuredLogger:V | Select-String "test_"
```

### If Firestore Empty
```powershell
# Check IngestQueue logs
adb logcat -s IngestQueue:V | Select-String "Enqueued|Failed"

# Check anonymous auth
adb logcat -s AlertsApplication:V | Select-String "Firebase Auth"

# Check server-side function logs
firebase functions:log --only ingest
```

---

**Update STATE.md after verification with actual log outputs and results.**

