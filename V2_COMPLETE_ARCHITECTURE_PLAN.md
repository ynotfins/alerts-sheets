# 🎯 AlertsToSheets V2 - COMPLETE ARCHITECTURE PLAN & MIGRATION STRATEGY

**Date:** December 21, 2025  
**Branch:** `feature/v2-clean-refactor` → `main`  
**Status:** PLANNING PHASE - Full System Design  
**Goal:** Production-ready, extensible, minimal-complexity notification forwarding system

---

## 📋 **EXECUTIVE SUMMARY**

**What We Have:**  
- V2 refactor with clean architecture (Source-based system)
- V1 working code on `main` (Prefs-based system)
- TWO PARALLEL SYSTEMS that don't communicate
- SMS working partially, App notifications broken

**What We Need:**  
- ONE unified system following best practices
- Per-source settings (auto-clean, templates, parsers)
- Easy to extend (add new data types without touching core)
- Merge to `main` and eliminate confusion

**Strategy:**  
Complete the V2 migration, test thoroughly, merge to `main`, delete old code.

---

## 🎯 **CORE REQUIREMENTS (From Past Conversations)**

### **1. PRIMARY USE CASE**
**Capture BNN notifications → Parse structured data → Send to Google Sheets**

**Supporting Features:**
- SMS message forwarding (fire dispatch alerts)
- Generic app notifications (fallback)
- Multi-endpoint routing
- Offline queue with retry
- Per-source configuration

---

### **2. DATA SOURCES (Current & Future)**

```
CURRENT (V1/V2):
├── BNN App Notifications
│   ├── Package: us.bnn.newsapp
│   ├── Format: Pipe-delimited text
│   └── Parser: BNN-specific logic
│
└── SMS Messages
    ├── Senders: Emergency Services, FireLead, etc.
    ├── Format: Free text (emojis/symbols)
    └── Parser: Generic SMS template

FUTURE (Extensible):
├── WhatsApp Messages
├── Email Notifications  
├── Calendar Events
├── Location Updates
├── Phone Call Logs
├── ANY Android Data Source
```

**Design Principle:** Add new sources by creating a Source object + Parser, **ZERO core code changes**.

---

### **3. DATA DESTINATIONS (Current & Future)**

```
CURRENT:
└── Google Sheets (via Apps Script webhook)

IMMEDIATE NEXT:
└── Firebase Firestore (cloud database)

FUTURE:
├── Multiple Google Sheets (different sources → different sheets)
├── Airtable
├── Notion
├── Custom REST APIs
├── Webhooks
├── ANY HTTP Endpoint
```

**Design Principle:** Add new destinations by adding Endpoint object, **ZERO core code changes**.

---

## 🏗️ **V2 ARCHITECTURE (FINAL DESIGN)**

### **Core Entities (Data Models)**

```kotlin
// SINGLE SOURCE OF TRUTH
data class Source(
    val id: String,                    // "com.example.bnn" or "sms:+1234567890"
    val type: SourceType,              // APP or SMS
    val name: String,                  // "BNN Alerts" or "Emergency Services"
    val enabled: Boolean,              // Toggle on/off
    
    // For APP type
    val packageName: String? = null,   // "com.example.bnn"
    
    // For SMS type
    val smsNumber: String? = null,     // "+1-561-419-3784"
    val filterText: String = "",       // Optional keyword filter
    val isCaseSensitive: Boolean = false,
    
    // Configuration (independent per source)
    val autoClean: Boolean,            // Remove emojis/symbols?
    val templateId: String,            // Which JSON template to use
    val parserId: String,              // "bnn", "sms", "generic"
    val endpointId: String,            // Which endpoint to send to
    
    // Metadata
    val iconColor: Int,                // UI color
    val stats: SourceStats,            // Counters
    val createdAt: Long,
    val updatedAt: Long
)

enum class SourceType {
    APP,      // Android app notification
    SMS,      // SMS message
    EMAIL,    // Future: Email
    LOCATION, // Future: Location update
    CALL,     // Future: Phone call log
    CUSTOM    // Future: User-defined
}

data class SourceStats(
    val totalProcessed: Int = 0,
    val totalSent: Int = 0,
    val totalFailed: Int = 0,
    val lastActivity: Long = 0
)

data class Endpoint(
    val id: String,                    // "endpoint-001"
    val name: String,                  // "Google Sheets Production"
    val url: String,                   // https://script.google.com/...
    val enabled: Boolean,              // Toggle on/off
    val headers: Map<String, String> = emptyMap(),  // Future: Auth headers
    val retryCount: Int = 10,          // Max retries
    val timeout: Int = 30000           // Timeout in ms
)

data class Template(
    val id: String,                    // "rock-solid-bnn-format"
    val name: String,                  // "🪨 Rock Solid BNN Format"
    val content: String,               // JSON template string
    val mode: TemplateMode,            // APP or SMS
    val isRockSolid: Boolean,          // Can't be deleted
    val variables: List<String>        // ["{{id}}", "{{status}}", ...]
)

enum class TemplateMode {
    APP,
    SMS
}
```

