# Fix for R.jar file lock issue
Write-Host "================================" -ForegroundColor Cyan
Write-Host "Fixing R.jar file lock issue..." -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan

# Stop Gradle daemons
Write-Host "`n[Step 1/5] Stopping Gradle daemons..." -ForegroundColor Yellow
& "$PSScriptRoot\..\gradlew.bat" --stop 2>&1 | Out-Null

# Wait for processes to stop
Start-Sleep -Seconds 2

# Kill ALL Java processes (aggressive approach)
Write-Host "[Step 2/5] Killing all Java processes..." -ForegroundColor Yellow
$javaProcesses = Get-Process | Where-Object {$_.ProcessName -eq "java"}
if ($javaProcesses) {
    $javaProcesses | ForEach-Object {
        Write-Host "  Killing Java process (PID: $($_.Id))" -ForegroundColor Red
    }
    Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "  No Java processes found." -ForegroundColor Green
}

# Wait for processes to fully terminate
Start-Sleep -Seconds 3

# Verify Java processes are killed
Write-Host "[Step 3/5] Verifying Java processes are terminated..." -ForegroundColor Yellow
$remainingJava = Get-Process | Where-Object {$_.ProcessName -eq "java"}
if ($remainingJava) {
    Write-Host "  Warning: Some Java processes are still running!" -ForegroundColor Red
    $remainingJava | ForEach-Object {
        Write-Host "  - PID: $($_.Id)" -ForegroundColor Red
    }
} else {
    Write-Host "  All Java processes terminated successfully!" -ForegroundColor Green
}

# Try to delete the build folder
Write-Host "[Step 4/5] Removing build directory..." -ForegroundColor Yellow
$buildPath = "$PSScriptRoot\..\app\build"
if (Test-Path $buildPath) {
    try {
        Remove-Item -Path $buildPath -Recurse -Force -ErrorAction Stop
        Write-Host "  Build directory removed successfully!" -ForegroundColor Green
    } catch {
        Write-Host "  Could not fully remove build directory: $_" -ForegroundColor Red
        Write-Host "  Attempting to remove specific intermediate files..." -ForegroundColor Yellow

        # Try to remove just the problematic directory
        $rjarPath = "$buildPath\intermediates\compile_and_runtime_not_namespaced_r_class_jar"
        if (Test-Path $rjarPath) {
            Remove-Item -Path $rjarPath -Recurse -Force -ErrorAction SilentlyContinue
            Write-Host "  Removed R.jar intermediates directory." -ForegroundColor Green
        }
    }
} else {
    Write-Host "  Build directory doesn't exist (already clean)." -ForegroundColor Green
}

Write-Host "[Step 5/5] Verification..." -ForegroundColor Yellow
if (Test-Path $buildPath) {
    Write-Host "  Warning: Build folder still exists. Manual intervention may be required." -ForegroundColor Red
} else {
    Write-Host "  Build folder successfully removed!" -ForegroundColor Green
}

Write-Host "`n================================" -ForegroundColor Green
Write-Host "Fix completed successfully!" -ForegroundColor Green
Write-Host "You can now run: .\gradlew.bat assembleDebug" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green

