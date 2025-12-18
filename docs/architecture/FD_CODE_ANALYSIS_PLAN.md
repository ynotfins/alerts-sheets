# 🚒 Fire Department Code Analysis & Encryption Tracking Plan

## 📋 **Project Overview**

**Goal:** Map FD codes to fire departments, determine service areas, and track encryption patterns (fully unencrypted, partially encrypted, fully encrypted).

---

## 🎯 **Core Objectives**

### 1. **FD Code to Address Mapping**
- Track which FD codes respond to which addresses
- Build a geographic service area map for each FD code
- Count frequency of each FD code at each address

### 2. **Update Tracking (Encryption Detection)**
- Count number of updates per incident by FD code
- Classify FDs into 3 categories:
  - **Fully Unencrypted**: Multiple updates tracked (normal behavior)
  - **First Call Only**: Only 1 initial alert, no updates (first call unencrypted, rest encrypted)
  - **Fully Encrypted**: Never appears in BNN data

### 3. **Cross-Reference with Existing Data**
- Compare live FD codes with known FDID codes
- Validate against radio channel encryption data
- Match with known FD addresses and service areas

### 4. **Service Area Determination**
- Use address clustering to determine FD boundaries
- Cross-reference with municipal/county boundaries
- Identify overlapping coverage areas (mutual aid)

---

## 📊 **Data Sources**

### **A. Live Data (Current System)**
- **Source:** `FD-Codes-Analytics` sheet (ID: `1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE`)
- **Contains:** Real-time BNN alerts with FD codes, addresses, incident types, timestamps, update counts
- **Update Frequency:** Real-time (as incidents occur)

### **B. Historical Alert Data** (Lines 13-19, extract then discard)
1. `14uPfcDTsX2eI7dqxmj-95YdHnVgNAJ7Uzgrnl3x9W5U` (appears twice: line 13, 19)
2. `1vg-JXhPz9t2MY1gMWkTI_6Yd4Qi8DtbPrI9M4wvOu3I` (line 15)
3. `1ly21BVGLGuMQ2Gtt5hNhFyWgVBQIu6oOC3hBoEPIZfA` (line 17)

**Action:** Extract FD Code → Address mappings, then archive/discard.

### **C. Reference Data** (Lines 1-11)
1. **Line 1:** `1vg-JXhPz9t2MY1gMWkTI_6Yd4Qi8DtbPrI9M4wvOu3I` - Fire Department Master Data (?)
2. **Line 3:** `1Dp563w_Z5GLno3UY5IYLeKVTzpAMcZVvljOz1ZsboKo` - FDID Codes / Addresses (?)
3. **Line 5:** Grok conversation (not a spreadsheet, skip or extract manually)
4. **Line 7:** `1xCFvGkbUtA7bxmXEYlfxeAmYcWnjC_VsNeiGkWYz5Co` - Radio Channels / Encryption Data (?)
5. **Line 9:** `16cMrLO-pj__5qTID5Z2rtS4lTGaYsHUis-j9DmG-sgw` - FD Service Areas (?)
6. **Line 11:** `1A5SNV_bDYktAIQ3WeUnEAU4ZTH0EkWwUaIaV4vHVSAQ` - Encryption Rules (?)

**Action:** Need to inspect each sheet to understand schema and extract relevant data.

---

## 🗂️ **New Data Structure**

### **1. FD Code Master Table** (New Sheet: `FD-Code-Master`)

| Column | Description | Example |
|--------|-------------|---------|
| **FD Code** | The code from BNN alerts | `nj312`, `njn751`, `nyl785` |
| **Fire Department Name** | Resolved FD name | `Paterson Fire Department` |
| **FDID** | Official FDID (if matched) | `NJ-1234` |
| **County** | Primary county | `Passaic` |
| **State** | NJ or NY | `NJ` |
| **Primary Address** | FD headquarters address | `115 Van Houten St, Paterson, NJ` |
| **Total Incidents** | Count of incidents this FD responded to | `347` |
| **Total Updates** | Sum of all updates across incidents | `892` |
| **Avg Updates per Incident** | `Total Updates / Total Incidents` | `2.57` |
| **Encryption Status** | `Fully Unencrypted`, `First Call Only`, `Fully Encrypted`, `Unknown` | `Fully Unencrypted` |
| **Confidence Score** | 0-100% based on data volume | `95%` |
| **Radio Channels** | Known radio freqs (if available) | `154.280 MHz` |
| **Last Seen** | Date of most recent incident | `2025-12-17` |
| **Service Area IDs** | List of service area polygon IDs | `SA-001, SA-002` |

