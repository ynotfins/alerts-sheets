# 🚨 CRITICAL: Updates Creating New Rows Instead of Appending

**Priority:** P0 - BLOCKING  
**Impact:** Updates not working - defeats entire purpose of app

---

## 🔍 **Problem**

Looking at Google Sheet rows 230-233:
- Row 230: `#1844731` - New Incident
- Row 231: `#1844731` - Update (should have appended to row 230!)
- Row 232: `#1844730` - New Incident  
- Row 233: `#1844712` - Update (should have appended to row 232!)

**Apps Script logic is correct** (lines 50-61 in `Code.gs`):
```javascript
if (incidentId !== "" && lastRow > 1) {
  const idValues = sheet.getRange(2, 3, lastRow - 1, 1).getValues();
  for (let i = 0; i < idValues.length; i++) {
    if (idValues[i][0].toString().trim() === incidentId) {
      foundRow = i + 2;
      break;
    }
  }
}
```

**This means:** Android is sending IDs that don't match what's in Column C.

---

## 🧪 **Add Two Test Buttons for Debugging**

We need to test:
1. **New Incident** with known ID
2. **Update** with SAME ID (to verify appending works)

### **Solution: Add Test Update Button**

**File:** `android/app/src/main/java/com/example/alertsheets/AppConfigActivity.kt`

**Current Test Button (Line 350-478):**
- Generates unique ID: `1${System.currentTimeMillis().toString().takeLast(6)}`
- Always creates NEW incidents

**Add Second Test Button:**

1. **Store last test ID** in SharedPreferences
2. **Test 1:** "Test New Incident" (existing button)
3. **Test 2:** "Test Update" (new button - reuses last ID)

---

## 📝 **Implementation Plan**

### **Step 1: Store Last Test ID**

Add to `PrefsManager.kt`:

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

### **Step 2: Modify Test Button Logic**

**Current:** `performTest(silent: Boolean = false)`

**Change to:** `performTest(isUpdate: Boolean = false, silent: Boolean = false)`

```kotlin
private fun performTest(isUpdate: Boolean = false, silent: Boolean = false) {
    val template = editJson.text.toString()
    val isApp = (radioGroupMode.checkedRadioButtonId == R.id.radio_app)

    val json = if (isApp) {
        // Determine ID
        val incidentId = if (isUpdate) {
            // Reuse last test ID for update testing
            val lastId = PrefsManager.getLastTestId(this)
            if (lastId.isEmpty()) {
                Toast.makeText(this, "No previous test to update! Send new incident first.", Toast.LENGTH_LONG).show()
                return
            }
            lastId
        } else {
            // Generate new unique ID
            val uniqueId = "1${System.currentTimeMillis().toString().takeLast(6)}"
            val fullId = "#$uniqueId"
            PrefsManager.saveLastTestId(this, fullId) // Save for future update test
            fullId
        }
        
        // Log for debugging
        android.util.Log.i("TEST", "=== Test ${if (isUpdate) "UPDATE" else "NEW"} ===")
        android.util.Log.i("TEST", "Incident ID: $incidentId")
        
        // Construct Mock BNN with STATUS prefix
        val statusPrefix = if (isUpdate) "U/D" else "N/D"
        val mockBnn = "$statusPrefix NJ | Test County | Test City | 888 Test Ave | TEST-TYPE | Testing ${if (isUpdate) "Update" else "New Incident"}: ID=$incidentId | <C> BNN | E-1/L-1 | nj312 | $incidentId"
        
        // Parse
        val parsed = Parser.parse(mockBnn)
        
        if (parsed != null) {
            val timestamped = parsed.copy(
                timestamp = TemplateEngine.getTimestamp(),
                status = if (isUpdate) "Update" else "New Incident"
            )
            android.util.Log.i("TEST", "Parsed Status: ${timestamped.status}")
            android.util.Log.i("TEST", "Parsed ID: ${timestamped.incidentId}")
            com.google.gson.Gson().toJson(timestamped)
        } else {
            android.util.Log.e("TEST", "PARSE FAILED! BNN: $mockBnn")
            return
        }
    } else {
        // SMS mode unchanged...
        val uniqueId = "SMS-${System.currentTimeMillis().toString().takeLast(4)}"
        TemplateEngine.applyGeneric(template, "sms", "TEST-SENDER", "This is a test SMS message.", "")
            .replace("{{id}}", uniqueId)
    }
    
    // Send to server (existing logic...)
    if (!silent) Toast.makeText(this, "Sending ${if (isUpdate) "Update" else "New"} Test...", Toast.LENGTH_SHORT).show()
    
    // ... rest of existing network code ...
}
```