---

### **System Flow (Unified)**

```
┌─────────────────────────────────────────────────────┐
│            EXTERNAL DATA SOURCES                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ BNN App  │  │   SMS    │  │ WhatsApp │  ...     │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘          │
└───────┼─────────────┼─────────────┼─────────────────┘
        │             │             │
        ▼             ▼             ▼
┌─────────────────────────────────────────────────────┐
│           CAPTURE LAYER (Receivers)                  │
│  ┌──────────────┐  ┌──────────────┐                │
│  │ Notification │  │     SMS      │                 │
│  │   Service    │  │   Receiver   │                 │
│  └──────┬───────┘  └──────┬───────┘                │
│         │                  │                         │
│         └──────────┬───────┘                         │
│                    ▼                                 │
│          ┌──────────────────┐                       │
│          │  SourceManager   │                       │
│          │  findSource()    │                       │
│          └────────┬─────────┘                       │
└───────────────────┼──────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────┐
│         PROCESSING LAYER (Business Logic)            │
│  ┌──────────────────────────────────────────────┐  │
│  │  Get Source Config:                          │  │
│  │    - autoClean? → TemplateEngine.cleanText() │  │
│  │    - parserId → Router.getParser()           │  │
│  │    - templateId → TemplateManager.get()      │  │
│  │    - endpointId → EndpointManager.get()      │  │
│  └────────────────┬─────────────────────────────┘  │
│                   ▼                                  │
│  ┌──────────────────────────────────────────────┐  │
│  │  Parser (BNN, SMS, Generic)                  │  │
│  │    → ParsedData or Raw JSON                  │  │
│  └────────────────┬─────────────────────────────┘  │
│                   ▼                                  │
│  ┌──────────────────────────────────────────────┐  │
│  │  Template Application                         │  │
│  │    → Final JSON Payload                      │  │
│  └────────────────┬─────────────────────────────┘  │
└───────────────────┼──────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────┐
│       PERSISTENCE LAYER (Queue & Retry)              │
│  ┌──────────────────────────────────────────────┐  │
│  │  QueueProcessor                              │  │
│  │    - Enqueue to SQLite                       │  │
│  │    - Retry with backoff (max 10x)            │  │
│  │    - Update LogRepository                    │  │
│  └────────────────┬─────────────────────────────┘  │
└───────────────────┼──────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────┐
│         NETWORK LAYER (HTTP Client)                  │
│  ┌──────────────────────────────────────────────┐  │
│  │  NetworkClient (OkHttp)                      │  │
│  │    - POST JSON to endpoint(s)                │  │
│  │    - Parallel multi-endpoint broadcast       │  │
│  └────────────────┬─────────────────────────────┘  │
└───────────────────┼──────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────┐
│           DESTINATION LAYER (Backends)               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │  Google  │  │ Firebase │  │  Custom  │  ...     │
│  │  Sheets  │  │ Firestore│  │  Webhook │          │
│  └──────────┘  └──────────┘  └──────────┘          │
└─────────────────────────────────────────────────────┘
```

---

## 🔧 **MIGRATION PLAN (V1 → V2)**

### **Phase 1: Data Migration (30 min)**

**Task:** Migrate existing SharedPreferences config to SourceRepository

