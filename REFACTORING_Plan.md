# AlertsToSheets Refactoring Plan

**Date:** December 28, 2025  
**Goal:** Transform the current application into a configuration-centric, multi-endpoint notification forwarder with a step-by-step wizard flow and dashboard tiles.

---

## Executive Summary

This plan refactors the AlertsToSheets Android application to support multiple user-defined configurations, each independently routing data from a selected source (App Notification, SMS, or Gmail) to one or more HTTP POST endpoints. The refactor prioritizes reliability improvements first, then introduces the new Configuration model and wizard UI, while maintaining backwards compatibility with existing functionality.

---

## Current State Analysis

### What Works Well
- Core notification interception via `AlertsNotificationListener` (foreground service)
- SMS capture via `AlertsSmsReceiver` (broadcast receiver)
- BNN parsing logic with pipe-delimited format support
- Template engine with proper JSON escaping
- Source/Endpoint/Template repository pattern (JSON file storage)
- Dashboard with status indicators

### Critical Issues Identified

1. **Reliability Gap (ROOT CAUSE OF INTERMITTENT SUCCESS)**
   - V2 `DataPipeline` bypasses `QueueProcessor` entirely
   - HTTP calls made directly without retry/persistence
   - If network fails, notifications are lost
   - `AlertsSmsReceiver` creates new `DataPipeline` instance per SMS (inefficient)

2. **Architecture Mismatch**
   - Current `Source` model tied to single endpoint (`endpointId`)
   - No "Configuration" concept bundling source + template + multiple endpoints
   - No step-by-step wizard flow for configuration creation
   - Dashboard shows feature cards, not configuration tiles

3. **Dual Logging Systems**
   - `Logger` class (V2) - stores to `logs.json`
   - `LogRepository` object (V1) - stores to SharedPreferences
   - Both used in different parts of the codebase

4. **Template Field Mismatch**
   - Current SMS template: `sender`, `message`, `time`, `timestamp`
   - User wants: `sender`, `sms_message`, `source`, `time`
   - Current App template: `package`, `title`, `text`, `bigText`, `time`, `timestamp`
   - User wants: `big_text`, `sub_text`, `text`, `title`

---

## Proposed Architecture

### New Configuration Model

```kotlin
data class Configuration(
    val id: String,                           // UUID
    val name: String,                         // User-defined name
    val enabled: Boolean = true,              // Master toggle
    
    // Source Definition
    val sourceType: SourceType,               // APP, SMS, GMAIL
    val selector: String,                     // Package name, phone number, or email
    val parserId: String = "generic",         // "bnn", "generic", "sms", "gmail"
    
    // Template
    val templateId: String,                   // Reference to template
    val autoClean: Boolean = false,           // Remove emojis/symbols
    
    // Endpoints (MULTIPLE!)
    val endpointIds: List<String>,            // List of endpoint IDs
    
    // Step Completion Tracking
    val stepCompletion: StepCompletion,       // Tracks wizard progress
    
    // Status & Stats
    val lastTestResult: TestResult? = null,   // Last test payload result
    val stats: ConfigurationStats,            // Usage statistics
    
    // Metadata
    val createdAt: Long,
    val updatedAt: Long
)

data class StepCompletion(
    val step1_named: Boolean = false,
    val step2_sourceSelected: Boolean = false,
    val step3_templateSelected: Boolean = false,
    val step4_templateReviewed: Boolean = false,
    val step5_endpointsAdded: Boolean = false,
    val step6_testSent: Boolean = false
) {
    val isComplete: Boolean
        get() = step1_named && step2_sourceSelected && step3_templateSelected &&
                step4_templateReviewed && step5_endpointsAdded && step6_testSent
}

enum class SourceType {
    APP,    // App notification
    SMS,    // SMS message
    GMAIL   // Gmail message (future)
}
```

