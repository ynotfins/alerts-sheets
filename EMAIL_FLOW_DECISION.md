# AlertsToSheets - Email Flow Decision & Implementation Options
**Generated:** December 23, 2025  
**Issue:** Email icon exists in UI but no capture mechanism  
**Evidence:** Comprehensive codebase audit confirms email is UI stub only  
**Status:** Two clear implementation paths proposed

---

## 🔍 **CURRENT STATE AUDIT**

### Email Functionality: **NOT IMPLEMENTED**

#### Evidence Summary
**Search Pattern:** `email|EMAIL|Email` across entire codebase  
**Result:** **Icon references ONLY** - no capture, no parser, no SourceType

---

### 📌 **What EXISTS (UI Stubs)**

| File | Line | Code | Purpose |
|------|------|------|---------|
| `MainActivity.kt` | 136 | `"email" -> R.drawable.ic_email` | Icon mapping for source cards |
| `LabActivity.kt` | 76 | `"email" to R.drawable.ic_email` | Selectable icon in Lab UI |
| `Source.kt` | 23 | `iconName: String = "notification"` | Generic string field (allows "email") |

**Analysis:** These are **decorative UI elements only**. A user CAN create a source with `iconName="email"`, but it will NOT capture or process email.

---

### ❌ **What DOES NOT EXIST (Critical Components)**

| Component | Status | Evidence |
|-----------|--------|----------|
| **Email Capture Service** | ❌ Not Implemented | No `EmailReceiver` or `EmailListenerService` |
| **Email Parser** | ❌ Not Implemented | `parsers/` has BNN, Generic, SMS - no Email parser |
| **SourceType.EMAIL** | ❌ Not Implemented | Enum only has `APP` and `SMS` (line 60-63 in `Source.kt`) |
| **Email Permissions** | ❌ Not Configured | `AndroidManifest.xml` has no email-related permissions |
| **Email-specific Logic** | ❌ Not Implemented | No email routing in `DataPipeline.kt` |

**Conclusion:** Email is a **misleading UI artifact**. The icon suggests functionality that doesn't exist.

---

## 📋 **ANDROID EMAIL CAPTURE REALITY CHECK**

### Why Email is Different from SMS/Notifications

| Feature | SMS | Notifications | Email |
|---------|-----|---------------|-------|
| **System-level API** | ✅ Yes (`BroadcastReceiver`) | ✅ Yes (`NotificationListenerService`) | ❌ No direct API |
| **Requires Permission** | ✅ Yes (dangerous) | ✅ Yes (special) | ⚠️ Depends on method |
| **Real-time Capture** | ✅ Yes (instant) | ✅ Yes (instant) | ⚠️ Via notifications only |
| **Historical Access** | ✅ Yes (via ContentProvider) | ❌ No | ⚠️ Via Gmail API (OAuth) |
| **Works Offline** | ✅ Yes | ✅ Yes | ⚠️ Gmail notifications work offline |
| **Third-party Apps** | ✅ Yes (all SMS apps) | ✅ Yes (all apps) | ❌ Gmail-specific |

---

### 🚨 **Critical Limitations**

#### **There is NO direct "Email API" on Android like SMS/Notifications.**

**Available Methods:**

1. **Gmail API (OAuth-based)**
   - **Requires:** User OAuth consent, Google account, internet connection
   - **Access:** Historical emails, full content, labels, attachments
   - **Limitation:** NOT real-time (polling required), NOT local (requires server)
   - **Use Case:** Gmail-specific, enterprise email management apps
   - **Feasibility for AlertsToSheets:** ❌ **Overkill** - requires backend server, OAuth flow, periodic polling

2. **NotificationListener Approach (Gmail App Notifications)**
   - **Requires:** `BIND_NOTIFICATION_LISTENER_SERVICE` permission (ALREADY GRANTED)
   - **Access:** Gmail app notification content (sender, subject, preview text)
   - **Limitation:** Only for Gmail notifications that appear, no historical access, no full body
   - **Use Case:** Lightweight email alerting (subject/sender only)
   - **Feasibility for AlertsToSheets:** ✅ **VIABLE** - reuses existing infrastructure

