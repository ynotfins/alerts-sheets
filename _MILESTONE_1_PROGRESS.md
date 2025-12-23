# Milestone 1: Zero Data Loss - Progress Report

**Date Started:** 2025-12-23  
**Status:** Day 3-4 Complete (Client-side queue + retry logic)  
**Phase:** 50% Complete (Infrastructure ready, integration pending)

---

## ✅ **COMPLETED: DAY 1-2 (Firestore Setup)**

### ✅ **1. Firestore Security Rules**
**File:** `functions/firestore.rules`

**Key Features:**
- ✅ **User isolation:** Each user can only read/write their own data
- ✅ **Immutable events:** Events cannot be updated after creation
- ✅ **Server-only receipts:** Clients cannot write delivery receipts
- ✅ **UUID validation:** Enforces proper UUID format for events
- ✅ **Required fields:** Validates all critical fields are present

**Security Boundaries:**
```
Client CAN:
- Create events (own userId only)
- Read own events
- CRUD own sources/endpoints/templates

Client CANNOT:
- Update events (immutable)
- Delete events (permanent audit trail)
- Write delivery receipts (server-only)
- Access other users' data
- Modify admin collections (fanoutRules, _system)
```

---

### ✅ **2. Cloud Function: /ingest**
**File:** `functions/src/index.ts`

**Endpoint:** `POST https://<region>-<project>.cloudfunctions.net/ingest`

**Features:**
- ✅ **Idempotent writes:** Same UUID → returns 200 (no duplicate)
- ✅ **Authentication:** Requires Firebase Auth token
- ✅ **Validation:** UUID format, timestamp, JSON payload
- ✅ **Server timestamp:** Uses `FieldValue.serverTimestamp()` for accuracy
- ✅ **Detailed logging:** All events logged with userId + sourceId

**Request Format:**
```json
POST /ingest
Authorization: Bearer <firebase-id-token>

{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "sourceId": "bnn-app",
  "payload": "{\"incidentId\":\"INC-123\",\"address\":\"...\"}",
  "timestamp": "2025-12-23T10:00:00.000Z",
  "deviceId": "android-12345",
  "appVersion": "2.0.1"
}
```

**Response (Success):**
```json
{
  "status": "ok",
  "message": "Event ingested successfully",
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "isDuplicate": false
}
```

**Response (Duplicate):**
```json
{
  "status": "ok",
  "message": "Event already ingested (duplicate)",
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "isDuplicate": true
}
```

**Error Handling:**
- 401: Missing/invalid auth token
- 400: Missing fields, invalid UUID, invalid timestamp, invalid JSON
- 405: Wrong HTTP method (not POST)
- 500: Firestore write failure

---

### ✅ **3. Firestore Collections Schema**

**Collection: `events`**
```javascript
{
  uuid: "550e8400-...",              // PK, client-generated
  sourceId: "bnn-app",                // Which source captured this
  payload: "{...}",                   // Final JSON (rendered template)
  timestamp: Timestamp,               // Client capture time
  
  userId: "firebase-user-id",         // Owner (from auth token)
  deviceId: "android-12345",          // Device identifier
  appVersion: "2.0.1",                // App version
  
  ingestionStatus: "INGESTED",        // PENDING, INGESTED
  ingestedAt: Timestamp,              // Server timestamp
  deliveryStatus: "PENDING",          // PENDING, PARTIAL, DELIVERED, FAILED
  deliveredAt: Timestamp | null,      // When delivery completed
  
  raw: null                           // Future: raw notification data
}
```

**Subcollection: `events/{uuid}/deliveryReceipts`**
```javascript
{
  endpointId: "sheets-primary",       // Which endpoint
  url: "https://...",                 // Full URL (for debugging)
  sentAt: Timestamp,                  // When delivery attempted
  httpCode: 200,                      // Response status code
  responseTime: 234,                  // Milliseconds
  error: null                         // Error message if failed
}
```

**Collection: `sources`**
```javascript
{
  userId: "firebase-user-id",         // Owner
  name: "BNN Notifications",          // Display name
  sourceType: "APP_NOTIFICATION",     // Type
  packageName: "com.bnn.app",         // For APP type
  sender: "+1234567890",              // For SMS type
  templateJson: "{...}",              // Template
  endpointIds: ["sheets", "webhook"], // Which endpoints
  autoClean: true,                    // Clean emojis
  enabled: true                       // Active
}
```

