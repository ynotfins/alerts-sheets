# Strategic Decision: Architecture & Roadmap

**Date:** December 17, 2025  
**Decision:** Two-phase approach with thin frontend + smart backend

---

## 🎯 **Your Question**

> "Should we add backend parsing logic now or wait? Should frontend parse then backend validates, or just let backend handle everything?"

---

## ✅ **Answer: Both (In Phases)**

### **Phase 1: Fix Frontend Parsing** (THIS WEEK)
**Status:** Ready to execute - give AG the prompt

**Why do this first?**
- Establishes clean incident IDs (critical for deduplication)
- Removes FD code noise (prevents garbage data)
- Unblocks all downstream work
- Only 3 small changes (~10 lines of code)

**Deliverable:** Android sends consistent, clean JSON to Apps Script

---

### **Phase 2: Backend Enrichment** (NEXT 2-4 WEEKS)
**Status:** Architecture designed (see `ENRICHMENT_PIPELINE.md`)

**What backend will do:**
1. **Re-parse & validate** (backend parser = source of truth)
2. **Geocode addresses** (reuse your existing Firestore geocodes!)
3. **Property data** (Attom, Estated, BatchData APIs)
4. **FD code translation** (codes → human language)
5. **AI enrichment** (Gemini for natural language summaries)
6. **Write to Firestore** (EMU/NFA apps consume enriched data)

**Deliverable:** 100% human-readable incidents in EMU/NFA apps

---

## 🏗️ **Best Practice: Why Both Frontend AND Backend Parsing?**

### **Frontend (Android App) Role:**
```
✅ Capture notification immediately
✅ Basic parsing (incident ID, state, city)
✅ Display in app (fast user feedback)
✅ Send to backend with originalBody
❌ NO geocoding (expensive, slow)
❌ NO AI calls (API keys exposed)
❌ NO property lookups (rate limits)
```

**Benefit:** Users see incidents instantly, app stays fast

---

### **Backend (Cloud Function) Role:**
```
✅ Re-parse originalBody (robust, updatable)
✅ Validate frontend parsing (fix errors)
✅ Geocode (Firestore cache = reuse existing!)
✅ Property data (APIs with caching)
✅ FD code dictionary lookup
✅ AI enrichment (Gemini/GPT)
✅ Write to Firestore (permanent storage)
❌ NO user-facing delays
```

**Benefit:** Enrichment happens async, no app updates needed for logic changes

---

## 📊 **Data Flow (Final Architecture)**

```
┌──────────────┐
│ BNN App      │ Sends notification
└──────┬───────┘
       ↓
┌──────────────┐
│ Android App  │ 1. Captures notification
│ (Frontend)   │ 2. Basic parsing (ID, state, city)
└──────┬───────┘ 3. Sends JSON with originalBody
       ↓ POST
┌──────────────────────────────────────────────────────┐
│ Apps Script                                          │
│ - Receives JSON                                      │
│ - Writes to "Staging" sheet                          │
│ - Triggers Cloud Function                            │
│ - Returns 200 OK (FAST)                              │
└──────┬───────────────────────────────────────────────┘
       ↓ Webhook
┌─────────────────────────────────────────────────────────┐
│ Cloud Function (Backend Enrichment)                     │
│                                                          │
│ 1. Re-parse originalBody (validate)                     │
│ 2. Geocode (check Firestore cache first!)               │
│ 3. Query Attom/Estated/BatchData                        │
│ 4. Translate FD codes (njn005 → "North Bergen FD")      │
│ 5. AI summary (Gemini: codes → human language)          │
│ 6. Write enriched data to Firestore                     │
│                                                          │
└──────┬──────────────────────────────────────────────────┘
       ↓ Writes to
┌──────────────────────────────────────────┐
│ Firestore (Source of Truth)             │
│ - emu_incidents (enriched)               │
│ - nfa_incidents (enriched)               │
│ - geocode_cache (reuse existing!)        │
└──────┬───────────────────────────────────┘
       ↓ Apps read
┌──────────────────────────────────────────┐
│ Consumption Layer                        │
│ - EMU Incidents App (100% human text)    │
│ - NFA Incidents App (100% human text)    │
│ - Google Sheets (analytics dashboard)    │
│ - Future: Dashboards, reports, alerts    │
└──────────────────────────────────────────┘
```

---

## 🎯 **Key Advantages of This Architecture**

### **1. Reuse Your Existing Firestore Geocodes** 💰
```javascript
// Backend checks YOUR existing geocode collection first
const cached = await db.collection('YOUR_GEOCODES')
  .where('address', '==', incident.address)
  .get();

if (!cached.empty) {
  return cached.docs[0].data(); // FREE! No API call!
}
```

**Savings:** Most geocoding API calls avoided → $50-100/month saved

---

