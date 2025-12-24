# AlertsToSheets - Comprehensive Front-to-Back Audit
**Date:** December 23, 2025  
**Scope:** Runtime workflows, card wiring, concurrency analysis  
**Methodology:** Evidence-based code inspection with zero assumptions

---

## 📋 QUICK ACCESS

### Critical Documents (Read First)
1. **[COMPREHENSIVE_AUDIT_SUMMARY.md](COMPREHENSIVE_AUDIT_SUMMARY.md)** - Executive overview with action items
2. **[CONCURRENCY_RISK.md](CONCURRENCY_RISK.md)** - Priority fixes (P0, P1, P2)
3. **[WORKFLOW_MAP.md](WORKFLOW_MAP.md)** - Complete runtime flow traces

### Technical Deep Dives
4. **[CARD_WIRING_MATRIX.md](CARD_WIRING_MATRIX.md)** - All UI components, data bindings, side effects
5. **[SEQUENCE_DIAGRAMS.md](SEQUENCE_DIAGRAMS.md)** - Visual Mermaid diagrams for all workflows

---

## 🎯 AUDIT FINDINGS AT A GLANCE

### ✅ EXCELLENT
- **Firestore Ingest Isolation:** Fully isolated, safe to integrate
- **Thread Safety:** LogRepository, IngestQueue properly synchronized
- **Async Design:** No Main thread blocking (proper coroutine usage)
- **Single Convergence Point:** All notifications/SMS → `DataPipeline.process()`

### ❌ CRITICAL (P0 - Fix Before Production)
- **Duplicate Notification Delivery**
  - **File:** `AlertsNotificationListener.kt:72`
  - **Impact:** Duplicate rows in Google Sheets, inflated stats
  - **Fix:** Add deduplication with 5-second window
  - **Effort:** 1 hour

### ⚠️ HIGH (P1 - Fix Soon)
- **Source/Endpoint Edit Race Condition**
  - **Files:** `SourceRepository.kt:101`, `EndpointRepository.kt:98`
  - **Impact:** User edits can be lost during concurrent access
  - **Fix:** Migrate to per-entity files or optimistic locking
  - **Effort:** 4 hours

---

## 📊 WORKFLOW SUMMARY

### Notification Path
```
Android System
   ↓
AlertsNotificationListener.onNotificationPosted() [Line 72]
   ↓
DataPipeline.processAppNotification() [Line 176]
   ↓
DataPipeline.process() [Line 55] ← CONVERGENCE POINT
   ↓
[Parse → Template → Resolve Endpoints → Fan-out → Log]
   ↓
Google Sheets (multiple endpoints)
```

**Key File:** `android/app/src/main/java/com/example/alertsheets/services/AlertsNotificationListener.kt`

---

### SMS Path
```
Android System (SMS_RECEIVED_ACTION)
   ↓
AlertsSmsReceiver.onReceive() [Line 28]
   ↓
handleSms() [Line 47]
   ↓
DataPipeline.processSms() [Line 199]
   ↓
DataPipeline.process() [Line 55] ← SAME CONVERGENCE POINT
   ↓
[Same processing as Notification Path]
```

**Key File:** `android/app/src/main/java/com/example/alertsheets/services/AlertsSmsReceiver.kt`

---

### Email Path
**Status:** ❌ NOT IMPLEMENTED

**Evidence:**
- Only `ic_email` drawable references found (UI decoration)
- No `EmailReceiver` or `EmailListenerService`
- No email parsing logic
- No email-related permissions in manifest

**Conclusion:** Email icon is a stub for future expansion, no runtime capture

---

### Firestore Ingest Path (Milestone 1 - NOT YET INTEGRATED)
```
IngestTestActivity (Debug-only)
   ↓
IngestQueue.enqueue() [Line 92]
   ↓
IngestQueueDb (SQLite WAL) [Crash-safe persistence]
   ↓
processQueue() [Line 119] [AtomicBoolean lock]
   ↓
FirebaseAuth (get ID token)
   ↓
OkHttp POST to Cloud Function
   ↓
/ingest endpoint [functions/src/index.ts]
   ↓
Firestore write (idempotent by eventId)
```

**Key Files:**
- `android/app/src/main/java/com/example/alertsheets/data/IngestQueue.kt`
- `android/app/src/main/java/com/example/alertsheets/data/IngestQueueDb.kt`
- `functions/src/index.ts`

**Status:** ✅ **READY FOR INTEGRATION** (full isolation proven)

