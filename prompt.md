# BNN Notification Parsing - End-to-End Implementation Prompt

## Context

You are working on the **AlertsToSheets** Android application that captures BNN (Breaking News Network) incident notifications and forwards them to a Google Sheet. The system must parse pipe-delimited incident data and properly populate ALL sheet columns.

---

## Current System Architecture

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. BNN APP NOTIFICATION (Android System)                            │
│    Example: "U/D NJ| Monmouth| Asbury Park| Working Fire|           │
│              1107 Langford St| Command reports fire is knocked      │
│              down...| <C> BNN | njc691/nj233 | #1825164"            │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. NOTIFICATION LISTENER SERVICE (NotificationService.kt)           │
│    - onNotificationPosted() captures notification                   │
│    - Extracts: title, text, bigText, packageName                    │
│    - Combines into fullContent string                               │
│                                                                      │
│    Detection Logic:                                                 │
│    ├─ Package: "us.bnn.newsapp"                                     │
│    ├─ Contains: "<C> BNN" (case-insensitive)                        │
│    └─ Pipe count: >= 4 pipes in content                             │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. DEDUPLICATION CHECK (DeDuplicator.kt)                            │
│    - Uses LruCache to prevent duplicate processing                  │
│    - If duplicate: IGNORE and log                                   │
│    - If unique: Continue to parser                                  │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. BNN PARSER (Parser.kt)                                           │
│    Input: Full notification text                                    │
│                                                                      │
│    Parsing Logic:                                                   │
│    ├─ Status: Extract "U/D" → "Update", "N/D" → "New Incident"     │
│    ├─ State: First segment (strip "U/D"/"N/D" prefix)               │
│    ├─ County/City: Second & third segments (NYC borough handling)   │
│    ├─ Address: Detect street patterns (digits + suffix)             │
│    ├─ Incident Type: Short codes (10-75, QN-6738)                   │
│    ├─ Incident Details: Longest field before "<C> BNN"              │
│    ├─ FD Codes: Parse slash-separated after "<C> BNN"               │
│    └─ Incident ID: Extract "#1234567" pattern or generate hash      │
│                                                                      │
│    Output: ParsedData object with all fields                        │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. TIMESTAMP & SERIALIZATION (NotificationService.kt)               │
│    - Add ISO 8601 timestamp: TemplateEngine.getTimestamp()          │
│    - Copy ParsedData with timestamp: parsed.copy(timestamp=...)     │
│    - Serialize to JSON: Gson().toJson(timestamped)                  │
│                                                                      │
│    JSON Schema (Matches Apps Script):                               │
│    {                                                                 │
│      "status": "Update",                                             │
│      "timestamp": "2025-12-17T18:30:45.123Z",                        │
│      "incidentId": "#1825164",                                       │
│      "state": "NJ",                                                  │
│      "county": "Monmouth",                                           │
│      "city": "Asbury Park",                                          │
│      "address": "1107 Langford St",                                  │
│      "incidentType": "Working Fire",                                 │
│      "incidentDetails": "Command reports fire is knocked down...",   │
│      "originalBody": "U/D NJ| Monmouth| Asbury Park...",             │
│      "fdCodes": ["njc691", "nj233"]                                  │
│    }                                                                 │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 6. QUEUE PROCESSOR (QueueProcessor.kt)                              │
│    - Stores request in SQLite: requests.db                          │
│    - Retries on failure (exponential backoff)                       │
│    - Multiple endpoints supported                                   │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 7. HTTP POST REQUEST (NetworkClient.kt)                             │
│    URL: https://script.google.com/macros/s/.../exec                 │
│    Method: POST                                                      │
│    Content-Type: application/json                                   │
│    Body: JSON from step 5                                            │
│                                                                      │
│    OkHttp Request:                                                   │
│    - MediaType: "application/json; charset=utf-8".toMediaType()     │
│    - Body: json.toRequestBody(mediaType)                            │
│    - Logs: endpoint URL, JSON payload, HTTP status, response        │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 8. GOOGLE APPS SCRIPT (Code.gs)                                     │
│    function doPost(e) {                                              │
│      const data = JSON.parse(e.postData.contents);                  │
│      const incidentId = data.incidentId;                            │
│                                                                      │
│      // Search for existing incident by ID (Column C)               │
│      if (foundRow !== -1) {                                          │
│        // UPDATE LOGIC (Append to existing row):                    │
│        ├─ Status (Col A): Append "\n" + newStatus                   │
│        ├─ Timestamp (Col B): Append "\n" + formattedTime            │
│        ├─ Incident Type (Col H): Append "\n" + newType              │
│        ├─ Incident Details (Col I): Append "\n" + cleanDetail       │
│        ├─ Original Body (Col J): Append "\n" + originalBody         │
│        └─ FD Codes (Col K+): Merge unique codes only                │
│      } else {                                                        │
│        // NEW ROW LOGIC:                                             │
│        └─ appendRow([status, timestamp, id, state, county,          │
│                      city, address, type, details, original,        │
│                      ...fdCodes])                                    │
│      }                                                               │
│    }                                                                 │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 9. GOOGLE SHEET (FD-Codes-Analtyics)                                │
│    Sheet ID: 1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE           │
│                                                                      │
│    Columns (Header Row):                                             │
│    A: New/Update                                                     │
│    B: Timestamp                                                      │
│    C: Incident ID                                                    │
│    D: State                                                          │
│    E: County                                                         │
│    F: City                                                           │
│    G: Address                                                        │
│    H: Incident type                                                  │
│    I: Incident (Details)                                             │
│    J: Original Full Notification                                     │
│    K+: FD Codes (multiple columns)                                   │
│                                                                      │
│    Example Row (Update):                                             │
│    "New Incident    │ 12/14/2025 2:06:11 │ #1843938 │ NY │ Suffolk  │
│     Update"         │ 12/14/2025 2:08:15 │          │    │          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Expected Behavior - Properly Parsed Example

