# AlertsToSheets Card Wiring Matrix
**Generated:** 2025-12-23  
**Purpose:** Comprehensive mapping of all UI cards, their data bindings, and side effects

---

## 1. DASHBOARD PERMANENT CARDS (MainActivity)

### Card 1: Lab Card
**File:** `android/app/src/main/res/layout/activity_main_dashboard.xml`  
**ID:** `@+id/card_lab`  
**Lines:** 65-102

#### Wiring (MainActivity.kt)
- **Setup:** `setupPermanentCards()` (Line 68)
- **Click:** Line 70 → `startActivity(Intent(this, LabActivity::class.java))`
- **Data Model:** None (navigation only)
- **Side Effects:** None
- **Concurrency:** Safe (read-only, UI thread only)

---

### Card 2: Permissions Card
**File:** `android/app/src/main/res/layout/activity_main_dashboard.xml`  
**ID:** `@+id/card_permissions`  
**Lines:** 105-148

#### Wiring (MainActivity.kt)
- **Setup:** `setupPermanentCards()` (Line 68)
- **Click:** Line 75 → `startActivity(Intent(this, PermissionsActivity::class.java))`
- **Status Dot:** `dot_permissions` (Line 38)
  - **Update:** `updateStatus()` (Line 198)
  - **Read:** `NotificationManager.isNotificationListenerAccessGranted()` (Line 181)
  - **Color:** Green (access granted) / Red (no access)
- **Data Model:** System permission state (read-only)
- **Side Effects:** None
- **Concurrency:** Safe (reads system API only)

---

### Card 3: Activity Log Card
**File:** `android/app/src/main/res/layout/activity_main_dashboard.xml`  
**ID:** `@+id/card_logs`  
**Lines:** 150-193

#### Wiring (MainActivity.kt)
- **Setup:** `setupPermanentCards()` (Line 68)
- **Click:** Line 80 → `startActivity(Intent(this, LogActivity::class.java))`
- **Status Dot:** `dot_logs` (Line 39)
  - **Update:** `updateStatus()` (Line 205)
  - **Read:** `LogRepository.getLogs()` (Line 188)
  - **Logic:** Green if recent SENT, Red otherwise
- **Data Model:** `LogRepository` (singleton, in-memory + SharedPreferences)
- **Side Effects:** None
- **Concurrency:** Safe (LogRepository uses CoroutineScope with SupervisorJob)

---

### Card 4: Test Harness Card (DEBUG ONLY)
**File:** Dynamically created in `MainActivity.kt`  
**Lines:** 87-102

#### Wiring (MainActivity.kt)
- **Setup:** `setupPermanentCards()` → `if (BuildConfig.DEBUG)` (Line 87)
- **Creation:** `layoutInflater.inflate(R.layout.item_dashboard_source_card, ...)`
- **Click:** Intent action `"com.example.alertsheets.DEBUG_INGEST_TEST"` (Line 93)
- **Data Model:** None
- **Side Effects:** Launches `IngestTestActivity` (debug-only, no production impact)
- **Concurrency:** Safe (navigation only)
- **Gate:** Activity declared ONLY in `android/app/src/debug/AndroidManifest.xml`

---

## 2. DASHBOARD DYNAMIC SOURCE CARDS (MainActivity)

### Card Layout
**File:** `android/app/src/main/res/layout/item_dashboard_source_card.xml`  
**Inflated:** `MainActivity.kt:124`

#### Wiring (MainActivity.kt)
- **Load:** `loadDynamicCards()` (Line 105)
- **Data Source:** `sourceManager.getAllSources()` (Line 108)
- **Trigger:** `onResume()` (Line 62)

#### Per-Card Binding
**File:** `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`  
**Lines:** 123-172

| Element | ID | Data | Logic |
|---------|-----|------|-------|
| Icon | `source_icon` | `source.iconName` | Map to drawable (Line 131-142), tint with `source.cardColor` (Line 144) |
| Name | `source_name` | `source.name` | Direct text (Line 146) |
| Status Dot | `source_status_dot` | `source.enabled`, `source.endpointIds`, `source.templateJson` | Green if enabled AND configured, else Red (Line 149-153) |

#### Click Action
- **Handler:** Line 156
- **Effect:** Launch `LabActivity` with `source_id` extra (Line 157-159)
- **Data Model:** `Source` (read-only)
- **Side Effects:** None
- **Concurrency:** Safe (read-only, launched on Main thread)

