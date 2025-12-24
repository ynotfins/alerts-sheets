# AlertsToSheets Documentation Index
**Last Updated:** December 23, 2025  
**Project:** Android notification/SMS capture → Google Sheets/Firestore webhook delivery  
**Status:** V2 Complete, Production-Hardening Roadmap Added

---

## 🚨 **NEW: PHASE 3 DETERMINISTIC AUDIT + CRM SCHEMA (DEC 23, 2025)**

**Priority: READ FOR WINDOWS-PROOF EVIDENCE + FIRESTORE CRM DESIGN**

### **Phase 3 Complete Deliverables**

1. **[PHASE_3_EXECUTION_SUMMARY.md](PHASE_3_EXECUTION_SUMMARY.md)** - Start here for overview
2. **[PHASE_3_DETERMINISTIC_PROOF.md](PHASE_3_DETERMINISTIC_PROOF.md)** - Manifest/UI/Email evidence (line-by-line)
3. **[EMAIL_STATUS.md](EMAIL_STATUS.md)** - Email implementation status + requirements (67 lines, 2-3 hours)
4. **[FIRESTORE_CRM_SCHEMA.md](FIRESTORE_CRM_SCHEMA.md)** - Address-centric CRM design (8 collections)
5. **[COMMAND_DRIVEN_AUDIT.md](COMMAND_DRIVEN_AUDIT.md)** - PowerShell call chain proof
6. **[SERENA_PREREQUISITES_TEST.md](SERENA_PREREQUISITES_TEST.md)** - Why Serena failed (Kotlin not indexed)

**All evidence:** Windows PowerShell native (no rg/aapt/jadx). 100% deterministic with file paths + line numbers.

---

## 📊 **COMPREHENSIVE SYSTEM ANALYSIS (PHASES 0-7)**

**Priority: READ THESE FIRST for production-hardening roadmap**

### **1. Executive Summary (START HERE)**
- **File:** [`_COMPREHENSIVE_ANALYSIS_EXECUTIVE_SUMMARY.md`](_COMPREHENSIVE_ANALYSIS_EXECUTIVE_SUMMARY.md)
- **Purpose:** Complete overview of all 7 phases, immediate next steps, 16-week roadmap
- **Key Findings:** Silent data loss risk, no disaster recovery, limited observability
- **Action:** Use as project plan for next 4 months

### **2. Phase 0: Ground Truth & Risk Assessment**
- **File:** [`_PHASE_0_GROUND_TRUTH_AND_RISK.md`](_PHASE_0_GROUND_TRUTH_AND_RISK.md)
- **Purpose:** How the app ACTUALLY works today (observed, not assumed)
- **Contents:**
  - Runtime entry points (Application, NotificationListener, SmsReceiver)
  - Data capture paths (App notifications, SMS)
  - Delivery paths (DataPipeline, fan-out, QueueProcessor)
  - Persistence mechanisms (JsonStorage, SharedPreferences, SQLite)
  - Risk assessment: P0 (silent data loss), P1 (missed alerts), P2 (duplicates)
- **Use For:** Understanding current system before making changes

### **3. Phase 1: Canonical Storage Strategy**
- **File:** [`_PHASE_1_CANONICAL_STORAGE.md`](_PHASE_1_CANONICAL_STORAGE.md)
- **Purpose:** Firestore as system of record, client/server boundaries
- **Contents:**
  - Canonical data strategy (Android = capture device, Server = source of truth)
  - Durability guarantees (network failure, app crash, server unavailability)
  - Firestore schema design (events, sources, endpoints, deliveryReceipts)
  - Security boundaries (what clients can/cannot write)
  - Write-once semantics (immutable events, mutable config)
- **Use For:** Implementing Firestore migration (Milestone 1)

### **4. Phases 2-7: Delivery, Fanout, Testing, Governance, Roadmap**
- **File:** [`_PHASE_3-7_FANOUT_STABILITY_TESTING_GOVERNANCE_ROADMAP.md`](_PHASE_3-7_FANOUT_STABILITY_TESTING_GOVERNANCE_ROADMAP.md)
- **Purpose:** Complete production-hardening strategy
- **Contents:**
  - **Phase 2:** Failure-aware delivery model (retry, backoff, observability)
  - **Phase 3:** Fanout responsibility split (server-side recommended)
  - **Phase 4:** Refactor readiness gates (zero data loss, full visibility)
  - **Phase 5:** Reality-based testing plan (no mock data for critical paths)
  - **Phase 6:** Invariants & contracts (event uniqueness, receipt completeness)
  - **Phase 7:** 16-week execution roadmap (5 milestones)
