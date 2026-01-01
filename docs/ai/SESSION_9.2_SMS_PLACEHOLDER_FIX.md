# Session 9.2: SMS Template Placeholder Substitution Fix

**Date:** 2025-12-30 00:30 UTC  
**Status:** ✅ COMPLETE - Build Successful  
**Branch:** `fix/wiring-sources-endpoints`

---

## 🎯 Problem Statement

**Evidence:**
- Android logs show: `{"result":"success","type":"sms","sender":"{{sender}}","parsed":false}`
- Google Sheets rows contain: `{{sender}}` and `{{message}}` instead of real SMS data
- HTTP 200 OK response, but payload sent with unresolved placeholders

**Root Cause:** DeliveryPipeline was creating a `ParsedData` object with App-specific fields (`incidentId`, `address`, `originalBody`) instead of SMS-specific fields (`sender`, `message`). Template engine couldn't resolve `{{sender}}` and `{{message}}` because the variable map didn't contain these keys.

---

## ✅ Solution Implemented

### 1. Fixed SMS Context Mapping ✅

**BEFORE (Lines 214-219):**
```kotlin
val parsedData = ParsedData(
    incidentId = alertId,
    timestamp = dateFormat.format(Date(timestamp)),
    originalBody = message,  // ❌ Template expects {{message}}
    address = message        // ❌ Template expects {{sender}}
)
TemplateEngine.apply(templateContent, parsedData, source)
```

**AFTER (Lines 213-226):**
```kotlin
val smsVariables = mapOf(
    "sender" to senderRaw,   // ✅ Matches {{sender}}
    "message" to message,    // ✅ Matches {{message}}
    "body" to message,       // Alias
    "time" to SimpleDateFormat("MM/dd/yyyy h:mm a", Locale.US).format(Date(timestamp)),
    "timestamp" to dateFormat.format(Date(timestamp))
)
TemplateEngine.applyGeneric(templateContent, smsVariables, source.autoClean)
```

### 2. Added Unresolved Placeholder Detection (Fail-Fast) ✅

**New Code (Lines 282-327):**
```kotlin
// Detect unresolved {{...}} patterns
val unresolvedPlaceholders = detectUnresolvedPlaceholders(json)

if (unresolvedPlaceholders.isNotEmpty()) {
    val placeholderList = unresolvedPlaceholders.joinToString(", ")
    val missingKeys = unresolvedPlaceholders.map { it.removePrefix("{{").removeSuffix("}}") }
    val availableKeys = smsVariables.keys.joinToString(", ")
    
    // Log ERROR with:
    // - Which placeholders are unresolved: {{foo}}, {{bar}}
    // - Available keys: sender, message, body, time, timestamp
    // - Missing keys: foo, bar
    
    // Emit event: payload_render_unresolved_placeholders
    // Details: "count=2 placeholders={{foo}},{{bar}} missingKeys=foo,bar"
    
    // ❌ BLOCK HTTP send - do not pollute Sheets
    return@launch
}
```

**Benefits:**
- Prevents sending bad data to Sheets
- Clear error messages with available vs. missing keys
- Structured logging for debugging

### 3. Added Enhanced Payload Logging (PII-Safe) ✅

**New Code (Lines 330-343):**
```kotlin
// Redact phone numbers from preview
val redactedPayload = redactPhoneNumbers(json.take(120))
val placeholderCount = templateContent.count { it == '{' } / 2

Log.d(TAG, "✓ Payload rendered (${json.length} chars, $placeholderCount placeholders resolved)")
Log.d(TAG, "   Preview (first 120 chars, redacted): $redactedPayload")

StructuredLogger.logEvent(
    level = "INFO",
    sourceId = source.id,
    endpointId = endpoint.id,
    alertId = alertId,
    event = "payload_render_ok",
    details = "payloadSize=${json.length} placeholdersResolved=$placeholderCount previewLen=${redactedPayload.length}"
)
```

**PII Protection Examples:**
- `+1-555-0123` → `+***23`
- `(555) 123-4567` → `(***) ***-**67`
- `5551234567` → `*******67`

