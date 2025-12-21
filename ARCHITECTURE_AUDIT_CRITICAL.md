# 🚨 CRITICAL ARCHITECTURE AUDIT - AlertsToSheets v2

**Date:** December 20, 2025  
**Severity:** CRITICAL - System Split Between Two Architectures

---

## 🔴 **CRITICAL ISSUE: DUAL ARCHITECTURE CONFLICT**

The app is running **TWO PARALLEL SYSTEMS** that don't communicate:

### **SYSTEM A: V2 Source-Based Architecture** (NEW)
- **Location:** `domain/SourceManager.kt`, `data/repositories/SourceRepository.kt`
- **Storage:** `sources.json` (JsonStorage)
- **Used By:** Dashboard footer ("Monitoring: X Apps, Y SMS")
- **Features:**
  - Per-source settings (autoClean, templateId, parserId, endpointId)
  - Source statistics tracking
  - Proper OOP design with repositories
  
**Default Sources:**
1. `com.example.bnn` (APP) - enabled=true
2. `generic-app` (APP) - enabled=false  
3. `sms:dispatch` (SMS) - enabled=true

**PROBLEM:** This system is NOT being used by actual notification/SMS receivers!

---

### **SYSTEM B: V1 Prefs-Based Architecture** (OLD)
- **Location:** `PrefsManager.kt`
- **Storage:** SharedPreferences (`app_prefs_v2`)
- **Used By:**
  - `SmsReceiver.kt` → `PrefsManager.getSmsConfigList()`
  - `NotificationService.kt` → `PrefsManager.getTargetApps()`
  - `SmsConfigActivity.kt` → Adds/edits SMS targets
  - `AppsListActivity.kt` → Selects app packages
  
**SMS Config Keys:**
- `sms_config_list` → List<SmsTarget> (phone numbers + filters)
- `target_apps` → Set<String> (package names)
- `should_clean_data` → Boolean (GLOBAL auto-clean)

**PROBLEM:** This is what's actually receiving/processing messages, but dashboard doesn't see it!

---

## 📊 **WHAT'S ACTUALLY HAPPENING:**

```
USER ADDS SMS IN APP:
1. User taps SMS card → SmsConfigActivity
2. Adds "+1-561-419-3784" → PrefsManager.saveSmsConfigList()
3. Saves to SharedPreferences key "sms_config_list"

DASHBOARD READS:
4. MainActivity calls sourceManager.getSourcesByType(SMS)
5. SourceManager reads from sources.json
6. Returns hardcoded default: [sms:dispatch] (enabled=true)
7. Dashboard shows "Monitoring: 1 SMS" ← WRONG!

SMS ARRIVES:
8. SmsReceiver.onReceive() 
9. Reads PrefsManager.getSmsConfigList()
10. Finds "+1-561-419-3784" in list ✓
11. Processes and sends to sheet ✓

USER SEES:
- Dashboard: "1 SMS" (reading sources.json)
- SMS Config: "2 SMS targets" (reading SharedPreferences)
- **THEY DON'T MATCH!**
```

---

## 🐛 **ALL CONFIRMED BUGS EXPLAINED:**

### 1. **"Monitoring: 1 Apps, 1 SMS" Never Changes**
**Root Cause:** Dashboard reads `sources.json` which has hardcoded defaults.  
**Reality:** User's actual config is in SharedPreferences.

### 2. **Auto-Clean is Global Instead of Per-Source**
**Root Cause:** V1 system uses `PrefsManager.getShouldCleanData()` - single boolean for everything.  
**V2 Design:** Each Source has `autoClean` property, but it's not being used.

### 3. **No Logs Being Created**
**Root Cause:** Need to check if `LogRepository` is properly initialized and accessible from receivers.

### 4. **App Sources Only Shows 2 Apps**
**Root Cause:** Filter logic still broken OR no apps are actually being saved to `PrefsManager.target_apps`.

### 5. **Each App Needs Independent Settings**
**Current:** All apps use same global template and settings.  
**V2 Design:** Each Source should have its own templateId, parserId, endpointId, autoClean.  
**Reality:** V2 system exists but isn't wired up!

---

## ✅ **CORRECT ARCHITECTURE (What We Need):**

```
┌─────────────────────────────────────────────────────┐
│                  USER INTERFACE                      │
├─────────────────────────────────────────────────────┤
│  Dashboard  │  Apps  │  SMS  │  Config  │  Logs    │
└──────┬──────┴────┬───┴───┬───┴────┬─────┴────┬─────┘
       │           │       │        │          │
       ▼           ▼       ▼        ▼          ▼
┌──────────────────────────────────────────────────────┐
│              SOURCE MANAGER (Singleton)               │
│  ┌──────────────────────────────────────────────┐   │
│  │  getAll() / getById() / save() / delete()    │   │
│  │  findSourceForNotification(pkg)              │   │
│  │  findSourceForSms(sender)                    │   │
│  └──────────────────────────────────────────────┘   │
└───────────────────┬──────────────────────────────────┘
                    │
                    ▼
┌───────────────────────────────────────────────────────┐
│           SOURCE REPOSITORY                           │
│  Storage: sources.json                                │
│  ┌─────────────────────────────────────────────┐     │
│  │  List<Source>:                              │     │
│  │    - id: "com.example.bnn"                  │     │
│  │      type: APP                              │     │
│  │      packageName: "com.example.bnn"         │     │
│  │      enabled: true                          │     │
│  │      templateId: "rock-solid-bnn-format"    │     │
│  │      parserId: "bnn"                        │     │
│  │      autoClean: false                       │     │
│  │                                             │     │
│  │    - id: "sms:+15614193784"                 │     │
│  │      type: SMS                              │     │
│  │      smsNumber: "+15614193784"              │     │
│  │      name: "Emergency Services"             │     │
│  │      enabled: true                          │     │
│  │      templateId: "rock-solid-sms-default"   │     │
│  │      parserId: "sms"                        │     │
│  │      autoClean: true                        │     │
│  │      filterText: ""                         │     │
│  └─────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────┘
                    ▲
                    │
         ┌──────────┴──────────┐
         │                     │
    ┌────▼─────┐         ┌────▼──────┐
    │  NOTIF   │         │   SMS     │
    │ SERVICE  │         │ RECEIVER  │
    └──────────┘         └───────────┘
```