- **Use For:** Complete project plan, technical decisions, testing strategy

---

## 🚀 **NEW: MILESTONE 1 IMPLEMENTATION (IN PROGRESS)**

**Status:** ✅ E2E Harness Complete, ⏳ Testing Pending

### **5. Milestone 1 Progress**
- **File:** [`_MILESTONE_1_PROGRESS.md`](_MILESTONE_1_PROGRESS.md)
- **Purpose:** Track Milestone 1 implementation (Zero Data Loss)
- **Status:** Days 1-4 complete (infrastructure built)
- **Contents:**
  - Day 1-2: Firestore setup (Security Rules, Cloud Functions, schema)
  - Day 3-4: Client queue (IngestQueueDb, IngestQueue with retry)
  - Architecture overview (client → SQLite → Firestore)

### **6. Milestone 1 Test Harness** ⭐
- **File:** [`_MILESTONE_1_HARNESS_COMPLETE.md`](_MILESTONE_1_HARNESS_COMPLETE.md)
- **Purpose:** E2E validation harness (NO DataPipeline changes yet)
- **Status:** ✅ Built, ready for deployment + testing
- **Contents:**
  - Test harness UI (`IngestTestActivity.kt`)
  - 4 critical tests (Happy Path, Network Outage, Crash Recovery, Dedup)
  - Deployment checklist
  - Gate criteria (all tests must pass before integration)

### **7. Milestone 1 Test Runbook** 📋
- **File:** [`_MILESTONE_1_TEST_RUNBOOK.md`](_MILESTONE_1_TEST_RUNBOOK.md)
- **Purpose:** Step-by-step test procedures with pass/fail criteria
- **Contents:**
  - Setup instructions (one-time deployment)
  - Test 1: Happy Path (enqueue → ingest → Firestore)
  - Test 2: Network Outage (airplane mode → retry → success)
  - Test 3: Crash Recovery (kill app → restart → resume)
  - Test 4: Deduplication (same UUID twice → one record)
  - Troubleshooting guide
  - Verification steps (Firestore Console checks)

### **8. Milestone 1 Checkpoint**
- **File:** [`_MILESTONE_1_CHECKPOINT_DAY_4.md`](_MILESTONE_1_CHECKPOINT_DAY_4.md)
- **Purpose:** Day 4 checkpoint with decision point
- **Status:** Checkpoint reached, harness path chosen
- **Contents:**
  - Infrastructure deliverables (server + client)
  - Configuration requirements
  - Three integration options (recommended: test first)

---

## 📊 **NEW: COMPREHENSIVE FRONT-TO-BACK AUDIT (DECEMBER 23, 2025)**

**Priority: CRITICAL FINDINGS - READ IMMEDIATELY**

### **9. Audit Index (START HERE)** ⭐
- **File:** [`AUDIT_INDEX.md`](AUDIT_INDEX.md)
- **Purpose:** Quick access guide to all audit documents
- **Key Findings:** 1 critical bug (duplicate notifications), 1 high-priority race condition
- **Action Items:** P0 (deduplication), P1 (per-entity files), integration roadmap
- **Status:** ✅ Firestore ingest READY FOR INTEGRATION (isolation proven)

### **10. Comprehensive Audit Summary**
- **File:** [`COMPREHENSIVE_AUDIT_SUMMARY.md`](COMPREHENSIVE_AUDIT_SUMMARY.md)
- **Purpose:** Executive overview with priority fixes
- **Critical Findings:**
  - ❌ **P0 (CRITICAL):** Duplicate notification delivery → fix before production
  - ⚠️ **P1 (HIGH):** Source/Endpoint edit race conditions → data loss risk
  - ✅ **EXCELLENT:** Firestore ingest fully isolated, safe to integrate
- **Integration Gate:** PASSED (ready for dual-write)

### **11. Workflow Map** 📍
- **File:** [`WORKFLOW_MAP.md`](WORKFLOW_MAP.md)
- **Purpose:** Complete tracing of all runtime paths
- **Contents:**
  - Notification Capture → Delivery (full flow with line numbers)
  - SMS Capture → Delivery (converges with notification path)
  - Email Status: **NOT IMPLEMENTED** (icon stub only, no capture mechanism)
  - Firestore Ingest Pipeline (isolated, not integrated yet)
  - Delivery guarantees and failure modes
