# Diagnostic Procedures

**Purpose:** Step-by-step procedures to identify and fix common issues.

---

## Issue: Empty Sheet Fields (All Columns Except Timestamp)

### Symptoms
- Google Sheet receives notification
- Only Column B (Timestamp) populates
- All other columns empty (Status, State, County, etc.)

### Diagnosis Steps

#### Step 1: Check Parser Success
```powershell
adb logcat | findstr "Parser"
```

**Expected (Working):**
```
Parser: Parsing 8 segments from: U/D NJ| Monmouth| ...
Parser: ✓ Parsed: ID=#1825164, State=NJ, City=Asbury Park
```

**Actual (Broken):**
```
Parser: No pipe delimiter found in: Update\n9/15/25\nU/D NJ|...
```
OR
```
Parser: returning null
```

**Root Cause:** Parser fails to find pipe line or returns null.

#### Step 2: Check JSON Payload
```powershell
adb logcat -s NotificationService:I | findstr "PAYLOAD"
```

**Expected (Working):**
```
PAYLOAD: {"status":"Update","timestamp":"2025-12-17T18:30:00Z","incidentId":"#1825164","state":"NJ","county":"Monmouth","city":"Asbury Park",...}
```

**Actual (Broken):**
```
PAYLOAD: {"package":"us.bnn.newsapp","title":"Update","text":"9/15/25",...}
```

**Root Cause:** Generic template used instead of BNN parser output.

#### Step 3: Verify Apps Script Execution
1. Go to [Google Apps Script Editor](https://script.google.com)
2. Open your project
3. View → Executions
4. Check recent runs for errors

**If error:** Read error message for clues (field name mismatch, etc.)

### Fix Actions

**If Parser returns null:**
1. Open `Parser.kt`
2. Add extensive logging:
   ```kotlin
   Timber.d("Parser", "Input text: $fullText")
   Timber.d("Parser", "Lines found: ${lines.size}")
   Timber.d("Parser", "Pipe line: $contentLine")
   ```
3. Identify which step fails
4. Make parser more robust (never return null for valid BNN)

**If JSON has wrong fields:**
1. Compare JSON keys with Apps Script expectations
2. Verify `ParsedData` field names match `data.fieldName` in Code.gs
3. Check serialization (Gson should use field names, not JSON annotations)

---

## Issue: Notifications Not Intercepted

### Symptoms
- App installed and running
- Dashboard shows green dots
- No logs appear when BNN notification arrives

### Diagnosis Steps

#### Step 1: Verify Listener Connected
```powershell
adb logcat -s NotificationService:D
```

**Expected:**
```
NotificationService: Listener Connected
```

#### Step 2: Check Master Switch
1. Open app
2. Check if "LIVE" button is green (not red "PAUSED")

#### Step 3: Verify Permission
```powershell
adb shell settings get secure enabled_notification_listeners
```

**Expected:** Should include `com.example.alertsheets`

**Fix:** Go to Settings → Notifications → Notification Access → Enable app

#### Step 4: Check App Filter
```powershell
adb logcat | findstr "App filtered out"
```

**If present:** BNN app is not in selected apps list.

**Fix:** Go to app → "Apps" → Select "BNN" or enable "God Mode" (select none)

---

## Issue: Queue Stuck Pending

### Symptoms
- Logs show "PENDING" status
- Never turns "SENT" (green)
- Google Sheet not updating

### Diagnosis Steps

#### Step 1: Check Network
```powershell
adb logcat -s QueueProcessor:D NetworkClient:E
```

**Expected:**
```
QueueProcessor: Processing request ID: 123
NetworkClient: Success sending to endpoint
```

**Actual (Broken):**
```
NetworkClient: Failed sending: java.net.UnknownHostException
```

**Root Cause:** No internet or endpoint URL wrong.

#### Step 2: Verify Endpoint URL
1. Open app → "Endpoints"
2. Check URL format: `https://script.google.com/macros/s/{SCRIPT_ID}/exec`
3. Test in browser (should return `{"result":"verified"}` for POST)

#### Step 3: Check Apps Script Deployment
1. Go to Apps Script Editor
2. Deploy → Manage Deployments
3. Verify "Web app" is deployed
4. Verify "Execute as: Me"
5. Verify "Who has access: Anyone"

### Fix Actions

**If network error:**
- Check device internet connection
- Verify firewall not blocking app
- Test URL in Postman/curl

**If endpoint wrong:**
- Copy correct URL from Apps Script → Deploy → Web app URL
- Paste in app → Endpoints → Edit

**If Apps Script error:**
- Re-deploy script (Deploy → New Deployment)
- Authorize permissions when prompted
- Update URL in app

---

## Issue: Duplicate Rows (Same Incident Creates Multiple Rows)

### Symptoms
- Update notification creates new row instead of appending
- Google Sheet has multiple rows with same Incident ID

### Diagnosis Steps

#### Step 1: Check Incident ID Format
```powershell
adb logcat | findstr "incidentId"
```

**Expected:** `incidentId: #1825164` (consistent format)

**Actual:** `incidentId: 1825164` or `incidentId: #` (inconsistent)

**Root Cause:** ID format mismatch between Android and Apps Script search.

#### Step 2: Verify Apps Script Search
1. Check `Code.gs` lines 46-60
2. Verify search logic: `idValues[i][0].toString().trim() === incidentId`
3. Both must match exactly (with or without `#`)

### Fix Actions

**Standardize ID format:**
- Android: Always send `#1234567` (with hash)
- Apps Script: Search for exact match including hash
- Or: Strip hash from both sides

---

## General Debug Commands

### Clear All Logs
```powershell
adb logcat -c
```

### Monitor Specific Tags
```powershell
adb logcat -s NotificationService:D Parser:D QueueProcessor:D NetworkClient:E
```

### Export Logs to File
```powershell
adb logcat -d > debug_log.txt
```

### Reinstall App (Clean State)
```powershell
adb uninstall com.example.alertsheets
cd android
.\gradlew.bat :app:installDebug --no-daemon
```

### Check SQLite Queue
```powershell
adb shell
run-as com.example.alertsheets
cd databases
sqlite3 alert_sheets_queue.db
SELECT * FROM request_queue WHERE status='PENDING';
.quit
```

---

## Escalation

If issue persists after following diagnostics:

1. **Capture Logs:**
   ```powershell
   adb logcat -d > full_debug_log.txt
   ```

2. **Document:**
   - Exact steps to reproduce
   - Expected vs actual behavior
   - Log excerpts showing failure point

3. **Check Known Issues:**
   - `/docs/tasks/` for active bugs
   - GitHub issues (if applicable)

4. **Create Bug Report:**
   - Include logs, screenshots, device info
   - Reference this diagnostic guide

