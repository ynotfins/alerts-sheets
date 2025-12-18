# 🎯 FD Code Analysis - Immediate Action Plan

## ✅ **Step 1: Inspect Your Spreadsheets** (30 minutes)

I need you to open each spreadsheet and tell me what's in them so I can map the data correctly:

### **Reference Sheets (Lines 1-11)**

1. **Sheet 1:** `1vg-JXhPz9t2MY1gMWkTI_6Yd4Qi8DtbPrI9M4wvOu3I`
   - **What's in it?** (FD names? FDID codes? Addresses?)
   - **Column headers?**

2. **Sheet 3:** `1Dp563w_Z5GLno3UY5IYLeKVTzpAMcZVvljOz1ZsboKo`
   - **What's in it?**
   - **Column headers?**

3. **Sheet 7:** `1xCFvGkbUtA7bxmXEYlfxeAmYcWnjC_VsNeiGkWYz5Co`
   - **What's in it?** (Radio channels? Encryption status?)
   - **Column headers?**

4. **Sheet 9:** `16cMrLO-pj__5qTID5Z2rtS4lTGaYsHUis-j9DmG-sgw`
   - **What's in it?**
   - **Column headers?**

5. **Sheet 11:** `1A5SNV_bDYktAIQ3WeUnEAU4ZTH0EkWwUaIaV4vHVSAQ`
   - **What's in it?** (Encryption rules?)
   - **Column headers?**

### **Historical Alert Sheets (Lines 13-19)**

6. **Sheet 13/19:** `14uPfcDTsX2eI7dqxmj-95YdHnVgNAJ7Uzgrnl3x9W5U`
   - **What's in it?** (Old BNN alerts?)
   - **How many rows?**
   - **Column headers?**

7. **Sheet 15:** `1vg-JXhPz9t2MY1gMWkTI_6Yd4Qi8DtbPrI9M4wvOu3I` (same as line 1)
   - Is this a duplicate or different tab?

8. **Sheet 17:** `1ly21BVGLGuMQ2Gtt5hNhFyWgVBQIu6oOC3hBoEPIZfA`
   - **What's in it?**
   - **How many rows?**

---

## ✅ **Step 2: Set Up Google Cloud APIs** (20 minutes)

### **A. Create/Use Existing Google Cloud Project**

