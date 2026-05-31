@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
set "PID_DIR=%ROOT%\.run"
set "PID_FILE=%PID_DIR%\algorithm.pid"

if not exist "%PID_FILE%" (
  echo [CampusLens] Algorithm PID file not found.
  echo [CampusLens] If the service was started manually, close its command window or stop that process manually.
  pause
  exit /b 0
)

set /p PID=<"%PID_FILE%"
if "%PID%"=="" (
  echo [CampusLens] Algorithm PID file is empty.
  del "%PID_FILE%" >nul 2>nul
  del "%PID_DIR%\run-algorithm.cmd" >nul 2>nul
  pause
  exit /b 0
)

tasklist /FI "PID eq %PID%" | findstr /R /C:" %PID% " >nul 2>nul
if errorlevel 1 (
  echo [CampusLens] Algorithm launcher PID %PID% is not running.
) else (
  taskkill /PID %PID% /T /F >nul 2>nul
  echo [CampusLens] Stopped algorithm launcher process tree PID %PID%.
)

del "%PID_FILE%" >nul 2>nul
del "%PID_DIR%\run-algorithm.cmd" >nul 2>nul
pause
endlocal
