# AlertsToSheets Sequence Diagrams
**Generated:** 2025-12-23  
**Purpose:** Visual flow documentation for all runtime workflows

---

## 1. NOTIFICATION CAPTURE → DELIVERY

```mermaid
sequenceDiagram
    participant Android as Android System
    participant NL as AlertsNotificationListener
    participant DP as DataPipeline
    participant SM as SourceManager
    participant SR as SourceRepository
    participant PR as ParserRegistry
    participant TE as TemplateEngine
    participant ER as EndpointRepository
    participant HC as HttpClient
    participant LR as LogRepository
    participant Sheet as Google Sheets

    Android->>NL: onNotificationPosted(sbn)
    Note over NL: Line 72
    NL->>NL: Extract packageName, title, text
    Note over NL: Lines 76-89
    NL->>NL: Create RawNotification
    Note over NL: Lines 94-103
    NL->>DP: processAppNotification(packageName, raw)
    Note over DP: Line 176

    DP->>SM: findSourceForNotification(packageName)
    SM->>SR: findByPackage(packageName)
    SR-->>SM: Source | null
    SM-->>DP: Source | null

    alt No Source Found
        DP->>LR: addLog(status=IGNORED)
        Note over DP: Lines 186-192
        DP-->>NL: END
    else Source Found
        DP->>DP: scope.launch { process(source, raw) }
        Note over DP: Line 180, Dispatchers.IO

        DP->>LR: addLog(logEntry, status=PENDING)
        Note over DP: Lines 58-65

        DP->>PR: get(source.parserId)
        PR-->>DP: Parser | null

        alt No Parser
            DP->>LR: updateStatus(FAILED)
            DP-->>NL: END
        else Parser Found
            DP->>PR: parse(raw)
            PR-->>DP: ParsedData | null

            alt Parse Failed
                DP->>LR: updateStatus(FAILED)
                DP-->>NL: END
            else Parse Success
                DP->>LR: updateStatus(PROCESSING)
                Note over DP: Line 91

                DP->>SM: getTemplateJsonForSource(source)
                SM-->>DP: templateJson

                alt No Template
                    DP->>LR: updateStatus(FAILED)
                    DP-->>NL: END
                else Template Found
                    DP->>TE: apply(templateJson, parsed, source)
                    TE-->>DP: json (rendered payload)

                    DP->>ER: getById() for each endpointId
                    ER-->>DP: List<Endpoint>

                    alt No Endpoints
                        DP->>LR: updateStatus(FAILED)
                        DP-->>NL: END
                    else Endpoints Found
                        loop For Each Endpoint
                            DP->>HC: post(endpoint.url, json, headers)
                            HC->>Sheet: HTTP POST
                            Sheet-->>HC: 200 OK / 4xx / 5xx
                            HC-->>DP: HttpResponse

                            alt Success
                                DP->>ER: updateStats(success=true)
                            else Failure
                                DP->>ER: updateStats(success=false)
                            end
                        end

                        alt All Success
                            DP->>LR: updateStatus(SENT)
                        else Some Success
                            DP->>LR: updateStatus(PARTIAL)
                        else All Failed
                            DP->>LR: updateStatus(FAILED)
                        end

                        DP->>SM: recordNotificationProcessed(success)
                    end
                end
            end
        end
    end
```

**Key Observations:**
- **Entry:** `AlertsNotificationListener.onNotificationPosted()` (Line 72)
- **Async Boundary:** `scope.launch` at Line 180 (switches to Dispatchers.IO)
- **Fan-out:** Loop at Line 125 sends to ALL configured endpoints
- **Partial Success:** Tracked with `anySuccess` and `allSuccess` flags
- **Logging:** LogEntry created at start (PENDING), updated throughout

---

## 2. SMS CAPTURE → DELIVERY

