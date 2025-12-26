# CLAUDE.MD - AlertsToSheets Project Guide

**Last Updated:** 2025-12-26  
**Project:** Alerts → Sheets Android App + Cloud Functions  
**Primary Tools:** Cursor AI with MCP Servers, Android Studio, Firebase

---

## 🎯 PROJECT OVERVIEW

**AlertsToSheets** is an Android notification/SMS listener that:
1. Captures notifications and SMS messages
2. Parses structured data using templates
3. Fan-outs HTTP POST to multiple endpoints (Google Apps Script, Firebase Functions)
4. Stores alerts in Firestore for CRM enrichment
5. Provides real-time dashboard monitoring

**Key Architectural Principles:**
- **Modularity:** Clean separation of UI, Domain, Data, Utils layers
- **Observability:** Comprehensive crash reporting, performance monitoring, network inspection
- **Zero Trust:** All secrets in `.env.local`, never hardcoded or committed
- **Dual-Write:** Apps Script (P0) + Firestore (P1) with feature flags

---

## 🔧 MANDATORY TOOL GATE (Run at Session Start)

Before any work, verify all tools are functional:

```powershell
# From project root (D:\github\alerts-sheets)

### 1. Git Status
git branch --show-current  # Should be: fix/wiring-sources-endpoints
git log -1 --oneline       # Latest commit

### 2. PowerShell
pwsh -Version              # 7.5.4+

### 3. Gradle (Android)
cd android
.\gradlew.bat --version    # Gradle 8.7, Kotlin 1.9.22, JVM 17

### 4. ADB (Android Debug Bridge)
adb version                # 36.0.0+

### 5. Firebase CLI
firebase --version         # 14.23.0+
firebase projects:list     # Verify alerts-sheets-bb09c

### 6. Node/npm (Cloud Functions)
node --version             # v22.18.0+
npm --version              # 11.7.0+
```

**MCP Server Status** (Cannot be verified via CLI):
- **Available:** Context7, Serena, Sequential Thinking, GitHub, Memory (Mem0), Exa, Firestore
- **Disabled:** Gmail, Google Sheets, Google Super (via Cursor UI)
- **Fallback:** If Serena indexing fails → PowerShell `Select-String` / `ripgrep`

---

## 📂 PROJECT STRUCTURE

```
D:\github\alerts-sheets\
├── android/                # Android Kotlin app
│   ├── app/
│   │   ├── src/main/java/com/example/alertsheets/
│   │   │   ├── ui/         # Activities, Fragments
│   │   │   ├── domain/     # Business logic
│   │   │   ├── data/       # Repositories, SQLite
│   │   │   ├── utils/      # Helpers
│   │   │   └── models/     # Data classes
│   │   ├── build.gradle     # App dependencies + plugins
│   │   └── google-services.json  # Firebase config (NEVER commit root-level)
│   ├── build.gradle         # Root plugins (Firebase, Crashlytics, Detekt, Ktlint)
│   ├── gradle.properties
│   ├── local.properties     # Secrets (SDK path, sentryDsn, etc.)
│   └── settings.gradle
├── functions/               # Firebase Cloud Functions (TypeScript)
│   ├── src/
│   │   ├── index.ts         # Main exports: ingest, enrichAlert, configGet
│   │   ├── enrichment.ts    # Firestore trigger for alert enrichment
│   │   └── utils/           # featureFlags, addressUtils
│   ├── .env.local           # 🔐 SOURCE OF TRUTH (never commit)
│   ├── .env                 # Generated deployment env (regenerate from .env.local)
│   ├── package.json
│   └── tsconfig.json
├── config/
│   └── detekt/
│       ├── detekt.yml       # Detekt static analysis config
│       └── baseline.xml     # Detekt baseline (generated)
├── .gitignore               # 🚨 CRITICAL: blocks .env*, .ai-context/, secrets
├── README.md
├── CLAUDE.md                # ← YOU ARE HERE
└── docs/                    # Additional guides
```

---

## 🛠️ DEVELOPMENT WORKFLOW

### Phase 0: Tool/MCP Health Check
Run the "Mandatory Tool Gate" section above. If any tool fails, consult `TOOL_FAILURE_RUNBOOK.md`.

### Phase 1: Plan (Use Sequential Thinking for Complex Tasks)
For tasks with >5 interconnected steps or requiring multi-file refactoring:
```
1. Invoke Sequential Thinking MCP to break down problem
2. Identify constraints, risks, dependencies
3. Generate solution options + trade-off analysis
4. Document decision rationale
```

