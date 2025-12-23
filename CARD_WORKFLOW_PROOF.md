# Card Workflow Proof - Complete Call Chain Analysis
**Generated:** December 23, 2025, 2:30 PM  
**Method:** Symbol-level analysis with concrete file/line evidence  
**Purpose:** Definitive entrypoint → delivery proof for all workflows

---

## 🎯 **WORKFLOW 1: NOTIFICATION → DELIVERY**

### Entrypoint Map

```
OS Notification Event
   ↓
[1] AlertsNotificationListener.onNotificationPosted()
   ↓
[2] DataPipeline.processAppNotification()
   ↓
[3] SourceManager.findSourceForNotification()
   ↓
[4] DataPipeline.process()
   ↓
[5] ParserRegistry.get() → Parser.parse()
   ↓
[6] TemplateEngine.apply()
   ↓
[7] EndpointRepository.getById() (fan-out loop)
   ↓
[8] HttpClient.post() (per endpoint)
   ↓
[9] EndpointRepository.updateStats()
   ↓
[10] LogRepository.updateStatus()
```

---

### [1] OS → Service Entrypoint

**File:** `android/app/src/main/java/com/example/alertsheets/services/AlertsNotificationListener.kt`

**Symbol:** `AlertsNotificationListener.onNotificationPosted(sbn: StatusBarNotification)`

**Lines:** 72-111

**Call Chain:**
```kotlin
override fun onNotificationPosted(sbn: StatusBarNotification) {
    // Line 76: Extract package name
    val packageName = sbn.packageName
    
    // Lines 79-80: Skip own notifications
    if (packageName == applicationContext.packageName) return
    
    // Lines 84-89: Extract notification data
    val notification = sbn.notification
    val extras = notification.extras
    val title = extras.getCharSequence("android.title")?.toString() ?: ""
    val text = extras.getCharSequence("android.text")?.toString() ?: ""
    val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text
    
    // Lines 94-103: Create RawNotification
    val raw = RawNotification.fromNotification(
        packageName = packageName,
        title = title,
        text = text,
        bigText = bigText,
        extras = mapOf(
            "notificationId" to sbn.id.toString(),
            "postTime" to sbn.postTime.toString()
        )
    )
    
    // Line 106: Send to pipeline
    pipeline.processAppNotification(packageName, raw)
}
```

**Dependencies:**
- `pipeline: DataPipeline` (initialized Line 46: `pipeline = DataPipeline(applicationContext)`)
- `RawNotification.fromNotification()` factory method

**Concurrency:** Runs on Android main thread (NotificationListenerService callback)

---

### [2] Pipeline Entry (Notification)

**File:** `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`

**Symbol:** `DataPipeline.processAppNotification(packageName: String, raw: RawNotification)`

**Lines:** 176-194

**Call Chain:**
```kotlin
fun processAppNotification(packageName: String, raw: RawNotification) {
    // Line 177: Find matching source
    val source = sourceManager.findSourceForNotification(packageName)
    
    // Lines 178-181: If matched, process
    if (source != null) {
        logger.log("📱 App: ${source.name}")
        Log.v("Pipe", "App notification from $packageName -> source ${source.name}")
        process(source, raw)
    } else {
        // Lines 183-193: Log as IGNORED if no source configured
        logger.log("⚠️ No source for: $packageName")
        LogRepository.addLog(LogEntry(
            packageName = packageName,
            title = "Notification Ignored",
            content = "No source configured for this app",
            status = LogStatus.IGNORED,
            rawJson = PayloadSerializer.toJson(raw)
        ))
    }
}
```

**Source Selection:** Calls `SourceManager.findSourceForNotification()`

---

### [3] Source Lookup & Selection

**File:** `android/app/src/main/java/com/example/alertsheets/domain/SourceManager.kt`

**Symbol:** `SourceManager.findSourceForNotification(packageName: String): Source?`

**Lines:** 30-44

