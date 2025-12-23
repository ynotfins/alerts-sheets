# PHASE 1 — ANCHOR THE SYSTEM (PERMANENT STORAGE)

**Date:** 2025-12-23  
**Project:** AlertsToSheets Android App  
**Context:** Building on Phase 0 ground truth, defining canonical data strategy

---

## PROMPT 1.1 — CANONICAL DATA INGESTION & STORAGE STRATEGY

### CORE PRINCIPLE: ANDROID CLIENTS ARE UNTRUSTED WRITERS

**Reality:** Android can kill apps at any time. Battery management, memory pressure, user actions, crashes - all can interrupt writes. Therefore:

- **Android client = data capture device ONLY**
- **Server = canonical system of record**
- **Client local storage = ephemeral cache for offline resilience**

---

### PROPOSED CANONICAL ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────┐
│                    CANONICAL FLOW                            │
└─────────────────────────────────────────────────────────────┘

1. EVENT CAPTURE (Android)
   ├─ Notification arrives
   ├─ Create immutable capture record
   ├─ Assign UUID (client-generated, idempotent key)
   ├─ Persist locally (write-ahead log)
   └─ Status: CAPTURED

2. INGESTION (Server - Firestore)
   ├─ Android POSTs to /ingest endpoint
   ├─ Server validates, deduplicates (by UUID)
   ├─ Write to Firestore (canonical storage)
   ├─ Server ACKs with 200 + UUID
   └─ Status: INGESTED

3. DELIVERY (Server - Cloud Function)
   ├─ Firestore trigger fires on new document
   ├─ Server reads fanout config (Sheets, webhooks, etc.)
   ├─ Server performs deliveries (with retries)
   ├─ Server writes delivery receipts to Firestore
   └─ Status: DELIVERED

4. CLIENT CLEANUP (Android)
   ├─ On receiving 200 ACK from /ingest
   ├─ Mark local record as ACKNOWLEDGED
   ├─ Garbage collect after 7 days
   └─ Status: ARCHIVED
```

---

### RESPONSIBILITIES & BOUNDARIES

#### ANDROID CLIENT RESPONSIBILITIES ✅
1. **Capture notifications/SMS immediately**  
   - Extract all available data
   - Assign client-generated UUID (for deduplication)
   - Timestamp with local device time
   - Persist locally BEFORE any network call

2. **Parse and transform locally**  
   - Apply parser (BnnParser, SmsParser, etc.)
   - Apply template transformation
   - Generate final JSON payload
   - Validate JSON syntax

3. **Ingest to server with durability**  
   - POST to /ingest endpoint (Firestore or Cloud Run)
   - Include UUID, payload, metadata
   - Retry on network failure (with backoff)
   - Maintain local queue until ACK received

4. **Report status to user**  
   - Show Activity Log (CAPTURED, SENT, ACKNOWLEDGED, FAILED)
   - Surface errors (network down, server error, validation failure)
   - Provide manual retry UI for failed ingestions

5. **Garbage collection**  
   - Delete acknowledged records after N days
   - Preserve failed records for manual intervention
   - Export logs for debugging

#### ANDROID CLIENT PROHIBITED ❌
1. **Cannot be trusted for canonical storage**  
   - User can uninstall app (data lost)
   - Storage can fill up (old records deleted)
   - Bugs can corrupt local files

2. **Cannot perform final delivery**  
   - Client doesn't know if delivery succeeded server-side
   - Client can't track delivery receipts from Sheets/webhooks
   - Client can't retry partial failures (fan-out to multiple endpoints)

3. **Cannot manage fanout configuration**  
   - Fanout rules may change server-side without client update
   - Client shouldn't embed endpoint URLs (security risk)
   - Client can't track endpoint health/metrics

---

#### SERVER RESPONSIBILITIES ✅
1. **Accept ingestion (write-once, immutable)**  
   - Validate UUID format (prevent injection attacks)
   - Validate payload schema (catch client bugs)
   - Deduplicate by UUID (idempotent writes)
   - Return 200 ACK immediately (don't wait for delivery)

2. **Canonical storage (Firestore)**  
   - Store ingested events immutably
   - Index by: UUID, timestamp, source ID, status
   - Queryable for dashboards, analytics, audits
   - Retention policy: 90 days (configurable)

3. **Fan-out delivery (Cloud Function)**  
   - Read fanout config from Firestore
   - Deliver to ALL configured endpoints
   - Track delivery status PER endpoint
   - Retry failures with exponential backoff
   - Write delivery receipts to Firestore

4. **Observability & alerting**  
   - Track ingestion rate (events/minute)
   - Track delivery success rate (per endpoint)
   - Alert on: high failure rate, queue backup, endpoint downtime
   - Provide admin dashboard for health monitoring

5. **Auditability**  
   - Immutable event log (Firestore documents)
   - Delivery receipts (timestamps, HTTP codes, errors)
   - Client metadata (device ID, app version, Android version)
   - Support forensic queries ("show all events from device X on date Y")

#### SERVER PROHIBITED ❌
1. **Cannot modify ingested events**  
   - Once written, events are immutable
   - Corrections/updates require new events (with parent UUID reference)

2. **Cannot delete events without audit trail**  
   - Deletions must be logged (who, when, why)
   - Soft delete preferred (mark as deleted, don't remove)

3. **Cannot expose raw endpoint URLs to clients**  
   - Security risk (URLs in client code can be extracted)
   - Use opaque endpoint IDs, server resolves to URL

---

### DURABILITY GUARANTEES

#### NETWORK FAILURE ✅
```
Scenario: Client captures event, network is down

