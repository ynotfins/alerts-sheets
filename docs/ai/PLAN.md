# IMPLEMENTATION PLAN - Apps Script SMS Parsing Fix

**Created:** 2025-12-30 (Session 9.3)  
**Target Branch:** `fix/wiring-sources-endpoints`  
**Planning Agent:** Cursor AI (Sequential Thinking + Serena MCP)  
**Authoritative Source:** `parsing.md` (JSON contracts, parsing rules, sheet schema)

---

## 🎯 OBJECTIVE

Fix Apps Script SMS parsing and row merge logic to ensure:
1. **Stable Incident IDs** for AdjustLeads SMS (AL-xxxxxx format)
2. **Correct row upsert** (new vs update detection by Column C)
3. **Multi-line append** behavior for updates (Status, Timestamp, Type, Details, Original)
4. **Address immutability** (Columns D/E/F/G never change on updates)
5. **BNN non-regression** (existing BNN handling must remain unchanged)

**Non-Goal:** Android changes, architecture refactoring, or scope expansion beyond Apps Script parsing.

---

## 📋 CONTEXT & PROBLEM STATEMENT

### Symptoms (User Reported)
- SMS events landing in Google Sheet but **not parsed consistently**
- `parsed:false` returned from Apps Script for AdjustLeads SMS
- Possible "5 rows per SMS" duplication bug
- Placeholders persisting in sheet (not replaced with actual data)

### Root Causes Identified
1. **`parseFireAlertSms()` inadequate** for AdjustLeads emoji-delimited format
2. **`handleSmsMessage()` always created new rows** with non-stable IDs (`SMS-${Date.now()}`)
3. **No row search/upsert logic** for SMS (unlike BNN, which had it)
4. **Incident ID extraction** not implemented for AdjustLeads URLs

### Authoritative Source
**`parsing.md`** defines:
- JSON contracts (Android → Apps Script)
- Sheet schema (columns A-K+)
- Parsing rules (BNN vs SMS)
- Row merge invariants (Column C = primary key)
- Safety checks (BNN must not regress)

---

## 🏗️ IMPLEMENTATION (SESSION 9.3)

### ✅ Completed Tasks

#### 1. Created Backup
**File:** `scripts/Code.gs.backup-20251230`
- Full backup of original Apps Script before changes
- Enables easy rollback if needed

#### 2. Replaced `parseFireAlertSms()` with `parseAdjustLeadsSms()`
**File:** `scripts/Code.gs` (lines 265-452, ~188 lines)

**New capabilities:**
- **3-tier incident ID extraction:**
  1. Primary: AdjustLeads URL → `AL-294966`
  2. Fallback: Last 6-digit number → `AL-xxxxxx`
  3. Last resort: MD5 hash → `AL-<hash>`
- **Emoji-aware line parsing:**
  - 🔥 line → County extraction
  - 📍 line → Address/City/State parsing
  - 📋 line → Incident type + details
  - ℹ️ line → AdjustLeads URL capture
- **Robust regex patterns** (handles variations: adjustleads.com, .net, /app/, etc.)
- **Returns structured object** with all parsed fields

**Key code patterns:**
```javascript
// Incident ID extraction (3-tier)
const urlMatch = message.match(/https?:\/\/(?:www\.)?adjustleads\.(?:com|net)\/(?:app\/)?alerts\/(\d{6,})/i);
if (urlMatch) {
  result.incidentId = `AL-${urlMatch[1]}`;
} else {
  // Fallback logic...
}

// Emoji-aware parsing
if (line.includes('🔥') || line.includes('Fire Alert')) {
  const countyMatch = line.match(/(?:in|at)\s+(\w+(?:\s+\w+)?)\s+County/i);
  if (countyMatch) result.county = countyMatch[1].trim();
}
```

#### 3. Replaced `handleSmsMessage()` with Row Upsert Logic
**File:** `scripts/Code.gs` (lines 204-336, ~133 lines)

**New behavior:**
- **Generic SMS path** (backward compatible):
  - If not AdjustLeads format → create single row with `SMS-${Date.now()}` ID
  - Return `parsed: false`
- **AdjustLeads SMS path:**
  - Parse message → extract stable incident ID
  - **Search existing rows** by Column C (same logic as BNN)
  - **If found (UPDATE):**
    - Append "\nSMS Update" to Column A
    - Append "\n{timestamp}" to Column B
    - Append new incident type to Column H (if different)
    - Append new details to Column I
    - Append "\n\nFrom: {sender}\n{message}" to Column J
    - **DO NOT MODIFY** Columns D/E/F/G
  - **If not found (NEW):**
    - Create new row with stable AL-* ID in Column C
    - Populate all columns (D/E/F/G with parsed location)
    - Return `action: "new"`

