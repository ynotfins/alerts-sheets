# 🚀 AG PROMPT - Phase 1 Parsing Fixes

**Quick Start:** Copy the prompt below and give to AG.

---

## 📋 Task for AG

**Mission:** Fix 4 small parsing issues based on Google Sheet analysis.

**Read these files for full context:**
1. `/docs/tasks/AG_FINAL_PARSING_FIXES.md` (detailed analysis)
2. `/docs/tasks/AG_QUICK_FIX_SUMMARY.md` (quick reference)

**Google Sheet Reference:**  
https://docs.google.com/spreadsheets/d/1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE/edit?gid=0#gid=0

---

## 🔧 4 Fixes Required (Total: ~15 lines of code)

### Fix 1: Keep `#` in Incident ID (CRITICAL)
**File:** `Parser.kt` Line 112

**Problem:** Android sends `"1843844"` but Apps Script expects `"#1843844"` → Can't find existing rows → Creates duplicates

**Change:**
```kotlin
// BEFORE:
incidentId = incidentId.replace("#", "")

// AFTER:
if (!incidentId.startsWith("#")) {
    incidentId = "#$incidentId"
}
```

**Also fix Line 120 (fallback ID):**
```kotlin
incidentId = "#${hash.toString().takeLast(7).padStart(7, '0')}"
```

---

### Fix 2: Populate County for NYC Boroughs
**File:** `Parser.kt` Line 92

**Problem:** When state=NY and city=Brooklyn, county is left empty

**Change:**
```kotlin
// BEFORE:
county = ""  // Empty for boroughs

// AFTER:
county = p1  // Same as city for NYC boroughs
```

---

### Fix 3: Better FD Code Filtering
**File:** `Parser.kt` Lines 288-299

**Problem:** Tokens like "BNNDESKnj123" still get through exact matching

**Change:**
```kotlin
// BEFORE: Exact matching
if (token.equals("BNNDESK", ignoreCase = true)) continue

// AFTER: Substring matching
val lowerToken = token.lowercase()
if (lowerToken.contains("bnndesk")) continue
if (lowerToken.contains("desk")) continue
if (lowerToken.contains("bnn")) continue
if (lowerToken.contains("<c>")) continue
if (lowerToken == "|") continue
if (token.isEmpty() || token.all { it.isDigit() }) continue
```

---

### Fix 4: Human-Readable Timestamp
**File:** `TemplateEngine.kt` Lines 309-313

**Problem:** ISO 8601 format (`2025-12-17T20:30:45.123Z`) not user-friendly

**Change:**
```kotlin
// BEFORE: ISO 8601 (technical)
fun getTimestamp(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

// AFTER: 12-hour format with AM/PM (human-readable)
fun getTimestamp(): String {
    val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)
    return sdf.format(Date())
}
```

**Examples:**
- Before: `2025-12-17T20:30:45.123Z`
- After: `12/17/2025 8:30:45 PM`

---

## ✅ Expected Results

After your fixes, Google Sheet should show:

```
Column A (Status):     New Incident
                       Update              ← Appends with \n

Column B (Timestamp):  12/17/2025 8:30:45 PM  ← Human-readable!

Column C (ID):         #1843844            ← WITH hash

Column E (County):     Brooklyn            ← NOT empty (NYC)
Column F (City):       Brooklyn            ← Same as county

Column K (FD Code):    nyc337              ← One per cell
Column L (FD Code):    ny153               ← Clean, no "DESK"
Column M (FD Code):    nyu9w               ← Clean, no "BNNDESK"
```

**Row 22 in the sheet is the PERFECT example** - all rows should behave like this.

---

## 🧪 Testing

After making changes, build and test:

```powershell
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Test 1: Send NEW incident
# Check sheet - should create new row with #1234567 format

# Test 2: Send UPDATE with SAME ID
# Check sheet - should APPEND to existing row (like Row 22)

# Test 3: Verify timestamp
# Check Column B - should show "12/17/2025 8:30:45 PM" format

# Test 4: Verify FD codes
# Check Columns K-U - no "DESK" or "BNNDESK"
```

---

## 🎯 Priority Order

1. **Fix #1 (ID hash)** - CRITICAL - Enables update appending
2. **Fix #4 (Timestamp)** - HIGH - Makes data readable
3. **Fix #3 (FD codes)** - HIGH - Cleans up sheet
4. **Fix #2 (Borough)** - MEDIUM - Fills empty fields

---

## 📚 Context

This is **Phase 1** of a larger system. After these fixes are stable:
- Phase 2: Backend enrichment (geocoding, property APIs, AI)
- Phase 3: FD code translation to human language
- Phase 4: 100% human-readable output to EMU/NFA apps

See `/docs/architecture/ENRICHMENT_PIPELINE.md` for full roadmap.

**Your mission:** Ensure Android sends clean, consistent JSON so Phase 2 has a solid foundation.

---

## 🚀 Ready to Go!

Total changes: **~15 lines across 2 files**  
Impact: **Massive improvement in data quality + readability**

Good luck! 🎉