Client behavior:
1. Write event to local SQLite queue (CAPTURED status)
2. Attempt POST to /ingest
3. If network error: keep in queue, retry with backoff
4. If timeout: keep in queue, retry with backoff
5. If server error (500): keep in queue, retry with backoff
6. If client error (400): mark as FAILED, alert user (bad config)
7. Retry until success or user intervenes

Guarantee: Event is NOT lost due to network failure
```

#### APP CRASH ✅
```
Scenario: Client captures event, app crashes before POST

Client behavior:
1. Event is written to SQLite BEFORE any network call
2. On app restart, queue processor resumes
3. Queued events are retried
4. No data loss

Guarantee: Event is NOT lost due to app crash
```

#### SERVER UNAVAILABILITY ✅
```
Scenario: Server is down for 24 hours

Client behavior:
1. Events queue up locally (SQLite)
2. Client continues capturing new events
3. When server returns, queue drains
4. All events eventually ingested

Server behavior:
1. Server processes ingestion backlog
2. Server performs fan-out delivery for all events
3. No events lost

Guarantee: Event is NOT lost due to server downtime
```

#### DUPLICATE INGESTION ✅
```
Scenario: Client POSTs event, ACK is lost, client retries

Client behavior:
1. POST event with UUID
2. ACK not received (network error)
3. Client retries POST with SAME UUID

Server behavior:
1. Receive POST with UUID
2. Check Firestore for existing document with UUID
3. If exists: return 200 (idempotent, no-op)
4. If not exists: create document, return 200

