# AlertsToSheets v2 - Clean Architecture Design

> **STATUS:** 🚧 IN DEVELOPMENT - DO NOT IMPLEMENT ON MASTER  
> **BRANCH:** `feature/v2-clean-refactor`  
> **PURPOSE:** Clean rebuild with per-source configuration and best practices  
> **SAFETY:** Master branch remains untouched for live BNN monitoring

---

## 🎯 **Design Goals**

1. **Per-Source Configuration**
   - Each source (BNN, SMS, Weather, etc.) has its own settings
   - No more "global" switches that affect everything
   - Easy to add new sources without touching existing code

2. **Clean Data Pipeline**
   - Clear flow: Capture → Identify → Parse → Transform → Queue → Send → Log
   - No spaghetti code
   - Easy to debug

3. **Zero Downtime**
   - Master branch keeps running
   - Test v2 on second phone
   - Switch only when proven stable

4. **Maintainability**
   - Follow Android best practices (MVVM, Repository pattern)
   - Clear separation of concerns
   - Easy to understand 6 months from now

---

## 🏗️ **Architecture Layers**

### **Layer 1: Presentation (UI)**
- Samsung One UI design (pure black, colorful icons)
- ViewBinding (no findViewById!)
- LiveData for reactive updates
- Material 3 components

### **Layer 2: Domain (Business Logic)**
- SourceManager: Central registry of all sources
- DataPipeline: Clean data flow
- Parser system: BNN, Generic, SMS parsers
- TemplateEngine: Apply variables to JSON templates

### **Layer 3: Data (Storage & Network)**
- Repositories: Clean abstraction for data access
- SharedPreferences: Simple settings
- JSON files: Complex objects (sources, templates)
- SQLite: Logs and queue
- HttpClient: Network requests

### **Layer 4: Services (Background)**
- NotificationListenerService: Captures app notifications
- SmsReceiver: Captures SMS messages
- BootReceiver: Auto-start after reboot

---

## 📊 **Key Data Models**

### **Source**
```kotlin
data class Source(
    val id: String,                    // "com.example.bnn" or "sms:+15551234567"
    val type: SourceType,              // APP or SMS
    val name: String,                  // "BNN Alerts"
    val enabled: Boolean = true,
    val templateId: String,            // Reference to template
    val autoClean: Boolean = false,    // Per-source auto-clean! ✨
    val parserId: String = "generic",  // "bnn", "generic", "sms"
    val endpointId: String,            // Which endpoint to use
    val iconColor: Int = 0xFF4A9EFF,  // For UI
    val stats: SourceStats = SourceStats()
)

enum class SourceType { APP, SMS }

data class SourceStats(
    val totalProcessed: Int = 0,
    val totalSent: Int = 0,
    val totalFailed: Int = 0,
    val lastActivity: Long = 0L
)
```

### **Template**
```kotlin
data class Template(
    val id: String,                    // UUID
    val name: String,                  // "Rock Solid BNN Default"
    val sourceId: String?,             // null = available for all sources
    val content: String,               // JSON template with {{variables}}
    val isRockSolid: Boolean = false,  // Immutable system templates
    val variables: List<String>        // ["{{package}}", "{{title}}", etc.]
)
```

### **Endpoint**
```kotlin
data class Endpoint(
    val id: String,                    // UUID
    val name: String,                  // "Google Sheets - Main"
    val url: String,                   // Full URL
    val enabled: Boolean = true,
    val timeout: Int = 30000,          // ms
    val retryCount: Int = 3,
    val headers: Map<String, String> = emptyMap()
)
```

---

## 🔄 **Data Flow: BNN Alert Example**

```
1. BNN posts notification
   ↓
2. NotificationListenerService.onNotificationPosted()
   ↓ captures StatusBarNotification
3. val raw = RawNotification(
     packageName = "com.example.bnn",
     title = "STRUCTURE FIRE",
     text = "123 Main St | Brooklyn | ...",
     timestamp = System.currentTimeMillis()
   )
   ↓
4. SourceManager.findSource("com.example.bnn")
   ↓ returns Source(id="com.example.bnn", enabled=true, parserId="bnn", autoClean=false)
5. Check: source.enabled? YES → Continue
   ↓
6. BnnParser.parse(raw)
   ↓ parses pipe-delimited text
7. ParsedData(
     incidentId = "#12345",
     state = "NY",
     county = "Kings",
     city = "Brooklyn",
     address = "123 Main St",
     incidentType = "STRUCTURE FIRE",
     fdCodes = ["FDNY-123", "ENGINE-4"],
     ...
   )
   ↓
8. TemplateRepository.getTemplate(source.templateId)
   ↓ returns Template with JSON content
9. TemplateEngine.apply(template, parsedData)
   ↓ replaces {{variables}}
10. Check: source.autoClean? NO (BNN doesn't need cleaning)
    ↓
11. val json = """
    {
      "incidentId": "#12345",
      "state": "NY",
      "county": "Kings",
      ...
    }
    """
    ↓
12. QueueRepository.enqueue(
      sourceId = "com.example.bnn",
      endpoint = source.endpointId,
      payload = json,
      timestamp = now()
    )
    ↓
13. HttpClient.post(
      url = endpoint.url,
      body = json,
      headers = endpoint.headers
    )
    ↓
14. Response: 200 OK { "result": "success", "id": "#12345" }
    ↓
15. LogRepository.insert(
      sourceId = "com.example.bnn",
      type = "SUCCESS",
      message = "Sent incident #12345",
      timestamp = now()
    )
    ↓
16. SourceRepository.updateStats(
      sourceId = "com.example.bnn",
      totalSent++,
      lastActivity = now()
    )
    ↓
17. LiveData emits update
    ↓
18. MainActivity updates dashboard card
    ✅ BNN card shows: "Last: just now | Total: 301"
```

