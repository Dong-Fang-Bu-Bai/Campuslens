@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
set "PID_DIR=%ROOT%\.run"

echo [CampusLens] Stopping services started by CampusLens scripts...
call :stop_one backend
call :stop_one frontend
call :stop_one algorithm

echo.
echo [CampusLens] Stop command finished.
echo [CampusLens] If port 8080, 5173, or 8000 is still occupied, check whether a service was started manually.
pause
exit /b 0

:stop_one
set "NAME=%~1"
set "PID_FILE=%PID_DIR%\%NAME%.pid"
if not exist "%PID_FILE%" (
  echo [SKIP] %NAME% PID file not found.
  exit /b 0
)

set /p PID=<"%PID_FILE%"
if "%PID%"=="" (
  echo [SKIP] %NAME% PID file is empty.
  del "%PID_FILE%" >nul 2>nul
  exit /b 0
)

tasklist /FI "PID eq %PID%" | findstr /R /C:" %PID% " >nul 2>nul
if errorlevel 1 (
  echo [SKIP] %NAME% launcher PID %PID% is not running.
) else (
  taskkill /PID %PID% /T /F >nul 2>nul
  echo [STOPPED] %NAME% launcher process tree PID %PID%
)

del "%PID_FILE%" >nul 2>nul
del "%PID_DIR%\run-%NAME%.cmd" >nul 2>nul
exit /b 0