---

## 🔧 **MIGRATION PATH (Option 1 - Recommended):**

### **Phase 1: Migrate Old Data to New System**
1. Read `PrefsManager.getSmsConfigList()` → Convert to Source objects
2. Read `PrefsManager.getTargetApps()` → Convert to Source objects
3. Save all to SourceRepository
4. Delete old SharedPreferences keys

### **Phase 2: Update Receivers**
1. `SmsReceiver` → Use `SourceManager.findSourceForSms(sender)`
2. `NotificationService` → Use `SourceManager.findSourceForNotification(pkg)`
3. Both read source's `autoClean`, `templateId`, `parserId` properties

### **Phase 3: Update UI**
1. `SmsConfigActivity` → CRUD operations via SourceManager
2. `AppsListActivity` → Save selected apps as Source objects
3. Dashboard → Already using SourceManager ✓

---

## 🔧 **QUICK FIX (Option 2 - Temporary):**

### **Make Dashboard Read Old System:**
```kotlin
// MainActivity.kt - updateDashboardStatus()
val smsCount = PrefsManager.getSmsConfigList(this).filter { it.isEnabled }.size
val appCount = PrefsManager.getTargetApps(this).size

footerTicker.text = "Monitoring: $appCount Apps, $smsCount SMS • ..."
```

**Problem:** This doesn't fix the per-source settings issue!

---

## 📋 **RECOMMENDED ACTION PLAN:**

1. **IMMEDIATE (30 min):**
   - Fix dashboard to read from PrefsManager (Option 2)
   - This makes "Monitoring" count accurate

2. **SHORT TERM (2-3 hours):**
   - Migrate SMS config to SourceRepository
   - Update SmsReceiver to use SourceManager
   - Per-SMS auto-clean working

3. **MEDIUM TERM (4-6 hours):**
   - Migrate app config to SourceRepository
   - Update NotificationService to use SourceManager
   - Per-app templates and settings working

4. **LONG TERM (1-2 days):**
   - Build unified Sources management UI
   - Remove all PrefsManager dependencies
   - Full v2 architecture implemented

---

## 🎯 **WHAT THE USER WANTS:**

> "every json functions independently and the settings of another app or sms affect each one"

**This requires:**
- Each Source has its own:
  - ✓ `templateId` (already in Source model)
  - ✓ `parserId` (already in Source model)
  - ✓ `autoClean` (already in Source model)
  - ✓ `endpointId` (already in Source model)
  
**What's missing:**
- Receivers aren't using the Source model!
- They're still reading global settings from PrefsManager!

---

## 📊 **BEST PRACTICES VIOLATIONS:**

### ❌ **Current Issues:**

1. **Dual State Management**
   - Two sources of truth (sources.json + SharedPreferences)
   - No synchronization between them
   - Dashboard lies to user

2. **Global Settings Instead of Per-Entity**
   - `should_clean_data` is global
   - `json_template_app` and `json_template_sms` are global
   - Can't have BNN without cleaning + SMS with cleaning

3. **Tight Coupling**
   - Receivers directly access PrefsManager
   - No dependency injection
   - Hard to test or swap implementations

4. **Inconsistent Data Models**
   - `SmsTarget` (old) vs `Source` (new) with SMS type
   - `Set<String>` for apps vs proper Source objects
   - No type safety

### ✅ **Proper Architecture:**

1. **Single Source of Truth**
   - SourceRepository is the ONLY data layer
   - All UI and business logic reads from it
   - Consistent state everywhere

2. **Entity-Level Configuration**
   - Each Source has its own settings
   - No global booleans affecting all entities
   - True independence

3. **Dependency Injection**
   - Pass SourceManager to receivers
   - Easy to mock for testing
   - Loose coupling

4. **Type-Safe Models**
   - Source model handles both APP and SMS
   - Compile-time safety
   - Clear contracts

---

## 🚀 **NEXT STEPS:**

**USER DECISION REQUIRED:**

**Option A: Quick Fix (15 min)**
- Dashboard reads from old system
- Everything else stays same
- User sees accurate counts
- Still has global auto-clean

**Option B: Proper Migration (3-6 hours)**
- Migrate all data to new system
- Receivers use SourceManager
- Per-source settings working
- Architecture clean and scalable

**Which do you want to do first?**