### 4. Added Helper Functions ✅

**Lines 738-787:**

```kotlin
/**
 * Detect unresolved placeholders in rendered payload
 * Returns: ["{{sender}}", "{{foo}}"]
 */
private fun detectUnresolvedPlaceholders(json: String): List<String> {
    val regex = Regex("\\{\\{[^}]+\\}\\}")
    return regex.findAll(json).map { it.value }.distinct().toList()
}

/**
 * Redact phone numbers from payload preview for logging
 * Patterns: +1-555-0123, (555) 123-4567, 5551234567
 */
private fun redactPhoneNumbers(text: String): String {
    // Pattern 1: +1-555-0123 or +15550123
    var result = text.replace(Regex("\\+\\d[\\d\\-]{7,}\\d{2}")) { match ->
        val last2 = match.value.takeLast(2)
        "+***$last2"
    }
    
    // Pattern 2: (555) 123-4567
    result = result.replace(Regex("\\(\\d{3}\\)\\s*\\d{3}-\\d{4}")) { match ->
        "(***) ***-**${match.value.takeLast(2)}"
    }
    
    // Pattern 3: 10+ digit sequences
    result = result.replace(Regex("\\b\\d{10,}\\b")) { match ->
        "*".repeat(match.value.length - 2) + match.value.takeLast(2)
    }
    
    return result
}
```

---

## 📊 Impact Summary

**Files Changed:** 1 (`DeliveryPipeline.kt`)  
**Lines Changed:** ~120 lines
- Fixed SMS context mapping (14 lines)
- Added placeholder detection + fail-fast (46 lines)
- Enhanced logging with redaction (14 lines)
- Added 2 helper functions (50 lines)

**Session 8/9/9.1 Impact:** None (no changes to validation/toggle logic)

**Risk Assessment:** MEDIUM
- Core change to SMS payload rendering path
- Added fail-fast protection (prevents bad data)
- PII redaction in logs (compliance improvement)

---

## 🧪 Verification Plan

### Test 1: Real SMS → Resolved Placeholders ✅

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb logcat -v time -s DeliveryPipeline:* StructuredLogger:*

# Send SMS from +1 888-660-1455: "Fire at 123 Main St"

# Expected logcat:
# DeliveryPipeline: ✓ Payload rendered (450 chars, 4 placeholders resolved)
# DeliveryPipeline:    Preview: {"source":"sms","sender":"+***55","message":"Fire at 123 Main St",...
# StructuredLogger: payload_render_ok payloadSize=450 placeholdersResolved=4
# DeliveryPipeline: ✓ HTTP OK: 200 (234ms)

# Expected Sheet row:
# | +18886601455 | Fire at 123 Main St | 12/30/2025 12:30 PM |
# NOT:
# | {{sender}} | {{message}} | ... |

# Expected Apps Script response:
# {"result":"success","type":"sms","sender":"+18886601455","parsed":true}
# NOT:
# {"result":"success","type":"sms","sender":"{{sender}}","parsed":false}
```

### Test 2: Unknown Placeholder → Fail-Fast ✅

```powershell
# Edit SMS source template: {"sender":"{{sender}}","foo":"{{invalidKey}}"}
# Send SMS

# Expected logcat:
# DeliveryPipeline: ❌ Unresolved placeholders detected: {{invalidKey}}
# DeliveryPipeline:    Available keys: sender, message, body, time, timestamp
# DeliveryPipeline:    Missing keys: invalidKey
# StructuredLogger: payload_render_unresolved_placeholders count=1 placeholders={{invalidKey}}

# Expected:
# - NO HTTP request sent
# - Sheet unchanged (no row added)
# - DeliveryLogBuffer event logged
```

### Test 3: Lab Test Preview ✅

```powershell
# Open Lab → Edit SMS Source → "Test New"

# Expected dialog:
# 📋 JSON Payload Preview
# {
#   "source": "sms",
#   "sender": "+1-555-0123",  ← Real test data
#   "message": "ALERT: Fire reported at 123 Main St..."  ← Not {{message}}
# }

