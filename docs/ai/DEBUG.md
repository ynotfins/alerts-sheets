# DEBUGGING PLAYBOOK - AlertsToSheets

**Last Updated:** 2025-12-31  
**Applies To:** Branch `fix/wiring-sources-endpoints`

---

## 🎯 PURPOSE

This document provides systematic debugging procedures for all observability tools integrated into the AlertsToSheets Android app. Use this when tools don't work as expected or when investigating production issues.

---

## 🧾 EVENT CATALOG (What to Look For in Logs)

This section is the **single source of truth for event names** emitted by the app’s pipeline and the Apps Script SMS handler.

### 1) Android: `StructuredLogger` NDJSON schema

- **Format**: one JSON object per line in Logcat (tag: `StructuredLogger`)
- **Fields** (typical):
  - `timestamp` (ISO8601)
  - `level` (`INFO|ERROR`)
  - `source_id`
  - `endpoint_id`
  - `alert_id`
  - `event` (**see event list below**)
  - `details` (free text)

Code reference: `android/app/src/main/java/com/example/alertsheets/utils/StructuredLogger.kt`.

### 2) Android: Delivery “Golden Path” events (`DeliveryPipeline` → `StructuredLogger`)

These are emitted by `android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt`.

- **Source matching**
  - `source_match_ok`: sender matched an enabled source
  - `source_ignored`: no matching source (or sender shape mismatch)
  - `source_disabled`: source matched but disabled

- **Endpoint selection**
  - `no_enabled_endpoints`: source had endpointIds, but none selectable (disabled/invalid URL)
  - `no_endpoint`: no endpointIds configured for the source

- **Payload render**
  - `payload_render_ok`: template rendered and passed placeholder checks
  - `payload_render_fail`: template render threw/failed (invalid template, etc.)
  - `payload_render_unresolved_placeholders`: rendered JSON still contained `{{...}}` placeholders (send blocked)

- **Auth guard (ingest endpoints)**
  - `auth_missing`: ingest endpoint requires secret but secret not configured (send blocked)

- **HTTP lifecycle**
  - `http_attempt`: HTTP POST attempted
  - `http_ok`: HTTP returned 2xx/ok (includes `code`, `latencyMs` in details)
  - `http_fail`: HTTP failed (code 0 or non-2xx, includes error class/message)

- **Test (“Dirty Test” / lab)**
  - `test_attempt_started`
  - `test_http_ok_confirmed` / `test_http_ok_unconfirmed`
  - `test_http_fail`

### 3) Android: Legacy / auxiliary events (`DataPipeline`)

These appear in `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`.

- `firestore_ingest_skipped`: ingest disabled by flag/config
- `ingest_enqueue_failed`: attempted to queue ingest but failed
- `http_exception`: exception thrown in old HTTP path
- `app_ignored`: app notification ignored by rules/template

### 4) Apps Script: SMS handler diagnostics (`Code.gs`)

These are **Apps Script `Logger.log()` markers** (not `StructuredLogger` events). They are emitted by the SMS-only path in `Code.gs`.

#### IncidentId extraction markers

- **`[SMS] incidentId_extracted`**
  - method=`adjustleads_url` | `fallback_digits` | `hash`
  - line snippet is digits-redacted (PII-safe)

#### Upsert suppression / anti-merge guards

- **`[SMS] upsert_suppressed`**
  - method was not `adjustleads_url` → **no upsert allowed**
- **`[SMS] id_match_non_sms_row_skipped`**
  - Column C matched incidentId, but Column A did not contain `SMS` → skip to avoid cross-type collisions
- **`[SMS] upsert_guard_block`**
  - Column C matched incidentId, but Column J did not contain the same `/alerts/<digits>` → block update to prevent accidental merges

#### Suggested quick-run harness

- Run `testSmsWiringDiagnostics()` inside Apps Script editor to print:
  - normalized timestamp outputs
  - incidentId + method
  - parsed state/county/city/address (NYC borough normalization)

---

## 🔍 TOOL-SPECIFIC DEBUGGING

### 1. Firebase Crashlytics

#### Problem: Crashes not appearing in Console
**Symptoms:** App crashes but nothing shows in Firebase Console after 5+ minutes