### **2. FD Code to Address Mapping** (New Sheet: `FD-Code-Addresses`)

| Column | Description | Example |
|--------|-------------|---------|
| **FD Code** | The FD code | `nj312` |
| **Address** | Incident address | `123 Main St` |
| **City** | City | `Paterson` |
| **County** | County | `Passaic` |
| **State** | State | `NJ` |
| **First Seen** | Date first responded here | `2024-06-12` |
| **Last Seen** | Date last responded here | `2025-12-15` |
| **Total Incidents** | Count at this address | `5` |
| **Incident Types** | Comma-separated incident types | `Fire, EMS, Hazmat` |
| **Lat** | Latitude (geocoded) | `40.9168` |
| **Lng** | Longitude (geocoded) | `-74.1718` |

### **3. Incident-Level Detail** (New Sheet: `FD-Code-Incidents`)

| Column | Description | Example |
|--------|-------------|---------|
| **Incident ID** | From BNN | `#1844772` |
| **FD Codes** | Pipe-separated FD codes that responded | `nj312|njn751|nyu9w` |
| **Address** | Incident address | `61 Terrace St` |
| **City** | City | `Park Ridge` |
| **County** | County | `Bergen` |
| **State** | State | `NJ` |
| **Incident Type** | Type | `3rd Alarm` |
| **Initial Alert Time** | First alert timestamp | `12/15/2025 5:18:37 PM` |
| **Update Count** | Number of updates received | `3` |
| **Update Times** | Timestamps of updates | `5:25 PM, 5:47 PM, 6:12 PM` |
| **Source Sheet** | Which sheet this came from | `FD-Codes-Analytics` |
| **Source Row** | Row number in source sheet | `234` |

### **4. Encryption Classification Report** (New Sheet: `FD-Encryption-Report`)

| Column | Description |
|--------|-------------|
| **FD Code** | The FD code |
| **Fire Department Name** | Resolved name |
| **Classification** | `Fully Unencrypted`, `First Call Only`, `Fully Encrypted`, `Unknown` |
| **Evidence** | Explanation of classification |
| **Sample Size** | Number of incidents analyzed |
| **Avg Updates** | Average updates per incident |
| **Expected Behavior** | From reference data |
| **Match Status** | ✅ Matches / ⚠️ Anomaly / ❓ Insufficient Data |
| **Notes** | Any observations |

---

## 🛠️ **Technical Implementation**

### **Phase 1: Data Extraction** (Week 1)

#### **Step 1.1: Read Current Live Data**
```javascript
// Apps Script to read FD-Codes-Analytics
function extractLiveData() {
  const sheet = SpreadsheetApp.openById('1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE').getSheets()[0];
  const data = sheet.getDataRange().getValues();
  
  // Process each row, extract FD codes, count updates
  // Build incident-level records
}
```

#### **Step 1.2: Extract Historical Data**
- Pull data from sheets on lines 13-19
- Extract only: FD codes, addresses, dates
- Merge into unified dataset
- Archive original sheets

#### **Step 1.3: Load Reference Data**
- Read FDID mappings (line 3)
- Read radio channel data (line 7)
- Read encryption rules (line 11)
- Read known service areas (line 9)

### **Phase 2: Data Processing** (Week 2)

#### **Step 2.1: Build FD Code Master Table**
```python
# Aggregate incidents by FD code
for incident in all_incidents:
    for fd_code in incident['fd_codes']:
        master[fd_code]['total_incidents'] += 1
        master[fd_code]['total_updates'] += incident['update_count']
        master[fd_code]['addresses'].add(incident['address'])
        master[fd_code]['last_seen'] = max(master[fd_code]['last_seen'], incident['date'])
```

