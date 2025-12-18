# Project State Before PC Restart

**Date:** December 17, 2025 - 9:12 PM  
**Status:** All changes committed, ready for restart  
**Next Session:** Full speed ahead after restart 🚀

---

## ✅ **Changes Made This Session (All Committed)**

### **1. Permission Card Crash - FIXED** ✅
**File:** `android/app/src/main/java/com/example/alertsheets/PermissionsActivity.kt`

**Problem:** 
- Tapping Permissions card crashed the app
- `refreshPermissions()` couldn't find layout reference

**Fix Applied:**
- Added `private lateinit var mainLayout: LinearLayout` class property
- Created `buildPermissionsList()` method to build/rebuild UI
- `refreshPermissions()` now simply calls `buildPermissionsList()`

**Test After Restart:**
```powershell
# Build
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug --no-daemon

# Uninstall old
adb uninstall com.example.alertsheets

# Install new
adb install app/build/outputs/apk/debug/app-debug.apk

# Test: Tap Permissions card → Should NOT crash ✅
```

---

### **2. Duplicate Launcher Icon - FIXED** ✅
**File:** `android/app/src/main/AndroidManifest.xml`

**Problem:**
- Two app icons appeared after install
- One with blue chat bubble, one without
- Same package name, shared permissions

**Root Cause:**
- `android:roundIcon` pointing to same resource as `icon`
- Some launchers (Samsung) create TWO shortcuts when this happens

**Fix Applied:**
- Removed `android:roundIcon="@drawable/ic_launcher_foreground"` line
- Kept only `android:icon="@drawable/ic_launcher_foreground"`

**Test After Restart:**
```powershell
# Full clean install
adb uninstall com.example.alertsheets
.\gradlew.bat clean assembleDebug --no-daemon
adb install app/build/outputs/apk/debug/app-debug.apk

# Clear launcher cache
adb shell pm clear com.android.launcher3
adb reboot

# After reboot: Check app drawer → Should show only ONE icon ✅
```

---

### **3. Documentation Created** 📚

#### **Sheet Update Logic (CORRECTED)** ✅
**File:** `docs/architecture/SHEET_UPDATE_LOGIC.md`

**Key Corrections:**
- **Static Columns (C-G):** ID, State, County, City, Address → NEVER append on updates
- **Dynamic Columns (A, B, H, I, J):** Status, Timestamp, Type, Details, Body → ALWAYS append with `\n`
- **FD Codes (K-U):** Only add NEW codes, skip duplicates for same incident

**Example Lifecycle:**
1. New incident: Write all columns
2. Update #1: Append to A, B, H, I, J | Add new FD codes | C-G unchanged
3. Update #2: Append to A, B, H, I, J | Add new FD codes | C-G unchanged

**This is the authoritative spec** for Google Sheet behavior.

---

#### **Credentials Audit** ✅
**File:** `docs/architecture/CREDENTIALS_AUDIT.md`

**Credentials We Have:**
- ✅ BNN_SHARED_SECRET (Android app)
- ✅ FD_SHEET_ID (Google Sheet)
- ✅ Apps Script Web App URL (deployed)

**Credentials We Need (Phase 2):**
- ❌ Firebase Project: `alerts-sheets` (NEW, separate from emu/nfa)
- ❌ Google Service Account JSON
- ❌ Google Maps API Key ($10-20/month with caching)
- ❌ Attom Data API Key ($50-70/month with caching)
- ❌ Estated API Key ($20-30/month with caching)
- ❌ Gemini AI API Key ($5-10/month - use Firebase Extension)

**Total Phase 2 Cost:** ~$85-130/month (mostly property APIs)

**Implementation Order:**
1. Week 1: Firebase setup + geocoding
2. Week 2: Property APIs (Attom, Estated)
3. Week 3: FD code translation + AI enrichment
4. Week 4: EMU/NFA app integration

---

