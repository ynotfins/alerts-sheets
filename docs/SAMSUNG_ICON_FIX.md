# Samsung Duplicate Launcher Icon Fix

## Problem
On Samsung devices (One UI 7 / Android 15), duplicate launcher icons can appear after installing the app due to:
- Samsung Dual Messenger creating clones of apps
- Secure Folder creating isolated app instances
- Work Profile duplication
- Launcher cache not refreshing properly after app updates

## Prevention (Already Applied)
The `AndroidManifest.xml` includes Samsung-specific protections:

```xml
<!-- At application level -->
<meta-data
    android:name="com.samsung.android.dual_messenger.nondualapplicable"
    android:value="true" />
<meta-data
    android:name="com.samsung.android.app.disablesecurefolder"
    android:value="true" />
<meta-data
    android:name="com.samsung.android.multidisplay.keep_process_alive"
    android:value="false" />

<!-- MainActivity settings -->
<activity
    android:name=".ui.MainActivity"
    android:launchMode="singleInstance"
    android:taskAffinity=""
    android:resizeableActivity="false">
    ...
</activity>
```

## Manual Fix (If Duplicate Icons Still Appear)

### Method 1: Clear Samsung Launcher Data (Recommended)
```bash
# Clear Samsung One UI Launcher cache
adb shell pm clear com.sec.android.app.launcher

# Reboot device
adb reboot
```

**Note:** This will reset your home screen layout. Users will need to reorganize their home screen after this.

### Method 2: Force App Reinstall
```bash
# Uninstall completely (including data)
adb uninstall com.example.alertsheets

# Reboot
adb reboot

# Reinstall clean
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Method 3: Disable and Re-enable App
```bash
# Disable the app
adb shell pm disable-user com.example.alertsheets

# Wait 5 seconds
# Enable the app
adb shell pm enable com.example.alertsheets

# Restart launcher
adb shell am force-stop com.sec.android.app.launcher
```

## Verification
After applying fixes:
1. Check home screen - should see only ONE "AlertsToSheets" icon
2. Check app drawer - should see only ONE entry
3. Check Settings > Apps - should see only ONE instance

If duplicate persists:
- Check logcat for `PackageManager` warnings
- Verify merged manifest: `android/app/build/intermediates/merged_manifests/debug/AndroidManifest.xml`
- Ensure only ONE `MAIN/LAUNCHER` intent-filter exists
- Ensure no `<activity-alias>` with LAUNCHER category

## Technical Details
- `launchMode="singleInstance"`: Ensures only one task/activity instance
- `taskAffinity=""`: Prevents task cloning
- `resizeableActivity="false"`: Disables multi-window (prevents Samsung cloning)
- Samsung meta-data: Explicit opt-out of Dual Messenger and Secure Folder

## Related Files
- `android/app/src/main/AndroidManifest.xml`
- `android/app/build/intermediates/merged_manifests/debug/AndroidManifest.xml` (build output)

