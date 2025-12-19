# AlertsToSheets V2 - Deployment Guide

**Date:** Dec 19, 2025  
**Branch:** `feature/v2-clean-refactor`  
**APK Location:** `android/app/build/outputs/apk/debug/app-debug.apk`

---

## 🎯 **DEPLOYMENT STATUS**

✅ **BUILD SUCCESSFUL**  
- APK compiled without errors
- God Mode permissions enabled
- Samsung One UI theme active
- All v1 activities integrated

---

## 📱 **INSTALLATION INSTRUCTIONS**

### **Step 1: Connect Phone via USB**

1. Enable **USB Debugging** on phone:
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → Enable "USB Debugging"

2. Connect phone to PC via USB

3. Select **File Transfer (MTP)** mode on phone

4. Verify connection:
```powershell
adb devices
```

Should show your device ID.

---

### **Step 2: Install APK**

```powershell
cd D:\github\alerts-sheets
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

- `-r` flag reinstalls and keeps data if app already exists

---

### **Step 3: Grant GOD MODE Permissions**

#### **A. Notification Access (CRITICAL)**

```powershell
# Open Notification Listener settings
adb shell am start -a android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS

# Then manually enable "AlertsToSheets"
```

#### **B. SMS Permissions (All 9)**

```powershell
# Grant all SMS permissions at once
adb shell pm grant com.example.alertsheets android.permission.READ_SMS
adb shell pm grant com.example.alertsheets android.permission.RECEIVE_SMS
adb shell pm grant com.example.alertsheets android.permission.RECEIVE_MMS
adb shell pm grant com.example.alertsheets android.permission.RECEIVE_WAP_PUSH
adb shell pm grant com.example.alertsheets android.permission.SEND_SMS
adb shell pm grant com.example.alertsheets android.permission.READ_PHONE_STATE
adb shell pm grant com.example.alertsheets android.permission.READ_CONTACTS
```

**Note:** `WRITE_SMS` and `BROADCAST_SMS` require system app privileges or root.

#### **C. Set as Default SMS App (ROLE_SMS)**

```powershell
# Open Default SMS app settings
adb shell am start -a android.provider.Telephony.ACTION_CHANGE_DEFAULT

# Or use direct command (requires API 29+)
adb shell cmd role add-role-holder --user 0 android.app.role.SMS com.example.alertsheets
```

#### **D. Battery Optimization (CRITICAL)**

```powershell
# Disable battery optimization
adb shell am start -a android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
```

Manually select **AlertsToSheets** and set to "Don't optimize".

#### **E. Other Permissions**

```powershell
# Overlay permission (for dialogs)
adb shell appops set com.example.alertsheets SYSTEM_ALERT_WINDOW allow

# Post notifications (Android 13+)
adb shell pm grant com.example.alertsheets android.permission.POST_NOTIFICATIONS
```

---

### **Step 4: Verify Installation**

```powershell
# Check app is installed
adb shell pm list packages | findstr alertsheets

# Launch app
adb shell am start -n com.example.alertsheets/.ui.MainActivity

# Check logs
adb logcat | findstr "AlertsApp\|AlertsNotif\|AlertsSms"
```

---

## 🔧 **CONFIGURATION**

### **1. Add Endpoint URL**

1. Open app
2. Tap **Endpoints** card
3. Add your Google Apps Script URL

### **2. Configure BNN Source**

1. Tap **Apps** card
2. Enable **BNN Connect** (or your notification app)
3. Configure payload template (use existing "Rock Solid Default")

### **3. Configure SMS Source**

1. Tap **SMS** card
2. Add phone numbers to monitor
3. Select SMS template

### **4. Test Payload**

1. Tap **Payloads** card
2. Select source (App or SMS)
3. Click **Test New Notification**
4. Verify JSON preview
5. Confirm send
6. Check Google Sheet for new row

---

## 🚀 **SAMSUNG-SPECIFIC FIXES**

### **Fix: Notification Access Keeps Turning Off**

```powershell
# Disable Samsung's notification optimization
adb shell pm disable-user --user 0 com.samsung.android.sm_cn
adb shell pm disable-user --user 0 com.samsung.android.sm