### Input Notification 1 (New):
```
NY| Suffolk| Patchogue| Working Fire| 215 River Ave| Working fire pvt dwelling | <C> BNN | BNNDESK/nyl815/nyl103 | #1843938
```

### Input Notification 2 (Update):
```
U/D NY| Suffolk| Patchogue| Working Fire| 215 River Ave| Updated address / 2 story dwelling fully involved starting mutual aid | <C> BNN | BNNDESK/bnndesk/nyl815/nyl103/ny690 | #1843938
```

### Expected Sheet Row (After Both):

| Column | Value |
|--------|-------|
| A (Status) | `"New Incident\nUpdate"` |
| B (Timestamp) | `"12/14/2025 2:06:11\n12/14/2025 2:08:15"` |
| C (Incident ID) | `#1843938` |
| D (State) | `NY` |
| E (County) | `Suffolk` |
| F (City) | `Patchogue` |
| G (Address) | `215 River Ave` |
| H (Incident Type) | `"Working Fire\nWorking Fire"` |
| I (Incident Details) | `"Working fire pvt dwelling\nUpdated address / 2 story dwelling fully involved starting mutual aid"` |
| J (Original Body) | `"New Incident\nNY\| Suffolk\|...\nUpdate\nU/D NY\| Suffolk\|..."` |
| K (FD Code 1) | `nyl815` |
| L (FD Code 2) | `nyl103` |
| M (FD Code 3) | `ny690` |

---

## Critical Issues to Verify

### Issue 1: Parser Status Extraction
**Problem:** Parser might include "U/D" or "N/D" in the state field.

**Required Fix:**
- Strip status prefix BEFORE extracting state
- `"U/D NJ"` → Status: `"Update"`, State: `"NJ"`
- `"N/D NY"` → Status: `"New Incident"`, State: `"NY"`

**File:** `Parser.kt` (lines 16-34)

**Verification:**
```kotlin
// Input: "U/D NJ| Bergen| ..."
// Expected: status = "Update", state = "NJ"
// NOT: status = "U/D NJ", state = "" 
```

### Issue 2: Original Body Field
**Problem:** Apps Script expects `originalBody` field, but parser might not populate it correctly.

**Required:**
- Parser extracts full notification text as-is
- NotificationService passes entire `fullContent` string
- JSON serialization includes it: `"originalBody": "U/D NJ| Bergen| ..."`