#### **Strategic Documents** ✅
All created and ready:
- `/docs/architecture/ENRICHMENT_PIPELINE.md` - Full Phase 2 architecture
- `/docs/architecture/STRATEGIC_DECISION.md` - Why frontend + backend
- `/docs/tasks/AG_FINAL_PARSING_FIXES.md` - Comprehensive parsing fixes (4 issues)
- `/docs/tasks/AG_QUICK_FIX_SUMMARY.md` - TL;DR for AG
- `/GIVE_AG_THIS_PROMPT.md` - Simple copy/paste prompt

---

## 📋 **Pending: Give AG the Parsing Fixes**

### **4 Fixes Needed (Total: ~15 lines of code)**

**You said:** "I have only given AG one prompt"

**Next Step After Restart:** Give AG the updated prompt with all 4 fixes:

```
Task: Phase 1 parsing fixes - 4 small changes to improve data quality.

Read for full context:
1. /docs/tasks/AG_FINAL_PARSING_FIXES.md
2. /docs/tasks/AG_QUICK_FIX_SUMMARY.md

Or use simple version: /GIVE_AG_THIS_PROMPT.md

Summary (4 fixes, ~15 lines total):
1. Keep # in incident ID (Parser.kt Line 112)
2. Populate county for NYC boroughs (Parser.kt Line 92)
3. Better FD code filtering (Parser.kt Lines 288-299)
4. Human-readable timestamp (TemplateEngine.kt Lines 309-313)

Goal: 
- Updates append like Row 22 in sheet
- Timestamps show "12/17/2025 8:30:45 PM" format
- FD codes de-duplicated per incident

CRITICAL SHEET CONVENTION (Visual Standard):
- UNDERLINED header text (C-G: ID, State, County, City, Address) = STATIC - never append
- Regular header text (A, B, H, I, J: Status, Timestamp, Type, Details, Body) = DYNAMIC - always append with \n
- This ensures updates append to SAME row, not create duplicates

See:
- /docs/architecture/SHEET_UPDATE_LOGIC.md (detailed spec)
- /docs/architecture/VISUAL_STANDARD.md (visual convention)

Sheet: https://docs.google.com/spreadsheets/d/1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE/edit?gid=0#gid=0

Build: cd android && .\gradlew.bat :app:assembleDebug --no-daemon
```

---

## 🎯 **Immediate Tasks After PC Restart**

### **Priority 1: Test Permission + Icon Fixes** ⚡
```powershell
cd D:\github\alerts-sheets\android
.\gradlew.bat clean assembleDebug --no-daemon
adb uninstall com.example.alertsheets
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.android.launcher3
adb reboot
```

**Verify:**
- [ ] Only ONE app icon in launcher
- [ ] No blue chat bubble duplicate
- [ ] Permissions card opens without crash
- [ ] Tapping permission items launches settings

---

### **Priority 2: Give AG Parsing Fixes** 📝
- [ ] Copy prompt from `/GIVE_AG_THIS_PROMPT.md`
- [ ] Give to AG
- [ ] Wait for AG to complete 4 fixes
- [ ] Build and test on device
- [ ] Verify sheet behavior matches Row 22

---

### **Priority 3: Firebase Setup** 🔥
- [ ] Go to https://console.firebase.google.com
- [ ] Create new project: `alerts-sheets`
- [ ] Enable Firestore Database
- [ ] Download service account JSON
- [ ] Install Firebase CLI: `npm install -g firebase-tools`
- [ ] Initialize: `firebase init`
- [ ] Select: Firestore + Functions

---

### **Priority 4: Get API Keys** 🔑
- [ ] Enable Google Maps Geocoding API
- [ ] Get Maps API key (restrict by IP + API)
- [ ] Sign up for Attom trial (1,000 free requests)
- [ ] Sign up for Estated free tier (500 requests)
- [ ] Install Gemini Firebase Extension (optional)

---

## 📂 **Project Structure (Current)**