3. **IMAP/POP3 (Third-party Libraries)**
   - **Requires:** User credentials (plaintext or OAuth), internet connection
   - **Access:** Full email content from any provider (Gmail, Outlook, custom servers)
   - **Limitation:** Requires managing credentials, polling, complex parsing
   - **Use Case:** Email client apps
   - **Feasibility for AlertsToSheets:** ❌ **Too complex** - security risks, maintenance overhead

---

## ✅ **RECOMMENDATION: OPTION A (Minimal Real Implementation)**

### **Implement Email as "Gmail Notification Capture"**

**Philosophy:** Email = Gmail app notifications captured via existing `NotificationListenerService`

---

### 🎯 **What This Means**

- **"Email Source"** = Monitor Gmail app (`com.google.android.gm`) notifications
- **Captures:** Sender, subject, preview text (what Gmail shows in notifications)
- **Does NOT capture:** Full email body, attachments, HTML content
- **No new permissions needed** (already has `BIND_NOTIFICATION_LISTENER_SERVICE`)
- **Works offline** (processes notifications as they appear)
- **Real-time** (instant when Gmail notification fires)

---

### 📐 **DESIGN DECISION: NO NEW SOURCETYPE**

**Keep `SourceType.APP` for Gmail sources, differentiate in UI only.**

#### Rationale
1. **Gmail IS an app** - package name `com.google.android.gm`
2. **Capture mechanism is identical** - `NotificationListenerService`
3. **Parsing logic is the same** - extract title/text/bigText from notification extras
4. **Adding `SourceType.EMAIL` adds complexity with ZERO functional benefit**

#### What Changes
- **UI Labels:** Display "Gmail Email" instead of just icon
- **Parser:** Potentially a dedicated `GmailParser` (or reuse `GenericAppParser`)
- **Template Variables:** Email-specific names like `$sender`, `$subject`, `$preview`

#### What Stays The Same
- `source.type` = `SourceType.APP`
- `source.id` = `"com.google.android.gm"`
- Capture via existing `AlertsNotificationListener`
- Routing via existing `DataPipeline.processAppNotification()`

---

### 🛠️ **IMPLEMENTATION STEPS (OPTION A)**

#### **Files to Change: 5 total**

---

#### **1. Add Gmail-Specific Parser (OPTIONAL but recommended)**

**File:** `android/app/src/main/java/com/example/alertsheets/domain/parsers/GmailParser.kt` (NEW)

**Purpose:** Extract email-specific fields from Gmail notification extras

**Code:**
```kotlin
package com.example.alertsheets.domain.parsers

import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.RawNotification

/**
 * Parser for Gmail app notifications.
 * Extracts sender, subject, and preview text.
 */
class GmailParser : Parser {
    override val id = "gmail"
    
    override fun parse(raw: RawNotification): ParsedData? {
        // Gmail notifications typically have:
        // - title = sender name or email
        // - text = subject line
        // - bigText = preview of email body
        
        val sender = raw.title ?: "Unknown Sender"
        val subject = raw.text ?: "No Subject"
        val preview = raw.bigText ?: raw.text ?: ""
        
        return ParsedData(
            title = sender,
            message = subject,
            extra = mapOf(
                "sender" to sender,
                "subject" to subject,
                "preview" to preview,
                "packageName" to raw.packageName
            )
        )
    }
}
```

**Why:** Provides email-specific variable names (`$sender`, `$subject`, `$preview`) for templates.

---

#### **2. Register Gmail Parser**

**File:** `android/app/src/main/java/com/example/alertsheets/domain/ParserRegistry.kt`

**Before:**
```kotlin
object ParserRegistry {
    private val parsers = mapOf(
        "bnn" to BnnParser(),
        "generic" to GenericAppParser(),
        "sms" to SmsParser()
    )
    // ...
}
```

