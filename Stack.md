# Tech Stack & Environment

## Mobile Application (Android)

- **Language**: Kotlin
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle 8.7 (AGP 8.2.0)
- **Architecture**: Service-based (Background Listener)
  - `NotificationListenerService`: Captures system notifications.
  - `GlobalScope` / Coroutines: Handles async network operations.
- **Dependencies**:
  - `Gson`: JSON serialization.
  - `OkHttp`: Network requests.

## Backend (Google Apps Script)

- **Platform**: Google Apps Script (V8 Runtime).
- **Deployment**: Web App (Executes as User, Access: Anyone).
- **Database**: Google Sheets.
- **Locking**: Uses `LockService` to handle concurrency (up to 30s wait).

## Data Pipeline

1.  **Trigger**: Android Notification (BNN App).
2.  **Capture**: `NotificationService` intercepts `StatusBarNotification`.
3.  **Process**: `Parser.kt` extracts structured data.
4.  **Transport**: HTTP POST (JSON Payload) to Apps Script URL.
5.  **Storage**: Apps Script parses JSON and appends/updates Google Sheet rows.

## Key Configuration

- **Package Name**: `com.example.alertsheets`
- **Version Code**: 1 (Debug v2)
- **Device Tested**: Samsung S25 Ultra (Android 15)