#### **Step 2.2: Calculate Update Statistics**
```python
for fd_code in master:
    master[fd_code]['avg_updates'] = master[fd_code]['total_updates'] / master[fd_code]['total_incidents']
    
    # Classify encryption pattern
    if master[fd_code]['avg_updates'] >= 1.5:
        master[fd_code]['encryption_status'] = 'Fully Unencrypted'
    elif master[fd_code]['avg_updates'] <= 0.3:
        master[fd_code]['encryption_status'] = 'First Call Only'
    else:
        master[fd_code]['encryption_status'] = 'Unknown'
```

#### **Step 2.3: Geocode Addresses**
- Use Google Maps Geocoding API
- Batch process all unique addresses
- Store lat/lng for spatial analysis
- Budget: ~$5-10 per 1000 addresses

#### **Step 2.4: Determine Service Areas**
```python
# Cluster addresses by FD code
from sklearn.cluster import DBSCAN

for fd_code in master:
    addresses = get_addresses_for_fd(fd_code)
    coords = [(addr['lat'], addr['lng']) for addr in addresses]
    
    # Cluster to find service area(s)
    clustering = DBSCAN(eps=0.01, min_samples=3).fit(coords)
    
    # Create polygon boundaries
    service_areas[fd_code] = create_polygon_from_clusters(clustering)
```

### **Phase 3: Cross-Reference & Validation** (Week 3)

#### **Step 3.1: Match FD Codes to FDID**
- Compare live FD codes with FDID database
- Use fuzzy matching on FD names
- Manual review of ambiguous matches

#### **Step 3.2: Validate Against Radio Data**
- Cross-reference with known encryption status
- Flag discrepancies for investigation
- Update confidence scores

