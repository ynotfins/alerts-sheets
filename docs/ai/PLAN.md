# IMPLEMENTATION PLAN - Observability Completion

**Created:** 2025-12-26  
**Target Branch:** `fix/wiring-sources-endpoints`  
**Planning Agent:** Cursor AI (Sequential Thinking Mode)

---

## 🎯 OBJECTIVE

Complete the observability stack implementation by adding:
1. **Structured Logging** with queryable tags
2. **Debug Screen** for delivery/queue inspection
3. **Test Crash Trigger** for Crashlytics verification

**Non-Goal:** Architecture changes, scope expansion, or breaking changes to existing delivery flow.

---

## 📋 PREREQUISITES

### ✅ Already Complete
- Firebase Crashlytics plugin + SDK
- Firebase Performance plugin + SDK
- LeakCanary (debug builds)
- Chucker HTTP inspector (debug builds)
- Detekt + Ktlint static analysis
- Sentry SDK (release builds, SDK-only)
- OkHttp logging interceptor

### ⚠️ Verification Needed
- [ ] Chucker actually intercepts HTTP calls
- [ ] LeakCanary notification appears
- [ ] Crashlytics can receive test crashes
- [ ] Detekt/Ktlint tasks run successfully

---

## 🏗️ IMPLEMENTATION PHASES

### Phase 1: Test Crash Trigger (P0)
**Estimated Effort:** 30 minutes  
**Risk:** Low (isolated change)

#### Tasks
1. **Add Test Crash Button to Lab**
   - File: `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`
   - Action: Add button to Lab card (debug builds only)
   - Implementation:
     ```kotlin
     if (BuildConfig.DEBUG) {
         btnTestCrash.setOnClickListener {
             FirebaseCrashlytics.getInstance().log("User triggered test crash")
             throw RuntimeException("Test crash from Lab button")
         }
     }
     ```

2. **Add Button to Layout**
   - File: `android/app/src/main/res/layout/activity_main_dashboard.xml`
   - Action: Add button inside Lab card (visibility controlled by BuildConfig)

3. **Verify Crashlytics Reception**
   - Build debug APK
   - Install on device
   - Trigger crash
   - Wait 5 minutes
   - Check Firebase Console → Crashlytics

#### Success Criteria
- ✅ Button only visible in debug builds
- ✅ Crash appears in Firebase Console within 5 minutes
- ✅ Stack trace includes "Test crash from Lab button"

---

### Phase 2: Structured Logging (P1)
**Estimated Effort:** 2-3 hours  
**Risk:** Medium (touches delivery flow)

#### Tasks

##### 2.1: Create StructuredLogger Utility
- **File:** `android/app/src/main/java/com/example/alertsheets/utils/StructuredLogger.kt`
- **Purpose:** Centralize all logging with consistent tag format
- **API Design:**
  ```kotlin
  object StructuredLogger {
      fun logDelivery(
          sourceId: String,
          endpointId: String,
          alertId: String,
          deliveryId: String,
          status: String,
          httpCode: Int? = null,
          latency: Long? = null,
          error: String? = null
      )
      
      fun logQueueState(
          alertId: String,
          state: String, // "enqueued", "sent", "failed"
          reason: String? = null
      )
      
      fun logPipeline(
          sourceId: String,
          stage: String, // "parse", "validate", "enqueue"
          alertId: String,
          success: Boolean,
          details: String? = null
      )
  }
  ```

- **Output Format:**
  ```
  [DELIVERY] sourceId=bnn_app endpointId=apps_script alertId=abc123 deliveryId=def456 status=success httpCode=200 latency=234ms
  [QUEUE] alertId=abc123 state=enqueued
  [PIPELINE] sourceId=bnn_app stage=parse alertId=abc123 success=true
  ```

##### 2.2: Integrate into Existing Code
- **Files to Modify:**
  - `android/app/src/main/java/com/example/alertsheets/utils/HttpClient.kt`
  - `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`
  - `android/app/src/main/java/com/example/alertsheets/data/QueueProcessor.kt`

- **Integration Points:**
  1. **HttpClient.post()** - Log delivery start/success/failure
  2. **DataPipeline.process()** - Log parse/validate/enqueue stages
  3. **QueueProcessor.processQueue()** - Log queue state changes

##### 2.3: Store Recent Logs for Debug Screen
- Extend existing `LogRepository` to store structured logs
- Keep last 50 deliveries + last 50 queue states in memory
- Persist to SharedPreferences (JSON array)