### Updated Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION/SMS CAPTURE                      │
│  AlertsNotificationListener / AlertsSmsReceiver                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                 CONFIGURATION RESOLVER                           │
│  - Find matching Configuration(s) by selector                    │
│  - Return list of configs that should receive this data          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      DATA PIPELINE                               │
│  For each matching Configuration:                                │
│    1. Get parser by parserId                                     │
│    2. Parse raw notification                                     │
│    3. Get template by templateId                                 │
│    4. Apply template (with autoClean if enabled)                 │
│    5. For each endpointId in config:                             │
│       → Enqueue to QueueProcessor (PERSISTENT!)                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    QUEUE PROCESSOR                               │
│  - SQLite-backed persistent queue                                │
│  - Retry with exponential backoff (max 10 retries)               │
│  - Per-endpoint success/failure tracking                         │
│  - Updates LogRepository on completion                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Implementation Phases

### Phase 1: Reliability Foundation (Priority: CRITICAL)

**Goal:** Fix the root cause of intermittent success by restoring persistent queueing.

#### 1.1 Integrate QueueProcessor into DataPipeline

**File:** `domain/DataPipeline.kt`

**Changes:**
- Remove direct `HttpClient.post()` calls
- Add `QueueProcessor.enqueue()` for each endpoint
- Support multi-endpoint fanout (enqueue once per endpoint)

```kotlin
// Current (broken):
val response = httpClient.post(url = endpoint.url, body = json, ...)

// Proposed (reliable):
for (endpointId in configuration.endpointIds) {
    val endpoint = endpointRepo.getById(endpointId) ?: continue
    if (!endpoint.enabled) continue
    
    QueueProcessor.enqueue(
        context = context,
        url = endpoint.url,
        payload = json,
        logId = logEntry.id,
        configId = configuration.id,
        endpointId = endpointId
    )
}
```

#### 1.2 Enhance QueueProcessor for Multi-Endpoint

**File:** `QueueProcessor.kt`

**Changes:**
- Add `configId` and `endpointId` to queue entries
- Track per-endpoint success/failure
- Update endpoint stats on completion

**File:** `data/QueueDbHelper.kt`

**Changes:**
- Add columns: `config_id`, `endpoint_id`
- Migration for existing database

#### 1.3 Fix AlertsSmsReceiver Efficiency

**File:** `services/AlertsSmsReceiver.kt`

**Changes:**
- Use singleton DataPipeline instead of creating new instance per SMS
- Or use Application-scoped pipeline

```kotlin
// Current (inefficient):
val pipeline = DataPipeline(context.applicationContext)
pipeline.processSms(sender, raw)

// Proposed:
AlertsApplication.getPipeline().processSms(sender, raw)
```

#### 1.4 Consolidate Logging

**Decision:** Use `LogRepository` as the single source of truth for UI-visible logs.

**Changes:**
- Remove `Logger` class usage from DataPipeline
- Use `LogRepository.addLog()` consistently
- Add `configId` field to `LogEntry` for filtering

---

### Phase 2: Configuration Model & Repository

**Goal:** Introduce the Configuration model without breaking existing functionality.

#### 2.1 Create Configuration Model

**New File:** `domain/models/Configuration.kt`

```kotlin
package com.example.alertsheets.domain.models

data class Configuration(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val sourceType: SourceType,
    val selector: String,
    val parserId: String = "generic",
    val templateId: String,
    val autoClean: Boolean = false,
    val endpointIds: List<String>,
    val stepCompletion: StepCompletion = StepCompletion(),
    val lastTestResult: TestResult? = null,
    val stats: ConfigurationStats = ConfigurationStats(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class StepCompletion(
    val step1_named: Boolean = false,
    val step2_sourceSelected: Boolean = false,
    val step3_templateSelected: Boolean = false,
    val step4_templateReviewed: Boolean = false,
    val step5_endpointsAdded: Boolean = false,
    val step6_testSent: Boolean = false
) {
    val isComplete: Boolean
        get() = step1_named && step2_sourceSelected && step3_templateSelected &&
                step4_templateReviewed && step5_endpointsAdded && step6_testSent
                
    val completedSteps: Int
        get() = listOf(step1_named, step2_sourceSelected, step3_templateSelected,
                       step4_templateReviewed, step5_endpointsAdded, step6_testSent)
                       .count { it }
}

data class TestResult(
    val timestamp: Long,
    val success: Boolean,
    val message: String,
    val testType: String  // "standard" or "dirty"
)

data class ConfigurationStats(
    val totalProcessed: Int = 0,
    val totalSent: Int = 0,
    val totalFailed: Int = 0,
    val lastActivity: Long = 0L
)
```

