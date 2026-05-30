@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
set "ALGORITHM_DIR=%ROOT%\algorithm"
set "PID_DIR=%ROOT%\.run"
set "PID_FILE=%PID_DIR%\algorithm.pid"
set "RUNNER=%PID_DIR%\run-algorithm.cmd"

if not exist "%ALGORITHM_DIR%\app\main.py" (
  echo [CampusLens] algorithm\app\main.py not found.
  pause
  exit /b 1
)

if not exist "%ALGORITHM_DIR%\.venv\Scripts\python.exe" (
  echo [CampusLens] Algorithm virtual environment not found: algorithm\.venv
  echo [CampusLens] Run the model setup first, then retry.
  pause
  exit /b 1
)

if not exist "%ALGORITHM_DIR%\.env" (
  echo [CampusLens] Algorithm .env not found: algorithm\.env
  echo [CampusLens] Create it from algorithm\.env.example and set DEVICE=cpu.
  pause
  exit /b 1
)

if not exist "%ALGORITHM_DIR%\models\dinov2_model.pth" (
  echo [CampusLens] DINOv2 model not found: algorithm\models\dinov2_model.pth
  echo [CampusLens] Download the model before starting the algorithm service.
  pause
  exit /b 1
)

if not exist "%PID_DIR%" mkdir "%PID_DIR%"

if exist "%PID_FILE%" (
  set /p OLD_PID=<"%PID_FILE%"
  if not "%OLD_PID%"=="" (
    tasklist /FI "PID eq %OLD_PID%" | findstr /R /C:" %OLD_PID% " >nul 2>nul
    if not errorlevel 1 (
      echo [CampusLens] Algorithm service already has a launcher PID: %OLD_PID%
      echo [CampusLens] Health check: http://localhost:8000/api/v1/health
      endlocal
      exit /b 0
    )
  )
  del "%PID_FILE%" >nul 2>nul
)

echo [CampusLens] Starting algorithm service on http://localhost:8000 ...

> "%RUNNER%" echo @echo off
>> "%RUNNER%" echo chcp 65001 ^>nul
>> "%RUNNER%" echo title CampusLens Algorithm
>> "%RUNNER%" echo set PYTHONUTF8=1
>> "%RUNNER%" echo cd /d "%ALGORITHM_DIR%"
>> "%RUNNER%" echo .\.venv\Scripts\python.exe app\main.py

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = Start-Process -FilePath 'cmd.exe' -ArgumentList '/k','""%RUNNER%""' -PassThru; Set-Content -Path '%PID_FILE%' -Value $p.Id -Encoding ascii"

echo [CampusLens] Algorithm launcher PID saved to %PID_FILE%.
echo [CampusLens] Keep the new algorithm window open while developing.
endlocal