- **Evidence:** All claims cited with file paths + line numbers

### **12. Card Wiring Matrix** 🗂️
- **File:** [`CARD_WIRING_MATRIX.md`](CARD_WIRING_MATRIX.md)
- **Purpose:** All UI cards, data bindings, side effects
- **Contents:**
  - Dashboard permanent cards (Lab, Permissions, Logs, Test Harness)
  - Dynamic source cards (lazy-loaded from `sources.json`)
  - Management activities (LabActivity, AppsListActivity, SmsConfigActivity, EndpointActivity)
  - Data models (Source, Endpoint, Template, LogEntry, IngestQueueEntry)
  - Concurrency analysis per card
  - Independence matrix (shared resources, side effects)

### **13. Concurrency Risk Analysis** 🔒
- **File:** [`CONCURRENCY_RISK.md`](CONCURRENCY_RISK.md)
- **Purpose:** Shared resources, race conditions, blocking operations
- **Contents:**
  - 10 shared resources inventoried (files, singletons, databases)
  - 4 race conditions analyzed (with probability × impact)
  - Blocking I/O check (all on Dispatchers.IO ✅)
  - Firestore ingest isolation proof (no shared state with DataPipeline)

### **14. Ground Truth Baseline Audit (CURRENT WORKING TREE)** ⭐ **NEW**
- **File:** [`AUDIT_CRM_BASELINE.md`](AUDIT_CRM_BASELINE.md)
- **Purpose:** Complete read-only audit of current codebase (post all changes)
- **Contents:**
  - All runtime ingestion paths (Notification, SMS, Email confirmation)
  - Lab card workflows (Create → Persist → Render → Edit → Delete)
  - Outbound HTTP paths (Apps Script fan-out, Firebase /ingest debug-only)
  - Independence analysis with concrete file/line evidence
  - Email status: ✅ CONFIRMED UI-ONLY STUB (no runtime capture)
- **Statistics:** 17 files audited, ~3,500 lines analyzed, 100% evidence-based
- **Key Findings:**
  - ✅ Notifications + SMS fully operational
  - ❌ Email is UI stub only (SourceType enum has APP & SMS only)
  - ✅ Sources operate independently (per-source config verified)
  - ✅ Concurrent processing safe (SupervisorJob + local state)
  - ⚠️ Minor file lock delays (10-50ms, acceptable vs 200-1000ms network latency)
  - ✅ Firebase ingest debug-only (100% isolated from production)
  - Priority fixes (P0, P1, P2 with effort estimates)
- **Critical Finding:** Duplicate notification delivery (MEDIUM-HIGH severity)

### **14. Sequence Diagrams** 📈
- **File:** [`SEQUENCE_DIAGRAMS.md`](SEQUENCE_DIAGRAMS.md)
- **Purpose:** Visual Mermaid diagrams for all workflows
- **Contents:**
  - Notification Capture → Delivery (full participant flow)
  - SMS Capture → Delivery (converges at DataPipeline.process)
  - Lab Card Create/Edit → Pipeline Effects (atomic writes, eventual consistency)
  - Firestore Ingest Pipeline (SQLite WAL → Cloud Function → Firestore)
  - Dashboard Card Lifecycle (lazy loading, auto-refresh)
  - Convergence point analysis (single choke point at DataPipeline.process)

---

## 🎯 **RECOMMENDED READING ORDER**

**For implementing production-hardening:**
1. `_COMPREHENSIVE_ANALYSIS_EXECUTIVE_SUMMARY.md` (15 min read)
2. `_PHASE_0_GROUND_TRUTH_AND_RISK.md` (30 min read)
3. `_PHASE_1_CANONICAL_STORAGE.md` (20 min read)
4. `_PHASE_3-7_FANOUT_STABILITY_TESTING_GOVERNANCE_ROADMAP.md` (45 min read)
5. Start Milestone 1 implementation (Week 1-4)

**For understanding current V2 system:**
1. `README.md` (quick overview)
2. `ZERO_TRUST_ARCHITECTURE_ANALYSIS.md` (deep dive)
3. `SOURCE_ENDPOINT_WIRING_COMPLETE.md` (V2 implementation details)

---

## 📋 **QUICK START**