#### 2.2 Create ConfigurationRepository

**New File:** `data/repositories/ConfigurationRepository.kt`

```kotlin
package com.example.alertsheets.data.repositories

class ConfigurationRepository(private val context: Context) {
    
    private val storage = JsonStorage(context, "configurations.json")
    private val gson = Gson()
    
    fun getAll(): List<Configuration>
    fun getById(id: String): Configuration?
    fun getEnabled(): List<Configuration>
    fun findBySelector(sourceType: SourceType, selector: String): List<Configuration>
    fun save(config: Configuration)
    fun delete(id: String)
    fun updateStats(id: String, processed: Int?, sent: Int?, failed: Int?)
    fun updateStepCompletion(id: String, step: StepCompletion)
    fun updateTestResult(id: String, result: TestResult)
}
```

#### 2.3 Create ConfigurationManager

**New File:** `domain/ConfigurationManager.kt`

```kotlin
package com.example.alertsheets.domain

class ConfigurationManager(context: Context) {
    
    private val repository = ConfigurationRepository(context)
    private val endpointRepo = EndpointRepository(context)
    private val templateRepo = TemplateRepository(context)
    
    /**
     * Find all configurations that should receive this notification
     */
    fun findConfigurationsForNotification(packageName: String): List<Configuration>
    
    /**
     * Find all configurations that should receive this SMS
     */
    fun findConfigurationsForSms(sender: String): List<Configuration>
    
    /**
     * Validate configuration is complete and ready
     */
    fun isConfigurationReady(config: Configuration): Boolean
    
    /**
     * Get status indicator color for dashboard
     */
    fun getStatusColor(config: Configuration): Int
}
```

---

### Phase 3: Template Alignment & Compatibility

**Goal:** Align default templates with user requirements while maintaining backwards compatibility.

#### 3.1 Update Default Templates

**File:** `PrefsManager.kt` (and `TemplateRepository.kt`)

**New SMS Default Template:**
```json
{
  "sender": "{{sender}}",
  "sms_message": "{{message}}",
  "source": "sms",
  "time": "{{time}}"
}
```

**New App Notification Default Template:**
```json
{
  "big_text": "{{bigText}}",
  "sub_text": "{{subText}}",
  "text": "{{text}}",
  "title": "{{title}}"
}
```

#### 3.2 Add Variable Aliases

**File:** `utils/TemplateEngine.kt`

**Changes:**
- Support both old and new variable names
- Add alias mapping for backwards compatibility

```kotlin
private val variableAliases = mapOf(
    "sms_message" to "message",
    "big_text" to "bigText",
    "sub_text" to "subText"
)

private fun applyVariables(...) {
    // ... existing code ...
    
    // Also replace aliased variables
    for ((alias, original) in variableAliases) {
        val placeholder = "{{$alias}}"
        if (result.contains(placeholder) && allVariables.containsKey(original)) {
            val value = allVariables[original] ?: ""
            val cleanValue = if (autoClean) cleanText(value) else value
            val escapedValue = escape(cleanValue)
            result = result.replace(placeholder, escapedValue)
        }
    }
}
```

---

### Phase 4: Configuration Wizard UI

**Goal:** Create step-by-step wizard for configuration creation.

#### 4.1 Create ConfigurationWizardActivity

**New File:** `ui/wizard/ConfigurationWizardActivity.kt`

**Structure:**
- Single Activity with ViewPager2 or Fragment-based steps
- ConfigurationDraft stored in ViewModel (survives rotation)
- Draft persisted to SharedPreferences (survives app kill)
- Step indicators at top showing completion status

**Steps:**

1. **Step 1: Configuration Naming**
   - EditText for configuration name
   - Validation: non-empty, unique name

2. **Step 2: Source Selection**
   - RadioGroup: App Notification / SMS Message / Gmail Message
   - Sub-selection based on type:
     - App: Show installed apps list (reuse AppsListActivity logic)
     - SMS: Show contact picker or manual phone number entry
     - Gmail: Email address input (future)

