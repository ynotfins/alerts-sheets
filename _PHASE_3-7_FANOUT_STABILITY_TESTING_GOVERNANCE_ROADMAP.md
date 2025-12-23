# PHASES 3-7 — FANOUT, STABILIZATION, TESTING, GOVERNANCE & ROADMAP

**Date:** 2025-12-23  
**Project:** AlertsToSheets Android App  
**Context:** Completing comprehensive system analysis

---

## 🔌 PHASE 3 — FANOUT & INTEGRATIONS

### PROMPT 3.1 — FANOUT RESPONSIBILITY SPLIT

#### DECISION: **HYBRID FANOUT (CLIENT INGESTS, SERVER DELIVERS)**

**Rationale:**

| Responsibility | Location | Why |
|----------------|----------|-----|
| **Event capture** | Client | Only client sees notifications/SMS |
| **Ingestion** | Client → Server | Client knows event exists |
| **Fanout config** | Server | Centralized, versioned, auditable |
| **Delivery execution** | Server | Survives client crashes, reliable retry |
| **Delivery tracking** | Server | Only server knows actual delivery status |

#### TRADE-OFF ANALYSIS

**Option 1: Client-side fanout (current state)**
```
Client owns:
- Capture
- Parse
- Transform
- Send to ALL endpoints
- Retry logic
- Status tracking

Pros:
+ Simple architecture (no server needed)
+ Low latency (direct to endpoints)
+ No server costs

Cons:
- Unreliable (client can crash mid-delivery)
- No retry after client crash
- Can't add endpoints without app update
- No centralized metrics
- Duplicate prevention is hard
- Client embeds endpoint URLs (security risk)

Verdict: ❌ UNACCEPTABLE for mission-critical alerts
```

**Option 2: Server-side fanout (proposed)**
```
Client owns:
- Capture
- Parse
- Transform
- Ingest to server (single HTTP POST)

Server owns:
- Fanout config
- Delivery to ALL endpoints
- Retry logic
- Status tracking
- Metrics

Pros:
+ Reliable (survives client crashes)
+ Retry guaranteed
+ Add endpoints without app update
+ Centralized metrics
+ Duplicate prevention via UUID
+ Secure (URLs not in client code)

Cons:
- Higher latency (client → server → endpoints)
- Server costs (Cloud Functions, Firestore)
- More complex architecture

Verdict: ✅ RECOMMENDED
```

**Option 3: Hybrid with client hint**
```
Client sends:
- Event data
- Preferred endpoints (hint only)

Server decides:
- Which endpoints actually receive event
- Can override client hint (admin rules)
- Can add additional endpoints

Pros:
+ All benefits of server-side fanout
+ Client can suggest endpoints (user control)
+ Server has final say (admin control)

Cons:
- Most complex architecture
- Client/server config mismatch risks

Verdict: ⚠️ OVERKILL for current needs, consider for v3
```

#### RECOMMENDED DEFAULT: **SERVER-SIDE FANOUT**

**Flow:**
```
1. Client captures event
2. Client transforms to JSON (using local template)
3. Client POSTs to /ingest with metadata:
   {
     "uuid": "abc-123",
     "sourceId": "bnn-app",
     "payload": "{...}",
     "timestamp": "2025-12-23T10:00:00Z",
     "deviceId": "android-12345"
   }
4. Server responds 200 ACK immediately
5. Server triggers Cloud Function
6. Cloud Function reads fanout config for sourceId
7. Cloud Function delivers to ALL configured endpoints
8. Cloud Function writes delivery receipts
9. Client polls for delivery status (optional)
```

**Fanout config (server-side):**
```javascript
// Firestore: fanoutRules/{sourceId}
{
  sourceId: "bnn-app",
  endpoints: [
    { id: "sheets-primary", priority: 1, fallback: null },
    { id: "firestore-ingest", priority: 1, fallback: null },
    { id: "webhook-backup", priority: 2, fallback: "sheets-primary" }
  ],
  filters: {
    // Optional: only fanout if condition met
    incidentType: ["fire", "medical"] // skip traffic alerts
  }
}
```