#### Success Criteria
- ✅ All HTTP deliveries logged with SOURCE_ID, ENDPOINT_ID, ALERT_ID, DELIVERY_ID
- ✅ All queue state changes logged
- ✅ Logs queryable via `adb logcat -s "DELIVERY:*" "QUEUE:*" "PIPELINE:*"`
- ✅ Recent logs persisted for Debug Screen

---

### Phase 3: Debug Screen (P1)
**Estimated Effort:** 3-4 hours  
**Risk:** Low (new code, no changes to existing)

#### Tasks

##### 3.1: Create DebugActivity
- **File:** `android/app/src/main/java/com/example/alertsheets/ui/DebugActivity.kt`
- **Layout:** `android/app/src/main/res/layout/activity_debug.xml`
- **Features:**
  - Two tabs: "Deliveries" | "Queue"
  - RecyclerView showing last 20 items
  - Pull-to-refresh
  - Export to clipboard button

##### 3.2: Delivery View (Tab 1)
- **Data Source:** `LogRepository.getRecentDeliveries(limit = 20)`
- **Columns:**
  - Timestamp
  - Source ID
  - Endpoint ID
  - Status (colored: green=success, red=error, yellow=pending)
  - HTTP Code
  - Latency (ms)
  - Error snippet (first 50 chars)

##### 3.3: Queue View (Tab 2)
- **Data Source:** `LogRepository.getRecentQueueStates(limit = 20)`
- **Columns:**
  - Timestamp
  - Alert ID
  - State (enqueued/sent/failed)
  - Reason (if failed)

##### 3.4: Wire Entry Point
- **Option A:** Add "Debug" tile to MainActivity (debug builds only)
- **Option B:** Add button to Lab card
- **Decision:** Use Option A (cleaner, follows existing pattern)

#### Success Criteria
- ✅ Debug tile only visible in debug builds
- ✅ Screen shows last 20 deliveries + queue states
- ✅ Data updates on pull-to-refresh
- ✅ Export to clipboard works

---

### Phase 4: Charles Proxy Documentation (P1)
**Estimated Effort:** 1 hour  
**Risk:** None (documentation only)

#### Tasks
1. **Create Guide:** `docs/CHARLES_PROXY_SETUP.md`
2. **Content:**
   - Install Charles on Windows 11
   - Configure Android device proxy
   - Install SSL certificate on device
   - Filter for `alerts-sheets-bb09c.cloudfunctions.net`
   - Troubleshooting common issues

#### Success Criteria
- ✅ Step-by-step guide with screenshots
- ✅ Works on Samsung OneUI devices
- ✅ Captures HTTPS traffic from app

---

## 🧪 TESTING STRATEGY

### Manual Testing Checklist

#### Test Crash Trigger
```bash
# 1. Build debug APK
cd android
./gradlew :app:assembleDebug

# 2. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Trigger crash
# - Open app
# - Navigate to Lab card
# - Tap "Test Crash" button
# - App should crash immediately

# 4. Verify in Firebase Console (wait 5 min)
# https://console.firebase.google.com/project/alerts-sheets-bb09c/crashlytics
```

#### Test Structured Logging
```bash
# 1. Send test notification
adb shell cmd notification post -S bigtext -t "BNN Alert" "Test Alert" com.example.alertsheets 1

# 2. Check Logcat for structured logs
adb logcat -s "DELIVERY:*" "QUEUE:*" "PIPELINE:*"

# Expected output:
# [PIPELINE] sourceId=bnn_app stage=parse alertId=... success=true
# [QUEUE] alertId=... state=enqueued
# [DELIVERY] sourceId=bnn_app endpointId=apps_script alertId=... status=success httpCode=200
```

#### Test Debug Screen
```bash
# 1. Open Debug tile
# 2. Verify deliveries shown
# 3. Switch to Queue tab
# 4. Pull to refresh
# 5. Tap "Export to Clipboard"
# 6. Paste in text editor - verify JSON format
```

#### Test Chucker
```bash
# 1. Send test notification
# 2. Pull down notification shade
# 3. Tap "Chucker" notification
# 4. Verify HTTP request to Cloud Functions visible
# 5. Check request headers + body
```

#### Test LeakCanary
```bash
# 1. Navigate through all activities multiple times
# 2. Wait 5 minutes
# 3. If leak detected, notification will appear
# 4. Tap notification to see leak trace
```

---

