# Call Chain Table - Complete Symbol-Level Reference
**Generated:** December 23, 2025, 2:35 PM  
**Purpose:** Quick reference for all workflow call chains with exact symbols

---

## 📋 **NOTIFICATION WORKFLOW CALL CHAIN**

| Step | File | Symbol | Lines | Inputs | Outputs | Next Call |
|------|------|--------|-------|--------|---------|-----------|
| **1** | `services/AlertsNotificationListener.kt` | `onNotificationPosted(sbn)` | 72-111 | `StatusBarNotification` | `RawNotification` | `processAppNotification()` |
| **2** | `domain/DataPipeline.kt` | `processAppNotification(pkg, raw)` | 176-194 | `String`, `RawNotification` | void | `findSourceForNotification()` |
| **3** | `domain/SourceManager.kt` | `findSourceForNotification(pkg)` | 30-44 | `String` | `Source?` | `process()` if found |
| **4** | `data/repositories/SourceRepository.kt` | `findByPackage(pkg)` | 76-78 | `String` | `Source?` | Return to SourceManager |
| **5** | `domain/models/Source.kt` | `matchesPackage(pkg)` | 85-93 | `String` | `Boolean` | Return to Repository |
| **6** | `domain/DataPipeline.kt` | `process(source, raw)` | 55-171 | `Source`, `RawNotification` | void | Launches coroutine |
| **7** | `domain/parsers/ParserRegistry.kt` | `get(parserId)` | ~15-30 | `String` | `Parser?` | Return parser instance |
| **8** | `domain/parsers/[Parser].kt` | `parse(raw)` | varies | `RawNotification` | `ParsedData?` | Return to process |
| **9** | `domain/SourceManager.kt` | `getTemplateJsonForSource(src)` | 172-174 | `Source` | `String` | Return template JSON |
| **10** | `utils/TemplateEngine.kt` | `apply(template, data, src)` | varies | `String`, `ParsedData`, `Source` | `String` | Return rendered JSON |
| **11** | `data/repositories/EndpointRepository.kt` | `getById(id)` | 62-69 | `String` | `Endpoint?` | Return endpoint |
| **12** | `utils/HttpClient.kt` | `post(url, body, hdrs, to)` | 32-80 | `String`, `String`, `Map`, `Int` | `HttpResponse` | HTTP POST |
| **13** | `data/repositories/EndpointRepository.kt` | `updateStats(id, success, time)` | 196-213 | `String`, `Boolean`, `Long` | void | Update endpoint stats |
| **14** | `LogRepository.kt` | `updateStatus(id, status)` | 46-54 | `String`, `LogStatus` | void | Update log entry |

---

## 📋 **SMS WORKFLOW CALL CHAIN**

| Step | File | Symbol | Lines | Inputs | Outputs | Next Call |
|------|------|--------|-------|--------|---------|-----------|
| **1** | `services/AlertsSmsReceiver.kt` | `onReceive(ctx, intent)` | 28-42 | `Context`, `Intent` | void | `handleSms()` |
| **2** | `services/AlertsSmsReceiver.kt` | `handleSms(ctx, intent)` | 47-76 | `Context`, `Intent` | void | Extract SMS messages |
| **3** | `services/AlertsSmsReceiver.kt` | `handleSms(ctx, intent)` | 68-71 | `String`, `String` | `RawNotification` | `RawNotification.fromSms()` |
| **4** | `domain/DataPipeline.kt` | `processSms(sender, raw)` | 199-217 | `String`, `RawNotification` | void | `findSourceForSms()` |
| **5** | `domain/SourceManager.kt` | `findSourceForSms(sender)` | 49-63 | `String` | `Source?` | `process()` if found |
| **6** | `data/repositories/SourceRepository.kt` | `findBySender(sender)` | 83-85 | `String` | `Source?` | Return to SourceManager |
| **7** | `domain/models/Source.kt` | `matchesSender(sender)` | 95-103 | `String` | `Boolean` | Return to Repository |
| **8-14** | **SAME AS NOTIFICATION** | Steps 6-14 from notification workflow | 55-171 | Same | Same | Same |

---

## 📋 **EMAIL WORKFLOW CALL CHAIN**

| Status | Evidence |
|--------|----------|
| ❌ **NOT IMPLEMENTED** | No `SourceType.EMAIL` in `Source.kt:136-143` |
| ❌ **NO ENTRYPOINT** | No `EmailReceiver` or `GmailListener` service |
| ❌ **NO PROCESSING** | No `processEmail()` in `DataPipeline.kt` |
| ❌ **NO PARSER** | No `EmailParser` in `domain/parsers/` |
| ⚠️ **UI STUB ONLY** | Icon exists in `LabActivity.kt:76` |

