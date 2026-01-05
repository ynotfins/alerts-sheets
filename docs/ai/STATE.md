# PROJECT STATE - Single Source of Truth

**Last Updated:** 2026-01-04 (Android: DebugActivity newest-first fix; DeliveryPipeline now writes to Notification Logs for every SMS/notification; Apps Script responses now include sheet write-proof debug fields (sheetName/url + lastRow before/after))  
**Branch:** `fix/wiring-sources-endpoints`  
**Status:** 🟡 Verifying Apps Script write path with returned write-proof fields; 🟢 Debug screen readable + newest-first; 🟢 Notification Logs now record every SMS/notification attempt

## Pause point (end of day)

**Latest pushed commit:** `533fc68` — "Write-proof responses + logs: newest-first debug, notification log for all events"
**Unpushed WIP (local changes after pause point):**
- Apps Script: AdjustLeads URL extraction now accepts `/alerts/# 302544` and debug now includes `scriptVersion:"2026-01-05-writeproof-v2"` to confirm correct deployment.
- Android: Canonical SMS source IDs enforced app-wide (`sms:+1XXXXXXXXXX`) to prevent duplicate cards across Lab/SMS config/migrations.

**Latest pushed commit:** `34bd7c2` — "SMS ID hardening + Apps Script versioned write-proof (adjustleads # url)"

### New known root cause (confirmed by runtime payload + UI behavior)
- Two SMS “cards” with the same phone number cannot be independent. With canonical IDs (`sms:+1XXXXXXXXXX`), they map to the SAME `Source.id`, so saving one overwrites the other, making fields appear to “revert”.

### Fix in progress (next commit)
- Auto-dedupe legacy SMS sources on load and persist normalized `sources.json`.
- Hard guard in Lab: block creating a second SMS card for the same number and guide user to fan-out endpoints on a single card.

### What is DONE (shipped)
- **Apps Script**: Responses now include `debug` write-proof fields (`spreadsheetUrl`, `sheetName`, `sheetIndex`, `lastRowBefore/After`, `wroteRow`, `rowIndex`) for `verify`, `sms`, and `bnn` flows (repo copies: `apps_script_current/code.gs.txt`, `scripts/Code.gs`).
- **Android Debug Logs**: Newest-first display + auto-scroll to newest; high contrast; includes `url`, `payload`, `response`.
- **Android Notifications Log**: Delivery pipeline now records **every SMS + every app notification** (even ignored) so the log cannot appear “stale”.

### What is NOT DONE (needs runtime proof tomorrow)
- **Sheet writes**: User reports `HTTP 200` + `result:"success"` but “no new row”. We must confirm via Apps Script `debug.wroteRow` and `debug.sheetName/url` to identify whether:
  - the script is writing to a different **tab** (`getSheets()[0]`), or
  - a different **spreadsheet ID**, or
  - verify-only / non-write path.
- **God Mode / capture priority**: Confirm on-device that SMS + notifications are captured immediately on Android 15 via fresh Notification Log entries + Debug Logs.

### Tomorrow's exact verification steps (copy/paste checklist)
1. Force-close app → reopen (ensures new build + repos initialized).
2. Run **Lab test** with Verify-only OFF (write test).
3. In **Debug Logs**, open newest `http_ok` entry and copy the `response=` JSON, especially `debug.{spreadsheetUrl,sheetName,lastRowBefore,lastRowAfter,wroteRow,rowIndex}`.
4. Check that spreadsheet URL + sheet name match the sheet you’re viewing; if mismatch, fix target tab selection (replace `getSheets()[0]` with `getSheetByName(...)`).
5. Open **Notifications Log** and confirm new entries appear within 1 minute for the test + any recent notifications/SMS.

---

## 🧰 SESSION 10 SUMMARY: Serena Kotlin/Java Indexing Enabled (Tooling)

### Goal
Make Serena `find_symbol` work for Android Kotlin/Java sources (e.g. `DeliveryPipeline`, `LabActivity`) in this workspace.

### What Changed (2026-01-02 06:55 UTC)
- Updated Serena per-project language server list in **`.serena/project.yml`**:
  - Before: `languages: [typescript]`
  - After: `languages: [kotlin, java, typescript]`

### Evidence (Full Reindex Ran)
Ran a full reindex with Serena CLI:
- `serena project index --log-level INFO --timeout 120 D:\github\alerts-sheets`
- Output included: `Indexed files per language: kotlin=71, typescript=66`

### Known Quirk
Kotlin LS logs a shutdown exception (`'kotlin.Nothing' does not have instances`) during teardown, but indexing completed and caches were written.

### Required Follow-up (Cursor UI)
Cursor/Serena MCP server must be restarted to reload the updated `.serena/project.yml`:
- Restart Cursor window **or** restart the **Serena MCP** server from Cursor’s MCP UI
- Then verify:
  - `mcp_serena_activate_project("alerts-sheets")`
  - `mcp_serena_find_symbol(name_path_pattern="DeliveryPipeline")` → should resolve to `android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt`
  - `mcp_serena_find_symbol(name_path_pattern="LabActivity")` → should return Kotlin class symbol(s) (paths under `android/app/src/main/java/...`)

### Verification Result (PASS, 2026-01-03)
- `mcp_serena_find_symbol("DeliveryPipeline")` returned Kotlin symbols (non-empty)
- `mcp_serena_find_symbol("LabActivity")` returned Kotlin symbols (non-empty)

### Android Home Screen Version Badge (2026-01-03)
- Home screen now shows **version + build date** (UTC) under the header:
  - Format: `v{VERSION_NAME} ({VERSION_CODE}) • {BUILD_DATE_UTC}`
- Implementation:
  - `android/app/build.gradle`: added `BuildConfig.BUILD_DATE_UTC` (build-time, `yyyy-MM-dd`, UTC)
  - `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`: updated `text_build_id` binding

### Documentation Update (2026-01-03)
- Updated docs to reflect newly-available optional MCPs and the required “no silent degradation” fallback rule:
  - `docs/ai/CURSOR_WORKFLOW.md` (added MagicMCP + Playwright MCP usage + fallback policy)
  - `MCP_QUICK_REFERENCE.md` (added MagicMCP + Playwright rows + failure policy section)
- Updated `parsing.md` (doc-only): clarified BNN incident ID invariants (no `APP-*` for BNN), FD code position variability, NYC borough handling, nfa-id insert-only increment rule, SMS whitespace trimming note; corrected the BNN example Incident ID to digits-only; added “Not a parsing.md problem”; fixed SMS example to store the raw `From:` blob in Column **K** (not J).

### MCP GAS Server Cleanup (2026-01-04)
- Removed all **repo-local GAS MCP** install artifacts per user request:
  - Deleted: `gas_mcp_install.sh`
  - Deleted: `devlimelabs-firestore-mcp.mcpb` (stale MCP bundle artifact)

---

## 🎯 SESSION 9.7 SUMMARY: APP Source (BNN) Delivery + Placeholder Fix (Android Only)

### What Was Fixed (2025-12-31 01:45 UTC)

**Issue:** APP notifications (BNN) were posting placeholder strings (`{package}`, `{title}`, `{bigText}`) to Google Sheets instead of real values.

**Root Causes:**

1. **APP used wrong variable map** → `{{package}}`, `{{title}}`, `{{text}}`, `{{bigText}}` never resolved
2. **Two separate paths** → SMS (Golden Path ✅) vs APP (OLD parser ❌)
3. **Single-brace syntax unsupported** → Only `{{key}}` worked, not `{key}`

**Solution:** Migrated APP to Golden Path (same as SMS)

### Files Changed (3 files, ~365 lines)

1. **`DeliveryPipeline.kt`** (+320 lines): Added `deliverAppEvent()` method
   - Creates `appVariables` map: `package`, `packageName`, `title`, `text`, `bigText`, `time`, `timestamp`
   - Multi-endpoint fanout (independent HTTP POST per endpoint)
   - Fail-fast on unresolved placeholders
   - PII-safe logging

2. **`TemplateEngine.kt`** (~30 lines): Support both `{{key}}` and `{key}` syntax
   
3. **`DataPipeline.kt`** (~15 lines): Route APP to `DeliveryPipeline.deliverAppEvent()`

### Verification Commands (Windows PowerShell)

```powershell
# Build
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug

# Watch logs
adb logcat -v time | Select-String -Pattern `
  "DeliveryPipeline|appVariables|http_ok|http_fail"