**Debug Steps:**
```bash
# 1. Verify Firebase project ID
cat android/app/google-services.json | grep project_id
# Expected: "alerts-sheets-bb09c"

# 2. Check Crashlytics plugin applied
grep "com.google.firebase.crashlytics" android/app/build.gradle
# Expected: apply plugin: 'com.google.firebase.crashlytics'

# 3. Check Crashlytics SDK dependency
grep "firebase-crashlytics-ktx" android/app/build.gradle
# Expected: implementation 'com.google.firebase:firebase-crashlytics-ktx'

# 4. Verify Crashlytics initialized (check logs)
adb logcat -s FirebaseCrashlytics:*
# Expected: "Crashlytics initialized successfully"

# 5. Check internet connectivity
adb shell ping -c 3 firebase.google.com

# 6. Force crash and wait 5 minutes (Crashlytics batches uploads)
# Then check: https://console.firebase.google.com/project/alerts-sheets-bb09c/crashlytics
```

**Common Fixes:**
- ✅ Wait full 5 minutes (uploads are batched)
- ✅ Ensure device has internet connectivity
- ✅ Verify `google-services.json` present in `android/app/`
- ✅ Clean + rebuild: `./gradlew clean assembleDebug`
- ✅ Check Firebase Rules allow writes

**Advanced Debugging:**
```bash
# Enable Crashlytics debug logging
adb shell setprop log.tag.FirebaseCrashlytics DEBUG
adb logcat -s FirebaseCrashlytics:*

# Check if crash reports are queued locally
adb shell ls /data/data/com.example.alertsheets/files/com.google.firebase.crashlytics/
```

---

### 2. Chucker (Network Inspector)

#### Problem: Chucker notification not appearing
**Symptoms:** HTTP calls made but no Chucker notification in status bar

**Debug Steps:**
```bash
# 1. Verify Chucker dependency (debug only)
grep "chucker" android/app/build.gradle
# Expected:
# debugImplementation 'com.github.chuckerteam.chucker:library:4.0.0'
# releaseImplementation 'com.github.chuckerteam.chucker:library-no-op:4.0.0'

# 2. Check Chucker integrated in OkHttp client
grep -A 10 "ChuckerInterceptor" android/app/src/main/java/com/example/alertsheets/utils/HttpClient.kt
# Expected: ChuckerInterceptor added as interceptor

# 3. Verify debug build (Chucker only works in debug)
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Check Chucker service running
adb logcat -s Chucker:*

# 5. Trigger HTTP call and check notifications
adb shell cmd notification post -S bigtext -t "Test" "Body" com.example.alertsheets 1
# Wait 2 seconds, pull down notification shade
```

**Common Fixes:**
- ✅ Only works in **debug builds** (not release)
- ✅ Requires notification permission (check Settings → Apps → AlertsSheets → Notifications)
- ✅ Verify `ChuckerInterceptor` added to OkHttp client
- ✅ Check if HTTP call actually executed (check Logcat)

**Manual Verification:**
```kotlin
// In HttpClient.kt, verify this code exists:
val client = OkHttpClient.Builder()
    .addInterceptor(ChuckerInterceptor.Builder(context).build()) // Debug builds only
    .build()
```

---

### 3. LeakCanary (Memory Leak Detection)

#### Problem: No leak notifications appearing
**Symptoms:** App used extensively but LeakCanary never notifies

**Debug Steps:**
```bash
# 1. Verify LeakCanary dependency (debug only)
grep "leakcanary" android/app/build.gradle
# Expected: debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.14'

# 2. Verify debug build
./gradlew :app:assembleDebug

# 3. Check LeakCanary installed
adb logcat -s LeakCanary:*
# Expected: "LeakCanary is running..."

# 4. Trigger known leak pattern
# - Open MainActivity
# - Navigate to AppConfigActivity
# - Press back
# - Repeat 5 times
# - Wait 60 seconds
```

**Common Fixes:**
- ✅ Only works in **debug builds**
- ✅ LeakCanary auto-hooks, no code changes needed
- ✅ Leaks detected only on heap dump (triggered automatically)
- ✅ No leaks = good! (silence is success)

**Force Heap Dump:**
```bash
# Manually trigger heap dump
adb shell am dumpheap com.example.alertsheets /sdcard/heap.hprof
adb pull /sdcard/heap.hprof .
# Analyze with Android Studio Memory Profiler
```

---

### 4. OkHttp Logging Interceptor

#### Problem: No HTTP logs in Logcat
**Symptoms:** HTTP calls made but nothing in Logcat

**Debug Steps:**
```bash
# 1. Verify logging interceptor dependency
grep "logging-interceptor" android/app/build.gradle
# Expected: debugImplementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

# 2. Check interceptor added to OkHttp client
grep -A 5 "HttpLoggingInterceptor" android/app/src/main/java/com/example/alertsheets/utils/HttpClient.kt
# Expected: HttpLoggingInterceptor added with level BODY

# 3. Check Logcat with correct filter
adb logcat -s OkHttp:*
# OR
adb logcat | grep -i okhttp

# 4. Trigger HTTP call
# Send test notification, check Logcat immediately
```

