# 🔥 GOD MODE - MAXIMUM PERMISSIONS (CRITICAL)

> **THIS IS THE #1 PRIORITY FOR THIS APP**  
> **WITHOUT GOD MODE, THE APP IS WORTHLESS**  
> **READ THIS FIRST BEFORE DOING ANYTHING ELSE**

---

## 🎯 **WHY GOD MODE EXISTS**

This app has **ONE JOB**: Capture **100% of notifications and SMS** before Android hides them.

**The Problem:**
- Android 15+ aggressively hides notifications
- Standard apps miss 30-50% of critical alerts
- Battery optimization kills background services
- Restricted settings prevent accessibility access
- SMS permissions are heavily restricted

**The Solution:**
- **GOD MODE** - Maximum possible permissions
- **System App** - Bypass all Android restrictions
- **No compromises** - If Android blocks it, we work around it

---

## ✅ **REQUIRED PERMISSIONS (NON-NEGOTIABLE)**

### **Notification Permissions**
```xml
<!-- Core notification access -->
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />

<!-- Foreground service to prevent Android from killing us -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Boot & restart -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Battery optimization bypass -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Overlays & full screen (for critical alerts) -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />

<!-- Exact alarms (for retry logic) -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

### **SMS Permissions (CRITICAL - FULL READ/WRITE/RECEIVE ON EVERYTHING)**
```xml
<!-- READ SMS: Read all messages (past, present, future) -->
<uses-permission android:name="android.permission.READ_SMS" />

<!-- RECEIVE_SMS: Receive incoming SMS in real-time -->
<uses-permission android:name="android.permission.RECEIVE_SMS" />

<!-- RECEIVE_MMS: Monitor incoming MMS messages -->
<uses-permission android:name="android.permission.RECEIVE_MMS" />

<!-- RECEIVE_WAP_PUSH: Receive WAP push messages -->
<uses-permission android:name="android.permission.RECEIVE_WAP_PUSH" />

<!-- SEND_SMS: Send SMS (for testing/verification) -->
<uses-permission android:name="android.permission.SEND_SMS" />

<!-- WRITE_SMS: Write to SMS database (full control) -->
<uses-permission android:name="android.permission.WRITE_SMS" />

<!-- READ_PHONE_STATE: Read phone state (for SMS context) -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />

<!-- BROADCAST_SMS: Broadcast SMS received intents -->
<uses-permission android:name="android.permission.BROADCAST_SMS" />
```

### **SMS DEFAULT APP ROLE (MAXIMUM PRIVILEGE)**
```xml
<!-- Declare SMS capabilities (allows becoming default SMS app) -->
<uses-permission android:name="android.permission.READ_CONTACTS" />

<!-- In your Application class or service, request SMS role: -->
<!-- RoleManager.createRequestRoleIntent(RoleManager.ROLE_SMS) -->
```

### **Network & Internet**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 🔒 **SYSTEM APP INSTALLATION (BYPASS ALL RESTRICTIONS)**

### **Why System App?**
- **Unrestricted notification access** - See everything, always
- **No battery optimization** - Never killed by Android
- **No restricted settings** - Full accessibility & notification access
- **Persistent background service** - Runs forever
- **Priority over user apps** - Gets notifications first

### **How to Install as System App**

#### **Method 1: Root (Magisk Module) - RECOMMENDED**

1. **Create Magisk Module Structure:**
   ```bash
   mkdir -p AlertsToSheets/system/priv-app/AlertsToSheets
   cp app-debug.apk AlertsToSheets/system/priv-app/AlertsToSheets/AlertsToSheets.apk
   ```

2. **Create module.prop:**
   ```
   id=alertstosheets
   name=AlertsToSheets System App
   version=v2.0
   versionCode=200
   author=YourName
   description=Critical notification & SMS monitoring system app
   ```

3. **Install via Magisk Manager:**
   - Copy `AlertsToSheets` folder to `/sdcard/Download/`
   - Open Magisk Manager
   - Modules → Install from storage
   - Select the ZIP (or create ZIP from folder)
   - Reboot

4. **Verify System App Status:**
   ```bash
   adb shell pm list packages -s | grep alertsheets
   # Should show: package:com.example.alertsheets
   ```

#### **Method 2: ADB Root (Without Magisk)**

```bash
# Enable root
adb root

# Remount system as writable
adb remount