---

## 🧩 **Parser System**

### **Base Interface**
```kotlin
interface Parser {
    val id: String
    val name: String
    fun canParse(source: Source, raw: RawNotification): Boolean
    fun parse(raw: RawNotification): ParsedData
}
```

### **BnnParser** (Port from v1)
```kotlin
class BnnParser : Parser {
    override val id = "bnn"
    override val name = "BNN Incident Parser"
    
    override fun canParse(source: Source, raw: RawNotification): Boolean {
        // BNN notifications have pipe-delimited format
        return raw.text.contains("|") && raw.packageName == "com.example.bnn"
    }
    
    override fun parse(raw: RawNotification): ParsedData {
        // Port existing BNN parser logic
        // Handles: NYC/Non-NYC, FD codes, addresses, etc.
        val parts = raw.text.split("|").map { it.trim() }
        
        return ParsedData(
            incidentId = extractIncidentId(parts),
            state = extractState(parts),
            county = extractCounty(parts),
            city = extractCity(parts),
            address = extractAddress(parts),
            incidentType = extractType(parts),
            incidentDetails = extractDetails(parts),
            fdCodes = extractFdCodes(parts),
            timestamp = TemplateEngine.getTimestamp(),
            originalBody = raw.fullText
        )
    }
}
```

### **GenericAppParser**
```kotlin
class GenericAppParser : Parser {
    override val id = "generic"
    override val name = "Generic App Parser"
    
    override fun canParse(source: Source, raw: RawNotification): Boolean {
        return source.type == SourceType.APP
    }
    
    override fun parse(raw: RawNotification): ParsedData {
        // Simple parsing for non-BNN apps
        return ParsedData(
            incidentId = "#${System.currentTimeMillis()}",
            state = "",
            county = "",
            city = "",
            address = "",
            incidentType = raw.title,
            incidentDetails = raw.text,
            fdCodes = emptyList(),
            timestamp = TemplateEngine.getTimestamp(),
            originalBody = raw.fullText
        )
    }
}
```

### **SmsParser**
```kotlin
class SmsParser : Parser {
    override val id = "sms"
    override val name = "SMS Parser"
    
    override fun canParse(source: Source, raw: RawNotification): Boolean {
        return source.type == SourceType.SMS
    }
    
    override fun parse(raw: RawNotification): ParsedData {
        return ParsedData(
            incidentId = "SMS-${System.currentTimeMillis()}",
            state = "",
            county = "",
            city = "",
            address = raw.sender ?: "Unknown",
            incidentType = "SMS Message",
            incidentDetails = raw.text,
            fdCodes = emptyList(),
            timestamp = TemplateEngine.getTimestamp(),
            originalBody = "From: ${raw.sender}\n${raw.text}"
        )
    }
}
```

---

## 🎨 **UI Design (Samsung One UI)**

### **MainActivity (Dashboard)**
```
┌─────────────────────────────────────┐
│ System Status            [LIVE]     │
│ ● Service Active                    │
│ Queue: Idle                         │
├─────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐        │
│  │  📱 Apps │  │ 💬 SMS   │        │
│  │  ● 301   │  │  ● 8     │        │
│  └──────────┘  └──────────┘        │
│  ┌──────────┐  ┌──────────┐        │
│  │ 🔧 Pay.  │  │ 📤 Endp. │        │
│  │  ●       │  │  ●       │        │
│  └──────────┘  └──────────┘        │
│  ┌──────────┐  ┌──────────┐        │
│  │ 🔒 Perms │  │ 📊 Logs  │        │
│  │  ●       │  │  ●       │        │
│  └──────────┘  └──────────┘        │
├─────────────────────────────────────┤
│ Monitoring: BNN, Dispatch SMS       │
└─────────────────────────────────────┘
```

### **Source Config Screen**
```
┌─────────────────────────────────────┐
│ ← BNN Alerts                        │
├─────────────────────────────────────┤
│ Name: BNN Alerts                    │
│ [                      ]            │
│                                     │
│ Enabled: [●───────────] ON          │
│                                     │
│ Template: 🪨 Rock Solid BNN Default │
│           ▼                         │
│                                     │
│ Parser: BNN Incident Parser         │
│         ▼                           │
│                                     │
│ Auto-Clean Emojis: OFF              │
│ [────○─────────]                    │
│ ℹ BNN doesn't need cleaning         │
│                                     │
│ Endpoint: Google Sheets - Main      │
│           ▼                         │
│                                     │
│ ╔═════════════════════════╗         │
│ ║ Stats                   ║         │
│ ║ Total Sent: 301         ║         │
│ ║ Total Failed: 0         ║         │
│ ║ Last Activity: 2m ago   ║         │
│ ╚═════════════════════════╝         │
│                                     │
│ [Test Notification]                 │
│ [Save Changes]                      │
└─────────────────────────────────────┘
```

