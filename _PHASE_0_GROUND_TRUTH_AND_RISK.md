# PHASE 0 — ORIENTATION & TRUTH SETTING

**Date:** 2025-12-23  
**Project:** AlertsToSheets Android App  
**Purpose:** Establish ground truth understanding and identify real-world failure modes

---

## PROMPT 0.1 — GROUND TRUTH SCAN

### RUNTIME ENTRY POINTS (OBSERVED)

#### 1. Application Entry
**File:** `AlertsApplication.kt`  
**Type:** `Application` class  
**Lifecycle:** Starts on app launch, killed on app termination

```
onCreate() sequence:
1. Initialize LogRepository (FIRST - critical dependency)
2. Add BOOT log entry (smoke test)
3. Initialize ParserRegistry
4. Initialize Logger
4. Load legacy logs
```

**OBSERVATION:** LogRepository is initialized via global singleton pattern (`LogRepository.initialize(this)`) - this is a known anti-pattern but functionally works. It ensures all subsequent logging operations have a valid context.

#### 2. Notification Capture Entry
**File:** `AlertsNotificationListener.kt`  
**Type:** `NotificationListenerService` (foreground service)  
**Lifecycle:** Starts on app launch (if permission granted), runs 24/7

```
Flow:
1. onCreate() → startForeground() (GOD MODE - Android cannot kill)
2. onListenerConnected() → ready to receive
3. onNotificationPosted() → extract data → pipeline.processAppNotification()
4. onListenerDisconnected() → requestRebind() (auto-recovery)
```

**OBSERVATION:** This is the PRIMARY capture mechanism. It runs as a foreground service with persistent notification, making it highly resistant to Android's aggressive battery management. The `requestRebind()` in `onListenerDisconnected()` provides automatic recovery if the service is killed.

**CRITICAL:** If this service dies, ALL app notification capture stops. There is no backup mechanism.

#### 3. SMS Capture Entry
**File:** `AlertsSmsReceiver.kt`  
**Type:** `BroadcastReceiver`  
**Lifecycle:** Invoked on SMS_RECEIVED_ACTION, SMS_DELIVER_ACTION, WAP_PUSH_RECEIVED_ACTION

```
Flow:
1. onReceive() → extract SMS → create RawNotification
2. Instantiate DataPipeline (new instance per SMS!)
3. pipeline.processSms()
```

**OBSERVATION:** This is the PRIMARY SMS capture mechanism. It has MAX priority (2147483647 in AndroidManifest) to ensure it receives SMS before other apps.

**INFERENCE:** Each SMS creates a NEW `DataPipeline` instance. This is inefficient but functionally safe (no shared state corruption risk).

#### 4. UI Entry (User Actions)
**File:** `MainActivity.kt`, `LabActivity.kt`, etc.  
**Type:** Activities  
**Lifecycle:** Created when user opens app, destroyed when navigating away

**OBSERVATION:** UI activities trigger source creation/editing but do NOT participate in data capture/delivery paths. They modify configuration (Sources, Endpoints, Templates) stored in JSON files.

---

### DATA CAPTURE PATHS (OBSERVED)

#### Path 1: App Notifications
```
1. AlertsNotificationListener.onNotificationPosted()
2. Extract: packageName, title, text, bigText, extras
3. Create RawNotification.fromNotification()
4. pipeline.processAppNotification(packageName, raw)
5. sourceManager.findSourceForNotification(packageName) → match Source
6. If match: process(source, raw)
7. If no match: log as IGNORED
```

**CRITICAL OBSERVATION:** Matching is based on `packageName` (string exact match). There is NO fuzzy matching, NO wildcard support, NO fallback. If a Source's `packageName` doesn't exactly match the incoming notification, it is ignored.

**RISK:** If app package names change (e.g., app updates), Sources must be manually updated or notifications will be silently dropped.