Guarantee: Event is NOT duplicated in canonical storage
```

---

### STORAGE QUERYABILITY

#### REQUIRED QUERIES (for future fan-out)

1. **By time range**  
   `WHERE timestamp BETWEEN start AND end`  
   Use case: Generate daily/weekly reports

2. **By source**  
   `WHERE sourceId = 'bnn-app'`  
   Use case: Dashboards per notification source

3. **By status**  
   `WHERE deliveryStatus = 'FAILED'`  
   Use case: Admin intervention dashboard

4. **By endpoint**  
   `WHERE deliveryReceipts.endpointId = 'sheets-primary'`  
   Use case: Endpoint health monitoring

5. **By device**  
   `WHERE deviceId = 'android-12345'`  
   Use case: Debugging client-specific issues

#### FIRESTORE SCHEMA (PROPOSED)

```javascript
// Collection: events
{
  uuid: "550e8400-e29b-41d4-a716-446655440000", // PK
  timestamp: "2025-12-23T10:30:45.123Z",
  sourceId: "bnn-app",
  sourceType: "APP_NOTIFICATION",
  deviceId: "android-12345",
  appVersion: "2.0.1",
  
  raw: {
    packageName: "com.bnn.app",
    title: "New alert",
    text: "Alert content...",
    extras: {...}
  },
  
  parsed: {
    incidentId: "INC-12345",
    address: "123 Main St",
    ...
  },
  
  payload: "{...}", // Final JSON sent to endpoints
  
  ingestionStatus: "INGESTED", // PENDING, INGESTED
  ingestedAt: "2025-12-23T10:30:46.456Z",
  
  deliveryStatus: "DELIVERED", // PENDING, PARTIAL, DELIVERED, FAILED
  deliveryReceipts: [
    {
      endpointId: "sheets-primary",
      url: "https://...",
      sentAt: "2025-12-23T10:30:47.000Z",
      httpCode: 200,
      responseTime: 234, // ms
      error: null
    },
    {
      endpointId: "firestore-ingest",
      url: "https://...",
      sentAt: "2025-12-23T10:30:47.500Z",
      httpCode: 500,
      responseTime: 1000,
      error: "Internal server error"
    }
  ]
}
```

---

### ROBUSTNESS > ELEGANCE

**Why this approach favors robustness:**

1. **Write-ahead logging (client-side SQLite)**  
   - Simple, battle-tested pattern
   - Survives crashes, network failures
   - No complex transaction management needed

2. **Idempotent ingestion (server-side UUID dedup)**  
   - Clients can safely retry without creating duplicates
   - Eliminates need for distributed locking
   - Reduces client-side complexity

3. **Immutable events (Firestore append-only)**  
   - No update/delete bugs
   - Full audit trail by default
   - Simplifies concurrent access (no locking)

4. **Separate ingestion from delivery**  
   - Ingestion is fast (200 ACK immediately)
   - Delivery is slow (multiple endpoints, retries)
   - Client doesn't wait for delivery to complete

5. **Server owns fanout config**  
   - Can add/remove endpoints without client update
   - Can change delivery logic without client change
   - Centralized endpoint health monitoring

**Trade-offs:**
- More complex (client + server coordination)
- More expensive (Firestore costs, Cloud Function costs)
- More latency (ingestion + delivery are separate)

**BUT:**
- Zero data loss (network/crash/server failures)
- Full auditability (every event logged)
- Scalable (Firestore handles high throughput)
- Observable (centralized metrics)

---

## PROMPT 1.2 — FIRESTORE ROLE DEFINITION

### WHAT IS CANONICAL ✅

#### 1. Ingested Events (write-once)
**Collection:** `events`  
**Document ID:** UUID (client-generated)  
**Mutability:** Immutable after creation  
**Retention:** 90 days (configurable)

**Purpose:** System of record for all captured notifications/SMS

**Write pattern:**
```javascript
// Client writes
await firestore.collection('events').doc(uuid).set({
  ...eventData,
  ingestedAt: FieldValue.serverTimestamp()
});

// If document exists, set() is a no-op (idempotent)
```

#### 2. Delivery Receipts (append-only)
**Subcollection:** `events/{uuid}/deliveries`  
**Document ID:** Auto-generated  
**Mutability:** Immutable after creation  
**Retention:** Same as parent event

**Purpose:** Audit trail of all delivery attempts

**Write pattern:**
```javascript
// Server writes
await firestore.collection('events').doc(uuid)
  .collection('deliveries').add({
    endpointId: 'sheets-primary',
    sentAt: FieldValue.serverTimestamp(),
    httpCode: 200,
    responseTime: 234
  });