**After:**
```kotlin
object ParserRegistry {
    private val parsers = mapOf(
        "bnn" to BnnParser(),
        "generic" to GenericAppParser(),
        "sms" to SmsParser(),
        "gmail" to GmailParser()  // ✅ NEW
    )
    // ...
}
```

**Lines Changed:** 1 (add one line to map)

---

#### **3. Update Lab Activity UI - Add Gmail Source Option**

**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`

**Find:** Section #2 "Select Source Type" (around line 86-98 in `activity_lab.xml`)

**Current:**
```xml
<RadioButton
    android:id="@+id/radio_app"
    android:text="App"
    android:checked="true" />

<RadioButton
    android:id="@+id/radio_sms"
    android:text="SMS" />
```

**After:**
```xml
<RadioButton
    android:id="@+id/radio_app"
    android:text="App"
    android:checked="true" />

<RadioButton
    android:id="@+id/radio_sms"
    android:text="SMS" />

<RadioButton
    android:id="@+id/radio_gmail"
    android:text="Gmail Email" />  <!-- ✅ NEW -->
```

**Lines Changed:** 4 (add new RadioButton)

---

**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`

**Find:** `radio_source_type.setOnCheckedChangeListener` (around line 250-270)

**Before:**
```kotlin
radioSourceType.setOnCheckedChangeListener { _, checkedId ->
    when (checkedId) {
        R.id.radio_app -> {
            cardMatcher.visibility = View.VISIBLE
            // ...
        }
        R.id.radio_sms -> {
            cardMatcher.visibility = View.VISIBLE
            // ...
        }
    }
}
```

**After:**
```kotlin
radioSourceType.setOnCheckedChangeListener { _, checkedId ->
    when (checkedId) {
        R.id.radio_app -> {
            cardMatcher.visibility = View.VISIBLE
            // ...
        }
        R.id.radio_sms -> {
            cardMatcher.visibility = View.VISIBLE
            // ...
        }
        R.id.radio_gmail -> {  // ✅ NEW
            cardMatcher.visibility = View.VISIBLE
            textMatcherInstructions.text = "Gmail notifications will be captured automatically"
            btnSelectApp.text = "Gmail is already configured (com.google.android.gm)"
            btnSelectApp.isEnabled = false
            // Pre-configure Gmail package name
            selectedPackageName = "com.google.android.gm"
        }
    }
}
```

**Lines Changed:** ~8 (add new case block)

---

**Find:** `btnSaveSource.setOnClickListener` (around line 600-650)

**Before:**
```kotlin
btnSaveSource.setOnClickListener {
    val type = when (radioSourceType.checkedRadioButtonId) {
        R.id.radio_app -> SourceType.APP
        R.id.radio_sms -> SourceType.SMS
        else -> SourceType.APP
    }
    
    val sourceId = when (type) {
        SourceType.APP -> selectedPackageName ?: ""
        SourceType.SMS -> "sms:${selectedSmsNumber ?: ""}"
    }
    // ...
}
```

**After:**
```kotlin
btnSaveSource.setOnClickListener {
    val type = when (radioSourceType.checkedRadioButtonId) {
        R.id.radio_app -> SourceType.APP
        R.id.radio_sms -> SourceType.SMS
        R.id.radio_gmail -> SourceType.APP  // ✅ Gmail uses APP type
        else -> SourceType.APP
    }
    
    val sourceId = when (radioSourceType.checkedRadioButtonId) {
        R.id.radio_app -> selectedPackageName ?: ""
        R.id.radio_sms -> "sms:${selectedSmsNumber ?: ""}"
        R.id.radio_gmail -> "com.google.android.gm"  // ✅ Hardcoded Gmail package
        else -> selectedPackageName ?: ""
    }
    
    val defaultParserId = when (radioSourceType.checkedRadioButtonId) {
        R.id.radio_gmail -> "gmail"  // ✅ Use Gmail parser
        R.id.radio_sms -> "sms"
        else -> "generic"
    }
    // ...
}
```