**Justification:**
- **Reliability:** Server never crashes mid-delivery (unlike client)
- **Debuggability:** All delivery logs in one place (Firestore)
- **Future integrations:** Add Slack, PagerDuty, etc. without app update

---

### PROMPT 3.2 — SHEETS AS INTEGRATION, NOT DEPENDENCY

#### CURRENT STATE: Sheets is primary datastore ❌

**Problems:**
1. If Sheets is down, data is lost (no fallback)
2. Schema changes in Sheets break app
3. Can't test without hitting production Sheets
4. Sheets rate limits block all alerts

#### PROPOSED STATE: Sheets is downstream consumer ✅

**Architecture:**
```
┌─────────────────────────────────────────────────────────┐
│            CANONICAL FLOW (Sheets as integration)        │
└─────────────────────────────────────────────────────────┘

1. Event captured → Firestore (canonical storage)
2. Cloud Function triggered
3. Deliver to ALL configured integrations IN PARALLEL:
   ├─ Google Sheets (append row)
   ├─ Firestore (structured data for analytics)
   ├─ PagerDuty (future)
   └─ Slack (future)
4. IF Sheets fails:
   ├─ Log failure (delivery receipt)
   ├─ Retry with backoff
   ├─ Alert admin after N retries
   ├─ Other integrations unaffected
   └─ Data is NOT lost (still in Firestore)
```

**Key principle:** **Ingestion and delivery are decoupled**

---

#### HOW FAILURES SHOULD BE HANDLED

**Scenario 1: Sheets returns 429 (rate limit)**
```
Server behavior:
1. Write delivery receipt (status: 429, error: "Rate limited")
2. Increment Sheets endpoint failure counter
3. Schedule retry with longer backoff (respect rate limit)
4. Continue delivering to other endpoints (Firestore, etc.)
5. After 3 retries, alert admin: "Sheets rate limit reached"

User impact: Zero (data in Firestore, other integrations work)
Admin action: Increase Sheets rate limit or batch requests
```

**Scenario 2: Sheets returns 400 (schema error)**
```
Server behavior:
1. Write delivery receipt (status: 400, error: "Bad request")
2. Mark as PERMANENT_FAILURE (don't retry)
3. Alert admin immediately: "Sheets schema mismatch"
4. Continue delivering to other endpoints

User impact: Zero (data in Firestore)
Admin action: Fix Sheets schema or update transformation logic
```

**Scenario 3: Sheets down for 4 hours (maintenance)**
```
Server behavior:
1. All Sheets deliveries fail (503, 502, timeout)
2. Retry with backoff (1min, 5min, 15min, ...)
3. After 1 hour, alert admin: "Sheets down"
4. After 4 hours, Sheets comes back online
5. Retry succeeds, delivery resumes
6. Backlog is processed (all queued events delivered)

User impact: Zero (data in Firestore, backlog processed)
Admin action: None (automatic recovery)
```

---

#### HOW SCHEMA CHANGES SHOULD BE ABSORBED

**Current problem:** App sends hardcoded columns, Sheets expects exact match

**Proposed solution:** Schema versioning + transformation layer

```javascript
// Server-side schema transformation
const SHEETS_SCHEMA_V2 = {
  columns: ['Timestamp', 'IncidentID', 'Address', 'Type', 'Priority'],
  transforms: {
    // Map event fields to Sheets columns
    'timestamp': (val) => new Date(val).toISOString(),
    'incidentId': (val) => val,
    'address': (val) => val,
    'incidentType': (val) => val.toUpperCase(), // Transform
    'priority': (val) => val || 'MEDIUM' // Default
  }
}

// When Sheets schema changes to V3:
const SHEETS_SCHEMA_V3 = {
  columns: ['Timestamp', 'ID', 'Location', 'Category', 'Urgency', 'Status'],
  transforms: {
    'timestamp': (val) => new Date(val).toISOString(),
    'incidentId': (val) => `INC-${val}`, // Prefix added
    'address': (val) => val, // Now called "Location"
    'incidentType': (val) => mapCategory(val), // New mapping
    'priority': (val) => mapUrgency(val), // Renamed "Urgency"
    'status': (val) => 'NEW' // New column, default value
  }
}

// Deployment:
1. Deploy SHEETS_SCHEMA_V3 to server
2. Update Sheets columns to match V3
3. No client app update needed
4. Old events still work (transformation handles it)
```

