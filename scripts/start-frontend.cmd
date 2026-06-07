@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
set "FRONTEND_DIR=%ROOT%\frontend"
set "PID_DIR=%ROOT%\.run"
set "PID_FILE=%PID_DIR%\frontend.pid"
set "RUNNER=%PID_DIR%\run-frontend.cmd"

if not exist "%FRONTEND_DIR%\package.json" (
  echo [CampusLens] frontend\package.json not found.
  pause
  exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
  echo [CampusLens] npm is not available in PATH. Run scripts\check-env.cmd first.
  pause
  exit /b 1
)

if not exist "%PID_DIR%" mkdir "%PID_DIR%"

call :http_ok "http://localhost:5173"
if not errorlevel 1 (
  echo [CampusLens] Frontend already responds at http://localhost:5173
  endlocal
  exit /b 0
)

if exist "%PID_FILE%" (
  set /p OLD_PID=<"%PID_FILE%"
  if not "%OLD_PID%"=="" (
    tasklist /FI "PID eq %OLD_PID%" | findstr /R /C:" %OLD_PID% " >nul 2>nul
    if not errorlevel 1 (
      echo [CampusLens] Frontend already has a launcher PID: %OLD_PID%
      echo [CampusLens] Page: http://localhost:5173
      endlocal
      exit /b 0
    )
  )
  del "%PID_FILE%" >nul 2>nul
)

if not exist "%FRONTEND_DIR%\node_modules" (
  echo [CampusLens] node_modules not found. Installing frontend dependencies...
  pushd "%FRONTEND_DIR%"
  call npm install --registry=https://registry.npmmirror.com
  if errorlevel 1 (
    popd
    echo [CampusLens] npm install failed.
    pause
    exit /b 1
  )
  popd
)

echo [CampusLens] Starting frontend on http://localhost:5173 ...

> "%RUNNER%" echo @echo off
>> "%RUNNER%" echo chcp 65001 ^>nul
>> "%RUNNER%" echo title CampusLens Frontend
>> "%RUNNER%" echo cd /d "%FRONTEND_DIR%"
>> "%RUNNER%" echo npm run dev

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = Start-Process -FilePath 'cmd.exe' -ArgumentList '/k','""%RUNNER%""' -PassThru; Set-Content -Path '%PID_FILE%' -Value $p.Id -Encoding ascii"

echo [CampusLens] Frontend launcher PID saved to %PID_FILE%.
echo [CampusLens] Keep the new frontend window open while developing.
endlocal
exit /b 0

:http_ok
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -UseBasicParsing -Uri '%~1' -TimeoutSec 2; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { exit 0 } } catch { }; exit 1" >nul 2>nul
exit /b %errorlevel%