```kotlin
// Migration script (one-time execution)
fun migrateV1ToV2(context: Context) {
    val sourceManager = SourceManager(context)
    
    // 1. Migrate SMS targets
    val smsTargets = PrefsManager.getSmsConfigList(context)
    smsTargets.forEach { target ->
        val source = Source(
            id = "sms:${target.phoneNumber}",
            type = SourceType.SMS,
            name = target.name,
            enabled = target.isEnabled,
            smsNumber = target.phoneNumber,
            filterText = target.filterText,
            isCaseSensitive = target.isCaseSensitive,
            autoClean = true,  // SMS default: clean emojis
            templateId = "rock-solid-sms-default",
            parserId = "sms",
            endpointId = "default-endpoint",
            iconColor = 0xFF00D980.toInt(),
            stats = SourceStats(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        sourceManager.saveSource(source)
    }
    
    // 2. Migrate app targets
    val targetApps = PrefsManager.getTargetApps(context)
    val pm = context.packageManager
    targetApps.forEach { packageName ->
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
        
        val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
        
        // Detect if BNN
        val isBnn = packageName.contains("bnn", ignoreCase = true)
        
        val source = Source(
            id = packageName,
            type = SourceType.APP,
            name = appName,
            enabled = true,
            packageName = packageName,
            autoClean = !isBnn,  // BNN doesn't need cleaning
            templateId = if (isBnn) "rock-solid-bnn-format" else "rock-solid-app-default",
            parserId = if (isBnn) "bnn" else "generic",
            endpointId = "default-endpoint",
            iconColor = if (isBnn) 0xFFA855F7.toInt() else 0xFF4A9EFF.toInt(),
            stats = SourceStats(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        sourceManager.saveSource(source)
    }
    
    // 3. Mark migration complete
    PrefsManager.setMigrationComplete(context, true)
}
```

**Execution:** Run on first launch after upgrade, or provide "Migrate Now" button in settings.

---

### **Phase 2: Update Receivers (1 hour)**

**Task:** Make NotificationService and SmsReceiver use SourceManager

**Before (Old Code):**
```kotlin
// NotificationService.kt
val targetApps = PrefsManager.getTargetApps(this)
if (targetApps.isNotEmpty() && !targetApps.contains(sbn.packageName)) {
    return
}
val shouldClean = PrefsManager.getShouldCleanData(this)
val template = PrefsManager.getAppJsonTemplate(this)
```

**After (New Code):**
```kotlin
// NotificationService.kt
val source = sourceManager.findSourceForNotification(sbn.packageName)
if (source == null) {
    LogRepository.addLog(LogEntry(
        packageName = sbn.packageName,
        title = "Notification Ignored",
        content = "Source not configured or disabled",
        status = LogStatus.IGNORED
    ))
    return
}

// Use source config
val shouldClean = source.autoClean
val template = templateManager.getTemplate(source.templateId)
val parser = parserManager.getParser(source.parserId)
val endpoint = endpointManager.getEndpoint(source.endpointId)
```

**Files to Update:**
1. `NotificationService.kt` (lines 102-180)
2. `SmsReceiver.kt` (lines 33-131)

---

### **Phase 3: UI Updates (1-2 hours)**

**Task:** Update all config activities to use SourceManager

**Changes:**

1. **SmsConfigActivity.kt**
   - Instead of `PrefsManager.saveSmsConfigList()`
   - Use `sourceManager.saveSource(smsSource)`
   - Show each SMS as a Source with toggle

2. **AppsListActivity.kt**
   - Instead of saving Set<String> of package names
   - Create Source object for each selected app
   - Allow per-app settings (auto-clean, template, parser)

3. **Dashboard (MainActivity.kt)**
   - Already using SourceManager ✓
   - Just needs refresh after migration

4. **New: SourcesManagementActivity**
   - Unified view of ALL sources (apps + SMS)
   - Edit, delete, toggle enable/disable
   - Configure per-source settings

---

### **Phase 4: Testing (2-3 hours)**

**Test Checklist:**

```
FUNCTIONAL TESTING:
├── SMS Reception
│   ├── [ ] Add SMS source
│   ├── [ ] Receive test SMS
│   ├── [ ] Verify auto-clean works
│   ├── [ ] Verify correct template used
│   └── [ ] Check Google Sheet for data
│
├── BNN Notifications
│   ├── [ ] Enable BNN source
│   ├── [ ] Receive BNN notification
│   ├── [ ] Verify parser extracts all fields
│   ├── [ ] Verify no auto-clean applied
│   └── [ ] Check Google Sheet for data
│
├── Generic App Notifications
│   ├── [ ] Enable WhatsApp source
│   ├── [ ] Receive WhatsApp notification
│   ├── [ ] Verify generic template used
│   └── [ ] Check Google Sheet for data
│
├── Multi-Endpoint
│   ├── [ ] Add 2 endpoints
│   ├── [ ] Send test notification
│   └── [ ] Verify sent to both
│
├── Offline Queue
│   ├── [ ] Turn off WiFi
│   ├── [ ] Send 5 test notifications
│   ├── [ ] Turn on WiFi
│   └── [ ] Verify all 5 sent
│
└── Dashboard
    ├── [ ] Verify accurate counts
    ├── [ ] Toggle source on/off
    └── [ ] Check logs

STABILITY TESTING (24 hours):
├── [ ] No crashes
├── [ ] Service stays alive
├── [ ] Memory stable
└── [ ] Battery acceptable
```