**Benefits:**
- Schema changes don't break client
- Can support multiple Sheets versions simultaneously
- Can gradually migrate Sheets (V2 and V3 coexist)

---

#### HOW TESTING SHOULD OCCUR

**Current problem:** Testing sends real data to production Sheets

**Proposed solution:** Test endpoints + staging environments

```javascript
// Endpoint config includes environment tag
{
  id: "sheets-production",
  url: "https://script.google.com/prod/...",
  environment: "production"
}

{
  id: "sheets-staging",
  url: "https://script.google.com/staging/...",
  environment: "staging"
}

// Client sends test events with flag
{
  uuid: "test-abc-123",
  sourceId: "bnn-app",
  payload: "{...}",
  isTest: true // Flag for testing
}

// Server routes test events to staging endpoints only
if (event.isTest) {
  endpoints = endpoints.filter(e => e.environment === "staging")
}

// UI: "Send Test" button explicitly marked as test
// Production events never have isTest flag
```

**Benefits:**
- Test without polluting production data
- Validate schema changes in staging
- Users can test configurations risk-free

---

## 🧼 PHASE 4 — STABILIZE BEFORE REFACTOR

### PROMPT 4.1 — REFACTOR READINESS GATE

#### OBJECTIVE CRITERIA FOR "SAFE TO REFACTOR"

**GATE 1: Data loss is impossible ✅**
```
Must have:
1. All events persisted to Firestore before ACK
2. Firestore Security Rules prevent unauthorized writes
3. Client queue survives app crashes (SQLite write-ahead log)
4. Server delivery survives Cloud Function crashes (Firestore queue)
5. Duplicate prevention works (UUID deduplication tested)

Test:
- Kill app mid-ingestion → event in queue on restart
- Kill server mid-delivery → event retries on next trigger
- Send same UUID twice → only one Firestore document
- Network outage → all events queued, delivered when online

Pass criteria: Zero events lost in 1000-event stress test
```

**GATE 2: All failures are visible ✅**
```
Must have:
1. Activity Log shows every event (PENDING, SENT, FAILED)
2. Admin dashboard shows endpoint health
3. Alerts fire for persistent failures
4. Failed events have manual retry button

Test:
- Disable network → see events in PENDING
- Break endpoint → see FAILED status + error message
- Leave endpoint broken for 1 hour → receive alert
- Click retry → event reprocessed

Pass criteria: 100% of failures visible in UI within 60s
```

**GATE 3: Rollback guarantees ✅**
```
Must have:
1. All config stored in Firestore (versioned)
2. Config changes have rollback button
3. Code deploys are gradual (canary rollout)
4. Metrics dashboard shows before/after comparison

Test:
- Change endpoint URL → click "Undo" → old URL restored
- Deploy new Cloud Function → see metrics drop → rollback deploy
- Change template → test with sample event → rollback if broken

Pass criteria: Any config/code change can be undone in <5min
```

**GATE 4: Invariants verified ✅**
```
Must hold (tested continuously):
1. Every ingested event has exactly one Firestore document
2. Every delivery attempt has exactly one receipt
3. LogEntry status matches server status (eventually consistent)
4. No orphaned queue entries (all events tracked)

Test:
- Ingest 1000 events → query Firestore → count == 1000
- Check receipts count == (events count × endpoints count)
- Check LogRepository count matches Firestore count (within delay)
- Check SQLite queue empty after all ACKs received

Pass criteria: All invariants hold after 1000-event test
```

---

### PROMPT 4.2 — REFACTOR PRIORITIZATION

#### PRINCIPLE: Minimize blast radius, prioritize by coupling

**ORDER:**