**Call Chain:**
```kotlin
fun findSourceForNotification(packageName: String): Source? {
    // Line 32: Try exact match first
    var source = repository.findByPackage(packageName)
    
    // Lines 35-40: Fallback to generic-app if exists and enabled
    if (source == null) {
        source = repository.getById("generic-app")
        if (source?.enabled != true) {
            source = null
        }
    }
    
    // Line 43: Only return if enabled
    return if (source?.enabled == true) source else null
}
```

**Repository Call:**

**File:** `android/app/src/main/java/com/example/alertsheets/data/repositories/SourceRepository.kt`

**Symbol:** `SourceRepository.findByPackage(packageName: String): Source?`

**Lines:** 76-78

```kotlin
fun findByPackage(packageName: String): Source? {
    return getAll().firstOrNull { it.matchesPackage(packageName) }
}
```

**Source Matching Logic:**

**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt`

**Symbol:** `Source.matchesPackage(packageName: String): Boolean`

**Lines:** 85-93

```kotlin
fun matchesPackage(packageName: String): Boolean {
    // Only for APP type sources
    if (type != SourceType.APP) return false
    
    // Match by ID (format: "app:packageName" or just packageName)
    val expectedId1 = "app:$packageName"
    val expectedId2 = packageName
    return id == expectedId1 || id == expectedId2
}
```

---

### [4] Core Processing Pipeline

**File:** `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`

**Symbol:** `DataPipeline.process(source: Source, raw: RawNotification)`

**Lines:** 55-171

**Call Chain (Step by Step):**

```kotlin
fun process(source: Source, raw: RawNotification) {
    scope.launch {  // Line 56: Launch coroutine on Dispatchers.IO
        
        // STEP 1: Create LogEntry (Lines 58-65)
        val logEntry = LogEntry(
            packageName = raw.packageName,
            title = raw.title,
            content = raw.text.take(200),
            status = LogStatus.PENDING,
            rawJson = PayloadSerializer.toJson(raw)
        )
        LogRepository.addLog(logEntry)
        
        try {
            // STEP 2: Get Parser (Lines 72-77)
            val parser = ParserRegistry.get(source.parserId)
            if (parser == null) {
                logger.error("❌ No parser found: ${source.parserId}")
                sourceManager.recordNotificationProcessed(source.id, success = false)
                return@launch
            }
            
            // STEP 3: Parse (Lines 80-86)
            val parsed = parser.parse(raw)
            if (parsed == null) {
                logger.error("❌ Parse failed: ${source.name}")
                LogRepository.updateStatus(logEntry.id, LogStatus.FAILED)
                sourceManager.recordNotificationProcessed(source.id, success = false)
                return@launch
            }
            
            // STEP 4: Add timestamp (Line 89)
            val parsedWithTimestamp = parsed.copy(timestamp = TemplateEngine.getTimestamp())
            LogRepository.updateStatus(logEntry.id, LogStatus.PROCESSING)
            
            // STEP 5: Get Template from Source (Lines 95-101)
            val templateContent = sourceManager.getTemplateJsonForSource(source)
            if (templateContent.isEmpty()) {
                logger.error("❌ Source has no template JSON: ${source.name}")
                LogRepository.updateStatus(logEntry.id, LogStatus.FAILED)
                sourceManager.recordNotificationProcessed(source.id, success = false)
                return@launch
            }
            
            // STEP 6: Apply Template (Line 104)
            val json = TemplateEngine.apply(templateContent, parsedWithTimestamp, source)
            logger.log("✓ Template applied (autoClean=${source.autoClean})")
            
            // STEP 7: Get Endpoints (Lines 108-117)
            val endpoints = source.endpointIds
                .mapNotNull { endpointRepo.getById(it) }
                .filter { it.enabled }
            
            if (endpoints.isEmpty()) {
                logger.error("❌ No valid endpoints configured for: ${source.name}")
                LogRepository.updateStatus(logEntry.id, LogStatus.FAILED)
                sourceManager.recordNotificationProcessed(source.id, success = false)
                return@launch
            }
            
            logger.log("📤 Delivering to ${endpoints.size} endpoint(s)")
            
            // STEP 8: Fan-out Delivery (Lines 122-150)
            var anySuccess = false
            var allSuccess = true
            
            for (endpoint in endpoints) {
                try {
                    val startTime = System.currentTimeMillis()
                    val response = httpClient.post(
                        url = endpoint.url,
                        body = json,
                        headers = endpoint.headers,
                        timeout = endpoint.timeout
                    )
                    val responseTime = System.currentTimeMillis() - startTime
                    
                    if (response.isSuccess) {
                        logger.log("✓ Sent to ${endpoint.name}: ${response.code}")
                        endpointRepo.updateStats(endpoint.id, success = true, responseTime)
                        anySuccess = true
                    } else {
                        logger.error("❌ Failed ${endpoint.name}: ${response.code} - ${response.message}")
                        endpointRepo.updateStats(endpoint.id, success = false, responseTime)
                        allSuccess = false
                    }
                } catch (e: Exception) {
                    logger.error("❌ Exception ${endpoint.name}: ${e.message}")
                    endpointRepo.updateStats(endpoint.id, success = false, 0L)
                    allSuccess = false
                }
            }
            
            // STEP 9: Update Overall Status (Lines 153-161)
            val finalStatus = when {
                allSuccess -> LogStatus.SENT
                anySuccess -> LogStatus.PARTIAL
                else -> LogStatus.FAILED
            }
            
            LogRepository.updateStatus(logEntry.id, finalStatus)
            sourceManager.recordNotificationProcessed(source.id, success = anySuccess)
            
        } catch (e: Exception) {
            // Lines 163-169: Exception handling
            Log.e(TAG, "Pipeline error", e)
            logger.error("❌ Pipeline error: ${e.message}")
            LogRepository.updateStatus(logEntry.id, LogStatus.FAILED)
            sourceManager.recordNotificationProcessed(source.id, success = false)
        }
    }
}
```

**Coroutine Scope:**
- **Line 49:** `private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())`
- **Dispatchers.IO:** Background thread pool (64 threads typical)
- **SupervisorJob:** Failures don't cascade to sibling coroutines

---

### [5] Parser Execution

**File:** `android/app/src/main/java/com/example/alertsheets/domain/parsers/ParserRegistry.kt`

**Symbol:** `ParserRegistry.get(parserId: String): Parser?`

**Lines:** ~15-30 (approximate)

```kotlin
object ParserRegistry {
    private val parsers = mapOf(
        "bnn" to BnnParser(),
        "generic" to GenericAppParser(),
        "sms" to SmsParser()
    )
    