```
D:\github\alerts-sheets\
├── android\                    # Android app (Phase 1)
│   ├── app\
│   │   ├── src\main\
│   │   │   ├── AndroidManifest.xml  ← FIXED (removed roundIcon)
│   │   │   ├── java\com\example\alertsheets\
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── PermissionsActivity.kt  ← FIXED (crash)
│   │   │   │   ├── Parser.kt              ← AG will fix
│   │   │   │   ├── TemplateEngine.kt      ← AG will fix
│   │   │   │   ├── NotificationService.kt
│   │   │   │   └── ... (other files)
│   │   │   └── res\
│   │   └── build.gradle
│   └── gradlew.bat
├── docs\                       # Documentation
│   ├── architecture\
│   │   ├── SHEET_UPDATE_LOGIC.md      ← NEW (corrected spec)
│   │   ├── CREDENTIALS_AUDIT.md       ← NEW (what we need)
│   │   ├── ENRICHMENT_PIPELINE.md     ← Phase 2 architecture
│   │   ├── STRATEGIC_DECISION.md      ← Why this approach
│   │   ├── HANDOFF.md
│   │   ├── PERMISSIONS_GUIDE.md
│   │   └── parsing.md
│   ├── tasks\
│   │   ├── AG_FINAL_PARSING_FIXES.md  ← Give to AG
│   │   └── AG_QUICK_FIX_SUMMARY.md    ← Give to AG
│   └── refactor\
│       └── OVERVIEW.md
├── scripts\
│   └── Code.gs                # Apps Script (Google Sheets)
├── GIVE_AG_THIS_PROMPT.md     ← Simple copy/paste for AG
├── STATE_BEFORE_RESTART.md    ← This file (you are here)
└── ... (other files)
```

---

## 🧪 **Testing Checklist (Post-Restart)**

### **Android App**
- [ ] Build succeeds without errors
- [ ] App installs cleanly (no duplicates)
- [ ] Only one launcher icon appears
- [ ] Permissions card opens without crash
- [ ] Permissions refresh correctly on resume
- [ ] Notification listener works
- [ ] SMS receiver works
- [ ] Test button sends realistic BNN payload

---

### **Parsing (After AG's Fixes)**
- [ ] Incident ID includes `#` prefix
- [ ] Timestamp shows `12/17/2025 8:30:45 PM` format
- [ ] NYC boroughs populate both county AND city
- [ ] FD codes clean (no DESK/BNNDESK)
- [ ] Parser logs show successful parsing

---

### **Google Sheet (After AG's Fixes)**
- [ ] New incident creates row with all fields
- [ ] Update with same ID appends to A, B, H, I, J (with `\n`)
- [ ] Update does NOT modify C, D, E, F, G (static)
- [ ] FD codes from update only add NEW codes
- [ ] Row 22 behavior happens for EVERY incident

---

## 📊 **Progress Tracking**

### **Phase 1: Android App Fixes** ✅ 90% Complete

| Task | Status | Notes |
|------|--------|-------|
| Permission crash | ✅ FIXED | Test after restart |
| Duplicate icon | ✅ FIXED | Test after restart |
| Parsing fixes | ⏳ PENDING | AG will do (4 fixes) |
| Test button | ✅ WORKING | Sends realistic BNN |
| Activity log | ✅ WORKING | Persists with SharedPreferences |
| Ticker text | ✅ WORKING | Dynamic, scrolling |
| Apps list UX | ✅ WORKING | Search + filter system apps |

**Remaining:** AG's parsing fixes (~15 lines of code)

---

### **Phase 2: Backend Enrichment** ⏸️ Not Started

| Task | Status | Timeline |
|------|--------|----------|
| Firebase setup | ❌ TODO | Week 1 (after restart) |
| Geocoding cache | ❌ TODO | Week 1 |
| Property APIs | ❌ TODO | Week 2 |
| FD code translation | ❌ TODO | Week 3 |
| AI enrichment | ❌ TODO | Week 3 |
| EMU/NFA integration | ❌ TODO | Week 4 |

**Start:** After Phase 1 complete + stable

---

## 💾 **Data Integrity**

