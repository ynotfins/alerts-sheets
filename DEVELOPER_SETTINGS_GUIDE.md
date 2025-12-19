# 🔧 Optimal Developer Settings for AlertsToSheets

Based on your screenshots, here are the recommended settings for optimal performance and reliability.

---

## ✅ Settings You Should ENABLE

### **Essential for App Functionality:**

1. **USB debugging** ✅ (Already ON)
   - Required for ADB deployment
   
2. **Stay awake** ✅ (Already ON)
   - Keeps screen on while charging - helpful for testing

3. **Don't keep activities** ⚠️ **TURN OFF**
   - Currently ON - this will destroy your app when you switch away
   - **Recommendation:** DISABLE this for normal operation
   - Only enable when testing memory leaks

4. **Show notification channel warnings** - Optional
   - Can help debug notification issues

5. **Wireless debugging** - Optional
   - Useful if you want to test without USB cable

---

## ❌ Settings You Should DISABLE

### **Background Processing (Critical for BNN Notifications):**

1. **Background process limit** ✅ (Standard limit is fine)
   - Keep at "Standard limit" or "No background process limit"
   - **DO NOT** set to "At most 1 process" or other restrictive limits

2. **Background check** - Should be OFF for your app
   - This can kill your notification listener
   - Check in "Settings → Apps → AlertsToSheets → Battery → Unrestricted"

### **Performance Killers:**

3. **Show GPU overdraw** - OFF (only for UI debugging)
4. **Show surface updates** - OFF
5. **Force GPU rendering** - Optional, but not needed
6. **Disable HW overlays** - OFF (wastes battery)
7. **Force MSAA** - OFF (wastes GPU)

### **Strict Mode:**

8. **Strict mode enabled** - ⚠️ **TURN OFF** for production testing
   - This will slow down your app and show false warnings
   - Only enable when debugging performance issues

---

## 🚫 Settings That Will BREAK Your App

### **DO NOT ENABLE These:**

1. **Disable child process restrictions** - Leave OFF
2. **Force activities to be resizable** - Can cause layout issues
3. **Disable adb authorization timeout** - Security risk
4. **Enable freeform windows** - Not needed, can cause issues
5. **Force Desktop mode** - Will break mobile UI

---

## 📱 Non-Developer Settings (Also Important)

### **In Main Settings → Apps → AlertsToSheets:**

1. **Battery Optimization:** **Unrestricted** ✅
   - Path: Settings → Apps → AlertsToSheets → Battery → Unrestricted
   
2. **Background Data:** **Allowed** ✅
   - Path: Settings → Apps → AlertsToSheets → Mobile data & Wi-Fi → Allow background data usage

3. **Autostart/Auto-launch:** **Allowed** ✅
   - Samsung: Settings → Apps → AlertsToSheets → Autostart → ON
   
4. **Notification Listener:** **Enabled** ✅
   - Path: Settings → Apps → Special access → Notification access → AlertsToSheets → ON

5. **Accessibility Service:** **Enabled** ✅
   - Path: Settings → Accessibility → Installed apps → AlertsToSheets → ON

6. **Battery Saver Mode:** **OFF** when testing
   - Battery saver will restrict background apps

---

## 🎯 Recommended Developer Settings Summary

Based on your screenshots:

| Setting | Current | Recommended | Why |
|---------|---------|-------------|-----|
| USB debugging | ✅ ON | ✅ ON | Required for ADB |
| Wireless debugging | ❌ OFF | Optional | Convenience |
| Don't keep activities | ✅ ON | ❌ **OFF** | **Will destroy your app!** |
| Background process limit | Standard | Standard | Don't restrict |
| Stay awake | ✅ ON | ✅ ON | Good for testing |
| Show surface updates | ❌ OFF | ❌ OFF | Performance |
| Force GPU rendering | ❌ OFF | ❌ OFF | Not needed |
| Strict mode enabled | ✅ ON | ❌ **OFF for testing** | **Slows down app** |
| Profile HWUI rendering | ❌ OFF | ❌ OFF | Only for debugging |
| Show notification channel warnings | ❌ OFF | ✅ Optional ON | Helps debug |

