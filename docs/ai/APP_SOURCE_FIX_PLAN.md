# APP Source (BNN) Fix Plan - Android Only

**Created:** 2025-12-31 01:30 UTC  
**Target Branch:** `fix/wiring-sources-endpoints`  
**Scope:** Android app only (NO Apps Script changes)  
**Authoritative Sources:** `parsing.md`, attached screenshots, evidence JSON files

---

## 🎯 OBJECTIVE

Fix APP source (BNN) delivery so that:
1. **ALL placeholders are correctly substituted** - support both `{{key}}` and `{key}` syntax
2. **Multi-endpoint fanout works** - each endpoint gets its own independent HTTP POST
3. **Per-card independence maintained** - no shared mutable state across cards
4. **Persistence guarantees met** - survives app restart, process death, device reboot
5. **Source ID stability enforced** - IDs generated once, never regenerated on Save
6. **Observability added** - explicit logs for resolved Source ID, endpointIds, payload variables

---

## 🔍 ROOT CAUSE ANALYSIS

### PRIMARY BUG: APP Templating Uses Wrong Variable Map

**File:** `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`  
**Lines:** 110, 61-110

**Problem:**

```kotlin
// Line 110 in DataPipeline.kt (OLD PATH)
val json = TemplateEngine.apply(templateContent, parsedWithTimestamp, source)
```

1. **APP notifications** have fields from `RawNotification`:
   - `packageName`: `"us.bnn.newsapp"`
   - `title`: `"New Media"` 
   - `text`: `"NY| Westchester| Eastchester| Smoke Condition..."`
   - `bigText`: (expanded version)
   - `timestamp`: `1767202986652`

2. **`TemplateEngine.apply()`** expects `ParsedData` which has:
   - `incidentId`, `state`, `county`, `city`, `address`, `incidentType`, `incidentDetails`, `fdCodes`, `timestamp`, `originalBody`

3. **APP templates use** (from `sources_2025-12-29.json`, BNN source):
   ```json
   {
     "source": "app",
     "package": "{{package}}",
     "title": "{{title}}",
     "text": "{{text}}",
     "bigText": "{{bigText}}",
     "time": "{{time}}",
     "timestamp": "{{timestamp}}"
   }
   ```

4. **Result:** `{{package}}`, `{{title}}`, `{{text}}`, `{{bigText}}` are NOT in the `ParsedData.toVariableMap()` → placeholders remain unsubstituted

**Evidence:**
- Screenshot `sheet-bad-parsing.png` shows APP rows with literal `{package}`, `{title}`, `{bigText}`
- Screenshot `notiflog-bnn-westchester.png` confirms RAW JSON has `packageName`, `title`, `text`, `bigText`

**Why SMS works but APP doesn't:**
- **SMS** uses NEW Golden Path: `DeliveryPipeline.deliverSmsEvent()` (line 53) → creates `smsVariables` map directly from sender/message
- **APP** uses OLD path: `DataPipeline.process()` → goes through Parser → ParsedData → wrong variable map

---

### SECONDARY ISSUE: Two Separate Delivery Paths

**Problem:**
- SMS: `DeliveryPipeline.deliverSmsEvent()` (NEW, works correctly)
- APP: `DataPipeline.process()` → parser → template (OLD, broken for APP templates)

**Files:**
- `android/app/src/main/java/com/example/alertsheets/services/AlertsNotificationListener.kt` line 106:
  ```kotlin
  pipeline.processAppNotification(packageName, raw)
  ```
- `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt` lines 264-293:
  ```kotlin
  fun processAppNotification(packageName: String, raw: RawNotification) {
      val source = sourceManager.findSourceForNotification(packageName)
      if (source != null) {
          process(source, raw)  // ❌ Goes to OLD path
      }
  }
  ```

**Result:** APP and SMS have different code paths, causing inconsistency

---

### ADDITIONAL FINDINGS (From User Requirements)

1. **Multi-endpoint fanout**: WORKS in OLD path (lines 166-238 in DataPipeline.kt) but not tested for APP
2. **Per-card independence**: Risk of shared state in `sourceManager.getTemplateJsonForSource()`
3. **Persistence**: Needs verification (user reported cards revert to defaults)
4. **Source ID stability**: Need to verify no UUID regeneration on Save
5. **Picker safety**: Need to trace contact/app picker result persistence

