# Email Flow Status - Implementation Options
**Generated:** December 23, 2025, 2:40 PM  
**Purpose:** Document email capture status and provide real implementation paths

---

## 🚨 **CURRENT STATUS: NOT IMPLEMENTED**

### Evidence

**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt`

**Lines:** 136-143

```kotlin
enum class SourceType {
    APP,  // Notification from installed app
    SMS   // SMS message from phone number
    // ❌ NO EMAIL
}
```

**Result:** Email is NOT a valid source type in the data model.

---

### UI Stub Evidence

**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`

**Line:** 76

```kotlin
private val icons = listOf(
    "fire" to R.drawable.ic_fire,
    "sms" to R.drawable.ic_sms,
    "email" to R.drawable.ic_email,  // ⚠️ Decorative only
    // ...
)
```

**Impact:** Users can select email icon for visual purposes, but cannot create EMAIL source type.

---

### Missing Components

| Component | Expected Location | Status |
|-----------|-------------------|--------|
| **SourceType.EMAIL** | `domain/models/Source.kt` enum | ❌ NOT DEFINED |
| **Email Config UI** | `LabActivity` | ❌ NO FLOW |
| **EmailReceiver** | `services/EmailReceiver.kt` | ❌ DOES NOT EXIST |
| **GmailListener** | `services/` | ❌ DOES NOT EXIST |
| **EmailParser** | `domain/parsers/EmailParser.kt` | ❌ DOES NOT EXIST |
| **processEmail()** | `domain/DataPipeline.kt` | ❌ NO METHOD |
| **findSourceForEmail()** | `domain/SourceManager.kt` | ❌ NO METHOD |

---

## 📧 **ANDROID EMAIL CAPTURE REALITY**

### ⚠️ **Critical Limitation**

**Android does NOT provide system-level email interception like SMS or notifications.**

### Available Options

| Method | Feasibility | Pros | Cons |
|--------|-------------|------|------|
| **Gmail API** | ❌ NOT LOCAL | Full email access | Requires OAuth, cloud server, not real-time |
| **Gmail Notification** | ✅ VIABLE | Works with existing NotificationListener | Only gets notification data (subject/sender), not full email body |
| **IMAP/POP3** | ⚠️ COMPLEX | Direct mailbox access | Requires user credentials, polling, battery drain |
| **Accessibility Service** | 🚫 FORBIDDEN | Screen scraping | Violates Play Store policy |

---

## ✅ **OPTION 1: GMAIL NOTIFICATION PARSING (RECOMMENDED)**

### Implementation Strategy

**Treat Gmail app notifications as EMAIL source type**

Gmail posts notifications when emails arrive. We can:
1. Detect Gmail package name (`com.google.android.gm`)
2. Parse notification extras for sender, subject, snippet
3. Create `SourceType.EMAIL` and `EmailParser`
4. Use existing `AlertsNotificationListener` infrastructure

---

### Files to Create/Modify

#### 1. Add EMAIL to SourceType Enum

**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt`

**Modification:** Lines 136-145

```kotlin
enum class SourceType {
    APP,   // Notification from installed app
    SMS,   // SMS message from phone number
    EMAIL  // Email from Gmail notifications
}
```

---

#### 2. Create EmailParser

**File:** `android/app/src/main/java/com/example/alertsheets/domain/parsers/EmailParser.kt` (NEW)

```kotlin
package com.example.alertsheets.domain.parsers

import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.RawNotification

class EmailParser : Parser {
    override fun parse(raw: RawNotification): ParsedData? {
        // Gmail notification structure:
        // title: Sender name/email
        // text: Subject line
        // bigText: Email snippet (first few lines)
        
        return ParsedData(
            variables = mapOf(
                "sender" to (raw.title.ifEmpty { "Unknown Sender" }),
                "subject" to (raw.text.ifEmpty { "(No Subject)" }),
                "snippet" to (raw.bigText ?: raw.text),
                "receivedAt" to System.currentTimeMillis().toString()
            )
        )
    }
}
```

---

#### 3. Register EmailParser

**File:** `android/app/src/main/java/com/example/alertsheets/domain/parsers/ParserRegistry.kt`

**Modification:** Add to parsers map

```kotlin
object ParserRegistry {
    private val parsers = mapOf(
        "bnn" to BnnParser(),
        "generic" to GenericAppParser(),
        "sms" to SmsParser(),
        "email" to EmailParser()  // ✅ NEW
    )
    // ...
}
```

---

#### 4. Add Email Configuration UI

**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`