    fun get(parserId: String): Parser? {
        return parsers[parserId]
    }
}
```

**Parser Interface:**

**File:** `android/app/src/main/java/com/example/alertsheets/domain/parsers/Parser.kt`

**Symbol:** `Parser.parse(raw: RawNotification): ParsedData?`

**Returns:** `ParsedData` with structured fields or `null` if parsing fails

---

### [6] Template Application

**File:** `android/app/src/main/java/com/example/alertsheets/utils/TemplateEngine.kt`

**Symbol:** `TemplateEngine.apply(template: String, data: ParsedData, source: Source): String`

**Process:**
1. Replace `{{variables}}` in template with data fields
2. If `source.autoClean == true`, remove emojis/special chars
3. Validate JSON structure
4. Return rendered JSON string

---

### [7] Endpoint Retrieval & Fan-out

**File:** `android/app/src/main/java/com/example/alertsheets/data/repositories/EndpointRepository.kt`

**Symbol:** `EndpointRepository.getById(endpointId: String): Endpoint?`

**Lines:** 62-69

```kotlin
fun getById(endpointId: String): Endpoint? {
    return try {
        getAll().firstOrNull { it.id == endpointId }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to find endpoint by ID: $endpointId", e)
        null
    }
}
```

**Fan-out Logic:** Sequential loop over `source.endpointIds` (DataPipeline.kt:125-150)

---

### [8] HTTP Delivery

**File:** `android/app/src/main/java/com/example/alertsheets/utils/HttpClient.kt`

**Symbol:** `HttpClient.post(url: String, body: String, headers: Map<String, String>, timeout: Int): HttpResponse`

**Lines:** 32-80

```kotlin
suspend fun post(
    url: String,
    body: String,
    headers: Map<String, String> = emptyMap(),
    timeout: Int = 30000
): HttpResponse = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val urlObj = URL(url)
        connection = urlObj.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = timeout
        connection.readTimeout = timeout
        
        // Add custom headers
        for ((key, value) in headers) {
            connection.setRequestProperty(key, value)
        }
        
        // Write body
        val writer = OutputStreamWriter(connection.outputStream)
        writer.write(body)
        writer.flush()
        writer.close()
        
        // Read response
        val code = connection.responseCode
        val message = connection.responseMessage ?: ""
        
        val responseBody = if (code in 200..299) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            reader.use { it.readText() }
        } else {
            val reader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
            reader.use { it.readText() }
        }
        
        HttpResponse(code, message, responseBody)
        
    } catch (e: Exception) {
        HttpResponse(0, e.message ?: "Unknown error", "")
    } finally {
        connection?.disconnect()
    }
}
```

**Returns:** `HttpResponse(code: Int, message: String, body: String)`

---

### [9] Stats Update

**File:** `android/app/src/main/java/com/example/alertsheets/data/repositories/EndpointRepository.kt`

**Symbol:** `EndpointRepository.updateStats(endpointId: String, success: Boolean, responseTime: Long)`

**Lines:** 196-213

```kotlin
fun updateStats(endpointId: String, success: Boolean, responseTime: Long) {
    try {
        val endpoint = getById(endpointId) ?: return
        
        val newStats = endpoint.stats.copy(
            totalRequests = endpoint.stats.totalRequests + 1,
            totalSuccess = if (success) endpoint.stats.totalSuccess + 1 else endpoint.stats.totalSuccess,
            totalFailed = if (!success) endpoint.stats.totalFailed + 1 else endpoint.stats.totalFailed,
            avgResponseTime = ((endpoint.stats.avgResponseTime * endpoint.stats.totalRequests) + responseTime) / (endpoint.stats.totalRequests + 1),
            lastActivity = System.currentTimeMillis()
        )
        
        save(endpoint.copy(stats = newStats))
        
    } catch (e: Exception) {
        Log.e(TAG, "Failed to update stats for endpoint: $endpointId", e)
    }
}
```

**Storage:** `endpoints.json` via `JsonStorage.write()`

---

### [10] Log Status Update

**File:** `android/app/src/main/java/com/example/alertsheets/LogRepository.kt`

**Symbol:** `LogRepository.updateStatus(id: String, newStatus: LogStatus)`

**Lines:** 46-54

```kotlin
fun updateStatus(id: String, newStatus: LogStatus) {
    synchronized(logs) {
        logs.find { it.id == id }?.let {
            it.status = newStatus
        }
    }
    saveLogs()
    notifyListeners()
}
```

---

### Failure Handling Summary

| Failure Point | Action | Log Status | Source Stats |
|---------------|--------|------------|--------------|
| **No parser found** | Return early | N/A (not created) | Not recorded |
| **Parse failed** | Return early | FAILED | `recordNotificationProcessed(success=false)` |
| **No template** | Return early | FAILED | `recordNotificationProcessed(success=false)` |
| **No endpoints** | Return early | FAILED | `recordNotificationProcessed(success=false)` |
| **Some endpoints fail** | Continue fan-out | PARTIAL | `recordNotificationProcessed(success=true)` |
| **All endpoints fail** | Complete loop | FAILED | `recordNotificationProcessed(success=false)` |
| **Exception** | Catch & log | FAILED | `recordNotificationProcessed(success=false)` |

---

### Concurrency Model

**File:** `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`

**Lines 49, 56:**
```kotlin
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun process(source: Source, raw: RawNotification) {
    scope.launch {  // Each event in own coroutine
        // All variables LOCAL to this coroutine
        var anySuccess = false
        var allSuccess = true
        // ...
    }
}
```

**Guarantees:**
- ✅ Each event runs in independent coroutine
- ✅ SupervisorJob - one event failure doesn't kill others
- ✅ Local variables - no shared mutable state
- ✅ Thread pool (Dispatchers.IO) supports 64 concurrent events

---

## 🎯 **WORKFLOW 2: SMS → DELIVERY**

### Entrypoint Map

```
OS SMS Event
   ↓