| For | Read This First |
|-----|-----------------|
| New developers | [`README.md`](README.md) |
| Setup & deployment | [`android/scripts/README.md`](android/scripts/README.md) |
| Samsung icon fix | [`docs/SAMSUNG_ICON_FIX.md`](docs/SAMSUNG_ICON_FIX.md) |
| Gradle build issues | [`GRADLE_FIX.md`](GRADLE_FIX.md) |
| MCP tool usage | [`MCP_QUICK_REFERENCE.md`](MCP_QUICK_REFERENCE.md) |

## 🏗️ **ARCHITECTURE & DESIGN**

**Note:** For production-hardening architecture, see **Phase 0-7 Analysis** above. The documents below cover the current V2 implementation.

### Current Architecture (V2)
- [`ZERO_TRUST_ARCHITECTURE_ANALYSIS.md`](ZERO_TRUST_ARCHITECTURE_ANALYSIS.md) - **Comprehensive analysis** (45KB, Dec 23)
- [`SOURCE_ENDPOINT_WIRING_COMPLETE.md`](SOURCE_ENDPOINT_WIRING_COMPLETE.md) - V2 wiring implementation
- [`UI_REDESIGN_COMPLETE.md`](UI_REDESIGN_COMPLETE.md) - OneUI design system

###Project Statistics (Verified Dec 23, 2025)
- **Kotlin Files:** 55
- **Total Lines of Code:** 8,149 lines
- **Files Over 200 Lines:** 15 files (27%)
- **Largest Files:** 
  - `AppConfigActivity.kt` (838 lines)
  - `LabActivity.kt` (805 lines)
  - `MigrationManager.kt` (377 lines)

---

## 🛠️ **MCP TOOLS & WORKFLOW**

### MCP Configuration (Verified Dec 23)
- [`TOOLS_INVENTORY.md`](TOOLS_INVENTORY.md) - Complete tool catalog (10 servers, 7 active, 70+ tools)
- [`MCP_OPTIMIZATION_SUMMARY.md`](MCP_OPTIMIZATION_SUMMARY.md) - Sequential Thinking integration & changes
- [`MCP_QUICK_REFERENCE.md`](MCP_QUICK_REFERENCE.md) - Quick lookup & decision tree
- [`mcp-optimization-analysis.md`](mcp-optimization-analysis.md) - Server relevance analysis for Android

### Current MCP Status:
- **Configured:** 10 servers
- **Active:** 7 (Sequential Thinking, Serena*, Context7, GitHub, Memory, Exa, Firestore)
- **Disabled:** 3 (Gmail, Google Super, Google Sheets - via UI toggle)
- **Note:** Serena requires Cursor restart to become active

---

## 🔧 **DEVELOPER GUIDES**

### Setup & Configuration
- [`DEVELOPER_SETTINGS_GUIDE.md`](DEVELOPER_SETTINGS_GUIDE.md) - Environment setup
- [`GRADLE_FIX.md`](GRADLE_FIX.md) - Build troubleshooting
- [`docs/SAMSUNG_ICON_FIX.md`](docs/SAMSUNG_ICON_FIX.md) - Fix duplicate launcher icons

### Testing & Verification
- [`VERIFICATION_CHECKLIST.md`](VERIFICATION_CHECKLIST.md) - On-device testing checklist

---

## 📐 **PROJECT SPECIFICATIONS**

### Android App Specifications
```yaml
compileSdk: 34
minSdk: 26
targetSdk: 34
versionCode: 1
versionName: "1.0"

Gradle: 8.7
Kotlin: 1.9.22
JVM: 17.0.16

Key Dependencies:
  - AndroidX Core KTX: 1.12.0
  - Material Design: 1.11.0
  - OkHttp: 4.12.0
  - Kotlinx Coroutines: 1.7.3
  - Gson: 2.10.1
```

### Architecture Pattern
- **Clean Architecture** (Presentation, Domain, Data, Infrastructure layers)
- **MVVM** for UI (Activities → ViewModels → Repositories)
- **Repository Pattern** for data access
- **Fan-out Delivery** (one event → multiple endpoints)

---

## 📊 **DATA MODELS**

### Core Entities
| Entity | File | Purpose |
|--------|------|---------|
| **Source** | `domain/models/Source.kt` | Notification/SMS capture configuration |
| **Endpoint** | `domain/models/Endpoint.kt` | Webhook delivery destination |
| **Template** | `domain/models/Template.kt` | JSON payload template |
| **LogEntry** | `LogEntry.kt` | Activity log entry |
| **RawNotification** | `domain/models/RawNotification.kt` | Captured notification data |
| **ParsedData** | `domain/models/ParsedData.kt` | Extracted structured data |