**ROUND 1: Decouple persistence (low risk)**
```
Goal: Move from JsonStorage → Firestore for Sources/Endpoints/Templates

Why first:
- High coupling (every screen reads/writes config)
- Low complexity (CRUD operations, well-defined)
- Easy rollback (keep JsonStorage as fallback)
- No impact on data capture (read-only at runtime)

Steps:
1. Create Firestore collections (sources, endpoints, templates)
2. Add SourceRepository.syncToFirestore() method
3. UI writes to both JsonStorage and Firestore (dual-write phase)
4. Verify Firestore writes succeed for 7 days
5. Switch UI to read from Firestore, fallback to JsonStorage
6. Verify reads succeed for 7 days
7. Remove JsonStorage writes (keep as cache only)
8. Monitor for 30 days, then remove JsonStorage entirely

Blast radius: Config UI only (not data capture path)
Rollback: Revert to JsonStorage reads in one line
```

**ROUND 2: Extract delivery to server (medium risk)**
```
Goal: Move delivery from DataPipeline → Cloud Function

Why second:
- Medium coupling (touches capture path)
- Medium complexity (retry logic, fanout, receipts)
- Moderate rollback (need to re-enable client delivery)
- DOES impact data capture (must not break)

Steps:
1. Deploy Cloud Function (no-op initially)
2. Add /ingest endpoint to Firestore
3. DataPipeline dual-writes (local delivery + ingest)
4. Verify ingestion for 7 days
5. Cloud Function starts reading ingested events
6. Cloud Function delivers in parallel with client
7. Compare client delivery vs server delivery (match rate)
8. If match rate > 99%, disable client delivery
9. Monitor for 30 days

Blast radius: All deliveries (must work perfectly)
Rollback: Re-enable client delivery flag
```

**ROUND 3: Centralize fanout config (low risk)**
```
Goal: Move endpoint selection from Source → fanoutRules

Why third:
- Low coupling (server-side only change)
- Low complexity (config mapping)
- Easy rollback (revert to Source.endpointIds)
- No client changes needed

Steps:
1. Create Firestore fanoutRules collection
2. Migrate Source.endpointIds → fanoutRules
3. Cloud Function reads from both (fallback)
4. Verify fanout works for 7 days
5. Remove Source.endpointIds (deprecated)
6. Monitor for 30 days

Blast radius: Server delivery only
Rollback: Read from Source.endpointIds fallback
```

**ROUND 4: Optimize large files (low risk, aesthetic)**
```
Goal: Split DataPipeline.kt, refactor UI activities

Why last:
- High coupling (many imports)
- Low complexity (move code, no logic change)
- Easy rollback (git revert)
- Zero functional impact (code organization only)

Steps:
1. Extract parsers from DataPipeline → separate files
2. Extract template engine from DataPipeline → separate file
3. Extract HTTP client from DataPipeline → separate file
4. Run full test suite, verify no regressions
5. Split large activities (LabActivity.kt → smaller pieces)
6. Verify UI works identically

Blast radius: Zero (code organization only)
Rollback: Git revert (no runtime changes)
```

---

## 🧪 PHASE 5 — TESTING STRATEGY

### PROMPT 5.1 — REALITY-BASED TESTING PLAN

#### PRINCIPLE: No mock data for critical paths

**CRITICAL PATH 1: Notification capture → delivery**
```
Test: Real BNN app notification → Firestore → Google Sheets

Steps:
1. Install app on real device
2. Install BNN app (real app, not simulator)
3. Configure Source for BNN
4. Trigger real BNN notification (use test incident if available)
5. Verify:
   - Event in Activity Log (within 5s)
   - Event in Firestore (within 10s)
   - Row in Google Sheets (within 30s)
   - Delivery receipt in Firestore
   - Endpoint stats updated

Pass criteria: All steps complete, data matches
```

**CRITICAL PATH 2: Network failure → retry → success**
```
Test: Capture event while offline → come online → delivery

Steps:
1. Enable airplane mode on device
2. Trigger BNN notification
3. Verify event in SQLite queue (PENDING_INGESTION)
4. Disable airplane mode
5. Verify:
   - Event POST to Firestore (within 60s)
   - Event in Activity Log (status updated to SENT)
   - Row in Google Sheets
   - SQLite queue empty

Pass criteria: Event delivered after network restored
```