**Common Fixes:**
- ✅ Only works in **debug builds**
- ✅ Verify `HttpLoggingInterceptor` added to OkHttp client
- ✅ Check logging level set to `BODY` (not `NONE`)
- ✅ Use correct Logcat filter: `-s OkHttp:*`

**Manual Verification:**
```kotlin
// In HttpClient.kt, verify:
if (BuildConfig.DEBUG) {
    val logging = HttpLoggingInterceptor()
    logging.level = HttpLoggingInterceptor.Level.BODY
    builder.addInterceptor(logging)
}
```

---

### 5. Detekt (Static Analysis)

#### Problem: Detekt task fails or not found
**Symptoms:** `./gradlew detekt` fails or says "Task not found"

**Debug Steps:**
```bash
# 1. Verify Detekt plugin in root build.gradle
grep "detekt" android/build.gradle
# Expected: id 'io.gitlab.arturbosch.detekt' version '1.23.8' apply false

# 2. Verify Detekt applied in app/build.gradle
grep "detekt" android/app/build.gradle
# Expected: apply plugin: 'io.gitlab.arturbosch.detekt'

# 3. Check Detekt config exists
ls -l config/detekt/detekt.yml
# Expected: File exists

# 4. List available Detekt tasks
./gradlew tasks --group verification | grep detekt

# 5. Run Detekt with verbose output
./gradlew detekt --stacktrace --info
```

**Common Fixes:**
- ✅ Ensure plugin applied in **both** root and app `build.gradle`
- ✅ Config file path correct: `$rootDir/../config/detekt/detekt.yml`
- ✅ Generate baseline if needed: `./gradlew detektBaseline`
- ✅ Sync Gradle: `./gradlew --refresh-dependencies`

**Config Debugging:**
```bash
# Check Detekt configuration
cat android/app/build.gradle | grep -A 10 "detekt {"

# Expected:
# detekt {
#     buildUponDefaultConfig = true
#     config.setFrom(files("$rootDir/../config/detekt/detekt.yml"))
#     baseline = file("$rootDir/../config/detekt/baseline.xml")
# }
```

---

### 6. Ktlint (Code Formatting)

#### Problem: Ktlint task fails or not found
**Symptoms:** `./gradlew ktlintCheck` fails or says "Task not found"

**Debug Steps:**
```bash
# 1. Verify Ktlint plugin in root build.gradle
grep "ktlint" android/build.gradle
# Expected: id 'org.jlleitschuh.gradle.ktlint' version '12.1.2' apply false

# 2. Verify Ktlint applied in app/build.gradle
grep "ktlint" android/app/build.gradle
# Expected: apply plugin: 'org.jlleitschuh.gradle.ktlint'

# 3. List available Ktlint tasks
./gradlew tasks --group formatting | grep ktlint

# 4. Run Ktlint with verbose output
./gradlew ktlintCheck --stacktrace --info
```

**Common Fixes:**
- ✅ Ensure plugin applied in **both** root and app `build.gradle`
- ✅ Version compatibility: Ktlint 12.1.2 works with Gradle 8.7
- ✅ Sync Gradle: `./gradlew --refresh-dependencies`
- ✅ Auto-fix issues: `./gradlew ktlintFormat`

**Config Debugging:**
```bash
# Check Ktlint configuration
cat android/app/build.gradle | grep -A 15 "ktlint {"

# Expected:
# ktlint {
#     version = "1.0.1"
#     android = true
#     ignoreFailures = false
# }
```

---

### 7. Firebase Performance Monitoring

#### Problem: No performance traces in Console
**Symptoms:** App runs but no traces appear in Firebase Performance

**Debug Steps:**
```bash
# 1. Verify Performance plugin applied
grep "firebase-perf" android/app/build.gradle
# Expected: apply plugin: 'com.google.firebase.firebase-perf'

# 2. Check Performance SDK dependency
grep "firebase-perf-ktx" android/app/build.gradle
# Expected: implementation 'com.google.firebase:firebase-perf-ktx'

# 3. Check Performance initialized (release builds only by default)
adb logcat -s FirebasePerformance:*

# 4. Trigger HTTP call (auto-traced)
# Send test notification, wait 5 minutes

# 5. Check Console
# https://console.firebase.google.com/project/alerts-sheets-bb09c/performance
```

**Common Fixes:**
- ✅ Performance traces only enabled in **release builds** by default
- ✅ Wait 5+ minutes (data batched like Crashlytics)
- ✅ Verify internet connectivity
- ✅ Check Firebase Rules allow writes

