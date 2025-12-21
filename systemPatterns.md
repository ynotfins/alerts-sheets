# 🏗️ SYSTEM PATTERNS - AlertsToSheets Deep Structural Analysis

**Generated:** 2025-12-21  
**Codebase:** alerts-sheets (Android + Google Apps Script)  
**Analysis Depth:** Complete structural decomposition

---

## 📊 PROJECT OVERVIEW

### **Core Purpose**
Capture Android notifications and SMS messages → Transform to JSON → Send to Google Sheets via webhooks

### **Tech Stack**
- **Android:** Kotlin, Coroutines, OkHttp, Gson, AndroidX
- **Backend:** Google Apps Script (JavaScript)
- **Storage:** JSON files (local), SharedPreferences, Google Sheets
- **Build:** Gradle 8.7, Kotlin 1.9+, Target SDK 34

---

## 🏛️ ARCHITECTURAL LAYERS

### **Layer 1: Capture (Input Layer)**
```
┌─────────────────────────────────────┐
│  NOTIFICATION CAPTURE               │
├─────────────────────────────────────┤
│  AlertsNotificationListener.kt      │  ← NotificationListenerService
│  NotificationService.kt (Legacy)    │  ← Older implementation
│  AlertsSmsReceiver.kt               │  ← BroadcastReceiver for SMS
│  SmsReceiver.kt (Legacy)            │  ← V1 SMS handler
└─────────────────────────────────────┘
```

**Coupling Issues:**
- TWO notification services (AlertsNotificationListener + NotificationService)
- TWO SMS receivers (AlertsSmsReceiver + SmsReceiver)
- Both pairs run simultaneously (spaghetti risk)

---

### **Layer 2: Processing (Domain Layer)**
```
┌─────────────────────────────────────┐
│  SOURCE MANAGEMENT (V2)             │
├─────────────────────────────────────┤
│  SourceManager.kt                   │  ← Main orchestrator
│  SourceRepository.kt                │  ← JSON file persistence
│  Source.kt (data class)             │  ← Configuration model
│  SourceType: APP | SMS              │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  PARSING PIPELINE                   │
├─────────────────────────────────────┤
│  ParserRegistry.kt                  │  ← Parser factory
│  BnnParser.kt                       │  ← Fire alert specific
│  GenericAppParser.kt                │  ← Fallback for apps
│  SmsParser.kt                       │  ← SMS handler
│  Parser.kt (V1 Legacy)              │  ← Old monolithic parser
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  TEMPLATE ENGINE                    │
├─────────────────────────────────────┤
│  TemplateEngine.kt (utils/)         │  ← V2 version
│  TemplateEngine.kt (root)           │  ← V1 version
│  TemplateRepository.kt              │  ← Template storage
└─────────────────────────────────────┘
```

**Duplication Alert:**
- **2x TemplateEngine** (utils/TemplateEngine.kt + TemplateEngine.kt)
- **2x Parser** (domain/parsers/Parser.kt + Parser.kt)
- **2x ParsedData** (domain/models/ParsedData.kt + ParsedData.kt)

---

### **Layer 3: Transformation**
```
┌─────────────────────────────────────┐
│  DATA PIPELINE (Experimental)       │
├─────────────────────────────────────┤
│  DataPipeline.kt                    │  ← Clean V2 flow (incomplete)
│  TemplateEngine                     │  ← JSON template rendering
│  DeDuplicator.kt                    │  ← Prevents duplicate sends
└─────────────────────────────────────┘
```

---