---

### **Phase 5: Merge to Main (15 min)**

```bash
# 1. Ensure all tests pass
cd D:\github\alerts-sheets

# 2. Commit all changes
git add .
git commit -m "feat: Complete V2 migration - Unified Source-based architecture"

# 3. Checkout main
git checkout main

# 4. Merge v2 branch
git merge feature/v2-clean-refactor --no-ff

# 5. Resolve conflicts (if any)
# Keep V2 code, delete V1 code

# 6. Test again on main branch
cd android
.\gradlew assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 7. Push to GitHub
git push origin main

# 8. Delete old branch
git branch -d feature/v2-clean-refactor
git push origin --delete feature/v2-clean-refactor
```

---

## 📦 **MINIMAL MOVING PARTS (Best Practices)**

### **Single Responsibility Principle**

```
SourceManager → Manages sources (CRUD)
ParserManager → Routes to correct parser
TemplateManager → Manages templates
EndpointManager → Manages endpoints
QueueProcessor → Handles retry logic
NetworkClient → Sends HTTP requests
LogRepository → Tracks activity
```

Each class does ONE thing, does it well.

---

### **Dependency Injection**

```kotlin
class NotificationService : NotificationListenerService() {
    // Injected dependencies (could use Hilt/Koin in future)
    private lateinit var sourceManager: SourceManager
    private lateinit var parserManager: ParserManager
    private lateinit var templateManager: TemplateManager
    private lateinit var queueProcessor: QueueProcessor
    
    override fun onCreate() {
        super.onCreate()
        sourceManager = SourceManager(applicationContext)
        parserManager = ParserManager()
        templateManager = TemplateManager(applicationContext)
        queueProcessor = QueueProcessor
    }
}
```

---

### **Repository Pattern (Already Implemented ✓)**

```
UI Layer → SourceManager → SourceRepository → JsonStorage
```

UI never touches storage directly.

---

### **Observer Pattern (For UI Updates)**

```kotlin
// LogRepository already has this
interface LogListener {
    fun onLogsChanged(logs: List<LogEntry>)
}

// Activity registers
LogRepository.addListener(this)

// Repository notifies
private fun notifyListeners() {
    listeners.forEach { it.onLogsChanged(logs) }
}
```

---

## 🚀 **EXTENSIBILITY (Future-Proof)**

### **Adding a New Data Source (e.g., WhatsApp)**

**ZERO core code changes required!**

1. **User enables WhatsApp in Sources UI**
2. **System creates Source object:**
```kotlin
val whatsappSource = Source(
    id = "com.whatsapp",
    type = SourceType.APP,
    name = "WhatsApp",
    enabled = true,
    packageName = "com.whatsapp",
    autoClean = true,
    templateId = "rock-solid-app-default",
    parserId = "generic",
    endpointId = "default-endpoint",
    // ...
)
sourceManager.saveSource(whatsappSource)
```
3. **Done!** NotificationService automatically picks it up.

---

### **Adding a New Destination (e.g., Firestore)**

**ZERO core code changes required!**

1. **User adds endpoint in UI:**
```kotlin
val firestoreEndpoint = Endpoint(
    id = "firestore-001",
    name = "Firebase Firestore",
    url = "https://us-central1-myproject.cloudfunctions.net/ingestAlert",
    enabled = true
)
endpointManager.save(firestoreEndpoint)
```
2. **Assign source to use it:**
```kotlin
bnnSource.copy(endpointId = "firestore-001")
sourceManager.saveSource(bnnSource)
```
3. **Done!** QueueProcessor sends to new endpoint.

---

### **Adding a New Parser (e.g., Custom Format)**

```kotlin
// 1. Create parser class
class CustomParser : Parser {
    override fun parse(text: String): ParsedData? {
        // Custom logic
    }
}

// 2. Register in ParserManager
ParserManager.register("custom", CustomParser())

// 3. Assign to source
source.copy(parserId = "custom")
```

**No changes to NotificationService, QueueProcessor, or any other core code!**

---