## 🚨 RISK MITIGATION

### Risk 1: Breaking Existing Delivery Flow
**Probability:** Low  
**Impact:** High  
**Mitigation:**
- Only add logging, don't modify existing logic
- Use try-catch around all logging calls
- Test with real BNN notifications before committing

### Risk 2: Performance Impact from Logging
**Probability:** Medium  
**Impact:** Low  
**Mitigation:**
- Use async logging (coroutines)
- Limit in-memory log storage to 50 items
- Make Debug Screen opt-in (not auto-started)

### Risk 3: Crashlytics Test Not Appearing
**Probability:** Medium  
**Impact:** Low  
**Mitigation:**
- Verify `google-services.json` present
- Check Firebase project ID matches
- Wait full 5 minutes (Crashlytics batches uploads)
- Check device internet connectivity

---

## 📦 DELIVERABLES

### Code Artifacts
1. `android/app/src/main/java/com/example/alertsheets/utils/StructuredLogger.kt`
2. `android/app/src/main/java/com/example/alertsheets/ui/DebugActivity.kt`
3. `android/app/src/main/res/layout/activity_debug.xml`
4. Modified: `MainActivity.kt`, `HttpClient.kt`, `DataPipeline.kt`, `QueueProcessor.kt`

### Documentation
1. `docs/CHARLES_PROXY_SETUP.md`
2. Updated: `CLAUDE.md` (add observability testing section)
3. Updated: `README.md` (add Gradle tasks for detekt/ktlint)

### Build Artifacts
1. `app-debug.apk` (with all observability tools)
2. `app-release.apk` (for final verification)

### Proof of Work
1. Screenshot: Test crash in Firebase Console
2. Screenshot: Chucker network inspection
3. Screenshot: Debug Screen showing deliveries
4. Terminal output: Structured logs from Logcat
5. Terminal output: `./gradlew detekt ktlintCheck` success

---

## 🔄 ROLLBACK PLAN

If implementation causes issues:

1. **Immediate:** Revert to commit `03699e8`
   ```bash
   git reset --hard 03699e8
   ./gradlew clean
   ./gradlew :app:assembleDebug
   ```

2. **Partial:** Remove only problematic feature
   - Test crash button: Remove from layout + MainActivity
   - Structured logging: Comment out StructuredLogger calls
   - Debug screen: Remove tile from MainActivity

3. **Nuclear:** Checkout clean branch
   ```bash
   git checkout -b observability-rollback
   git revert HEAD~3..HEAD  # Revert last 3 commits
   ```

---

## 📅 TIMELINE ESTIMATE

| Phase | Effort | Dependencies | Blocker Risk |
|-------|--------|--------------|--------------|
| Test Crash Trigger | 30 min | None | Low |
| Structured Logging | 2-3 hours | None | Medium |
| Debug Screen | 3-4 hours | Structured Logging | Low |
| Charles Docs | 1 hour | None | None |
| **TOTAL** | **6-8 hours** | - | - |

---

## ✅ DEFINITION OF DONE

### Phase 1: Test Crash Trigger
- [ ] Button visible only in debug builds
- [ ] Crash appears in Firebase Console
- [ ] Stack trace includes marker text
- [ ] No impact on release builds

### Phase 2: Structured Logging
- [ ] All HTTP deliveries tagged
- [ ] All queue states tagged
- [ ] Logs queryable via Logcat
- [ ] Recent logs persisted
- [ ] No performance degradation
- [ ] No crashes in production flow

### Phase 3: Debug Screen
- [ ] Activity created + wired to Dashboard
- [ ] Shows last 20 deliveries
- [ ] Shows last 20 queue states
- [ ] Pull-to-refresh works
- [ ] Export to clipboard works
- [ ] Only accessible in debug builds

### Phase 4: Charles Docs
- [ ] Guide created with screenshots
- [ ] Tested on Windows 11 + Android device
- [ ] Captures HTTPS traffic successfully

### Overall
- [ ] `./gradlew :app:assembleDebug` - SUCCESS
- [ ] `./gradlew :app:assembleRelease` - SUCCESS
- [ ] `./gradlew detekt` - No new issues
- [ ] `./gradlew ktlintCheck` - PASS
- [ ] Manual test checklist 100% complete
- [ ] All deliverables committed
- [ ] `CLAUDE.md` updated
- [ ] `STATE.md` updated

---

**This plan is ready for Execution Agent to implement. No architecture decisions required.**