```

#### 3. Source Configuration (mutable)
**Collection:** `sources`  
**Document ID:** sourceId  
**Mutability:** User can update anytime  
**Retention:** Permanent (deleted only when user deletes)

**Purpose:** Single source of truth for Source config (replaces JsonStorage)

**Write pattern:**
```javascript
// Client writes (authenticated)
await firestore.collection('sources').doc(sourceId).set({
  name: 'BNN Notifications',
  sourceType: 'APP_NOTIFICATION',
  packageName: 'com.bnn.app',
  templateJson: {...},
  endpointIds: ['sheets-primary', 'firestore-ingest'],
  autoClean: true,
  enabled: true,
  updatedAt: FieldValue.serverTimestamp()
});
```

#### 4. Endpoint Configuration (mutable)
**Collection:** `endpoints`  
**Document ID:** endpointId  
**Mutability:** User can update anytime  
**Retention:** Permanent

**Purpose:** Single source of truth for Endpoint config (replaces JsonStorage)

**Write pattern:**
```javascript
// Client writes (authenticated)
await firestore.collection('endpoints').doc(endpointId).set({
  name: 'Primary Sheets',
  url: 'https://script.google.com/...',
  headers: {},
  timeout: 15000,
  enabled: true,
  stats: {
    totalRequests: 1234,
    successCount: 1200,
    failureCount: 34,
    avgResponseTime: 234
  },
  updatedAt: FieldValue.serverTimestamp()
});
```

---

### WHAT IS EPHEMERAL ⏳

#### 1. Client-side Activity Logs
**Storage:** SharedPreferences (Android)  
**Purpose:** UI display only (recent events)  
**Retention:** 200 entries max, circular buffer  
**Why ephemeral:** Duplicate of Firestore `events`, exists only for offline UI

**Sync pattern:**
```
- LogRepository keeps last 200 events in memory
- On app start, load from SharedPreferences (fast)
- Periodically sync from Firestore (background)
- If conflict, Firestore is source of truth
```

#### 2. Client-side Pending Queue
**Storage:** SQLite (Android)  
**Purpose:** Retry failed ingestions  
**Retention:** Delete after successful ACK  
**Why ephemeral:** Once ingested to Firestore, queue entry is redundant

**Lifecycle:**
```
1. Event captured → write to SQLite queue
2. POST to Firestore /ingest
3. Receive 200 ACK → delete from SQLite
4. If ACK not received → retry from SQLite
```

#### 3. Client-side Templates Cache
**Storage:** JsonStorage (Android)  
**Purpose:** Offline access to template library  
**Retention:** Delete when user logs out  
**Why ephemeral:** True source is Firestore `templates` collection

**Sync pattern:**
```
- On app start, load from JsonStorage (fast)
- Background sync from Firestore
- If conflict, Firestore wins
- User can create templates offline → sync when online
```

---

### WHAT IS WRITE-ONCE ✍️

#### 1. Ingested Events
**Write pattern:** `set()` with UUID (idempotent)  
**Update pattern:** NEVER (immutable)  
**Delete pattern:** Soft delete (mark `deleted: true`)

**Rationale:** Audit trail requires immutability. Corrections are done by creating new events with `parentUuid` reference.

#### 2. Delivery Receipts
**Write pattern:** `add()` (auto-generated ID)  
**Update pattern:** NEVER  
**Delete pattern:** NEVER (cascades with parent event)

**Rationale:** Each delivery attempt must be logged for debugging. No updates needed.

---

### WHAT IS MUTABLE 🔄

#### 1. Source Configuration
**Write pattern:** `set()` with sourceId  
**Update pattern:** User edits via UI → `update()`  
**Delete pattern:** User deletes → `delete()`

**Rationale:** Users need to modify Sources (add endpoints, change templates, toggle enable/disable).

**Concurrency:** Last-write-wins (Firestore default). For production, use transactions if multiple devices edit same Source.

#### 2. Endpoint Configuration
**Write pattern:** `set()` with endpointId  
**Update pattern:** User edits via UI → `update()`  
**Delete pattern:** User deletes → `delete()`

**Concurrency:** Last-write-wins. Stats are updated atomically via `FieldValue.increment()`.

#### 3. Endpoint Stats
**Write pattern:** `FieldValue.increment()` (atomic)  
**Update pattern:** Server increments after each delivery  
**Delete pattern:** Reset to 0 via UI

**Rationale:** Stats must be accurate. Atomic increments prevent race conditions.

---

### WHAT SHOULD NEVER BE WRITTEN DIRECTLY BY CLIENTS 🚫

#### 1. Delivery Receipts
**Why:** Only server knows if delivery actually succeeded. Client cannot write:
```javascript
// ❌ NEVER DO THIS FROM CLIENT
await firestore.collection('events').doc(uuid)
  .collection('deliveries').add({
    endpointId: 'sheets-primary',
    httpCode: 200 // Client can lie!
  });
```

**Correct pattern:**
```javascript
// ✅ Server writes after actual delivery
const response = await fetch(endpoint.url, {body: payload});
await firestore.collection('events').doc(uuid)
  .collection('deliveries').add({
    endpointId: endpoint.id,
    httpCode: response.status,
    sentAt: FieldValue.serverTimestamp()
  });
