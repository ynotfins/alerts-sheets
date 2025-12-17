@echo off
echo Waiting for device...
adb wait-for-device
echo Device found! Installing update...
cd android
call gradlew installDebug
echo.
echo ==========================================================
echo   UPDATE COMPLETE!
echo   1. check App Title says "(v2 DEBUG)"
echo   2. Click "Test Payload Now"
echo   3. Verify Google Sheet Row 22 style behavior.
echo ==========================================================
pause