---

## 🗂️ CARD WIRING OVERVIEW

### Dashboard Cards
| Card | File | Data Model | Side Effects | Concurrency |
|------|------|------------|--------------|-------------|
| Lab | `MainActivity.kt:70` | None | Navigation | ✅ Safe |
| Permissions | `MainActivity.kt:75` | System API | Navigation | ✅ Safe |
| Activity Log | `MainActivity.kt:80` | LogRepository | Navigation | ✅ Safe |
| Test Harness (Debug) | `MainActivity.kt:87` | None | Navigation | ✅ Safe |
| Dynamic Sources | `MainActivity.kt:124` | Source (read) | Navigation | ✅ Safe |

### Management Activities
| Activity | Purpose | Data Model | Write Operations | Risk |
|----------|---------|------------|------------------|------|
| LabActivity | Create/Edit Sources | Source | Atomic file write | ⚠️ Race with stats |
| AppsListActivity | Bulk Add Apps | Source | Sequential writes | ✅ Safe |
| SmsConfigActivity | Add SMS Source | Source | Single write | ✅ Safe |
| EndpointActivity | Manage Endpoints | Endpoint | Atomic file write | ⚠️ Dangling refs |
| LogActivity | View Logs | LogEntry | Read-only | ✅ Safe |
| PermissionsActivity | Guide Setup | System API | Read-only | ✅ Safe |
| IngestTestActivity | Test Harness | IngestQueue | SQLite write | ✅ Isolated |

---

## 🔒 CONCURRENCY RISK MATRIX

| Shared Resource | Access Pattern | Lock Mechanism | Risk Level | Status |
|-----------------|----------------|----------------|------------|--------|
| sources.json | Read-modify-write | None (atomic file write) | MEDIUM | ⚠️ Fix recommended |
| endpoints.json | Read-modify-write | None (atomic file write) | MEDIUM | ⚠️ Fix recommended |
| templates.json | Read-modify-write | None (atomic file write) | LOW | ✅ Acceptable |
| LogRepository | Concurrent add/update | Synchronized | NONE | ✅ Thread-safe |
| IngestQueue | Concurrent enqueue/process | AtomicBoolean | NONE | ✅ Safe |
| IngestQueueDb | Concurrent SQL ops | SQLite WAL | NONE | ✅ Safe |
| DataPipeline instances | Independent scopes | N/A (stateless) | NONE | ✅ Safe |

---

## 🚀 INTEGRATION ROADMAP: FIRESTORE INGEST

### Phase 1: Current State (COMPLETE)
- ✅ IngestQueue implemented
- ✅ IngestQueueDb with WAL
- ✅ Cloud Function deployed
- ✅ Firestore Security Rules deployed
- ✅ E2E test harness available

### Phase 2: Integration (READY TO START)
**Prerequisites:**
- ✅ Isolation verified (no shared resources)
- ✅ Failure modes analyzed (cannot block existing delivery)
- ✅ Test harness passing all 4 scenarios

**Steps:**
1. Add `IngestQueue.enqueue()` call in `DataPipeline.process()` after successful parse
2. Implement kill switch (BuildConfig flag or Firebase Remote Config)
3. Wrap ingest call in try-catch to prevent exceptions from blocking HttpClient delivery
4. Log ingest success/failure separately from Sheets delivery
5. Deploy and monitor Firestore write success rate

**Code Change Location:**
```kotlin
// DataPipeline.kt Line 104 (after template application)
val json = TemplateEngine.apply(templateContent, parsedWithTimestamp, source)
logger.log("✓ Template applied (autoClean=${source.autoClean})")

// NEW: Enqueue to Firestore (dual-write)
if (BuildConfig.ENABLE_INGEST) {
    try {
        ingestQueue.enqueue(
            eventId = UUID.randomUUID().toString(),
            sourceId = source.id,
            payload = json
        )
        logger.log("✓ Queued for Firestore ingest")
    } catch (e: Exception) {
        // ✅ CRITICAL: Ingest failure must NOT block Sheets delivery
        logger.error("⚠️ Ingest enqueue failed: ${e.message}")
    }
}

// Continue with existing Sheets delivery (unchanged)
val endpoints = source.endpointIds...
```

### Phase 3: Verification (POST-INTEGRATION)
- Monitor Firestore write success rate (target: >99%)
- Confirm no impact on existing Sheets delivery latency
- Verify idempotency (duplicate submissions → single Firestore record)
- Test kill switch (disable ingest without app redeploy)
- Document rollback procedure