3. **Step 3: Template Selection**
   - Spinner/RecyclerView of available templates
   - Show default template for selected source type
   - Preview of template content

4. **Step 4: Template Review & Editing**
   - EditText showing template JSON
   - If edited, prompt to save as new template
   - Validate JSON syntax

5. **Step 5: Endpoint Addition**
   - RecyclerView of available endpoints with checkboxes
   - "Add New Endpoint" button
   - At least one endpoint required

6. **Step 6: Test Payload**
   - Two test buttons:
     - "Send Standard Test" - clean fire alert
     - "Send Dirty Test" - emoji-rich message
   - Show test results (success/failure per endpoint)

#### 4.2 Create Step Fragments

**New Files:**
- `ui/wizard/Step1NameFragment.kt`
- `ui/wizard/Step2SourceFragment.kt`
- `ui/wizard/Step3TemplateFragment.kt`
- `ui/wizard/Step4ReviewFragment.kt`
- `ui/wizard/Step5EndpointsFragment.kt`
- `ui/wizard/Step6TestFragment.kt`

#### 4.3 Create WizardViewModel

**New File:** `ui/wizard/ConfigurationWizardViewModel.kt`

```kotlin
class ConfigurationWizardViewModel : ViewModel() {
    
    private val _draft = MutableLiveData<ConfigurationDraft>()
    val draft: LiveData<ConfigurationDraft> = _draft
    
    private val _currentStep = MutableLiveData<Int>(1)
    val currentStep: LiveData<Int> = _currentStep
    
    fun updateName(name: String)
    fun updateSourceType(type: SourceType)
    fun updateSelector(selector: String)
    fun updateTemplate(templateId: String)
    fun updateEndpoints(endpointIds: List<String>)
    fun markStepComplete(step: Int)
    fun saveConfiguration(): Configuration
    fun loadDraft(draftId: String?)
    fun saveDraft()
}
```

---

### Phase 5: Dashboard Refactor

**Goal:** Replace card-based dashboard with configuration tiles.

#### 5.1 Update MainActivity Layout

**File:** `res/layout/activity_main_dashboard.xml`

**Changes:**
- Add RecyclerView for configuration tiles
- Keep utility row for Permissions, Logs, Settings
- Add FAB for "New Configuration"

#### 5.2 Create ConfigurationTileAdapter

**New File:** `ui/adapters/ConfigurationTileAdapter.kt`

**Tile Contents:**
- Configuration name
- Source type icon + selector preview
- Enabled/Disabled toggle switch
- Status indicator (green/yellow/red)
- Tap to edit, long-press for options

#### 5.3 Update MainActivity

**File:** `ui/MainActivity.kt`

**Changes:**
- Load configurations from ConfigurationRepository
- Display as tiles in RecyclerView
- FAB launches ConfigurationWizardActivity
- Tile tap opens wizard in edit mode

#### 5.4 Status Indicator Logic

**Green Light Conditions:**
- All 6 wizard steps complete
- At least one endpoint enabled
- Last test successful (within 24 hours) OR never tested but all steps complete

**Yellow Light Conditions:**
- Configuration incomplete (missing steps)
- No endpoints enabled

**Red Light Conditions:**
- Last test failed
- All endpoints disabled or deleted

---

### Phase 6: Test Payload Implementation

**Goal:** Implement standard and "dirty" test payloads.

#### 6.1 Create TestPayloadGenerator

**New File:** `domain/TestPayloadGenerator.kt`