**Files:** 
- `Parser.kt` → sets `originalBody = fullText`
- `NotificationService.kt` → passes `fullContent` to parser
- Apps Script → reads `data.originalBody`

### Issue 3: FD Codes Parsing
**Problem:** Codes might not split correctly or include "BNNDESK" noise.

**Required Logic:**
1. Find segment after `<C> BNN` marker
2. Split by slash `/`
3. Filter out: `BNN`, `BNNDESK`, empty strings
4. Return clean array: `["njc691", "nj233"]`

**Example:**
- Input: `BNNDESK/nj153/nj137/nju9w`
- Output: `["nj153", "nj137", "nju9w"]`

**File:** `Parser.kt` (lines 137-165)

### Issue 4: Apps Script Update Logic
**Problem:** Apps Script MUST update existing rows by incident ID, not always append new rows.

**Required:**
- Search Column C for matching `incidentId`
- If found: Append new data with newlines (`\n`)
- If not found: Create new row
- FD Codes: Merge unique only (no duplicates)

**File:** `scripts/Code.gs` (lines 46-161)

**Current Status:** ✅ Already implemented correctly

### Issue 5: JSON Field Mapping
**Problem:** Android sends wrong field names or Apps Script reads wrong fields.

**Required Mapping:**

| Android ParsedData Field | JSON Key | Apps Script Field | Sheet Column |
|--------------------------|----------|-------------------|--------------|
| `status` | `"status"` | `data.status` | A (New/Update) |
| `timestamp` | `"timestamp"` | - (ignored, Apps Script generates) | B (Timestamp) |
| `incidentId` | `"incidentId"` | `data.incidentId` | C (Incident ID) |
| `state` | `"state"` | `data.state` | D (State) |
| `county` | `"county"` | `data.county` | E (County) |
| `city` | `"city"` | `data.city` | F (City) |
| `address` | `"address"` | `data.address` | G (Address) |
| `incidentType` | `"incidentType"` | `data.incidentType` | H (Incident type) |
| `incidentDetails` | `"incidentDetails"` | `data.incidentDetails` | I (Incident) |
| `originalBody` | `"originalBody"` | `data.originalBody` | J (Original Full) |
| `fdCodes[]` | `"fdCodes"` | `data.fdCodes` | K+ (FD Codes) |

**Verification Command:**
```bash
adb logcat -s TEST | findstr "JSON Payload"
# Check: All field names match exactly
```

### Issue 6: Test Payload Realism
**Problem:** Test payload doesn't match real BNN format or generates duplicate IDs.

**Required:**
- Use same parser pipeline as real notifications
- Generate unique ID per test: `#1{timestamp.last6digits}`
- Include multiple FD codes with slashes
- Match exact BNN format: `N/D NJ | County | City | Address | Type | Details | <C> BNN | E-1/L-1 | #ID`

**File:** `AppConfigActivity.kt` (lines 350-390)

**Current Status:** ✅ Already fixed

---

## Diagnostic Steps

### Step 1: Verify Parser Output
```bash
# Monitor parser execution
adb logcat | findstr "Parser BNN"

# Expected logs:
# Parser: status=Update, state=NJ (no "U/D" prefix)
# Parser: incidentId=#1825164
# Parser: fdCodes=[njc691, nj233] (no BNNDESK)
```

### Step 2: Verify JSON Payload
```bash
# Run test payload
adb logcat -s TEST

# Expected output:
# TEST: JSON Payload: {"status":"New Incident","incidentId":"#1234567","state":"NJ","county":"Test County",...,"fdCodes":["E-1","L-1"]}

# Verify ALL fields present:
# ✓ status, timestamp, incidentId, state, county, city, address, incidentType, incidentDetails, originalBody, fdCodes
```

### Step 3: Verify Sheet Update
1. Send test payload
2. Check Google Sheet for new row
3. Send SAME incident ID again (modify details)
4. Verify row UPDATES (appends) instead of creating duplicate

### Step 4: Check Apps Script Logs
```
Google Apps Script Editor → Executions
- Check for errors
- Verify `foundRow` logic (update vs new)
- Check FD codes merge behavior
```

---

## Files Requiring Attention

### Primary Files (Parsing Logic):
1. **`Parser.kt`** (lines 16-180)
   - Status extraction (strip U/D prefix)
   - State extraction (clean)
   - FD codes parsing (filter BNNDESK)
   - Incident ID fallback (hash generation)