## 🎯 **FINAL SYSTEM CHARACTERISTICS**

### **Minimal Complexity**
- 7 core classes (down from 15+ in V1)
- 1 data model (Source) instead of 3 (SmsTarget, Endpoint, AppConfig)
- 1 storage system (SourceRepository) instead of 2 (SharedPrefs + SourceRepo)
- Clear, linear data flow

### **Maximum Extensibility**
- Add sources: Just create Source object
- Add destinations: Just create Endpoint object
- Add parsers: Just implement Parser interface
- Add templates: Just create Template object

### **Production Ready**
- Offline queue with retry
- Foreground service (unkillable)
- Comprehensive logging
- Per-source configuration
- Multi-endpoint broadcast

### **Testable**
- All business logic in managers (can mock)
- Parsers are pure functions
- Repository pattern (can mock storage)
- Clear interfaces (can swap implementations)

---

## 📅 **IMPLEMENTATION TIMELINE**

**Estimated Total:** 4-6 hours (one afternoon)

```
Hour 1: Data Migration Script
  - Write migration function
  - Test with sample data
  - Verify sources.json populated

Hour 2: Update Receivers
  - NotificationService uses SourceManager
  - SmsReceiver uses SourceManager
  - Test with real notifications

Hour 3: UI Updates Part 1
  - SmsConfigActivity refactor
  - AppsListActivity refactor
  
Hour 4: UI Updates Part 2
  - Dashboard updates (already mostly done)
  - Test all UI flows

Hour 5: Testing
  - Run functional test checklist
  - Fix any bugs found
  
Hour 6: Merge to Main
  - Final commit
  - Merge feature branch
  - Push to GitHub
  - Celebrate! 🎉

Next 24 hours: Stability testing
  - Monitor for crashes
  - Verify queue works
  - Check battery usage
```

---

## ✅ **SUCCESS CRITERIA**

**Before merging to main:**

1. ✅ Migration script runs without errors
2. ✅ All existing SMS targets migrated
3. ✅ All existing app targets migrated
4. ✅ Dashboard shows accurate counts
5. ✅ SMS notifications reach sheet
6. ✅ BNN notifications reach sheet with all fields
7. ✅ Per-source auto-clean works (BNN=off, SMS=on)
8. ✅ Multi-endpoint broadcast works
9. ✅ Offline queue + retry works
10. ✅ Logs show correct status
11. ✅ No crashes for 1 hour of testing
12. ✅ All UI flows work (add/edit/delete sources)

---

## 🎁 **BONUS: Code.gs Update Needed**

You mentioned the Code.gs file needs updating. Here's what needs to sync:

**Current Code.gs Issues:**
1. Handles BNN format (good)
2. Handles SMS format (good) 
3. BUT doesn't handle generic app format

**Fix:** Add handler for generic app notifications:

```javascript
// In Code.gs, after SMS handler (line 23)
if (data.source === "app" && !data.incidentId) {
  // Generic app notification (not BNN)
  return handleGenericApp(data, sheet);
}

function handleGenericApp(data, sheet) {
  const now = new Date();
  const formattedTime = Utilities.formatDate(
    now,
    Session.getScriptTimeZone(),
    "MM/dd/yyyy hh:mm:ss a"
  );
  
  const row = [
    "App Notification",           // Status
    formattedTime,                // Timestamp  
    `APP-${Date.now()}`,          // ID
    "",                           // State (blank)
    "",                           // County (blank)
    "",                           // City (blank)
    data.package || "",           // Address column shows package
    "Generic Notification",       // Type
    data.title || "",             // Details shows title
    data.bigText || data.text || ""  // Original body
  ];
  
  sheet.appendRow(row);
  
  return ContentService.createTextOutput(
    JSON.stringify({ result: "success", type: "app" })
  ).setMimeType(ContentService.MimeType.JSON);
}
```

**I'll update the Code.gs file in our repo, but YOU need to paste it into Google Apps Script editor.**

---

## 🚀 **READY TO EXECUTE?**

**I have:**
- ✅ Complete architecture plan
- ✅ Migration strategy
- ✅ Best practices design
- ✅ Minimal complexity approach
- ✅ Extensibility built-in
- ✅ Clear timeline

**Next step:**
**Should I start implementing the migration?** or do you want to review/adjust the plan first?

---

**This is the FINAL design. Once we execute this, you'll have a production-ready, extensible system that follows best practices and can easily add new data sources and destinations without touching core code.** 🎯