#### Path 2: SMS Messages
```
1. AlertsSmsReceiver.onReceive()
2. Extract SMS messages (multi-part SMS support)
3. Combine parts, extract sender
4. Create RawNotification.fromSms(sender, message)
5. pipeline.processSms(sender, raw)
6. sourceManager.findSourceForSms(sender) → match Source
7. If match: process(source, raw)
8. If no match: log as IGNORED
```

**CRITICAL OBSERVATION:** Matching is based on `sender` (phone number or shortcode). The matching logic likely uses exact string match or contains/startsWith pattern (need to verify in `SourceManager`).

**RISK:** If sender phone numbers change format (e.g., +1 prefix added/removed), Sources must be updated or SMS will be ignored.

#### Path 3: UI Test Actions
**File:** `LabActivity.kt` (Test buttons)

```
1. User clicks "Test New" / "Dirty Test" / "Duplicate Test"
2. Load test payload (persistent, per-source)
3. Manually create ParsedData
4. Apply template → JSON
5. Send via DataPipeline (same path as real notifications)
```

**OBSERVATION:** Test actions use the SAME delivery path as real notifications, ensuring high fidelity. Test payloads are stored per-source in `Source.testPayloadNew`, `Source.testPayloadDirty`, `Source.testPayloadDuplicate`.

---

### DELIVERY PATHS (OBSERVED)

#### Primary Delivery Path (DataPipeline.process)
```
Step 1: Create LogEntry (PENDING status) → add to LogRepository
Step 2: Get parser from ParserRegistry
Step 3: Parse RawNotification → ParsedData
Step 4: Add timestamp to ParsedData
Step 5: Get templateJson from Source (NOT from TemplateRepository!)
Step 6: Apply template → rendered JSON (with autoClean per source)
Step 7: Get all endpoints for source (source.endpointIds → filter enabled)
Step 8: Fan-out delivery to ALL endpoints (parallel)
   - For each endpoint:
     a. httpClient.post(url, json, headers, timeout)
     b. Measure response time
     c. Update endpoint stats (success/fail, response time)
Step 9: Calculate overall status:
   - allSuccess → SENT
   - anySuccess → PARTIAL
   - noneSuccess → FAILED
Step 10: Update LogEntry status
Step 11: Update Source stats
```

**CRITICAL OBSERVATIONS:**

1. **LogEntry is created FIRST** (PENDING) before any processing. This ensures Activity Log UI shows the event immediately, even if processing fails.

2. **Template is stored IN the Source**, not fetched from TemplateRepository. This ensures each "card" has its own independent configuration.

3. **Fan-out delivery is synchronous per endpoint** but happens in a coroutine (`Dispatchers.IO`). Endpoints are processed sequentially, NOT in parallel (despite comment saying "parallel").

4. **Partial success is tracked** - if ANY endpoint succeeds, the log shows PARTIAL. If ALL succeed, SENT. If NONE succeed, FAILED.

5. **Failure in one endpoint does NOT stop others** - all endpoints are attempted even if one fails.

**INFERENCE:** The delivery path is robust to individual endpoint failures but vulnerable to:
- Coroutine cancellation (if app is killed mid-delivery)
- Network timeouts (15s per endpoint configured in HttpClient)
- Memory exhaustion (if many notifications arrive simultaneously)

#### Secondary Delivery Path (QueueProcessor - OFFLINE QUEUE)
```
1. If NetworkClient.sendJson() fails, enqueue to SQLite queue
2. QueueProcessor.processQueue() runs periodically
3. Read pending requests from DB
4. For each:
   a. NetworkClient.sendSynchronous()
   b. If success: delete from queue, update LogEntry → SENT
   c. If fail: increment retry count (max 10)
   d. If max retries: delete from queue, update LogEntry → FAILED
5. Delay 200ms between requests
6. Exponential backoff: delay = 1000ms * (retryCount + 1)
```