---

## 🛠️ SOLUTION DESIGN

### Option A: Unified Golden Path (RECOMMENDED)

**Migrate APP notifications to use DeliveryPipeline (same as SMS)**

**Benefits:**
- Single code path for both APP and SMS
- Consistent templating logic
- Already has multi-endpoint fanout
- Already has observability/logging
- Reduces code duplication

**Implementation:**
1. Add `DeliveryPipeline.deliverAppEvent()` method
2. Create `appVariables` map directly from `RawNotification` fields
3. Support both `{{key}}` and `{key}` placeholder syntax
4. Route APP notifications from `DataPipeline.processAppNotification()` to new method
5. Keep OLD path for backward compatibility (if needed)

---

## 📦 IMPLEMENTATION PLAN

### Phase 1: Add APP Delivery to DeliveryPipeline ✅

**File:** `android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt`

**Add new method after `deliverSmsEvent()` (around line 485):**

```kotlin
/**
 * Deliver APP notification event via Golden Path
 * Uses same multi-endpoint fanout logic as SMS
 */
fun deliverAppEvent(
    context: Context,
    packageName: String,
    title: String,
    text: String,
    bigText: String,
    timestamp: Long = System.currentTimeMillis()
) {
    Log.d(TAG, "📱 deliverAppEvent() | package=$packageName title=${title.take(50)}")
    
    // Step 1: Find matching source by packageName
    val sourceManager = SourceManager(context)
    val source = sourceManager.findSourceByPackageName(packageName)
    
    if (source == null) {
        Log.w(TAG, "❌ No source configured for package: $packageName")
        
        StructuredLogger.logEvent(
            level = "INFO",
            sourceId = null,
            endpointId = null,
            alertId = null,
            event = "app_ignored",
            details = "reason=no_matching_source package=$packageName"
        )
        
        return
    }
    
    if (!source.enabled) {
        Log.w(TAG, "❌ Source disabled: ${source.name}")
        return
    }
    
    Log.d(TAG, "✓ Source matched: ${source.name} (${source.id})")
    
    val alertId = "app-${System.currentTimeMillis()}"
    
    // Step 2: Create APP variable map (similar to smsVariables)
    val dateFormat = SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)
    val appVariables = mapOf(
        "package" to packageName,
        "packageName" to packageName,  // Alias
        "title" to title,
        "text" to text,
        "bigText" to bigText,
        "time" to SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US).format(Date(timestamp)),
        "timestamp" to dateFormat.format(Date(timestamp))
    )
    
    Log.d(TAG, "✓ APP variables created: ${appVariables.keys.joinToString(", ")}")
    
    // Step 3-N: Same as SMS (endpoint resolution, template rendering, HTTP fanout)
    // ... (copy logic from deliverSmsEvent lines 120-450)
}
```

**Key Changes:**
- Creates `appVariables` map with APP-specific keys
- Supports both `package` and `packageName` (alias)
- Uses same multi-endpoint fanout logic as SMS

---

### Phase 2: Add Support for Both `{{key}}` and `{key}` Syntax ✅

**File:** `android/app/src/main/java/com/example/alertsheets/utils/TemplateEngine.kt`

**Modify `applyVariables()` method (around line 149):**

```kotlin
// Replace each variable (support both {{key}} and {key} syntax)
for ((key, value) in allVariables) {
    val placeholder1 = "{{$key}}"  // Standard syntax
    val placeholder2 = "{$key}"    // Alternative syntax
    
    if (result.contains(placeholder1) || result.contains(placeholder2)) {
        val cleanValue = if (autoClean) cleanText(value) else value
        val finalValue = if (isJsonValue(cleanValue)) {
            cleanValue
        } else {
            escapeForJson(cleanValue)
        }
        
        // Replace both syntaxes
        result = result.replace(placeholder1, finalValue)
        result = result.replace(placeholder2, finalValue)
    }
}
```

---

### Phase 3: Route APP Notifications to Golden Path ✅

**File:** `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`

**Modify `processAppNotification()` method (line 264):**