---

## 📋 **FIRESTORE INGEST CALL CHAIN (DEBUG-ONLY)**

| Step | File | Symbol | Lines | Inputs | Outputs | Next Call |
|------|------|--------|-------|--------|---------|-----------|
| **1** | `data/IngestQueue.kt` | `enqueue(srcId, payload, ts)` | 90-113 | `String`, `Map<String,Any>`, `Long` | `String` (eventId) | `db.enqueue()` |
| **2** | `data/IngestQueueDb.kt` | `enqueue(entity)` | 87-111 | `RequestEntity` | `Long` (rowId) | SQLite INSERT |
| **3** | `data/IngestQueue.kt` | `processQueue()` | 115-128 | none | void | `processQueueInternal()` |
| **4** | `data/IngestQueue.kt` | `processQueueInternal()` | 130-157 | none | void | Loop over pending |
| **5** | `data/IngestQueue.kt` | `sendToFirebase(entity)` | 159-242 | `RequestEntity` | `RequestEntity` (updated) | Get Firebase token |
| **6** | Firebase Auth | `FirebaseAuth.currentUser.getIdToken()` | N/A | none | `String` (token) | OkHttp POST |
| **7** | `data/NetworkClient.kt` | `post(url, json, token)` | varies | `String`, `String`, `String` | `Response` | HTTP POST to Cloud Function |
| **8** | Cloud Function | `/ingest` | `functions/src/index.ts:40-153` | `{eventId, sourceId, payload, timestamp}` | `{status, docId?}` | Firestore transaction |
| **9** | Firestore | `db.runTransaction()` | `functions/src/index.ts:73-144` | Transaction object | void | Idempotent write |
| **10** | `data/IngestQueueDb.kt` | `updateStatus(id, status)` | 149-170 | `Long`, `String` | void | SQLite UPDATE |

---

## 📋 **SHARED COMPONENT CALL CHAINS**

### Parser Registry

| Symbol | File | Lines | Purpose |
|--------|------|-------|---------|
| `ParserRegistry.get(parserId)` | `domain/parsers/ParserRegistry.kt` | ~15-30 | Returns parser instance for ID |
| `BnnParser.parse(raw)` | `domain/parsers/BnnParser.kt` | varies | Parse BNN Fire notifications |
| `GenericAppParser.parse(raw)` | `domain/parsers/GenericAppParser.kt` | varies | Parse generic notifications |
| `SmsParser.parse(raw)` | `domain/parsers/SmsParser.kt` | varies | Parse SMS messages |

### Template Engine

| Symbol | File | Lines | Purpose |
|--------|------|-------|---------|
| `TemplateEngine.apply(template, data, source)` | `utils/TemplateEngine.kt` | varies | Replace {{vars}} in template |
| `TemplateEngine.getTimestamp()` | `utils/TemplateEngine.kt` | varies | Generate RFC3339 timestamp |
| `TemplateEngine.cleanText(text)` | `utils/TemplateEngine.kt` | varies | Remove emojis if autoClean=true |

### HTTP Client

| Symbol | File | Lines | Purpose |
|--------|------|-------|---------|
| `HttpClient.post(url, body, headers, timeout)` | `utils/HttpClient.kt` | 32-80 | Send JSON to webhook (HttpURLConnection) |
| `HttpResponse(code, message, body)` | `utils/HttpClient.kt` | 16-21 | Response data class |

### Log Repository

| Symbol | File | Lines | Purpose |
|--------|------|-------|---------|
| `LogRepository.addLog(entry)` | `LogRepository.kt` | 35-44 | Add new log entry |
| `LogRepository.updateStatus(id, status)` | `LogRepository.kt` | 46-54 | Update log entry status |
| `LogRepository.saveLogs()` | `LogRepository.kt` | 88-96 | Persist logs to SharedPrefs |

---

## 📋 **REPOSITORY CALL CHAINS**

### SourceRepository

| Symbol | Lines | Purpose | Storage |
|--------|-------|---------|---------|
| `getAll()` | 34-64 | Load all sources | `sources.json` |
| `getById(id)` | 69-71 | Find source by ID | `sources.json` |
| `findByPackage(pkg)` | 76-78 | Find APP source | `sources.json` |
| `findBySender(sender)` | 83-85 | Find SMS source | `sources.json` |
| `save(source)` | 101-124 | Create/update source | `sources.json` |
| `delete(id)` | 129-145 | Delete source | `sources.json` |
| `updateStats(id, processed, sent, failed)` | 150-177 | Update source stats | `sources.json` |