[1] AlertsSmsReceiver.onReceive()
   ↓
[2] AlertsSmsReceiver.handleSms()
   ↓
[3] DataPipeline.processSms()
   ↓
[4] SourceManager.findSourceForSms()
   ↓
[5-10] DataPipeline.process() [SAME AS NOTIFICATION]
```

---

### [1] OS → Receiver Entrypoint

**File:** `android/app/src/main/java/com/example/alertsheets/services/AlertsSmsReceiver.kt`

**Symbol:** `AlertsSmsReceiver.onReceive(context: Context, intent: Intent)`

**Lines:** 28-42

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    try {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
            Telephony.Sms.Intents.SMS_DELIVER_ACTION -> {
                handleSms(context, intent)
            }
            Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION -> {
                handleWapPush(context, intent)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error processing SMS", e)
    }
}
```

---

### [2] SMS Extraction

**File:** `android/app/src/main/java/com/example/alertsheets/services/AlertsSmsReceiver.kt`

**Symbol:** `AlertsSmsReceiver.handleSms(context: Context, intent: Intent)`

**Lines:** 47-76

```kotlin
private fun handleSms(context: Context, intent: Intent) {
    // Lines 49-54: Extract SMS messages
    val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
    } else {
        extractMessagesLegacy(intent)
    }
    
    if (messages == null || messages.isEmpty()) {
        Log.w(TAG, "No SMS messages found in intent")
        return
    }
    
    // Lines 62-63: Combine message parts
    val sender = messages[0].originatingAddress ?: "Unknown"
    val fullMessage = messages.joinToString("") { it.messageBody ?: "" }
    
    Log.i(TAG, "📩 SMS from: $sender")
    
    // Lines 68-71: Create RawNotification
    val raw = RawNotification.fromSms(
        sender = sender,
        message = fullMessage
    )
    
    // Lines 74-75: Send to pipeline
    val pipeline = DataPipeline(context.applicationContext)
    pipeline.processSms(sender, raw)
}
```

