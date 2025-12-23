# Email Implementation Status
**Generated:** December 23, 2025, 3:35 PM  
**Evidence:** PowerShell Select-String + Manifest inspection

---

## ❌ **IS EMAIL IMPLEMENTED?**

### Answer: NO

**Proof:**
- ✅ Manifest search: 0 matches for email/gmail services/receivers
- ✅ Source code: 3 matches (all icon references only)
- ✅ No `SourceType.EMAIL` in enum
- ✅ No email parser, no email processing method

---

## 📁 **EXACT FILES WHERE EMAIL IS REFERENCED**

### Reference 1: Icon Picker List

**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`

**Line:** 76

```kotlin
private val icons = listOf(
    "fire" to R.drawable.ic_fire,
    "sms" to R.drawable.ic_sms,
    "email" to R.drawable.ic_email,  // ⚠️ UI STUB ONLY
    "notification" to R.drawable.ic_notification,
    // ...
)
```

**Purpose:** Allows users to select "email" icon for visual purposes when creating a source card.

**Problem:** User can select icon, but cannot create EMAIL source type.

---

### Reference 2: Source Model Comment

**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt`

**Line:** 23

```kotlin
data class Source(
    // ...
    val iconName: String = "notification",    // Icon for card (fire, sms, email, etc)
    // ...
)
```

**Purpose:** Documentation comment listing example icon names.

**Impact:** No functional impact (comment only).

---

### Reference 3: Icon Mapper

**File:** `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`

**Line:** 136

```kotlin
private fun getIconResource(iconName: String): Int {
    return when (iconName) {
        "fire" -> R.drawable.ic_fire
        "sms" -> R.drawable.ic_sms
        "email" -> R.drawable.ic_email  // ⚠️ Maps icon string to drawable
        "notification" -> R.drawable.ic_notification
        // ...
        else -> R.drawable.ic_notification
    }
}
```

**Purpose:** Maps icon name string to drawable resource for rendering source cards.

**Impact:** If a source has `iconName = "email"`, the email icon is displayed (decorative only).

---

## 📊 **SUMMARY TABLE**

| File | Line | Type | Functional? |
|------|------|------|-------------|
| `LabActivity.kt` | 76 | Icon picker entry | ❌ UI only |
| `Source.kt` | 23 | Comment | ❌ Documentation |
| `MainActivity.kt` | 136 | Icon mapper | ❌ Display only |

**Total Lines of Processing Code:** 0  
**Total Lines of UI Code:** 3

---

## 🛠️ **WHAT WOULD BE REQUIRED TO IMPLEMENT**

### Implementation Option: Gmail-Notification-Based Email Source

**Concept:** Treat Gmail app notifications as EMAIL source type (similar to how SMS messages are captured).

**Android Limitation:** Email content is NOT accessible like SMS. Gmail API requires OAuth + cloud server. However, Gmail *notifications* ARE accessible via `NotificationListenerService`.

---

### Required Changes

#### 1. Add EMAIL to SourceType Enum

**File:** `android/app/src/main/java/com/example/alertsheets/domain/models/Source.kt`

**Current (Lines 136-143):**
```kotlin
enum class SourceType {
    APP,  // Notification from installed app
    SMS   // SMS message from phone number
}
```

**Proposed:**
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

**Lines:** ~30

```kotlin
package com.example.alertsheets.domain.parsers

import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.RawNotification

class EmailParser : Parser {
    override fun parse(raw: RawNotification): ParsedData? {
        // Gmail notification structure:
        // title: Sender name/email
        // text: Subject line
        // bigText: Email snippet (first ~50 chars)
        
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

**Current:**
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

**Proposed:**
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

#### 4. Add Email Source Lookup

**File:** `android/app/src/main/java/com/example/alertsheets/domain/SourceManager.kt`

**Add method after `findSourceForSms()` (~line 65):**

```kotlin
/**
 * Find enabled EMAIL source for Gmail notifications
 */
fun findSourceForEmail(packageName: String): Source? {
    // Only process if it's Gmail
    if (packageName != "com.google.android.gm") return null
    
    // Find EMAIL source
    return repository.getAll()
        .firstOrNull { it.type == SourceType.EMAIL && it.enabled }
}
```

---

#### 5. Route Gmail Notifications to Email Source

**File:** `android/app/src/main/java/com/example/alertsheets/domain/DataPipeline.kt`

**Current (Lines 176-194):**
```kotlin
fun processAppNotification(packageName: String, raw: RawNotification) {
    val source = sourceManager.findSourceForNotification(packageName)
    
    if (source != null) {
        logger.log("📱 App: ${source.name}")
        process(source, raw)
    } else {
        // Log as IGNORED
    }
}
```

**Proposed:**
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
    
    if (source != null) {
        logger.log("📱 App: ${source.name}")
        process(source, raw)
    } else {
        // Log as IGNORED
    }
}
```