# Push APK to system
adb push app-debug.apk /system/priv-app/AlertsToSheets/AlertsToSheets.apk

# Set permissions
adb shell chmod 644 /system/priv-app/AlertsToSheets/AlertsToSheets.apk
adb shell chown root:root /system/priv-app/AlertsToSheets/AlertsToSheets.apk

# Reboot
adb reboot
```

#### **Method 3: Custom ROM**

If building custom ROM, add to `device/[manufacturer]/[device]/device.mk`:

```makefile
PRODUCT_PACKAGES += \
    AlertsToSheets
```

---

## 🛡️ **RUNTIME PERMISSIONS (MUST GRANT ALL)**

### **One-Time Setup (After Install)**

```bash
# Grant ALL notification permissions
adb shell pm grant com.example.alertsheets android.permission.POST_NOTIFICATIONS
adb shell pm grant com.example.alertsheets android.permission.ACCESS_NOTIFICATION_POLICY

# Grant ALL SMS permissions (FULL READ/WRITE/RECEIVE)
adb shell pm grant com.example.alertsheets android.permission.READ_SMS
adb shell pm grant com.example.alertsheets android.permission.RECEIVE_SMS
adb shell pm grant com.example.alertsheets android.permission.RECEIVE_MMS
adb shell pm grant com.example.alertsheets android.permission.RECEIVE_WAP_PUSH
adb shell pm grant com.example.alertsheets android.permission.SEND_SMS
adb shell pm grant com.example.alertsheets android.permission.WRITE_SMS
adb shell pm grant com.example.alertsheets android.permission.BROADCAST_SMS
adb shell pm grant com.example.alertsheets android.permission.READ_PHONE_STATE
adb shell pm grant com.example.alertsheets android.permission.READ_CONTACTS

# Grant system-level permissions
adb shell pm grant com.example.alertsheets android.permission.SYSTEM_ALERT_WINDOW
adb shell pm grant com.example.alertsheets android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

# Disable battery optimization (CRITICAL!)
adb shell dumpsys deviceidle whitelist +com.example.alertsheets

# Enable notification listener
adb shell settings put secure enabled_notification_listeners com.example.alertsheets/com.example.alertsheets.services.AlertsNotificationListener

# Set as default SMS app (ROLE_SMS - MAXIMUM PRIVILEGE)
adb shell cmd role add-role-holder android.app.role.SMS com.example.alertsheets

# Verify SMS role granted
adb shell cmd role get-role-holders android.app.role.SMS
# Expected output should include: com.example.alertsheets
```

---

## ⚙️ **SAMSUNG-SPECIFIC (Knox & Game Launcher)**

### **Disable Knox Restrictions**

1. **Developer Options:**
   - Settings → Developer Options
   - Find "Allow apps to access restricted settings"
   - Enable for AlertsToSheets

2. **ADB Method:**
   ```bash
   adb shell pm grant com.example.alertsheets android.permission.RESTRICTED_SETTINGS
   ```

### **Disable Game Launcher Interference**

Samsung Game Launcher can block notifications during "game mode":

```bash
# Remove from Game Launcher
adb shell pm disable-user com.samsung.android.game.gametools

# Or exclude AlertsToSheets
# Settings → Game Launcher → Game Booster → Disable for AlertsToSheets
```

---

## 📱 **DEVICE-SPECIFIC SETTINGS**

### **Disable All Restrictions:**

1. **Battery Optimization: OFF**
   - Settings → Apps → AlertsToSheets → Battery → Unrestricted

2. **Background Activity: ALLOWED**
   - Settings → Apps → AlertsToSheets → Mobile data → Allow background data usage

3. **Adaptive Battery: EXCLUDE**
   - Settings → Battery → Adaptive battery → Exclude AlertsToSheets

4. **Sleeping Apps: REMOVE**
   - Settings → Battery → Background usage limits → Remove AlertsToSheets from "Sleeping apps"

5. **Auto-Start: ENABLED**
   - Settings → Apps → AlertsToSheets → Permissions → Auto-start → Enable

6. **Notification Access: GRANTED**
   - Settings → Notifications → Notification access → AlertsToSheets → Enable

7. **Accessibility Service: ENABLED**
   - Settings → Accessibility → Installed services → AlertsToSheets → Enable

8. **SMS Default App (CRITICAL - MAXIMUM PRIVILEGE):**
   - Settings → Apps → Default apps → SMS app → AlertsToSheets
   - This gives ROLE_SMS - the highest possible SMS privilege
   - Grants full read/write access to SMS database
   - Receives ALL SMS before any other app
   - Can intercept, modify, or block SMS
   
   **ADB Method (Recommended):**
   ```bash
   adb shell cmd role add-role-holder android.app.role.SMS com.example.alertsheets
   ```

---

## ✅ **VERIFICATION CHECKLIST**

Run these commands to verify GOD MODE is active:

```bash
# 1. Check if system app
adb shell pm list packages -s | grep alertsheets
# Expected: package:com.example.alertsheets