---

### **Step 3: Add UI Buttons**

**File:** `android/app/src/main/res/layout/activity_app_config.xml`

Find the existing test button and add a second one:

```xml
<!-- Existing Test Button -->
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
    android:text="Test Update (Reuse Last ID)"
    android:backgroundTint="#FF9800"
    android:textColor="#FFFFFF"
    android:layout_marginTop="8dp" />
```

---

### **Step 4: Wire Up Buttons**

**File:** `AppConfigActivity.kt` in `onCreate()`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_app_config)
    
    // ... existing initialization ...
    
    // Existing test button
    findViewById<Button>(R.id.btn_test).setOnClickListener {
        performTest(isUpdate = false) // New incident
    }
    
    // NEW: Test update button
    findViewById<Button>(R.id.btn_test_update).setOnClickListener {
        performTest(isUpdate = true) // Update existing
    }
}
```

---

## ✅ **Expected Behavior**

### **Test Sequence:**

1. **Tap "Test New Incident"**
   - Generates ID: `#1844999` (example)
   - Sends to sheet
   - Sheet creates NEW row with ID `#1844999`
   - ID saved to SharedPreferences

2. **Tap "Test Update (Reuse Last ID)"**
   - Retrieves ID: `#1844999` (from step 1)
   - Sends to sheet with status = "Update"
   - Apps Script searches Column C for `#1844999`
   - Finds row → APPENDS to same row ✅
   - Row now shows:
     ```
     Column A: New Incident
               Update         ← Appended!
     
     Column B: 12/17/2025 8:30:45 PM
               12/17/2025 8:31:12 PM ← Appended!
     
     Column C: #1844999     ← STATIC (no change)
     ```

---

## 🐛 **Debugging: Log the JSON Payload**

The existing test button already logs (lines 421-423):

```kotlin
android.util.Log.i("TEST", "=== Test Request ===")
android.util.Log.i("TEST", "Endpoint: $url")
android.util.Log.i("TEST", "JSON Payload: $json")
```

**After adding update test**, verify logcat shows:

```
TEST: === Test NEW ===
TEST: Incident ID: #1844999
TEST: JSON Payload: {"incidentId":"#1844999","status":"New Incident",...}

(Tap update button)

TEST: === Test UPDATE ===
TEST: Incident ID: #1844999
TEST: JSON Payload: {"incidentId":"#1844999","status":"Update",...}
```

**Key Check:** Both payloads have EXACT SAME `incidentId` value.

---

## 🎯 **Success Criteria**

After implementation:

1. ✅ "Test New Incident" button creates new row
2. ✅ "Test Update" button APPENDS to that row (not create new)
3. ✅ Static columns (C-G) unchanged on update
4. ✅ Dynamic columns (A, B, H, I, J) show newline-separated values
5. ✅ Logcat shows SAME ID for both new and update

---

## 📋 **Files to Modify**

1. `android/app/src/main/java/com/example/alertsheets/PrefsManager.kt` - Add save/get last test ID
2. `android/app/src/main/java/com/example/alertsheets/AppConfigActivity.kt` - Add `isUpdate` param, modify logic
3. `android/app/src/main/res/layout/activity_app_config.xml` - Add second button

**Total changes:** ~30 lines across 3 files

---

## 🚀 **Implementation Order**

1. Add `saveLastTestId()` and `getLastTestId()` to PrefsManager
2. Modify `performTest()` to accept `isUpdate` parameter
3. Add second button to XML layout
4. Wire up button in `onCreate()`
5. Build and test!

---

**This will let us verify if the update appending logic works, or if there's a deeper issue with ID matching.** 🎯