### **What's Preserved:**
- ✅ All code changes committed
- ✅ Documentation up to date
- ✅ Build outputs in `android/app/build/`
- ✅ APK ready for testing

### **What's Safe to Delete (If Needed):**
- `android/app/build/` (can rebuild)
- `android/.gradle/` (Gradle cache)
- `node_modules/` (can reinstall)
- All `build_*.txt` log files (old build logs)

### **NEVER Delete:**
- `android/app/src/` (source code)
- `docs/` (documentation)
- `scripts/Code.gs` (Apps Script)
- `.gitignore` (important!)

---

## 🚀 **Post-Restart Action Plan**

### **Immediate (5 minutes)**
1. Boot PC
2. Open project in Cursor
3. `cd D:\github\alerts-sheets\android`
4. `.\gradlew.bat clean assembleDebug --no-daemon`
5. Install on device and test

---

### **Hour 1: Verify Fixes**
1. Test permission card (should not crash)
2. Check for duplicate icons (should see only one)
3. Give AG the parsing fix prompt
4. Wait for AG to complete

---

### **Hour 2: AG's Parsing Fixes**
1. Review AG's changes
2. Build and install
3. Send test notification
4. Verify Google Sheet behavior
5. Check for Row 22-style updates

---

### **Hour 3+: Firebase Setup**
1. Create Firebase project `alerts-sheets`
2. Download service account JSON
3. Enable APIs (Maps, Firestore)
4. Install Firebase CLI
5. Initialize project structure
6. Test basic Cloud Function deployment

---

### **Next Session: Backend Development**
1. Implement geocoding cache
2. Deploy enrichment function
3. Test with real addresses
4. Add property API integration
5. Build FD code dictionary

---

## 🎯 **Success Criteria**

### **End of Today (Post-Restart):**
- [ ] Android app builds without errors
- [ ] No permission card crash
- [ ] No duplicate launcher icons
- [ ] AG has completed 4 parsing fixes
- [ ] Google Sheet shows proper update behavior
- [ ] Firebase project created and initialized

---

### **End of Week 1:**
- [ ] Geocoding working with Firestore cache
- [ ] Basic enrichment function deployed
- [ ] Property API accounts created (Attom, Estated)
- [ ] Test enrichment with 10 real addresses

---

### **End of Phase 2 (4 weeks):**
- [ ] Full enrichment pipeline operational
- [ ] FD codes translated to human language
- [ ] AI summaries generated for all incidents
- [ ] EMU/NFA apps reading from Firestore
- [ ] 100% human-readable incidents

---

## 📞 **If Something Goes Wrong**

### **Build Fails:**
```powershell
.\gradlew.bat clean
.\gradlew.bat --stop
.\gradlew.bat assembleDebug --no-daemon --stacktrace
```

### **Permission Crash Still Happens:**
```kotlin
// Check PermissionsActivity.kt has:
private lateinit var mainLayout: LinearLayout

private fun buildPermissionsList() {
    mainLayout.removeAllViews()
    // ...
}
```

### **Duplicate Icon Still Appears:**
```xml
<!-- Check AndroidManifest.xml does NOT have: -->
android:roundIcon="@drawable/ic_launcher_foreground"

<!-- Should ONLY have: -->
android:icon="@drawable/ic_launcher_foreground"
```

### **AG Gets Stuck:**
- Refer to `/docs/tasks/AG_FINAL_PARSING_FIXES.md`
- Specific fixes listed with before/after code
- Test cases included

---

## 🎉 **You're Ready!**

**Everything is saved, documented, and ready to go.**

**After PC restart:**
1. ✅ Build and test Android fixes
2. ✅ Give AG parsing prompt
3. ✅ Create Firebase project
4. ✅ Get API keys
5. 🚀 Full speed ahead!

---

**See you after the restart! Good luck!** 🚀💪

---

**Last Updated:** December 17, 2025 - 9:12 PM  
**Next Session:** After PC restart  
**Status:** All changes committed, ready for action