# 2. Check all permissions granted
adb shell dumpsys package com.example.alertsheets | grep permission
# Expected: All permissions from manifest listed as "granted=true"

# 3. Check battery whitelist
adb shell dumpsys deviceidle whitelist | grep alertsheets
# Expected: com.example.alertsheets

# 4. Check notification listener
adb shell settings get secure enabled_notification_listeners | grep alertsheets
# Expected: com.example.alertsheets/...

# 5. Check foreground service running
adb shell dumpsys activity services | grep alertsheets
# Expected: Service running

# 6. Send test notification
adb shell am broadcast -a com.example.alertsheets.TEST_NOTIFICATION
# Expected: Captured and sent to Google Sheets
```

---

## 🔥 **TROUBLESHOOTING GOD MODE**

### **Problem: Notifications Still Missing**

1. **Check Service Running:**
   ```bash
   adb shell dumpsys activity services | grep AlertsNotificationListener
   ```

2. **Restart Service:**
   ```bash
   adb shell am stopservice com.example.alertsheets/.services.AlertsNotificationListener
   adb shell am startservice com.example.alertsheets/.services.AlertsNotificationListener
   ```

3. **Check Logcat:**
   ```bash
   adb logcat -s AlertsNotificationListener:V DataPipeline:V
   ```

### **Problem: SMS Not Received**

1. **Check SMS Permissions:**
   ```bash
   adb shell pm grant com.example.alertsheets android.permission.READ_SMS
   adb shell pm grant com.example.alertsheets android.permission.RECEIVE_SMS
   ```

2. **Check SMS Receiver Registered:**
   ```bash
   adb shell dumpsys package com.example.alertsheets | grep Receiver
   ```

3. **Send Test SMS:**
   ```bash
   adb emu sms send 5551234567 "Test SMS"
   ```

### **Problem: Service Killed by Android**

**Solution: Make system app + foreground service**

```kotlin
// In NotificationListenerService.onCreate()
val notification = NotificationCompat.Builder(this, CHANNEL_ID)
    .setContentTitle("AlertsToSheets")
    .setContentText("Monitoring 300+ alerts/day")
    .setSmallIcon(R.drawable.ic_notification)
    .setPriority(NotificationCompat.PRIORITY_MAX)
    .build()

startForeground(1, notification)
```

---

## 📋 **PRE-DEPLOYMENT CHECKLIST**

Before deploying to main phone:

- [ ] APK installed as system app (Magisk or ADB root)
- [ ] All permissions granted (check with ADB)
- [ ] Battery optimization disabled
- [ ] Notification listener enabled
- [ ] Accessibility service enabled (optional but recommended)
- [ ] SMS permissions granted (all 5)
- [ ] Foreground service running
- [ ] Test notification captured
- [ ] Test SMS captured
- [ ] Both sent to Google Sheets
- [ ] No crashes for 24 hours on second phone

---

## 🚨 **NEVER FORGET:**

1. **GOD MODE IS NON-NEGOTIABLE** - Without it, the app is useless
2. **SYSTEM APP IS REQUIRED** - Don't fight Android, become Android
3. **ALL SMS PERMISSIONS** - Read, receive, send, MMS, WAP push
4. **NO BATTERY OPTIMIZATION** - Ever. Period.
5. **300+ ALERTS/DAY** - We can't miss a single one

---

## 📝 **DOCUMENTATION REFERENCES**

- v1 Permissions: `docs/architecture/PERMISSIONS_GUIDE.md`
- Android Manifest: `android/app/src/main/AndroidManifest.xml`
- Service Implementation: `android/app/src/main/java/com/example/alertsheets/services/`

---

**This document is the foundation of the entire app. If GOD MODE fails, nothing else matters.**

**Last Updated:** Dec 19, 2025  
**Status:** ✅ Documented, ready for v2 implementation