**Collection: `endpoints`**
```javascript
{
  userId: "firebase-user-id",         // Owner
  name: "Primary Sheets",             // Display name
  url: "https://...",                 // Webhook URL
  headers: {},                        // Custom HTTP headers
  timeout: 15000,                     // Milliseconds
  enabled: true,                      // Active
  stats: {
    totalRequests: 1234,
    successCount: 1200,
    failureCount: 34,
    avgResponseTime: 234
  }
}
```

---

## 🚀 **DEPLOYMENT INSTRUCTIONS**

### **Prerequisites:**
1. Firebase project created
2. Firebase CLI installed (`npm install -g firebase-tools`)
3. Logged in (`firebase login`)

### **Deploy:**
```bash
# Navigate to functions directory
cd functions

# Install dependencies
npm install

# Build TypeScript
npm run build

# Deploy Firestore Security Rules
firebase deploy --only firestore:rules

# Deploy Cloud Functions
firebase deploy --only functions

# Expected output:
# ✔ functions[ingest(us-central1)]: Successful create operation.
# ✔ functions[deliverEvent(us-central1)]: Successful create operation.
# ✔ functions[health(us-central1)]: Successful create operation.
```

### **Test Deployment:**
```bash
# Health check
curl https://us-central1-<project-id>.cloudfunctions.net/health

# Expected response:
# {
#   "status": "ok",
#   "timestamp": "2025-12-23T10:00:00.000Z",
#   "service": "AlertsToSheets Cloud Functions",
#   "version": "1.0.0-milestone1"
# }
```

---

## ✅ **VERIFICATION CHECKLIST**

- [ ] Firestore Security Rules deployed
- [ ] Cloud Function `/ingest` deployed
- [ ] Health check returns 200
- [ ] Firestore collections visible in console
- [ ] Test ingestion with cURL + Firebase Auth token
- [ ] Verify idempotency (send same UUID twice)

---

## ✅ **COMPLETED: DAY 3-4 (Client-side Queue + Retry Logic)**

### ✅ **1. Enhanced SQLite Database (`IngestQueueDb.kt`)**
**File:** `android/app/src/main/java/com/example/alertsheets/data/IngestQueueDb.kt`

**Key Features:**
- ✅ **Write-Ahead Logging (WAL):** Crash-safe writes
- ✅ **UUID Primary Key:** Prevents duplicates at DB level
- ✅ **Retry Tracking:** Counts attempts + timestamps
- ✅ **Automatic Cleanup:** Deletes entries >7 days old
- ✅ **Crash Recovery:** Marks in-flight events back to PENDING
- ✅ **Indexed Queries:** Fast lookups by status and creation time

**Schema:**
```sql
CREATE TABLE ingestion_queue (
  uuid TEXT PRIMARY KEY,              -- Client-generated UUID
  sourceId TEXT NOT NULL,              -- Which source
  payload TEXT NOT NULL,               -- Final JSON
  timestamp TEXT NOT NULL,             -- Capture time (ISO 8601)
  deviceId TEXT,                       -- Device identifier
  appVersion TEXT,                     -- App version
  status TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING, SENT, FAILED
  retryCount INTEGER DEFAULT 0,        -- How many retries
  lastAttemptAt INTEGER,               -- Last retry timestamp
  createdAt INTEGER NOT NULL,          -- When queued
  errorMessage TEXT                    -- Last error message
);

CREATE INDEX idx_status ON ingestion_queue(status);
CREATE INDEX idx_created_at ON ingestion_queue(createdAt);
```

**Critical Features:**
```kotlin
// Write-Ahead Logging (crash-safe)
db.execSQL("PRAGMA journal_mode=WAL")

// Enqueue with conflict handling
db.insertWithOnConflict(
    TABLE_QUEUE, 
    values, 
    SQLiteDatabase.CONFLICT_IGNORE  // Ignore duplicates
)

// Crash recovery
fun recoverFromCrash() {
    db.rawQuery("UPDATE ingestion_queue SET status = 'PENDING' ...")
}

// Automatic cleanup
fun cleanupOldEntries() {
    val cutoffTime = now - (7 days)
    db.delete("createdAt < ?", cutoffTime)
}
```

