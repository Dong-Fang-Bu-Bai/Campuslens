@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
set "BACKEND_DIR=%ROOT%\backend"
set "PID_DIR=%ROOT%\.run"
set "PID_FILE=%PID_DIR%\backend.pid"
set "RUNNER=%PID_DIR%\run-backend.cmd"

if exist "D:\Tools\apache-maven-3.9.11\bin\mvn.cmd" set "PATH=D:\Tools\apache-maven-3.9.11\bin;%PATH%"

if not exist "%BACKEND_DIR%\pom.xml" (
  echo [CampusLens] backend\pom.xml not found.
  pause
  exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
  echo [CampusLens] Maven is not available in PATH. Run scripts\check-env.cmd first.
  pause
  exit /b 1
)

if not exist "%PID_DIR%" mkdir "%PID_DIR%"
echo [CampusLens] Starting backend on http://localhost:8080 ...

> "%RUNNER%" echo @echo off
>> "%RUNNER%" echo chcp 65001 ^>nul
>> "%RUNNER%" echo title CampusLens Backend
>> "%RUNNER%" echo if exist "D:\Tools\apache-maven-3.9.11\bin\mvn.cmd" set "PATH=D:\Tools\apache-maven-3.9.11\bin;%%PATH%%"
>> "%RUNNER%" echo cd /d "%BACKEND_DIR%"
>> "%RUNNER%" echo mvn spring-boot:run

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = Start-Process -FilePath 'cmd.exe' -ArgumentList '/k','""%RUNNER%""' -PassThru; Set-Content -Path '%PID_FILE%' -Value $p.Id -Encoding ascii"

echo [CampusLens] Backend launcher PID saved to %PID_FILE%.
echo [CampusLens] Keep the new backend window open while developing.
endlocal
