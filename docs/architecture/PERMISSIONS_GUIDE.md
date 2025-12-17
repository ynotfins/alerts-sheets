# Maximum Permissions Guide - Android 15+ Bulletproof Configuration

**Last Updated:** Dec 17, 2025  
**Target:** Private use, NOT Play Store (no restrictions)

---

## 🔒 Critical Permissions (MUST HAVE)

### 1. Notification Access (Core Functionality)
**Manifest:**
```xml
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> <!-- Android 13+ -->
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```

**Runtime Setup:**
1. Settings → Notifications → Notification Access → Enable "AlertsToSheets"
2. Android 13+: Allow notification permission when prompted
3. Do Not Disturb: Allow app to override DND

**Android 15 Specific:**
- Notification Listener is MORE restricted on Android 15
- Must be explicitly enabled in Settings (cannot be enabled programmatically)
- App MUST remain in "Recently used apps" to stay active

### 2. Battery Optimization Bypass (24/7 Operation)
**Manifest:**
```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

**Runtime Setup:**
1. Settings → Battery → Battery Optimization → All apps → AlertsToSheets → Don't optimize
2. Settings → Apps → AlertsToSheets → Battery → Unrestricted

**Android 15 Specific:**
- "Adaptive Battery" is MORE aggressive
- Must disable "Pause app activity if unused" in app settings
- Recommended: Add app to "Never sleeping apps" list

### 3. Foreground Service (Always Running)
**Manifest:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

**Code:**
```kotlin
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
    startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
} else {
    startForeground(101, notification)
}
```

**Android 15 Specific:**
- Foreground service notifications CANNOT be swiped away (good!)
- Service auto-restarts if killed by system
- Must declare foregroundServiceType in manifest

### 4. Auto-Start After Reboot
**Manifest:**
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<receiver android:name=".BootReceiver" android:exported="true">
    <intent-filter android:priority="999">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

**Android 15 Specific:**
- Boot receiver priority MUST be high (999)
- Service may take 30-60 seconds to start after boot
- Check "Auto-start" permission in device settings (Xiaomi/Samsung)

---

## 📱 Android 15 Specific Issues & Solutions

### Issue 1: Notification Listener Stops Working
**Symptom:** Notifications stop being intercepted after a few hours/days

**Root Causes:**
1. App removed from "Recent apps" (kills background service)
2. Battery optimization re-enabled by system
3. Adaptive Battery learning disabled the app

**Solutions:**
1. ✅ **Pin app in Recent Apps** (long-press → lock icon)
2. ✅ **Disable "Remove permissions if unused"** (Android 15 feature)
   - Settings → Apps → AlertsToSheets → Permissions → Don't remove
3. ✅ **Add to Protected Apps** (Manufacturer-specific)
   - Samsung: Device Care → Battery → Background usage limits → Never sleeping apps
   - Xiaomi: Security → Permissions → Autostart → Enable
   - Huawei: Settings → Battery → App launch → Manage manually
4. ✅ **Restart Notification Listener Daily** (Workaround)
   - Use AlarmManager to rebind service every 24 hours
   - Already implemented in NotificationService

### Issue 2: SMS Not Received
**Symptom:** Some SMS messages not forwarded

**Root Cause:** Android 15 restricts SMS receivers to default SMS app only

**Solution:**
1. ✅ **Set as Default SMS App** (if possible)
   - Settings → Apps → Default apps → SMS app → AlertsToSheets
2. ❌ **OR: Use Accessibility Service** (less reliable)
3. ✅ **Best: Dual mode** (both NotificationListener + Accessibility)

### Issue 3: Foreground Service Killed
**Symptom:** App stops working, no foreground notification visible

**Root Cause:** System aggressively kills services on low memory

**Solution:**
1. ✅ **Persistent Notification** (cannot be dismissed)
2. ✅ **START_STICKY** service return type
3. ✅ **Immediate restart** in onDestroy()
   ```kotlin
   override fun onDestroy() {
       super.onDestroy()
       if (isRunning) {
           // Restart service
           val intent = Intent(this, NotificationService::class.java)
           startForegroundService(intent)
       }
   }
   ```

---

## 🛡️ Maximum Reliability Checklist

### Initial Setup (One-Time)
- [ ] Enable Notification Listener (Settings → Notification Access)
- [ ] Disable Battery Optimization (Settings → Battery)
- [ ] Allow all permissions when prompted
- [ ] Pin app in Recent Apps
- [ ] Disable "Remove permissions if unused"
- [ ] Add to Protected/Never Sleeping Apps list
- [ ] Enable Auto-start (Manufacturer settings)

### Daily Verification
- [ ] Foreground notification visible
- [ ] Dashboard shows "Service Active" (green)
- [ ] Test notification arrives in sheet

### Weekly Verification
- [ ] Check logs for gaps (missed notifications)
- [ ] Verify battery usage is "Unrestricted"
- [ ] Verify notification listener still enabled

---

## 🔧 ADB Commands for Verification

### Check Notification Listener Status
```powershell
adb shell settings get secure enabled_notification_listeners
# Expected: com.example.alertsheets/com.example.alertsheets.NotificationService
```

### Force Enable Notification Listener (Root Required)
```powershell
adb shell settings put secure enabled_notification_listeners com.example.alertsheets/com.example.alertsheets.NotificationService
```

### Check Battery Optimization
```powershell
adb shell dumpsys deviceidle whitelist | findstr alertsheets
# Expected: com.example.alertsheets should be listed
```

### Disable Battery Optimization (Programmatically)
```powershell
adb shell cmd appops set com.example.alertsheets RUN_IN_BACKGROUND allow
adb shell cmd appops set com.example.alertsheets START_FOREGROUND allow
```

### Check if Service is Running
```powershell
adb shell dumpsys activity services | findstr NotificationService
# Expected: ServiceRecord with "app=ProcessRecord"
```

### Force Start Service After Reboot
```powershell
adb shell am startservice -n com.example.alertsheets/.NotificationService
```

---

## 📊 Permission Matrix

| Permission | Android 15+ | Required | Play Store Allowed | Our App |
|------------|-------------|----------|-------------------|---------|
| BIND_NOTIFICATION_LISTENER | ✅ Yes | ✅ Critical | ✅ Yes | ✅ Granted |
| POST_NOTIFICATIONS | ✅ Yes | ✅ Critical | ✅ Yes | ✅ Granted |
| REQUEST_IGNORE_BATTERY_OPT | ✅ Yes | ✅ Critical | ⚠️ Restricted* | ✅ Granted |
| QUERY_ALL_PACKAGES | ✅ Yes | ✅ Required | ⚠️ Restricted* | ✅ Granted |
| SCHEDULE_EXACT_ALARM | ✅ Yes | ✅ Required | ✅ Yes | ✅ Granted |
| SYSTEM_ALERT_WINDOW | ⚠️ Optional | ❌ Optional | ⚠️ Review required | ✅ Granted |
| ACCESSIBILITY_SERVICE | ⚠️ Optional | ⚠️ Backup | ⚠️ Review required | ✅ Granted |

*Restricted on Play Store but allowed for private use.

---

## 🚀 Testing Maximum Reliability

### Test 1: Reboot Test
```powershell
adb reboot
# Wait 2 minutes after boot
adb logcat | findstr "BootReceiver NotificationService"
# Expected: "BootReceiver: Device booted" → "NotificationService: Listener Connected"
```

### Test 2: 24-Hour Test
1. Install app with all permissions
2. Send test notification immediately (verify arrives in sheet)
3. Wait 24 hours WITHOUT opening app
4. Send another test notification
5. Verify it arrives in sheet (proves 24/7 operation)

### Test 3: Low Memory Stress Test
```powershell
# Fill device memory
adb shell am instrument -w -e class com.android.cts.devicepolicy.DeviceOwnerTest#testLockTask

