# 🔒 MILESTONE 1: CONFIGURATION HARDENING COMPLETE

**Date:** 2025-12-23  
**Task:** Eliminate hardcoded config + Gate test harness to debug builds only

---

## ✅ **COMPLETED CHANGES**

### **1. Dynamic Endpoint Resolution**

**File:** `android/app/src/main/java/com/example/alertsheets/data/IngestQueue.kt`

**Before (❌ HARDCODED):**
```kotlin
private const val INGEST_ENDPOINT = "https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest"
```

**After (✅ DYNAMIC):**
```kotlin
fun getIngestEndpoint(context: Context): String {
    val projectId = FirebaseApp.getInstance().options.projectId
        ?: throw IllegalStateException("Firebase project ID not found in google-services.json")
    
    val region = context.getString(R.string.firebase_functions_region)
    
    return "https://$region-$projectId.cloudfunctions.net/ingest"
}
```

**Benefits:**
- ✅ No manual edits required for different Firebase projects
- ✅ Works across debug/release/staging builds automatically
- ✅ Resolves from `google-services.json` at runtime
- ✅ Region configurable via `strings.xml` resource

---

### **2. Firebase Region Configuration**

**File:** `android/app/src/main/res/values/strings.xml`

```xml
<string name="firebase_functions_region">us-central1</string>
```

**Usage:**
- Change region for different environments (e.g., `europe-west1`, `asia-east1`)
- No code changes required - modify resource file only

---

### **3. Debug-Only Test Harness (Source Set Isolation)**

#### **3.1 Moved to Debug Source Set**

**Files Moved:**
- `android/app/src/debug/java/com/example/alertsheets/ui/IngestTestActivity.kt`
- `android/app/src/debug/res/layout/activity_ingest_test.xml`
- `android/app/src/debug/AndroidManifest.xml` (created)

**Result:**
- ✅ **IngestTestActivity does NOT exist in release builds** (class not compiled)
- ✅ Layout resources not included in release APK
- ✅ No ProGuard workarounds needed - source set handles exclusion

---

#### **3.2 Debug-Only Manifest Registration**

**File:** `android/app/src/debug/AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name=".ui.IngestTestActivity"
            android:label="@string/activity_ingest_test"
            android:exported="false"
            android:theme="@style/Theme.AlertsToSheets">
            <!-- DEBUG ONLY: NOT exported, NOT launcher -->
        </activity>
    </application>
</manifest>
```

**Security:**
- ✅ `exported="false"` - Cannot be invoked externally
- ✅ No intent filters - Not visible in app drawer
- ✅ Only accessible via explicit `Intent` from `MainActivity`

---

#### **3.3 MainActivity Gating (BuildConfig.DEBUG)**

**File:** `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`

```kotlin
// DEBUG ONLY: Ingest Test Harness card
if (BuildConfig.DEBUG) {
    val testCard = findViewById<FrameLayout>(R.id.card_test_harness)
    testCard?.visibility = View.VISIBLE
    testCard?.setOnClickListener {
        try {
            // Reflection to avoid compile-time dependency in release builds
            val activityClass = Class.forName("com.example.alertsheets.ui.IngestTestActivity")
            val intent = Intent(this, activityClass)
            startActivity(intent)
        } catch (e: ClassNotFoundException) {
            Toast.makeText(this, "Test harness not available", Toast.LENGTH_SHORT).show()
        }
    }
}
```

**How It Works:**
1. **Debug Build:** `BuildConfig.DEBUG == true` → Test card visible → Reflection finds class → Activity launches
2. **Release Build:** `BuildConfig.DEBUG == false` → Card hidden → Class doesn't exist → No crash, no exposure

---

## 🛡️ **SECURITY VERIFICATION**

### **Test 1: Release Build Cannot Access Harness**

```bash
# Build release APK
cd android && ./gradlew assembleRelease

# Verify IngestTestActivity is NOT in APK
unzip -l app/build/outputs/apk/release/app-release.apk | grep IngestTestActivity
# Expected: NO MATCHES
```

### **Test 2: Debug Build Shows Harness**

```bash
# Build debug APK
cd android && ./gradlew assembleDebug

# Verify IngestTestActivity IS in APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep IngestTestActivity
# Expected: IngestTestActivity.class found
```

### **Test 3: Runtime Gating**

```kotlin
// In release build, this code path is never executed:
if (BuildConfig.DEBUG) { // ← FALSE in release
    // This entire block is optimized out by R8/ProGuard
}
```

---

## 📊 **CONFIGURATION AUDIT**

| Component | Before | After | Status |
|-----------|--------|-------|--------|
| **Ingest Endpoint** | Hardcoded URL | Dynamic from `FirebaseApp` | ✅ Fixed |
| **Project ID** | Hardcoded string | From `google-services.json` | ✅ Fixed |
| **Region** | Hardcoded | `strings.xml` resource | ✅ Fixed |
| **Test Harness** | Main source set | Debug source set only | ✅ Secured |
| **Test Activity** | Always in APK | Debug APK only | ✅ Secured |
| **MainActivity Access** | Unconditional | `BuildConfig.DEBUG` gated | ✅ Secured |

---

## 🎯 **PRODUCTION READINESS**

### **✅ Safe for Release:**
- No hardcoded Firebase project IDs
- No hardcoded Cloud Function URLs
- No test/debug code in production builds
- No reflection or ClassNotFoundException crashes in release

### **✅ Easy Environment Switching:**
```bash
# Dev environment (dev Firebase project)
cp google-services-dev.json app/google-services.json

# Staging environment (staging Firebase project)
cp google-services-staging.json app/google-services.json

# Production environment (prod Firebase project)
cp google-services-prod.json app/google-services.json
```

**No code changes required** - endpoint resolves automatically!

---

## 🧪 **TESTING CHECKLIST**

- [x] IngestQueue resolves endpoint from FirebaseApp
- [x] IngestTestActivity moved to debug source set
- [x] Debug manifest registers activity
- [x] MainActivity gates access with BuildConfig.DEBUG
- [x] Reflection prevents ClassNotFoundException
- [ ] **USER VERIFICATION:** Build release APK and confirm test card is hidden
- [ ] **USER VERIFICATION:** Build debug APK and confirm test card launches harness

---

## 📝 **DEVELOPER NOTES**

### **Adding New Firebase Projects:**

1. Place `google-services.json` in `android/app/`
2. No code changes needed
3. Endpoint auto-resolves to new project

### **Changing Cloud Function Region:**

Edit `android/app/src/main/res/values/strings.xml`:
```xml
<string name="firebase_functions_region">europe-west1</string>
```

### **Adding More Debug-Only Activities:**

1. Create in `android/app/src/debug/java/...`
2. Register in `android/app/src/debug/AndroidManifest.xml`
3. Gate access in `MainActivity` with `BuildConfig.DEBUG`

---

**🔒 Configuration is now environment-agnostic and production-safe!**