2. **`NotificationService.kt`** (lines 118-167)
   - BNN detection logic
   - Parser invocation
   - JSON serialization
   - Original body passing

3. **`ParsedData.kt`**
   - Ensure `originalBody` is mutable (`var`)
   - Ensure `timestamp` has default value

### Secondary Files (Testing):
4. **`AppConfigActivity.kt`** (lines 350-420)
   - Test payload generation
   - Logging HTTP response

### Backend:
5. **`scripts/Code.gs`**
   - Already correct (update logic exists)
   - No changes needed

---

## Success Criteria

✅ **Parser Correctly Extracts:**
- Status: "Update" (not "U/D NJ")
- State: "NJ" (clean)
- County: "Monmouth"
- City: "Asbury Park"
- Address: "1107 Langford St"
- Incident Type: "Working Fire"
- Details: Full sentence without BNN tags
- FD Codes: `["njc691", "nj233"]` (no BNNDESK)
- Incident ID: `#1825164`

✅ **JSON Serialization Includes:**
- All 11 fields (status → fdCodes)
- Field names match Apps Script expectations exactly
- `originalBody` contains full notification text

✅ **Apps Script Behavior:**
- NEW incident: Creates new row with all columns filled
- UPDATE incident: Appends to existing row (same ID)
- FD Codes: Merges unique codes across updates

✅ **Google Sheet Result:**
- All columns populated (A through K+)
- Update rows show multiline values with `\n`
- No empty cells (except optional county for NYC)
- FD codes in separate columns (K, L, M, ...)

---

## Testing Protocol

### Test 1: New Incident
```
Input: "NY| Suffolk| Patchogue| 215 River Ave| Working Fire| Fire reported | <C> BNN | nyl815/nyl103 | #1999001"

Expected Sheet Row:
- Status: "New Incident"
- State: "NY"
- County: "Suffolk"
- City: "Patchogue"
- Address: "215 River Ave"
- FD Codes: nyl815, nyl103
```

### Test 2: Update Incident
```
Input: "U/D NY| Suffolk| Patchogue| 215 River Ave| Working Fire| Fire under control | <C> BNN | ny690 | #1999001"

Expected Sheet Row (SAME row as Test 1):
- Status: "New Incident\nUpdate"
- Timestamp: (two timestamps)
- FD Codes: nyl815, nyl103, ny690 (merged)
```

### Test 3: No FD Codes
```
Input: "NJ| Essex| Newark| 123 Main St| MVA| Vehicle accident | <C> BNN | #1999002"

Expected:
- Row created
- FD Codes columns: empty
```

### Test 4: Missing Incident ID
```
Input: "NJ| Bergen| Paramus| 456 Oak St| Gas Leak| Odor reported | <C> BNN"

Expected:
- Parser generates hash-based ID
- Row created with generated ID
```

---

## Minimal Changes Required

Based on current codebase analysis, the system is **95% correct**. Only verify/fix:

1. **Parser.kt**: Status prefix stripping (lines 16-34)
2. **NotificationService.kt**: Ensure `fullContent` passed to `originalBody`
3. **Test thoroughly**: Run diagnostics above to confirm all fields populate

**DO NOT:**
- Change Apps Script (already correct)
- Refactor existing logic (minimal changes only)
- Add new dependencies

---

## Prompt for AI Agent

**Task:** Verify and fix the BNN notification parsing pipeline to ensure ALL Google Sheet columns populate correctly.

**Requirements:**
1. Read and understand `parsing.md` specification
2. Trace execution flow from notification → sheet
3. Verify Parser.kt status extraction (no "U/D" in state)
4. Verify JSON field names match Apps Script expectations
5. Test with realistic BNN payloads
6. Ensure update logic works (same incident ID appends)

**Deliverables:**
1. Confirmation all fields parse correctly
2. Fix any discrepancies in Parser.kt
3. Test payload that fills ALL sheet columns
4. Logcat output showing clean JSON

**Constraints:**
- Minimal code changes only
- No dependency additions
- Apps Script unchanged
- Must work with existing Google Sheet structure

---

**End of Prompt**

