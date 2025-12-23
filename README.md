# AlertsToSheets

**Android app that captures notifications and SMS messages, then delivers them to configured webhooks (Google Sheets, Firestore, custom endpoints)**

**Version:** 2.3  
**Status:** Production Ready  
**Last Updated:** December 23, 2025

---

## 📊 **Project Stats** (Verified Dec 23, 2025)

- **Kotlin Files:** 55
- **Lines of Code:** 8,149
- **Architecture:** Clean Architecture (MVVM + Repository Pattern)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **Gradle:** 8.7
- **Kotlin:** 1.9.22

---

## 🎯 **What It Does**

1. **Captures** notifications from selected apps (e.g., BNN banking alerts)
2. **Captures** SMS messages from configured phone numbers
3. **Parses** notification/SMS content using custom parsers
4. **Renders** JSON payloads from templates
5. **Delivers** to multiple endpoints (fan-out) via HTTP POST

---

## 🏗️ **Architecture**

```
Notification/SMS → Parser → ParsedData → TemplateEngine → JSON → HttpClient → Endpoint(s)
```

### Key Features:
- ✅ Fan-out delivery (one event → N endpoints)
- ✅ Custom JSON templates with variable substitution
- ✅ UUID-based stable endpoint IDs
- ✅ Activity log with delivery status
- ✅ Samsung OneUI-inspired dark theme
- ✅ Lab mode for testing payloads without real events

---

## 🚀 **Quick Start**

### Prerequisites
- Android device/emulator (API 26+)
- Android Studio (recommended) or Gradle 8.7+
- JDK 17

### Build & Install
```bash
cd android
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Grant Permissions
1. Open app → tap "Permissions" card
2. Enable "Notification Access"
3. Enable "SMS Permissions" (if using SMS sources)
4. Disable battery optimization (for persistent background operation)

### Configure
1. Tap "Lab" card
2. Add endpoint (Google Sheets webhook URL or Firestore ingest URL)
3. Create template (JSON with variables like `{{title}}`, `{{body}}`, `{{timestamp}}`)
4. Add source:
   - **App Notification:** Select app(s) to monitor
   - **SMS:** Enter phone number(s)
5. Select endpoint(s) for delivery
6. Test with "Send Test" button

---

## 📚 **Documentation**

**Start here:** [`DOC_INDEX.md`](DOC_INDEX.md) - Complete documentation index

### Key Docs:
- [Complete Documentation Index](DOC_INDEX.md) - **Read this first**
- [Architecture Analysis](ZERO_TRUST_ARCHITECTURE_ANALYSIS.md) - Comprehensive deep-dive
- [MCP Tools Reference](MCP_QUICK_REFERENCE.md) - AI assistant tool usage
- [Developer Settings](DEVELOPER_SETTINGS_GUIDE.md) - Environment setup
- [Verification Checklist](VERIFICATION_CHECKLIST.md) - Testing guide
- [Samsung Icon Fix](docs/SAMSUNG_ICON_FIX.md) - Fix duplicate launcher icons

---

## 🛠️ **Development**

### Tech Stack
- **Language:** Kotlin 1.9.22
- **Build:** Gradle 8.7
- **Network:** OkHttp 4.12.0
- **JSON:** Gson 2.10.1
- **Async:** Kotlinx Coroutines 1.7.3
- **UI:** Material Design 3, AndroidX

### Project Structure
```
android/app/src/main/java/com/example/alertsheets/
├── ui/                          # Activities & UI
├── domain/                      # Business logic
│   ├── models/                  # Data models
│   ├── parsers/                 # Notification/SMS parsers
│   ├── DataPipeline.kt          # Core event processing
│   └── SourceManager.kt         # Source lifecycle
├── data/                        # Persistence
│   ├── repositories/            # CRUD operations
│   └── storage/                 # JSON file storage
├── services/                    # Android services
│   ├── AlertsNotificationListener.kt
│   ├── AlertsSmsReceiver.kt
│   └── BootReceiver.kt
└── utils/                       # Utilities
    ├── HttpClient.kt
    ├── TemplateEngine.kt
    └── PayloadSerializer.kt
```

### Testing
```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (on device/emulator)
./gradlew connectedDebugAndroidTest

# Logs
adb logcat -s AlertsApp:I Pipe:V Logs:V
```

**Note:** Test coverage is currently 0%. See [DOC_INDEX.md](DOC_INDEX.md#-testing-status) for test implementation plan.

---

## 🔐 **Security**

- ❌ **Never commit** `.env` files or service account JSON
- ✅ Store secrets in `functions/.env.local` (gitignored)
- ✅ Use environment variables for sensitive data
- ✅ Validate all HTTP endpoints before adding

---

## 🐛 **Troubleshooting**

| Issue | Solution |
|-------|----------|
| Duplicate launcher icons (Samsung) | See [docs/SAMSUNG_ICON_FIX.md](docs/SAMSUNG_ICON_FIX.md) |
| Gradle lock errors | See [GRADLE_FIX.md](GRADLE_FIX.md) |
| Notifications not captured | Check NotificationListener permission |
| SMS not captured | Check SMS permissions + default SMS app |
| Failed delivery | Check LogActivity for HTTP errors |

---

## 📝 **License**

Private project - All rights reserved

---

## 🤝 **Contributing**

For internal use. Contact project maintainer for access.

---

**For complete documentation, see [`DOC_INDEX.md`](DOC_INDEX.md)**
