# 🚨 CRITICAL LOGICAL CONTRADICTIONS FOUND

**Date:** December 21, 2025  
**Severity:** CRITICAL - Will cause production failures

---

## 🔴 **ISSUE #1: HARDCODED DEFAULT SOURCES (HIGHEST PRIORITY)**

### **Location:**
- `android/app/src/main/java/com/example/alertsheets/data/repositories/SourceRepository.kt:110-146`

### **Problem:**
```kotlin
private fun getDefaultSources(): List<Source> {
    return listOf(
        Source(id = "com.example.bnn", ...),      // ❌ HARDCODED BNN
        Source(id = "generic-app", ...),           // ❌ HARDCODED
        Source(id = "sms:dispatch", ...)           // ❌ HARDCODED SMS
    )
}
```

**This is called when:**
1. `sources.json` doesn't exist (first launch)
2. JSON parsing fails
3. **EVERY TIME** `getAll()` is called if storage is empty

### **User Impact:**
> "Monitoring 1 apps, 1 SMS" appears even when NO sources configured  
> User CANNOT remove BNN or Dispatch SMS  
> Sources persist through restarts because they're CODE, not data

### **Root Cause:**
The fallback `getDefaultSources()` is **ALWAYS** returned when storage is empty, creating phantom sources that:
- Can't be deleted (they regenerate on next read)
- Don't match actual configured sources
- Mislead dashboard statistics

---

## 🔴 **ISSUE #2: MIGRATION ONLY RUNS IF OLD DATA EXISTS**

### **Location:**
- `android/app/src/main/java/com/example/alertsheets/MigrationManager.kt:70-105`

### **Problem:**
```kotlin
if (smsTargets.isEmpty()) {
    Log.i(TAG, "No SMS targets to migrate")
    return  // ❌ EXITS EARLY
}
```

**If user has NO old PrefsManager data:**
- Migration completes instantly
- `sources.json` is NEVER created
- `SourceRepository.getAll()` → returns `getDefaultSources()`
- Dashboard shows "Monitoring 1 apps, 1 SMS" (hardcoded defaults)

### **User Impact:**
New installations or fresh starts will ALWAYS show phantom sources.

---

## 🔴 **ISSUE #3: RECEIVERS STILL USE OLD DATA AFTER MIGRATION**

### **Location:**
- `android/app/src/main/java/com/example/alertsheets/NotificationService.kt:103`
- `android/app/src/main/java/com/example/alertsheets/SmsReceiver.kt:33`

### **Problem:**
Migration runs once, but:
- `NotificationService` checks `sourceManager.getSourcesByType(APP)`
- `SmsReceiver` checks `sourceManager.getSourcesByType(SMS)`
- **BUT** old UI activities still write to PrefsManager!

### **Data Flow Contradiction:**
```
AppsListActivity → saves to PrefsManager.saveTargetApps()
NotificationService → reads from SourceManager.getSourcesByType()
❌ NEVER SYNCED!
```

### **User Impact:**
1. User opens App Sources page → saves selection to PrefsManager
2. Notification arrives → NotificationService reads SourceManager
3. **MISMATCH** → notification ignored or uses wrong config

---

## 🔴 **ISSUE #4: APPS LIST ACTIVITY SHOWS NO APPS**

### **Location:**
- `android/app/src/main/java/com/example/alertsheets/AppsListActivity.kt:80-116`

### **Problem:**
```kotlin
val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

if (showSystemApps) {
    if (!isSystemApp) continue  // Skip non-system
} else {
    if (isSystemApp) continue   // Skip system
}
```

**BNN might be flagged as system app** on some devices if:
- Installed via ADB
- Installed in system partition
- Has `FLAG_UPDATED_SYSTEM_APP`

### **User Issue:**
> "BNN app doesn't show up in list"

**Root Cause:** BNN has system flags, so when `showSystemApps=false`, it's filtered out.

---

## 🔴 **ISSUE #5: WHITE TEXT ON WHITE BACKGROUND IN SEARCH**

### **Location:**
- `android/app/src/main/res/layout/activity_apps_list.xml` (EditText styling)

### **Problem:**
EditText doesn't explicitly set text color, inheriting white from theme on white background.

---