**Modification:** Lines ~315-320 (after SMS config)

```kotlin
SourceType.EMAIL -> {
    // Simple config: Just use Gmail package name
    selectedPackageOrNumber = "com.google.android.gm"
    txtSourceDetails.text = "Gmail Email Notifications"
}
```

---

#### 5. Add Email Source Matching

**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt`

**Modification:** Add method after `matchesSender()`

```kotlin
fun matchesEmail(packageName: String): Boolean {
    // Only for EMAIL type sources
    if (type != SourceType.EMAIL) return false
    
    // Match Gmail app
    return packageName == "com.google.android.gm" && 
           id == "email:gmail"
}
```

---

#### 6. Add Email Source Lookup

**File:** `android/app/src/main/java/com/example/alertsheets/domain/SourceManager.kt`

**Modification:** Add method after `findSourceForSms()`

```kotlin
fun findSourceForEmail(packageName: String): Source? {
    // Only process if it's Gmail
    if (packageName != "com.google.android.gm") return null
    
    // Find EMAIL source
    var source = repository.getAll()
        .firstOrNull { it.type == SourceType.EMAIL && it.enabled }
    
    return source
}
```

---

#### 7. Route Gmail Notifications to Email Source

**File:** `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`

**Modification:** Lines ~178-182 (in `processAppNotification`)

```kotlin
fun processAppNotification(packageName: String, raw: RawNotification) {
    // Check if it's Gmail first
    if (packageName == "com.google.android.gm") {
        val emailSource = sourceManager.findSourceForEmail(packageName)
        if (emailSource != null) {
            logger.log("📧 Email: ${emailSource.name}")
            process(emailSource, raw)
            return
        }
    }
    
    // Original APP source lookup
    val source = sourceManager.findSourceForNotification(packageName)
    // ...
}
```

---

#### 8. Add Email Template in TemplateRepository

**File:** `android/app/src/main/java/com/example/alertsheets/data/repositories/TemplateRepository.kt`

**Modification:** Add to Rock Solid templates

```kotlin
private fun getFallbackEmailTemplate(): String {
    return """
    {
      "type": "email",
      "sender": "{{sender}}",
      "subject": "{{subject}}",
      "snippet": "{{snippet}}",
      "receivedAt": "{{receivedAt}}"
    }
    """.trimIndent()
}

// In getDefaultJsonForNewSource():
fun getDefaultJsonForNewSource(sourceType: SourceType): String {
    return when (sourceType) {
        SourceType.APP -> getFallbackAppTemplate()
        SourceType.SMS -> getFallbackSmsTemplate()
        SourceType.EMAIL -> getFallbackEmailTemplate()  // ✅ NEW
    }
}
```

---

### What Users Get

✅ **Notification-level data:**
- Sender name/email
- Subject line
- Email snippet (first few lines)
- Timestamp

❌ **NOT available:**
- Full email body
- Attachments
- CC/BCC
- HTML formatting

---

### Limitations

| Aspect | Reality |
|--------|---------|
| **Email Content** | Snippet only (20-50 chars typically) |
| **Reliability** | Depends on user having Gmail notifications enabled |
| **Other Providers** | Outlook, Yahoo, etc. would need separate sources (same pattern) |
| **Historical Emails** | Only NEW emails that trigger notifications |
| **Threading** | No thread/conversation context |

---

### Testing Steps

1. Create EMAIL source in Lab
2. Configure endpoint (e.g., Sheets URL)
3. Create template with `{{sender}}`, `{{subject}}`, `{{snippet}}`
4. Send test email to Gmail account
5. Verify notification triggers source
6. Check Activity Logs for delivery status

---

## ❌ **OPTION 2: REMOVE EMAIL UI (HONEST APPROACH)**

### Implementation Strategy

**Remove all email references from UI to avoid misleading users.**

---

### Files to Modify

#### 1. Remove Email Icon from Lab

**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`

