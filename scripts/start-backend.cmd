@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
set "BACKEND_DIR=%ROOT%\backend"
set "PID_DIR=%ROOT%\.run"
set "PID_FILE=%PID_DIR%\backend.pid"
set "RUNNER=%PID_DIR%\run-backend.cmd"
set "DB_ENV=%PID_DIR%\database-env.cmd"
if "%CAMPUSLENS_BACKEND_PROFILE%"=="" set "CAMPUSLENS_BACKEND_PROFILE=mysql"

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

if /I "%CAMPUSLENS_BACKEND_PROFILE%"=="mysql" (
  echo [CampusLens] Ensuring MySQL database is ready for backend profile "mysql" ...
  call "%~dp0start-database.cmd"
  if errorlevel 1 (
    echo [CampusLens] MySQL is not ready. Backend startup aborted.
    pause
    exit /b 1
  )
)

call :http_ok "http://localhost:8080/api/health"
if not errorlevel 1 (
  echo [CampusLens] Backend already responds at http://localhost:8080/api/health
  endlocal
  exit /b 0
)

if exist "%PID_FILE%" (
  set /p OLD_PID=<"%PID_FILE%"
  if not "%OLD_PID%"=="" (
    tasklist /FI "PID eq %OLD_PID%" | findstr /R /C:" %OLD_PID% " >nul 2>nul
    if not errorlevel 1 (
      echo [CampusLens] Backend already has a launcher PID: %OLD_PID%
      echo [CampusLens] Health check: http://localhost:8080/api/health
      endlocal
      exit /b 0
    )
  )
  del "%PID_FILE%" >nul 2>nul
)

echo [CampusLens] Starting backend on http://localhost:8080 ...

> "%RUNNER%" echo @echo off
>> "%RUNNER%" echo chcp 65001 ^>nul
>> "%RUNNER%" echo title CampusLens Backend
>> "%RUNNER%" echo if exist "D:\Tools\apache-maven-3.9.11\bin\mvn.cmd" set "PATH=D:\Tools\apache-maven-3.9.11\bin;%%PATH%%"
>> "%RUNNER%" echo set "CAMPUSLENS_BACKEND_PROFILE=%CAMPUSLENS_BACKEND_PROFILE%"
>> "%RUNNER%" echo if /I "%%CAMPUSLENS_BACKEND_PROFILE%%"=="mysql" if exist "%DB_ENV%" call "%DB_ENV%"
>> "%RUNNER%" echo cd /d "%BACKEND_DIR%"
>> "%RUNNER%" echo mvn spring-boot:run "-Dspring-boot.run.profiles=%%CAMPUSLENS_BACKEND_PROFILE%%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = Start-Process -FilePath 'cmd.exe' -ArgumentList '/k','""%RUNNER%""' -PassThru; Set-Content -Path '%PID_FILE%' -Value $p.Id -Encoding ascii"

echo [CampusLens] Backend launcher PID saved to %PID_FILE%.
echo [CampusLens] Keep the new backend window open while developing.
endlocal
exit /b 0

:http_ok
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -UseBasicParsing -Uri '%~1' -TimeoutSec 2; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { exit 0 } } catch { }; exit 1" >nul 2>nul
exit /b %errorlevel%