```mermaid
sequenceDiagram
    participant Android as Android System
    participant SMSRx as AlertsSmsReceiver
    participant DP as DataPipeline
    participant SM as SourceManager
    participant SR as SourceRepository
    participant PR as ParserRegistry
    participant TE as TemplateEngine
    participant ER as EndpointRepository
    participant HC as HttpClient
    participant LR as LogRepository
    participant Sheet as Google Sheets

    Android->>SMSRx: onReceive(SMS_RECEIVED_ACTION)
    Note over SMSRx: Line 28
    SMSRx->>SMSRx: handleSms(context, intent)
    Note over SMSRx: Line 47

    SMSRx->>SMSRx: Extract SmsMessage[]
    Note over SMSRx: Lines 49-54 (Android reassembles multi-part)

    SMSRx->>SMSRx: Get sender & fullMessage
    Note over SMSRx: Lines 62-63

    SMSRx->>SMSRx: Create RawNotification.fromSms()
    Note over SMSRx: Lines 68-71

    SMSRx->>DP: processSms(sender, raw)
    Note over DP: Line 199

    DP->>SM: findSourceForSms(sender)
    SM->>SR: findBySender(sender)
    SR-->>SM: Source | null
    SM-->>DP: Source | null

    alt No Source Found
        DP->>LR: addLog(status=IGNORED)
        Note over DP: Lines 209-215
        DP-->>SMSRx: END
    else Source Found
        Note over DP: SAME AS NOTIFICATION FLOW
        DP->>DP: process(source, raw)
        Note over DP: Line 204
        Note right of DP: See Notification Diagram<br/>Lines 55-161 (identical)
        DP->>LR: [Processing steps...]
        DP->>PR: [Parsing...]
        DP->>TE: [Template application...]
        DP->>ER: [Endpoint resolution...]
        loop For Each Endpoint
            DP->>HC: post(endpoint.url, json)
            HC->>Sheet: HTTP POST
            Sheet-->>HC: Response
            HC-->>DP: HttpResponse
        end
        DP->>LR: updateStatus(SENT/PARTIAL/FAILED)
        DP->>SM: recordNotificationProcessed()
    end
```

**Key Observations:**
- **Entry:** `AlertsSmsReceiver.onReceive()` (Line 28)
- **Convergence:** Both SMS and Notification paths call `DataPipeline.process()` (Line 55)
- **Multi-part SMS:** Android handles reassembly, `getMessagesFromIntent()` returns complete message
- **Same Delivery:** Uses identical endpoint fan-out and logging as notifications

---

## 3. LAB CARD CREATE/EDIT → PIPELINE EFFECTS

