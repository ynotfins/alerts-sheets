# TASK: Fix BNN Parsing - All Sheet Columns Empty

**Priority:** P0 - Critical Bug  
**Status:** Active  
**Agent:** AG  
**Context Files:** `/docs/architecture/parsing.md`, `/docs/architecture/HANDOFF.md`

---

## Problem Summary

**Symptom:** Google Sheet receives BNN notifications, but only the **timestamp** populates. All other columns (Status, State, County, City, Address, Type, Details, FD Codes) are **empty**.

**Root Cause:** `Parser.parse()` returns `null` for real BNN notifications (likely due to unexpected multi-line format with date header), causing `NotificationService` to fall back to generic template that Apps Script doesn't recognize.

---

## Expected vs Actual Behavior

### Real BNN Notification Format:
```
Update
9/15/25
U/D NJ| Monmouth| Asbury Park| Working Fire| 1107 Langford St| Command reports fire is knocked down. 2 lines in operation. Ventilation in progress. Searches are negative.| <C> BNN | njc691/nj233 | #1825164
```

### Expected Sheet Row:
| Status | Timestamp | ID | State | County | City | Address | Type | Details | FD Codes |
|--------|-----------|----|----|--------|------|---------|------|---------|----------|
| Update | 12/17/2025 18:30 | #1825164 | NJ | Monmouth | Asbury Park | 1107 Langford St | Working Fire | Command reports... | njc691, nj233 |

### Actual Sheet Row (BROKEN):
| Status | Timestamp | ID | State | County | City | Address | Type | Details | FD Codes |
|--------|-----------|----|----|--------|------|---------|------|---------|----------|
| [EMPTY] | 12/17/2025 18:30 | [EMPTY] | [EMPTY] | [EMPTY] | [EMPTY] | [EMPTY] | [EMPTY] | [EMPTY] | [EMPTY] |

---

## Fix Required

### File: `Parser.kt` (Primary)

**Issue 1: Pipe Line Not Found**
- Real notifications have "Update" and "9/15/25" lines BEFORE pipe content
- Parser must find the first line containing `|` even if it's line 3

**Current Logic (Broken):**
```kotlin
val contentLine = lines.firstOrNull { it.contains("|") }
```
**This works** ✅ BUT status extraction may be looking at wrong lines ❌

**Issue 2: Status Extraction**
- Parser extracts "U/D NJ" as status instead of "Update"
- Must check PREVIOUS LINES for "Update" keyword

**Fix:**
```kotlin
// After finding contentLine, check previous lines
val contentLineIndex = lines.indexOf(contentLine)
if (contentLineIndex > 0) {
    val prevLines = lines.subList(0, contentLineIndex)
    for (line in prevLines) {
        if (line.equals("Update", ignoreCase = true)) {
            status = "Update"
            break
        }
    }
}
```

**Issue 3: Never Return Null for Valid BNN**
- If content has `|` and `<C> BNN`, parsing MUST succeed
- Use fallback values instead of returning `null`

**Pattern:**
```kotlin
try {
    // Extract fields
    if (address.isEmpty()) address = "" // Don't fail, use empty
    if (incidentType.isEmpty()) incidentType = "Unknown"
    if (incidentId.isEmpty()) incidentId = "#${hash}"
    
    return ParsedData(...) // Always return data
} catch (e: Exception) {
    Timber.e(e, "Parse error but continuing with fallbacks")
    return ParsedData(...) // Return with fallbacks, not null
}
```

### File: `NotificationService.kt` (Secondary)

**Issue: Silent Fallback Hides Errors**

**Current (Lines 156-160):**
```kotlin
if (parsed == null) {
    // Falls back to generic, no indication of why
    jsonToSend = TemplateEngine.applyGeneric(...)
}
```

**Better:**
```kotlin
if (parsed == null) {
    Timber.e("❌ BNN PARSE FAILED for: ${fullContent.take(200)}")
    Timber.e("This should NOT happen. Parser must return data or throw.")
    // Still fallback for safety
    jsonToSend = TemplateEngine.applyGeneric(...)
}
```

---

## Testing Strategy

### Step 1: Enable Verbose Logging
I've already added:
```kotlin
// Parser.kt
Timber.d("Parser", "Parsing: $fullText")
Timber.d("Parser", "Parsed: $parsedData")

// NotificationService.kt
Timber.i("NotificationService", "PAYLOAD: $jsonToSend")
```

### Step 2: Verify Parser Output
```powershell
adb logcat | findstr "Parser"

# Expected (SUCCESS):
# Parser: Parsing 8 segments from: U/D NJ| Monmouth...
# Parser: ✓ Parsed: ID=#1825164, State=NJ, County=Monmouth

# Actual (FAILURE):
# Parser: No pipe delimiter found
# OR
# Parser: returning null due to...
```

### Step 3: Verify JSON Payload
```powershell
adb logcat -s NotificationService:I | findstr "PAYLOAD"

# Expected (SUCCESS):
# PAYLOAD: {"status":"Update","timestamp":"...","incidentId":"#1825164","state":"NJ","county":"Monmouth",...}

# Actual (FAILURE):
# PAYLOAD: {"package":"us.bnn.newsapp","title":"Update","text":"9/15/25",...}
```

### Step 4: Test Payload Button
Tap "Test Payload Now" in AppConfigActivity:
- ✅ All sheet columns should fill
- ✅ Status: "New Incident"
- ✅ State: "NJ"
- ✅ FD Codes: Multiple columns

---

## Success Criteria

- [ ] `Parser.parse()` NEVER returns `null` for valid BNN (has `|` and `<C> BNN`)
- [ ] Status correctly extracted as "Update" or "New Incident" (not "U/D NJ")
- [ ] State is clean 2-letter code (NJ, NY, PA)
- [ ] All 11 fields populate in Google Sheet
- [ ] Test payload fills every column
- [ ] Real BNN notification fills every column
- [ ] Update logic works (same incidentId appends to row)

---

## Files to Modify

**Primary:**
- `android/app/src/main/java/com/example/alertsheets/Parser.kt`

**Secondary (if needed):**
- `android/app/src/main/java/com/example/alertsheets/NotificationService.kt`

**DO NOT TOUCH:**
- `scripts/Code.gs` (Apps Script already correct)
- Build files, dependencies, manifest

---

## Build & Deploy

```powershell
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug --no-daemon
# Expected: BUILD SUCCESSFUL
```

---

## Reference Documents

- **Parsing Logic:** `/docs/architecture/parsing.md`
- **System Architecture:** `/docs/architecture/HANDOFF.md`
- **Diagnostics Guide:** `/docs/architecture/DIAGNOSTICS.md`
- **Original Prompt:** `prompt.md` (root)

---

## Priority Actions (Do These First)

1. ✅ **Read** `/docs/architecture/parsing.md` to understand current logic
2. ✅ **Add extensive logging** to Parser.kt (see where it fails)
3. ✅ **Make parser never return null** for valid BNN (use fallbacks)
4. ✅ **Extract status from previous lines** (handle multi-line format)
5. ✅ **Test with real notification** (verify logs show success)
6. ✅ **Verify Google Sheet** (all columns filled)

**Start with logging, observe the failure point, then fix root cause.**

Good luck! 🚀