### Key Relationships
```
Source (1:N) endpointIds → Endpoint
Source (1:1) templateId → Template
Notification → Parser → ParsedData → TemplateEngine → JSON → HttpClient → Endpoint
```

---

## 🔍 **CODE NAVIGATION**

### Key Components by Layer

#### Presentation (UI)
```
ui/MainActivity.kt              - Dashboard & navigation
LabActivity.kt                  - Source creation wizard
AppConfigActivity.kt            - Template/endpoint management
AppsListActivity.kt             - App selection for monitoring
SmsConfigActivity.kt            - SMS source configuration
EndpointActivity.kt             - Endpoint CRUD
PermissionsActivity.kt          - Permission request flow
LogActivity.kt                  - Activity log viewer
```

#### Domain (Business Logic)
```
domain/DataPipeline.kt          - Core event processing & fan-out
domain/SourceManager.kt         - Source lifecycle management
domain/parsers/BnnParser.kt     - BNN-specific notification parsing
domain/parsers/GenericAppParser.kt - Generic notification parsing
domain/parsers/SmsParser.kt     - SMS message parsing
```

#### Data (Persistence & Repositories)
```
data/repositories/SourceRepository.kt   - Source CRUD
data/repositories/EndpointRepository.kt - Endpoint CRUD
data/repositories/TemplateRepository.kt - Template CRUD
data/storage/JsonStorage.kt             - File-based JSON persistence
LogRepository.kt                         - In-memory log storage
PrefsManager.kt                          - SharedPreferences facade (legacy)
```

#### Infrastructure (Services & Utils)
```
services/AlertsNotificationListener.kt  - NotificationListenerService
services/AlertsSmsReceiver.kt           - SMS BroadcastReceiver
services/BootReceiver.kt                - Boot event handler
utils/HttpClient.kt                     - OkHttp wrapper
utils/TemplateEngine.kt                 - JSON template rendering
utils/PayloadSerializer.kt              - Schema versioning
utils/Logger.kt                         - Debug logging
MigrationManager.kt                     - Data schema migrations
NetworkClient.kt                        - Legacy HTTP (deprecated)
QueueProcessor.kt                       - Delivery queue worker
```

---

## 🧪 **TESTING STATUS**

### Current Test Coverage
- **Unit Tests:** 0 files
- **Integration Tests:** 0 files  
- **UI Tests (Espresso):** 0 files

### Test Infrastructure
- JUnit: 4.13.2
- AndroidX Test: 1.1.5
- Espresso: 3.5.1

**Note:** Testing was identified as a gap during refactoring analysis. See `_DOCUMENTATION_CLEANUP_PLAN.md` for test implementation plan (16-27 hours estimated).

---

## 🔐 **SECURITY & CREDENTIALS**

### Credential Management (CRITICAL)
```
✅ functions/.env.local - Source of truth for all secrets (NOT committed)
✅ functions/.env - Generated deployment file (NOT committed)
❌ Never hardcode secrets in source files
❌ Never commit .env* files or service account JSON
```

### Required Secrets
```
BNN_SHARED_SECRET          - For BNN webhooks
FD_SHEET_ID                - Google Sheet ID for FD endpoint
GOOGLE_APPLICATION_CREDENTIALS_JSON - Firebase service account
```

---

## 📈 **PROJECT HISTORY**

### Major Milestones
- **V1** (Dec 17-19): Initial prototype with hardcoded endpoints
- **V2.0** (Dec 20): Clean Architecture refactor, fan-out delivery
- **V2.1** (Dec 21): Template management, SMS improvements
- **V2.2** (Dec 22): UI redesign (OneUI), Samsung icon fix
- **V2.3** (Dec 23): MCP optimization, Sequential Thinking integration

### Migration Path
```
V1 → V2.0: Refactored to Clean Architecture, added Source/Endpoint entities
V2.0 → V2.1: Added Template entity, JSON template engine
V2.1 → V2.2: UUID-based endpoint IDs, fan-out delivery
```

**MigrationManager.kt** handles automatic schema upgrades on app launch.

---

## 🚀 **DEPLOYMENT**

### Build & Deploy
```powershell
# Clean build
cd android
./gradlew clean assembleDebug

# Install to device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat -s AlertsApp:I Pipe:V Logs:V
```

### Deployment Scripts
See [`android/scripts/README.md`](android/scripts/README.md) for automated deployment workflows.