### EndpointRepository

| Symbol | Lines | Purpose | Storage |
|--------|-------|---------|---------|
| `getAll()` | 34-57 | Load all endpoints | `endpoints.json` |
| `getById(id)` | 62-69 | Find endpoint by ID | `endpoints.json` |
| `save(endpoint)` | 98-121 | Create/update endpoint | `endpoints.json` |
| `deleteById(id)` | 139-155 | Delete endpoint | `endpoints.json` |
| `updateStats(id, success, responseTime)` | 196-213 | Update endpoint stats | `endpoints.json` |

### TemplateRepository

| Symbol | Lines | Purpose | Storage |
|--------|-------|---------|---------|
| `getAllTemplates()` | 134-141 | Load all templates | `SharedPreferences` |
| `getByMode(mode)` | 182-189 | Filter by APP/SMS | `SharedPreferences` |
| `saveUserTemplate(template)` | 146-153 | Save custom template | `SharedPreferences` |
| `deleteUserTemplate(name)` | 158-165 | Delete custom template | `SharedPreferences` |

---

## 📋 **STORAGE MECHANISM CALL CHAINS**

### JsonStorage (Atomic Writes)

| Symbol | File | Lines | Purpose |
|--------|------|-------|---------|
| `JsonStorage.write(json)` | `data/storage/JsonStorage.kt` | 74-108 | Atomic write (temp + rename) |
| `JsonStorage.read()` | `data/storage/JsonStorage.kt` | 35-63 | Read JSON string |
| **Lock Type** | `synchronized(lock)` per file | 23 | Per-file lock object |

**Usage:**
- `SourceRepository` → `sources.json`
- `EndpointRepository` → `endpoints.json`

### IngestQueueDb (SQLite WAL)

| Symbol | File | Lines | Purpose |
|--------|------|-------|---------|
| `IngestQueueDb.enqueue(entity)` | `data/IngestQueueDb.kt` | 87-111 | INSERT request |
| `IngestQueueDb.getPending()` | `data/IngestQueueDb.kt` | 113-147 | SELECT pending/retrying |
| `IngestQueueDb.updateStatus(id, status)` | `data/IngestQueueDb.kt` | 149-170 | UPDATE status |
| `IngestQueueDb.markSent(id)` | `data/IngestQueueDb.kt` | 172-188 | DELETE after success |
| **Lock Type** | SQLite WAL mode | 46 | Multi-reader, single-writer |

---

## 📋 **CONCURRENCY PRIMITIVES**

| Component | Type | File | Line | Purpose |
|-----------|------|------|------|---------|
| **DataPipeline scope** | `CoroutineScope(Dispatchers.IO + SupervisorJob())` | `domain/DataPipeline.kt` | 49 | Independent event processing |
| **IngestQueue scope** | `CoroutineScope(Dispatchers.IO + SupervisorJob())` | `data/IngestQueue.kt` | 55 | Queue processing |
| **IngestQueue gate** | `AtomicBoolean(false)` | `data/IngestQueue.kt` | 56 | Prevent concurrent processQueue() |
| **LogRepository lock** | `synchronized(logs)` | `LogRepository.kt` | 27 | Thread-safe log access |
| **JsonStorage lock** | `synchronized(lock)` | `data/storage/JsonStorage.kt` | 23 | Thread-safe file I/O |
| **IngestQueueDb lock** | SQLite WAL | `data/IngestQueueDb.kt` | 46 | Database-level locking |

---

## 📋 **FAILURE PROPAGATION MATRIX**

| Failure Point | Propagates Up? | Kills Coroutine? | Kills Other Events? | Logged? |
|---------------|----------------|------------------|---------------------|---------|
| **Parser fails** | ✅ Early return | ✅ Yes (this event only) | ❌ No (SupervisorJob) | ✅ LogEntry FAILED |
| **Template empty** | ✅ Early return | ✅ Yes (this event only) | ❌ No (SupervisorJob) | ✅ LogEntry FAILED |
| **No endpoints** | ✅ Early return | ✅ Yes (this event only) | ❌ No (SupervisorJob) | ✅ LogEntry FAILED |
| **HTTP timeout** | ❌ Caught per endpoint | ❌ No (try-catch) | ❌ No | ✅ Endpoint stats |
| **Stats update fails** | ❌ Caught | ❌ No | ❌ No | ⚠️ Log.e only |
| **Pipeline exception** | ❌ Caught | ✅ Yes (this event only) | ❌ No (SupervisorJob) | ✅ LogEntry FAILED |

---

**END OF CALLCHAIN_TABLE.md**

