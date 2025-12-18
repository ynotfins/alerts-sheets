# 🔥 FD Code Analysis - Quick Reference

## 📋 **What You Need to Do Right Now**

### 1️⃣ **Open These 5 Sheets & Tell Me What's Inside**

For EACH sheet, tell me:
- What's the sheet called? (title)
- What are the column headers?
- What type of data is in it? (FD names, addresses, radio frequencies, etc.)
- How many rows of data?

**Sheet URLs:**
1. https://docs.google.com/spreadsheets/d/1vg-JXhPz9t2MY1gMWkTI_6Yd4Qi8DtbPrI9M4wvOu3I
2. https://docs.google.com/spreadsheets/d/1Dp563w_Z5GLno3UY5IYLeKVTzpAMcZVvljOz1ZsboKo
3. https://docs.google.com/spreadsheets/d/1xCFvGkbUtA7bxmXEYlfxeAmYcWnjC_VsNeiGkWYz5Co
4. https://docs.google.com/spreadsheets/d/16cMrLO-pj__5qTID5Z2rtS4lTGaYsHUis-j9DmG-sgw
5. https://docs.google.com/spreadsheets/d/1A5SNV_bDYktAIQ3WeUnEAU4ZTH0EkWwUaIaV4vHVSAQ

---

### 2️⃣ **Get Google Cloud API Key**

1. Go to: https://console.cloud.google.com
2. Select or create project "alerts-sheets"
3. Enable **Geocoding API**
4. Enable **Places API**
5. **Credentials** → **+ CREATE CREDENTIALS** → **API key**
6. Copy the key and send it to me (we'll use it for address geocoding)

---

### 3️⃣ **Create 4 New Tabs in FD-Codes-Analytics Sheet**

**Tab names & headers:**

#### **Tab: `FD-Code-Master`**
```
FD Code | Fire Department Name | FDID | County | State | Primary Address | Total Incidents | Total Updates | Avg Updates | Encryption Status | Confidence % | Radio Channels | Last Seen | Service Area IDs
```

#### **Tab: `FD-Code-Addresses`**
```
FD Code | Address | City | County | State | First Seen | Last Seen | Total Incidents | Incident Types | Lat | Lng
```

#### **Tab: `FD-Code-Incidents`**
```
Incident ID | FD Codes (pipe-separated) | Address | City | County | State | Incident Type | Initial Alert Time | Update Count | Update Times | Source Sheet | Source Row
```

#### **Tab: `FD-Encryption-Report`**
```
FD Code | Fire Department Name | Classification | Evidence | Sample Size | Avg Updates | Expected Behavior | Match Status | Notes
```

---

## 🎯 **What I'll Do Once You Give Me That Info**

1. ✅ Write Apps Script to extract all data from your live sheet
2. ✅ Build FD Code Master Table (with encryption classification)
3. ✅ Create address mapping (FD code → addresses)
4. ✅ Write Python script to geocode addresses
5. ✅ Cross-reference with your existing data
6. ✅ Generate final encryption classification report

---

## 🔑 **Key Concepts**

### **FD Code**
- Example: `nj312`, `njn751`, `nyl785`
- The codes that appear in BNN alerts (Column K+ in your sheet)
- We're trying to figure out which fire department each code represents

### **FDID**
- Official fire department ID (may be different from FD codes)
- Example: `NJ-1234`
- Used by NFIRS (National Fire Incident Reporting System)

### **Update Count**
- How many times an incident is updated
- **Key indicator of encryption:**
  - High updates (1.5+) = Fully unencrypted (we see all dispatches)
  - Low updates (≤0.3) = First call only (rest encrypted)
  - Zero appearances = Fully encrypted (we never see them)

### **Service Area**
- The geographic region a fire department covers
- Determined by clustering the addresses they respond to

---

## 📊 **Encryption Classification Logic**

```
If avg_updates >= 1.5 and sample_size >= 10:
    → "Fully Unencrypted" (we see everything)

If avg_updates <= 0.3 and sample_size >= 10:
    → "First Call Only" (initial alert only, rest encrypted)

If sample_size < 10:
    → "Insufficient Data" (need more incidents)
```

---

## 💰 **Cost Breakdown**

- **Geocoding:** $50 (10,000 addresses)
- **Places API:** $10 (500 FD lookups)
- **Cloud Functions:** $10/month (optional, for automation)
- **Scraping:** $0-50 (if needed)

**Total one-time:** ~$60-70  
**Total monthly (if automated):** ~$10-20

---

## 📚 **Full Documentation**

- **Executive Summary:** `docs/architecture/FD_CODE_EXECUTIVE_SUMMARY.md`
- **Complete Plan:** `docs/architecture/FD_CODE_ANALYSIS_PLAN.md`
- **Action Items:** `docs/tasks/FD_CODE_IMMEDIATE_ACTIONS.md`
- **This Reference:** `docs/tasks/FD_CODE_QUICK_REF.md`

---

## ✅ **Your Reply Should Include:**

1. **Schema for each of the 5 reference sheets** (sheet name, columns, data type)
2. **Google Cloud API key** (or confirmation you created it)
3. **Confirmation that 4 new tabs are created** in FD-Codes-Analytics

Then I'll write all the scripts and we'll execute! 🚀

