# AG Quick Fix Summary (3 Changes)

## 📋 What to Read First
1. Read: `/docs/tasks/AG_FINAL_PARSING_FIXES.md` (full context)
2. Reference: [Google Sheet](https://docs.google.com/spreadsheets/d/1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE/edit?gid=0#gid=0)
3. Perfect Example: **Row 22** in sheet (shows how updates should append)

---

## 🎯 4 Required Fixes

### Fix 1: Incident ID Must Include `#` Prefix
**File:** `Parser.kt` Lines 111-112

**Current (WRONG):**
```kotlin
incidentId = incidentId.replace("#", "") // Removes hash
```

**Change To:**
```kotlin
// Keep the hash! Apps Script expects "#1234567"
if (!incidentId.startsWith("#")) {
    incidentId = "#$incidentId"
}
```

**Also Fix Line 120 (Fallback ID):**
```kotlin
incidentId = "#${hash.toString().takeLast(7).padStart(7, '0')}"
```

**Why:** Apps Script searches Column C for `"#1843844"` but Android sends `"1843844"` → No match → Creates duplicate rows instead of appending updates.

---

### Fix 2: NYC Boroughs Need Both County AND City
**File:** `Parser.kt` Line 92

**Current (WRONG):**
```kotlin
if (state.equals("NY", ignoreCase = true) &&
    boroughs.any { p1.equals(it, ignoreCase = true) }
) {
    county = "" // ❌ Empty
    city = p1
}
```

**Change To:**
```kotlin
if (state.equals("NY", ignoreCase = true) &&
    boroughs.any { p1.equals(it, ignoreCase = true) }
) {
    county = p1  // ✅ Same as city for NYC boroughs
    city = p1
}
```

**Why:** Brooklyn, Queens, Manhattan, Bronx, Staten Island function as BOTH county and city in NYC.

---

### Fix 3: FD Codes - Better Noise Filtering
**File:** `Parser.kt` Lines 288-299

**Current (WEAK):**
```kotlin
if (token.equals("BNNDESK", ignoreCase = true)) continue
if (token.equals("DESK", ignoreCase = true)) continue
```

**Change To (STRONGER):**
```kotlin
val lowerToken = token.lowercase()

// Use .contains() instead of .equals() to catch substrings
if (lowerToken.contains("bnndesk")) continue
if (lowerToken.contains("desk")) continue
if (lowerToken.contains("bnn")) continue
if (lowerToken.contains("<c>")) continue
if (lowerToken == "|") continue

// Skip empty or pure numbers
if (token.isEmpty() || token.all { it.isDigit() }) continue
```

**Why:** Tokens like "BNNDESKnj123" or "desk" still get through exact matching. Substring matching catches all variations.

---

### Fix 4: Human-Readable Timestamp
**File:** `TemplateEngine.kt` Lines 309-313

**Current (WRONG):**
```kotlin
fun getTimestamp(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}
```

**Change To:**
```kotlin
fun getTimestamp(): String {
    val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)
    return sdf.format(Date())
}
```

**Why:** ISO 8601 format (`2025-12-17T20:30:45.123Z`) is technical. Users want friendly format (`12/17/2025 8:30:45 PM`).

---

## ✅ Expected Results After Fix

### Google Sheet Row (After Update):
```
Column A (Status):     New Incident
                       Update          ← Appended with \n

Column B (Timestamp):  12/17/2025 8:30:45 PM  ← Human-readable!
                    
Column C (ID):         #1843844        ← WITH hash

Column E (County):     Brooklyn        ← NOT empty (NYC)
Column F (City):       Brooklyn        ← Same as county (NYC)

Column K (FD Code):    nyc337          ← One per cell
Column L (FD Code):    ny153           ← Clean, no "DESK"
Column M (FD Code):    nyu9w           ← Clean, no "BNNDESK"
```

---

## 🧪 Quick Test

```powershell
# Build & install
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor logs
adb logcat | findstr "Parser"

# Send test notification, verify log shows:
# Parser: ✓ Parsed: ID=#1234567 (note the # prefix!)
```

Then check Google Sheet:
- Updates append to same row (like Row 22)
- FD Codes columns have one code per cell
- No "DESK" or "BNNDESK" in FD code cells

---

## 🎯 Priority

1. **Fix #1 (ID hash)** - CRITICAL - Enables update appending
2. **Fix #4 (Timestamp)** - HIGH - Makes timestamps readable
3. **Fix #3 (FD codes)** - HIGH - Cleans up sheet
4. **Fix #2 (Borough)** - MEDIUM - Fills empty county fields

**Total Changes:** ~15 lines of code  
**Impact:** 🚀 Updates will finally append correctly + human-readable timestamps!