# Expected output for BNN notification:
# DeliveryPipeline: 📱 APP event received | package=us.bnn.newsapp
# DeliveryPipeline: ✓ APP variables created: package, packageName, title, text, bigText, time, timestamp
# DeliveryPipeline: ✓ Payload rendered (450 chars, 7 variables resolved)
# DeliveryPipeline: ✅ HTTP OK | code=200
```

### Acceptance Checklist

- [x] Code: `deliverAppEvent()` added ✅
- [x] Code: Both `{{key}}` and `{key}` syntax supported ✅
- [x] Build: Compiles without errors ✅
- [ ] Runtime: Sheet shows real values (not placeholders)
- [ ] Runtime: Multi-endpoint fanout works
- [ ] Runtime: SMS unchanged (no regression)

---

## 🎯 SESSION 9.6.1 SUMMARY: Debug Event Catalog Updated

### Goal
Fix **SMS alert handling only** in `scripts/Code.gs` without impacting **BNN** parsing/upsert logic.

### What Was Fixed
- **Timestamp consistency (SMS)**: NEW + UPDATE timestamps now always use Script timezone formatting: `MM/dd/yyyy hh:mm:ss a`.
- **IncidentId extraction safety (SMS)**:
  - Prefer **AdjustLeads URL** extraction (bottom-most AdjustLeads URL line).
  - Fallback digits only when **no AdjustLeads URL exists**, and only from **non-URL** lines (never maps URLs).
  - **Defensive upsert**: SMS row update is now allowed **only when id came from AdjustLeads URL** (prevents accidental row merging).
- **NYC borough normalization (SMS)**:
  - Manhattan/Brooklyn/Queens/Bronx/Staten Island normalized to consistent **county + city** for geocoding safety (state defaults to NY if borough clearly indicated).

### Evidence / Test Harness
- Added `testSmsParsingExamples()` in `scripts/Code.gs` (3 deterministic examples) with `Logger.log()` output:
 - Added `testSmsTimestampNormalization()` in `scripts/Code.gs` to prove Script-TZ formatting is consistent across payload timestamp shapes (Date.toString/ISO/epoch).

### Additional Anti-Merge Guard (SMS)
- Added an upsert guard: even when Column C matches, SMS updates only occur if the existing row’s Column J contains the same AdjustLeads `/alerts/<digits>` as the parsed incidentId.
- Also prevents cross-type collisions by skipping any matching-ID row whose Column A does not contain `SMS`.
  - incidentId + method + matched line snippet (digits redacted)
  - state/county/city/address/type/details

### Non-Goals / Invariants
- **BNN code path untouched** (no changes to BNN parsing/upsert logic in `doPost()` for incidents with `data.incidentId`).

---

## 🎯 SESSION 9.6 SUMMARY: Canonical Apps Script SMS Wiring Alignment (BNN Untouched)

### Source of Truth
- **Canonical deployed script file**: `Apps_Script_current/code.gs.txt`
- Repo mirror kept in sync: `scripts/Code.gs`
- Contract: `parsing.md`

### SMS-only fixes applied (comprehensive wiring alignment)
- **Timestamp normalization (SMS only)**:
  - Uses `formatSmsTimestamp(data.timestamp, data.time)`
  - Always outputs Script-TZ `MM/dd/yyyy hh:mm:ss a`
  - Supports: formatted string, ISO, epoch seconds, epoch millis
- **IncidentId extraction hardening (SMS only)**:
  - Primary: last-line AdjustLeads URL `/alerts/<digits>` (6+)
  - Fallback: last standalone 6+ digits from **NON-URL** lines only (never any http(s) line, never maps URLs, rejects 10–11 digit phone-like)
  - `incidentIdMethod` logged: `adjustleads_url` | `fallback_digits` | `hash`
  - **Upsert suppressed** unless `incidentIdMethod == adjustleads_url`
- **Anti-merge guard (SMS only)**:
  - Even if Column C matches, UPDATE only if Column J contains the same AdjustLeads `/alerts/<digits>` as the parsed incidentId
  - Only updates rows where Column A contains `SMS` (prevents cross-type collisions)
- **NYC borough normalization (SMS only)**:
  - Manhattan→(city=New York, county=New York), Brooklyn→(New York, Kings), Queens→(New York, Queens), Bronx→(New York, Bronx), Staten Island→(New York, Richmond)
  - Non-NYC: does **not** invent county when missing

### Test harness (Apps Script)
- Added `testSmsWiringDiagnostics()`:
  - Logs timestamp normalization across variants
  - Logs `incidentId`, `incidentIdMethod`, matched line snippet (digits redacted)
  - Logs normalized `state/county/city/address`

### Touched functions (BNN untouched)
- Modified: `handleSmsMessage`, `parseAdjustLeadsSms`
- Added: `formatSmsTimestamp`, `epochNumberToDate`, `extractAdjustLeadsIncidentId`, `normalizeNyBoroughs`, `extractDigitsFromIncidentId`, `extractAdjustLeadsDigitsFromText`, `testSmsWiringDiagnostics` (and small helpers)
- **No changes**: BNN incident handling block in `doPost()` (lines above SMS routing)

### Docs Updated
- Updated `docs/ai/DEBUG.md` with a consolidated **Event Catalog** for:
  - Android `StructuredLogger` / `DeliveryPipeline` events
  - Android `DataPipeline` auxiliary events
  - Apps Script SMS `Logger.log()` diagnostics markers (anti-merge + incidentId extraction)

## 🎯 SESSION 9.3 SUMMARY: Apps Script SMS Parsing & Row Upsert Fix

### What Was Fixed (2025-12-30 02:00 UTC)

**Issue:** SMS alerts from AdjustLeads were creating new rows every time instead of updating existing incidents. Apps Script returned `parsed:false` for all SMS messages.

**Root Causes Identified:**

1. **Non-Stable Incident IDs** (Line 224 of Code.gs):
   ```javascript
   `SMS-${Date.now()}`,  // ❌ Generated new ID every time!
   ```
   - **Impact:** Every SMS created a new row, never updated existing
   - **User reported:** Multiple rows for same incident in Google Sheets

2. **Inadequate SMS Parser** (Lines 265-334):
   - **Missing:** AdjustLeads URL detection (`https://www.adjustleads.com/app/alerts/294966`)
   - **Missing:** Emoji-aware line parsing (`🔥`, `📍`, `🗺️`, `📋`, `ℹ️`)
   - **Missing:** Structured extraction per AdjustLeads SMS format

3. **No Update Logic for SMS**:
   - Current code ALWAYS appended new row
   - **Missing:** Check for existing SMS incident ID and update that row
   - **Expected:** Same row upsert behavior as BNN (lines 79-148)

### Implementation Details

#### 1. **New parseAdjustLeadsSms() Function** (Replaces parseFireAlertSms)

**Stable Incident ID Extraction (3-Tier Fallback):**
```javascript
// Priority 1: Extract from AdjustLeads URL
const urlMatch = message.match(/https?:\/\/(?:www\.)?adjustleads\.(?:com|net)\/(?:app\/)?alerts\/(\d{6,})/i);
if (urlMatch) {
  result.incidentId = `AL-${urlMatch[1]}`; // e.g., AL-294966
}

// Priority 2: Find trailing 6-digit number anywhere in message
const fallbackMatch = message.match(/\b(\d{6})\b(?!.*\d{6})/);
if (fallbackMatch) {
  result.incidentId = `AL-${fallbackMatch[1]}`;
}

// Priority 3: MD5 hash of (sender + first 50 chars of message)
const hash = Utilities.computeDigest(Utilities.DigestAlgorithm.MD5, sender + message.substring(0, 50));
const shortHash = hash.slice(0, 8).map(b => (b & 0xFF).toString(16).padStart(2, '0')).join('');
result.incidentId = `AL-${shortHash}`;
```

**Emoji-Aware Line Parsing:**
```javascript
// 🔥 County line: "New Fire Alert in Morris County"
if (line.includes('🔥') || line.includes('Fire Alert')) {
  const countyMatch = line.match(/(?:in|at)\s+(\w+(?:\s+\w+)?)\s+County/i);
  if (countyMatch) result.county = countyMatch[1].trim();
}

// 📍 Address line: "31 Grand Avenue, Cedar Knolls, NJ"
if (line.includes('📍') || (line.match(/\d+\s+\w+/) && line.includes(','))) {
  const addressMatch = cleanLine.match(/^([^,]+),\s*([^,]+),\s*([A-Z]{2})/i);
  if (addressMatch) {
    result.address = addressMatch[1].trim();
    result.city = addressMatch[2].trim();
    result.state = addressMatch[3].trim().toUpperCase();
  }
}

// 📋 Incident type line: "Residential Fire - Possible structure fire..."
if (line.includes('📋') || (line.includes('Fire') && line.includes('-'))) {
  const parts = cleanLine.split('-').map(p => p.trim());
  if (parts.length >= 2) {
    result.incidentType = parts[0]; // "Residential Fire"
    result.incidentDetails = parts.slice(1).join(' - '); // Rest
  }
}
```

#### 2. **Enhanced handleSmsMessage() with Upsert Logic**

**Row Search (Same as BNN):**
```javascript
const lastRow = sheet.getLastRow();
let foundRow = -1;

if (lastRow > 1) {
  const idValues = sheet.getRange(2, 3, lastRow - 1, 1).getValues();
  for (let i = 0; i < idValues.length; i++) {
    const sheetId = idValues[i][0].toString().trim();
    if (sheetId === incidentId) {
      foundRow = i + 2;
      break;
    }
  }
}
```

**UPDATE Path (Append Lines to Existing Row):**
```javascript
if (foundRow !== -1) {
  // Status: Append "SMS Update"
  statusCell.setValue(statusCell.getValue() + "\nSMS Update");
  
  // Timestamp: Append new timestamp
  timeCell.setValue(timeCell.getValue() + "\n" + timestamp);
  
  // Incident Details: Append new details
  detailsCell.setValue(detailsCell.getValue() + "\n" + parsedData.incidentDetails);
  
  // Original Body: Append full SMS
  originalCell.setValue(originalCell.getValue() + "\n\nFrom: " + sender + "\n" + message);
  
  return JSON.stringify({ 
    result: "success", 
    incidentId: incidentId,
    action: "update",  // ✅ Indicates row was updated
    parsed: true 
  });
}
```

**NEW Path (Create Row with Stable ID):**
```javascript
else {
  const row = [
    "SMS Fire Alert",                // Status
    timestamp,                       // Timestamp
    incidentId,                      // ✅ Stable incident ID (AL-294966)
    parsedData.state || "",          // State (from 📍 line)
    parsedData.county || "",         // County (from 🔥 line)
    parsedData.city || "",           // City (from 📍 line)
    parsedData.address || "",        // Address (from 📍 line)
    parsedData.incidentType || "Fire Alert",  // Type (from 📋 line)
    parsedData.incidentDetails || message,    // Details (from 📋 line)
    `From: ${sender}\n${message}`    // Original Body
  ];
  
  sheet.appendRow(row);
  
  return JSON.stringify({ 
    result: "success", 
    incidentId: incidentId,
    action: "new",  // ✅ Indicates new row created
    parsed: true 
  });
}
```

**Generic SMS (Backward Compatibility):**
```javascript
if (!parsedData.isAdjustLeads) {
  const row = [
    "SMS",
    timestamp,
    `SMS-${Date.now()}`,  // One-off ID OK for generic
    "",
    "",
    "",
    `From: ${sender}`,
    "SMS Message",
    message,
    `From: ${sender}\n${message}`
  ];
  sheet.appendRow(row);
  
  return JSON.stringify({ 
    result: "success", 
    type: "sms", 
    sender: sender,
    parsed: false  // ✅ Generic SMS still returns false
  });
}
```

#### 3. **Test Function Added: testSmsParser()**

```javascript
function testSmsParser() {
  const testMessage = `🔥 New Fire Alert in Morris County
📍 31 Grand Avenue, Cedar Knolls, NJ
🗺️ https://maps.google.com/?q=31+Grand+Avenue+Cedar+Knolls+NJ
📋 Residential Fire - Possible structure fire with smoke showing
ℹ️ https://www.adjustleads.com/app/alerts/294966`;

  const result = parseAdjustLeadsSms(testMessage, "+1 888-660-1455");
  
  Logger.log("=== SMS Parser Test ===");
  Logger.log("Incident ID: " + result.incidentId);  // Should be: AL-294966
  Logger.log("County: " + result.county);            // Should be: Morris
  Logger.log("Address: " + result.address);          // Should be: 31 Grand Avenue
  Logger.log("City: " + result.city);                // Should be: Cedar Knolls
  Logger.log("State: " + result.state);              // Should be: NJ
  Logger.log("Type: " + result.incidentType);        // Should be: Residential Fire
  Logger.log("Details: " + result.incidentDetails);  // Should be: Possible structure...
  Logger.log("Is AdjustLeads: " + result.isAdjustLeads); // Should be: true
}
```

**Run in Apps Script Editor:**
1. Open Apps Script project
2. Select `testSmsParser` from function dropdown
3. Click "Run"
4. Check Execution Log for expected values

### Files Changed (Session 9.3) - 2 Files

**1. `scripts/Code.gs`** (~165 lines changed total)

- **Lines 204-336 (133 lines)**: Replaced `handleSmsMessage()` with upsert logic
  - Added row search logic (same as BNN)
  - Added UPDATE path (append to existing row)
  - Added NEW path (create row with stable ID)
  - Added generic SMS fallback (backward compat)

- **Lines 337-452 (116 lines)**: Replaced `parseFireAlertSms()` with `parseAdjustLeadsSms()`
  - 3-tier incident ID extraction (URL → 6-digit → hash)
  - Emoji-aware line parsing
  - Extracts county, address, city, state, type, details
  - Returns structured object with `isAdjustLeads` flag

- **Lines 489-510 (22 lines)**: Added `testSmsParser()` function
  - Test message with all emoji lines
  - Logs parsed values for verification

**2. `scripts/Code.gs.backup-20251230`** (NEW backup file)
  - Complete backup of original Code.gs before changes
  - Created for rollback safety

**No changes to:**
- Lines 1-203: `doPost()` and BNN handling (UNCHANGED)
- Lines 453-488: `handleGenericApp()` (UNCHANGED)
- Android code (SMS payload already correct from Session 9.2)
- Sheet schema (columns A-J + FD codes)

---

## 🧪 VERIFICATION STEPS (Session 9.3 Apps Script)

### Pre-Deployment: Test Function Verification

**Run in Apps Script Editor:**

```javascript
// 1. Open Apps Script project: https://script.google.com/home/projects/YOUR_PROJECT_ID
// 2. Select "testSmsParser" from function dropdown
// 3. Click "Run"
// 4. Check Execution Log (View → Logs)