#### Data Model: Source
**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt`  
**Storage:** `sources.json` via `SourceRepository`  
**Fields Used:**
- `id` (String)
- `name` (String)
- `iconName` (String)
- `cardColor` (Int)
- `enabled` (Boolean)
- `endpointIds` (List<String>)
- `templateJson` (String)

#### Concurrency Analysis
**Safe:** Cards are created on Main thread, data loaded via coroutine (Line 107):
```kotlin
scope.launch {
    val sources = withContext(Dispatchers.IO) {
        sourceManager.getAllSources()  // File I/O on IO dispatcher
    }
    // UI updates on Main thread (implicit)
}
```

**Race Condition Risk:** NONE  
- `sourceManager.getAllSources()` reads file on Dispatchers.IO
- UI updates happen on Main thread
- No shared mutable state between cards

---

## 3. LAB ACTIVITY - SOURCE CREATION/EDITING

### Entry Points
1. **Create New:** Launch from MainActivity Lab card, no `source_id` extra
2. **Edit Existing:** Launch from dynamic source card, with `source_id` extra (Line 158)

### Card-Like Sections (Not actual cards, but functional sections)

#### Section 1: Basic Info
**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`  
**Elements:**
- `inputName` (EditText) - Line 50
- `radioGroup` (RadioGroup) - Line 51, options: App / SMS
- Phone number selection (SMS only) - `selectedPhoneNumber` (Line 62)

**Data Model:**
- `Source.name` (String)
- `Source.type` (SourceType.APP or SourceType.SMS)
- `Source.id` (packageName or `"sms:{phone}"`)

**Side Effects:** None (local state only)

---

#### Section 2: Template Selection
**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`  
**Elements:**
- `spinnerTemplate` (Spinner) - Line 52
- Template management buttons (Lines 350-385)

**Data Model:**
- `Source.templateJson` (String) - **PER-SOURCE** template
- `TemplateRepository` - Shared templates available for selection

**Side Effects:**
- **"Save as New"**: Calls `templateRepo.saveUserTemplate()` (Line 367)
- **"Delete"**: Calls `templateRepo.deleteUserTemplate()` (Line 375)

**Concurrency Risk:** LOW  
- Template operations are synchronous file writes
- No concurrent modification of same template expected (single-user app)

---

#### Section 3: Auto-Clean & Custom Test Payloads
**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`  
**Elements:**
- `checkAutoClean` (CheckBox) - Line 53
- Custom test payload fields (Lines 67-70):
  - `customTestPayload`
  - `customDuplicatePayload`
  - `customDirtyPayload`

**Data Model:**
- `Source.autoClean` (Boolean)
- `Source.customTestPayload` (String)
- `Source.customDuplicatePayload` (String)
- `Source.customDirtyPayload` (String)

**Side Effects:** None (local state only until save)

---

#### Section 4: Endpoint Selection
**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`  
**Elements:**
- `endpointsCheckboxes` (LinearLayout) - Line 56
- `btnManageEndpoints` (Button) - Line 57

**Data Model:**
- `Source.endpointIds` (List<String>)
- `EndpointRepository.getAll()` - Populates checkbox list

**Side Effects:**
- **"+ Manage Endpoints"**: Launches `EndpointActivity` (Line 362)

**Wiring:**
- **Population:** `populateEndpoints()` function (inferred from pattern)
- **Selection:** Checkboxes update `selectedEndpointIds` (Line 65)

**Concurrency Risk:** LOW  
- Endpoint list loaded synchronously
- User interaction is single-threaded (Main UI)

---

#### Section 5: Icon & Color Selection
**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`  
**Elements:**
- `previewIcon` (ImageView) - Line 58
- `previewColor` (View) - Line 59
- Available icons (Lines 73-84)
- Available colors (Lines 87-100)

**Data Model:**
- `Source.iconName` (String) - Line 63
- `Source.cardColor` (Int) - Line 64

**Side Effects:** None (local state only)

---

#### Section 6: Preview & Test
**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`  
**Elements:**
- `inputJson` (EditText) - Line 54, shows rendered JSON
- "Test New" button (inferred)
- "Duplicate Test" button (inferred)
- "Dirty Test" button (inferred)

**Data Model:**
- Reads: `Source.templateJson`, `Source.customTestPayload`, etc.
- Applies: `TemplateEngine.apply()` to render JSON

**Side Effects:**
- **"Test New"**: HTTP POST to selected endpoints (inferred)
  - Calls `HttpClient.post()`
  - Updates UI with response code/body

**Concurrency Risk:** MEDIUM  
- Test sends real HTTP requests
- If user clicks "Save" while test is in-flight, no coordination
- **Mitigation:** Tests are async (likely coroutines), Save is immediate
- **Impact:** Low (test result displayed but not stored)

---

### Save Action
**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`  
**Function:** Save button click (inferred, standard pattern)

