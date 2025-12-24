# PHASE 3: CRM FOUNDATION DEPLOYMENT GUIDE
**Generated:** December 23, 2025  
**Status:** Ready for deployment  
**Schema Version:** 1.0 (locked, immutable)

---

## 🎯 **OBJECTIVE**

Deploy the Firestore CRM foundation with:
1. ✅ **Production-grade Firestore database** (8 collections, security rules, indexes)
2. ✅ **Alert enrichment pipeline** (address normalization, property linkage)
3. ✅ **Feature flags / kill switches** (zero-downtime control)
4. ✅ **Non-blocking architecture** (existing Apps Script delivery NEVER affected)
5. ✅ **CRM-ready schema** (address-centric, idempotent, audit trail)

---

## 📋 **PRE-DEPLOYMENT CHECKLIST**

### ✅ Phase 3 Artifacts Complete

- [x] `firestore.rules` - Production-grade security rules
- [x] `firestore.indexes.json` - 20 composite indexes for CRM queries
- [x] `enrichment.ts` - Alert + property enrichment functions
- [x] `addressUtils.ts` - Address normalization + property ID generation
- [x] `featureFlags.ts` - Kill switch system
- [x] `index.ts` - Updated ingest endpoint + enrichment exports

### ✅ Firebase Project Configured

- [x] Project ID: `alerts-sheets-bb09c`
- [x] Firebase Auth enabled
- [x] Firestore database created
- [x] Cloud Functions enabled
- [x] Node.js 20 runtime configured

### ✅ Existing System Safety

- [x] Apps Script delivery path NOT modified
- [x] Android client NOT modified (dual-write comes in Phase 4)
- [x] IngestQueue.kt NOT modified
- [x] DataPipeline.kt NOT modified

---

## 🚀 **DEPLOYMENT STEPS**

### STEP 1: Install Dependencies

```bash
cd functions
npm install
```

### STEP 2: Build TypeScript

```bash
npm run build
```

**Expected output:**
```
> build
> tsc

✔ TypeScript compilation successful
```

### STEP 3: Deploy Firestore Security Rules

```bash
cd .. # Back to repo root
firebase deploy --only firestore:rules
```

**Expected output:**
```
✔ Deploy complete!
Rules deployed: firestore.rules
```

### STEP 4: Deploy Firestore Indexes

```bash
firebase deploy --only firestore:indexes
```

**Expected output:**
```
✔ Deploy complete!
Indexes deployed: 20 composite indexes
Building indexes... (may take 5-10 minutes for first-time deployment)
```

### STEP 5: Deploy Cloud Functions

```bash
firebase deploy --only functions
```

**Expected output:**
```
✔ functions(ingest): https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest
✔ functions(enrichAlert): Deployed
✔ functions(enrichProperty): Deployed
✔ functions(health): https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/health
```

**⏱️ Estimated time:** 3-5 minutes

### STEP 6: Initialize Feature Flags

Run this once to create `/config/featureFlags` in Firestore:

```bash
# From Firebase Console or via curl:
curl -X POST https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/initConfig \
  -H "Authorization: Bearer $(firebase auth:print-identity-token)"
```

**Alternative:** Manually create `/config/featureFlags` in Firestore Console:

```json
{
  "firestoreIngest": true,
  "alertEnrichment": true,
  "propertyEnrichment": false,
  "geocoding": false,
  "appsScriptDelivery": true,
  "firestoreFanout": false,
  "maxAlertsPerMinute": 100,
  "maxEnrichmentsPerHour": 500,
  "maintenanceMode": false
}
```

---

## ✅ **POST-DEPLOYMENT VERIFICATION**

### TEST 1: Health Check

```bash
curl https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/health
```

**Expected response:**
```json
{
  "status": "ok",
  "timestamp": "2025-12-23T...",
  "service": "AlertsToSheets Cloud Functions",
  "version": "1.0.0-milestone1"
}
```

### TEST 2: Firestore Rules Active

1. Open Firebase Console → Firestore
2. Try to create a document in `/alerts` (should fail - requires auth)
3. Verify rules are deployed: Settings → Rules → `firestore.rules`

### TEST 3: Indexes Building

1. Firebase Console → Firestore → Indexes
2. Verify 20 composite indexes listed
3. Status should be "Building" or "Enabled"

### TEST 4: Feature Flags Readable

1. Firebase Console → Firestore → `/config/featureFlags`
2. Verify document exists with all flags
3. Change `alertEnrichment` to `false`, save, change back to `true`

### TEST 5: Enrichment Functions Listed

```bash
firebase functions:list
```

**Expected output:**
```
enrichAlert (alerts/{alertId}.onCreate)
enrichProperty (properties/{propertyId}.onCreate)
ingest (https)
health (https)
```

---

## 🧪 **INTEGRATION TEST (MANUAL)**

### Test Alert → Property Creation

1. **Send test alert via IngestTestActivity** (from Android debug build)
   - Ensure alert payload contains `"address": "123 Main St, Austin, TX 78701"`
   
2. **Verify alert written to Firestore:**
   - Firebase Console → Firestore → `/alerts`
   - Find alert by `alertId` (UUID from test harness)
   - Verify fields: `alertId`, `sourceId`, `rawAddress`, `rawPayload`, `ingestedAt`
   