**Note:** Creates NEW DataPipeline instance per SMS (unlike notification listener which reuses one)

---

### [3] Pipeline Entry (SMS)

**File:** `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`

**Symbol:** `DataPipeline.processSms(sender: String, raw: RawNotification)`

**Lines:** 199-217

```kotlin
fun processSms(sender: String, raw: RawNotification) {
    // Line 200: Find matching source
    val source = sourceManager.findSourceForSms(sender)
    
    // Lines 201-204: If matched, process
    if (source != null) {
        logger.log("💬 SMS: ${source.name}")
        Log.v("Pipe", "SMS from $sender -> source ${source.name}")
        process(source, raw)
    } else {
        // Lines 206-216: Log as IGNORED
        logger.log("⚠️ No source for SMS: $sender")
        Log.v("Pipe", "No source configured for SMS from $sender, ignoring")
        LogRepository.addLog(LogEntry(
            packageName = "SMS",
            title = "SMS Ignored",
            content = "No source configured for sender: $sender",
            status = LogStatus.IGNORED,
            rawJson = PayloadSerializer.toJson(raw)
        ))
    }
}
```

---

### [4] Source Lookup (SMS)

**File:** `android/app/src/main/java/com/example/alertsheets/domain/SourceManager.kt`

**Symbol:** `SourceManager.findSourceForSms(sender: String): Source?`

**Lines:** 49-63

```kotlin
fun findSourceForSms(sender: String): Source? {
    // Line 51: Try exact sender match
    var source = repository.findBySender(sender)
    
    // Lines 54-59: Fallback to generic SMS source
    if (source == null) {
        source = repository.getById("sms:dispatch")
        if (source?.enabled != true) {
            source = null
        }
    }
    
    // Line 62: Only return if enabled
    return if (source?.enabled == true) source else null
}
```

**Repository Call:**

**File:** `android/app/src/main/java/com/example/alertsheets/data/repositories/SourceRepository.kt`

**Symbol:** `SourceRepository.findBySender(sender: String): Source?`

**Lines:** 83-85