```kotlin
fun processAppNotification(packageName: String, raw: RawNotification) {
    Log.d(TAG, "📱 processAppNotification() routing to DeliveryPipeline | package=$packageName")
    
    // Route to Golden Path delivery pipeline (same as SMS)
    DeliveryPipeline.deliverAppEvent(
        context = context,
        packageName = packageName,
        title = raw.title,
        text = raw.text,
        bigText = raw.bigText,
        timestamp = raw.timestamp
    )
}
```

---

### Phase 4: Add SourceManager Helper Method ✅

**File:** `android/app/src/main/java/com/example/alertsheets/domain/SourceManager.kt`

**Add method (if not exists):**

```kotlin
fun findSourceByPackageName(packageName: String): Source? {
    return getSources().firstOrNull { it.matchesPackage(packageName) }
}
```

---

### Phase 5: Enhanced Observability Logging ✅

**Add to DeliveryPipeline.deliverAppEvent():**

```kotlin
Log.d(TAG, "🔍 Resolved Source ID: ${source.id}")
Log.d(TAG, "🔍 Endpoint IDs: ${source.endpointIds.joinToString(", ")}")
Log.d(TAG, "🔍 APP Variables: ${appVariables.keys.joinToString(", ")}")
Log.d(TAG, "🔍 Template preview: ${source.templateJson.take(200)}")
```

**Add before HTTP send:**

```kotlin
Log.d(TAG, "📤 Final payload (first 300 chars, redacted): ${redactPhoneNumbers(json.take(300))}")
```

---

## ✅ ACCEPTANCE CRITERIA

### APP Templating
- [ ] APP notifications correctly substitute `{{package}}` → real package name
- [ ] APP notifications correctly substitute `{{title}}` → real title
- [ ] APP notifications correctly substitute `{{text}}` → real text  
- [ ] APP notifications correctly substitute `{{bigText}}` → real bigText
- [ ] Both `{{key}}` and `{key}` syntax work
- [ ] No placeholder strings reach HTTP endpoints
- [ ] Sheet rows show real values (not `{package}`, `{title}`, etc.)

### Multi-Endpoint Fanout
- [ ] APP notification sent to ALL endpoints in `source.endpointIds`
- [ ] Each endpoint receives independent HTTP POST
- [ ] Failure of one endpoint doesn't block others
- [ ] Success/failure logged per endpoint

### Per-Card Independence
- [ ] Editing BNN card doesn't affect SMS cards
- [ ] Saving BNN card doesn't mutate SMS templates
- [ ] Each source has isolated `templateJson` and `endpointIds`

### Persistence
- [ ] Source config survives app restart
- [ ] Source config survives process death  
- [ ] Source config survives device reboot
- [ ] Saving a card is idempotent (no duplicate creation)

### Source ID Stability
- [ ] Source IDs generated once at creation
- [ ] Saving existing card NEVER regenerates ID
- [ ] No UUID regeneration on Save button click

### Observability
- [ ] Logs show resolved Source ID
- [ ] Logs show resolved endpoint IDs
- [ ] Logs show APP variable map keys
- [ ] Logs show final payload preview (redacted)
- [ ] If notification ignored, log shows WHY + which source checked

---

## 🧪 VERIFICATION STEPS

### Test 1: BNN APP Notification (New Row)

```powershell
# Prerequisites:
# - BNN source configured with correct template ({{package}}, {{title}}, etc.)
# - BNN source pointing to FD-Codes-Analytics endpoint (enabled)

# Monitor logs
adb logcat -c
adb logcat -v time -s DeliveryPipeline:* DataPipeline:* StructuredLogger:* *:E

# Trigger BNN notification from device
# (e.g., via BNN app or test notification)

# Expected logcat:
# DataPipeline: 📱 processAppNotification() routing to DeliveryPipeline | package=us.bnn.newsapp
# DeliveryPipeline: 📱 deliverAppEvent() | package=us.bnn.newsapp title=New Media
# DeliveryPipeline: ✓ Source matched: BNN (us.bnn.newsapp)
# DeliveryPipeline: ✓ APP variables created: package, packageName, title, text, bigText, time, timestamp
# DeliveryPipeline: ✓ Payload rendered (450 chars, 5 placeholders resolved)
# DeliveryPipeline: 📤 Sending to: FD-Codes-Analytics...
# DeliveryPipeline: ✅ HTTP OK | code=200 latency=XXXms

# Verify Google Sheet:
# - New row with REAL values (not placeholders)
# - bigText column shows full notification text
# - title/text columns show real values
```

