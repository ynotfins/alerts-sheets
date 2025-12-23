#!/usr/bin/env pwsh
# FIX DUPLICATE APP ICONS ON SAMSUNG (One UI 7 / Android 15)
#
# PROBLEM: Two app icons appear after install
# CAUSE: Samsung launcher cache corruption
# SOLUTION: Clear launcher data + reboot
#
# RUN THIS SCRIPT ONCE AFTER INSTALLING THE APP

Write-Host "=======================" -ForegroundColor Cyan
Write-Host "DUPLICATE APP ICON FIX" -ForegroundColor Cyan
Write-Host "=======================" -ForegroundColor Cyan
Write-Host ""

# Check if ADB is available
if (!(Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: ADB not found in PATH" -ForegroundColor Red
    Write-Host "Please install Android SDK Platform Tools" -ForegroundColor Yellow
    exit 1
}

# Check if device is connected
$devices = adb devices | Select-String "device$" | Measure-Object
if ($devices.Count -eq 0) {
    Write-Host "ERROR: No Android device connected" -ForegroundColor Red
    Write-Host "Please connect your device via USB" -ForegroundColor Yellow
    exit 1
}

Write-Host "Device connected ✓" -ForegroundColor Green
Write-Host ""

# Step 1: Uninstall app completely
Write-Host "[1/5] Uninstalling AlertsToSheets..." -ForegroundColor Yellow
adb uninstall com.example.alertsheets 2>&1 | Out-Null
Start-Sleep -Seconds 2
Write-Host "      App uninstalled ✓" -ForegroundColor Green

# Step 2: Clear Samsung launcher cache
Write-Host "[2/5] Clearing Samsung launcher cache..." -ForegroundColor Yellow
adb shell pm clear com.sec.android.app.launcher 2>&1 | Out-Null
Start-Sleep -Seconds 2
Write-Host "      Launcher cache cleared ✓" -ForegroundColor Green

# Step 3: Clear One UI Home cache (if exists)
Write-Host "[3/5] Clearing One UI Home cache..." -ForegroundColor Yellow
adb shell pm clear com.samsung.android.app.launcher 2>&1 | Out-Null
Start-Sleep -Seconds 1
Write-Host "      One UI cache cleared ✓" -ForegroundColor Green

# Step 4: Install fresh APK
Write-Host "[4/5] Installing fresh APK..." -ForegroundColor Yellow
$apkPath = ".\android\app\build\outputs\apk\debug\app-debug.apk"
if (!(Test-Path $apkPath)) {
    Write-Host "      ERROR: APK not found at $apkPath" -ForegroundColor Red
    Write-Host "      Please build the APK first: .\gradlew assembleDebug" -ForegroundColor Yellow
    exit 1
}
adb install -r $apkPath 2>&1 | Out-Null
Start-Sleep -Seconds 2
Write-Host "      App installed ✓" -ForegroundColor Green

# Step 5: Reboot device
Write-Host "[5/5] Rebooting device..." -ForegroundColor Yellow
Write-Host ""
Write-Host "      Your phone will reboot now." -ForegroundColor Cyan
Write-Host "      After reboot, check the app drawer." -ForegroundColor Cyan
Write-Host "      There should be ONLY ONE 'Alerts to Sheets' icon." -ForegroundColor Cyan
Write-Host ""
Start-Sleep -Seconds 3
adb reboot

Write-Host ""
Write-Host "=======================" -ForegroundColor Green
Write-Host "CLEANUP COMPLETE!" -ForegroundColor Green
Write-Host "=======================" -ForegroundColor Green
Write-Host ""
Write-Host "Device is rebooting..." -ForegroundColor Yellow
Write-Host "After reboot, verify:" -ForegroundColor Cyan
Write-Host "  • Only ONE app icon in drawer" -ForegroundColor White
Write-Host "  • Contact picker permission works" -ForegroundColor White
Write-Host ""