# Send test notification
# Verify service survived
```

### Test 4: Airplane Mode Test
1. Enable airplane mode
2. Send 5 test notifications (queued)
3. Disable airplane mode
4. Verify all 5 arrive in sheet (proves offline queue works)

---

## 🔐 Security Note

**Since this app is for private use (NOT Play Store):**
- ✅ Maximum permissions are acceptable
- ✅ No privacy policy required
- ✅ No data handling disclosure needed
- ✅ Can use restricted APIs (QUERY_ALL_PACKAGES, etc.)
- ✅ Can bypass battery optimization
- ✅ Can use accessibility services

**If ever publishing to Play Store:**
- Must justify each "dangerous" permission
- Must remove QUERY_ALL_PACKAGES (or justify)
- Must remove battery optimization bypass (or justify)
- Must add privacy policy
- Must handle data deletion requests

---

## 📞 Troubleshooting

**Notifications still not working after all setup?**
1. Check logs: `adb logcat | findstr "NotificationService"`
2. Verify all checkboxes above are completed
3. Restart device (some settings require reboot)
4. Factory reset notification listener:
   ```powershell
   adb shell pm clear com.android.providers.settings
   ```
   (Warning: Resets ALL system settings!)

**Service keeps dying?**
- Check for conflicting apps (other notification managers, cleaners)
- Uninstall battery saver apps (Greenify, etc.)
- Disable manufacturer's "Game Mode" or "Performance Mode"
- Check for OEM-specific task killers (Xiaomi, Samsung)

---

**This configuration ensures 100% notification capture on Android 15+ for private use.** 🔒