---

#### 6. Add Email Configuration UI

**File:** `android/app/src/main/java/com/example/alertsheets/LabActivity.kt`

**Add after SMS config (~line 318):**

```kotlin
SourceType.EMAIL -> {
    // Simple config: Use Gmail package name
    selectedPackageOrNumber = "com.google.android.gm"
    txtSourceDetails.text = "Gmail Email Notifications"
    txtSourceDetails.visibility = View.VISIBLE
}
```

---

#### 7. Add Email Template

**File:** `android/app/src/main/java/com/example/alertsheets/data/repositories/TemplateRepository.kt`

**Add method:**

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
```

**Update `getDefaultJsonForNewSource()` (~line 175):**

```kotlin
fun getDefaultJsonForNewSource(sourceType: SourceType): String {
    return when (sourceType) {
        SourceType.APP -> getFallbackAppTemplate()
        SourceType.SMS -> getFallbackSmsTemplate()
        SourceType.EMAIL -> getFallbackEmailTemplate()  // ✅ NEW
    }
}
```

---

## 📊 **IMPLEMENTATION SUMMARY**

| Component | Action | Lines Changed | New Files |
|-----------|--------|---------------|-----------|
| **SourceType enum** | Add EMAIL | +1 | 0 |
| **EmailParser** | Create | ~30 | 1 |
| **ParserRegistry** | Register email parser | +1 | 0 |
| **SourceManager** | Add findSourceForEmail() | ~10 | 0 |
| **DataPipeline** | Route Gmail notifications | ~8 | 0 |
| **LabActivity** | Add EMAIL config UI | ~5 | 0 |
| **TemplateRepository** | Add email template | ~12 | 0 |
| **TOTAL** | - | ~67 lines | 1 file |

**Estimated Time:** 2-3 hours  
**Testing Time:** 1 hour

---

## ⚠️ **LIMITATIONS**

| Aspect | Reality |
|--------|---------|
| **Email Content** | ❌ Snippet only (~20-50 chars), not full body |
| **Attachments** | ❌ Not accessible |
| **CC/BCC** | ❌ Not accessible |
| **HTML Formatting** | ❌ Plain text only |
| **Reliability** | ⚠️ Depends on Gmail notification settings |
| **Other Providers** | ⚠️ Outlook/Yahoo would need separate sources |
| **Historical Emails** | ❌ Only NEW emails that trigger notifications |
| **Threading** | ❌ No conversation context |

---

## 🎯 **RECOMMENDATION**

### Option A: Implement Gmail-Notification Email Source

**Pros:**
- ✅ Provides *some* email functionality (better than nothing)
- ✅ Uses existing infrastructure (`NotificationListenerService`)
- ✅ No new permissions required
- ✅ Can expand to Outlook/Yahoo later

**Cons:**
- ⚠️ Limited data (snippet only, not full email)
- ⚠️ Users may expect full email body
- ⚠️ Gmail notification format could change

**Use Case:** Alert-style emails where subject + sender is sufficient (e.g., "New order from John Doe").

---

### Option B: Remove Email Icon from UI

**Pros:**
- ✅ Honest UX (no false promises)
- ✅ No user confusion
- ✅ 15 minutes to implement

**Cons:**
- ❌ Removes future expansion option
- ❌ If users need email, they have to request it

**Action:**
- Remove `"email" to R.drawable.ic_email` from `LabActivity.kt:76`
- Remove `"email" -> R.drawable.ic_email` from `MainActivity.kt:136`

---

### Final Recommendation: **Implement Option A**

**Rationale:**
1. Users benefit from *some* email capture (subject/sender/snippet)
2. CRM use case: Email alerts are often actionable from subject alone
3. Infrastructure already exists (NotificationListener)
4. Can document limitations clearly in UI ("Email alerts from Gmail notifications")
5. Future-proof for Outlook/Yahoo expansion

**Set Expectations:** Add label in Lab: "Email source captures Gmail notification data (sender, subject, snippet). Full email bodies not available on Android."

---

**END OF EMAIL_STATUS.md**

