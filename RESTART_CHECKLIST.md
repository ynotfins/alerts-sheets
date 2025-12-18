# 🚀 Post-Restart Quick Checklist

**Print this or keep open** - Your roadmap for the next session!

---

## ✅ **Immediate Actions (5 Minutes)**

```powershell
# Navigate to project
cd D:\github\alerts-sheets\android

# Clean build
.\gradlew.bat clean assembleDebug --no-daemon

# Uninstall old version (removes both duplicate icons)
adb uninstall com.example.alertsheets

# Install fresh
adb install app\build\outputs\apk\debug\app-debug.apk

# Clear launcher cache (removes ghost icons)
adb shell pm clear com.android.launcher3

# Reboot device (ensures clean state)
adb reboot
```

**Test After Reboot:**
- [ ] Only ONE app icon (no duplicate)
- [ ] Tap Permissions card (should NOT crash)
- [ ] Accept a permission and return (should refresh correctly)

---

## 📝 **Give AG This Prompt**

```
Task: Phase 1 parsing fixes - 4 small changes.

Files to read:
- /docs/tasks/AG_FINAL_PARSING_FIXES.md
- /docs/tasks/AG_QUICK_FIX_SUMMARY.md
- /GIVE_AG_THIS_PROMPT.md

4 fixes (Parser.kt + TemplateEngine.kt):
1. Keep # in incident ID (enables update appending)
2. Populate county for NYC boroughs
3. Better FD code filtering (no DESK/BNNDESK)
4. Human-readable timestamp (12/17/2025 8:30:45 PM)

Sheet: https://docs.google.com/spreadsheets/d/1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE/edit?gid=0#gid=0

CRITICAL SHEET CONVENTION: 
- UNDERLINED header text (columns C-G) = STATIC fields - never append on updates
- Regular header text (columns A, B, H, I, J) = DYNAMIC fields - always append with \n
- This ensures updates append to SAME row (like Row 22), not create duplicates
- FD Codes (K-U): Only add NEW codes, skip duplicates

See /docs/architecture/SHEET_UPDATE_LOGIC.md and /docs/architecture/VISUAL_STANDARD.md for full spec.

Build: cd android && .\gradlew.bat :app:assembleDebug --no-daemon
```

---

## 🔑 **Credentials to Get (Phase 2)**

### **Priority Order:**

**P0 - Start Immediately:**
1. [ ] Create Firebase project: `alerts-sheets`
   - URL: https://console.firebase.google.com
   - Enable: Firestore + Cloud Functions
   
2. [ ] Download Service Account JSON
   - Firebase Console → Project Settings → Service Accounts
   - "Generate New Private Key" button

**P1 - This Week:**
3. [ ] Google Maps API Key
   - Enable Geocoding API
   - Create key + restrict by IP/API
   
4. [ ] Attom Data API Key
   - Sign up: https://www.attomdata.com
   - Start with trial (1,000 free requests)

**P2 - Next Week:**
5. [ ] Estated API Key
   - Sign up: https://estated.com/developers
   - Free tier: 500 requests/month
   
6. [ ] Gemini AI (Firebase Extension)
   - Install: "Generate Text with Gemini" extension

**Details:** See `/docs/architecture/CREDENTIALS_AUDIT.md`

---

## 📂 **Important Files Reference**

| Need | File |
|------|------|
| **AG Prompt** | `/GIVE_AG_THIS_PROMPT.md` |
| **Sheet Logic** | `/docs/architecture/SHEET_UPDATE_LOGIC.md` |
| **Credentials** | `/docs/architecture/CREDENTIALS_AUDIT.md` |
| **State Snapshot** | `/STATE_BEFORE_RESTART.md` |
| **Phase 2 Plan** | `/docs/architecture/ENRICHMENT_PIPELINE.md` |
| **Why This Approach** | `/docs/architecture/STRATEGIC_DECISION.md` |

---

## 🎯 **Today's Goals**

- [x] Permission crash fixed
- [x] Duplicate icon fixed
- [ ] Test on device (verify fixes work)
- [ ] AG completes 4 parsing fixes
- [ ] Verify sheet behavior matches spec
- [ ] Create Firebase project
- [ ] Get Google Maps API key

---

## 🧪 **Quick Test (After AG's Fixes)**

```powershell
# Build
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug --no-daemon

# Install
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Test: Send BNN notification
# Check Google Sheet:
# - Incident ID has # prefix?
# - Timestamp shows 12/17/2025 8:30:45 PM?
# - Update appends to SAME row?
# - Static columns (C-G) unchanged?
# - FD codes clean (no DESK)?
```

---

## 💡 **Key Reminders**

1. **DO NOT** use Firebase projects: `emu-alerts-v2`, `nfa-alerts-v2`
   - Those are production (must stay operational)
   - Create NEW project: `alerts-sheets`

2. **Column Update Logic:**
   - **C-G** (ID, State, County, City, Address) = STATIC (never change)
   - **A, B, H, I, J** (Status, Timestamp, Type, Details, Body) = DYNAMIC (append with `\n`)
   - **K-U** (FD Codes) = SMART MERGE (only add new codes)

3. **Cost Estimates (Phase 2):**
   - Google Maps: $10-20/month (with caching)
   - Attom: $50-70/month (with caching)
   - Estated: $20-30/month (with caching)
   - Gemini AI: $5-10/month
   - **Total: ~$85-130/month**

---

## 🚨 **If Something Goes Wrong**

**Build fails:**
```powershell
.\gradlew.bat clean --no-daemon
.\gradlew.bat --stop
.\gradlew.bat assembleDebug --no-daemon --stacktrace
```

**Permission crash still happens:**
- Check `/STATE_BEFORE_RESTART.md` section "Changes Made This Session #1"

**Duplicate icon still shows:**
- Check `/STATE_BEFORE_RESTART.md` section "Changes Made This Session #2"

**AG gets stuck:**
- Reference `/docs/tasks/AG_FINAL_PARSING_FIXES.md` for detailed specs

---

## 📞 **Quick Links**

- **Google Sheet:** https://docs.google.com/spreadsheets/d/1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE/edit?gid=0#gid=0
- **Firebase Console:** https://console.firebase.google.com
- **Google Cloud Console:** https://console.cloud.google.com
- **Attom Data:** https://www.attomdata.com
- **Estated:** https://estated.com/developers

---

## ✅ **Success Criteria (Today)**

- [ ] Android app builds cleanly
- [ ] No crash when tapping Permissions
- [ ] Only ONE launcher icon
- [ ] AG completes 4 parsing fixes
- [ ] Test notification creates proper sheet row
- [ ] Update appends to same row (like Row 22)
- [ ] Timestamp human-readable
- [ ] FD codes clean
- [ ] Firebase project created

---

## 🎉 **You've Got This!**

**Everything is documented, saved, and ready.**  
**Let's get this project rolling full speed ahead!** 🚀

---

**Quick Start After Restart:**
1. Build & test Android fixes (5 min)
2. Give AG parsing prompt (1 min)
3. Create Firebase project (10 min)
4. Get API keys (30 min)
5. Deploy first Cloud Function (30 min)

**Total time to Phase 2 kickoff: ~1 hour!**

Good luck! 💪