**Lines Changed:** ~10 (add Gmail-specific cases)

---

#### **4. Update Dashboard Subtitle Logic**

**File:** `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`

**Find:** Subtitle logic (around line 150-155)

**Before:**
```kotlin
val sourceTypeText = when (source.type) {
    SourceType.APP -> "App"
    SourceType.SMS -> "SMS"
}
subtitle.text = "$sourceTypeText • ${source.endpointIds.size} endpoint(s)"
```

**After:**
```kotlin
val sourceTypeText = when {
    source.type == SourceType.SMS -> "SMS"
    source.id == "com.google.android.gm" -> "Gmail"  // ✅ Detect Gmail by package
    else -> "App"
}
subtitle.text = "$sourceTypeText • ${source.endpointIds.size} endpoint(s)"
```

**Lines Changed:** 5 (refactor to when-expression with Gmail detection)

---

#### **5. Add Default Gmail Template (OPTIONAL)**

**File:** `android/app/src/main/java/com/example/alertsheets/data/repositories/TemplateRepository.kt`

**Add a default Gmail template to the built-in templates:**

**Before:**
```kotlin
private val builtInTemplates = listOf(
    Template(
        id = "default",
        name = "Default",
        json = """{"type": "${'$'}type", "message": "${'$'}message"}"""
    ),
    // ... other templates
)
```

**After:**
```kotlin
private val builtInTemplates = listOf(
    Template(
        id = "default",
        name = "Default",
        json = """{"type": "${'$'}type", "message": "${'$'}message"}"""
    ),
    Template(  // ✅ NEW
        id = "gmail",
        name = "Gmail Email",
        json = """{
            "sender": "${'$'}sender",
            "subject": "${'$'}subject",
            "preview": "${'$'}preview",
            "timestamp": "${'$'}timestamp"
        }"""
    ),
    // ... other templates
)
```

**Lines Changed:** 7 (add new template object)

---

### 📊 **SUMMARY: OPTION A**

| Aspect | Details |
|--------|---------|
| **Files Changed** | 5 total (4 Kotlin, 1 XML) |
| **New Files** | 1 (`GmailParser.kt`) |
| **Lines Changed** | ~35 total |
| **New Permissions** | 0 (reuses existing `BIND_NOTIFICATION_LISTENER_SERVICE`) |
| **New Dependencies** | 0 |
| **Testing Required** | Create Gmail source in Lab → Send test email → Verify capture |
| **Limitations** | Only Gmail notifications (sender/subject/preview), no full body/attachments |
| **User Experience** | "Gmail Email" option appears in Lab alongside App/SMS |
| **Backwards Compatible** | ✅ Yes (existing sources unaffected) |

---

### ⚠️ **OPTION A LIMITATIONS**

1. **Gmail-Only:** Only works for Gmail app notifications, not other email apps
2. **Notification-Dependent:** Only captures if Gmail shows a notification
3. **Limited Content:** Subject + preview only, no full body or attachments
4. **User Configuration Required:** Gmail must have notifications enabled
5. **No Historical Access:** Can't read past emails, only new incoming notifications

---

### ✅ **OPTION A BENEFITS**

1. **Zero New Permissions:** Reuses existing notification listener
2. **Minimal Code:** ~35 lines across 5 files
3. **Real-Time:** Instant capture when Gmail notification fires
4. **Offline:** Works without internet after initial Gmail notification
5. **Familiar UX:** Same Lab workflow as App/SMS sources
6. **Independently Configurable:** Own source card, own endpoints, own template
7. **No Backend Required:** Pure client-side, no server/OAuth/polling

---

## 🚫 **OPTION B: REMOVE MISLEADING UI**

### **Clean Up Email References**

**Philosophy:** If not implemented, don't mislead users with UI that suggests it is.

---

### 🛠️ **IMPLEMENTATION STEPS (OPTION B)**

#### **Files to Change: 2 total**

---

#### **1. Remove Email Icon from Lab Selection**