**CRITICAL PATH 3: App crash → restart → recovery**
```
Test: Capture event → kill app → restart → delivery

Steps:
1. Trigger BNN notification
2. Immediately kill app (force stop)
3. Verify event in SQLite queue (query directly via adb)
4. Restart app
5. Verify:
   - Queue processor resumes
   - Event POST to Firestore
   - Event delivered

Pass criteria: No data loss, queue processed on restart
```

**CRITICAL PATH 4: Endpoint failure → retry → alert**
```
Test: Configure bad endpoint URL → trigger event → verify alert

Steps:
1. Add endpoint with invalid URL
2. Trigger notification
3. Verify:
   - Delivery fails (HTTP error or timeout)
   - Delivery receipt shows failure
   - Activity Log shows FAILED or PARTIAL
   - Retry scheduled (check Firestore deliveryRetries)
   - After N retries, admin alerted

Pass criteria: Failure visible, retries work, alert fires
```

---

### PROMPT 5.2 — OBSERVABILITY OVER ASSERTIONS

#### KEY OBSERVABILITY SIGNALS

**SIGNAL 1: Queue depth over time**
```
What: Number of events in client SQLite queue
Why useful: Spikes indicate network issues or server downtime
Alert: queueDepth > 1000 for >10min → network problem

Better than assertion because:
- Assertions test "queue should be empty" (flaky in production)
- Observability shows "queue growing" (actionable insight)
```

**SIGNAL 2: Delivery latency (P50, P95, P99)**
```
What: Time from event capture to delivery receipt
Why useful: Degradation indicates endpoint slowness
Alert: P95 latency > 30s → endpoint performance issue

Better than assertion because:
- Assertions test "delivery within 5s" (brittle)
- Observability shows "latency increasing" (early warning)
```

**SIGNAL 3: Endpoint success rate (rolling 1-hour window)**
```
What: Percentage of 2xx responses per endpoint
Why useful: Detects endpoint degradation before total failure
Alert: successRate < 90% → endpoint degraded

Better than assertion because:
- Assertions test "this request succeeded" (single point)
- Observability shows "success rate dropping" (trend)
```

**SIGNAL 4: Retry exhaustion rate**
```
What: Number of events that hit max retries
Why useful: Indicates persistent failure (not transient)
Alert: exhaustionRate > 0 → critical data loss

Better than assertion because:
- Assertions test "retry succeeded" (optimistic)
- Observability shows "retries exhausted" (worst case)
```

**SIGNAL 5: Ingestion-to-delivery gap**
```
What: Time between Firestore ingestedAt and first delivery attempt
Why useful: Detects Cloud Function trigger delays
Alert: gap > 60s → serverless cold start issue

Better than assertion because:
- Assertions test "function triggered immediately" (assumption)
- Observability shows "trigger delay" (actual behavior)
```

---

## 🧠 PHASE 6 — GOVERNANCE & FUTURE-PROOFING

### PROMPT 6.1 — INVARIANTS & CONTRACTS

#### CORE INVARIANTS (NEVER VIOLATE)

**INVARIANT 1: Event uniqueness**
```
Contract: Every event has exactly one UUID, one Firestore document

Enforcement:
- Client generates UUID at capture time (never changes)
- Firestore document ID is UUID (primary key)
- Duplicate POSTs are idempotent (no-op if UUID exists)

Test: POST same event twice → only one document
Violation: Two documents with same UUID → DATA CORRUPTION
```

**INVARIANT 2: Delivery receipt completeness**
```
Contract: Every delivery attempt has exactly one receipt

Enforcement:
- Cloud Function writes receipt after every POST (success or failure)
- Receipts are immutable (never updated, only created)
- Receipt count = endpoints count × delivery attempts

Test: Deliver to 3 endpoints → 3 receipts
Violation: Missing receipt → AUDIT TRAIL INCOMPLETE
```

