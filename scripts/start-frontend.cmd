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