---

### ✅ **2. Ingestion Queue Manager (`IngestQueue.kt`)**
**File:** `android/app/src/main/java/com/example/alertsheets/data/IngestQueue.kt`

**Key Features:**
- ✅ **Exponential Backoff:** 1s → 2s → 4s → 8s → 16s → 32s → 60s
- ✅ **Jitter:** ±20% randomization prevents thundering herd
- ✅ **Firebase Auth:** Automatic token refresh
- ✅ **Idempotent Ingestion:** Detects server duplicates
- ✅ **Permanent Failure Detection:** Stops retrying on 400 errors
- ✅ **Queue Statistics:** pendingCount, oldestEventAgeSec

**Retry Strategy:**
```kotlin
RETRY_DELAYS = [1s, 2s, 4s, 8s, 16s, 32s, 60s]

fun calculateBackoff(retryCount: Int): Long {
    val delay = RETRY_DELAYS.getOrElse(retryCount) { 60s }
    val jitter = delay * (0.8 + Math.random() * 0.4)  // ±20%
    return jitter
}
```

**HTTP Response Handling:**
```kotlin
200 OK + isDuplicate=false  → SUCCESS (delete from queue)
200 OK + isDuplicate=true   → DUPLICATE (delete from queue)
400 Bad Request             → PERMANENT_FAILURE (stop retrying, alert user)
401/403 Auth Failure        → RETRY (token may refresh)
429 Rate Limited            → RETRY (with backoff)
500/502/503/504 Server Error → RETRY
IOException (network)       → RETRY
```

**Usage Example:**
```kotlin
// Initialize (app start)
val ingestQueue = IngestQueue(context)

// Enqueue event
val uuid = ingestQueue.enqueue(
    sourceId = "bnn-app",
    payload = "{\"incidentId\":\"INC-123\",\"address\":\"...\"}",
    timestamp = "2025-12-23T10:00:00.000Z"
)

// Get stats
val stats = ingestQueue.getStats()
Log.d("Queue", "Pending: ${stats.pendingCount}, Oldest: ${stats.oldestEventAgeSec}s")

// Shutdown (app termination)
ingestQueue.shutdown()
```

---

## 📊 **CURRENT ARCHITECTURE**

```
┌──────────────────────────────────────────────────────────────────┐
│                  ZERO DATA LOSS ARCHITECTURE                      │
└──────────────────────────────────────────────────────────────────┘

ANDROID CLIENT:
1. Event captured (BNN notification, SMS, etc.)
2. Generate UUID (UUID.randomUUID())
3. ✅ Write to SQLite (WAL mode) ← DURABILITY CHECKPOINT
4. Attempt POST to /ingest
5. If success: Delete from SQLite
6. If failure: Retry with exponential backoff
7. If crash: Resume on app restart

FIREBASE CLOUD FUNCTIONS:
1. POST /ingest receives event
2. Verify Firebase Auth token
3. Validate UUID, payload, timestamp
4. Check for duplicate (Firestore document exists?)
5. If duplicate: Return 200 (idempotent, no-op)
6. If new: Write to Firestore events collection
7. Return 200 ACK

FIRESTORE:
1. Event document created (uuid as PK)
2. Trigger fires deliverEvent Cloud Function
3. (Future: Fan-out delivery to endpoints)
```

---

##

1. **Enhance QueueDbHelper.kt:**
   - Add write-ahead log (SQLite WAL mode)
   - Add crash recovery (resume on app start)
   - Add retry tracking (attempt count, last attempt time)

2. **Create IngestQueue.kt:**
   - Wrapper around QueueDbHelper
   - Exponential backoff retry logic
   - Firebase Auth token management
   - POST to /ingest endpoint

3. **Integration test:**
   - Capture real BNN notification
   - Verify event → SQLite → Firestore
   - Test crash recovery (kill app mid-ingestion)

---

**Status:** ✅ **Day 1-2 COMPLETE**  
**Ready For:** Day 3-4 (Client-side queue enhancement)