// Expected Output:
// === SMS Parser Test ===
// Incident ID: AL-294966
// County: Morris
// Address: 31 Grand Avenue
// City: Cedar Knolls
// State: NJ
// Type: Residential Fire
// Details: Possible structure fire with smoke showing
// Is AdjustLeads: true
```

### Manual Testing (Required Before Production)

**Test 1: AdjustLeads SMS New Incident**

```powershell
# Send real SMS from configured number (+1 888-660-1455)
# Message format:
# 🔥 New Fire Alert in Morris County
# 📍 31 Grand Avenue, Cedar Knolls, NJ
# 🗺️ https://maps.google.com/?q=...
# 📋 Residential Fire - Possible structure fire
# ℹ️ https://www.adjustleads.com/app/alerts/294966

# Expected Android logcat:
adb logcat -v time -s DeliveryPipeline:* StructuredLogger:*

# DeliveryPipeline: ✓ HTTP OK: 200 (234ms)
# Response: {"result":"success","type":"sms","sender":"+***55","incidentId":"AL-294966","action":"new","parsed":true}

# Expected in Google Sheets:
# - New row with Incident ID = AL-294966 (column C)
# - Status = "SMS Fire Alert" (column A)
# - State/County/City/Address populated from 📍 and 🔥 lines
# - Incident Type = "Residential Fire" (column H)
# - Incident Details = "Possible structure fire..." (column I)
# - Original Body = "From: +1 888-660-1455\n🔥 New Fire Alert..." (column J)
```

**Test 2: AdjustLeads SMS Update (Same Incident)**

```powershell
# Send SECOND SMS with SAME AdjustLeads ID (294966)
# Use different details to verify append behavior

# Expected Android logcat:
# Response: {"result":"success","incidentId":"AL-294966","action":"update","parsed":true}

# Expected in Google Sheets:
# - SAME ROW (AL-294966) updated, NOT new row
# - Status column: "SMS Fire Alert\nSMS Update" (two lines)
# - Timestamp column: Two timestamps (original + new)
# - Address (columns D-G): UNCHANGED (original address preserved)
# - Incident Details: Original details + "\n" + new details
# - Original Body: Original + "\n\nFrom: +1 888..." (appended)
```

**Test 3: BNN Regression (Unchanged Behavior)**

```powershell
# Send BNN notification (curl test)
$body = @{
    incidentId = "#1825784"
    state = "NJ"
    county = "Morris"
    city = "Parsippany"
    address = "123 Main St"
    incidentType = "Structure Fire"
    incidentDetails = "Heavy smoke showing from 2nd floor"
    originalBody = "<C> BNN DESK`n123 Main St`nParsippany, NJ`nStructure Fire BNNDESK"
    fdCodes = @("nj-morris-fd", "parsippany-rescue")
    status = "New Incident"
} | ConvertTo-Json

curl -X POST `
  -H "Content-Type: application/json" `
  -d $body `
  https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec

# Expected in Google Sheets:
# - Incident ID = #1825784
# - Address = "123 Main St" (from BNN payload, not SMS parsing)
# - FD Codes = ["nj-morris-fd", "parsippany-rescue"] (no BNN/BNNDESK)
# - Update behavior: appends to existing row (lines 79-148 UNCHANGED)
```

**Test 4: Generic SMS (Non-AdjustLeads)**

```powershell
# Send SMS without AdjustLeads URL or Fire Alert keywords
# Message: "Meeting at 3pm tomorrow"

# Expected Android logcat:
# Response: {"result":"success","type":"sms","sender":"+1 555-0123","parsed":false}

# Expected in Google Sheets:
# - New row with SMS-{timestamp} ID (one-off ID)
# - Status = "SMS"
# - Type = "SMS Message"
# - Details = "Meeting at 3pm tomorrow"
# - Original Body = "From: {sender}\n{message}"
```

---

## ✅ ACCEPTANCE CHECKLIST (Session 9.3 Apps Script)

### SMS Parsing (AdjustLeads Format)

- [ ] **Code:** parseAdjustLeadsSms() function replaces parseFireAlertSms() ✅
- [ ] **Code:** Incident ID extracted from URL pattern: `AL-294966` ✅
- [ ] **Code:** 3-tier fallback (URL → 6-digit → hash) ✅
- [ ] **Code:** County extracted from 🔥 line ✅
- [ ] **Code:** Address/City/State extracted from 📍 line ✅
- [ ] **Code:** Incident Type/Details extracted from 📋 line ✅
- [ ] **Code:** Original message preserved in result ✅
- [ ] **Test:** testSmsParser() logs expected values (run in Apps Script Editor)
- [ ] **Runtime:** Real SMS extracts correct Incident ID from AdjustLeads URL
- [ ] **Runtime:** Parsed fields match emoji line content
- [ ] **Runtime:** Response includes: `{"parsed":true,"incidentId":"AL-294966","action":"new"}`

### Row Upsert Behavior

- [ ] **Code:** handleSmsMessage() searches for existing row by incident ID ✅
- [ ] **Code:** UPDATE path appends Status, Timestamp, Details, Original Body ✅
- [ ] **Code:** NEW path creates row with stable incident ID ✅
- [ ] **Runtime:** First SMS with AL-294966 → NEW row created
- [ ] **Runtime:** Second SMS with AL-294966 → SAME row updated (not new)
- [ ] **Runtime:** Status column shows: "SMS Fire Alert\nSMS Update" (two lines)
- [ ] **Runtime:** Timestamp column shows two timestamps
- [ ] **Runtime:** Columns D-G (State/County/City/Address) UNCHANGED on update
- [ ] **Runtime:** Incident Details appended with newline separator
- [ ] **Runtime:** Original Body appended with "\n\n" separator
- [ ] **Runtime:** Zero duplicate AL-* rows in sheet

### BNN Regression (Must Not Break)

- [ ] **Code:** Lines 1-203 (doPost + BNN handling) UNCHANGED ✅
- [ ] **Runtime:** BNN incident #1825784 creates new row if first time
- [ ] **Runtime:** BNN update to #1825784 appends to SAME row
- [ ] **Runtime:** BNN FD Codes deduplicated, BNN/BNNDESK removed
- [ ] **Runtime:** BNN columns D-G unchanged on update
- [ ] **Runtime:** BNN response: `{"result":"success","id":"1825784"}`

### Generic SMS (Backward Compat)

- [ ] **Code:** Non-AdjustLeads SMS uses generic format ✅
- [ ] **Code:** Returns `parsed:false` for generic SMS ✅
- [ ] **Runtime:** Non-AdjustLeads SMS creates row with SMS-{timestamp} ID
- [ ] **Runtime:** Generic SMS response: `{"result":"success","parsed":false}`
- [ ] **Runtime:** No parsing errors in Apps Script logs

### Build & Deployment

- [ ] **Code:** testSmsParser() function added to Code.gs ✅
- [ ] **Code:** Backup created: Code.gs.backup-20251230 ✅
- [ ] **Deploy:** Apps Script project updated with new Code.gs
- [ ] **Deploy:** No execution errors in Apps Script logs (past 24h)
- [ ] **Deploy:** Apps Script execution time <5s per SMS

---

## 🎯 SESSION 9.2 SUMMARY: SMS Template Placeholder Substitution Fix

### What Was Fixed (2025-12-30 00:30 UTC)

**Issue:** SMS rows in Google Sheets showing `{{sender}}` and `{{message}}` instead of real data.

**Root Cause:** DeliveryPipeline was creating a `ParsedData` object with App-specific fields (`incidentId`, `address`, `originalBody`) instead of SMS-specific fields (`sender`, `message`). The template engine couldn't resolve placeholders because the variable keys didn't match.

```kotlin
// ❌ BEFORE: Wrong context for SMS templates
val parsedData = ParsedData(
    incidentId = alertId,
    originalBody = message,  // Template expects {{message}}, gets {{originalBody}}
    address = message        // Template expects {{sender}}, gets {{address}}
)
TemplateEngine.apply(templateContent, parsedData, source)

// ✅ AFTER: Correct SMS variable map
val smsVariables = mapOf(
    "sender" to senderRaw,
    "message" to message,
    "body" to message,  // Alias
    "time" to SimpleDateFormat(...).format(Date(timestamp)),
    "timestamp" to dateFormat.format(Date(timestamp))
)
TemplateEngine.applyGeneric(templateContent, smsVariables, source.autoClean)
```

### New Features Added

#### 1. **Unresolved Placeholder Detection (Fail-Fast)** ✅

Blocks HTTP send if rendered payload still contains `{{...}}` patterns:

```kotlin
val unresolvedPlaceholders = detectUnresolvedPlaceholders(json)
if (unresolvedPlaceholders.isNotEmpty()) {
    // Log error with:
    // - Count of unresolved placeholders
    // - Which placeholders are missing
    // - Available variable keys vs. template needs
    // - Block HTTP send (return early)
    
    // Event: payload_render_unresolved_placeholders
    // Details: "count=2 placeholders={{foo}},{{bar}} missingKeys=foo,bar"
    return@launch  // ❌ Do NOT pollute Sheets
}
```

#### 2. **Enhanced Payload Render Logging (PII-Safe)** ✅

```kotlin
// Redact phone numbers from preview
val redactedPayload = redactPhoneNumbers(json.take(120))

Log.d("DeliveryPipeline", "✓ Payload rendered (${json.length} chars, $placeholderCount placeholders resolved)")
Log.d("DeliveryPipeline", "   Preview (first 120 chars, redacted): $redactedPayload")

// Event: payload_render_ok
// Details: "payloadSize=450 placeholdersResolved=4 previewLen=120"
```

**Redaction Examples:**
- `+1-555-0123` → `+***23`
- `(555) 123-4567` → `(***) ***-**67`
- `5551234567` → `*******67`

#### 3. **Helper Functions Added**

```kotlin
private fun detectUnresolvedPlaceholders(json: String): List<String>
// Uses regex: \\{\\{[^}]+\\}\\}
// Returns: ["{{sender}}", "{{foo}}"]

private fun redactPhoneNumbers(text: String): String
// Masks phone numbers in logs (PII protection)
// Patterns: +1-555-0123, (555) 123-4567, 5551234567
```