## 🔴 **ISSUE #6: PAYLOAD PAGE RADIO BUTTON NOT PERSISTING CORRECTLY**

### **Location:**
- `android/app/src/main/java/com/example/alertsheets/AppConfigActivity.kt:254-264`

### **Problem:**
```kotlin
val lastMode = PrefsManager.getLastConfigMode(this) // "APP" or "SMS"
val isAppMode = (lastMode == "APP")

if (isAppMode) {
    radioGroupMode.check(R.id.radio_app)
} else {
    radioGroupMode.check(R.id.radio_sms)
}

loadConfig(isAppMode)  // ✅ Loads correct template
```

**BUT** the radio button check triggers `setOnCheckedChangeListener` (line 266-272), which:
1. Calls `loadConfig()` AGAIN
2. May load wrong template if listener fires before manual check completes

### **Race Condition:**
`onCreate` → check radio → listener fires → loads template → overrides previous load

---

## 🔴 **ISSUE #7: NO "DIRTY TEST" BUTTON**

### **User Request:**
> "Add a Dirty Test button that sends SMS with emojis"

Currently only one test button exists. Need second button for emoji-rich test.

---

## 🔴 **ISSUE #8: GOD MODE PERMISSIONS AMBIGUITY**

### **Manifest:**
```xml
<uses-permission android:name="android.permission.SEND_SMS" />
```

**User says:**
> "Not allowed: SEND_SMS, ROLE_SMS"

**Contradiction:** Manifest declares `SEND_SMS`, but user says not allowed.

### **Clarification Needed:**
- Does app need `SEND_SMS` for testing?
- Should it be removed from manifest?
- Is `ROLE_SMS` (default SMS app) required or not?

---

## 📋 **FIX PRIORITY ORDER:**

1. **CRITICAL (Fix NOW):**
   - Remove hardcoded defaults from `SourceRepository`
   - Ensure migration creates empty `sources.json` if no old data
   - Make AppsListActivity save to SourceManager (not PrefsManager)
   - Make SmsConfigActivity save to SourceManager (not PrefsManager)

2. **HIGH (Fix Today):**
   - Add "Dirty Test" button with emoji SMS
   - Fix search box text color
   - Fix BNN not showing in apps list (FLAG_UPDATED_SYSTEM_APP handling)

3. **MEDIUM (Fix This Week):**
   - Fix radio button race condition
   - Clarify SEND_SMS permission requirement

---

## 🎯 **ROOT CAUSE ANALYSIS:**

The V2 migration was **incomplete**:
- ✅ Receivers (NotificationService, SmsReceiver) → use SourceManager
- ✅ Dashboard (MainActivity) → uses SourceManager
- ❌ **UI Activities** → STILL use PrefsManager!
  - AppsListActivity → `PrefsManager.saveTargetApps()`
  - SmsConfigActivity → `PrefsManager.saveSmsConfigList()`
  - AppConfigActivity → `PrefsManager.getAppJsonTemplate()`

**The user writes to PrefsManager, but receivers read from SourceManager → DATA NEVER SYNCS!**

---

## ✅ **PROPOSED SOLUTION:**

### **Phase 1: Remove Hardcoded Defaults (15 min)**
```kotlin
// SourceRepository.kt
private fun getDefaultSources(): List<Source> {
    return emptyList()  // ✅ NO HARDCODED SOURCES
}
```

### **Phase 2: Create Empty Storage on First Run (5 min)**
```kotlin
// MigrationManager.kt
if (smsTargets.isEmpty() && targetApps.isEmpty()) {
    // First run or no old data - create empty sources.json
    sourceManager.saveSource(Source(...))  // Save at least empty list
}
```

### **Phase 3: Wire UI to SourceManager (1-2 hours)**
- `AppsListActivity` → save to `SourceManager.saveSource()`
- `SmsConfigActivity` → save to `SourceManager.saveSource()`
- Remove all `PrefsManager` references from UI activities

### **Phase 4: Add Dirty Test (15 min)**
Add button that sends:
```
🔥 New Fire Alert in Middlesex County
📍 31 Grand Avenue, Cedar Knolls, NJ 07927-1506, USA
...
```

---

## 🚀 **READY TO FIX?**

