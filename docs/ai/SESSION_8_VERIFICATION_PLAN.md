# Session 8 Verification Plan - Invariant-Driven Fixes

**Date:** 2025-12-29  
**Branch:** `fix/wiring-sources-endpoints`  
**APK Built:** 2025-12-29 16:44:40 (Today)  
**Status:** Ready for testing

---

## 🎯 WHAT WE'RE VERIFYING

After implementing Session 8 fixes, we need to verify:

1. ✅ **EndpointValidators** correctly filters disabled/invalid endpoints
2. ✅ **SmsConfigActivity** UI prevents selecting bad endpoints
3. ✅ **DeliveryPipeline** uses same validation + logs detailed counts
4. ✅ **Template guardrails** detect and fix SMS/APP bleed-through
5. ✅ **Logs screen** has working search + export
6. ✅ **Log colors** are readable (high contrast)

---

## 📋 PHASE 0: Evidence Collection

### Git State
```powershell
cd D:\github\alerts-sheets
git status
```

**Expected Output:**
- Modified: 7 files (SmsConfigActivity, DeliveryPipeline, LogActivity, 3 layouts, STATE.md)
- Untracked: 1 file (EndpointValidators.kt - NEW)

**Actual Output:**
```
On branch fix/wiring-sources-endpoints
Changes not staged for commit:
  modified:   android/app/src/main/java/com/example/alertsheets/LogActivity.kt
  modified:   android/app/src/main/java/com/example/alertsheets/SmsConfigActivity.kt
  modified:   android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt
  modified:   android/app/src/main/res/layout/activity_log.xml
  modified:   android/app/src/main/res/layout/dialog_add_sms.xml
  modified:   android/app/src/main/res/layout/item_log.xml
  modified:   docs/ai/STATE.md

Untracked files:
  android/app/src/main/java/com/example/alertsheets/utils/EndpointValidators.kt ✅ NEW
```

### APK Build Status
```powershell
cd D:\github\alerts-sheets
(Get-Item android\app\build\outputs\apk\debug\app-debug.apk).LastWriteTime
```

**Result:** Monday, December 29, 2025 4:44:40 PM ✅ (Fresh build with all fixes)

### Capture Current Config
```powershell
# Create evidence directory if needed
New-Item -ItemType Directory -Force -Path evidence

# Dump sources.json
adb shell run-as com.example.alertsheets cat files/sources.json > evidence\sources_phase0.json

# Dump endpoints.json
adb shell run-as com.example.alertsheets cat files/endpoints.json > evidence\endpoints_phase0.json

# View summary
Get-Content evidence\sources_phase0.json | Select-String '"id":', '"enabled":', '"endpointIds":'
Get-Content evidence\endpoints_phase0.json | Select-String '"id":', '"name":', '"enabled":'
```

**What to Look For:**
- SMS sources pointing to Apps Script endpoint (disabled) ← **This is the bug we fixed UI for**
- Firestore ingest endpoint enabled status
- Any endpoints with "YOUR_SCRIPT_ID" placeholder URLs

---

## 🔧 PHASE 1: Install Fresh APK

```powershell
cd D:\github\alerts-sheets\android

# Install fresh build with EndpointValidators
adb install -r .\app\build\outputs\apk\debug\app-debug.apk

# Expected output:
# Performing Streamed Install
# Success ✅
```

**Verify Install:**
```powershell
# Check installed version
adb shell dumpsys package com.example.alertsheets | Select-String "versionName"

# Launch app
adb shell am start -n com.example.alertsheets/.ui.MainActivity
```

---

## 🧪 PHASE 2: UI Testing (Endpoint Selection)

### Test 2A: View Endpoint Status in SMS Config

**Steps:**
1. In app: Open SMS Config (from main dashboard)
2. Tap "+" to add new SMS source OR edit existing
3. Scroll to "Select Endpoints (Fan-Out) *" section

**Expected Results with EndpointValidators:**

✅ **Firestore Ingest Function** (if enabled)
- Checkbox: **Enabled** (can be checked)
- Label: Just the name, no suffix
- Text color: Black (normal)

