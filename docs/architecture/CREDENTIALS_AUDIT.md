# Credentials & API Keys Audit

**Date:** December 17, 2025  
**Status:** Pre-PC Restart Snapshot  
**Purpose:** Complete inventory of credentials for alerts-sheets project

---

## 🔑 **Credentials We Already Have**

### **1. BNN Shared Secret** ✅
**Location:** Android app `PrefsManager` or environment variable  
**Purpose:** Authenticate with BNN news app API  
**Status:** ✅ Active (used in Android app)  
**Variable Name:** `BNN_SHARED_SECRET`

**Where It's Used:**
- Android app sends this with test payloads
- Validates requests from BNN app

**Action Needed:** None (already configured)

---

### **2. Google Sheets ID** ✅
**Location:** Android app endpoint configuration  
**Purpose:** Target Google Sheet for writing incident data  
**Status:** ✅ Active (FD-Codes-Analytics sheet)  
**Variable Name:** `FD_SHEET_ID`  
**Value:** `1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE`

**Where It's Used:**
- Android app posts to Apps Script web app URL
- Apps Script writes to this sheet

**Action Needed:** None (already configured)

---

### **3. Google Apps Script Web App URL** ✅
**Location:** Android app endpoint configuration  
**Purpose:** Receive POST requests from Android app  
**Status:** ✅ Active (deployed)  
**File:** `/scripts/Code.gs`

**Current Deployment:**
- URL format: `https://script.google.com/macros/s/.../exec`
- Receives JSON from Android
- Writes to Google Sheet

**Action Needed:** Will need to update to trigger backend webhook (Phase 2)

---

### **4. Firebase Project (EMU/NFA - DO NOT USE)** ⚠️
**Projects:** `emu-alerts-v2`, `nfa-alerts-v2`  
**Status:** ❌ OFF LIMITS - Keep these functioning for existing apps  
**Purpose:** Production apps that must stay operational

**Action Needed:** Create SEPARATE Firebase project (see below)

---

## 🆕 **Credentials We Need to CREATE**

### **Phase 2: Backend Enrichment Pipeline**

---

### **1. NEW Firebase Project** 🔥 **CRITICAL**
**Project Name:** `alerts-sheets` (or `alerts-sheets-prod`)  
**Purpose:** Isolated Firebase environment for this project  
**What It Includes:**
- Firestore Database (geocode cache, enriched incidents)
- Cloud Functions (enrichment pipeline)
- Firebase Authentication (if needed later)
- Cloud Storage (if needed later)

**Setup Steps:**
```bash
# Create new project at console.firebase.google.com
# Project ID: alerts-sheets

# Initialize Firebase locally
firebase login
firebase init

# Select:
# - Firestore
# - Functions (Node.js or Python)
# - Do NOT select Hosting, Auth, Storage (yet)
```

**Cost:** FREE tier generous
- Firestore: 1GB storage, 50K reads/day, 20K writes/day
- Cloud Functions: 2M invocations/month
- Expected usage: Well within free tier

**Action Needed:** ✅ Create at https://console.firebase.google.com

---

### **2. Google Cloud Service Account JSON** 🔐 **CRITICAL**
**Purpose:** Server-to-server authentication for backend  
**Permissions Needed:**
- Firestore: Read/Write
- Cloud Functions: Invoke
- Geocoding API: Use

**Where to Get:**
1. Go to Firebase Console → Project Settings → Service Accounts
2. Click "Generate New Private Key"
3. Download JSON file (KEEP SECURE!)
4. Store as environment variable or Firestore Secret

**File Format:**
```json
{
  "type": "service_account",
  "project_id": "alerts-sheets",
  "private_key_id": "...",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...",
  "client_email": "firebase-adminsdk@alerts-sheets.iam.gserviceaccount.com",
  "client_id": "...",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token"
}
```