**Data Flow:**
1. Collect all fields (name, type, template, endpoints, autoClean, etc.)
2. Create `Source` object
3. Call `sourceManager.saveSource(source)` → `SourceRepository.save()`
4. Write to `sources.json` via `JsonStorage.write()`
5. Finish activity

**Side Effects:**
- **File Write:** `sources.json` updated atomically
- **Dashboard Refresh:** MainActivity.onResume() reloads sources

**Concurrency Risk:** LOW  
- File writes are atomic (JsonStorage uses `writeText()`)
- No in-memory caching, so concurrent reads see updated data after write completes

---

## 4. APPS LIST ACTIVITY - APP SOURCE CREATION

### Purpose
Select apps to monitor for notifications

### Entry Point
**Launch:** From LabActivity when selecting "App" source type (inferred)

### Card Display
**File:** `android/app/src/main/java/com/example/alertsheets/AppsListActivity.kt`  
**Layout:** GridLayout with app cards (inferred)

#### Per-Card Data
- **App Name:** `ApplicationInfo.loadLabel()`
- **Package Name:** `ApplicationInfo.packageName`
- **Icon:** `ApplicationInfo.loadIcon()`
- **Selection State:** Checkbox or toggle

### Data Model
**Read:**
- `PackageManager.getInstalledApplications()` (system API)
- `SourceManager.getAllSources()` (to mark already-monitored apps)

**Write:**
- Creates new `Source` object per selected app
- Calls `SourceManager.saveSource(source)`

### Side Effects
**On Save:**
1. For EACH selected app:
   - Create `Source(id=packageName, type=APP, ...)`
   - Assign `endpointIds` from selection
   - Save to `sources.json`
2. Return to previous activity

**Concurrency Risk:** LOW  
- Sequential saves (loop)
- No parallel writes to same file (single thread)

---

## 5. SMS CONFIG ACTIVITY - SMS SOURCE CREATION

### Purpose
Configure SMS sources (by sender phone number)

### Entry Point
**Launch:** From LabActivity when selecting "SMS" source type (inferred)

### Card Display
**File:** `android/app/src/main/java/com/example/alertsheets/SmsConfigActivity.kt`  
**Layout:** Dialog or list (inferred)

#### Input Methods
1. **Manual Entry:** EditText for phone number
2. **Contact Picker:** Launch contact picker intent (REQUEST_CONTACT_PICK = 1001)

### Data Model
**Read:**
- `ContactsContract` (if picking from contacts)

**Write:**
- Creates `Source(id="sms:{phone}", type=SMS, ...)`
- Calls `SourceManager.saveSource(source)`

### Side Effects
**On Save:**
1. Create SMS source with selected phone number
2. Assign endpoints and template
3. Save to `sources.json`

**Concurrency Risk:** LOW  
- Single source creation per session
- No concurrent writes expected

---

## 6. ENDPOINT ACTIVITY - ENDPOINT MANAGEMENT

### Purpose
CRUD operations for delivery endpoints

### Entry Point
**Launch:** From LabActivity "Manage Endpoints" button (Line 362)

### Card Display
**File:** `android/app/src/main/java/com/example/alertsheets/EndpointActivity.kt`  
**Layout:** RecyclerView with endpoint cards (inferred)

