# 🚒 FD Code Analysis Project - Executive Summary

## 🎯 **Mission**
Determine which fire departments in NJ/NY are operating fully unencrypted, partially encrypted (first call only), or fully encrypted by analyzing BNN alert data and tracking FD code response patterns.

---

## 📊 **What We'll Build**

### **1. FD Code Intelligence Database**
- Map every FD code to fire departments, addresses, and service areas
- Track incident frequency and update patterns per FD code
- Calculate encryption classification based on update behavior

### **2. Encryption Classification System**
- **Fully Unencrypted:** Avg 1.5+ updates per incident (we see all dispatches)
- **First Call Only:** Avg ≤0.3 updates per incident (only initial alert, rest encrypted)
- **Fully Encrypted:** Never appears in BNN data

### **3. Validation & Cross-Reference**
- Match FD codes to official FDID codes
- Validate against known radio channel encryption data
- Identify anomalies (expected encrypted but found unencrypted, etc.)

---

## 📈 **Data Sources**

| Source | Purpose | Status |
|--------|---------|--------|
| **FD-Codes-Analytics** (live) | Real-time incident data | ✅ Active |
| **Historical Sheets** (3 sheets) | Past alert data for FD→address mapping | 🔲 To extract |
| **FDID Reference** | Official fire department IDs | 🔲 To inspect |
| **Radio Channel Data** | Known encryption status | 🔲 To inspect |
| **Service Area Maps** | Geographic boundaries | 🔲 To inspect |

---

## 🛠️ **Technical Stack**

### **Phase 1: Data Extraction & Processing**
- **Tool:** Google Apps Script
- **Input:** Live Google Sheet + Historical sheets
- **Output:** 4 new tracking sheets
- **Time:** 1-2 days

### **Phase 2: Geocoding & Spatial Analysis**
- **Tool:** Python + Google Geocoding API
- **Input:** Address list from tracking sheets
- **Output:** Lat/Lng coordinates, service area polygons
- **Cost:** ~$60 for 10,000 addresses
- **Time:** 3-5 days

### **Phase 3: Cross-Reference & Classification**
- **Tool:** Apps Script + Python
- **Input:** All tracking data + reference sheets
- **Output:** Encryption classification report
- **Time:** 2-3 days

### **Phase 4: Automation & Monitoring**
- **Tool:** Cloud Functions (optional) or Apps Script triggers
- **Schedule:** Daily processing of new incidents
- **Alerts:** Email/SMS when encryption pattern changes
- **Time:** 2-3 days

---

## 💰 **Budget Estimate**

| Item | Cost | Notes |
|------|------|-------|
| **Google Geocoding API** | $50 | 10,000 addresses @ $0.005 each |
| **Google Places API** | $10 | 500 FD lookups @ $0.017 each |
| **Cloud Functions** (optional) | $10/month | Automated processing |
| **Scraping Service** (optional) | $0-50 | If using Apify/Bright Data |
| **Total (one-time)** | **$60-70** | |
| **Total (monthly)** | **$10-20** | If fully automated |

---

## 📅 **Timeline**

### **Week 1: Data Extraction**
- ✅ Inspect all reference sheets
- ✅ Create 4 new tracking sheets
- ✅ Write Apps Script to extract live data
- ✅ Extract historical data
- ✅ Build FD Code Master Table

### **Week 2: Geocoding & Analysis**
- ✅ Set up Google Cloud APIs
- ✅ Geocode all addresses
- ✅ Calculate update statistics
- ✅ Classify encryption patterns

### **Week 3: Cross-Reference & Validation**
- ✅ Match FD codes to FDID
- ✅ Compare with radio channel data
- ✅ Generate classification report
- ✅ Identify anomalies

### **Week 4: Automation**
- ✅ Set up daily processing
- ✅ Create alerting system
- ✅ Build visualization dashboard

---

## 🎯 **Success Criteria**

- ✅ **Coverage:** 100% of FD codes in our data mapped
- ✅ **Accuracy:** 95%+ confidence in encryption classification (for FDs with sufficient data)
- ✅ **Completeness:** 80%+ of FD codes matched to official FDID
- ✅ **Timeliness:** New incidents processed within 24 hours
- ✅ **Actionability:** Clear, prioritized list of encrypted FDs for investigation

---

## 🚀 **Immediate Next Steps**

### **Your Tasks:**
1. **Inspect the 5 reference sheets** - Tell me what columns and data are in each
2. **Create Google Cloud API key** - Follow instructions in `FD_CODE_IMMEDIATE_ACTIONS.md`
3. **Create 4 new tabs** in FD-Codes-Analytics sheet (headers provided)

### **My Tasks:**
1. **Write Apps Script** to extract live data
2. **Write Apps Script** to build FD Code Master Table
3. **Write Python script** to geocode addresses (once API key ready)

---

## 📚 **Documentation**

- **📖 Full Plan:** `docs/architecture/FD_CODE_ANALYSIS_PLAN.md`
- **✅ Action Items:** `docs/tasks/FD_CODE_IMMEDIATE_ACTIONS.md`
- **📊 This Summary:** `docs/architecture/FD_CODE_EXECUTIVE_SUMMARY.md`

---

## 🔗 **Reference Sheet URLs**

### **To Inspect (Lines 1-11)**
1. `1vg-JXhPz9t2MY1gMWkTI_6Yd4Qi8DtbPrI9M4wvOu3I` - Unknown
2. `1Dp563w_Z5GLno3UY5IYLeKVTzpAMcZVvljOz1ZsboKo` - Unknown
3. `1xCFvGkbUtA7bxmXEYlfxeAmYcWnjC_VsNeiGkWYz5Co` - Unknown
4. `16cMrLO-pj__5qTID5Z2rtS4lTGaYsHUis-j9DmG-sgw` - Unknown
5. `1A5SNV_bDYktAIQ3WeUnEAU4ZTH0EkWwUaIaV4vHVSAQ` - Unknown

### **To Extract & Archive (Lines 13-19)**
6. `14uPfcDTsX2eI7dqxmj-95YdHnVgNAJ7Uzgrnl3x9W5U` - Historical alerts
7. `1vg-JXhPz9t2MY1gMWkTI_6Yd4Qi8DtbPrI9M4wvOu3I` - (duplicate?)
8. `1ly21BVGLGuMQ2Gtt5hNhFyWgVBQIu6oOC3hBoEPIZfA` - Historical alerts

---

## 💡 **Key Insights (Expected)**

After analysis, we'll be able to answer:

1. **How many fire departments are fully unencrypted?** (baseline for coverage)
2. **Which FDs switched from unencrypted to encrypted?** (policy changes)
3. **Are there geographic patterns?** (e.g., urban counties more encrypted)
4. **Which FDs have the largest service areas?** (major departments)
5. **Where is mutual aid most common?** (overlapping response zones)
6. **Is BNN coverage complete for unencrypted FDs?** (data quality check)

---

**Ready to start? Let's inspect those reference sheets!** 🚀