**Store As:**
- **Development:** `functions/.env.local` → `GOOGLE_APPLICATION_CREDENTIALS_JSON`
- **Production:** Firebase Functions Secrets → `firebase functions:secrets:set GOOGLE_CREDS`

**Action Needed:** ✅ Generate after creating Firebase project

---

### **3. Google Maps Geocoding API Key** 🗺️ **HIGH PRIORITY**
**Purpose:** Convert addresses to lat/lng coordinates  
**Cost:** $5 per 1,000 requests (after free $200/month credit)  
**Expected Usage:** ~$10-20/month (with Firestore caching)

**Where to Get:**
1. Go to https://console.cloud.google.com
2. Select your `alerts-sheets` project
3. APIs & Services → Library → Search "Geocoding API"
4. Enable API
5. Credentials → Create Credentials → API Key
6. **Restrict Key:**
   - Application Restriction: IP addresses (your Cloud Functions IP)
   - API Restriction: Geocoding API only

**API Key Format:** `AIzaSyC...` (39 characters)

**Store As:**
- **Development:** `functions/.env.local` → `GOOGLE_MAPS_API_KEY`
- **Production:** Firebase Functions Secrets → `firebase functions:secrets:set MAPS_API_KEY`

**Documentation:** https://developers.google.com/maps/documentation/geocoding/start

**Action Needed:** ✅ Create and restrict

---

### **4. Attom Data API Key** 🏠 **HIGH PRIORITY**
**Purpose:** Property data (ownership, value, tax info, hazards)  
**Cost:** $0.10 per request (or tiered pricing)  
**Expected Usage:** ~$50-70/month (with Firestore caching)

**Where to Get:**
1. Go to https://www.attomdata.com
2. Sign up for Developer Account
3. Choose API Plan:
   - **Trial:** 1,000 free requests (good for testing)
   - **Pay-As-You-Go:** $0.10 per request
   - **Monthly Plan:** Bulk pricing available
4. Get API Key from Dashboard

**API Key Format:** Varies (alphanumeric string)

**Store As:**
- **Development:** `functions/.env.local` → `ATTOM_API_KEY`
- **Production:** Firebase Functions Secrets

**APIs to Use:**
- Property Basic API (ownership, beds/baths, square footage)
- Property Detail API (value, tax assessment)
- AVM API (automated valuation model)

**Documentation:** https://api.developer.attomdata.com/

**Action Needed:** ✅ Sign up for trial account first

---

### **5. Estated API Key** 🏘️ **MEDIUM PRIORITY**
**Purpose:** Property data (alternative/supplement to Attom)  
**Cost:** $0.05 per request (cheaper than Attom)  
**Expected Usage:** ~$20-30/month (with caching)

**Where to Get:**
1. Go to https://estated.com/developers
2. Sign up for API Access
3. Choose Pricing Tier:
   - **Free Tier:** 500 requests/month
   - **Starter:** $99/month (5,000 requests)
   - **Pay-As-You-Go:** $0.05 per request
4. Get API Key from Dashboard

**API Key Format:** `Bearer <token>` (header authentication)

**Store As:**
- **Development:** `functions/.env.local` → `ESTATED_API_KEY`
- **Production:** Firebase Functions Secrets

**APIs to Use:**
- Property API (owner, value, mortgage info)
- Combined API (address normalization + property data)

**Documentation:** https://estated.com/developers/docs/v4

**Action Needed:** ✅ Start with free tier

---

### **6. BatchData API Key** 📊 **LOW PRIORITY**
**Purpose:** Batch property lookups, additional data enrichment  
**Cost:** Varies by plan  
**Expected Usage:** ~$10-20/month

**Where to Get:**
1. Go to https://www.batchdata.com (or similar bulk data provider)
2. Sign up for Account
3. Get API credentials

**Note:** BatchData is OPTIONAL - use only if Attom/Estated don't provide sufficient data.