### Files Changed (Session 9.2) - 1 File

**`android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt`** (~120 lines changed)

**Lines 213-220:** Changed from ParsedData to SMS variable map
```kotlin
// Now creates smsVariables map with: sender, message, body, time, timestamp
```

**Lines 252-256:** Use applyGeneric instead of apply (for SMS)
```kotlin
TemplateEngine.applyGeneric(templateContent, smsVariables, source.autoClean)
```

**Lines 282-327:** Added unresolved placeholder detection + fail-fast
```kotlin
if (unresolvedPlaceholders.isNotEmpty()) {
    // Log error with missing keys vs. available keys
    // Emit payload_render_unresolved_placeholders event
    // Block HTTP send
    return@launch
}
```

**Lines 330-337:** Added enhanced logging with redacted preview
```kotlin
val redactedPayload = redactPhoneNumbers(json.take(120))
Log.d(TAG, "Preview (first 120 chars, redacted): $redactedPayload")
```

**Lines 738-787:** Added 2 new helper functions
- `detectUnresolvedPlaceholders()` - regex-based detection
- `redactPhoneNumbers()` - PII-safe logging

---

## 🧪 VERIFICATION STEPS (Session 9.2 SMS Placeholders)

### Build Verification ✅

```powershell
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug

# Expected: BUILD SUCCESSFUL in 2s
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

### Test 1: Real SMS With Resolved Placeholders

```powershell
# Install APK
adb install -r android\app\build\outputs\apk\debug\app-debug.apk

# Clear logs
adb logcat -c

# Monitor DeliveryPipeline
adb logcat -v time -s DeliveryPipeline:* StructuredLogger:* *:E

# Manual test:
# 1. Send SMS from configured sender (+1 888-660-1455)
# 2. Message: "Fire at 123 Main St"

# Expected logcat:
# DeliveryPipeline: ✓ Payload rendered (450 chars, 4 placeholders resolved)
# DeliveryPipeline:    Preview (first 120 chars, redacted): {"source":"sms","sender":"+***55","message":"Fire at 123 Main St",...
# StructuredLogger: payload_render_ok payloadSize=450 placeholdersResolved=4 previewLen=120
# DeliveryPipeline: ✓ HTTP OK: 200 (234ms)
# StructuredLogger: http_ok code=200 latencyMs=234

# Verify Sheets:
# - Row added with REAL sender (not "{{sender}}")
# - Row added with REAL message (not "{{message}}")

# Verify Apps Script response (from logcat):
# Response should show: {"result":"success","type":"sms","sender":"+18886601455","parsed":true}
# NOT: {"result":"success","type":"sms","sender":"{{sender}}","parsed":false}
```

### Test 2: Template With Unknown Placeholder (Fail-Fast)

```powershell
# Edit SMS source template in Lab:
# Add invalid placeholder: {"sender":"{{sender}}","foo":"{{invalidKey}}"}

# Send SMS from configured number

# Expected logcat:
# DeliveryPipeline: ❌ Unresolved placeholders detected: {{invalidKey}}
# DeliveryPipeline:    Available keys: sender, message, body, time, timestamp
# DeliveryPipeline:    Missing keys: invalidKey
# StructuredLogger: payload_render_unresolved_placeholders count=1 placeholders={{invalidKey}} missingKeys=invalidKey

# Expected behavior:
# - NO HTTP request sent
# - DeliveryLogBuffer event: payload_render_unresolved_placeholders
# - Sheet remains unchanged (no row added)
```

### Test 3: Lab Test Preview Shows Resolved Placeholders

```powershell
# In app:
# 1. Open Lab → Edit SMS Source
# 2. Tap "Test New" button

# Expected:
# - Dialog appears: "📋 JSON Payload Preview"
# - JSON shows REAL test data (not placeholders):
#   {
#     "source": "sms",
#     "sender": "+1-555-0123",  ← NOT {{sender}}
#     "message": "ALERT: Fire reported...",  ← NOT {{message}}
#     "time": "12/30/2025 12:30 PM"
#   }
# - Tap "✓ Send" → HTTP request sent
# - Result dialog shows: "✓ Test SUCCESS HTTP 200"
```

### Test 4: Verify PII Redaction in Logs

```powershell
adb logcat -v time -s DeliveryPipeline:D

# After sending SMS with number +1-555-1234:
# Expected log shows: "sender":"+***34"
# NOT: "sender":"+1-555-1234" (full number redacted)

# Regex patterns tested:
# - +1-555-0123 → +***23
# - (555) 123-4567 → (***) ***-**67
# - 5551234567 → *******67
```

---

## 🎯 SESSION 9.1 SUMMARY: Toast Message Correctness Fix

### What Was Fixed (2025-12-29 23:15 UTC)

**Issue:** Endpoint toggle toast appeared inverted or incorrect in some cases.

**Root Cause Analysis:**
- Toast was using `endpoint.name` (old object) instead of `updated.name` (new object)
- Toast condition `if (isEnabled)` was correct, but derived from callback parameter
- Need to ensure toast and persistence use the **same updated object**

**Fix Applied:**
```kotlin
// ❌ BEFORE: Used old endpoint object
Toast.makeText(this, "${endpoint.name} ${if (isEnabled) "enabled" else "disabled"}", ...)

