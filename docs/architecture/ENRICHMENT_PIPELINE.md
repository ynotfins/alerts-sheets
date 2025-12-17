# Data Enrichment Pipeline Architecture

**Status:** Planning Phase  
**Priority:** P1 - Critical for Production  
**Timeline:** Implement after frontend parsing is stable

---

## 🎯 Strategic Decision: Frontend vs Backend Parsing

### ❌ **Anti-Pattern: Heavy Frontend Parsing**
```
Android App
  ↓ (does ALL parsing, geocoding, enrichment)
Google Sheets
  ↓ (just stores final data)
```

**Problems:**
- App updates required for logic changes
- Limited API rate limits per device
- No centralized error correction
- Duplicate work across multiple clients
- Offline users get stale data

---

### ✅ **Best Practice: Thin Client + Smart Backend**

```
┌─────────────────────────────────────────────────────────────────────┐
│ FRONTEND (Android App)                                              │
│ - Capture notification                                              │
│ - Basic parsing (just enough to be functional)                      │
│ - Send to backend with raw originalBody                             │
└─────────────────────────────────────────────────────────────────────┘
                              ↓ POST JSON
┌─────────────────────────────────────────────────────────────────────┐
│ ENTRY POINT (Apps Script)                                           │
│ - Receive JSON from Android                                         │
│ - Write RAW data to "Staging" sheet                                 │
│ - Trigger Cloud Function via webhook                                │
│ - Return 200 OK immediately (fast response)                         │
└─────────────────────────────────────────────────────────────────────┘
                              ↓ Webhook trigger
┌─────────────────────────────────────────────────────────────────────┐
│ ENRICHMENT BACKEND (Cloud Function / Python Service)                │
│                                                                      │
│ 1. VALIDATION & CORRECTION                                          │
│    - Re-parse originalBody with robust backend parser               │
│    - Validate frontend parsing                                      │
│    - Fix any errors (backend parser is source of truth)             │
│                                                                      │
│ 2. GEOCODING (with Firestore cache)                                 │
│    - Check Firestore for existing geocode                           │
│    - If not found: Call Google Maps Geocoding API                   │
│    - Store result in Firestore (permanent cache)                    │
│    - Extract: lat, lng, formatted address, place_id                 │
│                                                                      │
│ 3. PROPERTY DATA ENRICHMENT                                         │
│    - Query Attom Data API (property details)                        │
│    - Query Estated API (owner, value, tax data)                     │
│    - Query BatchData API (additional property info)                 │
│    - Cache results in Firestore                                     │
│                                                                      │
│ 4. FD CODE TRANSLATION                                              │
│    - Cross-reference FD codes with dictionary sheet                 │
│    - Convert: "E-300" → "Engine 300 (FDNY Manhattan)"               │
│    - Convert: "njn005" → "North Bergen Fire Department"             │
│                                                                      │
│ 5. AI ENRICHMENT (Gemini/GPT webhook)                               │
│    - Normalize incident type to human language                      │
│    - Summarize incident details                                     │
│    - Extract entities (people, hazards, structures)                 │
│    - Generate natural language description                          │
│                                                                      │
│ 6. OUTPUT TO MULTIPLE DESTINATIONS                                  │
│    - Google Sheets (Analytics - raw + enriched)                     │
│    - Firestore (EMU Incidents collection)                           │
│    - Firestore (NFA Incidents collection)                           │
│    - BigQuery (long-term analytics)                                 │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ CONSUMPTION LAYER                                                   │
│ - EMU Incidents App (reads enriched Firestore data)                 │
│ - NFA Incidents App (reads enriched Firestore data)                 │
│ - Google Sheets (human-readable analytics)                          │
│ - Dashboards (Looker Studio / Tableau)                              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🏗️ **Recommended Tech Stack**

### **Backend Service: Firebase Cloud Functions (Node.js or Python)**

**Why Firebase?**
- ✅ Already using Firestore (geocode cache ready to go)
- ✅ Integrated with Apps Script via webhooks
- ✅ Auto-scales (serverless)
- ✅ Free tier generous (2M invocations/month)
- ✅ Easy deployment (`firebase deploy --only functions`)

**Alternative:** Python FastAPI on Cloud Run if you need more control

---

### **Geocoding Strategy: Hybrid Firestore Cache + API**

```javascript
// Cloud Function: geocodeAddress()
async function geocodeAddress(address) {
  // 1. Check Firestore cache first (FAST + FREE)
  const cacheRef = db.collection('geocode_cache').doc(addressHash);
  const cached = await cacheRef.get();
  
  if (cached.exists) {
    console.log('✅ Geocode cache HIT:', address);
    return cached.data();
  }
  
  // 2. Cache MISS - call Google Geocoding API
  console.log('⚠️ Geocode cache MISS, calling API:', address);
  const result = await googleMapsClient.geocode({ address }).asPromise();
  
  const geocoded = {
    address: result.json.results[0].formatted_address,
    lat: result.json.results[0].geometry.location.lat,
    lng: result.json.results[0].geometry.location.lng,
    place_id: result.json.results[0].place_id,
    cached_at: admin.firestore.FieldValue.serverTimestamp()
  };
  
  // 3. Store in Firestore for future requests
  await cacheRef.set(geocoded);
  
  return geocoded;
}
```

**Benefits:**
- First request hits API (costs $5/1000 requests)
- All future requests FREE and instant from Firestore
- You already have Firestore → reuse existing geocodes!

---

### **FD Code Dictionary: Google Sheet or Firestore**

**Option A: Google Sheet (Easiest)**
```
Sheet: "FD_Code_Dictionary"
Columns: FD_Code | Full_Name | Department | State | Type
Example: E-300   | Engine 300 | FDNY      | NY    | Engine
```

**Option B: Firestore (Fastest)**
```javascript
// Collection: fd_codes
{
  "E-300": {
    name: "Engine 300",
    department: "FDNY",
    state: "NY",
    type: "Engine",
    jurisdiction: "Manhattan"
  }
}
```

**Recommendation:** Start with Google Sheet (easy to edit), migrate to Firestore later for speed.

---

### **AI Enrichment: Backend Webhook to Gemini/GPT**

**DON'T:** Call AI from Android app (expensive, slow, user data exposed)  
**DO:** Call AI from backend (centralized, cached, secure)

```javascript
// Cloud Function: enrichWithAI()
async function enrichWithAI(incidentData) {
  const prompt = `
    Convert this fire/EMS incident to natural language:
    
    Type: ${incidentData.incidentType}
    Details: ${incidentData.incidentDetails}
    FD Codes: ${incidentData.fdCodes.join(', ')}
    
    Output:
    1. Human-readable incident type (not codes)
    2. Plain English summary (100 words)
    3. Severity level (1-5)
  `;
  
  const response = await geminiClient.generateContent(prompt);
  
  return {
    humanType: response.humanReadableType,
    summary: response.summary,
    severity: response.severity
  };
}
```

**Cost Optimization:**
- Cache AI responses in Firestore by `originalBody` hash
- Similar incidents reuse cached enrichment

---

## 📊 **Data Flow Example**

### **Step 1: Android App Sends**
```json
POST https://script.google.com/macros/s/.../exec
{
  "status": "New Incident",
  "timestamp": "2025-12-17T20:30:00Z",
  "incidentId": "#1844200",
  "state": "NY",
  "county": "Brooklyn",
  "city": "Brooklyn",
  "address": "456 Atlantic Ave",
  "incidentType": "Working Fire",
  "incidentDetails": "FD O/S with heavy smoke from 3rd floor",
  "originalBody": "NY| Brooklyn| 456 Atlantic Ave| Working Fire| FD O/S with heavy smoke from 3rd floor| <C> BNN | nyc337/ny153 | #1844200",
  "fdCodes": ["nyc337", "ny153"]
}
```

---

### **Step 2: Apps Script Receives → Writes to Sheet → Triggers Backend**
```javascript
// Apps Script: doPost()
function doPost(e) {
  const data = JSON.parse(e.postData.contents);
  
  // 1. Write RAW data to "Staging" sheet
  const stagingSheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Staging');
  stagingSheet.appendRow([
    data.incidentId,
    data.originalBody,
    data.timestamp,
    'PENDING_ENRICHMENT'
  ]);
  
  // 2. Trigger Cloud Function (webhook)
  const cloudFunctionUrl = 'https://us-central1-YOUR-PROJECT.cloudfunctions.net/enrichIncident';
  UrlFetchApp.fetch(cloudFunctionUrl, {
    method: 'POST',
    contentType: 'application/json',
    payload: JSON.stringify(data)
  });
  
  // 3. Return fast (don't wait for enrichment)
  return ContentService.createTextOutput(JSON.stringify({
    result: 'success',
    message: 'Incident queued for enrichment'
  })).setMimeType(ContentService.MimeType.JSON);
}
```

---

### **Step 3: Cloud Function Enriches**
```javascript
// Cloud Function: enrichIncident()
exports.enrichIncident = functions.https.onRequest(async (req, res) => {
  const incident = req.body;
  
  try {
    // 1. Validate/Re-parse
    const validated = await validateParsing(incident.originalBody);
    
    // 2. Geocode (Firestore cache first)
    const geocoded = await geocodeAddress(incident.address);
    
    // 3. Property data
    const propertyData = await Promise.all([
      queryAttom(geocoded.lat, geocoded.lng),
      queryEstated(incident.address),
      queryBatchData(incident.address)
    ]);
    
    // 4. Translate FD codes
    const translatedCodes = await translateFDCodes(incident.fdCodes);
    
    // 5. AI enrichment (cached)
    const aiEnriched = await enrichWithAI(incident);
    
    // 6. Build final enriched object
    const enriched = {
      ...incident,
      ...validated,
      geocoded: geocoded,
      propertyData: propertyData,
      fdCodesHuman: translatedCodes,
      aiSummary: aiEnriched.summary,
      humanType: aiEnriched.humanType,
      severity: aiEnriched.severity,
      enrichedAt: admin.firestore.FieldValue.serverTimestamp()
    };
    
    // 7. Write to Firestore
    await db.collection('emu_incidents').doc(incident.incidentId).set(enriched);
    await db.collection('nfa_incidents').doc(incident.incidentId).set(enriched);
    
    // 8. Update Google Sheet with enriched data
    await updateSheetWithEnrichedData(enriched);
    
    res.status(200).send({ success: true, incidentId: incident.incidentId });
    
  } catch (error) {
    console.error('Enrichment failed:', error);
    res.status(500).send({ error: error.message });
  }
});
```

---

### **Step 4: Apps Read Human-Readable Data**
```javascript
// EMU Incidents App (reads Firestore)
const enrichedIncident = {
  id: "#1844200",
  type: "Working Structure Fire", // AI-generated human type
  summary: "FDNY Engine 337 and Ladder 153 responding to heavy smoke condition on 3rd floor of residential building at 456 Atlantic Avenue in Brooklyn. Incident escalated to working fire status.", // AI summary
  location: "456 Atlantic Ave, Brooklyn, NY 11217",
  coordinates: { lat: 40.6844, lng: -73.9772 },
  responding: [
    "Engine 337 (FDNY Brooklyn)",
    "Ladder 153 (FDNY Brooklyn)"
  ],
  property: {
    type: "Multi-family Residential",
    built: 1925,
    stories: 4,
    owner: "Brooklyn Properties LLC",
    value: "$1,250,000"
  },
  severity: 4, // AI-calculated
  status: "Active",
  updates: [
    { time: "20:30", text: "Units dispatched" },
    { time: "20:35", text: "On scene, heavy smoke 3rd floor" },
    { time: "20:40", text: "Working fire declared" }
  ]
}
```

**100% Human Readable** ✅ No codes, no jargon, perfect for EMU/NFA apps!

---

## 🚀 **Implementation Phases**

### **Phase 1: Finish Frontend Parsing** (THIS WEEK)
- [ ] AG makes 3 fixes to `Parser.kt`
- [ ] Verify Android sends clean, consistent JSON
- [ ] Apps Script handles raw data correctly
- [ ] Google Sheet shows proper update appending (like Row 22)

**Milestone:** Solid foundation of clean incident IDs + FD codes

---

### **Phase 2: Setup Backend Infrastructure** (NEXT WEEK)
- [ ] Create Firebase Cloud Functions project
- [ ] Setup Firestore collections:
  - `geocode_cache` (reuse existing geocodes)
  - `emu_incidents` (enriched data)
  - `nfa_incidents` (enriched data)
  - `fd_codes` (dictionary)
- [ ] Create "FD_Code_Dictionary" Google Sheet
- [ ] Deploy basic enrichment function (just geocoding first)

---

### **Phase 3: Add Geocoding + Property APIs** (WEEK 2)
- [ ] Implement Firestore geocoding cache
- [ ] Integrate Google Maps Geocoding API
- [ ] Add Attom Data API integration
- [ ] Add Estated API integration
- [ ] Add BatchData API integration
- [ ] Test with real addresses from your sheet

---

### **Phase 4: FD Code Translation** (WEEK 2-3)
- [ ] Build NY/NJ/PA dispatch code dictionary
  - Sources: FDNY website, county dispatch sites
  - Format: Code → Full Name → Department
- [ ] Implement code lookup logic
- [ ] Handle multi-state codes (cross-reference)
- [ ] Test with real FD codes from your sheet

---

### **Phase 5: AI Enrichment** (WEEK 3)
- [ ] Setup Gemini API (Firebase extension) or OpenAI
- [ ] Design prompt templates for:
  - Incident type normalization
  - Summary generation
  - Severity calculation
- [ ] Implement Firestore caching for AI responses
- [ ] Test output quality

---

### **Phase 6: Apps Integration** (WEEK 4)
- [ ] Update EMU Incidents app to read Firestore
- [ ] Update NFA Incidents app to read Firestore
- [ ] Build human-readable UI components
- [ ] Real-time updates via Firestore listeners

---

## 💰 **Cost Estimates**

### **Firestore (Storage)**
- 1 GB storage: FREE
- 50K reads/day: FREE
- 20K writes/day: FREE
- **Your usage:** Well within free tier

### **Cloud Functions**
- 2M invocations/month: FREE
- **Your usage:** ~1K incidents/month = FREE

### **Geocoding API**
- $5 per 1,000 requests
- **With Firestore cache:** Most requests FREE (cache hits)
- **Estimated:** $10-20/month

### **Property Data APIs**
- Attom: $0.10 per request
- Estated: $0.05 per request
- BatchData: $0.08 per request
- **With caching:** $50-100/month

### **AI (Gemini)**
- Gemini 1.5 Flash: $0.075 per 1M input tokens
- **Your usage:** ~$5-10/month

### **Total Monthly Cost: ~$65-130**
(Mostly property APIs; Firestore + Functions nearly free)

---

## 🎯 **Decision Matrix**

| Question | Recommendation |
|----------|----------------|
| **Where to parse?** | **Frontend:** Basic parsing (incident ID, state, city)<br>**Backend:** Source of truth, re-parses + validates |
| **Where to geocode?** | **Backend only** (Firestore cache = reuse existing geocodes!) |
| **Where to call AI?** | **Backend only** (centralized, cached, secure) |
| **Where to store enriched data?** | **Firestore** (EMU/NFA apps read from here) |
| **Apps Script role?** | **Entry point** (receive from Android, trigger backend) |
| **Google Sheets role?** | **Analytics dashboard** (human-readable, not source of truth) |

---

## ✅ **Answer to Your Question**

> "Should we send the notification over in one block and let the backend handle everything, or is it beneficial to have the frontend parse it?"

**Best Practice:** **Thin frontend + Smart backend**

**Frontend (Android):**
- ✅ Basic parsing (just enough to display in app)
- ✅ Send `originalBody` field (backend can re-parse)
- ✅ Fast response (don't wait for geocoding/AI)

**Backend (Cloud Function):**
- ✅ Re-parse `originalBody` (robust, updatable)
- ✅ Validate frontend parsing (fix errors)
- ✅ Geocoding, property data, AI enrichment
- ✅ Write to Firestore (EMU/NFA apps consume)

**Why Both?**
- Frontend parsing gives **immediate user feedback**
- Backend parsing ensures **data quality** for analytics
- Backend enrichment adds **geocoding + AI** without app updates

---

## 🔄 **Migration Path: Reuse Existing Geocodes**

You said: *"I already have these addresses being geocoded on my Firestore database."*

**Perfect!** Just point the backend to your existing Firestore geocode collection:

```javascript
// Check YOUR existing Firestore geocode collection
const existingGeocode = await db.collection('YOUR_GEOCODE_COLLECTION')
  .where('address', '==', incident.address)
  .limit(1)
  .get();