**Key code patterns:**
```javascript
// Row search (same as BNN)
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

// UPDATE path: append to multi-line cells
if (foundRow !== -1) {
  const statusCell = sheet.getRange(foundRow, 1);
  statusCell.setValue(statusCell.getValue() + "\nSMS Update");
  // ... append to other cells ...
}
```

#### 4. Added `testSmsParser()` Function
**File:** `scripts/Code.gs` (lines 489-510)

**Purpose:** Pre-deployment verification of parsing logic
**Usage:** Run directly in Apps Script Editor (Extensions → Apps Script → Run)

**Test message:**
```javascript
const testMessage = `🔥 New Fire Alert in Morris County
📍 31 Grand Avenue, Cedar Knolls, NJ
🗺️ https://maps.google.com/?q=31+Grand+Avenue+Cedar+Knolls+NJ
📋 Residential Fire - Possible structure fire with smoke showing
ℹ️ https://www.adjustleads.com/app/alerts/294966`;
```

**Expected output:**
```
Incident ID: AL-294966
County: Morris
Address: 31 Grand Avenue
City: Cedar Knolls
State: NJ
Type: Residential Fire
Details: Possible structure fire with smoke showing
Is AdjustLeads: true
```

#### 5. Updated `docs/ai/STATE.md`
**Section:** SESSION 9.3 SUMMARY: Apps Script SMS Parsing Fix

**Content:**
- Problem statement + root causes
- Files changed (3 functions replaced, 1 test added)
- Verification commands (Apps Script test + curl tests)
- Acceptance checklist (SMS parsing, row upsert, BNN regression, generic SMS)

---

## 🧪 TESTING STRATEGY (PENDING USER ACTION)

### Pre-Deployment Test (Apps Script Editor)

**Test:** `testSmsParser()` function
**Status:** ⏳ PENDING

```javascript
// In Apps Script Editor: Extensions → Apps Script
// Run: testSmsParser()
// Check: Logs (View → Logs or Ctrl+Enter)

// Expected output:
// Incident ID: AL-294966
// County: Morris
// Address: 31 Grand Avenue
// City: Cedar Knolls
// State: NJ
// Type: Residential Fire
// Details: Possible structure fire with smoke showing
// Is AdjustLeads: true
```

**PASS CRITERIA:**
- All fields extracted correctly
- Incident ID format: `AL-{digits}`
- No JavaScript errors

---

### Post-Deployment Tests (curl + Google Sheets)

#### Test 1: BNN Non-Regression
**Goal:** Verify BNN handling unchanged (lines 29-198 preserved)
**Status:** ⏳ PENDING

```bash
# Send BNN test payload
curl -X POST "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec" \
  -H "Content-Type: application/json" \
  -d '{
    "source": "bnn",
    "incidentId": "#9999999",
    "status": "New Incident",
    "timestamp": "12/30/2025 12:00:00 PM",
    "state": "NJ",
    "county": "Test",
    "city": "TestCity",
    "address": "123 Test St",
    "incidentType": "Test Fire",
    "incidentDetails": "BNN regression test",
    "fdCodes": ["test1", "test2"],
    "originalBody": "BNN test message"
  }'

# Expected response:
# {"result":"success","type":"bnn","incidentId":"#9999999","action":"new"}

# Verify in Google Sheets:
# - New row created
# - Column C = #9999999
# - FD codes in columns K+
# - All fields populated correctly
```

**PASS CRITERIA:**
- Response: `result: "success"`, `action: "new"`
- Sheet: New row with `#9999999` in Column C
- FD codes deduped and filtered correctly

---

#### Test 2: AdjustLeads SMS (New Row)
**Goal:** Verify stable ID extraction + new row creation
**Status:** ⏳ PENDING

```bash
# Send AdjustLeads SMS payload
curl -X POST "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec" \
  -H "Content-Type: application/json" \
  -d '{
    "source": "sms",
    "sender": "+15614193784",
    "message": "🔥 New Fire Alert in Bergen\n\n📍 2100 North Central Road, Fort Lee, NJ 07024-7558, USA\n🗺️ https://maps.google.com/?q=2100+North+Central+Road+Fort+Lee+NJ\n📋 Structural Fire - First engine on scene\nℹ️ https://www.adjustleads.com/app/alerts/294966",
    "timestamp": "12/30/2025 12:30:00 PM"
  }'

# Expected response:
# {"result":"success","type":"sms","incidentId":"AL-294966","action":"new","parsed":true}

# Verify in Google Sheets:
# - New row created
# - Column C = AL-294966
# - Column E = Bergen
# - Column F = Fort Lee
# - Column G = 2100 North Central Road
# - Column H = Structural Fire
# - Column I = First engine on scene
# - Column J starts with "From: +1561..."
```