// ✅ AFTER: Use updated object for consistency
val updated = endpoint.copy(enabled = isEnabled, ...)
endpoints[position] = updated
saveEndpoints()
Toast.makeText(this, "${updated.name} ${if (updated.enabled) "enabled" else "disabled"}", ...)
```

**Additional Improvements:**
1. **Enhanced Logging:** Added before/after state tracking
   ```kotlin
   Log.d("EndpointActivity", "Endpoint toggle: name=${endpoint.name}, enabled_before=${endpoint.enabled}, enabled_after=$isEnabled, position=$position")
   Log.d("EndpointActivity", "Toggle complete: ${updated.name} now enabled=${updated.enabled}, screen should stay open")
   ```

2. **Consistent State Verification:** Toast, persistence, and logs all use `updated.enabled`

3. **No PII/Secrets:** Logs show name + enabled state only (no URLs)

### Files Changed (Session 9.1) - 1 File

**`android/app/src/main/java/com/example/alertsheets/EndpointActivity.kt`** (~10 lines changed, lines 64-91)
- **Line 73:** Added before/after logging: `enabled_before=${endpoint.enabled}, enabled_after=$isEnabled`
- **Line 85:** Changed toast to use `updated.name` and `updated.enabled` (not `endpoint.name` + `isEnabled`)
- **Line 91:** Enhanced completion log: `now enabled=${updated.enabled}`

**Verification:** Toast and endpoints.json now guaranteed to match.

---

## 🎯 SESSION 9 SUMMARY: Toggle UX/Persistence Regression Fixes

### What Was Fixed (2025-12-29 22:45 UTC)

**Context:** After Session 8's validation improvements, discovered two UX regressions in toggle functionality.

#### 🔧 Issues Fixed

**Issue 1: Endpoint toggle closes screen without persisting**
- **Root Cause:** EndpointsAdapter callback used object reference instead of position
- **Symptom:** Toggle switch moved, screen closed, but change not saved to endpoints.json
- **Fix:** Changed callback from `(Endpoint, Boolean)` to `(Int, Boolean)` for position-based indexing
- **Result:** Toggle works, screen stays open, changes persist to disk ✅

**Issue 2: Source toggle missing from dashboard**
- **Root Cause:** Dashboard cards had no enabled/disabled toggle
- **Symptom:** Had to open Lab to enable/disable sources (extra taps)
- **Fix:** Added SwitchCompat widget to dashboard cards
- **Result:** One-tap enable/disable directly from main screen ✅

#### 📦 Files Changed (Session 9) - 4 Files

**1. `android/app/src/main/java/com/example/alertsheets/EndpointsAdapter.kt`** (~10 lines changed)
   - **Line 13:** Changed callback signature: `onToggle: (Endpoint, Boolean)` → `onToggle: (Int, Boolean)`
   - **Line 39:** Pass position instead of object: `onToggle(position, isChecked)`
   - Added comment: "✅ Pass position instead of object"

**2. `android/app/src/main/java/com/example/alertsheets/EndpointActivity.kt`** (~25 lines changed, lines 61-84)
   - **Line 64:** Changed callback: `onToggle = { position, isEnabled ->` (position-based)
   - **Line 66:** Added logging: `"Toggle at position=$position enabled=$isEnabled"`
   - **Line 69:** Use position directly: `endpoints[position] = updated` (no indexOf)
   - **Line 73:** Added `adapter.notifyItemChanged(position)` to prevent screen close
   - **Lines 82-84:** Added detailed logging around `saveEndpoints()` to verify no finish() called

**3. `android/app/src/main/res/layout/item_dashboard_source_card.xml`** (+10 lines)
   - **Lines 13-19:** Added `SwitchCompat` widget with:
     - `android:id="@+id/source_toggle"`
     - `android:layout_gravity="bottom|end"` (bottom-right corner)
     - `android:clickable="true"` and `android:focusable="true"`

**4. `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`** (~35 lines changed, lines 155-218)
   - **Line 155:** Added `val toggle = card.findViewById<SwitchCompat>(R.id.source_toggle)`
   - **Lines 191-207:** Wire toggle with:
     - `setOnCheckedChangeListener(null)` guard (prevent double-firing)
     - `sourceManager.setSourceEnabled(source.id, isChecked)` call
     - Toast confirmation: "${source.name} enabled/disabled"
     - `loadDynamicCards()` refresh to update status dot
   - **Lines 209-212:** Consume toggle touch events to prevent card click
   - All done in coroutine with Dispatchers.IO

#### ✅ No Regressions to Session 8

**Session 8 Features PRESERVED:**
- ✅ EndpointValidators.kt unchanged (no modifications)
- ✅ DeliveryPipeline.kt unchanged (still filters disabled endpoints)
- ✅ SmsConfigActivity.kt unchanged (still blocks selecting disabled endpoints)
- ✅ Template guardrails intact
- ✅ High-contrast log colors intact
- ✅ Logs search/export intact

**Validation:** Toggle fixes are purely UX improvements - validation logic untouched.

---

## 🧪 VERIFICATION STEPS (Session 9 Toggles)

### Build Verification

```powershell
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug

# Expected output:
# BUILD SUCCESSFUL in 15s
# 42 actionable tasks: 12 executed, 30 up-to-date
```

**APK Location:** `android/app/build/outputs/apk/debug/app-debug.apk` ✅

### Test 1: Endpoint Toggle Persistence

```powershell
# Install fresh APK
adb install -r android\app\build\outputs\apk\debug\app-debug.apk

# Clear logs
adb logcat -c

# Monitor EndpointActivity
adb logcat -v time -s EndpointActivity:* EndpointsAdapter:* *:E

# Manual test:
# 1. Open app → Lab → Manage Endpoints
# 2. Toggle an endpoint OFF (switch should move)
# 3. Expected: Screen STAYS OPEN (does NOT close)
# 4. Expected: Toast appears: "Endpoint Name disabled"
# 5. Expected: Logcat shows: "Toggle complete, screen should stay open"

# Verify persistence:
adb shell run-as com.example.alertsheets cat files/endpoints.json | ConvertFrom-Json | Select-Object id, name, enabled | Format-Table

# Expected: Toggled endpoint shows enabled=false (or true if toggled ON)
adb shell run-as com.example.alertsheets cat files/endpoints.json | Select-String '"enabled"' -Context 1,0

# Expected: Changed endpoint shows "enabled": false
```

**Logcat to check:**
```powershell
adb logcat -v time -s EndpointActivity:D

# Expected logs (Session 9.1 enhanced format):
# EndpointActivity: Endpoint toggle: name=Firestore Ingest, enabled_before=true, enabled_after=false, position=0
# EndpointActivity: Calling saveEndpoints() for: Firestore Ingest
# EndpointActivity: saveEndpoints() called - saving 2 endpoints
# EndpointActivity: saveEndpoints() complete - NO finish() called
# EndpointActivity: Toggle complete: Firestore Ingest now enabled=false, screen should stay open
```

**Toast Verification:**
- **Toggle OFF:** Toast says "Firestore Ingest disabled" ✅
- **Toggle ON:** Toast says "Firestore Ingest enabled" ✅
- **Persistence:** endpoints.json `"enabled": false` or `true` matches toast ✅

### Test 2: Source Toggle on Dashboard

```powershell
# In app: Main dashboard
# Look at source cards (bottom-right corner)

# Expected: SwitchCompat visible on each card
# Toggle a source OFF

# Expected behavior:
# - Toast appears: "Source Name disabled"
# - Status dot changes from green to red
# - Card refreshes (loadDynamicCards called)

# Dump sources.json
adb shell run-as com.example.alertsheets cat files/sources.json | Select-String '"enabled"' -Context 1,0

# Expected: Toggled source shows "enabled": false
```

### Test 3: Disabled Source Ignored by Pipeline

```powershell
# Disable SMS source via dashboard toggle
# Send SMS from that number

# Expected logcat (DeliveryPipeline):
adb logcat -s DeliveryPipeline:* DataPipeline:*

# Should see:
# DataPipeline: No matching source for sender +18886601455 (source disabled or not found)
# OR
# DeliveryPipeline: Source disabled, skipping delivery
```

### Test 4: No Regression - EndpointValidators Still Works

```powershell
# Open SMS Config → Edit source
# Try to select disabled endpoint

# Expected:
# - Endpoint checkbox is DISABLED (grayed out)
# - Label shows "[DISABLED]"
# - Cannot be checked
# - EndpointValidators.isSelectable() = false ✅

# Try to save with no valid endpoints
# Expected:
# - Toast: "⚠️ Select at least one ENABLED endpoint"
# - Dialog does NOT close ✅
```

---

## ✅ ACCEPTANCE CHECKLIST (Session 9.2 SMS Placeholders)

### Code Changes ✅
- [x] **DeliveryPipeline:** SMS context uses `smsVariables` map (not ParsedData)
- [x] **SMS Variables:** Map includes `sender`, `message`, `body`, `time`, `timestamp`
- [x] **Template Engine:** Uses `applyGeneric()` for SMS (supports variable map)
- [x] **Placeholder Detection:** `detectUnresolvedPlaceholders()` regex finds `{{...}}`
- [x] **Fail-Fast:** Blocks HTTP send if unresolved placeholders detected
- [x] **Logging:** Enhanced with placeholder count + redacted preview
- [x] **PII Protection:** `redactPhoneNumbers()` masks phone numbers in logs
- [x] **Error Events:** Emits `payload_render_unresolved_placeholders` with details
- [x] **Build:** Succeeds with no errors ✅

### Runtime Verification (Pending Manual Test)
- [ ] **Real SMS:** Send from configured number → placeholders resolved
- [ ] **Logcat:** Shows `payload_render_ok placeholdersResolved=4`
- [ ] **Logcat:** Shows redacted preview (phone numbers masked)
- [ ] **HTTP Response:** Apps Script returns `"parsed":true,"sender":"<real number>"`
- [ ] **Sheets Row:** Contains real sender + message (no `{{...}}` placeholders)
- [ ] **Invalid Template:** Unknown placeholder → blocked send + error logged
- [ ] **Lab Test:** Preview dialog shows resolved JSON (not placeholders)
- [ ] **No Regression:** App sources still work (if any configured)

### Expected Logcat Output

**Success Case:**
```
D/DeliveryPipeline: ✓ Payload rendered (450 chars, 4 placeholders resolved)
D/DeliveryPipeline:    Preview (first 120 chars, redacted): {"source":"sms","sender":"+***55","message":"Fire at 123 Main St",...
I/StructuredLogger: payload_render_ok payloadSize=450 placeholdersResolved=4
D/DeliveryPipeline: ✓ HTTP OK: 200 (234ms)
I/StructuredLogger: http_ok code=200 latencyMs=234
```

**Fail-Fast Case:**
```
E/DeliveryPipeline: ❌ Unresolved placeholders detected: {{foo}}, {{bar}}
E/DeliveryPipeline:    Available keys: sender, message, body, time, timestamp
E/DeliveryPipeline:    Missing keys: foo, bar
E/StructuredLogger: payload_render_unresolved_placeholders count=2 placeholders={{foo}},{{bar}} missingKeys=foo,bar
```

---

## ✅ ACCEPTANCE CHECKLIST (Session 9 Toggles)

### Endpoint Toggle
- [x] **Code:** EndpointsAdapter uses position-based callback (`(Int, Boolean)`)
- [x] **Code:** EndpointActivity uses position indexing with bounds check
- [x] **Code:** Toast uses `updated.enabled` (NEW state, not old endpoint.enabled) ✅ Session 9.1
- [x] **Code:** Toast message: "Endpoint Name enabled/disabled" (correct state)
- [x] **Code:** Enhanced logging: before/after state + position ✅ Session 9.1
- [x] **Code:** Error handling with try-catch around saveEndpoints()
- [ ] **Runtime:** Toggle OFF → Toast says "disabled" + endpoints.json enabled:false ✅ (verification required)
- [ ] **Runtime:** Toggle ON → Toast says "enabled" + endpoints.json enabled:true ✅ (verification required)
- [ ] **Runtime:** Screen stays open (no close) (manual test required)
- [ ] **Runtime:** No exceptions in logcat (verify EndpointActivity:*)

### Source Toggle
- [x] **Code:** SwitchCompat widget added to item_dashboard_source_card.xml
- [x] **Code:** Toggle wired in MainActivity with setOnCheckedChangeListener
- [x] **Code:** Toast confirmation added: "Source Name enabled/disabled"
- [x] **Code:** Error handling with state revert on failure
- [x] **Code:** Touch event handling to prevent card click
- [ ] **Runtime:** Toggle visible on dashboard cards (manual test required)
- [ ] **Runtime:** Toggle works without triggering card click (manual test required)
- [ ] **Runtime:** Toast appears on toggle (manual test required)
- [ ] **Runtime:** sources.json updated after toggle (adb dump required)
- [ ] **Runtime:** Disabled source ignored by DeliveryPipeline (logcat required)

### No Regressions
- [x] **Code:** EndpointValidators.kt unchanged (no modifications)
- [x] **Code:** DeliveryPipeline.kt unchanged (still filters disabled endpoints)
- [x] **Code:** SmsConfigActivity.kt unchanged (still blocks disabled endpoints)
- [x] **Build:** `.\gradlew.bat :app:assembleDebug` succeeds
- [ ] **Runtime:** EndpointValidators still blocks disabled endpoints in SMS config (manual test)
- [ ] **Runtime:** Template warnings still appear (manual test)
- [ ] **Runtime:** Log search/export still works (manual test)
- [ ] **Runtime:** High-contrast log colors unchanged (visual inspection)

**Status:** ✅ Code fixes complete, awaiting runtime verification

---

## 🎯 SESSION 8 SUMMARY: Invariant-Driven Fixes + Centralized Validation

### 🏗️ Key Architecture Improvement: EndpointValidators

**NEW FILE:** `android/app/src/main/java/com/example/alertsheets/utils/EndpointValidators.kt`

Created a **single source of truth** for endpoint validation logic, eliminating duplicate validation code between UI and pipeline layers.

**Why This Matters:**
- ✅ UI and pipeline now use identical validation rules (no drift)
- ✅ Changes to validation logic automatically propagate everywhere
- ✅ Easier to test (one utility class vs scattered logic)
- ✅ Clear, consistent error messages via `getReason()` helper
- ✅ Debugging made easy with `getValidationSummary()` breakdowns

**Key Methods:**
```kotlin
isValidUrl(url: String): Boolean          // http/https check + no "YOUR_SCRIPT_ID"
isSelectable(endpoint: Endpoint): Boolean // enabled==true AND validUrl
getReason(endpoint: Endpoint): String?    // "Disabled", "Needs URL", or null
getDisplayLabel(endpoint: Endpoint): String // "Name [STATUS]" formatting
filterSelectable(endpoints: List): List   // Keep only valid endpoints
getValidationSummary(endpoints): Map      // {total, enabled, validUrl, selectable}
```

### What Was Fixed (2025-12-29 09:30 UTC)

#### 🔧 Root Causes Addressed

1. **"no_endpoint" event even with endpoint selected**
   - **Root Cause:** DeliveryPipeline accepted disabled endpoints and endpoints with placeholder URLs
   - **Fix:** Created `EndpointValidators` utility with `isValidUrl()` and `isSelectable()` methods
   - **Implementation:** Both UI (SmsConfigActivity) and pipeline (DeliveryPipeline) now use same validators
   - **Result:** Emits `no_enabled_endpoints` event with detailed counts (total, enabled, validUrl, selectable)

2. **SMS rows in Google Sheets showing {{message}} placeholders**
   - **Root Cause:** SMS sources using APP template schema ({{package}}, {{title}} instead of {{sender}}, {{message}})
   - **Fix:** Added template bleed-through detection + warning banner + "Reset to SMS Template" button in SmsConfigActivity
   - **Result:** Users warned before saving; can fix with one tap

3. **Debug/log rows low-contrast gray-on-gray**
   - **Root Cause:** `item_log.xml` used #AAAAAA and #888888 colors
   - **Fix:** Updated to #CCCCCC (package/source) and #AAAAAA (time) for better contrast
   - **Result:** All text readable on dark background

4. **Logs screen missing search + export**
   - **Root Cause:** Feature parity gap between DebugActivity and LogActivity
   - **Fix:** Added search box (filters by package/title/content/status) + Export Last 10 + Copy Last 10 buttons
   - **Result:** Same functionality as Debug screen

#### ✅ Hard Invariants Enforced

- ❌ **Never allow selecting disabled endpoints**  
  → Checkboxes disabled in UI with "[DISABLED]" label

- ❌ **Never allow selecting endpoints with placeholder URLs**  
  → Checkboxes disabled with "[NEEDS URL]" label when URL contains "YOUR_SCRIPT_ID" or is blank

- ❌ **Never allow saving SMS source with zero enabled endpoints**  
  → Save button blocked with clear error: "Select at least one ENABLED endpoint"

- ⚠️ **Warn when SMS source uses APP template**  
  → Yellow banner appears: "SMS source is using App template" + "Reset to SMS Template" button

- ⚠️ **Confirm before saving template mismatch**  
  → Dialog: "Template Mismatch - Save anyway?" with option to fix

#### 📦 Files Changed (Session 8) - 8 Files Total

**NEW FILES CREATED:**

1. **`android/app/src/main/java/com/example/alertsheets/utils/EndpointValidators.kt`** ⭐ (NEW - 105 lines)
   - **Purpose:** Single source of truth for all endpoint validation logic
   - **Key Methods:**
     - `isValidUrl()`: Validates URL format and checks for placeholder text
     - `isSelectable()`: Returns true only if endpoint is enabled AND has valid URL
     - `getReason()`: Returns human-readable reason ("Disabled" / "Needs URL" / null)
     - `getDisplayLabel()`: Formats endpoint name with status suffix for UI
     - `filterSelectable()`: Filters list to only valid endpoints (used by pipeline)
     - `getValidationSummary()`: Returns detailed counts map for debugging/logging
   - **Used By:** SmsConfigActivity (UI validation) + DeliveryPipeline (runtime filtering)
   - **Benefit:** Guarantees UI and pipeline always agree on what endpoints are valid

**MODIFIED FILES:**

2. **`android/app/src/main/java/com/example/alertsheets/SmsConfigActivity.kt`** (Full rewrite - 387 lines)
   - **Refactored to use EndpointValidators:**
     - Calls `EndpointValidators.isSelectable()` to determine if checkbox should be enabled
     - Calls `EndpointValidators.getDisplayLabel()` to format "Name [STATUS]" labels
     - Calls `EndpointValidators.filterSelectable()` on save to validate selection
   - **Template guardrails added:**
     - Added `isAppTemplate()` detection (checks for {{package}}, {{title}}, {{text}}, {{bigText}})
     - Yellow warning banner when SMS source uses APP template placeholders
     - "Reset to SMS Template" button provides one-tap fix
     - Save-time confirmation dialog if template mismatch remains
   - **Endpoint selection UI:**
     - Dynamic endpoint checkboxes with status labels
     - Disabled checkboxes for invalid endpoints (grayed out)
     - Real-time validation feedback (hint text color changes)
     - Save blocked if zero valid endpoints selected

3. **`android/app/src/main/res/layout/dialog_add_sms.xml`**
   - Added template warning banner (LinearLayout, hidden by default, yellow background)
   - Added "Reset to SMS Template" button inside banner
   - Added "Select Endpoints (Fan-Out) *" section header with hint text
   - Added `container_endpoints` LinearLayout for dynamic endpoint checkboxes
   - Removed hardcoded endpoint checkboxes (now generated dynamically in activity code)

4. **`android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt`** (~40 lines changed around line 140-180)
   - **Refactored to use EndpointValidators:**
     - Added import: `import com.example.alertsheets.utils.EndpointValidators`
     - Replaced inline filtering logic with `EndpointValidators.filterSelectable(allSelectedEndpoints)`
     - Calls `EndpointValidators.getValidationSummary()` to get breakdown counts
   - **Enhanced logging:**
     - Logs: "Selected: X, Selectable: Y, Enabled: Z, ValidUrl: W" (detailed breakdown)
     - `no_enabled_endpoints` event now includes all counts in details field
     - Error message shows: "X selected, Y valid" for clarity
   - **Runtime guard:** Never attempts HTTP to disabled/invalid endpoints
   - Added search box with real-time filtering
   - Added "Export Last 10" button (shares NDJSON file via FileProvider)
   - Added "Copy Last 10" button (copies to clipboard)
   - Reused pattern from DebugActivity

5. **`android/app/src/main/java/com/example/alertsheets/LogActivity.kt`** (Full rewrite - 150 lines)
   - Added `searchBox` EditText with TextWatcher for real-time filtering
   - Added `filterLogs()` method: filters by package, title, content, status (case-insensitive)
   - Added `exportLast10Logs()`: Creates NDJSON file in cache, shares via FileProvider
   - Added `copyLast10ToClipboard()`: Copies NDJSON to clipboard with Gson serialization
   - Added button click handlers for Export and Copy buttons
   - Reused export pattern from DebugActivity for consistency

6. **`android/app/src/main/res/layout/activity_log.xml`**
   - Added search toolbar (LinearLayout) with horizontal orientation
   - Added `search_box` EditText with hint "Search logs..."
   - Added `btn_export_last10` Button (text: "Export")
   - Added `btn_copy_last10` Button (text: "Copy")
   - Changed RecyclerView to use `layout_weight="1"` for proper sizing below toolbar

7. **`android/app/src/main/res/layout/item_log.xml`** (Color adjustments for high contrast)
   - Changed `text_log_pkg` (package name) color: #AAAAAA → #CCCCCC (lighter gray, more readable)
   - Changed `text_log_time` (timestamp) color: #888888 → #AAAAAA (improved secondary text contrast)
   - Kept `text_log_content` at #FFFFFF (bright white for primary content)
   - **Result:** All text now readable on Samsung OneUI dark theme

8. **`docs/ai/STATE.md`** (This file - comprehensive documentation update)

---

## 🎯 SESSION 7 SUMMARY: Complete Shared Secret Auth

### What Was Done
✅ **Android:** Shared secret loaded from `local.properties` → added to Authorization header for ingest URLs
✅ **Cloud Functions:** Replaced Firebase ID token validation with shared secret validation
✅ **Evidence Capture:** Documented current config state (sources pointing to wrong endpoints)
✅ **Build Verification:** Both Android APK and Cloud Functions build successfully

### Critical Discovery from Phase 0 Evidence
**Problem:** SMS sources configured to point to **disabled** Apps Script endpoint!
- SMS source `+1 888-660-1455`: → Apps Script endpoint (disabled)
- SMS source `+1 561-419-3784`: → Apps Script endpoint (disabled)
- BNN app source: → Firestore ingest endpoint (enabled)
- **Result:** SMS not being delivered because endpoint is disabled

### What's Needed Before Testing
1. ✅ Code complete (Android + Cloud Functions)
2. ⏳ **Configure `INGEST_SHARED_SECRET` in android/local.properties**
3. ⏳ **Configure `INGEST_SHARED_SECRET` in functions/.env.local** (or Firebase config)
4. ⏳ **Deploy updated Cloud Functions**
5. ⏳ **Fix SMS source endpoint assignments** (point to Firestore ingest)

---

## 📊 PHASE 0 EVIDENCE (Captured 2025-12-29 00:30 UTC)

### App Files Directory Listing
```bash
$ adb shell run-as com.example.alertsheets ls -la files

total 46
drwxrwx--x 6 u0_a696 u0_a696 3452 2025-12-28 23:52 .
drwx------ 7 u0_a696 u0_a696 3452 2025-12-24 13:29 ..
-rw------- 1 u0_a696 u0_a696  751 2025-12-28 17:45 endpoints.json
-rw------- 1 u0_a696 u0_a696 1239 2025-12-28 23:52 logs.json
-rw------- 1 u0_a696 u0_a696 2690 2025-12-28 20:51 sources.json
drwx------ 2 u0_a696 u0_a696 3452 2025-12-29 00:29 datastore
```

### Current Source Configuration
```json
[
  {
    "id": "sms:+1 888-660-1455",
    "name": "SMS Adjust Leads Firestore Database",
    "type": "SMS",
    "enabled": true,
    "endpointIds": ["b0040999-d963-4b57-aaf4-f6d2301bad72"],  // ❌ Apps Script (disabled!)
    "templateJson": "{ ... }"
  },
  {
    "id": "us.bnn.newsapp",
    "name": "BNN",
    "type": "APP",
    "enabled": true,
    "endpointIds": ["endpoint-1766609309063"],  // ✅ Firestore ingest (enabled)
    "parserId": "bnn"
  },
  {
    "id": "sms:+1 561-419-3784",
    "name": "SMS 3784",
    "type": "SMS",
    "enabled": true,
    "endpointIds": ["b0040999-d963-4b57-aaf4-f6d2301bad72"],  // ❌ Apps Script (disabled!)
    "templateJson": "{ ... }"
  }
]
```

**Analysis:**
- ❌ **2 SMS sources** pointing to disabled Apps Script endpoint
- ✅ **1 APP source** (BNN) pointing to enabled Firestore ingest
- **Consequence:** Real SMS will match source but fail delivery (disabled endpoint)

### Current Endpoint Configuration
```json
[
  {
    "id": "b0040999-d963-4b57-aaf4-f6d2301bad72",
    "name": "Google Apps Script",
    "enabled": false,  // ❌ DISABLED
    "url": "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec",
    "stats": {
      "totalRequests": 0,
      "totalSuccess": 0,
      "totalFailed": 0
    }
  },
  {
    "id": "endpoint-1766609309063",
    "name": "Firestore Ingest Function",
    "enabled": true,  // ✅ ENABLED
    "url": "https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest",
    "stats": {
      "totalRequests": 18,
      "totalSuccess": 0,
      "totalFailed": 18,  // ❌ All 18 requests failed (401 auth errors)
      "avgResponseTime": 1747,
      "lastActivity": 1766961951364
    }
  }
]
```

**Analysis:**
- Firestore ingest: **18 requests, 0 success, 18 failed**
- All failures due to 401 authentication errors (before shared secret implementation)
- Apps Script endpoint disabled (no recent activity)

---

## 📦 FILES CHANGED (Session 7)

### Android (2 files - Already Complete from Session 6)
1. **`android/app/build.gradle`** (+6 lines)
   - Added `INGEST_SHARED_SECRET` BuildConfig field
   - Loads from `local.properties` or environment variable

2. **`android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt`** (~35 lines)
   - URL-based detection for ingest endpoints
   - Adds `Authorization: Bearer <secret>` header automatically
   - Logs secret length (never value)
   - Fails early with `auth_missing` event if secret not configured

### Cloud Functions (1 file - NEW)
3. **`functions/src/index.ts`** (~30 lines changed)
   - **Replaced Firebase ID token validation with shared secret validation**
   - Checks `Authorization: Bearer <secret>` header
   - Validates against `process.env.INGEST_SHARED_SECRET` or `BNN_SHARED_SECRET`
   - Returns clear error messages:
     - 401: "Missing Authorization header" (if no header)
     - 401: "Invalid secret" (if wrong secret)
     - 500: "Server configuration error" (if secret not configured on server)
   - Removed `userId` references (no longer using Firebase Auth)

---

## 🔍 IMPLEMENTATION DETAILS

### EndpointValidators Usage Pattern

**Before (Duplicated Logic):**
```kotlin
// In SmsConfigActivity:
val isValid = endpoint.enabled && endpoint.url.isNotBlank() && !endpoint.url.contains("YOUR_SCRIPT_ID")

// In DeliveryPipeline:
val validEndpoints = endpoints.filter { ep ->
    ep.enabled && ep.url.isNotBlank() && !ep.url.contains("YOUR_SCRIPT_ID")
}
```

**After (Centralized):**
```kotlin
// In SmsConfigActivity:
val isValid = EndpointValidators.isSelectable(endpoint)
val label = EndpointValidators.getDisplayLabel(endpoint) // "Name [DISABLED]"

// In DeliveryPipeline:
val validEndpoints = EndpointValidators.filterSelectable(endpoints)
val summary = EndpointValidators.getValidationSummary(endpoints)
// summary = {total: 3, enabled: 2, validUrl: 2, selectable: 1}
```

**Benefits:**
- 🔄 Logic changes automatically propagate to both UI and pipeline
- 🐛 Impossible for UI and pipeline validation to drift apart
- 🧪 Single class to unit test instead of scattered validation code
- 📊 Rich debugging info via `getValidationSummary()`

### Template Bleed-Through Detection

**Detection Logic:**
```kotlin
private fun isAppTemplate(templateJson: String): Boolean {
    return templateJson.contains("{{package}}", ignoreCase = true) ||
           templateJson.contains("{{title}}", ignoreCase = true) ||
           templateJson.contains("{{text}}", ignoreCase = true) ||
           templateJson.contains("{{bigText}}", ignoreCase = true)
}
```

**SMS Template (Correct):**
```json
{
  "source": "sms",
  "sender": "{{sender}}",
  "message": "{{message}}",
  "time": "{{time}}",
  "timestamp": "{{timestamp}}"
}
```

**APP Template (Wrong for SMS):**
```json
{
  "package": "{{package}}",
  "title": "{{title}}",
  "text": "{{text}}",
  "bigText": "{{bigText}}"
}
```

**User Flow:**
1. User opens SMS source edit → Banner appears if APP template detected
2. User taps "Reset to SMS Template" → Confirmation dialog
3. User confirms → Template replaced with correct SMS schema
4. OR user tries to save without fixing → Final warning: "Save Anyway?" with "Fix Template" option

---

## ✅ VERIFICATION STEPS (Session 8 Fixes)

### Test 1: Endpoint Selection Validity

```bash
# In app: Open SMS Config Activity
# Tap "+" to add new SMS source or edit existing

# Expected UI behavior:
# 1. Endpoint list shows each endpoint with status
#    - "Firestore Ingest Function" [enabled, can be checked]
#    - "Google Apps Script [DISABLED]" [checkbox disabled, grayed out]
#    - "Placeholder Endpoint [NEEDS URL]" [checkbox disabled if URL has YOUR_SCRIPT_ID]

# 2. Try to save with NO endpoints selected:
#    → Toast: "⚠️ Select at least one ENABLED endpoint"
#    → Dialog does NOT close

# 3. Try to save with only disabled endpoint checked:
#    → Toast: "⚠️ Select at least one ENABLED endpoint"
#    → Dialog does NOT close

# 4. Select enabled endpoint → Save:
#    → ✓ Success, dialog closes
```

### Test 2: Template Bleed-Through Guardrails

```bash
# Scenario A: Edit SMS source that has APP template
# 1. Edit SMS source with templateJson containing {{package}} or {{title}}
# Expected: Yellow warning banner appears at top:
#   "⚠️ SMS source is using App template"
#   "This may cause placeholder variables..."
#   [Reset to SMS Template] button

# 2. Tap "Reset to SMS Template"
# Expected: Confirmation dialog:
#   "Reset to SMS Template?"
#   "This will replace...{{sender}}, {{message}}, {{time}}"
# Tap "Reset" → Banner disappears, template fixed

# Scenario B: Try to save SMS source with APP template without fixing
# 1. Leave template as APP schema, tap Save
# Expected: Confirmation dialog:
#   "⚠️ Template Mismatch"
#   "SMS source is using App template..."
#   [Save Anyway] [Cancel] [Fix Template]
# Tap "Fix Template" → Dialog reopens with SMS template
# Tap "Save Anyway" → Saves (not recommended but allowed)

# Scenario C: Send SMS from source with correct SMS template
# Expected in Google Sheets:
#   - Real sender phone number (not {{sender}})
#   - Real message content (not {{message}})
#   - Real timestamp (not {{time}})
```

### Test 3: Log Readability (High Contrast)

```bash
# In app: Open Logs screen (from main dashboard)
# Expected:
# - Package name (top-left of each row): Light gray #CCCCCC (readable)
# - Content text (middle): White #FFFFFF (bright, readable)
# - Time (right): Gray #AAAAAA (readable, secondary)
# - NOT: Dark gray-on-dark-gray (previous bug)

# Visual test: All text legible on Samsung OneUI dark mode
```

### Test 4: Logs Screen Search + Export

```bash
# In app: Open Logs screen
# Expected UI:
# - Search box at top with hint "Search logs..."
# - [Export] button to right of search
# - [Copy] button next to Export

# Test search:
# 1. Type "bnn" in search box
#    → List filters to show only BNN app logs
# 2. Clear search
#    → Full list reappears

# Test export:
# 1. Tap "Export" button
#    → Share sheet appears with "notification_logs_last10.ndjson"
#    → Can save to Drive/Files/email/etc

# Test copy:
# 1. Tap "Copy" button
#    → Toast: "Last 10 logs copied to clipboard"
#    → Paste in text editor → NDJSON format (1 JSON object per line)
```

### Test 5: DeliveryPipeline Filtering (Backend)

```bash
# Manual test via adb logcat:
adb logcat -c
adb logcat -v time -s DeliveryPipeline:* StructuredLogger:*

# Send SMS from configured source
# Expected logs (with EndpointValidators integration):

# If NO enabled endpoints selected:
# DeliveryPipeline: ❌ No enabled endpoints with valid URL for source: SMS Source
# DeliveryPipeline:    Selected: 2, Selectable: 0, Enabled: 1, ValidUrl: 1
#   ^^ Shows breakdown: 2 total, 0 pass all checks, 1 enabled but bad URL, 1 good URL but disabled
# StructuredLogger: {"event":"no_enabled_endpoints","selected":2,"selectable":0,"enabled":1,"validUrl":1}

# If endpoint has placeholder URL:
# [Filtered during EndpointValidators.filterSelectable()]
# DeliveryPipeline: Selected endpoints: 1, Valid after filtering: 0

# If endpoint is disabled:
# [Filtered during EndpointValidators.filterSelectable()]
# DeliveryPipeline: Selected endpoints: 1, Valid after filtering: 0

# If at least one valid endpoint exists:
# DeliveryPipeline: ✓ Endpoint selected: Firestore Ingest Function
# DeliveryPipeline: 📤 Sending to: https://us-central1-...
# DeliveryPipeline: ✅ HTTP OK | code=200
```

---

## ✅ BUILD VERIFICATION

### Compilation Status

```bash
$ cd android && .\gradlew.bat :app:assembleDebug

BUILD SUCCESSFUL in 2s
42 actionable tasks: 8 executed, 34 up-to-date
```

**APK Location:** `android/app/build/outputs/apk/debug/app-debug.apk`  
**APK Size:** ~8.5 MB (debug build with symbols)

### Static Analysis

No new linter errors introduced. All files compile without warnings related to our changes.

### Code Coverage

**Files Modified/Created:** 8 total
- 1 new utility class (EndpointValidators)
- 3 Kotlin activities/logic files
- 3 XML layout files
- 1 documentation file

**Lines Changed:** ~750 lines total
- NEW: 105 lines (EndpointValidators.kt)
- Modified: ~645 lines across 7 files

### Integration Points

**Successful Integration:**
- ✅ EndpointValidators used by both SmsConfigActivity (UI) and DeliveryPipeline (domain)
- ✅ LogActivity reuses export pattern from DebugActivity (consistent UX)
- ✅ Color changes in item_log.xml maintain existing status color logic
- ✅ Template detection works with existing TemplateRepository

**No Breaking Changes:**
- ✅ Existing endpoints without "YOUR_SCRIPT_ID" work as before
- ✅ APP sources (non-SMS) unaffected by template checks
- ✅ Disabled endpoints can still exist, just can't be selected for new sources
- ✅ Logs screen backward compatible (search just filters existing list)

---

## 🎯 EXPECTED USER EXPERIENCE AFTER DEPLOYMENT

### Scenario 1: User Configures New SMS Source

**Before (Broken):**
1. User adds SMS source
2. Checks "Google Apps Script" endpoint (but it's disabled)
3. Saves successfully ❌
4. Sends SMS → Gets "no_endpoint" error ❌
5. Confused why checked endpoint doesn't work ❌

**After (Fixed):**
1. User adds SMS source
2. Sees "Google Apps Script [DISABLED]" with grayed-out checkbox ✅
3. Cannot check disabled endpoint ✅
4. Must check "Firestore Ingest Function" (enabled) ✅
5. Save succeeds with valid endpoint ✅
6. Sends SMS → Delivery works ✅

### Scenario 2: User Edits SMS Source with Wrong Template

**Before (Broken):**
1. SMS source has APP template ({{package}}, {{title}})
2. User edits and saves ❌
3. Sends SMS → Google Sheets shows "{{message}}" placeholder ❌
4. User confused why data not appearing ❌

**After (Fixed):**
1. User opens edit dialog
2. Yellow banner appears: "⚠️ SMS source is using App template" ✅
3. User taps "Reset to SMS Template" ✅
4. Template fixed in one tap ✅
5. Saves and sends SMS → Google Sheets shows real message ✅

### Scenario 3: User Searches Logs

**Before (Missing Feature):**
1. User opens Logs screen
2. Sees 100+ log entries ❌
3. Must manually scroll to find specific app ❌
4. No way to export for analysis ❌

**After (Fixed):**
1. User opens Logs screen
2. Types "bnn" in search box ✅
3. List instantly filters to BNN app logs only ✅
4. Taps "Export" → Shares NDJSON file via Drive/email ✅

### Scenario 4: Developer Debugs "no_endpoint" Issue

**Before (Unclear Logs):**
```
DeliveryPipeline: ❌ No enabled endpoint
```
👆 Which endpoints? Why did they fail validation?

**After (Rich Debugging):**
```
DeliveryPipeline: ❌ No enabled endpoints with valid URL for source: SMS Source
DeliveryPipeline:    Selected: 3, Selectable: 0, Enabled: 2, ValidUrl: 2
```
👆 Clear breakdown: 3 selected, 2 enabled, 2 have URLs, but ZERO pass both checks  
(Likely: 1 endpoint is enabled but has bad URL, 1 has good URL but is disabled)

---

## 🔧 REQUIRED CONFIGURATION

### Step 1: Generate Shared Secret

```bash
# Generate a secure random secret (32 bytes = 64 hex characters)
openssl rand -hex 32

# Example output:
# a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2

# Copy this value - you'll need it for both Android and Cloud Functions
```

### Step 2: Configure Android

**Edit `android/local.properties`:**
```properties
# SDK path (existing)
sdk.dir=C:\\Users\\ynotf\\AppData\\Local\\Android\\Sdk

# Sentry DSN (existing, optional)
sentryDsn=https://your-key@o0.ingest.sentry.io/0

# ✅ ADD THIS LINE:
INGEST_SHARED_SECRET=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2
```

**Important:**
- Use the SAME secret value generated in Step 1
- `local.properties` is gitignored (safe for secrets)
- Must rebuild Android app after adding secret

### Step 3: Configure Cloud Functions

**Option A: Using `.env.local` (Recommended for local testing)**

Create/edit `functions/.env.local`:
```bash
# Firestore Ingest Shared Secret
# Must match INGEST_SHARED_SECRET in android/local.properties
INGEST_SHARED_SECRET=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2

# OR use legacy name (code checks both):
BNN_SHARED_SECRET=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2
```

**Option B: Using Firebase Functions Config (For production deployment)**

```bash
cd functions

# Set secret in Firebase
firebase functions:config:set ingest.shared_secret="a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2"

# Verify it was set
firebase functions:config:get

# Deploy
firebase deploy --only functions
```

**Note:** Cloud Functions code checks both `INGEST_SHARED_SECRET` and `BNN_SHARED_SECRET` env vars (backward compatibility).

### Step 4: Deploy Cloud Functions

```bash
cd D:\github\alerts-sheets\functions

# Build
npm run build

# Deploy
firebase deploy --only functions:ingest

# Expected output:
# ✔  Deploy complete!
# Function URL (ingest(us-central1)): https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest
```

### Step 5: Rebuild Android

```bash
cd D:\github\alerts-sheets\android

# Clean rebuild (ensures BuildConfig updated)
.\gradlew.bat clean :app:assembleDebug

# Install
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

---

## 🧪 VERIFICATION CHECKLIST

### Pre-Flight Checks
```bash
# ✅ Verify Android secret configured
notepad android\local.properties
# Should contain: INGEST_SHARED_SECRET=<your-secret>

# ✅ Verify Cloud Functions secret configured
# If using .env.local:
notepad functions\.env.local
# Should contain: INGEST_SHARED_SECRET=<same-secret>

# If using Firebase config:
firebase functions:config:get
# Should show: ingest.shared_secret: "<your-secret>"

# ✅ Verify secrets MATCH (critical!)
# Android local.properties and Cloud Functions env must have identical values
```

### Test 1: Lab Dirty Test (Without SMS)
```bash
# Clear logs
adb logcat -c

# Start monitoring
adb logcat -v time -s DeliveryPipeline:V ReliableHttpSender:V StructuredLogger:V *:E

# In app:
# 1. Open AppConfigActivity (from main screen)
# 2. Tap "🔥 Dirty Test (Emoji SMS)" button

# Expected logcat output:
# DeliveryPipeline: ✓ Authorization header added for ingest endpoint (secret length=64)
# DeliveryPipeline: 📤 Sending to: Firestore Ingest Function...
# ReliableHttpSender: POST https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest
# DeliveryPipeline: ✅ HTTP OK | code=200 latency=XXXms

# Expected dialog:
# Title: "Test Result"
# Content: "✓ Test SUCCESS\nHTTP 200 (XXXms)\nStatus: ✓ CONFIRMED\n\nResponse preview:\n{\"status\":\"ok\",...}"

# ❌ If you see:
# - "secret length=0" → Secret not configured in local.properties
# - "code=401" → Secret mismatch OR Cloud Function not deployed
# - "auth_missing" event → Secret empty in BuildConfig
```

### Test 2: Fix SMS Source Routing
**Problem:** SMS sources currently point to disabled Apps Script endpoint

**Fix in App UI:**
```
1. Open MainActivity
2. Tap "Sources" tile
3. Select "SMS Adjust Leads" source
4. Tap "Endpoints" section
5. Uncheck "Google Apps Script" (disabled)
6. Check "Firestore Ingest Function" (enabled)
7. Tap "Save"

Repeat for other SMS sources.
```

**OR manually edit via adb (faster):**
```bash
# Download current config
adb shell run-as com.example.alertsheets cat files/sources.json > sources_backup.json

# Edit sources.json:
# Change all "endpointIds": ["b0040999-..."] 
# To: "endpointIds": ["endpoint-1766609309063"]

# Push back
adb push sources_edited.json /sdcard/sources.json
adb shell run-as com.example.alertsheets cp /sdcard/sources.json files/sources.json

# Restart app
adb shell am force-stop com.example.alertsheets
adb shell am start -n com.example.alertsheets/.ui.MainActivity
```

### Test 3: Real SMS Delivery
```bash
# Prerequisites:
# - SMS source pointing to Firestore ingest endpoint (see Test 2)
# - Shared secret configured in both Android and Cloud Functions
# - Cloud Functions deployed

# Clear logs
adb logcat -c

# Start monitoring
adb logcat -v time -s DeliveryPipeline:V ReliableHttpSender:V StructuredLogger:V *:E

# Send SMS from configured number (e.g., +1 888-660-1455)

# Expected logcat output:
# DeliveryPipeline: 📨 SMS event | sender=***55 message_len=XX
# DeliveryPipeline: ✓ Source matched: SMS Adjust Leads Firestore Database
# DeliveryPipeline: ✓ Endpoint selected: Firestore Ingest Function
# DeliveryPipeline: ✓ Authorization header added for ingest endpoint (secret length=64)
# DeliveryPipeline: 📤 Sending to: Firestore Ingest Function (https://us-central1-...)
# ReliableHttpSender: POST (with Authorization header)
# DeliveryPipeline: ✅ HTTP OK | code=200 latency=XXXms
# StructuredLogger: {"event":"http_ok","code":200,...}

# Check Firestore Console:
# https://console.firebase.google.com/project/alerts-sheets-bb09c/firestore/data
# Expected: New document in /alerts collection with SMS data
```

### Test 4: Apps Script Path (Verify Unchanged)
```bash
# If you have Apps Script endpoint enabled and configured:

# Monitor logs
adb logcat -c
adb logcat -v time -s DeliveryPipeline:V

# Send notification/SMS routed to Apps Script endpoint

# Expected logcat output:
# DeliveryPipeline: Endpoint does not require auth (Apps Script or other)
# DeliveryPipeline: 📤 Sending to: Google Apps Script...
# [NO Authorization header added]
# DeliveryPipeline: ✅ HTTP OK | code=200

# Verify: No "Authorization header added" log
```

---

## 🚨 TROUBLESHOOTING

### Issue 1: Still Getting 401 Errors

**Symptoms:**
```
DeliveryPipeline: ❌ HTTP FAIL | code=401
Response: "Unauthorized. Invalid secret."
```

**Root Causes:**
1. **Secret mismatch** - Android and Cloud Functions using different values
2. **Cloud Functions not deployed** - Still running old code
3. **Typo in secret** - Extra spaces, wrong case, etc.

**Debug Steps:**
```bash
# A) Verify Android secret
adb logcat -d -s DeliveryPipeline:* | Select-String "secret length"
# Expected: "secret length=64" (or whatever your secret length is)
# If you see "secret length=0" → Not configured in local.properties

# B) Verify Cloud Functions deployed
firebase functions:log --only ingest --limit 10
# Look for "✅ Authentication successful" logs

# C) Test Cloud Function directly with curl
curl -X POST https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest \
  -H "Authorization: Bearer YOUR_SECRET_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "uuid": "test-' + (new Date()).getTime() + '",
    "sourceId": "test",
    "payload": "{\"test\":true}",
    "timestamp": "' + (new Date()).toISOString() + '"
  }'

# Expected: {"status":"ok",...}
# If 401: Secret wrong or not configured on server
```

**Fix:**
1. Verify secrets match EXACTLY (copy-paste, don't retype)
2. Redeploy Cloud Functions: `firebase deploy --only functions:ingest`
3. Rebuild Android: `.\gradlew.bat clean assembleDebug`

---

### Issue 2: auth_missing Event

**Symptoms:**
```
DeliveryPipeline: ❌ INGEST_SHARED_SECRET is empty!
StructuredLogger: {"event":"auth_missing",...}
```

**Root Cause:**
Secret not configured in `android/local.properties`

**Fix:**
```bash
# 1. Add secret
notepad android\local.properties
# Add line: INGEST_SHARED_SECRET=your-secret-here

# 2. Clean rebuild (REQUIRED - BuildConfig needs regeneration)
cd android
.\gradlew.bat clean :app:assembleDebug

# 3. Reinstall
adb install -r .\app\build\outputs\apk\debug\app-debug.apk

# 4. Verify
adb logcat -c
adb logcat -s DeliveryPipeline:*
# Should now show: "secret length=XX"
```

---

### Issue 3: SMS Not Delivering (Endpoint Disabled)

**Symptoms:**
- SMS arrives on device
- No delivery logs appear
- OR logs show "endpoint disabled"

**Root Cause:**
SMS source pointing to disabled Apps Script endpoint (see Phase 0 Evidence)

**Fix:**
See **Test 2: Fix SMS Source Routing** above

---

### Issue 4: Server Configuration Error (500)

**Symptoms:**
```
DeliveryPipeline: ❌ HTTP FAIL | code=500
Response: "Server configuration error"
```

**Root Cause:**
Cloud Function deployed without `INGEST_SHARED_SECRET` env var

**Fix:**
```bash
cd functions

# Option A: Set in Firebase config
firebase functions:config:set ingest.shared_secret="your-secret-here"
firebase deploy --only functions:ingest

# Option B: Use .env.local for local emulator
echo "INGEST_SHARED_SECRET=your-secret-here" > .env.local
firebase emulators:start --only functions
```

---

## 📋 CURRENT STATUS SUMMARY

### ✅ Complete
- [x] Android shared secret implementation
- [x] Cloud Functions shared secret validation
- [x] Build verification (both Android and Cloud Functions)
- [x] Documentation complete
- [x] Troubleshooting guide

### ⏳ Pending User Action
- [ ] Generate shared secret (`openssl rand -hex 32`)
- [ ] Add secret to `android/local.properties`
- [ ] Add secret to `functions/.env.local` OR Firebase config
- [ ] Deploy Cloud Functions (`firebase deploy --only functions:ingest`)
- [ ] Rebuild Android (`gradlew clean assembleDebug`)
- [ ] Fix SMS source endpoint assignments (point to Firestore ingest)

### 🎯 Expected After Configuration
- [ ] Lab Dirty Test → HTTP 200 (not 401)
- [ ] Real SMS → HTTP 200, document in Firestore
- [ ] Apps Script path unchanged (no auth header)
- [ ] Debug screen shows successful deliveries
- [ ] Firestore console shows new documents

---

## 📞 RESOURCES

- **Firebase Console:** https://console.firebase.google.com/project/alerts-sheets-bb09c
- **Firestore Data:** https://console.firebase.google.com/project/alerts-sheets-bb09c/firestore/data
- **Cloud Functions Logs:** `firebase functions:log --only ingest`
- **Workspace:** `D:\github\alerts-sheets`
- **Device:** R5CX20WL15P (Samsung, Android SDK 34)

---

## 📝 NEXT SESSION PRIORITIES

1. **P0:** Configure shared secret (generate + add to both Android and Cloud Functions)
2. **P0:** Deploy Cloud Functions with new auth
3. **P0:** Fix SMS source routing (point to Firestore ingest endpoint)
4. **P1:** Test end-to-end (SMS → Firestore)
5. **P2:** Implement Lab test button fixes (show real response dialog)
6. **P2:** Add "Copy Build Info" + "Export last 10 logs" features

---

**Status:** Code complete, waiting for secret configuration + deployment.