### **Layer 4: Storage (Persistence Layer)**
```
┌─────────────────────────────────────┐
│  V2 REPOSITORIES (JSON Files)       │
├─────────────────────────────────────┤
│  SourceRepository.kt                │  → sources.json
│  TemplateRepository.kt              │  → templates.json
│  EndpointRepository.kt              │  → endpoints.json
│  JsonStorage.kt                     │  ← File I/O abstraction
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  V1 PREFERENCES (SharedPreferences) │
├─────────────────────────────────────┤
│  PrefsManager.kt                    │  → app_prefs_v2
│  Stores: endpoints, templates,      │
│  target apps, SMS config            │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  LOGS                               │
├─────────────────────────────────────┤
│  LogRepository.kt                   │  ← In-memory + SharedPrefs
│  Logger.kt                          │  ← File-based logger
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  QUEUE (Offline/Retry)              │
├─────────────────────────────────────┤
│  QueueDbHelper.kt                   │  ← SQLite database
│  QueueProcessor.kt                  │  ← Background retry
│  RequestEntity.kt                   │  ← Queue item model
└─────────────────────────────────────┘
```

**Critical Issue:**
- **DUAL STORAGE SYSTEM** (Repositories + PrefsManager)
- Some activities use PrefsManager, others use SourceManager
- Data inconsistency risk

---

### **Layer 5: Network (Output Layer)**
```
┌─────────────────────────────────────┐
│  HTTP CLIENT                        │
├─────────────────────────────────────┤
│  NetworkClient.kt                   │  ← OkHttp wrapper
│  HttpClient.kt                      │  ← Alternative impl (unused)
│  Endpoint.kt (V1)                   │  ← Data class
│  Endpoint.kt (domain/models/)       │  ← V2 Data class
└─────────────────────────────────────┘
```

**Duplication:**
- **2x Endpoint** model classes
- **2x HTTP clients** (NetworkClient vs HttpClient)

---

### **Layer 6: UI (Presentation Layer)**
```
┌─────────────────────────────────────┐
│  ACTIVITIES                         │
├─────────────────────────────────────┤
│  MainActivity.kt                    │  ← Dashboard (V2)
│  AppsListActivity.kt                │  ← App selection (V2)
│  SmsConfigActivity.kt               │  ← SMS config (V2)
│  AppConfigActivity.kt               │  ← Payload editor (V1)
│  EndpointActivity.kt                │  ← Endpoint config (V1)
│  PermissionsActivity.kt             │  ← Permission check (V1)
│  LogActivity.kt                     │  ← Activity logs (V1)
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  ADAPTERS                           │
├─────────────────────────────────────┤
│  AppsAdapter.kt                     │  ← RecyclerView for apps
│  SmsSourceAdapter.kt                │  ← RecyclerView for SMS (V2)
│  SmsTargetAdapter.kt                │  ← RecyclerView for SMS (V1)
│  EndpointsAdapter.kt                │  ← RecyclerView for endpoints
│  LogAdapter.kt                      │  ← RecyclerView for logs
└─────────────────────────────────────┘
```

**Inconsistency:**
- 3 activities use SourceManager (V2)
- 4 activities still use PrefsManager (V1)

---

## 🔄 DATA FLOW PATTERNS

### **Pattern A: Notification → Sheet (Current Flow)**
```
┌──────────────────┐
│  Notification    │
│  Arrives         │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│ AlertsNotification│
│ Listener.kt      │  ← NotificationListenerService.onNotificationPosted()
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Find Source     │  ← SourceManager.findSourceForNotification(packageName)
│  (if not found,  │
│   ignore)        │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Check Duplicate │  ← DeDuplicator.shouldProcess(content)
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Parse Data      │  ← Parser.parse() (BNN or Generic)
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Apply Template  │  ← TemplateEngine.apply()
│  (with auto-clean│
│   if enabled)    │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Send to         │  ← NetworkClient.sendJson()
│  Endpoints       │     (parallel to all enabled endpoints)
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Log Result      │  ← LogRepository.addLog()
│  Update Stats    │     SourceManager.updateStats()
└──────────────────┘
```

---

### **Pattern B: SMS → Sheet (Current Flow)**
```
┌──────────────────┐
│  SMS Arrives     │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  SmsReceiver.kt  │  ← BroadcastReceiver (Telephony.SMS_RECEIVED_ACTION)
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Get SMS Sources │  ← SourceManager.getSourcesByType(SMS)
│  (filter enabled)│
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Match Sender    │  ← Check phone number + optional filter text
│  to Source       │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Apply Template  │  ← TemplateEngine.applyGeneric()
│  Clean if needed │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Enqueue         │  ← QueueProcessor.enqueue()
│  to Send Queue   │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│  Send to Sheet   │  ← Background worker
└──────────────────┘
```