---

## ⚠️ **CRITICAL: Fix These Now**

### 1. **Don't Keep Activities - TURN OFF**
- **Location:** Developer options → Don't keep activities
- **Current:** ✅ ON (BAD)
- **Change to:** ❌ OFF
- **Why:** This setting destroys your app every time you switch away from it. Your `PermissionsActivity` will crash on resume because the views are destroyed.

### 2. **Strict Mode - TURN OFF for Normal Testing**
- **Location:** Developer options → Strict mode enabled
- **Current:** ✅ ON
- **Change to:** ❌ OFF (or only enable when specifically debugging)
- **Why:** This adds performance overhead and shows warnings that aren't relevant for production testing.

---

## 🔍 How to Check Your Current Settings

### Using ADB:
```bash
# Check if "Don't keep activities" is enabled
adb shell settings get global always_finish_activities
# 0 = OFF (good), 1 = ON (bad for you)

# Check background process limit
adb shell settings get global debug_app_mode
# Should be null or 0

# Disable "Don't keep activities" via ADB
adb shell settings put global always_finish_activities 0

# Disable strict mode via ADB
adb shell settings put global strict_mode_visual 0
```

---

## 🧪 Settings for Different Testing Scenarios

### **Scenario 1: Daily BNN Monitoring (Production Use)**
```
✅ USB debugging: ON (for emergency access)
❌ Don't keep activities: OFF
❌ Strict mode: OFF
❌ Background process limit: Standard
✅ Stay awake: ON (if phone is charging)
✅ Battery optimization: Unrestricted
```

### **Scenario 2: Active Development/Testing**
```
✅ USB debugging: ON
✅ Wireless debugging: ON (convenience)
❌ Don't keep activities: OFF (unless testing lifecycle)
❌ Strict mode: OFF (unless debugging performance)
✅ Show notification channel warnings: ON
✅ Logcat open: adb logcat -s AlertsToSheets:*
```

### **Scenario 3: Debugging Memory Issues**
```
✅ Don't keep activities: ON (forces lifecycle events)
✅ Background process limit: At most 1 process
✅ Profile HWUI rendering: ON (if UI issues)
⚠️ This is NOT for normal use - only when actively debugging
```

---

## 📊 Impact of Your Current Settings

### **Your Current Setup:**

**Good:**
- ✅ USB debugging enabled
- ✅ Stay awake enabled
- ✅ Standard background limits

**Needs Fixing:**
- ⚠️ **"Don't keep activities" is ON** → App will be destroyed when you switch away
- ⚠️ **"Strict mode" is ON** → App performance degraded

**Impact:**
- Your `PermissionsActivity` crash when resuming is likely caused by "Don't keep activities"
- Test response times might be slower due to strict mode overhead

---

## 🚀 Quick Fix Commands

Run these ADB commands to set optimal developer settings:

```bash
# Disable "Don't keep activities"
adb shell settings put global always_finish_activities 0

# Disable strict mode visual indicators
adb shell settings put global strict_mode_visual 0
adb shell settings put global strict_mode_flash 0

# Ensure no background process limit
adb shell settings put global debug_app_mode 0

# Restart your app to apply changes
adb shell am force-stop com.example.alertsheets
adb shell am start -n com.example.alertsheets/.MainActivity
```

---

## 📝 Summary

**Change these 2 settings immediately:**
1. **Don't keep activities** → OFF
2. **Strict mode enabled** → OFF

**Keep everything else as-is** - your other settings look good!

After changing these settings, reinstall the app and test the Permissions screen again. The crashes should stop.

---

**TL;DR:** Your current settings are optimized for **finding bugs during development**, but they're making your app **unstable for production testing**. Turn off "Don't keep activities" and "Strict mode" for normal BNN monitoring.