**Enable in Debug Builds:**
```bash
# Add to AndroidManifest.xml:
# <meta-data
#     android:name="firebase_performance_logcat_enabled"
#     android:value="true" />
```

---

### 8. Sentry (Error Tracking)

#### Problem: Errors not appearing in Sentry
**Symptoms:** Crashes occur but nothing in Sentry dashboard

**Debug Steps:**
```bash
# 1. Verify Sentry SDK dependency
grep "sentry-android" android/app/build.gradle
# Expected: implementation 'io.sentry:sentry-android:7.18.1'

# 2. Check Sentry DSN configured
grep "SENTRY_DSN" android/local.properties
# Expected: sentryDsn=https://your-key@o0.ingest.sentry.io/0

# 3. Verify Sentry enabled (release builds only)
grep "ENABLE_SENTRY" android/app/build.gradle
# Expected:
# debug: buildConfigField "boolean", "ENABLE_SENTRY", "false"
# release: buildConfigField "boolean", "ENABLE_SENTRY", "true"

# 4. Check Sentry initialized (check app initialization code)
grep -r "Sentry.init" android/app/src/main/java/

# 5. Build release and test
./gradlew :app:assembleRelease
```

**Common Fixes:**
- ✅ Sentry only enabled in **release builds** (by design)
- ✅ DSN must be in `android/local.properties` (not hardcoded)
- ✅ Verify `Sentry.init()` called in `Application.onCreate()`
- ✅ Check Sentry project exists and DSN is correct

**Manual Init Check:**
```kotlin
// In AlertsApplication.kt or similar:
if (BuildConfig.ENABLE_SENTRY && BuildConfig.SENTRY_DSN.isNotEmpty()) {
    Sentry.init { options ->
        options.dsn = BuildConfig.SENTRY_DSN
        options.environment = BuildConfig.ENVIRONMENT
    }
}
```

---

## 🧪 SYSTEMATIC TESTING PROCEDURE

### Full Observability Stack Test
Run this procedure after any changes to observability configuration.

```bash
# ========================================
# PHASE 1: Build Verification
# ========================================
cd android

# 1. Clean build
./gradlew clean

# 2. Build debug
./gradlew :app:assembleDebug
# Expected: BUILD SUCCESSFUL

# 3. Build release
./gradlew :app:assembleRelease
# Expected: BUILD SUCCESSFUL

# 4. Run static analysis
./gradlew detekt ktlintCheck
# Expected: Both pass (or only baseline issues)

# ========================================
# PHASE 2: Debug Build Testing
# ========================================

# 5. Install debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 6. Start Logcat monitoring (separate terminal)
adb logcat -s "AlertsSheets:*" "FirebaseCrashlytics:*" "Chucker:*" "LeakCanary:*" "OkHttp:*"

# 7. Launch app
adb shell am start -n com.example.alertsheets/.ui.MainActivity

# 8. Check LeakCanary initialized
# Expected in Logcat: "LeakCanary is running"

# 9. Send test notification
adb shell cmd notification post -S bigtext -t "BNN Alert" "Test Body" com.example.alertsheets 1

# 10. Check Chucker notification appears
# Pull down notification shade, look for "Chucker" entry

# 11. Check OkHttp logs
# Expected in Logcat: HTTP request details (URL, headers, body)

# 12. Navigate to Lab → Trigger test crash (if button exists)
# App should crash immediately

# 13. Wait 5 minutes, check Firebase Console
# https://console.firebase.google.com/project/alerts-sheets-bb09c/crashlytics

# ========================================
# PHASE 3: Release Build Testing
# ========================================

# 14. Install release APK
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-unsigned.apk

# 15. Verify Chucker/LeakCanary NOT present
# - No Chucker notification should appear
# - No LeakCanary logs in Logcat

# 16. Trigger crash (via intentional bug or test mechanism)
# Wait 5 minutes, check both:
# - Firebase Crashlytics
# - Sentry dashboard (if DSN configured)

# ========================================
# PHASE 4: Performance Testing
# ========================================

# 17. Use app normally for 5 minutes
# - Send 10+ test notifications
# - Navigate through all activities
# - Rotate screen multiple times

# 18. Check Firebase Performance Console (wait 10 min)
# https://console.firebase.google.com/project/alerts-sheets-bb09c/performance
# Expected: HTTP traces, screen traces visible

# ========================================
# VERIFICATION CHECKLIST
# ========================================
# [ ] Debug build installs successfully
# [ ] Release build installs successfully
# [ ] Chucker notification appears (debug only)
# [ ] OkHttp logs visible (debug only)
# [ ] LeakCanary initialized (debug only)
# [ ] Test crash reaches Crashlytics (both builds)
# [ ] Performance traces visible (release, after 10 min)
# [ ] Sentry receives errors (release, if configured)
# [ ] No Chucker/LeakCanary in release builds
# [ ] App functions normally (no regressions)
```