**CRITICAL OBSERVATION:** QueueProcessor provides durability against network failures, but:
- Max 10 retries then **data is dropped permanently**
- Retry logic is per-request, not per-endpoint (if Source has 2 endpoints and both fail, they're queued separately)
- Queue is unbounded (SQLite DB) - could grow indefinitely if network is down for days

**RISK:** If network is down for extended periods, queue could exhaust storage or memory.

---

### PERSISTENCE MECHANISMS (OBSERVED)

#### 1. SharedPreferences (LEGACY - Activity Logs Only)
**File:** `LogRepository.kt`  
**Storage:** `getSharedPreferences("log_prefs", MODE_PRIVATE)`  
**Data:** List of LogEntry (max 200, circular buffer)  
**Format:** JSON array

**OBSERVATION:** LogRepository uses SharedPreferences for activity logs only. This is a legacy pattern and is the ONLY remaining use of SharedPreferences for data (not config).

**CRITICAL:** SharedPreferences are NOT transactional. If app crashes mid-write, logs can be corrupted. The `synchronized(logs)` block provides thread safety but not crash safety.

#### 2. JsonStorage (PRIMARY - Sources, Endpoints, Templates)
**File:** `JsonStorage.kt`  
**Storage:** `context.filesDir/` (internal app storage)  
**Files:**
- `sources.json` - List<Source>
- `endpoints.json` - List<Endpoint>
- `templates.json` - List<Template> (user-created)

**Features:**
- Thread-safe (file-level lock)
- Atomic writes (temp file + rename)
- Exception recovery (cleanup temp files)

**CRITICAL OBSERVATION:** Atomic writes provide crash safety. The pattern is:
```
1. Write to sources.json.tmp
2. Rename sources.json.tmp → sources.json (atomic filesystem operation)
3. If rename fails, fallback to direct write
```

This ensures data is never corrupted mid-write, but:
- If app crashes BEFORE rename, old data is kept (no data loss)
- If app crashes AFTER rename, new data is kept (success)
- If app crashes DURING direct write (fallback), data MAY be corrupted

**INFERENCE:** JsonStorage is the most robust persistence mechanism currently in use.

#### 3. SQLite (QUEUE ONLY - Retry Mechanism)
**File:** `QueueDbHelper.kt` (implied from QueueProcessor)  
**Storage:** SQLite database (location not observed in provided code)  
**Data:** Queued HTTP requests (url, payload, logId, retryCount, status)

**OBSERVATION:** SQLite is used ONLY for the offline queue, not for canonical data. This is appropriate because:
- Queue needs transactional guarantees (SQLite provides)
- Queue needs efficient queries (by status, retry count)
- Queue size is unbounded (SQLite handles large datasets better than JSON files)

**CRITICAL:** If queue DB is corrupted, ALL queued requests are lost. There is no backup or recovery mechanism.

---

### OBSERVED vs INFERRED

| Aspect | Observed in Code | Inferred from Behavior |
|--------|------------------|------------------------|
| Entry points | ✅ Directly observed | - |
| Notification capture | ✅ Code reviewed | Works as documented |
| SMS capture | ✅ Code reviewed | Works as documented |
| Delivery fan-out | ✅ Code reviewed | Sequential, not parallel |
| Persistence atomic writes | ✅ Code reviewed | Crash-safe for JsonStorage |
| Queue retry logic | ✅ Code reviewed | 10 retries then drop |
| Failure modes | ❌ Not directly tested | Inferred from code review |
| Recovery mechanisms | ✅ Code reviewed | Limited (rebind, retry only) |

---

## PROMPT 0.2 — RISK-FIRST ASSESSMENT

### HIGHEST-RISK FAILURE MODES (by real-world impact)

#### RANK 1: SILENT DATA LOSS (CRITICAL)
**Scenario:** Notification/SMS arrives but is not captured, not logged, not visible to user

**Causes:**
1. **NotificationListenerService dies and doesn't rebind**
   - Impact: ALL app notifications lost until user manually restarts app
   - Frequency: Low (foreground service is protected)
   - Detection: User notices missing notifications in Activity Log
   - Recovery: Manual app restart

2. **SMS arrives but app crashes before creating LogEntry**
   - Impact: SMS lost forever (no retry, no log)
   - Frequency: Very low (rare crash window)
   - Detection: None (user doesn't know SMS arrived)
   - Recovery: Impossible

3. **Exception in pipeline.process() before LogEntry is created**
   - Impact: Event lost, no log entry
   - Frequency: Very low (LogEntry is created FIRST)
   - Detection: None (no log entry exists)
   - Recovery: Impossible

4. **Source misconfigured (packageName/sender doesn't match)**
   - Impact: Notifications/SMS logged as IGNORED, never delivered
   - Frequency: Medium (user error during setup)
   - Detection: Activity Log shows IGNORED status
   - Recovery: User must reconfigure Source

**RISK SCORE: 10/10**  
**REAL-WORLD IMPACT:** Emergency alerts may not be delivered to responders, potentially causing harm/death.

---

#### RANK 2: DUPLICATE DELIVERY (HIGH)
**Scenario:** Same notification/SMS delivered multiple times to same endpoint

**Causes:**
1. **App crashes mid-delivery, notification reprocessed on restart**
   - Impact: Duplicate alert sent to responders
   - Frequency: Low (rare crash window)
   - Detection: Activity Log shows duplicate entries
   - Recovery: Manual dedup by responders

2. **Queue retry sends successful request again**
   - Impact: Endpoint receives same payload 2+ times
   - Frequency: Very low (QueueProcessor checks success before retrying)
   - Detection: Endpoint logs show duplicates
   - Recovery: Endpoint must deduplicate

3. **User clicks "Test" button multiple times**
   - Impact: Multiple test payloads sent
   - Frequency: Medium (user error)
   - Detection: Activity Log shows multiple test entries
   - Recovery: Not a bug, user-initiated

**OBSERVATION:** Current architecture does NOT have duplicate detection/prevention. Each notification is assigned a unique LogEntry ID, but this ID is NOT sent to endpoints. Endpoints cannot detect duplicates.

**RISK SCORE: 7/10**  
**REAL-WORLD IMPACT:** False alarms, resource waste, responder confusion.

---

#### RANK 3: MISSED ALERTS (HIGH)
**Scenario:** Notification/SMS captured but never delivered due to transient failure that doesn't retry properly

**Causes:**
1. **All endpoints fail, max retries exceeded (10 attempts)**
   - Impact: Alert logged as FAILED, never delivered
   - Frequency: Medium (network outages, endpoint downtime)
   - Detection: Activity Log shows FAILED status
   - Recovery: User must manually retry (no UI for this currently)

2. **App killed by Android mid-delivery (coroutine cancelled)**
   - Impact: Partial delivery (some endpoints succeed, others don't)
   - Frequency: Medium (aggressive battery management)
   - Detection: Activity Log shows PARTIAL status
   - Recovery: Failed endpoints are NOT retried (no queue for partial failures)

3. **Endpoint timeout (15s) repeatedly**
   - Impact: Request queued, retried 10 times, dropped
   - Frequency: Medium (slow endpoint, network congestion)
   - Detection: Activity Log shows FAILED after 10 retries
   - Recovery: None (data lost)

**CRITICAL:** QueueProcessor drops requests after 10 retries. There is NO escalation, NO admin notification, NO manual retry UI.

**RISK SCORE: 8/10**  
**REAL-WORLD IMPACT:** Critical alerts may be lost during network outages or endpoint downtime.

---

#### RANK 4: UNRECOVERABLE ERRORS (MEDIUM)
**Scenario:** App enters a state where it cannot function without user intervention or reinstall

**Causes:**
1. **JsonStorage files corrupted (sources.json, endpoints.json)**
   - Impact: App cannot load Sources/Endpoints, all capture stops
   - Frequency: Very low (atomic writes prevent most corruption)
   - Detection: App crashes on startup or shows empty dashboard
   - Recovery: Manual file editing or app reinstall (data loss)

2. **SQLite queue DB corrupted**
   - Impact: Queue cannot load, app crashes on queue access
   - Frequency: Very low (SQLite is robust)
   - Detection: App crashes when QueueProcessor runs
   - Recovery: Delete DB file (all queued requests lost)

3. **LogRepository SharedPreferences corrupted**
   - Impact: Activity Log UI crashes or shows garbage
   - Frequency: Low (non-transactional writes)
   - Detection: LogActivity crashes
   - Recovery: Clear app data (all logs lost)

4. **Parser exception on every notification from a Source**
   - Impact: All notifications from that Source fail permanently
   - Frequency: Low (parser tested during setup)
   - Detection: Activity Log shows repeated FAILED for same Source
   - Recovery: User must fix Source config or delete/recreate

**RISK SCORE: 5/10**  
**REAL-WORLD IMPACT:** App requires reinstall or manual intervention, temporary loss of monitoring.

---

### RISK MATRIX (IMPACT × LIKELIHOOD)

| Failure Mode | Impact | Likelihood | Risk Score | Priority |
|--------------|--------|------------|------------|----------|
| Silent data loss (NotificationListener dies) | Critical | Low | 10/10 | P0 |
| Silent data loss (no log entry created) | Critical | Very Low | 10/10 | P0 |
| Missed alerts (max retries exceeded) | High | Medium | 8/10 | P1 |
| Missed alerts (app killed mid-delivery) | High | Medium | 8/10 | P1 |
| Duplicate delivery (crash mid-delivery) | Medium | Low | 7/10 | P2 |
| Unrecoverable errors (file corruption) | Medium | Very Low | 5/10 | P3 |

---

### CRITICAL GAPS (NOT SUGGESTED REFACTORS, JUST OBSERVATIONS)

1. **No duplicate detection at endpoint level**  
   Each notification has a unique LogEntry ID, but this ID is not included in the HTTP payload. Endpoints cannot deduplicate.

2. **No retry for partial failures**  
   If 1 of 2 endpoints succeeds, the failed endpoint is NOT queued for retry. LogEntry shows PARTIAL, but user has no way to manually retry failed endpoints.

3. **No escalation for persistent failures**  
   If an endpoint fails 10 times, the request is silently dropped. No alert, no notification, no admin dashboard.

4. **No health monitoring**  
   If NotificationListener dies and doesn't rebind, there's no system alert. User only discovers by noticing missing logs.

5. **No endpoint-level metrics dashboard**  
   Endpoints track success/fail stats in `EndpointStats`, but there's no UI to view this. User cannot see "Sheets endpoint is down 50% of the time".

6. **No backpressure handling**  
   If 100 notifications arrive simultaneously, 100 coroutines are launched. No queuing, no throttling, potential memory exhaustion.

---

## SUMMARY

**GROUND TRUTH:**
- App has 3 entry points: Application, NotificationListener, SmsReceiver
- 2 capture paths: App notifications, SMS
- 1 primary delivery path: DataPipeline → fan-out to endpoints
- 1 secondary delivery path: QueueProcessor (offline retry)
- 3 persistence mechanisms: SharedPreferences (logs), JsonStorage (config), SQLite (queue)

**HIGHEST RISKS:**
1. Silent data loss (P0) - if NotificationListener dies or log entry not created
2. Missed alerts (P1) - if max retries exceeded or app killed mid-delivery
3. Duplicate delivery (P2) - if crash mid-delivery
4. Unrecoverable errors (P3) - if file corruption

**CRITICAL OBSERVATION:**  
The app prioritizes "robustness" (foreground service, atomic writes, retry logic) but has gaps in:
- Observability (no health checks, no metrics UI)
- Recovery (no manual retry, no escalation)
- Durability (no endpoint-level dedup, no partial failure retry)

---

**NEXT PHASE:** Define canonical data strategy (PHASE 1)

