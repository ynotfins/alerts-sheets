# Quick Test Commands - Session 8 Verification

## 🚀 Quick Start (Copy-Paste Ready)

```powershell
# Navigate to project
cd D:\github\alerts-sheets

# Create evidence folder
New-Item -ItemType Directory -Force -Path evidence

# Install fresh APK
adb install -r android\app\build\outputs\apk\debug\app-debug.apk

# Capture config state
adb shell run-as com.example.alertsheets cat files/sources.json > evidence\sources_before.json
adb shell run-as com.example.alertsheets cat files/endpoints.json > evidence\endpoints_before.json

# Start monitoring (leave this running in separate terminal)
adb logcat -c && adb logcat -v time -s DeliveryPipeline:* ReliableHttpSender:* StructuredLogger:* *:E
```

## 📱 UI Tests (Manual in App)

### Test 1: Endpoint Selection
1. Open SMS Config → Add/Edit source
2. **VERIFY:** Disabled endpoints show "[DISABLED]" and can't be checked ✅
3. **VERIFY:** Endpoints with "YOUR_SCRIPT_ID" show "[NEEDS URL]" ✅
4. **TRY:** Save with no valid endpoints → Should block with toast ✅

### Test 2: Template Warning
1. Edit SMS source (if has APP template)
2. **VERIFY:** Yellow banner appears: "⚠️ SMS source is using App template" ✅
3. **TRY:** Tap "Reset to SMS Template" → Template fixed ✅

### Test 3: Logs Screen
1. Open Logs screen
2. **VERIFY:** Search box visible at top ✅
3. **TRY:** Type "bnn" → Filters to BNN logs only ✅
4. **TRY:** Tap "Export" → Share sheet appears ✅
5. **VERIFY:** Text is readable (not gray-on-gray) ✅

## 📊 Expected Log Output

### ✅ Success Case (Valid Endpoint Selected)
```
DeliveryPipeline: 📥 SMS event received | alertId=alert_XXX
DeliveryPipeline: ✓ Source matched: SMS Source Name
DeliveryPipeline: ✓ Endpoint selected: Firestore Ingest Function
DeliveryPipeline: ✓ Authorization header added (secret length=64)
DeliveryPipeline: 📤 Sending to: https://us-central1-...
DeliveryPipeline: ✅ HTTP OK | code=200 latency=XXXms
```

### ❌ Failure Case (No Valid Endpoints - Bug We Fixed)
```
DeliveryPipeline: 📥 SMS event received | alertId=alert_XXX
DeliveryPipeline: ✓ Source matched: SMS Source Name
DeliveryPipeline: ❌ No enabled endpoints with valid URL for source: SMS Source Name
DeliveryPipeline:    Selected: 2, Selectable: 0, Enabled: 1, ValidUrl: 1
                     ^^^^^^^^    ^^^^^^^^^^^^    ^^^^^^^^^^    ^^^^^^^^^^^
                     Total       Pass BOTH       Are           Have
                     selected    checks          enabled       valid URL
StructuredLogger: {"event":"no_enabled_endpoints","selected":2,"selectable":0,...}
```

**Interpretation of "Selected: 2, Selectable: 0, Enabled: 1, ValidUrl: 1":**
- 2 endpoints were selected by the source
- 0 pass BOTH enabled AND validUrl checks
- 1 is enabled (but has bad URL like "YOUR_SCRIPT_ID")
- 1 has valid URL (but is disabled)
- **Result:** No delivery attempt made ✅ (Correct behavior)

## 🔍 Quick Diagnostic Commands

```powershell
# Count events after sending SMS
adb logcat -d -s StructuredLogger:* | Select-String "http_attempt" | Measure-Object -Line
adb logcat -d -s StructuredLogger:* | Select-String "http_ok" | Measure-Object -Line
adb logcat -d -s StructuredLogger:* | Select-String "no_enabled_endpoints" | Measure-Object -Line

# Check for crashes
adb logcat -d -s AndroidRuntime:E | Select-Object -Last 10

# Verify endpoint state
adb shell run-as com.example.alertsheets cat files/endpoints.json | Select-String '"enabled"' -Context 1,0

# Save full log
adb logcat -d > evidence\logcat_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt
```

## ✅ Pass/Fail Checklist

- [ ] **Disabled endpoints cannot be selected** (UI prevents)
- [ ] **"YOUR_SCRIPT_ID" endpoints cannot be selected** (UI prevents)
- [ ] **Save blocked if no valid endpoints** (Toast appears)
- [ ] **Detailed logs show counts** (Selected: X, Selectable: Y, ...)
- [ ] **Template banner appears** for SMS with APP template
- [ ] **Logs text is readable** (white/light gray, not dark gray)
- [ ] **Search filters logs** in real-time
- [ ] **Export creates NDJSON** file via share
- [ ] **No crashes** (AndroidRuntime:E is clean)

## 📸 Screenshots to Capture

```powershell
# Endpoint checkboxes with [DISABLED] labels
adb exec-out screencap -p > evidence\endpoint_checkboxes.png

# Template warning banner
adb exec-out screencap -p > evidence\template_banner.png

# Logs screen with readable colors
adb exec-out screencap -p > evidence\logs_readable.png

# Search filtering
adb exec-out screencap -p > evidence\logs_search.png
```

## 🐛 Common Issues

**"Endpoint checkboxes look the same as before"**
→ Old APK installed. Uninstall + reinstall:
```powershell
adb uninstall com.example.alertsheets
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

**"Changes to endpoints.json not showing in UI"**
→ Force-stop app:
```powershell
adb shell am force-stop com.example.alertsheets
adb shell am start -n com.example.alertsheets/.ui.MainActivity
```

**"Search box not visible in Logs screen"**
→ Check APK build time is AFTER 16:44 today:
```powershell
(Get-Item android\app\build\outputs\apk\debug\app-debug.apk).LastWriteTime
```

## 🎯 Success Means

✅ **Before our fixes:**
- Could select disabled endpoints → Failed at runtime with confusing "no_endpoint" error
- SMS placeholders {{message}} appeared in Sheets
- Logs unreadable (gray-on-gray)
- No search/export in Logs screen

✅ **After our fixes:**
- UI prevents selecting disabled endpoints → Fail-fast with clear error
- Template warning + one-tap fix → No placeholders in Sheets
- Logs readable (high contrast)
- Search + export work like Debug screen

**If all tests pass → Code works as designed! 🚀**

