# AlertsToSheets - Android Notification Forwarder

**Purpose:** Intercepts BNN (Breaking News Network) notifications, parses incident data, and forwards to Google Sheets in real-time.

---

## 📁 Project Structure

```
alerts-sheets/
├── android/                  # Android app source code
│   ├── app/
│   │   └── src/main/java/... # Kotlin source files
│   └── build.gradle          # Dependencies
│
├── scripts/
│   └── Code.gs               # Google Apps Script (doPost handler)
│
├── docs/                     # 📚 ALL DOCUMENTATION HERE
│   ├── README.md             # Documentation organization guide
│   │
│   ├── tasks/                # 🎯 ACTIVE WORK (AI agents read this)
│   │   └── AG_PARSING_FIX_PROMPT.md  # Current: Fix empty sheet fields
│   │
│   ├── architecture/         # 📐 SYSTEM DESIGN (reference docs)
│   │   ├── HANDOFF.md        # Build, deploy, troubleshooting
│   │   ├── DIAGNOSTICS.md    # Debug procedures
│   │   └── parsing.md        # BNN parsing specification
│   │
│   └── refactor/             # 🚀 FUTURE WORK (ignore for now)
│       └── OVERVIEW.md       # Long-term improvements (after bugs fixed)
│
├── prompt.md                 # Original implementation prompt
└── README.md                 # This file
```

---

## 🚨 Current Status

**Active Bug:** BNN notifications only populate timestamp in Google Sheet. All other fields empty.

**Fix In Progress:** See `/docs/tasks/AG_PARSING_FIX_PROMPT.md`

---

## 🏃 Quick Start

### For AI Agents (AG, Claude, etc.)

**Working on active task?**
1. ✅ Read: `/docs/tasks/{task-name}.md` (your assignment)
2. ✅ Reference: `/docs/architecture/` (understand system)
3. ❌ Ignore: `/docs/refactor/` (future work, will confuse context)

**Starting a refactor?** (Not now!)
1. ✅ Read: `/docs/refactor/OVERVIEW.md` first
2. ✅ Verify: All P0/P1 bugs resolved
3. ✅ Get approval: Stakeholder sign-off

---

## 🛠️ For Developers

### Prerequisites
- **JDK 17**
- **Android Studio Hedgehog (2023.1.1) or newer**
- **Android device/emulator** (API 26+)

### Build & Install
```bash
cd android
.\gradlew.bat :app:assembleDebug --no-daemon
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Debug
```powershell
adb logcat | findstr "NotificationService Parser BNN"
```

**Full instructions:** See `/docs/architecture/HANDOFF.md`

---

## 📋 Key Files

| File | Purpose |
|------|---------|
| `NotificationService.kt` | Intercepts notifications |
| `Parser.kt` | Parses BNN pipe-delimited format |
| `QueueProcessor.kt` | Offline queue + retry logic |
| `NetworkClient.kt` | HTTP POST to Apps Script |
| `scripts/Code.gs` | Google Apps Script webhook |

---

## 🔧 Troubleshooting

**Issue:** Sheet fields empty  
**Fix:** See `/docs/tasks/AG_PARSING_FIX_PROMPT.md`

**Issue:** Notifications not intercepted  
**Fix:** See `/docs/architecture/DIAGNOSTICS.md`

**Issue:** Queue stuck pending  
**Fix:** See `/docs/architecture/DIAGNOSTICS.md`

---

## 📚 Documentation Index

- **System Architecture:** `/docs/architecture/HANDOFF.md`
- **Build & Deploy:** `/docs/architecture/HANDOFF.md`
- **Debugging Guide:** `/docs/architecture/DIAGNOSTICS.md`
- **Parsing Spec:** `/docs/architecture/parsing.md`
- **Active Tasks:** `/docs/tasks/`
- **Future Plans:** `/docs/refactor/` (DO NOT implement yet)

---

## 🤝 Contributing

1. Check `/docs/tasks/` for active assignments
2. Reference `/docs/architecture/` for system understanding
3. Follow existing code patterns (minimal changes preferred)
4. Test thoroughly before submitting
5. Update relevant docs if architecture changes

---

## 📞 Support

**For current bugs:** See `/docs/tasks/`  
**For system questions:** See `/docs/architecture/`  
**For future features:** See `/docs/refactor/` (after bugs fixed)

---

**Current Focus:** Fix BNN parsing to populate all Google Sheet columns. See active task in `/docs/tasks/`.