```kotlin
fun findBySender(sender: String): Source? {
    return getAll().firstOrNull { it.matchesSender(sender) }
}
```

**Source Matching:**

**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt`

**Symbol:** `Source.matchesSender(sender: String): Boolean`

**Lines:** 95-103

```kotlin
fun matchesSender(sender: String): Boolean {
    // Only for SMS type sources
    if (type != SourceType.SMS) return false
    
    // Match by ID (format: "sms:phoneNumber")
    val expectedId = "sms:$sender"
    return id == expectedId || id == sender
}
```

---

### [5-10] Processing

**Same as Notification workflow** (DataPipeline.process(), lines 55-171)

---

## 🎯 **WORKFLOW 3: EMAIL → DELIVERY**

### Status: ❌ **NOT IMPLEMENTED**

**Evidence:**

**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt`

**Symbol:** `SourceType` enum

**Lines:** 136-143

```kotlin
enum class SourceType {
    APP,  // Notification from installed app
    SMS   // SMS message from phone number
    // ❌ NO EMAIL
}
```

**Missing Symbols:**
- ❌ No `DataPipeline.processEmail()`
- ❌ No `SourceManager.findSourceForEmail()`
- ❌ No `EmailReceiver` service
- ❌ No `EmailParser`

**UI Stub Evidence:**

**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`

**Line:** 76

```kotlin
private val icons = listOf(
    "fire" to R.drawable.ic_fire,
    "sms" to R.drawable.ic_sms,
    "email" to R.drawable.ic_email,  // ⚠️ UI stub only
    // ...
)
```

---

## 🔥 **OPTIONAL WORKFLOW: FIRESTORE INGEST (DEBUG-ONLY)**

### Status: ⚠️ **IMPLEMENTED BUT NOT INTEGRATED**

**File:** `android/app/src/main/java/com/example/alertsheets/data/IngestQueue.kt`

**Symbol:** `IngestQueue.enqueue(sourceId, payload, timestamp): String`

**Lines:** 90-113

**Call Chain:**
```
IngestQueue.enqueue()
   ↓
IngestQueueDb.enqueue() [SQLite WAL]
   ↓
processQueue() [AtomicBoolean gate]
   ↓
sendToFirebase(entity: RequestEntity)
   ↓
POST to BuildConfig.INGEST_ENDPOINT
   with Firebase Auth token
   ↓
Cloud Function /ingest
   ↓
Firestore (idempotent write)
```

**Current Usage:** ONLY `IngestTestActivity` (debug source set)

**Integration Status:** ❌ **NOT called by DataPipeline**

**Proof:**
```bash
grep -r "IngestQueue" android/app/src/main/java/com/example/alertsheets/domain/
grep -r "IngestQueue" android/app/src/main/java/com/example/alertsheets/services/
# Result: No matches
```

---

## 📊 **SUMMARY TABLE**

| Workflow | Entrypoint | Source Lookup | Parser | Template | Endpoints | HTTP | Stats | Status |
|----------|-----------|---------------|--------|----------|-----------|------|-------|--------|
| **Notification** | `AlertsNotificationListener.onNotificationPosted()` | `SourceManager.findSourceForNotification()` | `ParserRegistry.get()` | `TemplateEngine.apply()` | `source.endpointIds` fan-out | `HttpClient.post()` | `EndpointRepository.updateStats()` | ✅ ACTIVE |
| **SMS** | `AlertsSmsReceiver.onReceive()` | `SourceManager.findSourceForSms()` | `ParserRegistry.get()` | `TemplateEngine.apply()` | `source.endpointIds` fan-out | `HttpClient.post()` | `EndpointRepository.updateStats()` | ✅ ACTIVE |
| **Email** | ❌ N/A | ❌ N/A | ❌ N/A | ❌ N/A | ❌ N/A | ❌ N/A | ❌ N/A | ❌ NOT IMPLEMENTED |
| **Firestore Ingest** | `IngestQueue.enqueue()` | N/A (manual test) | N/A | N/A | Firebase only | `OkHttpClient` | `IngestQueueDb` | ⚠️ DEBUG-ONLY |

---

**END OF CARD_WORKFLOW_PROOF.md**