### **2. Backend is Updatable Without App Updates** 🚀
```
Need to fix parsing logic? → Update Cloud Function (30 seconds)
Need to add new API? → Update Cloud Function (no app resubmit)
Need to change AI prompt? → Update Cloud Function (instant)
```

**No Android app updates needed!** Logic changes deploy instantly.

---

### **3. Centralized Caching = Cost Optimization** 💸
```javascript
// Cache AI responses by originalBody hash
const aiCache = await db.collection('ai_cache')
  .doc(hashCode(originalBody))
  .get();

if (aiCache.exists) {
  return aiCache.data(); // FREE! Similar incidents reuse AI output
}
```

**Savings:** AI calls only for unique incidents → $20-30/month saved

---

### **4. Multiple Apps Share Enriched Data** 🔄
```
Backend enriches ONCE → multiple apps consume
- EMU Incidents App reads Firestore
- NFA Incidents App reads Firestore
- Future web dashboard reads Firestore
- Future mobile apps read Firestore
```

**No duplicate enrichment work!** One source of truth.

---

## 💰 **Cost Breakdown (Monthly Estimates)**

| Service | Cost | Notes |
|---------|------|-------|
| **Firestore** | FREE | Well within free tier (50K reads, 20K writes/day) |
| **Cloud Functions** | FREE | 2M invocations/month free (you'll use ~1K) |
| **Geocoding API** | $10-20 | **Mostly FREE** (cache hits from your existing geocodes!) |
| **Property APIs** | $50-100 | Attom, Estated, BatchData (with caching) |
| **AI (Gemini)** | $5-10 | Gemini 1.5 Flash ($0.075 per 1M tokens) |
| **Total** | **$65-130** | Mostly property APIs; infrastructure nearly free |

**ROI:** 100% human-readable incidents worth it for EMU/NFA apps!

---

## 🚀 **Implementation Timeline**

### **Week 1 (This Week): Frontend Fixes**
- [ ] AG fixes 3 Parser.kt issues
- [ ] Build & test on device
- [ ] Verify Google Sheet updates append (like Row 22)
- [ ] Confirm clean FD codes (no DESK/BNNDESK)

**Milestone:** Solid foundation of clean data

---

### **Week 2: Backend Setup**
- [ ] Create Firebase Cloud Functions project
- [ ] Setup Firestore collections (reuse existing geocodes!)
- [ ] Deploy basic enrichment function (geocoding only)
- [ ] Test with real addresses

**Milestone:** Geocoding working with Firestore cache

---

### **Week 3: Property APIs + FD Codes**
- [ ] Integrate Attom/Estated/BatchData APIs
- [ ] Build FD code dictionary (Google Sheet or Firestore)
- [ ] Implement code translation logic
- [ ] Test with real incidents

**Milestone:** Property data + human-readable FD codes

---

### **Week 4: AI + App Integration**
- [ ] Setup Gemini API (Firebase extension)
- [ ] Implement AI enrichment with caching
- [ ] Update EMU/NFA apps to read Firestore
- [ ] Build human-readable UI

**Milestone:** 100% human-readable incidents in production!

---

## 📋 **Decision Matrix (Reference)**

| Question | Answer |
|----------|--------|
| **Fix frontend parsing now?** | ✅ YES (Phase 1) |
| **Add backend enrichment now?** | ⏳ Next week (Phase 2) |
| **Frontend does geocoding?** | ❌ NO (backend only) |
| **Frontend calls AI?** | ❌ NO (backend only) |
| **Backend re-parses everything?** | ✅ YES (source of truth) |
| **Reuse existing Firestore geocodes?** | ✅ YES (huge cost savings!) |
| **AI enrichment where?** | 🌐 Backend webhook (cached) |
| **EMU/NFA apps read from?** | 📱 Firestore (enriched data) |
| **Google Sheets role?** | 📊 Analytics dashboard (not source of truth) |

---

## 🎯 **Bottom Line**

**This Week:** Fix frontend parsing (AG's 3 changes)  
**Next 2-4 Weeks:** Build backend enrichment pipeline  
**Result:** 100% human-readable incidents with property data, geocoding, and AI summaries

**Why this order?**
1. Clean frontend data prevents garbage → everything downstream benefits
2. Backend can be built incrementally without app updates
3. Reusing your existing Firestore geocodes saves $50-100/month
4. Centralized enrichment = one source of truth for all apps

**This is industry best practice for mobile + backend architecture.** 🚀

---

## 📚 **Related Documents**

- `/docs/tasks/AG_FINAL_PARSING_FIXES.md` - Give this to AG for Phase 1
- `/docs/tasks/AG_QUICK_FIX_SUMMARY.md` - Quick reference for AG
- `/docs/architecture/ENRICHMENT_PIPELINE.md` - Full Phase 2 architecture
- `/docs/architecture/HANDOFF.md` - System overview

---

**Ready to proceed?** Give AG the parsing fix prompt, then we'll build the backend next week! 🎉

