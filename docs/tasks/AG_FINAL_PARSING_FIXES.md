# TASK: Final BNN Parsing Fixes - Sheet Analysis

**Priority:** P1 - Important Refinements  
**Status:** Active  
**Agent:** AG  
**Context:** Parser is mostly working, but sheet analysis reveals 3 specific issues

---

## ✅ What's Working (Great Job!)

Looking at the Google Sheet ([FD-Codes-Analytics](https://docs.google.com/spreadsheets/d/1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE/edit?gid=0#gid=0)), the parser is **mostly correct**:

1. ✅ Status extraction working ("Update" vs "New Incident")
2. ✅ State extraction clean (no "U/D" prefix)
3. ✅ Incident Details populating
4. ✅ Most FD codes extracting
5. ✅ **Row 22 (#1843844)** - Perfect example of update appending behavior!

---

## 🔧 Issues to Fix (4 Specific Problems)

### Issue 1: Incident ID Format Mismatch ⚠️

**Current Behavior (Parser.kt Line 112):**
```kotlin
incidentId = incidentId.replace("#", "") // Removes hash
// Sends: "1843844" (no hash)
```

**Apps Script Expectation (Code.gs Line 56):**
```javascript
if (idValues[i][0].toString().trim() === incidentId)
// Searches for: "#1843844" (WITH hash)
```

**Problem:** Android sends `"1843844"` but Apps Script searches Column C for `"#1843844"`.  
**Result:** Apps Script can't find existing incidents → creates duplicate rows instead of appending updates.

**Google Sheet Evidence:**
- Column C (Incident ID) shows: `#1843829`, `#1843828`, `#1843830`, etc. (all WITH hash)
- Android must send WITH hash to match

**Fix Required:**
```kotlin
// Parser.kt Lines 111-112
if (idMatcher.find()) {
    incidentId = idMatcher.group(1) ?: ""
    // KEEP the hash prefix! Apps Script expects it
    if (!incidentId.startsWith("#")) {
        incidentId = "#$incidentId"
    }
    break
}

// Fallback ID (Line 120)
incidentId = "#${hash.toString().takeLast(7).padStart(7, '0')}" // WITH hash
```

**Test:**
1. Send notification with ID `#1843844`
2. Send update with SAME ID `#1843844`
3. Verify Apps Script appends to existing row (like Row 22)

---

### Issue 2: Borough Handling (NYC Only) 🗽

**Current Behavior (Parser.kt Lines 89-99):**
```kotlin
if (state.equals("NY", ignoreCase = true) &&
    boroughs.any { p1.equals(it, ignoreCase = true) }
) {
    county = "" // Empty county ❌
    city = p1   // City = "Brooklyn"
}
```

**Problem:** When borough is the only location field, **county is left empty**.

**User Requirement:**
> "If only the borough is provided (Bronx, Brooklyn, Manhattan, Queens, Staten Island), populate BOTH county AND city with the borough name."

**Example:**
```
Input:  NY| Brooklyn| 123 Main St| Fire| ...
Current: state="NY", county="", city="Brooklyn"
Expected: state="NY", county="Brooklyn", city="Brooklyn"
```

**Why:** NYC boroughs function as BOTH county AND city. For consistency and searchability, both fields should contain the borough name.

**Fix Required:**
```kotlin
// Parser.kt Lines 89-99
if (state.equals("NY", ignoreCase = true) &&
    boroughs.any { p1.equals(it, ignoreCase = true) }
) {
    // Populate BOTH county and city with the borough name
    county = p1  // ✅ Changed from ""
    city = p1
    startMiddleIndex = 2
} else {
    // Standard: County and City are separate fields
    county = p1
    city = p2
    startMiddleIndex = 3
}
```

**Test Cases:**
```
Input:  "NY| Brooklyn| 456 Atlantic Ave| Fire| ..."
Expected: county="Brooklyn", city="Brooklyn"

Input:  "NY| Queens| 78-21 Main St| MVA| ..."
Expected: county="Queens", city="Queens"

Input:  "NJ| Bergen| Paramus| 123 Main St| Fire| ..."
Expected: county="Bergen", city="Paramus" (unchanged)
```

---

### Issue 3: FD Codes Still Contain Noise 🧹

**Current Behavior:** FD codes are being extracted, but Google Sheet still shows noise:

**From Sheet (FD Codes Columns K-U):**
- Some cells: `BNNDESK` ❌
- Some cells: `DESK` ❌
- Some cells: `nyc337` ✅ (good!)
- Some cells: Multiple codes in one cell (comma/slash separated) ❌

**Problem 1: Filter Not Catching All Noise**
```kotlin
// Parser.kt Lines 289-296 - Current filtering:
if (token.equals("BNNDESK", ignoreCase = true)) continue
if (token.equals("DESK", ignoreCase = true)) continue
if (token.equals("BNN", ignoreCase = true)) continue
```

**BUT:** Tokens containing these as substrings still get through.

**Fix Required:**
```kotlin
// Parser.kt - Enhanced filtering (replace lines 288-299):
for (token in tokens) {
    // Comprehensive noise filtering
    val lowerToken = token.lowercase()
    
    // Skip if contains ANY of these substrings
    if (lowerToken.contains("bnndesk")) continue
    if (lowerToken.contains("desk")) continue
    if (lowerToken.contains("bnn")) continue
    if (lowerToken.contains("<c>")) continue
    if (lowerToken == "|") continue
    
    // Skip ID-like patterns
    if (token.contains("#") && token.length > 5) continue
    if (token == incidentId || token == "#$incidentId") continue
    
    // Skip if too long (not a valid FD code)
    if (token.length > 20) continue
    
    // Skip if empty or just numbers
    if (token.isEmpty() || token.all { it.isDigit() }) continue
    
    // VALID FD CODE - Add it
    fdCodes.add(token)
}
```

**Problem 2: Apps Script Must Split FD Codes Into Individual Cells**

**Apps Script Current (Code.gs Lines 154-158):**
```javascript
incomingCodes.forEach((code) => {
  if (code.toLowerCase() !== "bnn" && code.toLowerCase() !== "bnndesk") {
    row.push(code);
  }
});
```

**This is CORRECT** ✅ - Each code gets its own array element → own sheet column.

**BUT:** Verify Android sends fdCodes as proper array, not comma-separated string.

**Verify in NotificationService.kt:**
```kotlin
// Should be:
val parsed = Parser.parse(fullContent)
val timestamped = parsed.copy(timestamp = TemplateEngine.getTimestamp())
jsonToSend = Gson().toJson(timestamped)

// JSON should look like:
// {"fdCodes": ["nyc337", "nyu9w", "ny153"]}  ← Array
// NOT: {"fdCodes": "nyc337,nyu9w,ny153"}     ← String
```

**Expected Sheet Result:**
| Column K | Column L | Column M | Column N |
|----------|----------|----------|----------|
| nyc337   | nyu9w    | ny153    | (empty)  |

Each unique FD code in its own cell (purple highlighted columns).

---

### Issue 4: Timestamp Format Not Human-Readable 📅

**Current Behavior:**
```kotlin
// TemplateEngine.kt getTimestamp()
fun getTimestamp(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}
```

**Sends:** `"2025-12-17T20:30:45.123Z"` (ISO 8601 - technical format)

**Google Sheet Shows:** Column B displays this exact string → Not user-friendly

**User Requirement:**
> "Make the timestamp more human-readable in the spreadsheet."

**Fix Required:**
```kotlin
// TemplateEngine.kt - Change to 12-hour format with AM/PM
fun getTimestamp(): String {
    val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)
    return sdf.format(Date())
}
```

**Examples:**
```
Before: "2025-12-17T20:30:45.123Z"
After:  "12/17/2025 8:30:45 PM"

Before: "2025-12-17T08:15:30.456Z"
After:  "12/17/2025 8:15:30 AM"
```

**Test:**
- Send test notification
- Check Google Sheet Column B (Timestamp)
- Should show: `"12/17/2025 8:30:45 PM"`
- NOT: `"2025-12-17T20:30:45.123Z"`

---

## 🏗️ **Context: Phase 1 of Larger System**

**Important:** This parsing work is **Phase 1** of a multi-phase enrichment pipeline.

**Future phases** (after your fixes):
- Phase 2: Backend enrichment service (geocoding, property data, AI)
- Phase 3: FD code translation to human language
- Phase 4: Integration with EMU/NFA Incidents apps

**See:** `/docs/architecture/ENRICHMENT_PIPELINE.md` for full roadmap.

**Your Mission (Phase 1):** Ensure Android sends **clean, consistent JSON** with correct incident IDs and FD codes. The backend enrichment service (Phase 2) will build on this foundation.

---

## 📋 Summary of Required Changes

### File: `Parser.kt`

**Change 1: Incident ID (Lines 111-112)**
```kotlin
// BEFORE:
incidentId = incidentId.replace("#", "")

// AFTER:
if (!incidentId.startsWith("#")) {
    incidentId = "#$incidentId"
}
```

**Change 2: Borough Handling (Line 92)**
```kotlin
// BEFORE:
county = ""

// AFTER:
county = p1  // Same as city for NYC boroughs
```

**Change 3: FD Codes Filtering (Lines 288-299)**
```kotlin
// BEFORE: Exact string matching
if (token.equals("BNNDESK", ignoreCase = true)) continue

// AFTER: Substring matching
val lowerToken = token.lowercase()
if (lowerToken.contains("bnndesk")) continue
if (lowerToken.contains("desk")) continue
if (lowerToken.contains("bnn")) continue
if (lowerToken.contains("<c>")) continue
```

### File: `TemplateEngine.kt`

**Change 4: Human-Readable Timestamp (Lines 309-313)**
```kotlin
// BEFORE: ISO 8601 format
fun getTimestamp(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

// AFTER: Human-readable 12-hour format
fun getTimestamp(): String {
    val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)
    return sdf.format(Date())
}
```

---

## 🧪 Testing Protocol

### Test 1: Update Appending (Most Critical)
```powershell
# 1. Send NEW incident
adb logcat | findstr "Parser BNN"

# Expected JSON:
# {"incidentId":"#1999001", "status":"New Incident", ...}

# 2. Check sheet - should create NEW row

# 3. Send UPDATE with SAME ID
# {"incidentId":"#1999001", "status":"Update", ...}

# 4. Check sheet - should APPEND to existing row (like Row 22)
# Expected: Row has "\n" in Status column showing both "New Incident" and "Update"
```

### Test 2: Borough Handling
```powershell
# Input: "NY| Brooklyn| 123 Main St| Fire| Details | <C> BNN | code1 | #1999002"

# Expected ParsedData:
# state = "NY"
# county = "Brooklyn"  ← Should NOT be empty
# city = "Brooklyn"
# address = "123 Main St"
```

### Test 3: FD Codes Clean
```powershell
# Input: "...| <C> BNN | BNNDESK/nyc337/nyu9w/DESK | #1999003"

# Expected fdCodes:
# ["nyc337", "nyu9w"]
# NOT: ["BNNDESK", "nyc337", "nyu9w", "DESK"]
```

### Test 4: Sheet Verification
After fixes, check Google Sheet:
- [ ] Column C: All IDs have `#` prefix
- [ ] Updates append to same row (newlines in cells)
- [ ] NYC boroughs: Both county AND city populated
- [ ] FD Codes (Columns K-U): One code per cell, no "BNNDESK"/"DESK"

---

## 🎯 Success Criteria

**Critical (Must Fix):**
- [ ] Incident ID includes `#` prefix (Apps Script can find existing rows)
- [ ] Updates append to existing rows (not creating duplicates)
- [ ] FD Codes cells do NOT contain: BNNDESK, DESK, BNN, <C>
- [ ] Timestamp is human-readable (e.g., "12/17/2025 8:30:45 PM")

**Important (Should Fix):**
- [ ] NYC boroughs populate both county AND city fields
- [ ] Each FD code in its own column (one per cell)

**Nice to Have:**
- [ ] Empty fields acceptable (not every notification has all data)
- [ ] Logging shows clean parsing (no noise in extracted codes)

---

## 📚 Reference Files

**Current Code:**
- `Parser.kt` - Already reviewed, mostly correct
- `NotificationService.kt` - JSON serialization (verify fdCodes is array)
- `Code.gs` - Apps Script (already correct for update logic)

**Documentation:**
- `/docs/architecture/parsing.md` - Parsing logic spec
- `/docs/architecture/HANDOFF.md` - System architecture

**Google Sheet:**
- [FD-Codes-Analytics](https://docs.google.com/spreadsheets/d/1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE/edit?gid=0#gid=0)
- **Row 22** - Perfect example of update appending behavior

---

## 🚀 Implementation Order

1. **Fix Incident ID first** (most critical - enables update appending)
2. **Fix FD Codes filtering** (removes noise from sheet)
3. **Fix Borough handling** (fills empty county fields for NYC)
4. **Test thoroughly** (verify updates append like Row 22)

---

## 💡 Key Insight

**The parser is 90% correct!** These are refinements, not major changes:
- ✅ Multi-line handling works
- ✅ Status extraction works
- ✅ State extraction works
- ✅ Most fields populating correctly
- ⚠️ Just need: ID format, borough logic, FD code cleanup

**You're almost there!** 🎉

---

## Build & Verify

```powershell
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb logcat | findstr "Parser BNN NotificationService"
```

Send test notification, verify logs show:
```
Parser: ✓ Parsed: ID=#1234567, State=NY, County=Brooklyn, City=Brooklyn
NotificationService: JSON: {"incidentId":"#1234567","fdCodes":["nyc337","ny153"]}
```

Then check Google Sheet for proper row creation/appending.

Good luck! 🚀