#### **Step 3.3: Generate Classification Report**
- Compare live behavior vs. expected behavior
- Identify anomalies (e.g., FDs that should be encrypted but aren't)
- Create actionable insights

### **Phase 4: Automation & Monitoring** (Week 4)

#### **Step 4.1: Real-Time Updates**
- Apps Script trigger to process new incidents daily
- Update FD Code Master Table automatically
- Recalculate encryption stats weekly

#### **Step 4.2: Alerting**
- Alert when FD encryption pattern changes
- Alert when new FD code appears
- Alert when service area expands significantly

---

## 🔧 **Tools & Services Needed**

### **1. MCP Servers**
- ✅ **Google Sheets MCP** (already configured)
  - Read/write all spreadsheets
  - Bulk data extraction
  
- 🆕 **Google Maps MCP** (if available)
  - Geocoding API
  - Places API (for FD headquarters)
  - Distance Matrix API (for service area boundaries)

### **2. APIs & Credentials**

#### **Google Cloud Services**
- **Geocoding API** ($5 per 1000 requests)
  - Estimate: 10,000 unique addresses = $50
- **Places API** ($17 per 1000 requests)
  - Estimate: 500 FDs to locate = $8.50
- **Maps JavaScript API** (free for <25k loads/day)
  - For visualization

#### **Scraping (100k pages/month budget)**
- **Target websites:**
  - RadioReference.com (radio frequencies, encryption status)
  - Fire Department websites (official FDID, service area info)
  - County/municipal 911 dispatch pages
- **Tool:** Apify, Bright Data, or custom Python scraper (BeautifulSoup + Selenium)
- **Priority targets:**
  - NJ Fire Marshal office (FDID registry)
  - NY Fire Service database

### **3. Analysis Tools**

#### **Python Libraries**
```bash
pip install pandas numpy scipy scikit-learn geopy shapely geopandas folium
```

- **pandas**: Data manipulation
- **numpy/scipy**: Statistical analysis
- **scikit-learn**: Clustering for service areas
- **geopy**: Geocoding
- **shapely**: Geometric operations (polygons, boundaries)
- **geopandas**: Spatial data analysis
- **folium**: Interactive maps

#### **Google Apps Script**
- For direct integration with Google Sheets
- Real-time processing of new incidents
- Automated report generation

### **4. Storage & Compute**

#### **Option A: Google Cloud (Recommended)**
- **Cloud Functions**: Process new incidents automatically
- **BigQuery**: Store large datasets for complex queries
- **Cloud Storage**: Archive historical data
- **Estimated cost:** $20-50/month

#### **Option B: Local Python Scripts**
- Run analysis scripts on your machine
- Export results to Google Sheets
- **Cost:** Free, but manual

---

## 📦 **Deliverables**

### **Immediate (Week 1-2)**
1. ✅ FD Code Master Table (all unique codes with basic stats)
2. ✅ FD Code to Address Mapping (complete address history)
3. ✅ Incident-Level Detail (all incidents with update counts)

### **Short-term (Week 3-4)**
4. ✅ Encryption Classification Report
5. ✅ Service Area Maps (visual polygons on Google Maps)
6. ✅ Cross-Reference Report (FD codes matched to FDID)

### **Ongoing**
7. ✅ Real-time Dashboard (live encryption status)
8. ✅ Anomaly Alerts (encryption pattern changes)
9. ✅ Weekly Summary Reports

---

## 🚀 **Next Steps**

### **Action Items (Right Now)**

1. **Inspect Reference Sheets**
   - Open each spreadsheet (lines 1-11)
   - Document schema and data quality
   - Identify which contain FDID, encryption data, service areas

2. **Set up Google Cloud Project**
   - Enable Geocoding API
   - Enable Places API
   - Generate API key
   - Set up billing ($50 budget)

3. **Create New Sheets**
   - `FD-Code-Master`
   - `FD-Code-Addresses`
   - `FD-Code-Incidents`
   - `FD-Encryption-Report`

4. **Write Initial Apps Script**
   - Extract all incidents from `FD-Codes-Analytics`
   - Parse FD codes from each row
   - Count updates per incident
   - Write to `FD-Code-Incidents`

5. **Extract Historical Data**
   - Read sheets on lines 13-19
   - Extract FD code + address pairs
   - Merge into unified dataset

---

## 🎯 **Success Metrics**

- ✅ **Coverage:** 100% of FD codes mapped to addresses
- ✅ **Accuracy:** 95%+ confidence in encryption classification
- ✅ **Completeness:** FDID matched for 80%+ of FD codes
- ✅ **Timeliness:** Updates processed within 24 hours
- ✅ **Actionability:** Clear list of encrypted FDs for investigation

---

## 🔒 **Encryption Classification Logic**

### **Thresholds (to be tuned with real data)**

```python
# Based on average updates per incident
if avg_updates >= 1.5 and sample_size >= 10:
    classification = "Fully Unencrypted"
    confidence = min(sample_size / 50 * 100, 100)
    
elif avg_updates <= 0.3 and sample_size >= 10:
    classification = "First Call Only"
    confidence = min(sample_size / 50 * 100, 100)
    
elif avg_updates > 0.3 and avg_updates < 1.5:
    classification = "Partial/Mixed"  # Needs investigation
    confidence = 50
    
elif sample_size < 10:
    classification = "Insufficient Data"
    confidence = 0
```

### **Validation Against Known Data**

```python
if reference_data[fd_code]['encryption'] == 'None':
    if classification != 'Fully Unencrypted':
        flag_anomaly(fd_code, "Expected unencrypted, found encrypted")
        
elif reference_data[fd_code]['encryption'] == 'First Call Only':
    if classification != 'First Call Only':
        flag_anomaly(fd_code, "Expected first-call-only, found different pattern")
```

---

## 📝 **Questions to Answer**

1. **How many unique FD codes exist in our data?**
2. **What percentage can we confidently classify?**
3. **Which FD codes have changed encryption patterns over time?**
4. **Are there geographic clusters of encrypted FDs?**
5. **Which FDs have the largest service areas?**
6. **Which addresses receive responses from the most FDs (mutual aid zones)?**
7. **What is the average response time by FD code?** (if timestamp data allows)

---

## 💡 **Optimization Ideas**

- **Caching:** Store geocoded addresses to avoid re-querying
- **Batch Processing:** Process incidents in batches of 100-500
- **Incremental Updates:** Only process new incidents since last run
- **Parallel Processing:** Use Cloud Functions to process multiple FDs simultaneously
- **ML Classification:** Train a model to predict encryption status based on update patterns

---

**Ready to begin Phase 1?** 🚀

