# 🔍 SESSION 9: ROOT CAUSE ANALYSIS & FIX PLAN

**Date:** 2025-12-29  
**Status:** Phase 0 Evidence Collection Required  
**Critical Issues:** Endpoint toggle bug, 5 rows per SMS, Placeholders persist, Log UI missing features

---

## 📊 CODE ANALYSIS FINDINGS

### ✅ **Apps Script Code Analysis** (`scripts/Code.gs`)

**Finding:** Apps Script code is **CORRECT** - only calls `sheet.appendRow()` **ONCE** per SMS.

**Evidence:**
- `handleSmsMessage()` function (lines 204-259):
  - Line 233: `sheet.appendRow(row)` for fire alerts (single call)
  - Line 248: `sheet.appendRow(row)` for generic SMS (single call)
- No loops or retry logic in Apps Script
- No message splitting logic

**Conclusion:** Duplication is **NOT** happening in Apps Script code. Must be Android-side or Apps Script execution count.

---

### ✅ **Android Code Analysis**

#### **1. Endpoint Toggle Bug**

**File:** `android/app/src/main/java/com/example/alertsheets/EndpointActivity.kt`

**Finding:** Code looks correct - no `finish()` call in toggle handler.

**Evidence:**
- Line 64-70: Toggle handler calls `saveEndpoints()` → `endpointRepository.saveAll(endpoints)`
- No navigation or `finish()` calls
- `EndpointsAdapter.kt` line 38-40: Toggle calls `onToggle(item, isChecked)` → Activity callback

**Possible Root Causes:**
1. Exception during `saveEndpoints()` crashes activity (appears as "close")
2. `EndpointRepository.saveAll()` throws exception
3. UI thread blocking causes ANR → activity killed
4. Samsung OneUI overlay intercepting toggle click

**Fix Strategy:** Add try-catch around `saveEndpoints()`, add Toast confirmation, verify no exceptions in logcat.

---

#### **2. Log UI Features**

**Files:**
- `android/app/src/main/java/com/example/alertsheets/LogActivity.kt` ✅ Has search/export/copy (lines 25-27, 43-45, 57-73, 117-173)
- `android/app/src/main/res/layout/activity_log.xml` ✅ Has search box + Export/Copy buttons (lines 26-35, 37-51)
- `android/app/src/main/res/layout/item_log.xml` ✅ Has high contrast colors (#CCCCCC, #FFFFFF, #AAAAAA)

**Finding:** Session 8 features **ARE** in the codebase.

**Possible Root Causes:**
1. **APK not rebuilt** after Session 8 changes
2. **User viewing wrong activity** (DebugActivity instead of LogActivity)
3. **Resource caching** on device (old APK resources cached)

**Fix Strategy:** Verify APK build date, confirm which activity opens from "Logs" tile, clean reinstall.

---

#### **3. "5 Rows Per SMS" Mystery**

**Files Analyzed:**
- `DeliveryPipeline.kt` line 148: Picks **FIRST** endpoint only (`validEndpoints.firstOrNull()`)
- `DataPipeline.kt` line 184: Fans out to **ALL** endpoints (`for (endpoint in endpoints)`)
- Apps Script: Only calls `appendRow()` **ONCE**

**Critical Question:** Which pipeline is handling SMS?

**Evidence Needed:**
- Check logcat for `DeliveryPipeline` vs `DataPipeline` logs
- Count HTTP requests sent (should be 1 if DeliveryPipeline, N if DataPipeline)
- Verify source configuration (how many endpoints selected?)

**Possible Root Causes:**
1. **DataPipeline fan-out** to 5 endpoints (but user says all sources point to one endpoint)
2. **Android retry logic** creating 5 HTTP requests (but logs show only 1 `http_ok`)
3. **Apps Script triggered 5 times** (webhook retries? Google Apps Script execution limits?)
4. **Message contains 5 lines** and script somehow splits (but code doesn't show this)

**Fix Strategy:** Add instrumentation to trace exact HTTP request count, verify Apps Script execution count.

---

#### **4. Placeholders Persist**

**Finding:** Templates look correct in `sources.json` ({{sender}}, {{message}}, {{time}}, {{timestamp}}).

**Possible Root Causes:**
1. Template not rendered (TemplateEngine not called)
2. JSON double-encoded (payload is string, not object)
3. Apps Script reads wrong field path (`e.parameter.sender` instead of `e.postData.contents.sender`)
4. Field name mismatch (template has `sender` but script expects `phoneNumber`)

**Fix Strategy:** Add logging to capture actual JSON payload sent, compare to Apps Script expectations.

---

## 🎯 PHASE 0: EVIDENCE COLLECTION PLAN

### **Step 1: Verify Git State**
```powershell
cd D:\github\alerts-sheets
git status
git log --oneline -5
git diff HEAD -- android/app/src/main/res/layout/item_log.xml
```

### **Step 2: Check APK Build Date**
```powershell
ls -lt android\app\build\outputs\apk\debug\app-debug.apk
# Compare to Session 8 date: 2025-12-29 09:30 UTC
```

### **Step 3: Dump Current Config**
```powershell
adb shell run-as com.example.alertsheets cat files/sources.json > evidence\sources_phase0.json
adb shell run-as com.example.alertsheets cat files/endpoints.json > evidence\endpoints_phase0.json
```

### **Step 4: Identify Activity**
```powershell
adb logcat -c
# (User taps "Logs" tile)
adb logcat -d | Select-String "START.*Activity.*alertsheets" | Select-Object -Last 5
```

### **Step 5: Trace Single SMS End-to-End**
```powershell
adb logcat -c
adb logcat -v time -s SmsReceiver:* DataPipeline:* DeliveryPipeline:* ReliableHttpSender:* StructuredLogger:* *:E

# (Send 1 SMS from +1 888-660-1455)
# (Wait 10 seconds)

# Count events:
adb logcat -d -s StructuredLogger:* | Select-String "sms_received" | Measure-Object -Line
adb logcat -d -s StructuredLogger:* | Select-String "http_attempt" | Measure-Object -Line
adb logcat -d -s StructuredLogger:* | Select-String "http_ok" | Measure-Object -Line

# Check Sheets: Manually count rows added (Expected: 1, Actual: 5 per user)
```

### **Step 6: Capture Actual JSON Payload**
```powershell
# Add temporary logging in DeliveryPipeline before HTTP send:
# Log.d(TAG, "JSON payload preview: ${jsonPayload.take(500)}")

adb logcat -s DeliveryPipeline:* | Select-String "payload" | Select-Object -First 5
```

---

## 🔧 PHASE 1-6: FIX IMPLEMENTATION

See main plan document for detailed fix strategies.

**Key Actions:**
1. **Endpoint Toggle:** Add try-catch, Toast confirmation, verify no exceptions
2. **Log UI:** Rebuild APK if needed, verify correct activity opens
3. **5 Rows:** Instrument pipeline, identify duplication point
4. **Placeholders:** Capture payload, fix template/script mismatch
5. **Timestamps:** Standardize on device time ({{timestamp}})

---

## 📋 ACCEPTANCE CHECKLIST

- [ ] Endpoint toggle saves without closing activity
- [ ] Log UI shows search/export/copy + high contrast colors
- [ ] 1 SMS → Exactly 1 row in Sheets
- [ ] No placeholders in Sheets (real values)
- [ ] Timestamps consistent (device time)

---

**NEXT STEP:** Execute Phase 0 evidence collection before making any code changes.