---

## 📝 CODE QUALITY OBSERVATIONS

### ✅ Best Practices
- Repository pattern consistently applied
- Coroutine scoping (no GlobalScope, proper SupervisorJob)
- Null-safe filtering (`.mapNotNull()` for dangling endpoint refs)
- Atomic file writes (temp file + rename)
- Crash recovery (IngestQueueDb.recoverFromCrash())

### ⚠️ Areas for Improvement
- No transaction support for file-based storage (race condition risk)
- No version field for optimistic locking
- Template operations in LabActivity not wrapped in coroutines (minor)
- No notification deduplication (critical)

---

## 📚 DOCUMENTATION STRUCTURE

```
AUDIT_INDEX.md (this file)
   ├── COMPREHENSIVE_AUDIT_SUMMARY.md (executive overview)
   ├── WORKFLOW_MAP.md (runtime flow traces)
   │   ├── Notification → Delivery
   │   ├── SMS → Delivery
   │   ├── Email (not implemented)
   │   ├── Firestore Ingest (isolated)
   │   └── Logging & Observability
   ├── CARD_WIRING_MATRIX.md (UI component analysis)
   │   ├── Dashboard Cards (12 cards)
   │   ├── Data Model Bindings
   │   ├── Side Effects
   │   └── Concurrency Matrix
   ├── CONCURRENCY_RISK.md (shared resources & race conditions)
   │   ├── Shared Resources Inventory
   │   ├── Race Condition Analysis
   │   ├── Blocking Operations Check
   │   ├── Ingest Path Isolation Proof
   │   └── Priority Fixes (P0, P1, P2)
   └── SEQUENCE_DIAGRAMS.md (Mermaid diagrams)
       ├── Notification → Delivery
       ├── SMS → Delivery
       ├── Lab Card Create/Edit → Pipeline
       ├── Firestore Ingest E2E
       └── Dashboard Card Lifecycle
```

---

## 🔍 METHODOLOGY

### Audit Approach
1. **No Assumptions:** All claims backed by file paths + line numbers
2. **Code Inspection:** Direct reading of source files, no speculation
3. **Evidence-Based:** Grep patterns, file reads, actual code snippets
4. **Risk Assessment:** Probability × Impact = Severity
5. **Actionable:** Concrete fixes with effort estimates

### Tools Used
- `grep` for pattern matching across codebase
- `read_file` for detailed code inspection
- `list_dir` for structure verification
- Manual timeline analysis for race conditions
- Mermaid for sequence diagram generation

---

## ✅ AUDIT COMPLETION CHECKLIST

- ✅ All runtime entrypoints traced
- ✅ All UI cards documented
- ✅ All data models mapped
- ✅ All side effects identified
- ✅ All shared resources inventoried
- ✅ All race conditions analyzed
- ✅ All blocking operations checked
- ✅ Firestore ingest isolation proven
- ✅ Sequence diagrams generated
- ✅ Priority fixes ranked
- ✅ Integration roadmap provided

---

## 📞 QUICK REFERENCE

### Critical Files
- **Notification Entry:** `AlertsNotificationListener.kt:72`
- **SMS Entry:** `AlertsSmsReceiver.kt:28`
- **Convergence Point:** `DataPipeline.kt:55` (`process()` function)
- **Ingest Entry:** `IngestQueue.kt:92` (`enqueue()` function)
- **Source Storage:** `SourceRepository.kt` → `sources.json`
- **Endpoint Storage:** `EndpointRepository.kt` → `endpoints.json`

### Key Constants
- **Log Repository:** Singleton, thread-safe, in-memory + SharedPreferences
- **DataPipeline Scope:** `Dispatchers.IO + SupervisorJob()`
- **IngestQueue Lock:** `AtomicBoolean(false)` (Line 44)
- **SQLite WAL:** Enabled at Line 77 in `IngestQueueDb.kt`

### Priority Contacts (Code Owners)
- **Notification/SMS Processing:** `DataPipeline.kt`
- **UI Cards:** `MainActivity.kt`, `LabActivity.kt`
- **Firestore Ingest:** `IngestQueue.kt`, `IngestQueueDb.kt`
- **Cloud Function:** `functions/src/index.ts`

---

**Audit Status:** ✅ COMPLETE  
**Next Action:** Address P0 (duplicate notification deduplication)  
**Integration Gate:** PASSED (Firestore ingest ready for dual-write)

---

**END OF AUDIT INDEX**

