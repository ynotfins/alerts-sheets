# Build Scripts

> **🚨 Quick Fix:** If you're getting the `R.jar` lock error right now, just run `.\scripts\fix-rjar-lock.bat` or follow the [Quick Fix (Manual)](#quick-fix-manual) steps below.

## fix-rjar-lock.ps1

### Purpose
Fixes the common Gradle build error on Windows where `R.jar` file is locked by another process:
```
java.io.IOException: Couldn't delete D:\github\alerts-sheets\android\app\build\intermediates\compile_and_runtime_not_namespaced_r_class_jar\debug\R.jar
```

### Usage
```powershell
# From PowerShell
.\scripts\fix-rjar-lock.ps1

# Or with execution policy bypass
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\fix-rjar-lock.ps1

# Or simply double-click the batch file
.\scripts\fix-rjar-lock.bat
```

### What it does
1. Stops all Gradle daemon processes
2. Kills all Java processes (to release file locks)
3. Removes the build directory
4. Verifies the cleanup was successful

### After running the script
You can build your project normally:
```bash
.\gradlew.bat assembleDebug
```

> **⚠️ Important:** Avoid using `.\gradlew.bat clean` or `clean assembleDebug` as the clean task itself can trigger the R.jar lock issue. Instead, use the fix script to manually clean, then run `assembleDebug` directly.

### Common causes of this issue
- Gradle daemon processes holding file locks
- Antivirus software scanning build files
- File indexing services
- Running build multiple times in parallel

### Prevention tips
- Always stop Gradle daemon before cleaning: `.\gradlew.bat --stop`
- Add build directories to antivirus exclusions
- Avoid running multiple builds simultaneously

---

## Quick Fix (Manual)

If you need to fix this manually without the script:

```powershell
# 1. Stop Gradle daemons
.\gradlew.bat --stop

# 2. Kill all Java processes
Stop-Process -Name "java" -Force

# 3. Wait a few seconds
Start-Sleep -Seconds 3

# 4. Delete build folder
Remove-Item ".\app\build" -Recurse -Force

# 5. Rebuild
.\gradlew.bat assembleDebug
```

### Troubleshooting

**Issue: Script fails to delete build folder**
- Close Android Studio or any IDE that might have the project open
- Check if antivirus is scanning the build folder
- Restart your computer if the issue persists

**Issue: Build still fails after running script**
- Ensure no background processes are holding file locks
- Try running the script again
- Check Windows Task Manager for lingering Java processes and kill them manually