**PASS CRITERIA:**
- Response: `parsed: true`, `action: "new"`, `incidentId: "AL-294966"`
- Sheet: New row with `AL-294966` in Column C
- Location fields (D/E/F/G) populated correctly

---

#### Test 3: AdjustLeads SMS (Update Existing Row)
**Goal:** Verify row upsert (append, not duplicate)
**Status:** ⏳ PENDING

```bash
# Send SAME AdjustLeads SMS again (same incident ID)
curl -X POST "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec" \
  -H "Content-Type: application/json" \
  -d '{
    "source": "sms",
    "sender": "+15614193784",
    "message": "🔥 Update: Fire now under control in Bergen\n\n📍 2100 North Central Road, Fort Lee, NJ 07024-7558, USA\n📋 Structural Fire - Units returning to station\nℹ️ https://www.adjustleads.com/app/alerts/294966",
    "timestamp": "12/30/2025 01:00:00 PM"
  }'

# Expected response:
# {"result":"success","type":"sms","incidentId":"AL-294966","action":"update","parsed":true}

# Verify in Google Sheets:
# - SAME ROW updated (no new row created)
# - Column A: "SMS Fire Alert\nSMS Update"
# - Column B: original timestamp + "\n12/30/2025 01:00:00 PM"
# - Column H: "Structural Fire" (no duplicate if same)
# - Column I: original details + "\nUnits returning to station"
# - Column J: original + "\n\nFrom: +1561...\n[new message]"
# - Columns D/E/F/G UNCHANGED
```

**PASS CRITERIA:**
- Response: `action: "update"` (not "new")
- Sheet: Same row updated, NO NEW ROW CREATED
- Multi-line append in columns A/B/H/I/J
- Columns D/E/F/G remain unchanged

---

#### Test 4: Generic SMS (Non-AdjustLeads)
**Goal:** Verify backward compatibility for non-fire SMS
**Status:** ⏳ PENDING

```bash
# Send generic SMS (no AdjustLeads format)
curl -X POST "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec" \
  -H "Content-Type: application/json" \
  -d '{
    "source": "sms",
    "sender": "+12125551234",
    "message": "Hey, this is just a regular text message.",
    "timestamp": "12/30/2025 02:00:00 PM"
  }'

# Expected response:
# {"result":"success","type":"sms","sender":"+12125551234","parsed":false}

# Verify in Google Sheets:
# - New row created
# - Column A = "SMS"
# - Column C = SMS-{timestamp} (one-off ID)
# - Column G = "From: +1212..."
# - Column H = "SMS Message"
# - Column I = message text
# - Column J = "From: +1212...\n{message}"
```

**PASS CRITERIA:**
- Response: `parsed: false`
- Sheet: New row with generic format
- No parsing attempted (simple passthrough)

---

## 🚨 SAFETY CHECKS & INVARIANTS

### Non-Negotiable Rules (from parsing.md)

1. **BNN handler correctness is sacred**
   - Lines 29-198 in Code.gs MUST NOT REGRESS
   - FD code deduplication logic preserved
   - Row merge by Column C preserved

2. **Row merge is always by Column C**
   - Search logic identical for BNN and SMS
   - Incident ID uniqueness enforced

3. **Updates never rewrite D/E/F/G**
   - Location fields (State/County/City/Address) immutable on updates
   - Only multi-line fields (A/B/H/I/J) append on updates

4. **FD codes are unique per incident**
   - BNN-specific logic (not applicable to SMS)
   - Must remain functional for BNN tests

### Implementation Safety

**What was preserved:**
- ✅ BNN `doPost()` handler (lines 1-203)
- ✅ BNN incident ID extraction (`/#(\d{7})/`)
- ✅ BNN FD code deduplication
- ✅ BNN row merge logic

**What was replaced:**
- ❌ `parseFireAlertSms()` (inadequate)
- ❌ `handleSmsMessage()` (always created new rows)

**Rollback plan:**
- Backup file: `scripts/Code.gs.backup-20251230`
- If any issues: restore backup and redeploy

---

## 📦 DELIVERABLES

### ✅ Code Artifacts (COMPLETED)
1. ✅ `scripts/Code.gs` (~165 lines modified)
   - Replaced `parseFireAlertSms()` → `parseAdjustLeadsSms()` (lines 265-452)
   - Replaced `handleSmsMessage()` with row upsert logic (lines 204-336)
   - Added `testSmsParser()` function (lines 489-510)
2. ✅ `scripts/Code.gs.backup-20251230` (full backup)

### ✅ Documentation (COMPLETED)
1. ✅ `docs/ai/STATE.md` - Session 9.3 summary added
   - Problem statement + root causes
   - Files changed + verification steps
   - Acceptance checklist