### Phase 2: Navigate Code
**Primary:** Use Serena MCP for code intelligence:
```kotlin
// ✅ GOOD: Symbol-level navigation
serena.find_symbol("DataPipeline", include_body=true)
serena.find_referencing_symbols("DataPipeline", "android/app/src/...")

// ❌ BAD: Reading entire files blind
read_file("android/app/src/main/.../DataPipeline.kt")
```

**Fallback:** If Serena indexing fails:
```powershell
# PowerShell Select-String
Select-String -Path "android\**\*.kt" -Pattern "class DataPipeline"

# Or ripgrep (if available)
rg "class DataPipeline" android/
```

### Phase 3: Implement
- **Always check existing patterns** before adding new code
- **Feature flags for risky changes:** Use `BuildConfig.ENABLE_*` flags
- **Secrets management:** Never hardcode; load from `local.properties` or `.env.local`
- **Modular:** Separate UI, domain, data logic (no >20 line inline blocks in Activities)

### Phase 4: Test
```powershell
# Android builds
cd android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease

# Static analysis
.\gradlew.bat detekt ktlintCheck

# Deploy APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Verify on device
adb logcat -s "AlertsSheets:*" "*:E"
```

### Phase 5: Deploy
```powershell
# Cloud Functions
cd functions
npm run build
firebase deploy --only functions
```

### Phase 6: Improve
- Store lessons in Mem0 Memory MCP
- Update this CLAUDE.md if workflow changes
- Generate Detekt/Ktlint baselines if needed

---

## 🔐 SECRETS & CREDENTIALS (CRITICAL)

### Android (`android/local.properties`)
```properties
# SDK path (auto-generated by Android Studio)
sdk.dir=C:\\Users\\...\\AppData\\Local\\Android\\Sdk

# Sentry DSN (optional for debug)
sentryDsn=https://your-key@o0.ingest.sentry.io/0
```

### Cloud Functions (`functions/.env.local`)
```bash
# Apps Script Endpoints (for fan-out HTTP delivery)
BNN_SHARED_SECRET=your-secret-here
FD_SHEET_ID=https://...

# Google Service Account (for Firestore/Sheets access)
GOOGLE_APPLICATION_CREDENTIALS_JSON='{"type":"service_account",...}'

# Other secrets...
```

**Regenerate `.env` before deploy:**
```powershell
cd functions
# Manually copy .env.local → .env, removing FIREBASE_* and X_GOOGLE_* keys
firebase deploy --only functions
```

**🚨 NEVER COMMIT:**
- `.env*` files
- `google-services.json` (root-level)
- `service-account*.json`
- Any file matching patterns in `.gitignore`

---

## 🔍 OBSERVABILITY & DEBUGGING

### Tools Integrated

| Tool | Purpose | Build Type | Docs |
|------|---------|------------|------|
| **Firebase Crashlytics** | Crash reporting + NDK | debug + release | [DEBUGGING_OBSERVABILITY_PLAYBOOK.md](docs/DEBUGGING_OBSERVABILITY_PLAYBOOK.md) |
| **Firebase Performance** | Network + app perf traces | release | Same |
| **Sentry** | Error tracking (optional) | release (SDK-only) | Configure DSN in `local.properties` |
| **LeakCanary** | Memory leak detection | debug only | Auto-enabled |
| **Chucker** | HTTP inspector (on-device UI) | debug only | Notification appears after HTTP calls |
| **OkHttp Logging** | HTTP logs in Logcat | debug only | Filter: `OkHttp` |
| **Detekt** | Static analysis | CI + local | `.\gradlew.bat detekt` |
| **Ktlint** | Code formatting | CI + local | `.\gradlew.bat ktlintCheck ktlintFormat` |

### Debugging Checklist

1. **Check Logcat:**
   ```powershell
   adb logcat -s "AlertsSheets:*" "*:E"
   ```

2. **Check Chucker (Debug Builds):**
   - Pull down notification shade
   - Tap "Chucker" notification
   - Inspect HTTP requests/responses

3. **Check Firebase Crashlytics:**
   - https://console.firebase.google.com/project/alerts-sheets-bb09c/crashlytics
   - View stack traces + breadcrumbs

4. **Check Sentry (if enabled):**
   - https://sentry.io/organizations/your-org/issues/

5. **Check Firestore Console:**
   - https://console.firebase.google.com/project/alerts-sheets-bb09c/firestore/data
   - Verify `/alerts`, `/properties` collections

---

## 📦 GRADLE TASKS CHEAT SHEET