1. Go to: https://console.cloud.google.com
2. Select existing project or create "alerts-sheets-analysis"
3. Enable APIs:
   - **Geocoding API** (https://console.cloud.google.com/apis/library/geocoding-backend.googleapis.com)
   - **Places API** (https://console.cloud.google.com/apis/library/places-backend.googleapis.com)
4. Create API Key:
   - **APIs & Services** → **Credentials** → **+ CREATE CREDENTIALS** → **API key**
   - Restrict key to Geocoding + Places APIs only
   - Copy the API key

### **B. Set Billing Budget**

1. **Billing** → **Budgets & alerts**
2. Create budget: $50/month
3. Set alert at 50%, 90%, 100%

**Estimated costs:**
- 10,000 addresses × $0.005 = **$50** (Geocoding)
- 500 FD lookups × $0.017 = **$8.50** (Places)
- **Total:** ~$60 for complete analysis

---

## ✅ **Step 3: Create New Tracking Sheets** (10 minutes)

Create 4 new tabs in your `FD-Codes-Analytics` spreadsheet:

### **Tab 1: `FD-Code-Master`**

Headers:
```
FD Code | Fire Department Name | FDID | County | State | Primary Address | Total Incidents | Total Updates | Avg Updates | Encryption Status | Confidence % | Radio Channels | Last Seen | Service Area IDs
```

### **Tab 2: `FD-Code-Addresses`**

Headers:
```
FD Code | Address | City | County | State | First Seen | Last Seen | Total Incidents | Incident Types | Lat | Lng
```

### **Tab 3: `FD-Code-Incidents`**

Headers:
```
Incident ID | FD Codes (pipe-separated) | Address | City | County | State | Incident Type | Initial Alert Time | Update Count | Update Times | Source Sheet | Source Row
```

### **Tab 4: `FD-Encryption-Report`**

Headers:
```
FD Code | Fire Department Name | Classification | Evidence | Sample Size | Avg Updates | Expected Behavior | Match Status | Notes
```

---

## ✅ **Step 4: Extract Live Data from Current Sheet** (I'll write the script)

**Apps Script to run:**

```javascript
function analyzeLiveData() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const liveSheet = ss.getSheets()[0]; // FD-Codes-Analytics (main tab)
  const incidentsSheet = ss.getSheetByName('FD-Code-Incidents');
  const addressSheet = ss.getSheetByName('FD-Code-Addresses');
  
  const data = liveSheet.getDataRange().getValues();
  const headers = data[0];
  
  // Find column indices
  const colIncidentId = headers.indexOf('Incident ID');
  const colAddress = headers.indexOf('Address');
  const colCity = headers.indexOf('City');
  const colCounty = headers.indexOf('County');
  const colState = headers.indexOf('State');
  const colIncidentType = headers.indexOf('Incident type');
  const colTimestamp = headers.indexOf('Timestamp');
  const colFdCodesStart = 10; // Column K (FD Codes start)
  
  const incidents = [];
  const addressMap = {}; // fd_code → addresses
  
  for (let i = 1; i < data.length; i++) {
    const row = data[i];
    const incidentId = row[colIncidentId];
    const address = row[colAddress];
    const city = row[colCity];
    const county = row[colCounty];
    const state = row[colState];
    const incidentType = row[colIncidentType];
    const timestamps = row[colTimestamp].split('\n'); // Multiple timestamps
    const updateCount = timestamps.length - 1; // First is initial, rest are updates
    
    // Extract FD codes (all cells from column K onward)
    const fdCodes = [];
    for (let j = colFdCodesStart; j < row.length; j++) {
      if (row[j]) fdCodes.push(row[j].toString().trim());
    }
    
    // Record incident
    incidents.push({
      incidentId,
      fdCodes: fdCodes.join('|'),
      address,
      city,
      county,
      state,
      incidentType,
      initialTime: timestamps[0],
      updateCount,
      updateTimes: timestamps.slice(1).join(', '),
      sourceSheet: 'FD-Codes-Analytics',
      sourceRow: i + 1
    });
    
    // Build address map
    fdCodes.forEach(code => {
      if (!addressMap[code]) addressMap[code] = {};
      if (!addressMap[code][address]) {
        addressMap[code][address] = {
          city, county, state,
          firstSeen: timestamps[0],
          lastSeen: timestamps[timestamps.length - 1],
          count: 0,
          types: new Set()
        };
      }
      addressMap[code][address].count++;
      addressMap[code][address].types.add(incidentType);
      addressMap[code][address].lastSeen = timestamps[timestamps.length - 1];
    });
  }
  
  // Write incidents to sheet
  const incidentRows = incidents.map(inc => [
    inc.incidentId, inc.fdCodes, inc.address, inc.city, inc.county, inc.state,
    inc.incidentType, inc.initialTime, inc.updateCount, inc.updateTimes,
    inc.sourceSheet, inc.sourceRow
  ]);
  incidentsSheet.getRange(2, 1, incidentRows.length, 12).setValues(incidentRows);
  
  // Write addresses to sheet
  const addressRows = [];
  for (const fdCode in addressMap) {
    for (const address in addressMap[fdCode]) {
      const data = addressMap[fdCode][address];
      addressRows.push([
        fdCode, address, data.city, data.county, data.state,
        data.firstSeen, data.lastSeen, data.count,
        Array.from(data.types).join(', '),
        '', '' // Lat/Lng (will geocode later)
      ]);
    }
  }
  addressSheet.getRange(2, 1, addressRows.length, 11).setValues(addressRows);
  
  Logger.log(`Processed ${incidents.length} incidents`);
  Logger.log(`Mapped ${addressRows.length} FD code → address pairs`);
}
```

---

## ✅ **Step 5: Build FD Code Master Table** (I'll write the script)

```javascript
function buildFdCodeMaster() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const incidentsSheet = ss.getSheetByName('FD-Code-Incidents');
  const masterSheet = ss.getSheetByName('FD-Code-Master');
  
  const data = incidentsSheet.getDataRange().getValues();
  const master = {};
  
  for (let i = 1; i < data.length; i++) {
    const fdCodes = data[i][1].split('|'); // Column B (pipe-separated codes)
    const updateCount = data[i][8]; // Column I
    
    fdCodes.forEach(code => {
      if (!master[code]) {
        master[code] = {
          totalIncidents: 0,
          totalUpdates: 0,
          lastSeen: ''
        };
      }
      master[code].totalIncidents++;
      master[code].totalUpdates += updateCount;
      master[code].lastSeen = data[i][7]; // Most recent timestamp
    });
  }
  
  // Calculate averages and classify
  const masterRows = [];
  for (const code in master) {
    const avgUpdates = master[code].totalUpdates / master[code].totalIncidents;
    let encryptionStatus = 'Unknown';
    let confidence = 0;
    
    if (master[code].totalIncidents >= 10) {
      if (avgUpdates >= 1.5) {
        encryptionStatus = 'Fully Unencrypted';
        confidence = Math.min(master[code].totalIncidents / 50 * 100, 100);
      } else if (avgUpdates <= 0.3) {
        encryptionStatus = 'First Call Only';
        confidence = Math.min(master[code].totalIncidents / 50 * 100, 100);
      } else {
        encryptionStatus = 'Partial/Mixed';
        confidence = 50;
      }
    } else {
      encryptionStatus = 'Insufficient Data';
      confidence = 0;
    }
    
    masterRows.push([
      code,
      '', // FD Name (to be filled)
      '', // FDID (to be matched)
      '', // County (can aggregate from addresses)
      '', // State
      '', // Primary Address
      master[code].totalIncidents,
      master[code].totalUpdates,
      avgUpdates.toFixed(2),
      encryptionStatus,
      confidence.toFixed(0) + '%',
      '', // Radio Channels
      master[code].lastSeen,
      '' // Service Area IDs
    ]);
  }
  
  // Sort by total incidents (descending)
  masterRows.sort((a, b) => b[6] - a[6]);
  
  masterSheet.getRange(2, 1, masterRows.length, 14).setValues(masterRows);
  Logger.log(`Created master table with ${masterRows.length} FD codes`);
}
```

---

## ✅ **Step 6: Geocode Addresses** (Python script)

Save this as `geocode_addresses.py`:

```python
import pandas as pd
import requests
import time

# Your Google Cloud API key
API_KEY = 'YOUR_API_KEY_HERE'

# Read addresses from Google Sheet (export as CSV first)
df = pd.read_csv('FD-Code-Addresses.csv')

def geocode(address, city, state):
    """Geocode an address using Google Geocoding API"""
    full_address = f"{address}, {city}, {state}"
    url = f"https://maps.googleapis.com/maps/api/geocode/json?address={full_address}&key={API_KEY}"
    
    response = requests.get(url)
    if response.status_code == 200:
        data = response.json()
        if data['results']:
            loc = data['results'][0]['geometry']['location']
            return loc['lat'], loc['lng']
    return None, None

# Geocode all addresses
for idx, row in df.iterrows():
    if pd.isna(row['Lat']) or row['Lat'] == '':
        lat, lng = geocode(row['Address'], row['City'], row['State'])
        df.at[idx, 'Lat'] = lat
        df.at[idx, 'Lng'] = lng
        
        if (idx + 1) % 10 == 0:
            print(f"Geocoded {idx + 1}/{len(df)} addresses")
            time.sleep(1)  # Rate limiting

# Save back to CSV
df.to_csv('FD-Code-Addresses-Geocoded.csv', index=False)
print("Done! Import back to Google Sheets")
```

---

## 📋 **Tools & Credentials Checklist**

### **MCP Servers**
- ✅ Google Sheets MCP (already configured)
- 🔲 GitHub MCP (optional, for version control)
- 🔲 Firebase MCP (if using Firestore for storage)

### **APIs Needed**
- 🔲 Google Geocoding API key
- 🔲 Google Places API key
- 🔲 (Optional) RadioReference.com API key (if available)

### **Accounts Needed**
- ✅ Google Cloud account (for APIs)
- 🔲 Apify account (for scraping) OR
- 🔲 Bright Data account (for scraping)

### **Python Libraries**
```bash
pip install pandas numpy requests geopandas shapely folium scikit-learn
```

---

## 🎯 **Your Next Message Should Include:**

1. **Schema of each reference sheet** (what columns, what data)
2. **Google Cloud API key** (or confirm you've created it)
3. **Which approach you prefer:**
   - **Option A:** Apps Script only (easier, slower)
   - **Option B:** Apps Script + Python (more powerful)
   - **Option C:** Full automation with Cloud Functions

Then I'll write the exact scripts you need to run! 🚀

