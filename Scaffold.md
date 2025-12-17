# Android Project Scaffold

## Directory Structure

```
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/alertsheets/
│   │   │   │   ├── AppConfigActivity.kt    // UI: Status, Test Payload, Validation
│   │   │   │   ├── NotificationService.kt  // Core Service: Listener & Controller
│   │   │   │   ├── Parser.kt               // Logic: Text Parsing Engine
│   │   │   │   ├── HttpService.kt          // Network: POST requests
│   │   │   │   ├── DataModels.kt           // Data Classes (ParsedData, etc.)
│   │   │   │   └── TemplateEngine.kt       // Fallback Logic
│   │   │   ├── res/
│   │   │   │   ├── layout/                 // XML UIs
│   │   │   │   ├── values/                 // Strings, Colors
│   │   │   │   └── xml/                    // Manifest configs
│   │   │   └── AndroidManifest.xml         // Permissions & Service Declarations
│   │   └── test/                           // Unit Tests
│   ├── build.gradle                        // App-level dependencies
│   └── proguard-rules.pro                  // Obfuscation rules
├── gradle/                                 // Gradle Wrapper
├── build.gradle                            // Project-level config
└── settings.gradle                         // Module inclusion
```

## Key Components

### 1. NotificationService

- **Role**: The "Brain" of the app.
- **Extends**: `NotificationListenerService`.
- **Events**: `onNotificationPosted`.
- **Duties**:
  - Filters for specific packages (BNN).
  - De-duplicates rapid-fire alerts.
  - Delegates text to `Parser`.
  - Delegates payload to `HttpService`.

### 2. AppConfigActivity

- **Role**: The "Face" of the app.
- **Features**:
  - **Dashboard**: Shows service status (Connected/Disconnected).
  - **Test Button**: "Test Payload Now" sends a mock BNN alert to verify end-to-end connectivity.
  - **Logs**: Displays the last status message on screen.

### 3. Parser

- **Role**: The "Intelligence".
- **Type**: Singleton Object (`object Parser`).
- **Function**: Pure function `parse(String) -> ParsedData?`.
