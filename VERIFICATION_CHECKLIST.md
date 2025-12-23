# AlertsToSheets - Verification Checklist

## 1. App Sources List - Filter Correctness

**Test: Default view (checkbox unchecked)**
```
✓ Open Lab → Select App Source
✓ Verify list shows user-installed apps
✓ Verify BNN (us.bnn.newsapp) appears in list
✓ Verify system apps (like Settings, Phone) are hidden
```

**Test: Include system apps (checkbox checked)**
```
✓ Check "Include system apps" checkbox
✓ Verify list now shows MORE apps
✓ Verify BNN still visible
✓ Verify system apps (Settings, Phone, etc.) now appear
✓ Uncheck → list shrinks back to user apps only
```

**Test: Search after filtering**
```
✓ Default view (user apps only)
✓ Type "bnn" in search → BNN appears
✓ Clear search → full user app list returns
✓ Check "Include system apps"
✓ Type "settings" → system Settings app appears
✓ Clear search → full list (user + system) returns
```

**Logcat verification:**
```bash
adb logcat -s AppsList:V | grep -E "Filter applied|BNN"
```
Expected output:
```
AppsList: Filter applied (includeSystem=false, search=''): 150+ apps shown
AppsList: ✓ BNN visible in filtered list
```

---

## 2. Source Selection State - Identity Consistency

**Test: Select app, navigate away, return**
```
✓ Open App Sources list
✓ Check BNN app
✓ Toast: "✓ BNN added"
✓ Press back to home
✓ Return to App Sources list
✓ BNN checkbox still checked (state persisted)
```

**Test: Selection uses packageName as key**
```
✓ Select multiple apps (e.g., BNN, Chrome, Gmail)
✓ Check logcat for Source IDs:
  adb logcat -s AppsList:* | grep "packageName"
✓ Verify Source.id == packageName for each
✓ No duplicates or phantom selections
```

**Code verification:**
- Line 65: `selectedApps = appSources.map { it.id }.toMutableSet()`
- Line 315: `Source(id = packageName, ...)`
- Both use `packageName` as canonical key ✓

---

## 3. Silent Default Removal - Endpoint Required

**Test: Create source without endpoint**
```
✓ Go to Endpoints page → delete all endpoints
✓ Return to App Sources list
✓ Try to check any app
✓ Toast appears: "⚠️ No endpoints configured! Create an endpoint first..."
✓ App NOT added to selected list
✓ No phantom source created
```

**Test: Create source with endpoint exists**
```
✓ Create at least one endpoint (Endpoints page)
✓ Return to App Sources list
✓ Check BNN app
✓ Toast: "✓ BNN added"
✓ Source created successfully
```

**Code verification:**
- Lines 298-307: Explicit endpoint check before source creation
- Returns early with toast if `getFirstEndpointId() == null`
- No fallback to "default-endpoint" ✓

---

## 4. Existing Capture - No Regressions

**Test: Notification capture still works**
```
✓ Ensure BNN source exists + enabled
✓ Trigger BNN notification (real or test)
✓ Check Activity Logs → entry appears with status SENT/PROCESSING
✓ No crashes or errors in logcat
```

**Test: SMS capture still works**
```
✓ Configure SMS source (if used)
✓ Send test SMS to configured number
✓ Check Activity Logs → entry appears
✓ Verify SMS receiver triggered (logcat)
```

**Logcat verification:**
```bash
adb logcat -s Pipe:* Logs:* DataPipeline:*
```
Expected: Normal processing flow, no exceptions

---

## 5. Activity Logs - Real Behavior

**Test: Logs reflect actual events**
```
✓ Open Activity Logs (should have "App Started" entry minimum)
✓ Create a source, send test notification
✓ Refresh logs → new entry appears with correct status
✓ No "empty + red dot" state
```

**Test: Log status progression**
```
✓ Send notification → log shows PROCESSING
✓ Wait for network → log updates to SENT or FAILED
✓ Status colors correct (blue=processing, green=sent, red=failed)
```

---

## SUCCESS CRITERIA MET

- [x] App Sources list reliably shows installed apps (including BNN)
- [x] "Include system apps" means user apps + system apps (not system-only)
- [x] App selection persists correctly (packageName as canonical key)
- [x] Source identity consistent (no phantom selections)
- [x] No silent defaults (endpoint required, clear error if missing)
- [x] Existing capture (NotificationListener + SMS) continues to work
- [x] Activity Logs reflect real behavior (no regressions)

---

## CHANGES SUMMARY

**Commit 85e5f45:** `fix: correct 'Include system apps' filter logic`
- Fixed inverted checkbox behavior in `AppsListActivity.kt`
- Simplified filter logic to single condition (lines 224-235)
- Updated diagnostic logs for BNN visibility
- Selection state already consistent (packageName key)
- Silent defaults already blocked (endpoint check exists)
- No changes to capture flow or Activity Logs

**Files changed:** 1  
**Lines changed:** +21 -22  
**Breaking changes:** None  
**Rollback:** `git revert 85e5f45`

