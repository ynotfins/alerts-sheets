# Google Sheet Update Logic (CORRECTED)

**Date:** December 17, 2025  
**Status:** Authoritative Specification

---

## 📊 **Column Types**

### **Visual Standard: Underlined Headers = Static Fields** 🎨
**Rule:** In the Google Sheet, any column with **UNDERLINED header text** contains static data that should NOT be updated when updates arrive.

### **STATIC Columns (Never Append on Updates)**
**Visual Indicator:** Header text is UNDERLINED in Google Sheet  
These fields identify the incident and NEVER change:

| Column | Field | Example | Update Behavior | Header Style |
|--------|-------|---------|-----------------|--------------|
| **C** | Incident ID | `#1843844` | ❌ NO APPEND (identifier) | <u>Underlined</u> |
| **D** | State | `NJ` | ❌ NO APPEND (static location) | <u>Underlined</u> |
| **E** | County | `Bergen` | ❌ NO APPEND (static location) | <u>Underlined</u> |
| **F** | City | `Paramus` | ❌ NO APPEND (static location) | <u>Underlined</u> |
| **G** | Address | `123 Main St` | ❌ NO APPEND (static location) | <u>Underlined</u> |

**Why?** These are **identifiers and static location data** - they define WHAT incident this row is about. They don't change with updates.

**Sheet Convention:** Any column you see with underlined header text in the Google Sheet should be treated as static in the Apps Script.

---

### **DYNAMIC Columns (Append on Updates)**
**Visual Indicator:** Header text is NOT underlined in Google Sheet  
These fields contain changing information and should append with `\n`:

| Column | Field | Example | Update Behavior | Header Style |
|--------|-------|---------|-----------------|--------------|
| **A** | Status | `New Incident` | ✅ APPEND (changes from New to Update) | Regular (not underlined) |
| **B** | Timestamp | `12/17/2025 8:30:45 PM` | ✅ APPEND (each update has new time) | Regular (not underlined) |
| **H** | Incident Type | `Working Fire` | ✅ APPEND (may escalate/change) | Regular (not underlined) |
| **I** | Incident Details | `FD on scene...` | ✅ APPEND (new info with each update) | Regular (not underlined) |
| **J** | Original Body | `Full notification text` | ✅ APPEND (captures each notification) | Regular (not underlined) |

**Why?** These fields **change over time** as the incident evolves. Each update adds NEW information.

**Sheet Convention:** If the header text is NOT underlined in Google Sheet, the Apps Script should append new data to that column with `\n` separator.

---

### **FD Codes Columns (Smart Merge)**
Special logic: Only add NEW codes, skip duplicates

| Column Range | Field | Example | Update Behavior |
|--------------|-------|---------|-----------------|
| **K-U** | FD Codes | `nyc337`, `ny153`, `nyu9w` | ✅ ADD NEW ONLY (no duplicates) |

**Logic:**
1. New incident arrives with FD codes: `nyc337`, `ny153`
   - Write to cells K2, L2
2. Update arrives with FD codes: `ny153`, `nyu9w`
   - `ny153` already exists → SKIP
   - `nyu9w` is NEW → Add to M2
3. Final result: `nyc337`, `ny153`, `nyu9w` (one per cell, no duplicates)

---

## 🔧 **Apps Script Update Logic**

### **Current Implementation (Code.gs)**