**Alternative Providers:**
- PropertyData.com
- Zillow API (limited, mostly read-only now)
- Realtor.com API (restricted)

**Action Needed:** ⏸️ Skip for Phase 2, evaluate later

---

### **7. Gemini AI API Key** 🤖 **MEDIUM PRIORITY**
**Purpose:** Convert technical codes to human-readable text  
**Cost:** Gemini 1.5 Flash: $0.075 per 1M input tokens (CHEAP!)  
**Expected Usage:** ~$5-10/month (with caching)

**Where to Get:**
1. Go to https://ai.google.dev
2. Sign in with Google Account
3. Get API Key → Create Key for `alerts-sheets` project

**OR (Better for Firebase):**
1. Install Firebase Extension: "Generate Text with Gemini"
2. Extension ID: `gemini-genai`
3. Automatic integration with Cloud Functions

**API Key Format:** `AIzaSy...` (similar to Maps API)

**Store As:**
- **If Extension:** Auto-configured
- **If Direct API:** `functions/.env.local` → `GEMINI_API_KEY`

**Documentation:** https://ai.google.dev/gemini-api/docs

**Action Needed:** ✅ Use Firebase Extension (easiest)

---

## 📋 **Credentials Summary Table**

| Credential | Status | Priority | Cost | Action |
|------------|--------|----------|------|--------|
| **BNN_SHARED_SECRET** | ✅ Have | N/A | FREE | None |
| **FD_SHEET_ID** | ✅ Have | N/A | FREE | None |
| **Apps Script URL** | ✅ Have | N/A | FREE | Update later |
| **Firebase Project (NEW)** | ❌ Need | P0 | FREE | Create at console.firebase.google.com |
| **Google Service Account JSON** | ❌ Need | P0 | FREE | Generate from Firebase |
| **Google Maps API Key** | ❌ Need | P1 | $10-20/mo | Enable + Restrict |
| **Attom Data API Key** | ❌ Need | P1 | $50-70/mo | Sign up for trial |
| **Estated API Key** | ❌ Need | P2 | $20-30/mo | Start with free tier |
| **BatchData API Key** | ❌ Need | P3 | $10-20/mo | Skip for now |
| **Gemini AI API Key** | ❌ Need | P2 | $5-10/mo | Use Firebase Extension |

**Total Monthly Cost (Phase 2):** ~$85-130/month (mostly property APIs)

---

## 🔒 **Credential Storage Strategy**

### **Development (Local)**
Create `functions/.env.local` (NEVER commit to git):

```bash
# Firebase
GOOGLE_APPLICATION_CREDENTIALS_JSON='{"type":"service_account",...}'
FIREBASE_PROJECT_ID=alerts-sheets

# APIs
GOOGLE_MAPS_API_KEY=AIzaSyC...
ATTOM_API_KEY=...
ESTATED_API_KEY=...
GEMINI_API_KEY=AIzaSy... (or use Firebase Extension)

# Existing
BNN_SHARED_SECRET=...
FD_SHEET_ID=1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE
```

**Add to `.gitignore`:**
```
functions/.env.local
functions/.env
*.json # Service account files
```

---

### **Production (Firebase)**
Use Firebase Functions Secrets (secure, managed):

```bash
# Set secrets
firebase functions:secrets:set GOOGLE_CREDS < service-account.json
firebase functions:secrets:set MAPS_API_KEY
firebase functions:secrets:set ATTOM_API_KEY
firebase functions:secrets:set ESTATED_API_KEY

# Access in code
const mapsKey = process.env.MAPS_API_KEY;
```

**Benefits:**
- Encrypted at rest
- Automatic rotation support
- Access control
- Audit logs

---

## 🚀 **Implementation Order (Post-Restart)**

### **Week 1: Firebase Setup** (After PC Restart)
1. ✅ Create new Firebase project `alerts-sheets`
2. ✅ Generate service account JSON
3. ✅ Initialize Firebase locally (`firebase init`)
4. ✅ Create Firestore database
5. ✅ Setup Cloud Functions structure