**INVARIANT 3: Status consistency**
```
Contract: LogEntry status matches Firestore status (eventually)

Enforcement:
- Client LogRepository syncs from Firestore every 60s
- Server writes status to Firestore after every state change
- Conflicts: Firestore wins (source of truth)

Test: Change status in Firestore → client updates within 60s
Violation: Client shows SENT, server shows FAILED → USER CONFUSED
```

**INVARIANT 4: No orphaned queue entries**
```
Contract: Every SQLite queue entry corresponds to a pending event

Enforcement:
- Queue entry deleted after Firestore ACK
- Queue pruned on app start (delete entries >7 days)
- Firestore query checks for missing entries

Test: Ingest 100 events → queue empty after 100 ACKs
Violation: Queue has 101 entries → MEMORY LEAK
```

---

### PROMPT 6.2 — CHANGE SAFETY MODEL

#### CHANGE CATEGORIES

**SAFE: Can change freely (low risk)**
```
What:
- UI layout, colors, fonts
- Log messages, debug strings
- Non-critical feature flags
- Client-side caching logic
- Observability dashboards

Why safe:
- No impact on data capture/delivery
- Easy rollback (UI only)
- User-facing only (no server changes)

Process: Standard PR review, deploy to production
```

**CAREFUL: Requires extra review (medium risk)**
```
What:
- Parser logic (BnnParser, SmsParser)
- Template transformation logic
- Endpoint configuration schema
- Retry backoff timings
- Queue size limits

Why careful:
- Impacts data correctness
- Can cause silent failures
- Harder to test (need production traffic)

Process:
1. PR review by 2+ engineers
2. Deploy to staging, test with real traffic
3. Canary deploy (10% of users)
4. Monitor for 48 hours
5. Full deploy if metrics stable
```

**CRITICAL: Never touch casually (high risk)**
```
What:
- UUID generation logic
- Firestore Security Rules
- Deduplication logic
- Cloud Function triggers
- Ingestion endpoint contract

Why critical:
- Data loss if broken
- Security breach if misconfigured
- Can't rollback easily (data already written)

Process:
1. Design doc required
2. PR review by tech lead + architect
3. Deploy to staging, soak test for 1 week
4. Canary deploy (1% of users)
5. Monitor for 1 week
6. Gradual rollout (1% → 10% → 50% → 100%)
7. Bake time: 1 month before next critical change
```

---

## 🎯 PHASE 7 — EXECUTION ROADMAP

### FINAL ROADMAP: Durability → Observability → Performance → Refactor

---

### **MILESTONE 1: ACHIEVE ZERO DATA LOSS** (4 weeks)

**Goal:** Events are never lost due to network/crash/server failure

**Deliverables:**
1. ✅ Firestore ingestion endpoint (`/ingest`)
2. ✅ Client SQLite queue with crash recovery
3. ✅ UUID-based deduplication
4. ✅ Cloud Function for delivery (parallel to client)
5. ✅ Delivery receipts in Firestore
6. ✅ Stress test: 1000 events, zero loss

**Success criteria:**
- 1000-event stress test: 100% ingestion rate
- Kill app mid-ingestion: 0 events lost
- Network outage test: All events delivered after reconnect
- Duplicate test: POST same UUID twice → 1 document

**Stop point:** Pass all stress tests, deploy to staging

---

### **MILESTONE 2: ACHIEVE FULL OBSERVABILITY** (2 weeks)

**Goal:** All failures visible within 60 seconds

**Deliverables:**
1. ✅ Activity Log real-time sync (Firestore → client)
2. ✅ Endpoint health dashboard (success rate, response time)
3. ✅ Admin alerts (email/SMS for failures)
4. ✅ Manual retry button (failed events)
5. ✅ Queue depth metric (client-side)

**Success criteria:**
- Break endpoint → see FAILED in Activity Log <60s
- Endpoint down for 1hr → receive admin alert
- Click retry button → event reprocessed
- Queue depth widget shows real-time count

**Stop point:** All failures visible, deploy to production (10% rollout)

---

### **MILESTONE 3: OPTIMIZE DELIVERY PERFORMANCE** (2 weeks)