❌ **Google Apps Script** (if disabled)
- Checkbox: **Disabled** (grayed out, cannot be checked)
- Label: "Google Apps Script **[DISABLED]**"
- Text color: Gray (#888888)

❌ **Test Endpoint** (if URL contains "YOUR_SCRIPT_ID")
- Checkbox: **Disabled** (grayed out)
- Label: "Test Endpoint **[NEEDS URL]**"
- Text color: Gray (#888888)

**Screenshot Capture:**
```powershell
adb exec-out screencap -p > evidence\endpoint_checkboxes.png
```

### Test 2B: Try to Save with Zero Valid Endpoints

**Steps:**
1. In SMS source dialog, uncheck all valid endpoints
2. OR only check disabled endpoints (should be impossible now)
3. Tap "Save"

**Expected Result:**
- Toast appears: **"⚠️ Select at least one ENABLED endpoint"**
- Dialog does NOT close
- Hint text color: Red (#FF5722)

### Test 2C: Save with Valid Endpoint

**Steps:**
1. Check "Firestore Ingest Function" (or other enabled endpoint)
2. Tap "Save"

**Expected Result:**
- Success toast: **"SMS Source saved ✓"**
- Dialog closes
- Source appears in list

---

## 📱 PHASE 3: Trace Single SMS Delivery

### Setup Logging
```powershell
# Clear existing logs
adb logcat -c

# Start monitoring in separate terminal/window
adb logcat -v time -s SmsReceiver:* DataPipeline:* DeliveryPipeline:* ReliableHttpSender:* StructuredLogger:* EndpointValidators:* *:E
```

### Send Test SMS
**Action:** Send 1 SMS from configured number (e.g., +1 888-660-1455)

### Expected Log Sequence

**If SMS source has valid endpoint:**
```
12-29 16:50:00.123 I SmsReceiver: SMS received from +1 888-660-1455
12-29 16:50:00.124 D DeliveryPipeline: 📥 SMS event received | alertId=alert_1735496400123
12-29 16:50:00.125 D DeliveryPipeline: Normalized sender: hasPlus=true digitsLen=11 last2=55
12-29 16:50:00.126 D DeliveryPipeline: ✓ Source matched: SMS Adjust Leads Firestore Database
12-29 16:50:00.127 D DeliveryPipeline: ✓ Endpoint selected: Firestore Ingest Function
12-29 16:50:00.128 D DeliveryPipeline: ✓ Authorization header added (secret length=64)
12-29 16:50:00.129 D DeliveryPipeline: 📤 Sending to: Firestore Ingest Function (https://us-central1-...)
12-29 16:50:00.500 I DeliveryPipeline: ✅ HTTP OK | code=200 latency=371ms
12-29 16:50:00.501 I StructuredLogger: {"event":"http_ok","code":200,...}
```

**If SMS source has NO valid endpoints (the bug we fixed):**
```
12-29 16:50:00.123 I SmsReceiver: SMS received from +1 888-660-1455
12-29 16:50:00.124 D DeliveryPipeline: 📥 SMS event received | alertId=alert_1735496400123
12-29 16:50:00.125 D DeliveryPipeline: ✓ Source matched: SMS Adjust Leads
12-29 16:50:00.126 E DeliveryPipeline: ❌ No enabled endpoints with valid URL for source: SMS Adjust Leads
12-29 16:50:00.127 E DeliveryPipeline:    Selected: 1, Selectable: 0, Enabled: 0, ValidUrl: 1
                                          ^^^^^^ This shows: 1 endpoint selected, but 0 pass validation, 0 are enabled, 1 has valid URL
12-29 16:50:00.128 E StructuredLogger: {"event":"no_enabled_endpoints","selected":1,"selectable":0,"enabled":0,"validUrl":1}
```

### Count Events
```powershell
# In separate terminal after SMS received:

# Count SMS received
adb logcat -d -s StructuredLogger:* | Select-String "sms_received" | Measure-Object -Line
# Expected: 1

# Count HTTP attempts
adb logcat -d -s StructuredLogger:* | Select-String "http_attempt" | Measure-Object -Line
# Expected: 1 (if valid endpoint) or 0 (if no valid endpoint)

# Count HTTP success
adb logcat -d -s StructuredLogger:* | Select-String "http_ok" | Measure-Object -Line
# Expected: 1 (if delivered) or 0 (if blocked by validation)

# Count no_enabled_endpoints events
adb logcat -d -s StructuredLogger:* | Select-String "no_enabled_endpoints" | Measure-Object -Line
# Expected: 0 (if valid endpoint selected) or 1 (if none valid)
```

### Verify Payload (No Placeholders)
```powershell
# Capture JSON payload being sent
adb logcat -d -s DeliveryPipeline:* | Select-String "payload" | Select-Object -First 5
```

**What to Check:**
- ❌ Should NOT see: `"sender":"{{sender}}"` or `"message":"{{message}}"`
- ✅ Should see: `"sender":"+18886601455"` or `"message":"Actual SMS text here"`

---

## 🎨 PHASE 4: Logs Screen Testing

### Test 4A: Log Readability (High Contrast)

**Steps:**
1. Open Logs screen from main dashboard
2. Look at log entries

**Expected Colors:**
- Package name (top-left): **#CCCCCC** (light gray) ✅ Readable
- Content text (middle): **#FFFFFF** (white) ✅ Bright
- Timestamp (right): **#AAAAAA** (gray) ✅ Readable
- Status icon: Green/Red/Yellow (existing logic preserved)

**Screenshot:**
```powershell
adb exec-out screencap -p > evidence\logs_high_contrast.png
```

### Test 4B: Search Functionality

**Steps:**
1. In Logs screen, type "bnn" in search box

**Expected Result:**
- List instantly filters to show only BNN app logs
- Other logs hidden
- Search is case-insensitive

**Test Search Terms:**
- "bnn" → Shows BNN app logs
- "sms" → Shows SMS logs
- "SENT" → Shows all successful deliveries
- Clear search → Full list reappears

### Test 4C: Export Last 10

**Steps:**
1. Tap "Export" button

**Expected Result:**
- Share sheet appears
- File name: "notification_logs_last10.ndjson"
- Can share to Drive/Files/Email/etc
- File format: NDJSON (1 JSON object per line)

**Verify Export:**
```powershell
# After sharing to PC, check format:
Get-Content notification_logs_last10.ndjson | Select-Object -First 3

# Expected: Each line is valid JSON
# {"timestamp":"2025-12-29T...","packageName":"com.bnn","status":"SENT",...}
# {"timestamp":"2025-12-29T...","packageName":"com.sms","status":"SENT",...}
```

### Test 4D: Copy Last 10

**Steps:**
1. Tap "Copy" button
2. Toast appears: "Last 10 logs copied to clipboard"
3. Paste into text editor (Notepad)

**Expected Result:**
- NDJSON format (same as export)
- 10 log entries (or fewer if less than 10 exist)

---

## 🔄 PHASE 5: Template Guardrails Testing

### Test 5A: Detect APP Template in SMS Source

**Prerequisites:**
- Need an SMS source with APP template ({{package}}, {{title}})
- OR manually edit sources.json to inject APP template

**Steps:**
1. Open SMS Config
2. Edit SMS source that has APP template

**Expected Result:**
- ⚠️ **Yellow warning banner appears at top of dialog**
- Text: "⚠️ SMS source is using App template"
- Subtext: "This may cause placeholder variables..."
- Button: **"Reset to SMS Template"**

**Screenshot:**
```powershell
adb exec-out screencap -p > evidence\template_warning_banner.png
```

### Test 5B: Reset Template with One Tap

**Steps:**
1. With banner visible, tap **"Reset to SMS Template"**
2. Confirmation dialog appears

**Expected Dialog:**
- Title: "Reset to SMS Template?"
- Message: Shows {{sender}}, {{message}}, {{time}} example
- Buttons: [Reset] [Cancel]

3. Tap **"Reset"**

**Expected Result:**
- Banner disappears ✅
- Template internally replaced with SMS schema
- Toast: "Template reset to SMS default"

### Test 5C: Try to Save APP Template Without Fixing

**Steps:**
1. Edit SMS source with APP template
2. Do NOT tap "Reset to SMS Template"
3. Tap "Save"

**Expected Result:**
- **Second confirmation dialog appears**
- Title: "⚠️ Template Mismatch"
- Message: "This SMS source is using an App template..."
- Buttons: **[Save Anyway]** **[Cancel]** **[Fix Template]**

If tap **"Fix Template"**:
- Dialog reopens with SMS template applied

If tap **"Save Anyway"**:
- Saves (not recommended, but allowed for edge cases)

---

## 🔍 PHASE 6: Endpoint Toggle Verification

### Test 6A: Toggle Endpoint in UI

**Steps:**
1. Open Endpoints screen
2. Toggle "Google Apps Script" OFF → ON
3. Go back to main screen

**Verify Persistence:**
```powershell
# Check endpoints.json before
adb shell run-as com.example.alertsheets cat files/endpoints.json | Select-String "Google Apps Script" -Context 2,2

# (Toggle in UI)

# Check endpoints.json after
adb shell run-as com.example.alertsheets cat files/endpoints.json | Select-String "Google Apps Script" -Context 2,2
```

**Expected:**
- Before: `"enabled": false`
- After: `"enabled": true`
- Change persisted to disk ✅

### Test 6B: Verify No Crashes

```powershell
# Check for runtime errors
adb logcat -d -s AndroidRuntime:E | Select-Object -Last 20

# Expected: No crashes, no "FATAL EXCEPTION" messages
```

---

## 📊 PHASE 7: DeliveryPipeline Detailed Logging

### Test 7A: Detailed Endpoint Counts

**Setup:**
1. Configure SMS source with multiple endpoints:
   - 1 enabled with valid URL
   - 1 disabled with valid URL
   - 1 enabled with placeholder URL ("YOUR_SCRIPT_ID")

**Send SMS and check logs:**
```powershell
adb logcat -v time -s DeliveryPipeline:* | Select-String "Selected:"
```

**Expected Log (EndpointValidators in action):**
```
DeliveryPipeline: ❌ No enabled endpoints with valid URL for source: Test SMS
DeliveryPipeline:    Selected: 3, Selectable: 1, Enabled: 2, ValidUrl: 2
                     ^^^^^^^^    ^^^^^^^^^^^^    ^^^^^^^^^^    ^^^^^^^^^^^
                     Total IDs   Pass BOTH       Enabled       Valid URL
                     selected    checks          (may have     (may be
                                                 bad URL)      disabled)
```

**Interpretation:**
- 3 endpoints selected
- 2 are enabled (1 disabled)
- 2 have valid URLs (1 has "YOUR_SCRIPT_ID")
- But only 1 passes BOTH checks (enabled AND valid URL)

### Test 7B: Verify EndpointValidators Called

```powershell
# Look for EndpointValidators method calls in logs
adb logcat -d | Select-String "EndpointValidators"

# Expected: No direct logs (it's a utility), but DeliveryPipeline uses it
```

---

## ✅ SUCCESS CRITERIA

### Must Pass All:

- [ ] **UI blocks selecting disabled endpoints** (checkboxes disabled, grayed out)
- [ ] **UI blocks selecting endpoints with "YOUR_SCRIPT_ID"** (checkboxes disabled)
- [ ] **Save blocked if zero valid endpoints selected** (Toast + dialog doesn't close)
- [ ] **DeliveryPipeline logs detailed counts** (Selected: X, Selectable: Y, Enabled: Z, ValidUrl: W)
- [ ] **`no_enabled_endpoints` event emitted** when no valid endpoints (instead of "no_endpoint")
- [ ] **Template warning banner appears** for SMS source with APP template
- [ ] **"Reset to SMS Template" works** (one-tap fix)
- [ ] **Logs screen colors readable** (white #FFFFFF, light gray #CCCCCC, gray #AAAAAA)
- [ ] **Logs search filters in real-time** (type "bnn" → shows only BNN logs)
- [ ] **"Export Last 10" creates NDJSON file** (via share sheet)
- [ ] **"Copy Last 10" copies to clipboard** (NDJSON format)
- [ ] **No crashes during any test** (check AndroidRuntime:E logs)

### Bonus Verification:

- [ ] **Google Sheets rows show real data** (not {{sender}} or {{message}} placeholders)
- [ ] **Firestore documents created** (if Firestore ingest endpoint used)
- [ ] **Endpoint toggle persists** (check endpoints.json before/after)

---

## 🐛 Known Issues to Watch For

**Issue 1: Old APK Installed**
- **Symptom:** Endpoint checkboxes don't show "[DISABLED]" labels
- **Fix:** Uninstall completely, then reinstall: `adb uninstall com.example.alertsheets && adb install -r app-debug.apk`

**Issue 2: Cached Config**
- **Symptom:** Changes to endpoints.json not reflected in UI
- **Fix:** Force-stop app: `adb shell am force-stop com.example.alertsheets`

**Issue 3: Search Box Not Appearing**
- **Symptom:** Logs screen missing search box
- **Fix:** Verify APK build time is AFTER 16:44 today (December 29)

**Issue 4: Template Banner Not Appearing**
- **Symptom:** SMS source with APP template doesn't show warning
- **Fix:** Check that templateJson actually contains {{package}} or {{title}} (case-sensitive)

---

## 📝 EVIDENCE CAPTURE CHECKLIST

After completing all tests, capture:

- [ ] `evidence\sources_phase0.json` (before)
- [ ] `evidence\endpoints_phase0.json` (before)
- [ ] `evidence\endpoint_checkboxes.png` (showing [DISABLED] labels)
- [ ] `evidence\logs_high_contrast.png` (showing readable colors)
- [ ] `evidence\template_warning_banner.png` (yellow banner with reset button)
- [ ] `evidence\logcat_no_enabled_endpoints.txt` (detailed count logs)
- [ ] `evidence\logcat_http_ok.txt` (successful delivery with valid endpoint)
- [ ] `evidence\notification_logs_last10.ndjson` (exported from app)

**Save logcat output:**
```powershell
adb logcat -d > evidence\full_logcat_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt
```

---

## 🎯 NEXT STEPS AFTER VERIFICATION

**If All Tests Pass:**
1. Commit changes: `git add -A && git commit -m "feat: centralized endpoint validation + template guardrails + logs improvements"`
2. Push to remote: `git push origin fix/wiring-sources-endpoints`
3. Update STATUS in STATE.md: "🟢 Verified on Device"
4. Deploy to production (after shared secret configuration)

**If Any Test Fails:**
1. Document failure in evidence folder
2. Check STATE.md troubleshooting section
3. Review relevant logs
4. Fix bug, rebuild, retest

---

**End of Verification Plan**