---

## 📁 **File Structure**

```
android/app/src/main/java/com/example/alertsheets/
├── ui/
│   ├── MainActivity.kt
│   ├── sources/
│   │   ├── SourcesActivity.kt
│   │   └── SourceConfigActivity.kt
│   ├── payloads/
│   │   └── PayloadsActivity.kt
│   ├── endpoints/
│   │   └── EndpointsActivity.kt
│   ├── permissions/
│   │   └── PermissionsActivity.kt
│   └── logs/
│       └── LogsActivity.kt
│
├── domain/
│   ├── SourceManager.kt
│   ├── DataPipeline.kt
│   ├── parsers/
│   │   ├── Parser.kt
│   │   ├── BnnParser.kt
│   │   ├── GenericAppParser.kt
│   │   └── SmsParser.kt
│   └── models/
│       ├── Source.kt
│       ├── Template.kt
│       ├── Endpoint.kt
│       ├── ParsedData.kt
│       └── RawNotification.kt
│
├── data/
│   ├── repositories/
│   │   ├── SourceRepository.kt
│   │   ├── TemplateRepository.kt
│   │   ├── EndpointRepository.kt
│   │   ├── LogRepository.kt
│   │   └── QueueRepository.kt
│   └── storage/
│       ├── PrefsStorage.kt
│       ├── JsonStorage.kt
│       └── SqliteHelper.kt
│
├── services/
│   ├── AlertsNotificationListener.kt
│   ├── AlertsSmsReceiver.kt
│   └── BootReceiver.kt
│
└── utils/
    ├── TemplateEngine.kt
    ├── HttpClient.kt
    ├── Logger.kt
    └── Extensions.kt
```

---

## 🧪 **Testing Strategy**

### **Unit Tests**
- ✅ BnnParser.parse() - Various BNN formats
- ✅ GenericAppParser.parse()
- ✅ SmsParser.parse()
- ✅ TemplateEngine.apply() - Variable replacement
- ✅ SourceManager.findSource() - Matching logic
- ✅ DataPipeline stages

### **Integration Tests**
- ✅ Full pipeline with mock HTTP
- ✅ Queue system (offline/online)
- ✅ Source CRUD operations
- ✅ Template management

### **Manual Tests (Second Phone)**
- ✅ Deploy APK
- ✅ Configure BNN source
- ✅ Send test BNN notification
- ✅ Verify Google Sheet update
- ✅ Configure SMS source
- ✅ Send test SMS
- ✅ Test offline queue (airplane mode)
- ✅ 24-hour stability test

---

## 🚀 **Implementation Plan**

### **Phase 1: Core (Today)**
- [x] Architecture document
- [x] Branch created
- [ ] Data models (Source, Template, Endpoint, ParsedData)
- [ ] Basic repositories (storage interfaces)
- [ ] SourceManager skeleton

### **Phase 2: Parsing (Tomorrow)**
- [ ] Parser interface
- [ ] BnnParser (port v1 parser)
- [ ] GenericAppParser
- [ ] SmsParser
- [ ] TemplateEngine (port v1)

### **Phase 3: Services (Day 3)**
- [ ] NotificationListenerService (clean)
- [ ] SmsReceiver (clean)
- [ ] DataPipeline implementation
- [ ] HttpClient (port v1)
- [ ] Queue system (port v1)

### **Phase 4: UI (Days 4-5)**
- [ ] Port Samsung UI
- [ ] Source management screens
- [ ] Payloads screen
- [ ] Logs screen
- [ ] Permissions screen

### **Phase 5: Testing (Day 6)**
- [ ] Unit tests
- [ ] Build APK
- [ ] Deploy to second phone
- [ ] Parallel testing vs v1

### **Phase 6: Production (Day 7)**
- [ ] 24-hour stability test
- [ ] Deploy to main phone
- [ ] Monitor
- [ ] Merge to master

---

## ✅ **Success Criteria**

Before deploying to main phone:

1. ✅ All 300+ BNN alerts/day processed correctly
2. ✅ SMS from dispatch processed correctly
3. ✅ No duplicate sheet rows
4. ✅ Proper timestamp formatting
5. ✅ FD codes parsed correctly
6. ✅ Offline queue works
7. ✅ No crashes (24-hour test)
8. ✅ Permissions working (God Mode)
9. ✅ Auto-clean per-source only
10. ✅ Beautiful UI maintained

---

**SAFETY:** Master branch protected ✅  
**TESTING:** Second phone only ✅  
**ROLLBACK:** Easy (just uninstall v2) ✅  
**MONITORING:** Full logging ✅

---

*Last updated: Dec 19, 2025 - Phase 1 starting*