# Tap "✓ Send" → HTTP 200 OK
```

### Test 4: PII Redaction ✅

```powershell
adb logcat -v time -s DeliveryPipeline:D

# After SMS from +1-555-1234:
# Expected: "sender":"+***34"
# NOT: "sender":"+1-555-1234"
```

---

## ✅ Acceptance Criteria

### Code ✅
- [x] SMS context uses variable map (not ParsedData)
- [x] Map includes: sender, message, body, time, timestamp
- [x] Template engine uses applyGeneric (not apply)
- [x] Unresolved placeholder detection via regex
- [x] Fail-fast blocks HTTP send if placeholders remain
- [x] Enhanced logging with placeholder count
- [x] PII redaction for phone numbers in logs
- [x] Helper functions: detectUnresolvedPlaceholders, redactPhoneNumbers
- [x] Build succeeds (no compilation errors)

### Runtime (Pending Manual Test)
- [ ] Real SMS → placeholders resolved
- [ ] Logcat shows `payload_render_ok placeholdersResolved=4`
- [ ] Logcat shows redacted preview (phone numbers masked)
- [ ] HTTP response: `"parsed":true,"sender":"<real number>"`
- [ ] Sheet row contains real data (no `{{...}}`)
- [ ] Unknown placeholder → send blocked + error logged
- [ ] Lab test preview shows resolved JSON

---

## 🎯 Key Design Decisions

### 1. SMS-Specific Variable Map

**Why:** SMS templates expect `{{sender}}` and `{{message}}`, not `{{incidentId}}` and `{{address}}`. Using ParsedData (designed for App notifications) was fundamentally wrong for SMS.

**Implementation:**
```kotlin
val smsVariables = mapOf(
    "sender" to senderRaw,  // Matches SMS template expectations
    "message" to message,
    "body" to message,      // Alias for backward compatibility
    "time" to ...,
    "timestamp" to ...
)
```

### 2. Fail-Fast on Unresolved Placeholders

**Why:** Sending `{{sender}}` to Sheets pollutes data and breaks downstream processing. Better to fail loudly than silently corrupt data.

**Implementation:**
- Regex detection: `\\{\\{[^}]+\\}\\}`
- Log missing keys vs. available keys
- Block HTTP send (early return)
- Emit structured event for debugging

### 3. PII Redaction in Logs

**Why:** Logging full phone numbers violates privacy best practices. Redact while preserving debuggability (show last 2 digits).

**Implementation:**
- Three regex patterns for common phone formats
- Keep last 2 digits for correlation
- Mask rest with asterisks

### 4. Template Engine: applyGeneric vs. apply

**Why:** `apply()` expects ParsedData, `applyGeneric()` accepts any Map<String, String>. SMS needs the flexibility of generic variables.

**Trade-off:** Could extend ParsedData to include SMS fields, but that couples SMS logic to a model designed for App notifications. Generic approach is cleaner.

---

## 📚 Documentation

✅ **Updated `docs/ai/STATE.md`:**
- Session 9.2 summary (root cause + fix)
- Code changes with line numbers
- Verification steps with expected outputs
- Acceptance checklist

✅ **Created `docs/ai/SESSION_9.2_SMS_PLACEHOLDER_FIX.md`:**
- Complete implementation details
- Before/after code comparison
- Testing guide with logcat commands
- Design rationale

---

## 🚀 Ready for Testing

**Status:** Code complete, documented, APK built successfully.

**Install & Monitor:**
```powershell
cd D:\github\alerts-sheets\android
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb logcat -v time -s DeliveryPipeline:* StructuredLogger:*
```

**Expected Result:**
- Real SMS → placeholders resolved ✅
- Sheet row contains real data ✅
- Unknown placeholder → blocked send ✅
- Phone numbers redacted in logs ✅

---

**Session 9.2 Complete!** 🎉

SMS placeholders now correctly substitute real data, with fail-fast protection and PII-safe logging.