**Deliverable:** Working Firebase environment

---

### **Week 2: Geocoding**
1. ✅ Enable Google Maps Geocoding API
2. ✅ Create and restrict API key
3. ✅ Implement Firestore geocoding cache
4. ✅ Deploy basic enrichment function
5. ✅ Test with real addresses

**Deliverable:** Geocoding working with cache

---

### **Week 3: Property Data**
1. ✅ Sign up for Attom trial (1,000 free requests)
2. ✅ Sign up for Estated free tier (500 requests)
3. ✅ Implement property data fetching
4. ✅ Add Firestore caching
5. ✅ Test with real addresses from sheet

**Deliverable:** Property enrichment working

---

### **Week 4: AI + FD Codes**
1. ✅ Install Gemini Firebase Extension
2. ✅ Build FD code dictionary (Google Sheet or Firestore)
3. ✅ Implement AI enrichment with caching
4. ✅ Deploy full pipeline
5. ✅ Update EMU/NFA apps to read Firestore

**Deliverable:** 100% human-readable incidents

---

## 📝 **Credential Checklist (Post-Restart)**

**Before Starting Phase 2, Complete:**

- [ ] Create Firebase project: `alerts-sheets`
- [ ] Download service account JSON
- [ ] Enable Google Maps Geocoding API
- [ ] Get Google Maps API key (restricted)
- [ ] Sign up for Attom trial account
- [ ] Get Attom API key
- [ ] Sign up for Estated free tier
- [ ] Get Estated API key
- [ ] Install Gemini Firebase Extension (optional)
- [ ] Create `functions/.env.local` with all keys
- [ ] Add `.env.local` to `.gitignore`
- [ ] Test Firebase Functions deployment
- [ ] Verify Firestore read/write access

---

## 🎯 **Next Steps After PC Restart**

1. **Build & Test Android Fixes** (Permission crash + duplicate icon)
2. **Give AG the Parsing Fix Prompt** (4 fixes)
3. **Create Firebase Project** (`alerts-sheets`)
4. **Setup Credentials** (follow checklist above)
5. **Deploy Basic Enrichment Function** (geocoding only)
6. **Iterate** (add property APIs, FD codes, AI)

---

## 💰 **Cost Optimization Strategies**

### **1. Firestore Caching (HUGE Savings)**
- Cache geocoding results → Save $50-100/month
- Cache property data → Save $30-50/month
- Cache AI responses → Save $20-30/month

**Total Savings:** ~$100-180/month

---

### **2. Batch Processing**
- Batch multiple property lookups into single API call
- Some APIs offer bulk pricing (cheaper per request)

---

### **3. Free Tier Maximization**
- Google Cloud: $200/month credit (covers geocoding)
- Estated: 500 free requests/month
- Attom: 1,000 trial requests
- Firebase: Generous free tier

---

### **4. Rate Limiting**
- Limit enrichment to NEW incidents only (not updates)
- Enrich only if data not already in Firestore

---

## 🔐 **Security Best Practices**

1. **NEVER commit credentials to git**
2. **Use Firebase Secrets for production**
3. **Restrict API keys by IP/domain**
4. **Rotate keys every 90 days**
5. **Enable API usage alerts**
6. **Use least-privilege IAM roles**
7. **Audit access logs regularly**

---

## 📚 **Documentation Links**

- Firebase: https://firebase.google.com/docs
- Google Maps API: https://developers.google.com/maps/documentation
- Attom Data: https://api.developer.attomdata.com
- Estated: https://estated.com/developers/docs
- Gemini AI: https://ai.google.dev/gemini-api/docs
- Firebase Secrets: https://firebase.google.com/docs/functions/config-env

---

**Last Updated:** December 17, 2025 (Pre-PC Restart)  
**Next Review:** After Firebase project creation


