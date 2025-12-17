@echo off
REM Quick fix for R.jar file lock issue
echo.
echo =====================================
echo Running R.jar Lock Fix...
echo =====================================
echo.

powershell.exe -ExecutionPolicy Bypass -File "%~dp0fix-rjar-lock.ps1"

echo.
pause