**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`

**Find:** Icon list (around line 73-81)

**Before:**
```kotlin
private val icons = listOf(
    "fire" to R.drawable.ic_fire,
    "sms" to R.drawable.ic_sms,
    "email" to R.drawable.ic_email,  // ❌ REMOVE THIS
    "notification" to R.drawable.ic_notification,
    // ...
)
```

**After:**
```kotlin
private val icons = listOf(
    "fire" to R.drawable.ic_fire,
    "sms" to R.drawable.ic_sms,
    // email icon removed - not implemented
    "notification" to R.drawable.ic_notification,
    // ...
)
```

**Lines Changed:** 1 (remove one line)

---

#### **2. Remove Email Icon Mapping from Dashboard**

**File:** `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`

**Find:** Icon mapping (around line 131-142)

**Before:**
```kotlin
val iconRes = when (source.iconName) {
    "fire" -> R.drawable.ic_fire
    "sms" -> R.drawable.ic_sms
    "email" -> R.drawable.ic_email  // ❌ REMOVE THIS
    "location" -> R.drawable.ic_location
    // ...
    else -> R.drawable.ic_notification
}
```

**After:**
```kotlin
val iconRes = when (source.iconName) {
    "fire" -> R.drawable.ic_fire
    "sms" -> R.drawable.ic_sms
    // email removed - not implemented, will fall through to default
    "location" -> R.drawable.ic_location
    // ...
    else -> R.drawable.ic_notification
}
```

**Lines Changed:** 1 (remove one line)

---

### 📊 **SUMMARY: OPTION B**

| Aspect | Details |
|--------|---------|
| **Files Changed** | 2 (both Kotlin) |
| **Lines Changed** | 2 (remove 2 lines) |
| **Impact** | Email icon no longer selectable in Lab, legacy sources with email icon show default icon |
| **Benefit** | No misleading UI suggesting email support |
| **Limitation** | Email remains unimplemented |
| **Future-Proof** | Can re-add icon when email is implemented |

---

## 🎯 **RECOMMENDATION SUMMARY**

### **Choose OPTION A** if:
- ✅ You want Gmail email alerting (sender/subject)
- ✅ You're okay with notification-based capture (not full email body)
- ✅ You want feature parity with SMS/Notifications
- ✅ You can test with Gmail app on device

### **Choose OPTION B** if:
- ✅ You want to focus on existing features (Notifications + SMS)
- ✅ You don't need email support immediately
- ✅ You want to avoid misleading users
- ✅ You prefer minimal changes

---

## 📝 **SIDE-BY-SIDE COMPARISON**

| Feature | Option A (Implement) | Option B (Remove UI) |
|---------|---------------------|----------------------|
| **Gmail Email Support** | ✅ Yes (notifications) | ❌ No |
| **Files Changed** | 5 (4 Kotlin, 1 XML) | 2 (both Kotlin) |
| **Lines Changed** | ~35 | 2 |
| **New Permissions** | 0 | 0 |
| **New Dependencies** | 0 | 0 |
| **Testing Required** | Medium (Gmail setup) | Minimal (UI check) |
| **User Benefit** | Email alerting | Honest UI |
| **Complexity** | Low | Minimal |
| **Maintenance** | Low | None |
| **Risk** | Low | None |

---

## 🚨 **CRITICAL CLARIFICATIONS**

### **What Option A Email Support DOES:**
- ✅ Captures Gmail app notifications in real-time
- ✅ Extracts sender, subject, and preview text
- ✅ Applies templates to create custom JSON payloads
- ✅ Delivers to configured endpoints (fan-out supported)
- ✅ Logs success/failure like notifications/SMS

### **What Option A Email Support DOES NOT:**
- ❌ Read full email body content
- ❌ Access email attachments
- ❌ Support non-Gmail email apps (Outlook, Yahoo, etc.)
- ❌ Provide historical email access
- ❌ Work if Gmail notifications are disabled by user
- ❌ Replace full email client functionality

---

## 🔐 **PERMISSIONS ANALYSIS**

### **Option A Permissions**

| Permission | Status | Reason |
|------------|--------|--------|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | ✅ Already Granted | Required for existing notification capture |
| Gmail-specific permissions | ❌ Not Required | We capture notifications, not emails directly |
| Internet | ✅ Already Granted | For endpoint delivery |

**Verdict:** ✅ **No new permissions needed**

---

### **Option B Permissions**

**Verdict:** ✅ **No changes** (no permissions involved)

---

## 🧪 **TESTING PLAN**

### **Option A Testing**

#### **Test Case 1: Gmail Source Creation**
1. Open Lab
2. Select "Gmail Email" source type
3. Configure name, endpoints, template
4. Save source

**Expected:** Source card appears on dashboard with Gmail icon/label

#### **Test Case 2: Gmail Notification Capture**
1. Send test email to Gmail account on device
2. Wait for Gmail notification to appear
3. Check Activity Logs

**Expected:** Log entry shows sender/subject, status=SENT

#### **Test Case 3: Endpoint Delivery**
1. Configure source with test endpoint (RequestBin or similar)
2. Send test email
3. Check endpoint received JSON

**Expected:** JSON contains `sender`, `subject`, `preview` fields

#### **Test Case 4: Template Variables**
1. Create custom template: `{"from": "$sender", "re": "$subject"}`
2. Send test email
3. Check delivered JSON

**Expected:** Variables correctly replaced

---

### **Option B Testing**

#### **Test Case 1: Email Icon Removed from Lab**
1. Open Lab
2. Scroll through icon selection

**Expected:** Email icon not present

#### **Test Case 2: Legacy Sources with Email Icon**
1. If any source has `iconName="email"`, view on dashboard

**Expected:** Falls back to default notification icon

---

## 📂 **FILE REFERENCE**

### **Files Analyzed**
```
✅ android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt
✅ android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt
✅ android/app/src/main/java/com/example/alertsheets/LabActivity.kt
✅ android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt
✅ android/app/src/main/java/com/example/alertsheets/domain/parsers/*.kt
✅ android/app/src/main/AndroidManifest.xml
✅ WORKFLOW_MAP.md (lines 212-239)
```

---

## 🎯 **FINAL RECOMMENDATION**

### **OPTION A (Minimal Real Implementation)**

**Why:** 
1. Delivers immediate value (Gmail alerting)
2. Low effort (~35 lines)
3. Zero new permissions
4. Consistent with existing App/SMS architecture
5. Users expect email support when they see the icon

**Risk:** Low - limited to notification-based capture, clearly documented limitations

**Next Steps:**
1. Implement GmailParser.kt
2. Update ParserRegistry
3. Add Gmail option to Lab UI
4. Update dashboard subtitle logic
5. Test with live Gmail account
6. Document limitations in UI (tooltip or help text)

---

## 📋 **IMPLEMENTATION CHECKLIST**

### **Option A (If Selected)**
- [ ] Create `GmailParser.kt` with sender/subject/preview extraction
- [ ] Register parser in `ParserRegistry.kt`
- [ ] Add Gmail RadioButton to `activity_lab.xml`
- [ ] Add Gmail case to Lab's `setOnCheckedChangeListener`
- [ ] Add Gmail case to `btnSaveSource` logic
- [ ] Update dashboard subtitle logic to detect Gmail
- [ ] Add default Gmail template (optional)
- [ ] Test on device with Gmail notifications
- [ ] Document limitations in user-facing help/docs

### **Option B (If Selected)**
- [ ] Remove email icon from `LabActivity.kt` icons list
- [ ] Remove email icon case from `MainActivity.kt` icon mapping
- [ ] Test Lab icon picker (email not present)
- [ ] Test legacy sources with email icon (fallback works)

---

**Decision Document Complete**  
**Status:** Awaiting user selection (Option A or Option B)  
**Estimated Implementation Time:**
- **Option A:** 1-2 hours (including testing)
- **Option B:** 5 minutes

---

**END OF EMAIL_FLOW_DECISION.md**