#### Per-Card Data
**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Endpoint.kt`
- `id` (UUID)
- `name` (String)
- `url` (String)
- `enabled` (Boolean)
- `timeout` (Int)
- `retryCount` (Int)
- `headers` (Map<String, String>)
- `stats` (EndpointStats)

### Data Model
**Read:** `EndpointRepository.getAll()`  
**Write:** `EndpointRepository.save(endpoint)` or `.deleteById(id)`

### Side Effects
**On Save/Delete:**
1. Update `endpoints.json` via `JsonStorage.write()`
2. Endpoints used by sources are NOT automatically updated
   - Sources store `endpointIds: List<String>`
   - Deleting an endpoint leaves dangling references
   - DataPipeline filters with `.mapNotNull { endpointRepo.getById(it) }`

**Concurrency Risk:** MEDIUM  
- If endpoint deleted while notification is processing:
  - DataPipeline reads `source.endpointIds`
  - Calls `endpointRepo.getById(id)` → returns `null`
  - Filtered out by `.mapNotNull()`
  - Result: Notification not delivered to deleted endpoint
- **Impact:** Expected behavior (endpoint intentionally removed)
- **No crash risk:** Null-safe handling

---

## 7. LOG ACTIVITY - ACTIVITY LOG VIEWER

### Purpose
Display recent notification/SMS processing logs

### Entry Point
**Launch:** From MainActivity Activity Log card (Line 80)

### Card Display
**File:** `android/app/src/main/java/com/example/alertsheets/LogActivity.kt`  
**Layout:** RecyclerView with `LogAdapter` (inferred)

#### Per-Card Data
**File:** `android/app/src/main/java/com/example/alertsheets/LogEntry.kt`
- `id` (String)
- `timestamp` (Long)
- `packageName` (String)
- `title` (String)
- `content` (String)
- `status` (LogStatus: PENDING, PROCESSING, SENT, PARTIAL, FAILED, IGNORED)
- `rawJson` (String)

### Data Model
**Read:** `LogRepository.getLogs()` (in-memory list + SharedPreferences)  
**Write:** None (read-only view)

### Side Effects
None (display only)

**Concurrency Risk:** NONE  
- LogRepository is thread-safe (CoroutineScope + synchronized list)
- Reads are non-blocking

---

## 8. PERMISSIONS ACTIVITY - PERMISSION SETUP

### Purpose
Guide user through enabling required permissions

### Entry Point
**Launch:** From MainActivity Permissions card (Line 75)

### Card Display
**File:** `android/app/src/main/java/com/example/alertsheets/PermissionsActivity.kt`  
**Layout:** List of permission cards (inferred)

#### Per-Card Data
- Permission name (e.g., "Notification Access")
- Current state (Granted / Denied)
- Action button (e.g., "Grant Access")

### Data Model
**Read:** System permission APIs (no persistent storage)

### Side Effects
**On Button Click:**
1. Launch system permission settings via Intent
2. No direct state modification (user controls via Settings app)

**Concurrency Risk:** NONE  
- Read-only system state checks
- No file writes

---

## 9. INGEST TEST ACTIVITY (DEBUG ONLY)

### Purpose
E2E test harness for Firestore ingestion pipeline

### Entry Point
**Launch:** From MainActivity test harness card (debug-only, Line 93)

### Card Display
**File:** `android/app/src/debug/java/com/example/alertsheets/ui/IngestTestActivity.kt`  
**Layout:** `android/app/src/debug/res/layout/activity_ingest_test.xml`

#### Test Scenario Cards
1. **Happy Path Test** (Button: `btn_test_happy_path`)
2. **Network Outage Test** (Button: `btn_test_network_outage`)
3. **Crash Recovery Test** (Button: `btn_test_crash_recovery`)
4. **Deduplication Test** (Button: `btn_test_dedup`)

### Data Model
**Read:** `IngestQueueDb.getStats()` (queue depth, pending count)  
**Write:** `IngestQueue.enqueue()` (creates test events)

### Side Effects
**On Test Execution:**
1. Enqueue test event to `IngestQueueDb` (SQLite)
2. `IngestQueue.processQueue()` sends HTTP POST to `/ingest` Cloud Function
3. Firestore document created in `users/{uid}/events/{eventId}`
4. UI displays result: eventId, queue depth, HTTP status, Firestore path

**Concurrency Risk:** LOW  
- Test runs on UI thread (coroutine)
- IngestQueue uses AtomicBoolean lock (`isProcessing`)
- Multiple tests can queue events, but processing is serialized

**Isolation:**
- ✅ NO interaction with DataPipeline
- ✅ Separate database (SQLite, not sources.json)
- ✅ Separate endpoint (BuildConfig.INGEST_ENDPOINT)
- ✅ Debug-only (release builds cannot access)

---

## 10. CARD INDEPENDENCE MATRIX

| Card/Activity | Data Model | Shared State | Side Effects | Concurrent Safe? |
|---------------|------------|--------------|--------------|------------------|
| Dashboard Lab Card | None | None | Navigation only | ✅ Yes |
| Dashboard Permissions Card | System API | None | Navigation only | ✅ Yes |
| Dashboard Logs Card | LogRepository | Singleton (thread-safe) | Navigation only | ✅ Yes |
| Dashboard Source Cards | Source (read) | None | Navigation only | ✅ Yes |
| LabActivity (Create) | Source (write) | sourceManager | File write (atomic) | ✅ Yes |
| LabActivity (Edit) | Source (read+write) | sourceManager | File write (atomic) | ✅ Yes |
| AppsListActivity | Source (bulk write) | sourceManager | Multiple file writes (sequential) | ✅ Yes |
| SmsConfigActivity | Source (write) | sourceManager | File write (atomic) | ✅ Yes |
| EndpointActivity | Endpoint (CRUD) | endpointRepo | File write (atomic) | ⚠️ Medium (dangling refs) |
| LogActivity | LogEntry (read) | LogRepository | None | ✅ Yes |
| PermissionsActivity | System API | None | System intent | ✅ Yes |
| IngestTestActivity | IngestQueue (write) | IngestQueue singleton | HTTP POST, Firestore write | ✅ Yes (isolated) |

---

## 11. CONCURRENT CARD OPERATIONS

### Scenario 1: Edit Source While Notification Processing
**Timeline:**
1. User opens LabActivity to edit source `com.example.bnn`
2. Notification arrives from `com.example.bnn`
3. DataPipeline reads `source` from `sources.json`
4. User clicks "Save" in LabActivity
5. `sources.json` updated
6. DataPipeline continues processing with OLD source data

**Result:** Processing uses stale data (old template, old endpoints)

**Risk Level:** LOW  
**Mitigation:** DataPipeline reads source at start of `process()` (Line 55), entire processing is in single coroutine with captured source object

**Impact:** Next notification will use updated source

---

### Scenario 2: Delete Endpoint While Notification Delivering
**Timeline:**
1. Notification arrives, DataPipeline resolves endpoints
2. User opens EndpointActivity, deletes endpoint X
3. `endpoints.json` updated
4. DataPipeline calls `endpointRepo.getById(X)` → returns `null`
5. Filtered out by `.mapNotNull()`

**Result:** Delivery to deleted endpoint skipped

**Risk Level:** LOW  
**Mitigation:** Null-safe filtering (Line 108-110)

**Impact:** Expected behavior (endpoint intentionally removed)

---

### Scenario 3: Multiple Notifications Arriving Simultaneously
**Timeline:**
1. Notification A arrives → `pipeline.processAppNotification()` → `scope.launch` (Dispatchers.IO)
2. Notification B arrives → `pipeline.processAppNotification()` → `scope.launch` (Dispatchers.IO)
3. Both coroutines run concurrently

**Shared Resources:**
- `SourceRepository.getAll()` (file read)
- `EndpointRepository.getById()` (file read)
- `HttpClient.post()` (stateless, connection pooling)
- `LogRepository.addLog()` (thread-safe)

**Result:** Both notifications processed independently

**Risk Level:** LOW  
**Mitigation:** All file reads are independent, JsonStorage is atomic, LogRepository is thread-safe

**Impact:** Concurrent processing is SAFE and EXPECTED

---

### Scenario 4: Save Source While Another Source Processing
**Timeline:**
1. Source A notification processing (reading `sources.json`)
2. User saves Source B (writing `sources.json`)
3. File write is atomic (temp file + rename)
4. Source A processing completes with old data
5. Next read of `sources.json` gets updated data

**Result:** Source A uses old data, Source B update takes effect immediately for next notification

**Risk Level:** NONE  
**Mitigation:** Atomic file writes (JsonStorage)

**Impact:** Expected behavior (eventual consistency)

---

## 12. CRITICAL WIRING OBSERVATIONS

### ✅ GOOD: Card Independence
- Each card reads its own data on-demand
- No shared mutable state between cards
- UI updates are Main thread only
- File I/O is on Dispatchers.IO

### ✅ GOOD: Repository Pattern
- All data access through repositories
- Repositories handle error recovery
- Atomic file writes (JsonStorage)
- No direct SharedPreferences manipulation

### ⚠️ MEDIUM: Endpoint Deletion
- Deleting endpoint leaves dangling references in sources
- Filtered at runtime (safe but not ideal)
- **Recommendation:** Add cascade check or warning UI

### ⚠️ MEDIUM: Test Harness Side Effects
- Test harness writes REAL data to Firestore
- No "test mode" flag in Cloud Function
- **Mitigation:** Debug-only Activity, cannot run in release

### ✅ GOOD: Firestore Ingest Isolation
- Completely isolated from existing delivery
- No shared state with DataPipeline
- Failures cannot affect production flow
- Debug-only test harness

---

**END OF CARD WIRING MATRIX**