```kotlin
object TestPayloadGenerator {
    
    /**
     * Generate standard emergency fire alert test
     */
    fun generateStandardTest(sourceType: SourceType): RawNotification {
        return when (sourceType) {
            SourceType.APP -> RawNotification(
                packageName = "com.test.alerts",
                title = "FIRE ALERT",
                text = "Structure fire reported at 123 Main Street",
                bigText = "Structure fire reported at 123 Main Street. " +
                         "Multiple units responding. Command established."
            )
            SourceType.SMS -> RawNotification.fromSms(
                sender = "+1-555-DISPATCH",
                message = "FIRE ALERT: Structure fire at 123 Main St. " +
                         "Engine 1, Ladder 1 responding."
            )
            SourceType.GMAIL -> TODO("Gmail not yet implemented")
        }
    }
    
    /**
     * Generate "dirty" test with emojis and special characters
     */
    fun generateDirtyTest(sourceType: SourceType): RawNotification {
        return when (sourceType) {
            SourceType.APP -> RawNotification(
                packageName = "com.test.alerts",
                title = "🔥 FIRE ALERT 🚒",
                text = "Structure fire @ 123 Main St! \"Urgent\" - 2nd alarm",
                bigText = "🔥🔥🔥 STRUCTURE FIRE 🔥🔥🔥\n" +
                         "Location: 123 Main Street\n" +
                         "Details: \"Heavy smoke showing\" - Command says:\n" +
                         "'All hands working' & requesting 2nd alarm\n" +
                         "Units: E-1/L-1/R-1 🚒🚑\n" +
                         "Special chars: <>&\"'\\n\\t"
            )
            SourceType.SMS -> RawNotification.fromSms(
                sender = "+1-555-DISPATCH",
                message = "🔥 FIRE @ 123 Main St! \"Heavy smoke\" - " +
                         "E-1/L-1 responding 🚒 Special: <>&\"'\\n"
            )
            SourceType.GMAIL -> TODO("Gmail not yet implemented")
        }
    }
}
```

#### 6.2 Integrate Test into Wizard

**File:** `ui/wizard/Step6TestFragment.kt`

**Features:**
- "Send Standard Test" button
- "Send Dirty Test" button
- Progress indicator during send
- Results display (per endpoint)
- Retry button on failure

---

### Phase 7: Migration & Compatibility

**Goal:** Migrate existing V1/V2 data to new Configuration model.

#### 7.1 Create MigrationManager

**New File:** `data/migration/ConfigurationMigrationManager.kt`

**Migration Logic:**
1. Read existing Sources from SourceRepository
2. For each Source, create a Configuration:
   - `name` = Source.name
   - `sourceType` = Source.type
   - `selector` = Source.id (package name or "sms:phone")
   - `templateId` = Source.templateId
   - `endpointIds` = [Source.endpointId]
   - `stepCompletion` = all steps marked complete (legacy data)
3. Save to ConfigurationRepository
4. Mark migration complete in SharedPreferences

#### 7.2 Backwards Compatibility

**Keep Existing:**
- `SourceRepository` - for internal matching logic
- `EndpointRepository` - endpoints are shared across configurations
- `TemplateRepository` - templates are shared across configurations
- `PrefsManager` - for legacy settings

**Deprecate:**
- Direct Source creation from AppsListActivity
- Direct Source creation from SmsConfigActivity
- These should create Configurations instead

---

## File Modifications Summary

### New Files to Create

| File | Purpose |
|------|---------|
| `domain/models/Configuration.kt` | Configuration data model |
| `data/repositories/ConfigurationRepository.kt` | Configuration CRUD operations |
| `domain/ConfigurationManager.kt` | Configuration business logic |
| `domain/TestPayloadGenerator.kt` | Test payload generation |
| `ui/wizard/ConfigurationWizardActivity.kt` | Wizard host activity |
| `ui/wizard/ConfigurationWizardViewModel.kt` | Wizard state management |
| `ui/wizard/Step1NameFragment.kt` | Step 1 UI |
| `ui/wizard/Step2SourceFragment.kt` | Step 2 UI |
| `ui/wizard/Step3TemplateFragment.kt` | Step 3 UI |
| `ui/wizard/Step4ReviewFragment.kt` | Step 4 UI |
| `ui/wizard/Step5EndpointsFragment.kt` | Step 5 UI |
| `ui/wizard/Step6TestFragment.kt` | Step 6 UI |
| `ui/adapters/ConfigurationTileAdapter.kt` | Dashboard tile adapter |
| `data/migration/ConfigurationMigrationManager.kt` | V1/V2 migration |
| `res/layout/activity_wizard.xml` | Wizard layout |
| `res/layout/fragment_step_*.xml` | Step fragment layouts |
| `res/layout/item_configuration_tile.xml` | Tile item layout |

### Files to Modify