```bash
# Build
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease

# Install
.\gradlew.bat :app:installDebug

# Test
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest

# Static Analysis
.\gradlew.bat detekt
.\gradlew.bat detektBaseline  # Generate baseline
.\gradlew.bat ktlintCheck
.\gradlew.bat ktlintFormat    # Auto-fix formatting

# Lint
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:lintRelease

# Dependencies
.\gradlew.bat :app:dependencies
.\gradlew.bat --refresh-dependencies

# Clean
.\gradlew.bat clean
```

---

## 🧪 TESTING STRATEGY

### Manual Testing
1. **Permissions Tile:** Toggle dark mode, verify colors (orange/green/red/blue)
2. **Notification Capture:** Send test notification, check Logs tile
3. **SMS Capture:** Send test SMS, check Logs tile
4. **HTTP Fan-out:** Verify Apps Script + Firestore delivery (check Chucker)
5. **Firestore Write:** Use IngestTestActivity or curl to hit `/ingest` endpoint

### Automated (Future)
- Unit tests: `testDebugUnitTest`
- Instrumented tests: `connectedDebugAndroidTest`
- UI tests: Espresso + UIAutomator

---

## 🚨 COMMON ISSUES & FIXES

### Issue: "Plugin [id: 'X'] was not found"
**Fix:** Verify plugin is in `android/build.gradle` plugins block and repositories include `gradlePluginPortal()`.

### Issue: "Dependency 'X' requires compileSdk 35"
**Fix:** Downgrade dependency version OR upgrade AGP + compileSdk (see `build.gradle`).

### Issue: "Serena MCP Kotlin indexing failed"
**Fix:** Use PowerShell fallback:
```powershell
Select-String -Path "android\**\*.kt" -Pattern "YourClassName"
```

### Issue: "Firestore Console empty after write"
**Fix:**
1. Check Firestore Rules allow write
2. Check Cloud Function logs: `firebase functions:log`
3. Verify `/alerts` collection exists (may need to create manually first time)

### Issue: "APK crashes on launch"
**Fix:**
1. Check Logcat for stack trace: `adb logcat *:E`
2. Verify all `findViewById()` IDs exist in XML
3. Check for resource caching (clean uninstall + reinstall)

---

## 📚 ADDITIONAL DOCUMENTATION

- **[DOC_INDEX.md](DOC_INDEX.md)** - Master navigator for all docs
- **[README.md](README.md)** - Project overview + quick start
- **[TOOL_HEALTH_AND_FALLBACKS.md](TOOL_HEALTH_AND_FALLBACKS.md)** - MCP/Tool failure recovery
- **[FIRESTORE_CRM_SCHEMA.md](FIRESTORE_CRM_SCHEMA.md)** - Database design
- **[PHASE_4_DUAL_WRITE_IMPLEMENTATION.md](PHASE_4_DUAL_WRITE_IMPLEMENTATION.md)** - Dual-write architecture
- **[SECURITY_AUDIT_REPORT.md](SECURITY_AUDIT_REPORT.md)** - Security posture

---

## 🎓 MCP SERVER USAGE PRINCIPLES

### Context7 (Documentation)
```
✅ Auto-invoke for ANY library/framework question
❌ Don't use for project-specific code
```

### Serena (Code Intelligence)
```
✅ Use find_symbol, get_symbols_overview, find_referencing_symbols
✅ Use replace_symbol_body for whole-function edits
❌ Don't read entire files without exploring symbols first
```

### Sequential Thinking (Reasoning)
```
✅ Use for complex refactoring, architecture decisions, debugging
❌ Don't use for trivial tasks (<3 steps)
```

### Mem0 Memory (Context Persistence)
```
✅ Store user preferences, project conventions, repeated issue solutions
✅ Cite with [[memory:ID]] when using
❌ Don't store temporary task-specific info
```

### Exa Search (Web Intelligence)
```
✅ Use get_code_context_exa for code/API/library questions
✅ Use web_search_exa for general web research
```

### GitHub (Version Control)
```
✅ Use for creating/updating remote files, PRs, issues
❌ Don't use for local git operations (use CLI)
```

---

## 🏁 SESSION START CHECKLIST

- [ ] Run tool health gate (see "Mandatory Tool Gate" section)
- [ ] Verify branch: `fix/wiring-sources-endpoints`
- [ ] Check MCP server status (Context7, Serena, etc.)
- [ ] If Serena fails, confirm PowerShell `Select-String` fallback works
- [ ] Review open TODOs in AI context
- [ ] Check latest commit message for context

---

## 📞 CONTACTS & RESOURCES

- **Firebase Console:** https://console.firebase.google.com/project/alerts-sheets-bb09c
- **Sentry:** (Configure after obtaining DSN)
- **GitHub Repo:** https://github.com/ynotf/alerts-sheets (assumed)

---

**This file is living documentation. Update it when workflows change.**