```

#### 2. Other Users' Data
**Why:** Security. Each user can only read/write their own data.

**Firestore Security Rules:**
```javascript
match /events/{uuid} {
  // User can write their own events
  allow create: if request.auth.uid == request.resource.data.userId;
  // User can read their own events
  allow read: if request.auth.uid == resource.data.userId;
  // User cannot update (immutable)
  allow update: if false;
}

match /sources/{sourceId} {
  // User can CRUD their own sources
  allow read, write: if request.auth.uid == resource.data.userId;
}
```

#### 3. System Configuration
**Why:** Security. Clients should not modify:
- Fanout rules (which endpoints get which events)
- Rate limits
- Cost quotas
- Admin settings

**Pattern:** System config stored in separate `_system` collection with strict security rules (admin-only write).

---

### SECURITY BOUNDARIES (EXPLICIT)

#### TRUST BOUNDARY 1: Client ↔ Firestore
```
Client sends:
├─ Authentication token (Firebase Auth)
├─ Event data (validated by Security Rules)
└─ Metadata (device ID, app version)

Firestore validates:
├─ Token is valid (not expired, not revoked)
├─ User owns this data (userId matches)
├─ Schema is correct (via Security Rules)
└─ Rate limits not exceeded

Firestore rejects:
├─ Unauthenticated requests
├─ Cross-user access attempts
├─ Schema violations
└─ Rate limit violations
```

#### TRUST BOUNDARY 2: Firestore ↔ External Endpoints
```
Cloud Function sends:
├─ Rendered JSON payload (from trusted source)
├─ HTTP headers (configured by admin)
└─ Timeout (prevents hanging)

External endpoint responds:
├─ HTTP status code
├─ Response body (logged, not trusted)
└─ Response time (tracked)

Cloud Function NEVER:
├─ Executes response body as code
├─ Stores sensitive data from response
├─ Trusts response without validation
```

#### TRUST BOUNDARY 3: Admin ↔ System Config
```
Admin can:
├─ View all users' data (for support)
├─ Modify system config (fanout rules, quotas)
├─ Delete events (with audit trail)
└─ Reset endpoint stats

Regular user CANNOT:
├─ View other users' data
├─ Modify system config
├─ Delete others' events
└─ Bypass rate limits
```

---

### CALL OUT: SECURITY BOUNDARIES EXPLICITLY

| Action | Client | Server | Admin |
|--------|--------|--------|-------|
| Write event to Firestore | ✅ Own events only | ✅ Any event | ✅ Any event |
| Update event | ❌ Immutable | ❌ Immutable | ❌ Immutable* |
| Delete event | ❌ | ❌ | ✅ Soft delete |
| Write delivery receipt | ❌ | ✅ | ❌ |
| Read own events | ✅ | ✅ | ✅ |
| Read others' events | ❌ | ❌ | ✅ |
| CRUD own sources | ✅ | ✅ | ✅ |
| CRUD others' sources | ❌ | ❌ | ✅ |
| CRUD own endpoints | ✅ | ✅ | ✅ |
| CRUD others' endpoints | ❌ | ❌ | ✅ |
| Modify system config | ❌ | ❌ | ✅ |

*Even admins cannot update immutable events; they create corrective events instead.

---

## SUMMARY

**CANONICAL:**
- Ingested events (Firestore, immutable)
- Delivery receipts (Firestore, append-only)
- Source config (Firestore, mutable)
- Endpoint config (Firestore, mutable)

**EPHEMERAL:**
- Client activity logs (SharedPreferences, UI only)
- Client pending queue (SQLite, retry only)
- Client templates cache (JsonStorage, offline only)

**WRITE-ONCE:**
- Events, delivery receipts (audit trail)

**MUTABLE:**
- Sources, endpoints (user config)

**CLIENT PROHIBITED:**
- Writing delivery receipts
- Accessing other users' data
- Modifying system config

**SECURITY BOUNDARIES:**
1. Client authenticated via Firebase Auth
2. Firestore Security Rules enforce userId matching
3. Server validates all external responses
4. Admin actions audited in separate collection

---

**NEXT PHASE:** Define failure-aware delivery model (PHASE 2)

