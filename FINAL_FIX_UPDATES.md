# 🎯 Fix Update Row Matching + Add Test Update Button

**Priority:** P0 - CRITICAL  
**Goal:** Updates must append to same row, not create duplicates

---

## 🔧 **Two Changes Needed**

### **Change 1: Make Apps Script Handle # Prefix Gracefully**

**Problem:** Android might send `#1844731` or `1844731`, but sheet always stores `#1844731`.  
**Solution:** Strip # before comparing, then always store with #.

**File:** `scripts/Code.gs`

**Current Code (Line 20):**
```javascript
const incidentId = data.incidentId ? data.incidentId.toString().trim() : "";
```

**Change to:**
```javascript
// Normalize ID: strip # for comparison, but preserve original format in sheet
const rawId = data.incidentId ? data.incidentId.toString().trim() : "";
const incidentId = rawId.startsWith("#") ? rawId.substring(1) : rawId; // Strip # for search
const displayId = rawId.startsWith("#") ? rawId : "#" + rawId; // Ensure # for display
```

**Then update the search logic (Line 56):**
```javascript
// Current:
if (idValues[i][0].toString().trim() === incidentId) {

// Change to:
const sheetId = idValues[i][0].toString().trim();
const normalizedSheetId = sheetId.startsWith("#") ? sheetId.substring(1) : sheetId;
if (normalizedSheetId === incidentId) {
```

**And when writing to sheet (Line 143):**
```javascript
// Current:
incidentId,

// Change to:
displayId, // Always write with # prefix
```

**Result:** Apps Script will match IDs whether Android sends with or without #, but always store with #.

---

### **Change 2: Add Test Update Button**

We need two test buttons:
1. **"Test New Incident"** - Generates new ID, saves to prefs
2. **"Test Update"** - Reuses last ID from prefs

---

## 📝 **Android Changes (3 files)**

### **File 1: PrefsManager.kt**

Add methods to save/retrieve last test ID:

```kotlin
object PrefsManager {
    // ... existing code ...
    
    private const val KEY_LAST_TEST_ID = "lastTestIncidentId"
    
    fun saveLastTestId(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_TEST_ID, id).apply()
    }
    
    fun getLastTestId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_TEST_ID, "") ?: ""
    }
}
```

---

### **File 2: AppConfigActivity.kt**

Modify `performTest()` to support update mode:

**Current signature (Line 350):**
```kotlin
private fun performTest(silent: Boolean = false) {
```

**Change to:**
```kotlin
private fun performTest(isUpdate: Boolean = false, silent: Boolean = false) {
```

**Then modify the test payload logic (Lines 354-396):**

```kotlin
val json = if (isApp) {
    // Determine incident ID based on mode
    val incidentId: String
    val statusPrefix: String
    val testType: String
    
    if (isUpdate) {
        // REUSE last test ID for update
        val lastId = PrefsManager.getLastTestId(this)
        if (lastId.isEmpty()) {
            runOnUiThread {
                Toast.makeText(this, "No previous test! Send NEW incident first.", Toast.LENGTH_LONG).show()
            }
            return
        }
        incidentId = lastId
        statusPrefix = "U/D"
        testType = "Update"
        android.util.Log.i("TEST", "=== TEST UPDATE ===")
        android.util.Log.i("TEST", "Reusing ID: $incidentId")
    } else {
        // GENERATE new unique ID
        val uniqueId = "1${System.currentTimeMillis().toString().takeLast(6)}"
        incidentId = "#$uniqueId"
        statusPrefix = "N/D"
        testType = "New Incident"
        
        // Save for next update test
        PrefsManager.saveLastTestId(this, incidentId)
        
        android.util.Log.i("TEST", "=== TEST NEW INCIDENT ===")
        android.util.Log.i("TEST", "Generated ID: $incidentId")
        android.util.Log.i("TEST", "Saved to prefs for update test")
    }
    
    // Construct realistic BNN message
    val mockBnn = "$statusPrefix NJ | Test County | Test City | 888 Test Ave | TEST-TYPE | Testing $testType - Columns should fill. | <C> BNN | E-1/L-1 | nj312/njn751/nyl785/pa547 | $incidentId"
    
    android.util.Log.i("TEST", "Mock BNN: $mockBnn")
    
    // Parse through real parser
    val parsed = Parser.parse(mockBnn)
    
    if (parsed != null) {
        val timestamped = parsed.copy(
            timestamp = TemplateEngine.getTimestamp(),
            status = testType
        )
        
        val jsonPayload = com.google.gson.Gson().toJson(timestamped)
        android.util.Log.i("TEST", "Parsed successfully")
        android.util.Log.i("TEST", "Incident ID in JSON: ${timestamped.incidentId}")
        android.util.Log.i("TEST", "Status: ${timestamped.status}")
        
        jsonPayload
    } else {
        android.util.Log.e("TEST", "PARSE FAILED!")
        runOnUiThread {
            Toast.makeText(this, "Parse failed! Check logs.", Toast.LENGTH_LONG).show()
        }
        return
    }
} else {
    // SMS mode (unchanged)
    val uniqueId = "SMS-${System.currentTimeMillis().toString().takeLast(4)}"
    TemplateEngine.applyGeneric(template, "sms", "TEST-SENDER", "This is a test SMS message.", "")
        .replace("{{id}}", uniqueId)
}

// Update toast message
if (!silent) {
    val message = if (isApp && isUpdate) "Sending Update Test..." else "Sending New Test..."
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

// ... rest of network code unchanged ...
```