---

## 🔧 BUILD TROUBLESHOOTING

### Problem: Build fails with "Plugin not found"
```bash
# Check plugin declarations in root build.gradle
cat android/build.gradle | grep "plugins {"

# Ensure all plugins listed:
# - com.android.application
# - org.jetbrains.kotlin.android
# - com.google.gms.google-services
# - com.google.firebase.crashlytics
# - com.google.firebase.firebase-perf
# - io.gitlab.arturbosch.detekt
# - org.jlleitschuh.gradle.ktlint

# If missing, add and sync:
./gradlew --refresh-dependencies
```

### Problem: Build fails with dependency conflicts
```bash
# Check for version conflicts
./gradlew :app:dependencies | grep FAILED

# Force dependency versions using Firebase BoM
# Already configured in build.gradle:
# implementation platform('com.google.firebase:firebase-bom:32.7.0')

# If still failing, clean and rebuild
./gradlew clean --refresh-dependencies
./gradlew :app:assembleDebug --stacktrace
```

### Problem: Build succeeds but app crashes on launch
```bash
# Check crash logs
adb logcat -s AndroidRuntime:E

# Common causes:
# 1. Missing google-services.json
# 2. Initialization errors in Application.onCreate()
# 3. Missing permissions in AndroidManifest.xml

# Verify google-services.json exists
ls -l android/app/google-services.json

# Check Application class
cat android/app/src/main/java/com/example/alertsheets/AlertsApplication.kt
```

---

## 📊 PERFORMANCE DEBUGGING

### Check APK Size Impact
```bash
# Compare APK sizes before/after observability tools
cd android/app/build/outputs/apk

# Debug build
ls -lh debug/app-debug.apk

# Release build
ls -lh release/app-release.apk

# Expected impact:
# - Chucker: +2 MB (debug only)
# - LeakCanary: +1 MB (debug only)
# - Firebase SDKs: +3 MB (both builds)
# - Sentry: +500 KB (both builds)
```

### Check Runtime Performance
```bash
# Use Android Profiler in Android Studio
# OR

# Command-line CPU profiling
adb shell am profile start com.example.alertsheets cpu_profile.trace
# Use app for 30 seconds
adb shell am profile stop com.example.alertsheets
adb pull /data/local/tmp/cpu_profile.trace .
# Open in Android Studio → Profiler → Load from file
```

---

## 🚨 EMERGENCY PROCEDURES

### Rollback to Pre-Observability State
```bash
# If observability tools break production:

# 1. Revert to last known good commit
git log --oneline | head -10
git checkout <commit-before-observability>

# 2. Remove observability dependencies (if needed)
# Edit android/app/build.gradle:
# - Comment out Crashlytics, Performance, Chucker, LeakCanary, Sentry
# - Remove plugin applications

# 3. Rebuild
./gradlew clean
./gradlew :app:assembleRelease

# 4. Test deployment
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Disable Individual Tools
```kotlin
// In android/app/build.gradle, add flags:
buildConfigField "boolean", "ENABLE_CRASHLYTICS", "false"
buildConfigField "boolean", "ENABLE_SENTRY", "false"
buildConfigField "boolean", "ENABLE_CHUCKER", "false"

// Then wrap initializations:
if (BuildConfig.ENABLE_CRASHLYTICS) {
    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
}
```

---

## 📞 SUPPORT & RESOURCES

### Firebase Console
- **Crashlytics:** https://console.firebase.google.com/project/alerts-sheets-bb09c/crashlytics
- **Performance:** https://console.firebase.google.com/project/alerts-sheets-bb09c/performance
- **Project Settings:** https://console.firebase.google.com/project/alerts-sheets-bb09c/settings

### Documentation
- **Firebase Crashlytics:** https://firebase.google.com/docs/crashlytics/get-started?platform=android
- **Chucker:** https://github.com/ChuckerTeam/chucker
- **LeakCanary:** https://square.github.io/leakcanary/
- **Detekt:** https://detekt.dev/
- **Ktlint:** https://pinterest.github.io/ktlint/

### Internal Docs
- `CLAUDE.md` - Workflow guide
- `DOC_INDEX.md` - Master documentation index
- `STATE.md` - Current project state
- `PLAN.md` - Implementation roadmap

---

**Keep this document updated as new issues are discovered and resolved.**