---

## 🚨 ARCHITECTURAL ISSUES

### **1. DUAL SYSTEM PROBLEM** (Critical)
**Impact:** Data inconsistency, confusing codebase

| Component | V1 (Legacy) | V2 (Current) | Status |
|-----------|-------------|--------------|--------|
| **Storage** | PrefsManager | SourceRepository | ⚠️ Both Active |
| **SMS Receiver** | SmsReceiver.kt | AlertsSmsReceiver.kt | ⚠️ Both Registered |
| **Notification** | NotificationService.kt | AlertsNotificationListener.kt | ⚠️ Both Running |
| **Parser** | Parser.kt | domain/parsers/*.kt | ✅ V2 Used |
| **Templates** | PrefsManager | TemplateRepository | ⚠️ Mixed Usage |

**Root Cause:** Incomplete migration from V1 to V2

---

### **2. CODE DUPLICATION** (High)

#### **Duplicated Files:**
```
utils/TemplateEngine.kt  ←→  TemplateEngine.kt
domain/models/Endpoint.kt  ←→  Endpoint.kt
domain/models/ParsedData.kt  ←→  ParsedData.kt
domain/parsers/Parser.kt  ←→  Parser.kt
services/BootReceiver.kt  ←→  BootReceiver.kt
```

**Risk:** Changes must be made twice, or bugs persist in one version

---

### **3. MISSING DEPENDENCY INJECTION** (Medium)
**Current Pattern:** Manual instantiation everywhere
```kotlin
val sourceManager = SourceManager(context)  // Repeated 15+ times
val templateRepo = TemplateRepository(context)  // Repeated 8+ times
```

**Impact:**
- Hard to test
- Tight coupling
- No interface abstractions

**Recommendation:** Consider Hilt/Koin for DI

---

### **4. GLOBAL SINGLETONS** (Medium)
**Objects using global state:**
- `LogRepository` (object) - in-memory list + SharedPrefs
- `DeDuplicator` (object) - in-memory cache
- `ParserRegistry` (object) - parser map
- `NetworkClient` (object) - OkHttp client

**Risk:** Thread safety, testing, memory leaks

---

### **5. COROUTINE LIFECYCLE ISSUES** (Fixed in Phase 1)
**Previously:** GlobalScope usage (memory leaks)  
**Now:** CoroutineScope + SupervisorJob + cancel() in onDestroy

✅ **Fixed in:** AppsListActivity, NotificationService

---

### **6. MIXED THREADING** (Medium)
**Inconsistent patterns:**
- Some use `suspend fun` + `withContext(Dispatchers.IO)`
- Some use `scope.launch(Dispatchers.IO)`
- Some use synchronous blocking calls on main thread

**File I/O on Main Thread:**
- `PrefsManager` reads (most methods)
- `JsonStorage` reads (some callers)

**Fix Applied:** SharedPreferences now on IO dispatcher in AppsListActivity

---

## 📦 MODULE STRUCTURE

### **Current Structure (Flat)**
```
com.example.alertsheets/
├── ui/
│   └── MainActivity.kt
├── domain/
│   ├── models/
│   ├── parsers/
│   └── SourceManager.kt, DataPipeline.kt
├── data/
│   ├── repositories/
│   └── storage/
├── services/
│   ├── AlertsNotificationListener.kt
│   └── AlertsSmsReceiver.kt
├── utils/
│   └── AppConstants.kt, Logger.kt, etc.
└── [54 root-level files] ← SPAGHETTI!
```

**Problem:** 54 files in root package (no clear module boundaries)

---

### **Recommended Structure (Clean)**
```
com.example.alertsheets/
├── app/
│   └── AlertsApplication.kt
├── ui/
│   ├── dashboard/
│   ├── apps/
│   ├── sms/
│   ├── config/
│   └── logs/
├── domain/
│   ├── models/
│   ├── usecases/
│   └── repositories/ (interfaces)
├── data/
│   ├── repositories/ (implementations)
│   ├── storage/
│   └── network/
├── capture/
│   ├── notification/
│   └── sms/
└── utils/
```

---

## 🔗 COUPLING ANALYSIS

### **High Coupling (Brittle Areas)**

#### **1. NotificationService.kt → 12 dependencies**
```kotlin
import com.example.alertsheets.PrefsManager
import com.example.alertsheets.TemplateEngine
import com.example.alertsheets.Parser
import com.example.alertsheets.DeDuplicator
import com.example.alertsheets.NetworkClient
import com.example.alertsheets.LogRepository
import com.example.alertsheets.MigrationManager
import com.example.alertsheets.domain.SourceManager
import com.example.alertsheets.data.repositories.TemplateRepository
// ...and more
```

**Impact:** Any change to these 12 classes requires recompiling NotificationService

---

#### **2. PrefsManager.kt → Used by 15+ files**
**Dependents:**
- EndpointActivity
- AppConfigActivity
- SmsReceiver
- NotificationService
- NetworkClient
- TemplateEngine
- MigrationManager
- ...and 8 more

**Impact:** PrefsManager is a **God Object** - changes ripple everywhere

---

### **Low Coupling (Good Design)**

#### **1. SourceRepository** 
✅ Only depends on: Context, JsonStorage, Gson  
✅ Used by: SourceManager only  
✅ Clear single responsibility

#### **2. Parsers**
✅ Each parser is independent  
✅ Registry pattern for lookup  
✅ Easy to add new parsers

---

## 🧪 TESTABILITY SCORE

| Component | Testable? | Reason |
|-----------|-----------|--------|
| **Parsers** | ✅ Yes | Pure functions, no Android deps |
| **TemplateEngine** | ✅ Yes | Static methods, deterministic |
| **SourceRepository** | ✅ Yes | Unit tests exist (25 tests) |
| **NetworkClient** | ⚠️ Partial | Mocking OkHttp needed |
| **NotificationService** | ❌ No | Android Service, 12 deps |
| **SourceManager** | ⚠️ Partial | Needs DI for testing |
| **PrefsManager** | ❌ No | Static object, SharedPrefs |

**Overall Score:** 4/10 (Needs DI + interfaces)

---

## 📊 METRICS

### **Codebase Stats**
- **Total Kotlin Files:** 60
- **Lines of Code:** ~8,500
- **Activities:** 7
- **Services:** 3 (2 notification + 1 unused)
- **Broadcast Receivers:** 3
- **Repositories:** 3
- **Parsers:** 3
- **Adapters:** 5

### **Duplication Rate**
- **Exact Duplicates:** 5 file pairs
- **Similar Code:** 12+ patterns (adapter boilerplate, coroutine setup)
- **Estimated Waste:** ~15% of codebase

### **Dependency Graph Complexity**
- **Max Dependencies (NotificationService):** 12
- **Most Depended On (PrefsManager):** 15+ files
- **Circular Dependencies:** 0 (Good!)

---

## 🎯 SYSTEM PATTERNS SUMMARY

### **Strengths** ✅
1. **Clean Domain Models** (Source, Template, Endpoint)
2. **Repository Pattern** (V2 is well-designed)
3. **Parser Registry** (extensible)
4. **Coroutine Lifecycle** (fixed in Phase 1)
5. **Constants Centralization** (AppConstants.kt)

### **Weaknesses** ⚠️
1. **Dual System (V1+V2 coexist)**
2. **No Dependency Injection**
3. **54 root-level files** (poor organization)
4. **5 duplicate file pairs**
5. **God Object (PrefsManager)**

### **Risks** 🚨
1. **Data Inconsistency** (V1 vs V2 storage)
2. **Race Conditions** (dual receivers/services)
3. **Memory Leaks** (global singletons)
4. **Tight Coupling** (NotificationService)
5. **Hard to Test** (no interfaces, DI)

---

**Next:** See `techContext.md` for tech debt analysis and refactor roadmap.