### Test 2: BNN APP Notification (Update - Same Incident)

```powershell
# Send SECOND BNN notification with SAME incident ID

# Expected:
# - SAME row updated (not new row)
# - Status appended: "New Incident\nUpdate"
# - Timestamp appended with newline
# - Details appended with newline
```

### Test 3: Both `{{key}}` and `{key}` Syntax

```powershell
# Edit BNN source template to use mixed syntax:
# {
#   "package": "{package}",
#   "title": "{{title}}",
#   "text": "{text}",
#   "bigText": "{{bigText}}"
# }

# Send notification

# Expected:
# - Both syntaxes work
# - Sheet shows real values for all fields
```

### Test 4: Multi-Endpoint Fanout

```powershell
# Prerequisites:
# - BNN source has 2 endpoints: FD-Codes-Analytics + Firestore Ingest

# Send BNN notification

# Expected logcat:
# DeliveryPipeline: 📤 Delivering to 2 endpoints
# DeliveryPipeline: ✅ HTTP OK | endpoint=FD-Codes-Analytics code=200
# DeliveryPipeline: ✅ HTTP OK | endpoint=Firestore-Ingest code=200

# Verify:
# - FD-Codes-Analytics sheet has new row
# - Firestore /alerts collection has new document
```

### Test 5: SMS Still Works (No Regression)

```powershell
# Send SMS from configured number

# Expected:
# - SMS uses smsVariables map (sender, message, body, time, timestamp)
# - Sheet shows real sender + message (not placeholders)
# - No regression from APP changes
```

---

## 🚨 CONSTRAINTS & SAFETY

### SMS Behavior Must Not Change
- SMS delivery path untouched except shared template engine fix
- `deliverSmsEvent()` logic unchanged
- `smsVariables` map unchanged

### No Apps Script Changes
- All fixes Android-only
- Apps Script `Code.gs` unchanged
- Sheet schema unchanged

### Backward Compatibility
- OLD `DataPipeline.process()` path kept for migration
- Existing parsers (BNN, generic) still work
- Existing templates still work

---

## 📝 FILES TO MODIFY

1. **`android/app/src/main/java/com/example/alertsheets/domain/DeliveryPipeline.kt`** (~150 lines added)
   - Add `deliverAppEvent()` method
   - Add `appVariables` map creation
   - Add APP-specific logging

2. **`android/app/src/main/java/com/example/alertsheets/utils/TemplateEngine.kt`** (~10 lines changed)
   - Modify `applyVariables()` to support both `{{key}}` and `{key}` syntax

3. **`android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`** (~15 lines changed)
   - Modify `processAppNotification()` to route to `DeliveryPipeline.deliverAppEvent()`

4. **`android/app/src/main/java/com/example/alertsheets/domain/SourceManager.kt`** (~5 lines added, if method doesn't exist)
   - Add `findSourceByPackageName()` helper

---

## 🎯 EXPECTED OUTCOMES

### Before Fix:
- Sheet rows show: `{package}`, `{title}`, `{bigText}` (literals)
- APP notifications don't work
- SMS works (uses Golden Path)

### After Fix:
- Sheet rows show: `us.bnn.newsapp`, `New Media`, actual content
- APP notifications work (use Golden Path)
- SMS still works (no regression)
- Both APP and SMS use same delivery pipeline
- Multi-endpoint fanout works for both
- Logs show variable resolution for debugging

---

## 📋 IMPLEMENTATION CHECKLIST

- [x] Root cause identified with file + line references
- [ ] `DeliveryPipeline.deliverAppEvent()` method added
- [ ] `TemplateEngine` supports both `{{key}}` and `{key}` syntax
- [ ] `DataPipeline.processAppNotification()` routes to Golden Path
- [ ] `SourceManager.findSourceByPackageName()` helper added (if needed)
- [ ] Enhanced observability logging added
- [ ] Build verification (no compile errors)
- [ ] Manual test: BNN notification → correct substitution
- [ ] Manual test: SMS notification → no regression
- [ ] Manual test: Multi-endpoint fanout works
- [ ] Manual test: Both placeholder syntaxes work
- [ ] Sheet verification: Real values (no placeholders)

---

**Status:** Ready for implementation