```javascript
// 1. Search for existing incident by ID
const existingRowIndex = searchForIncident(incidentId);

if (existingRowIndex !== -1) {
  // EXISTING INCIDENT - Update logic
  
  // Static columns (C-G): DO NOT TOUCH
  // ID, State, County, City, Address stay the same
  
  // Dynamic columns (A, B, H, I, J): APPEND with \n
  const existingStatus = sheet.getRange(existingRowIndex, 1).getValue();
  sheet.getRange(existingRowIndex, 1).setValue(existingStatus + "\n" + data.status);
  
  const existingTimestamp = sheet.getRange(existingRowIndex, 2).getValue();
  sheet.getRange(existingRowIndex, 2).setValue(existingTimestamp + "\n" + formattedTime);
  
  // ... append to columns H, I, J similarly ...
  
  // FD Codes (K-U): Merge unique codes
  const existingCodes = getExistingFDCodes(existingRowIndex); // e.g., ["nyc337", "ny153"]
  const newCodes = data.fdCodes.filter(code => !existingCodes.includes(code)); // e.g., ["nyu9w"]
  
  // Append only NEW codes to next available columns
  appendFDCodes(existingRowIndex, newCodes);
  
} else {
  // NEW INCIDENT - Write entire row
  const row = [
    data.status,           // A
    formattedTime,         // B
    incidentId,            // C (STATIC)
    data.state,            // D (STATIC)
    data.county,           // E (STATIC)
    data.city,             // F (STATIC)
    data.address,          // G (STATIC)
    data.incidentType,     // H
    data.incidentDetails,  // I
    data.originalBody      // J
  ];
  
  // Add FD codes to columns K-U (one per cell)
  data.fdCodes.forEach(code => row.push(code));
  
  sheet.appendRow(row);
}
```

---

## 📝 **Example: Incident Lifecycle**

### **1. New Incident Arrives**
```json
{
  "status": "New Incident",
  "timestamp": "12/17/2025 8:30:00 PM",
  "incidentId": "#1843900",
  "state": "NJ",
  "county": "Bergen",
  "city": "Paramus",
  "address": "123 Main St",
  "incidentType": "Fire Alarm",
  "incidentDetails": "Smoke detector activation",
  "originalBody": "NJ| Bergen| Paramus| 123 Main St| Fire Alarm| Smoke detector activation| <C> BNN | nj5264 | #1843900",
  "fdCodes": ["nj5264"]
}
```

**Sheet Row 100 After Insert:**
| A | B | C | D | E | F | G | H | I | J | K | L |
|---|---|---|---|---|---|---|---|---|---|---|---|
| New Incident | 12/17/2025 8:30:00 PM | #1843900 | NJ | Bergen | Paramus | 123 Main St | Fire Alarm | Smoke detector activation | [Full body] | nj5264 | (empty) |

---

### **2. First Update Arrives (5 minutes later)**
```json
{
  "status": "Update",
  "timestamp": "12/17/2025 8:35:00 PM",
  "incidentId": "#1843900",
  "state": "NJ",
  "county": "Bergen",
  "city": "Paramus",
  "address": "123 Main St",
  "incidentType": "Working Fire",
  "incidentDetails": "Fire confirmed, units on scene",
  "originalBody": "U/D NJ| Bergen| Paramus| 123 Main St| Working Fire| Fire confirmed, units on scene| <C> BNN | nj5264/nj137 | #1843900",
  "fdCodes": ["nj5264", "nj137"]
}
```

**Sheet Row 100 After Update:**
| A | B | C | D | E | F | G | H | I | J | K | L |
|---|---|---|---|---|---|---|---|---|---|---|---|
| New Incident<br>Update | 12/17/2025 8:30:00 PM<br>12/17/2025 8:35:00 PM | #1843900 | NJ | Bergen | Paramus | 123 Main St | Fire Alarm<br>Working Fire | Smoke detector activation<br>Fire confirmed, units on scene | [Full body 1]<br>[Full body 2] | nj5264 | nj137 |

**Note:**
- Columns C-G (ID, State, County, City, Address): **NO CHANGE** ✅
- Columns A, B, H, I, J: **APPENDED with `\n`** ✅
- FD Codes: `nj5264` already existed → SKIP, `nj137` is NEW → Added to L ✅

---

### **3. Second Update Arrives (10 minutes later)**
```json
{
  "status": "Update",
  "timestamp": "12/17/2025 8:45:00 PM",
  "incidentId": "#1843900",
  "incidentType": "Working Fire",
  "incidentDetails": "Fire under control",
  "originalBody": "U/D NJ| Bergen| Paramus| 123 Main St| Working Fire| Fire under control| <C> BNN | nj5264 | #1843900",
  "fdCodes": ["nj5264"]
}
```