---

## 🗑️ **DEPRECATED DOCUMENTATION**

The following docs are **obsolete** and scheduled for deletion (see `_DOCUMENTATION_CLEANUP_PLAN.md`):

### Old Refactor Docs (Superseded by V2 - Dec 17)
- REFACTOR_MASTER_PLAN.md
- REFACTOR_EXECUTIVE_SUMMARY.md
- REFACTOR_DAY_1_CHECKLIST.md
- RESUME_TOMORROW.md
- STATE_BEFORE_RESTART.md
- RESTART_CHECKLIST.md

### Old Architecture Docs (Superseded - Dec 21)
- ARCHITECTURE_REPORT.md (use ZERO_TRUST_ARCHITECTURE_ANALYSIS.md instead)
- ARCHITECTURE_AUDIT_CRITICAL.md
- docs/architecture/* (mostly obsolete)
- docs/v2-refactor/* (entire folder obsolete)

### Old Validation Reports (Redundant - Dec 21)
- CODEBASE_VALIDATION_REPORT.md
- FULL_CODEBASE_VALIDATION_REPORT.md
- END_TO_END_VALIDATION_REPORT.md
- VALIDATION_SUMMARY.md
- LOGICAL_CONTRADICTIONS_REPORT.md
- CRITICAL_LOGICAL_ISSUES_FOUND.md

### Temporary/Session Docs (Outdated)
- SESSION_SUMMARY.md (Dec 21)
- HANDOFF_DEC_21_NIGHT.md
- STATUS_REPORT_DEC19.md
- systemPatterns.md (boilerplate)
- techContext.md (boilerplate)

### Build Logs (Temporary - Delete)
- android/build_*.txt (34 files)
- android/Eula.txt
- android/handle*.exe (debugging tools)

**Total for deletion:** ~80 files (see `_DOCUMENTATION_CLEANUP_PLAN.md` for complete list)

---

## 📚 **EXTERNAL DOCUMENTATION**

### Android SDK References
- [Android Developer Guide](https://developer.android.com/)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

### Library Documentation (via Context7 MCP)
```
// Get docs directly in Cursor
mcp_context7-mcp_get-library-docs(
  id="/kotlinx/coroutines",
  topic="Flow operators"
)
```

---

## 🔄 **KEEPING DOCS UPDATED**

### Update Triggers
- **Code changes:** Update ZERO_TRUST_ARCHITECTURE_ANALYSIS.md
- **New features:** Update README.md + feature-specific docs
- **MCP changes:** Update TOOLS_INVENTORY.md + MCP_OPTIMIZATION_SUMMARY.md
- **Build changes:** Update GRADLE_FIX.md or android/scripts/README.md

### Verification Checklist
```bash
# Verify file counts
Get-ChildItem -Path "android\app\src\main\java\com\example\alertsheets" -Recurse -Filter "*.kt" | Measure-Object

# Verify line counts
$files = Get-ChildItem -Path "android\app\src\main\java\com\example\alertsheets" -Recurse -Filter "*.kt"
$totalLines = 0
$files | ForEach-Object { $totalLines += (Get-Content $_.FullName | Measure-Object -Line).Lines }
Write-Output "Total lines: $totalLines"

# Verify MCP config
Get-Content "c:\Users\ynotf\.cursor\mcp.json" | jq '.mcpServers | keys'
```

---

## 🆘 **TROUBLESHOOTING**

### Common Issues
| Issue | Doc | Fix |
|-------|-----|-----|
| Duplicate launcher icons | `docs/SAMSUNG_ICON_FIX.md` | Samsung meta-data tags |
| Gradle lock errors | `GRADLE_FIX.md` | Kill processes, clean build |
| Permission denied | `DEVELOPER_SETTINGS_GUIDE.md` | Check permissions activity |
| BNN not visible | `AppsListActivity.kt:filterApps()` | Check system app filter |
| Failed delivery | `VERIFICATION_CHECKLIST.md` | Check LogActivity for errors |

---

## 📞 **SUPPORT**

For questions or issues:
1. Check this index for relevant documentation
2. Search MCP_QUICK_REFERENCE.md for tool usage
3. Review ZERO_TRUST_ARCHITECTURE_ANALYSIS.md for architecture questions
4. Check VERIFICATION_CHECKLIST.md for testing procedures

---

**Last Verified:** December 23, 2025  
**Next Review:** When major code changes occur  
**Maintainer:** Update this index when adding/removing documentation