if (!existingGeocode.empty) {
  console.log('✅ Reusing existing geocode from Firestore!');
  return existingGeocode.docs[0].data();
}

// Only call API if not in your existing database
const newGeocode = await googleMapsClient.geocode({ address });
// ...store it...
```

**Benefit:** Zero geocoding API cost for addresses you've already processed!

---

## 📋 **Next Steps**

### **This Week (Priority 1):**
1. ✅ Send AG the parsing fix prompt (3 small changes)
2. ✅ Verify Android sends clean JSON with correct IDs
3. ✅ Confirm updates append to same row (like Row 22)

### **Next Week (Priority 2):**
1. Setup Firebase Cloud Functions project
2. Create Firestore collections
3. Build geocoding cache (reuse your existing geocodes!)
4. Deploy basic enrichment function

### **Week 3-4 (Priority 3):**
1. Add property API integrations
2. Build FD code dictionary
3. Add AI enrichment
4. Update EMU/NFA apps to read Firestore

---

## 🎉 **Final Answer**

**YES** - Fix frontend parsing this week with AG.  
**THEN** - Build backend enrichment pipeline next week.  

**Backend is source of truth for:**
- ✅ Validation & error correction
- ✅ Geocoding (reuse your Firestore geocodes!)
- ✅ Property data (Attom, Estated, BatchData)
- ✅ FD code translation
- ✅ AI enrichment

**Frontend is for:**
- ✅ Fast user feedback
- ✅ Sending raw data to backend
- ✅ Displaying enriched data from Firestore

This architecture is **industry best practice** and sets you up for scale! 🚀

