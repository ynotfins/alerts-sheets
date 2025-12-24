# AlertsToSheets Runtime Workflow Map
**Generated:** 2025-12-23  
**Purpose:** Evidence-based tracing of all runtime entrypoints to delivery

---

## 1. NOTIFICATION CAPTURE → DELIVERY WORKFLOW

### Entry Point
**File:** `android/app/src/main/java/com/example/alertsheets/services/AlertsNotificationListener.kt`  
**Class:** `AlertsNotificationListener`  
**Trigger:** `onNotificationPosted(StatusBarNotification)`  
**Line:** 72

### Flow Trace

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. CAPTURE (AlertsNotificationListener.kt)                      │
└─────────────────────────────────────────────────────────────────┘
   ↓
   Line 72:  override fun onNotificationPosted(sbn: StatusBarNotification)
   Line 76:  val packageName = sbn.packageName
   Line 84:  val notification = sbn.notification
   Line 87:  val title = extras.getCharSequence("android.title")?.toString()
   Line 88:  val text = extras.getCharSequence("android.text")?.toString()
   Line 89:  val bigText = extras.getCharSequence("android.bigText")?.toString()
   ↓
   Line 94-103: Create RawNotification.fromNotification()
   ↓
   Line 106: pipeline.processAppNotification(packageName, raw)
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 2. ROUTING (DataPipeline.kt)                                    │
└─────────────────────────────────────────────────────────────────┘
   File: android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt
   ↓
   Line 176: fun processAppNotification(packageName: String, raw: RawNotification)
   Line 177: val source = sourceManager.findSourceForNotification(packageName)
   ↓
   IF source == null:
      Line 186-192: LogRepository.addLog(status=IGNORED)
      → END (no processing)
   ↓
   IF source != null:
      Line 180: process(source, raw)  // Main pipeline
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 3. PROCESSING (DataPipeline.kt:process)                         │
└─────────────────────────────────────────────────────────────────┘
   Line 55: fun process(source: Source, raw: RawNotification)
   Line 56: scope.launch { ... }  // Coroutine on Dispatchers.IO
   ↓
   Line 58-65: Create LogEntry (status=PENDING)
   Line 65:    LogRepository.addLog(logEntry)
   ↓
   Line 72: val parser = ParserRegistry.get(source.parserId)
   ↓
   IF parser == null:
      Line 74-76: Log error, update stats, return
   ↓
   Line 80: val parsed = parser.parse(raw)
   ↓
   IF parsed == null:
      Line 82-85: Update LogEntry to FAILED, return
   ↓
   Line 89: val parsedWithTimestamp = parsed.copy(timestamp = ...)
   Line 91: LogRepository.updateStatus(logEntry.id, PROCESSING)
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 4. TEMPLATE APPLICATION (DataPipeline.kt)                       │
└─────────────────────────────────────────────────────────────────┘
   Line 95: val templateContent = sourceManager.getTemplateJsonForSource(source)
   ↓
   IF templateContent.isEmpty():
      Line 97-100: Update LogEntry to FAILED, return
   ↓
   Line 104: val json = TemplateEngine.apply(templateContent, parsedWithTimestamp, source)
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 5. ENDPOINT RESOLUTION (DataPipeline.kt)                        │
└─────────────────────────────────────────────────────────────────┘
   Line 108-110: val endpoints = source.endpointIds
                    .mapNotNull { endpointRepo.getById(it) }
                    .filter { it.enabled }
   ↓
   IF endpoints.isEmpty():
      Line 113-116: Update LogEntry to FAILED, return
   ↓
   Line 119: Log "📤 Delivering to ${endpoints.size} endpoint(s)"
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 6. FAN-OUT DELIVERY (DataPipeline.kt)                           │
└─────────────────────────────────────────────────────────────────┘
   Line 125: for (endpoint in endpoints) {
   ↓
   For EACH endpoint:
      Line 128-133: httpClient.post(endpoint.url, json, endpoint.headers, endpoint.timeout)
      ↓
      IF response.isSuccess:
         Line 137-139: Log success, update endpoint stats (success=true)
         anySuccess = true
      ELSE:
         Line 141-143: Log error, update endpoint stats (success=false)
         allSuccess = false
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 7. FINAL STATUS UPDATE (DataPipeline.kt)                        │
└─────────────────────────────────────────────────────────────────┘
   Line 153-157: val finalStatus = when {
                    allSuccess -> LogStatus.SENT
                    anySuccess -> LogStatus.PARTIAL
                    else -> LogStatus.FAILED
                 }
   ↓
   Line 159: LogRepository.updateStatus(logEntry.id, finalStatus)
   Line 161: sourceManager.recordNotificationProcessed(source.id, success=anySuccess)
   ↓
   END
```

### Shared Resources
- **LogRepository** (singleton, thread-safe via CoroutineScope)
- **SourceRepository** (reads from `sources.json`, writes via JsonStorage)
- **EndpointRepository** (reads from `endpoints.json`, writes via JsonStorage)
- **HttpClient** (stateless utility)
- **TemplateEngine** (stateless utility)

### Failure Modes
1. **Parser fails**: LogEntry → FAILED, source stats updated
2. **No template**: LogEntry → FAILED, source stats updated
3. **No endpoints**: LogEntry → FAILED, source stats updated
4. **Some endpoints fail**: LogEntry → PARTIAL, source stats updated with anySuccess
5. **All endpoints fail**: LogEntry → FAILED, source stats updated

---

## 2. SMS CAPTURE → DELIVERY WORKFLOW

### Entry Point
**File:** `android/app/src/main/java/com/example/alertsheets/services/AlertsSmsReceiver.kt`  
**Class:** `AlertsSmsReceiver`  
**Trigger:** `onReceive(Context, Intent)`  
**Line:** 28

### Flow Trace

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. CAPTURE (AlertsSmsReceiver.kt)                               │
└─────────────────────────────────────────────────────────────────┘
   Line 28: override fun onReceive(context: Context, intent: Intent)
   ↓
   Line 30-38: Switch on intent.action:
      - SMS_RECEIVED_ACTION / SMS_DELIVER_ACTION → handleSms()
      - WAP_PUSH_RECEIVED_ACTION → handleWapPush()
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 2. SMS EXTRACTION (AlertsSmsReceiver.kt:handleSms)              │
└─────────────────────────────────────────────────────────────────┘
   Line 47: private fun handleSms(context: Context, intent: Intent)
   Line 49-54: Extract messages from intent
   Line 62: val sender = messages[0].originatingAddress
   Line 63: val fullMessage = messages.joinToString("") { it.messageBody }
   ↓
   Line 68-71: Create RawNotification.fromSms(sender, fullMessage)
   ↓
   Line 74: val pipeline = DataPipeline(context.applicationContext)
   Line 75: pipeline.processSms(sender, raw)
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 3. ROUTING (DataPipeline.kt:processSms)                         │
└─────────────────────────────────────────────────────────────────┘
   File: android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt
   ↓
   Line 199: fun processSms(sender: String, raw: RawNotification)
   Line 200: val source = sourceManager.findSourceForSms(sender)
   ↓
   IF source == null:
      Line 209-215: LogRepository.addLog(status=IGNORED)
      → END (no processing)
   ↓
   IF source != null:
      Line 204: process(source, raw)  // Main pipeline (same as notification)
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 4-7. SAME AS NOTIFICATION WORKFLOW                              │
│      (Lines 55-161 in DataPipeline.kt)                          │
└─────────────────────────────────────────────────────────────────┘
   Parse → Template → Resolve Endpoints → Fan-out → Update Status
```

### Shared Resources
**IDENTICAL TO NOTIFICATION WORKFLOW**
- Both paths converge at `DataPipeline.process(source, raw)` (Line 55)
- Same repositories, same HttpClient, same LogRepository

### Failure Modes
**IDENTICAL TO NOTIFICATION WORKFLOW**

---

## 3. EMAIL CAPTURE → DELIVERY WORKFLOW

### Status: **NOT IMPLEMENTED**

### Evidence
**Search pattern:** `email|EMAIL|Email` across entire codebase  
**Results:** ONLY icon references, NO capture mechanism

#### Icon-Only References
1. **File:** `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`  
   **Line:** 134  
   **Code:** `"email" -> R.drawable.ic_email`  
   **Context:** Icon mapping for displaying source cards (UI decoration only)

2. **File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`  
   **Line:** 76  
   **Code:** `"email" to R.drawable.ic_email`  
   **Context:** Available icon list for user selection (UI only)

#### Missing Components
- ❌ No `EmailReceiver` or `EmailListenerService`
- ❌ No email parsing logic in `parsers/` directory
- ❌ No email-related permissions in `AndroidManifest.xml`
- ❌ No email capture logic in any service
- ❌ No `SourceType.EMAIL` variant (only `APP` and `SMS`)

#### Conclusion
**Email is a UI stub only.** The icon exists for future expansion, but there is NO runtime capture or processing mechanism for email. Any source with `iconName="email"` is purely decorative and will NOT receive or process email notifications.

---

## 4. FIRESTORE INGESTION PIPELINE (NEW - MILESTONE 1)

### Status: **IMPLEMENTED BUT NOT INTEGRATED INTO DATAPIPELINE**

### Entry Point (CLIENT)
**File:** `android/app/src/main/java/com/example/alertsheets/data/IngestQueue.kt`  
**Class:** `IngestQueue`  
**Trigger:** `enqueue(eventId, sourceId, payload)`  
**Line:** 92

### Flow Trace

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. ENQUEUE (IngestQueue.kt)                                     │
└─────────────────────────────────────────────────────────────────┘
   Line 92: fun enqueue(eventId: String, sourceId: String, payload: String)
   Line 93: Check if already queued
   Line 101: db.enqueue(IngestQueueEntry(...))
   Line 109: processQueue()  // Trigger async processing
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 2. PERSISTENCE (IngestQueueDb.kt)                               │
└─────────────────────────────────────────────────────────────────┘
   File: android/app/src/main/java/com/example/alertsheets/data/IngestQueueDb.kt
   ↓
   Line 150: fun enqueue(entry: IngestQueueEntry)
   Line 168: INSERT INTO ingestion_queue
   Line 172: WAL checkpoint (durability guarantee)
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 3. PROCESSING (IngestQueue.kt:processQueue)                     │
└─────────────────────────────────────────────────────────────────┘
   Line 119: private fun processQueue()
   Line 123: isProcessing.compareAndSet(false, true)  // Lock
   Line 127: val pending = db.getPendingEvents()
   ↓
   For EACH event:
      Line 134: ingestEvent(event)
      ↓
      Result handling:
         SUCCESS → db.markSuccess(event.uuid)
         RETRY → db.incrementRetry(event.uuid), exponential backoff
         FAILED → db.markFailed(event.uuid, error)
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 4. FIREBASE AUTH (IngestQueue.kt:getFirebaseIdToken)            │
└─────────────────────────────────────────────────────────────────┘
   Line 270: private suspend fun getFirebaseIdToken(): String
   Line 272: FirebaseAuth.getInstance().currentUser
   Line 276: user.getIdToken(forceRefresh=false).await()
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 5. HTTP POST (IngestQueue.kt:ingestEvent)                       │
└─────────────────────────────────────────────────────────────────┘
   Line 218: private suspend fun ingestEvent(event: IngestQueueEntry)
   Line 229: Build request with Authorization: Bearer <idToken>
   Line 235: httpClient.newCall(request).execute()
   ↓
   Response handling:
      200/201 → IngestResult.Success
      401/403 → IngestResult.Failed (auth error)
      4xx → IngestResult.Failed (client error)
      5xx → IngestResult.Retry (server error)
      Network exception → IngestResult.Retry
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 6. SERVER (functions/src/index.ts)                              │
└─────────────────────────────────────────────────────────────────┘
   File: functions/src/index.ts
   ↓
   Line 41: export const ingest = functions.https.onRequest(...)
   Line 42: Check method === POST
   Line 62: Validate payload (eventId, sourceId, data, clientMetadata)
   ↓
   Line 72: db.runTransaction(async (transaction) => {
   Line 73:    const eventRef = db.collection(`users/${userId}/events`).doc(eventId)
   Line 74:    const doc = await transaction.get(eventRef)
   ↓
   IF doc.exists:
      Line 77: Return 200 (idempotent, already ingested)
   ELSE:
      Line 82: transaction.set(eventRef, eventData)
      Line 87: Return 201 (created)
   ↓

┌─────────────────────────────────────────────────────────────────┐
│ 7. FIRESTORE PERSISTENCE                                        │
└─────────────────────────────────────────────────────────────────┘
   Path: users/{userId}/events/{eventId}
   Document structure:
      - eventId: string (client-generated UUID)
      - timestamp: number
      - sourceId: string
      - data: object (parsed event data)
      - clientMetadata: { appVersion, deviceModel, osVersion, userId }
      - ingestedAt: serverTimestamp
      - status: "ingested"
```

### Integration Status
**⚠️ NOT INTEGRATED WITH DataPipeline.process() YET**

Current state:
- ✅ Client-side queue implemented (IngestQueue.kt, IngestQueueDb.kt)
- ✅ Firebase Cloud Function deployed (/ingest endpoint)
- ✅ Firestore Security Rules deployed
- ✅ Test harness available (IngestTestActivity.kt, debug-only)
- ❌ DataPipeline does NOT call IngestQueue.enqueue()
- ❌ Existing delivery (HttpClient → Google Apps Script) is PRIMARY path
- ❌ Dual-write NOT implemented yet

**Next step:** Milestone 1 Gate requires E2E tests pass before DataPipeline integration.

### Shared Resources
- **IngestQueueDb** (SQLite with WAL, thread-safe)
- **IngestQueue** (singleton instance, AtomicBoolean lock for processQueue)
- **FirebaseAuth** (global singleton)
- **OkHttpClient** (stateless, connection pooling)

### Isolation Guarantees
✅ **Ingest path is ISOLATED from existing delivery:**
- Separate database (SQLite, not SharedPreferences)
- Separate HTTP client (OkHttpClient, not HttpClient)
- Separate endpoint (BuildConfig.INGEST_ENDPOINT)
- No shared state with DataPipeline.process()
- Failures in ingest path CANNOT block existing delivery (not integrated yet)

---

## 5. SUMMARY: RUNTIME ENTRYPOINTS

| Entrypoint | File | Trigger | Destination |
|------------|------|---------|-------------|
| **Notification** | `AlertsNotificationListener.kt:72` | `onNotificationPosted()` | `DataPipeline.processAppNotification()` |
| **SMS** | `AlertsSmsReceiver.kt:28` | `onReceive()` (SMS_RECEIVED) | `DataPipeline.processSms()` |
| **Email** | ❌ NOT IMPLEMENTED | N/A | N/A (icon stub only) |
| **Firestore Ingest** | `IngestQueue.kt:92` | `enqueue()` (manual, not auto) | Firebase Cloud Function `/ingest` |
| **Boot** | `BootReceiver.kt:15` | `RECEIVE_BOOT_COMPLETED` | Restart foreground service |

### Critical Observation
**All production notification/SMS processing converges at `DataPipeline.process()` (line 55).**

This is a **single choke point** for:
- Parsing
- Template application
- Endpoint resolution
- Fan-out delivery
- Status logging

**Implications:**
- Any failure in `process()` affects ALL sources
- Shared `CoroutineScope` (Dispatchers.IO) handles all async work
- Repositories are read-on-demand (no caching), so concurrent notifications = concurrent file I/O

---

## 6. LOGGING & OBSERVABILITY

### Log Entry Creation
**File:** `android/app/src/main/java/com/example/alertsheets/LogRepository.kt`  
**Function:** `addLog(LogEntry)` (Line 49)

### Log Status Transitions
```
PENDING (created at start of process)
   ↓
PROCESSING (after successful parse)
   ↓
SENT (all endpoints succeeded)
PARTIAL (some endpoints succeeded)
FAILED (all endpoints failed or pipeline error)
IGNORED (no source configured)
```

### Stats Updates
**Source stats:** `SourceManager.recordNotificationProcessed()` → `SourceRepository.updateStats()`  
**Endpoint stats:** `EndpointRepository.updateStats()` (per-endpoint, tracks responseTime, success/fail counts)

---

## 7. DELIVERY GUARANTEES (CURRENT STATE)

### What IS Guaranteed
✅ **Attempt is logged:** Every notification/SMS creates a LogEntry  
✅ **Fan-out delivery:** All configured endpoints receive payload  
✅ **Partial success tracked:** PARTIAL status if some endpoints succeed  
✅ **Stats are updated:** Source and endpoint stats reflect delivery outcomes

### What IS NOT Guaranteed
❌ **No retry on failure:** If HTTP POST fails, it's logged but NOT retried  
❌ **No offline queue:** Network failures result in immediate FAILED status  
❌ **No durability:** App crash during processing = data loss  
❌ **No delivery receipts:** No confirmation that Apps Script actually processed the data

### Future (Milestone 1 Integration)
After IngestQueue integration:
✅ **Durable queue:** SQLite WAL ensures events survive crashes  
✅ **Exponential backoff retry:** Failed requests retried with backoff  
✅ **Idempotent writes:** Duplicate submissions deduplicated by eventId  
✅ **Firestore canonical storage:** Permanent record in Firestore

---

**END OF WORKFLOW MAP**