2. ✅ `docs/ai/PLAN.md` - Updated to reflect Session 9.3 work (this file)
3. ✅ `parsing.md` - Authoritative source of truth (already existed)

### ⏳ Testing Artifacts (PENDING USER ACTION)
1. ⏳ Apps Script Editor test output (testSmsParser logs)
2. ⏳ curl test results (BNN, AdjustLeads new/update, generic SMS)
3. ⏳ Google Sheets screenshots showing:
   - BNN row (non-regression)
   - AdjustLeads new row (with AL-* ID)
   - AdjustLeads update (same row, multi-line append)
   - Generic SMS row (backward compat)

---

## 🔄 ROLLBACK PLAN

### If Apps Script Fails After Deployment

**Option 1: Restore from backup**
```bash
# Copy backup to active file
cp scripts/Code.gs.backup-20251230 scripts/Code.gs

# Upload to Apps Script:
# 1. Open Apps Script Editor (script.google.com)
# 2. Select project
# 3. Replace Code.gs content with backup
# 4. Save (Ctrl+S)
# 5. Deploy → New deployment (or update existing)
```

**Option 2: Targeted fix**
- If only SMS parsing broken: revert `parseAdjustLeadsSms()` only
- If only SMS upsert broken: revert `handleSmsMessage()` only
- Keep test function for future debugging

---

## 📅 TIMELINE

| Task | Status | Duration | Completed |
|------|--------|----------|-----------|
| Context reset | ✅ DONE | 5 min | 2025-12-30 |
| Code backup | ✅ DONE | 1 min | 2025-12-30 |
| Replace parseFireAlertSms | ✅ DONE | 45 min | 2025-12-30 |
| Replace handleSmsMessage | ✅ DONE | 60 min | 2025-12-30 |
| Add testSmsParser | ✅ DONE | 15 min | 2025-12-30 |
| Update STATE.md | ✅ DONE | 30 min | 2025-12-30 |
| Update PLAN.md | ✅ DONE | 20 min | 2025-12-30 |
| **User testing** | ⏳ PENDING | 30 min | TBD |
| **TOTAL** | **~3.5 hours** | - | - |

---

## ✅ ACCEPTANCE CRITERIA

### Implementation (✅ COMPLETED)
- [x] Backup created
- [x] `parseAdjustLeadsSms()` implemented with 3-tier ID extraction
- [x] `handleSmsMessage()` implements row upsert logic
- [x] `testSmsParser()` function added
- [x] STATE.md updated with Session 9.3 summary
- [x] PLAN.md updated to reflect current work
- [x] BNN handler (lines 29-198) preserved

### Testing (⏳ PENDING USER ACTION)
- [ ] `testSmsParser()` runs without errors (Apps Script Editor)
- [ ] BNN test payload: creates new row with `#xxxxxxx` ID
- [ ] BNN test payload: FD codes deduped correctly
- [ ] AdjustLeads SMS (new): creates row with `AL-xxxxxx` ID
- [ ] AdjustLeads SMS (new): location fields (D/E/F/G) populated
- [ ] AdjustLeads SMS (update): SAME ROW updated (no new row)
- [ ] AdjustLeads SMS (update): multi-line append in A/B/H/I/J
- [ ] AdjustLeads SMS (update): D/E/F/G unchanged
- [ ] Generic SMS: creates row with `SMS-{timestamp}` ID
- [ ] Generic SMS: returns `parsed: false`

### Regression (⏳ PENDING USER ACTION)
- [ ] BNN alerts still parse correctly
- [ ] BNN incident ID extraction still works (`/#(\d{7})/`)
- [ ] BNN FD code deduplication still works
- [ ] BNN row merge still works (Column C search)

---

## 📋 NEXT STEPS (USER ACTION REQUIRED)

1. **Deploy to Apps Script:**
   - Copy `scripts/Code.gs` to Apps Script Editor
   - Save and deploy

2. **Run Pre-Deployment Test:**
   - Run `testSmsParser()` in Apps Script Editor
   - Verify output in Logs (View → Logs)

3. **Run Post-Deployment Tests:**
   - Test 1: BNN regression (curl)
   - Test 2: AdjustLeads SMS new row (curl)
   - Test 3: AdjustLeads SMS update row (curl same ID)
   - Test 4: Generic SMS (curl non-AdjustLeads)

4. **Verify in Google Sheets:**
   - Check row creation/updates
   - Check Column C incident IDs
   - Check multi-line append behavior
   - Check location field immutability

5. **Report Results:**
   - Update STATE.md with test outcomes
   - Report any failures for troubleshooting

---

**Implementation complete. Awaiting user testing and deployment.**