```mermaid
sequenceDiagram
    participant User
    participant Lab as LabActivity
    participant SM as SourceManager
    participant SR as SourceRepository
    participant ER as EndpointRepository
    participant TR as TemplateRepository
    participant JS as JsonStorage
    participant Disk as sources.json

    User->>Lab: Open LabActivity
    Note over Lab: onCreate()

    alt Editing Existing Source
        User->>Lab: Click source card (with source_id)
        Lab->>SM: getSource(source_id)
        SM->>SR: getById(source_id)
        SR->>Disk: Read sources.json
        Disk-->>SR: JSON content
        SR-->>SM: Source object
        SM-->>Lab: Source object
        Lab->>Lab: Populate UI fields
        Note over Lab: name, type, template, endpoints,<br/>autoClean, customPayloads
    else Creating New Source
        Lab->>Lab: Initialize empty fields
    end

    User->>Lab: Select Source Type (App or SMS)
    
    alt Type = App
        User->>Lab: Launch AppsListActivity
        Lab->>User: Select app from list
        Lab->>Lab: Set id = packageName
    else Type = SMS
        User->>Lab: Enter phone or pick contact
        Lab->>Lab: Set id = "sms:{phone}"
    end

    User->>Lab: Select Template (from spinner)
    Lab->>TR: getAll()
    TR->>Disk: Read templates.json
    Disk-->>TR: Template list
    TR-->>Lab: Template list
    Lab->>Lab: Populate spinner

    User->>Lab: Choose template
    Lab->>Lab: Set source.templateJson = selected template
    Note over Lab: Per-source independent copy

    User->>Lab: Check/uncheck Auto-Clean
    Lab->>Lab: Set source.autoClean = checked

    User->>Lab: Select Endpoints (checkboxes)
    Lab->>ER: getAll()
    ER->>Disk: Read endpoints.json
    Disk-->>ER: Endpoint list
    ER-->>Lab: Endpoint list
    Lab->>Lab: Populate checkboxes

    User->>Lab: Check endpoints A, B, C
    Lab->>Lab: Set source.endpointIds = [A, B, C]

    User->>Lab: Edit custom test payloads
    Lab->>Lab: Update customTestPayload, etc.

    User->>Lab: Click "Test"
    Lab->>Lab: Render JSON with TemplateEngine
    Lab->>ER: getById(endpointId)
    ER-->>Lab: Endpoint
    Lab->>HC: post(endpoint.url, testJson)
    HC-->>Lab: Response (code, body)
    Lab->>User: Display response in UI

    User->>Lab: Click "Save"

    Lab->>Lab: Build Source object
    Note over Lab: Collect all fields into<br/>Source data class

    Lab->>SM: saveSource(source)
    SM->>SR: save(source)
    SR->>SR: getAll() (read existing)
    SR->>Disk: Read sources.json
    Disk-->>SR: Existing sources

    SR->>SR: Update or append source
    Note over SR: Lines 103-113

    SR->>JS: write(gson.toJson(allSources))
    JS->>JS: Create temp file
    JS->>JS: Write JSON to temp
    JS->>JS: Rename temp → sources.json (atomic)
    JS-->>SR: Success

    SR-->>SM: Success
    SM-->>Lab: Success

    Lab->>Lab: finish()
    Lab-->>User: Return to MainActivity

    Note over User,Disk: PIPELINE EFFECTS (Next Notification)

    Android->>NL: onNotificationPosted(packageName)
    NL->>DP: processAppNotification(packageName, raw)
    DP->>SM: findSourceForNotification(packageName)
    SM->>SR: findByPackage(packageName)
    SR->>Disk: Read sources.json
    Disk-->>SR: ✅ NEW/UPDATED SOURCE
    SR-->>SM: Updated Source
    SM-->>DP: Updated Source

    DP->>DP: Use NEW templateJson, endpointIds, autoClean
    Note over DP: Processing continues with<br/>updated configuration
```

**Key Observations:**
- **Per-Source Configuration:** Each source has its own `templateJson`, `endpointIds`, `autoClean`
- **No Shared Templates:** Source gets a copy of template, not a reference
- **Atomic Write:** `JsonStorage.write()` uses temp file + rename
- **Immediate Effect:** Next notification uses updated source (file re-read on each notification)
- **Test Feature:** HTTP POST sent from LabActivity (separate from DataPipeline)

**Side Effect Timeline:**
1. User saves source → `sources.json` updated
2. Notification arrives → DataPipeline reads `sources.json` → finds updated source
3. Processing uses NEW config (template, endpoints, autoClean flags)

**Race Condition Window:**
- If notification arrives DURING source save → uses old data (coroutine captures source at start)
- Next notification → uses new data
- **Impact:** Expected behavior (eventual consistency)

---

## 4. FIRESTORE INGEST PIPELINE (MILESTONE 1 - NOT YET INTEGRATED)