# OR set as system app (requires root/Magisk)
adb root
adb remount
adb push app-debug.apk /system/priv-app/AlertsToSheets/AlertsToSheets.apk
adb reboot
```

### **Fix: SMS Not Capturing**

1. Set AlertsToSheets as **default SMS app** (ROLE_SMS)
2. Disable Samsung Messages optimizations:

```powershell
adb shell pm disable-user --user 0 com.samsung.android.messaging
```

3. Verify SMS priority:

```powershell
adb shell dumpsys package com.example.alertsheets | findstr priority
```

Should show `priority=2147483647` for SMS receiver.

---

## ✅ **VERIFICATION CHECKLIST**

After installation, verify:

- [ ] App launches and shows Samsung UI dashboard
- [ ] All 6 cards are visible (Apps, SMS, Payloads, Endpoints, Permissions, Logs)
- [ ] Service status shows "● Service Active (GOD MODE)"
- [ ] Notification listener is enabled in system settings
- [ ] SMS permissions granted (check Permissions card)
- [ ] Battery optimization disabled
- [ ] Default SMS app set to AlertsToSheets (for SMS monitoring)
- [ ] Test notification sends successfully
- [ ] Data appears in Google Sheet

---

## 🔍 **TROUBLESHOOTING**

### **App Crashes on Launch**

```powershell
# Clear app data and reinstall
adb shell pm clear com.example.alertsheets
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

### **Notifications Not Capturing**

```powershell
# Check if notification listener is running
adb shell dumpsys notification_listener

# Restart service
adb shell am stopservice com.example.alertsheets/.services.AlertsNotificationListener
adb shell am startservice com.example.alertsheets/.services.AlertsNotificationListener
```

### **SMS Not Capturing**

```powershell
# Verify SMS receiver is registered
adb shell dumpsys package com.example.alertsheets | findstr "SMS_RECEIVED"

# Send test SMS and watch logs
adb logcat -c
adb logcat | findstr "AlertsSms"
# (send SMS to phone)
```

### **Build Errors**

```powershell
# Clean build
cd D:\github\alerts-sheets\android
.\gradlew.bat clean
.\gradlew.bat assembleDebug --no-daemon
```

---

## 📊 **MONITORING**

### **Real-time Logs**

```powershell
# Watch all app logs
adb logcat | findstr "AlertsApp\|AlertsNotif\|AlertsSms\|AlertsPipeline"

# Watch only errors
adb logcat *:E | findstr alertsheets
```

### **Check Queue Status**

```powershell
# Export and view queue database
adb shell "su -c 'cp /data/data/com.example.alertsheets/databases/request_queue.db /sdcard/'"
adb pull /sdcard/request_queue.db .
# Open with SQLite browser
```

---

## 🎛️ **OPTIMAL PHONE SETTINGS**

Based on your screenshots:

### **Recommended Changes:**

1. **Developer Options:**
   - Don't keep activities: `OFF`
   - Background process limit: `Standard limit` (4 processes)
   - Force GPU rendering: `OFF` (can cause crashes)

2. **Battery:**
   - AlertsToSheets → Battery usage → `Unrestricted`
   - Power saving mode → `OFF` (when monitoring)

3. **Notifications:**
   - Show notifications: `ON`
   - Allow notification dots: `ON`
   - Do Not Disturb exceptions: Add AlertsToSheets

4. **Data Usage:**
   - Background data: `Unrestricted`

---

## 📁 **FILES CHECKLIST**

Before deployment, ensure these exist:

- [x] APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- [x] Manifest: `android/app/src/main/AndroidManifest.xml` (God Mode)
- [x] MainActivity: `android/app/src/main/java/com/example/alertsheets/ui/MainActivity.kt`
- [x] Services: `AlertsNotificationListener.kt`, `AlertsSmsReceiver.kt`
- [x] God Mode docs: `docs/v2-refactor/GOD_MODE.md`

---

## 🔄 **ROLLBACK TO V1**

If v2 has issues, rollback:

```powershell
# Switch back to master branch
git checkout master

# Rebuild v1
cd android
.\gradlew.bat clean assembleDebug

# Install v1
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

V1 remains untouched on `master` branch.

---

## 📞 **SUPPORT**

**Logs Location:** `android/app/build/outputs/logs/`  
**Documentation:** `docs/v2-refactor/`  
**Issues:** Check `VALIDATION_REPORT.md` for known issues

---

**Status:** ✅ Ready for deployment  
**Next Step:** Connect phone and run installation commands above