3. **Verify enrichAlert function triggered:**
   - Firebase Console → Functions → Logs
   - Search for: `[enrichAlert] Processing alert`
   - Verify normalization logs: `Normalized address: ...`
   - Verify property ID generated: `Generated property ID: ...`
   
4. **Verify property created:**
   - Firebase Console → Firestore → `/properties`
   - Find property by `propertyId` (hash from logs)
   - Verify fields: `propertyId`, `normalizedAddress`, `city`, `state`, `zipCode`, `firstAlertAt`, `lastAlertAt`, `totalAlerts` (should be 1)
   
5. **Verify alert linked to property:**
   - Go back to `/alerts/{alertId}`
   - Verify `propertyId` field now populated
   - Verify `normalizedAddress` field populated
   - Verify `processedAt` timestamp exists

### Test Duplicate Alert (Idempotency)

1. **Send same alert UUID twice**
2. **Verify:**
   - Second request returns `isDuplicate: true`
   - Only ONE alert document exists in `/alerts`
   - Property `totalAlerts` still equals 1

### Test Alert Without Address

1. **Send alert with `rawAddress: ""`**
2. **Verify:**
   - Alert is written to `/alerts`
   - `enrichAlert` function logs: `No address found in payload`
   - Alert has `processingNote: "No address found in payload"`
   - No property created
   - No errors thrown

---

## 🔧 **FEATURE FLAG CONTROL (PRODUCTION)**

### Disable Alert Enrichment (Emergency)

```bash
# Via Firestore Console:
1. Navigate to /config/featureFlags
2. Set alertEnrichment: false
3. Save

# Via gcloud CLI:
firebase firestore:set config/featureFlags --data '{"alertEnrichment": false}'
```

**Effect:** New alerts are still ingested, but enrichment is skipped (no property creation)

### Enable Maintenance Mode (Emergency)

```bash
firebase firestore:set config/featureFlags --data '{"maintenanceMode": true}'
```

**Effect:** `/ingest` returns 503 for all requests (existing alerts unaffected)

### Disable Firestore Ingest (Rollback to Apps Script only)

```bash
firebase firestore:set config/featureFlags --data '{"firestoreIngest": false}'
```

**Effect:** `/ingest` returns 503, client falls back to Apps Script delivery only

---

## 📊 **MONITORING**

### Cloud Function Logs

```bash
firebase functions:log --only enrichAlert
firebase functions:log --only ingest
```

### Firestore Queries (Console)

**Recent alerts:**
```
Collection: alerts
Order by: ingestedAt (descending)
Limit: 20
```

**Recent properties:**
```
Collection: properties
Order by: createdAt (descending)
Limit: 20
```

**Alerts needing enrichment:**
```
Collection: alerts
Where: propertyId == null AND rawAddress != ""
```

---

## 🔄 **ROLLBACK PROCEDURE**

### Immediate Kill Switch (No Redeployment)

1. Set `firestoreIngest: false` in `/config/featureFlags`
2. Verify `/ingest` returns 503
3. Existing Apps Script delivery continues unaffected

### Full Rollback (Redeploy Previous Version)

```bash
# Checkout previous commit
git checkout <previous-commit-hash>

# Redeploy functions
cd functions
npm install
npm run build
cd ..
firebase deploy --only functions

# Redeploy rules
firebase deploy --only firestore:rules
```

---

## 🎯 **SUCCESS CRITERIA**

- [ ] All 4 Cloud Functions deployed successfully
- [ ] Firestore rules active and enforced
- [ ] 20 composite indexes building/enabled
- [ ] Feature flags document exists and readable
- [ ] Test alert creates property with correct `propertyId`
- [ ] Duplicate alert returns `isDuplicate: true`
- [ ] Alert without address handled gracefully
- [ ] Apps Script delivery path NEVER called by enrichment functions
- [ ] Zero errors in Cloud Function logs during test
- [ ] Feature flag toggle (enable/disable) works without redeploy

---

## 📝 **NEXT STEPS (PHASE 4)**

After successful Phase 3 deployment:

1. **Integrate into DataPipeline** (dual-write)
   - Modify Android `DataPipeline.kt` to enqueue to `IngestQueue`
   - Ensure Apps Script delivery NEVER blocked by Firestore failures
   
2. **End-to-End Testing**
   - Happy path: Notification → Sheets + Firestore
   - Network failure: Firestore fails, Sheets succeeds
   - Crash recovery: App restart, queue resumes
   
3. **Provider Integration**
   - Geocoding API (Google Maps or Mapbox)
   - ATTOM API for owner lookup
   - Contact discovery API (phones/emails)

---

## 🚨 **CRITICAL REMINDERS**

1. **Schema is LOCKED** - Do NOT modify collection names or core document shapes
2. **Apps Script path is UNTOUCHED** - Zero code changes to existing delivery
3. **Feature flags are MANDATORY** - Never deploy without kill switches
4. **Idempotency is CRITICAL** - Same UUID = same result, always
5. **Failures are NON-BLOCKING** - Enrichment errors never throw to client

---

**End of Deployment Guide**