**Then wire up buttons in onCreate() (around line 150):**

```kotlin
// Existing test button
findViewById<Button>(R.id.btn_test).setOnClickListener {
    performTest(isUpdate = false) // New incident
}

// Add new test update button
val btnTestUpdate = findViewById<Button>(R.id.btn_test_update)
btnTestUpdate.setOnClickListener {
    performTest(isUpdate = true) // Update existing
}
```

---

### **File 3: activity_app_config.xml**

Add second test button in the layout.

**Find the existing test button (should be around line 80-90):**
```xml
<Button
    android:id="@+id/btn_test"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Test"
    ... />
```

**Change it to:**
```xml
<Button
    android:id="@+id/btn_test"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Test New Incident"
    android:backgroundTint="#4CAF50"
    android:textColor="#FFFFFF"
    android:layout_marginTop="16dp" />

<!-- NEW: Test Update Button -->
<Button
    android:id="@+id/btn_test_update"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Test Update (Same ID)"
    android:backgroundTint="#FF9800"
    android:textColor="#FFFFFF"
    android:layout_marginTop="8dp" />
```

---

## ✅ **Expected Behavior**

### **Test Sequence:**

1. **Open App → App Config → Tap "Test New Incident"**
   - Generates ID: `#1844999` (example)
   - Saves `#1844999` to SharedPreferences
   - Sends to Apps Script
   - Apps Script creates NEW row with ID `#1844999`
   - Toast: "Test SUCCESS!"

2. **Check Google Sheet**
   - New row appears (example: row 247)
   - Column C shows: `#1844999`
   - Column A shows: `New Incident`
   - All fields populated

3. **Tap "Test Update (Same ID)"**
   - Retrieves ID: `#1844999` from prefs
   - Sends to Apps Script with status = "Update"
   - Apps Script searches Column C for `#1844999`
   - **FINDS IT** → Updates row 247 (doesn't create row 248!)
   - Toast: "Test SUCCESS!"

4. **Check Google Sheet - Row 247 now shows:**
   ```
   Column A: New Incident
             Update              ← Appended!
   
   Column B: 12/17/2025 8:30:45 PM
             12/17/2025 8:31:12 PM ← Appended!
   
   Column C: #1844999            ← STATIC (unchanged)
   
   Column D-G: (unchanged)       ← STATIC fields
   
   Column H-J: (appended)        ← DYNAMIC fields
   
   Column K+: nj312, njn751, nyl785, pa547 ← Unique codes merged
   ```

---

## 🐛 **Debugging Output**

After building, when you tap buttons, logcat will show:

**Test New:**
```
TEST: === TEST NEW INCIDENT ===
TEST: Generated ID: #1844999
TEST: Saved to prefs for update test
TEST: Mock BNN: N/D NJ | Test County | ...
TEST: Parsed successfully
TEST: Incident ID in JSON: #1844999
TEST: Status: New Incident
TEST: Endpoint: https://script.google.com/...
TEST: HTTP Status: 200
```

**Test Update:**
```
TEST: === TEST UPDATE ===
TEST: Reusing ID: #1844999
TEST: Mock BNN: U/D NJ | Test County | ...
TEST: Parsed successfully
TEST: Incident ID in JSON: #1844999
TEST: Status: Update
TEST: Endpoint: https://script.google.com/...
TEST: HTTP Status: 200
```

---

## 🎯 **Success Criteria**

After implementation:

1. ✅ Apps Script handles IDs with or without # prefix
2. ✅ "Test New Incident" creates new row
3. ✅ "Test Update" appends to SAME row (not create new)
4. ✅ Static columns (C-G) unchanged on update
5. ✅ Dynamic columns (A, B, H, I, J) show newline-separated values
6. ✅ FD codes merged (no duplicates)
7. ✅ Both tests use EXACT SAME ID

---

## 📋 **Files to Modify**

1. `scripts/Code.gs` - Normalize ID comparison (~5 lines changed)
2. `android/app/src/main/java/com/example/alertsheets/PrefsManager.kt` - Add 2 methods (~10 lines)
3. `android/app/src/main/java/com/example/alertsheets/AppConfigActivity.kt` - Add isUpdate param (~50 lines changed)
4. `android/app/src/main/res/layout/activity_app_config.xml` - Add button (~10 lines)

**Total:** ~75 lines across 4 files

---

## 🚀 **Build & Test**

```bash
cd D:\github\alerts-sheets\android
.\gradlew.bat :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Test:**
1. Open app → App Config
2. Tap "Test New Incident" → Check sheet (new row)
3. Note the row number
4. Tap "Test Update (Same ID)" → Check sheet (SAME row updated!)

---

**This will definitively prove if Apps Script upsert works and fix the update bug!** 🎯