```mermaid
sequenceDiagram
    participant Test as IngestTestActivity
    participant IQ as IngestQueue
    participant DB as IngestQueueDb
    participant SQLite as SQLite WAL
    participant FAuth as FirebaseAuth
    participant OkHttp as OkHttpClient
    participant CF as Cloud Function /ingest
    participant FS as Firestore

    Test->>IQ: enqueue(eventId, sourceId, payload)
    Note over IQ: Line 92

    IQ->>DB: Check if already queued
    DB->>SQLite: SELECT WHERE uuid = ?
    SQLite-->>DB: Row | null
    DB-->>IQ: Boolean (exists)

    alt Already Queued
        IQ-->>Test: Skip (idempotent)
    else New Event
        IQ->>DB: enqueue(IngestQueueEntry)
        DB->>SQLite: BEGIN TRANSACTION
        DB->>SQLite: INSERT INTO ingestion_queue
        Note over SQLite: status=PENDING, retryCount=0
        SQLite-->>DB: Success
        DB->>SQLite: COMMIT
        DB->>SQLite: WAL checkpoint
        Note over SQLite: Durability guarantee
        SQLite-->>DB: Checkpoint complete
        DB-->>IQ: Enqueued

        IQ->>IQ: processQueue() (async)
        Note over IQ: Line 119

        IQ->>IQ: isProcessing.compareAndSet(false, true)
        Note over IQ: AtomicBoolean lock, Line 123

        alt Lock Acquired
            IQ->>DB: getPendingEvents()
            DB->>SQLite: SELECT WHERE status IN (PENDING, IN_PROGRESS)
            SQLite-->>DB: List<IngestQueueEntry>
            DB-->>IQ: List<IngestQueueEntry>

            loop For Each Event
                IQ->>DB: markInProgress(uuid)
                DB->>SQLite: UPDATE status = IN_PROGRESS
                SQLite-->>DB: Success

                IQ->>FAuth: getCurrentUser()
                FAuth-->>IQ: FirebaseUser | null

                alt Not Authenticated
                    IQ->>FAuth: signInAnonymously()
                    FAuth-->>IQ: FirebaseUser
                end

                IQ->>FAuth: getIdToken(forceRefresh=false)
                FAuth-->>IQ: idToken (JWT)

                IQ->>IQ: Build HTTP request
                Note over IQ: Authorization: Bearer {idToken}<br/>Content-Type: application/json

                IQ->>OkHttp: execute(request)
                OkHttp->>CF: POST /ingest
                Note over CF: functions/src/index.ts

                CF->>CF: Validate payload
                Note over CF: eventId, sourceId, data,<br/>clientMetadata

                alt Invalid Payload
                    CF-->>OkHttp: 400 Bad Request
                    OkHttp-->>IQ: HttpResponse(400)
                    IQ->>DB: markFailed(uuid, error)
                    DB->>SQLite: UPDATE status = FAILED
                    SQLite-->>DB: Success
                else Valid Payload
                    CF->>FS: db.runTransaction()
                    FS->>FS: Get users/{uid}/events/{eventId}

                    alt Document Exists
                        Note over FS: Idempotent: already ingested
                        FS-->>CF: doc.exists = true
                        CF-->>OkHttp: 200 OK (idempotent)
                        OkHttp-->>IQ: HttpResponse(200)
                        IQ->>DB: markSuccess(uuid)
                        DB->>SQLite: DELETE FROM ingestion_queue
                        SQLite-->>DB: Success
                    else Document Not Found
                        FS->>FS: transaction.set(eventRef, eventData)
                        Note over FS: ingestedAt: serverTimestamp<br/>status: "ingested"
                        FS-->>CF: Transaction committed
                        CF-->>OkHttp: 201 Created
                        OkHttp-->>IQ: HttpResponse(201)
                        IQ->>DB: markSuccess(uuid)
                        DB->>SQLite: DELETE FROM ingestion_queue
                        SQLite-->>DB: Success
                    end
                end

                alt Network Error / 5xx
                    OkHttp-->>IQ: Exception / 5xx
                    IQ->>DB: incrementRetry(uuid)
                    DB->>SQLite: UPDATE retryCount++, nextRetry=now+backoff
                    SQLite-->>DB: Success
                    Note over IQ: Exponential backoff:<br/>1s, 2s, 4s, 8s, 16s, 32s, 60s
                end
            end

            IQ->>IQ: isProcessing.set(false)
            Note over IQ: Release lock
        else Lock Not Acquired
            Note over IQ: Another processQueue() running,<br/>skip this invocation
        end

        IQ-->>Test: Queue processed
    end

    Test->>DB: getStats()
    DB->>SQLite: SELECT COUNT(*) WHERE status = ?
    SQLite-->>DB: Counts
    DB-->>Test: QueueStats (pendingCount, oldestEventAgeSec)
    Test->>Test: Display results in UI
```

**Key Observations:**
- **Entry:** `IngestQueue.enqueue()` (Line 92) - manual, NOT called by DataPipeline yet
- **Durability:** SQLite WAL + checkpoint after INSERT (Line 172)
- **Crash Recovery:** `recoverFromCrash()` marks `IN_PROGRESS` → `PENDING` on init (Line 99)
- **Concurrency:** AtomicBoolean lock prevents concurrent `processQueue()` (Line 123)
- **Idempotency:** Cloud Function checks `doc.exists` before write (Line 77)
- **Retry Logic:** Exponential backoff, max 7 retries (Line 50-56)
- **Isolation:** Separate from DataPipeline, no shared state