**Sheet Row 100 After Second Update:**
| A | B | C | D | E | F | G | H | I | J | K | L |
|---|---|---|---|---|---|---|---|---|---|---|---|
| New Incident<br>Update<br>Update | 12/17/2025 8:30:00 PM<br>12/17/2025 8:35:00 PM<br>12/17/2025 8:45:00 PM | #1843900 | NJ | Bergen | Paramus | 123 Main St | Fire Alarm<br>Working Fire<br>Working Fire | Smoke detector activation<br>Fire confirmed...<br>Fire under control | [Body 1]<br>[Body 2]<br>[Body 3] | nj5264 | nj137 |

**Note:**
- Columns C-G: **STILL NO CHANGE** ✅
- Columns A, B, H, I, J: **Third entry appended** ✅
- FD Codes: `nj5264` already existed → SKIP (no new codes added) ✅

---

## 🎯 **Summary of Rules**

### **Rule 0: VISUAL STANDARD (Primary Rule)** 🎨
```
Look at Google Sheet header row:
→ UNDERLINED header text = STATIC field (never append on updates)
→ Regular header text = DYNAMIC field (always append with \n)
→ This visual convention is the source of truth
```

### **Rule 1: Static Fields Never Change**
```
Columns with UNDERLINED headers (C, D, E, F, G) = Incident identifiers and location
→ Write ONCE on new incident
→ NEVER append on updates
→ These define WHAT incident the row represents
```

### **Rule 2: Dynamic Fields Always Append**
```
Columns with REGULAR headers (A, B, H, I, J) = Changing incident information
→ Append with \n on every update
→ Captures incident timeline
→ Shows how incident evolved over time
```

### **Rule 3: FD Codes Merge Uniquely**
```
Columns K-U = Fire department codes (special logic)
→ Check if code already exists for this incident ID
→ If EXISTS: Skip (no duplicate)
→ If NEW: Add to next available column
→ Result: One code per cell, no duplicates per incident
```

---

## 🔧 **Apps Script Functions Needed**

### **1. Search for Existing Incident**
```javascript
function searchForIncident(incidentId) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  const data = sheet.getDataRange().getValues();
  
  for (let i = 1; i < data.length; i++) { // Skip header row
    if (data[i][2] === incidentId) { // Column C (index 2) = Incident ID
      return i + 1; // Return 1-based row number
    }
  }
  return -1; // Not found
}
```

### **2. Get Existing FD Codes**
```javascript
function getExistingFDCodes(rowIndex) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  const codes = [];
  
  // Columns K-U = columns 11-21 (0-based: 10-20)
  for (let col = 11; col <= 21; col++) {
    const value = sheet.getRange(rowIndex, col).getValue();
    if (value && value.trim() !== "") {
      codes.push(value.trim());
    }
  }
  return codes;
}
```

### **3. Append New FD Codes**
```javascript
function appendFDCodes(rowIndex, newCodes) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  const existingCodes = getExistingFDCodes(rowIndex);
  
  let currentCol = 11 + existingCodes.length; // Start after last existing code
  
  newCodes.forEach(code => {
    if (currentCol <= 21) { // Column U = 21
      sheet.getRange(rowIndex, currentCol).setValue(code);
      currentCol++;
    }
  });
}
```

---

## ✅ **Verification Checklist**

After implementing update logic, verify:

- [ ] New incident creates row with ALL columns C-J populated
- [ ] Update with SAME ID appends to columns A, B, H, I, J (with `\n`)
- [ ] Update does NOT modify columns C, D, E, F, G (static)
- [ ] FD codes from new incident populate columns K-U (one per cell)
- [ ] FD codes from update only add NEW codes (skip duplicates)
- [ ] Multiple updates create multi-line cells (visible in Google Sheets)

---

## 🚀 **Next Steps**

1. **Phase 1:** Ensure Android sends correct JSON (AG's fixes)
2. **Phase 2:** Update Apps Script with proper column logic (this spec)
3. **Phase 3:** Test with real BNN notifications
4. **Phase 4:** Backend enrichment pipeline

**This specification is the source of truth for Google Sheet update behavior.** 📋