**Modification:** Line 76

```kotlin
private val icons = listOf(
    "fire" to R.drawable.ic_fire,
    "sms" to R.drawable.ic_sms,
    // ❌ REMOVE: "email" to R.drawable.ic_email,
    "notification" to R.drawable.ic_notification,
    "bell" to R.drawable.ic_bell,
    // ...
)
```

---

#### 2. Remove Email Icon Mapping from MainActivity

**File:** `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`

**Modification:** Lines ~130-145 (icon mapping)

```kotlin
private fun getIconResource(iconName: String): Int {
    return when (iconName) {
        "fire" -> R.drawable.ic_fire
        "sms" -> R.drawable.ic_sms
        // ❌ REMOVE: "email" -> R.drawable.ic_email
        "notification" -> R.drawable.ic_notification
        // ...
        else -> R.drawable.ic_notification
    }
}
```

---

### What Users Get

✅ **Honesty:** No misleading UI elements  
✅ **Clarity:** Only APP and SMS sources shown  
✅ **No Confusion:** No "why can't I create email source?" questions

---

## 📊 **COMPARISON TABLE**

| Aspect | Option 1: Gmail Notification | Option 2: Remove UI |
|--------|------------------------------|---------------------|
| **Implementation Time** | 2-3 hours | 15 minutes |
| **Files Changed** | 6 files | 2 files |
| **New Files** | 1 (EmailParser.kt) | 0 |
| **Data Available** | Sender, subject, snippet | N/A |
| **User Value** | ⚠️ LIMITED (snippet only) | ✅ NO FALSE PROMISE |
| **Future Expandability** | ✅ Can add Outlook, etc. | ❌ Must reimplement |
| **Maintenance** | ⚠️ Gmail notification format changes | ✅ NONE |
| **Risk** | ⚠️ Users expect full email body | ✅ NO RISK |

---

## 🎯 **RECOMMENDATION**

### **Option 1: Gmail Notification Parsing**

**Rationale:**
1. ✅ Provides SOME email functionality (better than nothing)
2. ✅ Uses existing infrastructure (NotificationListener)
3. ✅ Can be expanded to Outlook/Yahoo/etc. later
4. ✅ No new permissions required (already has BIND_NOTIFICATION_LISTENER_SERVICE)
5. ⚠️ Set user expectations: "Email alerts from Gmail notifications" (not full emails)

**Caveat:**  
Document in Lab UI that email sources only capture notification data (sender, subject, snippet), not full email bodies.

---

### If User Wants Full Email Bodies

**Then recommend:**
- ❌ **Not feasible on-device** without user entering IMAP credentials
- ✅ **Cloud-based solution:** Firebase Cloud Function polls Gmail API with OAuth
- ✅ **Zapier/IFTTT:** No-code email → webhook trigger

---

## 🛠️ **IMPLEMENTATION CHECKLIST (OPTION 1)**

- [ ] Add `EMAIL` to `SourceType` enum (Source.kt:138)
- [ ] Create `EmailParser.kt` with sender/subject/snippet parsing
- [ ] Register `EmailParser` in `ParserRegistry` (line ~20)
- [ ] Add email config UI in `LabActivity` (~line 318)
- [ ] Add `matchesEmail()` method to `Source.kt` (~line 105)
- [ ] Add `findSourceForEmail()` to `SourceManager.kt` (~line 65)
- [ ] Route Gmail notifications to email source in `DataPipeline.kt` (~line 178)
- [ ] Add `getFallbackEmailTemplate()` to `TemplateRepository.kt` (~line 90)
- [ ] Update `getDefaultJsonForNewSource()` to include EMAIL case (~line 175)
- [ ] Test: Send email → Gmail notification → Verify capture → Check logs

---

**END OF EMAIL_FLOW_STATUS.md**