**NOT YET INTEGRATED:**
- DataPipeline.process() does NOT call IngestQueue.enqueue()
- Only accessible via IngestTestActivity (debug-only)
- Dual-write NOT implemented

---

## 5. DASHBOARD CARD LIFECYCLE

```mermaid
sequenceDiagram
    participant User
    participant MA as MainActivity
    participant SM as SourceManager
    participant SR as SourceRepository
    participant Disk as sources.json
    participant Lab as LabActivity

    User->>MA: Launch app
    MA->>MA: onCreate()
    MA->>MA: setupPermanentCards()
    Note over MA: Lines 68-103<br/>Lab, Permissions, Logs cards

    MA->>MA: onResume()
    Note over MA: Line 62

    MA->>MA: loadDynamicCards()
    Note over MA: Line 105

    MA->>MA: scope.launch
    Note over MA: Main + SupervisorJob

    MA->>SM: getAllSources()
    Note over MA: withContext(Dispatchers.IO)
    SM->>SR: getAll()
    SR->>Disk: Read sources.json
    Disk-->>SR: JSON content
    SR-->>SM: List<Source>
    SM-->>MA: List<Source>

    alt No Sources
        MA->>MA: Show empty state
        Note over MA: Lines 115-117
    else Sources Exist
        MA->>MA: Hide empty state
        MA->>MA: gridCards.removeAllViews()

        loop For Each Source
            MA->>MA: Inflate item_dashboard_source_card
            MA->>MA: Set icon (iconName → drawable)
            MA->>MA: Tint icon (cardColor)
            MA->>MA: Set name (source.name)
            MA->>MA: Set status dot (enabled + configured)
            MA->>MA: Set click listener
            Note over MA: Launch LabActivity with source_id
            MA->>MA: gridCards.addView(card)
        end
    end

    MA->>MA: updateStatus()
    Note over MA: Lines 176-212<br/>Update permission + log dots

    User->>MA: Click source card
    MA->>Lab: Launch with source_id extra
    Note over Lab: Edit mode

    User->>Lab: [Edit source...]
    Lab->>Lab: [Save changes]
    Lab->>SM: saveSource(source)
    SM->>SR: save(source)
    SR->>Disk: Write sources.json (atomic)
    Disk-->>SR: Success
    SR-->>SM: Success
    SM-->>Lab: Success
    Lab->>Lab: finish()
    Lab-->>MA: Return

    MA->>MA: onResume()
    Note over MA: Auto-refresh on return

    MA->>MA: loadDynamicCards()
    Note over MA: Re-read sources, rebuild cards
    MA->>SM: getAllSources()
    SM->>SR: getAll()
    SR->>Disk: Read sources.json
    Disk-->>SR: ✅ UPDATED content
    SR-->>SM: Updated sources
    SM-->>MA: Updated sources

    MA->>User: Display updated cards
    Note over MA: Changes immediately visible
```

**Key Observations:**
- **Lazy Loading:** Sources loaded in `onResume()`, not `onCreate()`
- **Auto-Refresh:** Returning from LabActivity triggers `onResume()` → reload sources
- **Async Loading:** File I/O on Dispatchers.IO, UI updates on Main
- **No Caching:** Sources re-read from disk every time (ensures fresh data)
- **Empty State:** Handled gracefully (Lines 115-117)

---

## 6. SUMMARY: CONVERGENCE POINTS

### Single Choke Point: DataPipeline.process()

```
Entry Points                 Convergence              Delivery
────────────────────────────────────────────────────────────────
NotificationListener  ───┐
                         │
SMS Receiver         ───┼──→  DataPipeline.process()  ──→  Fan-out
                         │         (Line 55)                to ALL
[Email - Not Impl]   ───┘                                endpoints
```

**Implications:**
- ALL notification/SMS processing uses same code path
- Shared failure modes (parser, template, endpoint resolution)
- Shared resources (repositories, HttpClient, LogRepository)
- Concurrent notifications = concurrent coroutines (safe, isolated)

---

**END OF SEQUENCE DIAGRAMS**