**Goal:** P95 latency <30s, handle burst traffic

**Deliverables:**
1. ✅ Batching (10 events per POST)
2. ✅ Rate limiting (per-endpoint)
3. ✅ Parallel delivery (multiple endpoints simultaneously)
4. ✅ Cold start optimization (Cloud Function)
5. ✅ Burst test: 50 events in 5min, all delivered <2min

**Success criteria:**
- P95 latency <30s (measured over 7 days)
- Burst test: 50 events delivered in <2min
- No rate limit errors from Sheets
- Cold start <500ms

**Stop point:** Performance SLA met, full production rollout (100%)

---

### **MILESTONE 4: STABILIZE FOR REFACTOR** (2 weeks)

**Goal:** Meet refactor readiness gate, achieve 30-day stability

**Deliverables:**
1. ✅ Zero data loss for 30 days straight
2. ✅ All invariants verified (automated tests)
3. ✅ Rollback plan documented
4. ✅ Monitoring dashboards deployed
5. ✅ On-call runbook written

**Success criteria:**
- 30 days zero incidents
- 100% of failures visible
- Rollback tested (staging)
- On-call responds to test alert <5min

**Stop point:** System stable, ready for refactor

---

### **MILESTONE 5: INCREMENTAL REFACTOR** (6 weeks)

**Goal:** Modernize codebase without breaking production

**Deliverables:**
1. ✅ Round 1: Decouple persistence (JsonStorage → Firestore)
2. ✅ Round 2: Extract delivery to server (client → Cloud Function)
3. ✅ Round 3: Centralize fanout config
4. ✅ Round 4: Optimize large files (code organization)

**Success criteria:**
- After each round: All tests pass, metrics unchanged
- Zero regressions (delivery rate, latency, failure rate)
- Code coverage >80% for refactored code

**Stop point:** Refactor complete, bake for 30 days

---

### **TIMELINE SUMMARY**

| Milestone | Duration | Cumulative | Key Outcome |
|-----------|----------|------------|-------------|
| M1: Zero Data Loss | 4 weeks | 4 weeks | Durability guaranteed |
| M2: Full Observability | 2 weeks | 6 weeks | All failures visible |
| M3: Performance | 2 weeks | 8 weeks | Burst traffic handled |
| M4: Stabilization | 2 weeks | 10 weeks | 30-day stability |
| M5: Refactor | 6 weeks | 16 weeks | Modern codebase |

**Total: 16 weeks (4 months) to production-ready, refactored system**

---

### **IMMEDIATE NEXT STEPS (WEEK 1)**

**Day 1-2: Firestore setup**
- Create Firestore collections (events, sources, endpoints)
- Deploy Security Rules
- Create /ingest Cloud Function (no-op)

**Day 3-4: Client queue**
- Implement SQLite queue with write-ahead log
- Add retry logic (exponential backoff)
- Test crash recovery

**Day 5: Integration**
- Connect client queue to /ingest endpoint
- Verify ingestion works
- Deploy to staging

**Week 1 deliverable:** Events ingest to Firestore (no delivery yet)

---

## 📋 FINAL SUMMARY

**PHASE 0:** Identified critical failure modes (silent data loss, missed alerts)  
**PHASE 1:** Defined canonical storage (Firestore as source of truth)  
**PHASE 2:** Designed failure-aware delivery (retry, backoff, observability)  
**PHASE 3:** Chose hybrid fanout (client ingests, server delivers)  
**PHASE 4:** Set refactor readiness gates (zero data loss, full observability)  
**PHASE 5:** Created reality-based testing plan (no mocks for critical paths)  
**PHASE 6:** Defined invariants (event uniqueness, receipt completeness)  
**PHASE 7:** Built execution roadmap (16 weeks, 5 milestones)

**NEXT ACTION:** Begin Milestone 1 (Firestore setup, client queue, ingestion endpoint)

---

**STATUS:** ✅ **COMPREHENSIVE ANALYSIS COMPLETE**  
**READY FOR:** Phase 0 execution (Firestore setup)