| File | Changes |
|------|---------|
| `domain/DataPipeline.kt` | Integrate QueueProcessor, support multi-endpoint |
| `QueueProcessor.kt` | Add configId, endpointId tracking |
| `data/QueueDbHelper.kt` | Add new columns, migration |
| `services/AlertsSmsReceiver.kt` | Use singleton pipeline |
| `services/AlertsNotificationListener.kt` | Use ConfigurationManager |
| `ui/MainActivity.kt` | Add configuration tiles, FAB |
| `res/layout/activity_main_dashboard.xml` | Add RecyclerView, FAB |
| `utils/TemplateEngine.kt` | Add variable aliases |
| `PrefsManager.kt` | Update default templates |
| `data/repositories/TemplateRepository.kt` | Update defaults |
| `AndroidManifest.xml` | Add ConfigurationWizardActivity |

### Files to Deprecate (Keep for Compatibility)

| File | Status |
|------|--------|
| `AppsListActivity.kt` | Keep, but redirect to wizard |
| `SmsConfigActivity.kt` | Keep, but redirect to wizard |
| `AppConfigActivity.kt` | Keep for template editing only |

---

## Implementation Order

### Week 1: Reliability Foundation
1. Integrate QueueProcessor into DataPipeline
2. Fix AlertsSmsReceiver efficiency
3. Add multi-endpoint support to queue
4. Test reliability improvements

### Week 2: Configuration Model
1. Create Configuration model
2. Create ConfigurationRepository
3. Create ConfigurationManager
4. Create migration logic

### Week 3: Template & Compatibility
1. Update default templates
2. Add variable aliases
3. Test backwards compatibility
4. Run migration on test data

### Week 4: Wizard UI (Steps 1-3)
1. Create wizard activity structure
2. Implement Step 1 (naming)
3. Implement Step 2 (source selection)
4. Implement Step 3 (template selection)

### Week 5: Wizard UI (Steps 4-6)
1. Implement Step 4 (template review)
2. Implement Step 5 (endpoints)
3. Implement Step 6 (test payloads)
4. Test complete wizard flow

### Week 6: Dashboard & Polish
1. Update MainActivity with tiles
2. Create ConfigurationTileAdapter
3. Implement status indicators
4. Final testing and bug fixes

---

## Risk Mitigation

### Risk 1: Breaking Existing Functionality
**Mitigation:** 
- Keep all existing repositories and models
- Migration creates Configurations from existing Sources
- Fallback to Source-based matching if Configuration not found

### Risk 2: Permissions on Android 15
**Mitigation:**
- Document which permissions are achievable
- Guide users to best-effort permission state
- SMS capture works best as default SMS app (ROLE_SMS)
- Notification capture requires NotificationListenerService permission

### Risk 3: Multi-Endpoint Partial Failures
**Mitigation:**
- Queue entries are per-endpoint
- Each endpoint retries independently
- Dashboard shows per-endpoint status
- Logs show which endpoints succeeded/failed

### Risk 4: Template JSON Validity
**Mitigation:**
- TemplateEngine already has proper escaping
- Validate JSON syntax before saving custom templates
- "Dirty" test specifically tests escaping

---

## Success Criteria

1. **Reliability:** No lost notifications due to network failures (queue persists)
2. **Multi-Endpoint:** Single notification can be sent to multiple endpoints
3. **Wizard Flow:** Users can create configurations through 6-step wizard
4. **Dashboard Tiles:** Configurations appear as interactive tiles
5. **Status Indicators:** Green light when configuration is complete and working
6. **Test Payloads:** Both standard and dirty tests work correctly
7. **Backwards Compatibility:** Existing Sources/Endpoints/Templates still work

---

## Questions for User

1. **Gmail Implementation:** Should Gmail source be stubbed out in the wizard (disabled with "Coming Soon" label) or completely hidden?

2. **Migration Behavior:** Should existing Sources be automatically migrated to Configurations on app update, or should users manually recreate them?

3. **Endpoint Sharing:** Should endpoints be shared across all configurations (current design), or should each configuration have its own copy of endpoint settings?

4. **Test Frequency:** Should the "green light" status require periodic re-testing, or is a one-time successful test sufficient?

5. **Notification Grouping:** If multiple configurations match the same notification (e.g., two configs for the same app), should it be sent to all matching configurations?
