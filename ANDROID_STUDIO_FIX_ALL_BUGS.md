# 🔧 ANDROID STUDIO - FIX ALL BUGS

## 🐛 **CONFIRMED BUGS TO FIX:**

### 1. ❌ **Test buttons missing on Payload page**
**Root Cause:** No ScrollView wrapping the main layout in `AppConfigActivity.kt`
**Fix:** Wrap the entire `layout` LinearLayout in a ScrollView before `setContentView()`

```kotlin
// BEFORE (Line 282):
setContentView(layout)

// AFTER:
val scrollView = ScrollView(this)
scrollView.addView(layout)
setContentView(scrollView)
```

---

### 2. ❌ **Clean Invalid Characters button sends data to sheet**
**Root Cause:** Button cleans the TEMPLATE text instead of test data
**Fix:** The button at line 184-199 should ONLY clean the EditText display, not trigger any API calls. This is correct, but verify it's not calling `performTest()` somehow.

**VERIFY:** Line 189 only calls `TemplateEngine.cleanText(current)` and sets it back to the EditText. This should NOT send data.

**If it's still sending:** Check if the `onPause()` auto-test is triggering. Add a flag to disable auto-test when cleaning.

---

### 3. ❌ **App Sources page only shows 2 apps instead of all installed**
**Root Cause:** The filter logic in `AppsListActivity.kt` is INVERTED for system apps

```kotlin
// CURRENT LOGIC (Line 84-87):
val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
if (!showSystemApps && isSystemApp) continue  // ← This is WRONG!

// SHOULD BE:
val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
if (showSystemApps && !isSystemApp) continue  // ← Show ONLY system when toggled
if (!showSystemApps && isSystemApp) continue  // ← Hide system by default
```

**CORRECTED LOGIC:**
```kotlin
private fun filterApps() {
    val pm = packageManager
    filteredApps.clear()
    
    for (app in allApps) {
        // Filter system apps based on toggle
        val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        
        // When "System Apps" is checked, show ONLY system apps
        // When unchecked, show ONLY non-system apps (installed apps)
        if (showSystemApps) {
            if (!isSystemApp) continue  // Skip non-system when showing system
        } else {
            if (isSystemApp) continue   // Skip system when showing installed
        }
        
        // Filter by search query
        if (searchQuery.isNotEmpty()) {
            val appName = try {
                app.loadLabel(pm).toString().lowercase()
            } catch (e: Exception) {
                app.packageName.lowercase()
            }
            val packageName = app.packageName.lowercase()
            
            if (!appName.contains(searchQuery) && !packageName.contains(searchQuery)) {
                continue
            }
        }
        
        filteredApps.add(app)
    }
    
    adapter.notifyDataSetChanged()
}
```

---

### 4. ❌ **BNN not in App Sources list**
**Root Cause:** BNN is hardcoded in `SourceRepository.kt` as `com.example.bnn` but the actual app package is different

**Find BNN's actual package name:**
1. Open Android Studio
2. Run: `adb shell pm list packages | findstr -i bnn`
3. OR check what package name BNN notifications come from in logcat

**Then update line 113 in SourceRepository.kt:**
```kotlin
Source(
    id = "com.actual.bnn.package",  // ← Use real package name
    type = SourceType.APP,
    name = "BNN Alerts",
    // ... rest of config
)
```

---

### 5. ❌ **Dashboard shows "Monitoring 1 apps, 1 SMS" (hardcoded)**
**Root Cause:** `SourceManager.kt` returns hardcoded default sources that don't reflect actual enabled sources

**Fix:** The issue is that `getDefaultSources()` in `SourceRepository.kt` creates 3 sources:
- BNN (enabled)
- Generic Apps (disabled)
- SMS Dispatch (enabled)

When the user hasn't saved any sources yet, it returns these defaults. The dashboard then counts: 1 app (BNN) + 1 SMS (Dispatch).

**Solution:** 
- Make sure the SourceRepository is actually saving to `sources.json`
- The dashboard should pull from actual saved sources, not defaults
- OR: Remove the default sources and force user to add sources manually

**Verify in Android Studio:**
1. Check `JsonStorage.kt` - is it writing to internal storage correctly?
2. Add logging to see if `sources.json` file exists
3. If file doesn't exist, defaults are always returned

---

### 6. ❌ **Can't remove BNN or SMS from monitoring**
**Root Cause:** No UI to manage sources (enable/disable BNN, SMS, etc.)

**Need to create:**
- Sources Management Activity (similar to AppsListActivity)
- Shows all sources (BNN, Generic Apps, SMS Dispatch)
- Toggle switches to enable/disable each
- Delete button for custom sources
- Add button to create new sources

---

## 🔨 **GRADLE/BUILD ISSUES:**

If the above fixes don't work, these might be Gradle cache issues:

### **Clean and Rebuild:**
```bash
cd D:\github\alerts-sheets\android
.\gradlew clean
.\gradlew assembleDebug
```

### **Invalidate Caches in Android Studio:**
1. File → Invalidate Caches...
2. Select "Clear file system cache and Local History"
3. Select "Clear downloaded shared indexes"
4. Click "Invalidate and Restart"

### **Wipe app data on phone:**
```bash
adb shell pm clear com.example.alertsheets
```

---

## 📋 **TESTING CHECKLIST:**

After fixes:
- [ ] Test buttons visible on Payload page
- [ ] Scroll works on Payload page
- [ ] Clean button only cleans text, doesn't send data
- [ ] App Sources shows ALL installed apps by default
- [ ] System Apps toggle shows ONLY system apps
- [ ] BNN appears in App Sources list
- [ ] Dashboard shows correct count of enabled sources
- [ ] Can toggle BNN/SMS on and off

---

## 🚀 **PRIORITY ORDER:**

1. **FIX #1 (ScrollView)** - Easiest, immediate impact
2. **FIX #3 (App filtering)** - Critical for usability
3. **FIX #4 (BNN package name)** - Need to find real package
4. **FIX #5 & #6 (Sources management)** - Requires new UI

---

## 📝 **FILES TO MODIFY:**

1. `android/app/src/main/java/com/example/alertsheets/AppConfigActivity.kt` (Line 282)
2. `android/app/src/main/java/com/example/alertsheets/AppsListActivity.kt` (Lines 80-107)
3. `android/app/src/main/java/com/example/alertsheets/data/repositories/SourceRepository.kt` (Line 113)

---

## 🧪 **HOW TO FIND BNN'S REAL PACKAGE:**

```bash
# Method 1: List all packages
adb shell pm list packages | findstr -i bnn

# Method 2: Watch logcat when BNN notification arrives
adb logcat | findstr -i "package"

# Method 3: Check notification listener logs
adb logcat | findstr -i "AlertsNotificationListener"
```

The package name will be something like `com.bnn.app` or `com.breakingnewsnetwork` etc.

---

**LET'S OPEN ANDROID STUDIO AND FIX THESE SYSTEMATICALLY!** 🎯

