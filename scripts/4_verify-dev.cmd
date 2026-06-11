@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
set "PID_DIR=%ROOT%\.run"
if exist "D:\Tools\apache-maven-3.9.11\bin\mvn.cmd" set "PATH=D:\Tools\apache-maven-3.9.11\bin;%PATH%"
if exist "D:\Tools\Docker\Docker\resources\bin\docker.exe" set "PATH=D:\Tools\Docker\Docker\resources\bin;%PATH%"
if "%CAMPUSLENS_BACKEND_PROFILE%"=="" set "CAMPUSLENS_BACKEND_PROFILE=mysql"
if "%CAMPUSLENS_DB_NAME%"=="" set "CAMPUSLENS_DB_NAME=campuslens"
if "%CAMPUSLENS_DB_USERNAME%"=="" set "CAMPUSLENS_DB_USERNAME=campuslens"
if "%CAMPUSLENS_DB_PASSWORD%"=="" set "CAMPUSLENS_DB_PASSWORD=campuslens123"
if "%CAMPUSLENS_DB_PORT%"=="" set "CAMPUSLENS_DB_PORT=3306"

echo [CampusLens] Verifying local development stack...
echo [CampusLens] Backend profile: %CAMPUSLENS_BACKEND_PROFILE%
echo.

call "%~dp0start-database.cmd"
if errorlevel 1 exit /b 1

call :verify_mysql
if errorlevel 1 exit /b 1

call :verify_redis
if errorlevel 1 exit /b 1

call "%~dp0start-algorithm.cmd"
if errorlevel 1 exit /b 1

call "%~dp0start-backend.cmd"
if errorlevel 1 exit /b 1

call "%~dp0start-frontend.cmd"
if errorlevel 1 exit /b 1

echo.
call :wait_http "Backend" "http://localhost:8080/api/health" 60
if errorlevel 1 exit /b 1

call :verify_backend_landmarks
if errorlevel 1 exit /b 1

call :wait_http "Algorithm" "http://localhost:8000/api/v1/health" 90
if errorlevel 1 exit /b 1

call :wait_http "Frontend" "http://localhost:5173" 60
if errorlevel 1 exit /b 1

echo.
echo [CampusLens] Verification passed.
echo [CampusLens] MySQL:    localhost:3306, database campuslens
echo [CampusLens] Redis:    localhost:6379
echo [CampusLens] Backend:  http://localhost:8080/api/health
echo [CampusLens] Frontend: http://localhost:5173
echo [CampusLens] Algorithm: http://localhost:8000/api/v1/health
echo [CampusLens] Use scripts\3_stop-dev.cmd to stop backend, frontend, and algorithm windows.
exit /b 0

:verify_mysql
echo [CampusLens] Verifying landmark seed data...
set "LANDMARK_COUNT="
where docker >nul 2>nul
if not errorlevel 1 (
  for /f "tokens=*" %%C in ('docker exec campuslens-mysql mysql -u%CAMPUSLENS_DB_USERNAME% -p%CAMPUSLENS_DB_PASSWORD% -N -B "%CAMPUSLENS_DB_NAME%" -e "SELECT COUNT(*) FROM landmark" 2^>nul') do set "LANDMARK_COUNT=%%C"
) else (
  for /f "tokens=*" %%C in ('wsl.exe -d Ubuntu -- docker exec campuslens-mysql mysql -u%CAMPUSLENS_DB_USERNAME% -p%CAMPUSLENS_DB_PASSWORD% -N -B "%CAMPUSLENS_DB_NAME%" -e "SELECT COUNT(*) FROM landmark" 2^>nul') do set "LANDMARK_COUNT=%%C"
)
if "%LANDMARK_COUNT%"=="10" (
  echo [OK] landmark table has 10 seed rows.
  exit /b 0
)
echo [CampusLens] Unexpected landmark row count: %LANDMARK_COUNT%
exit /b 1

:verify_redis
echo [CampusLens] Verifying Redis queue...
set "REDIS_PONG="
where docker >nul 2>nul
if not errorlevel 1 (
  for /f "tokens=*" %%C in ('docker exec campuslens-redis redis-cli PING 2^>nul') do set "REDIS_PONG=%%C"
) else (
  for /f "tokens=*" %%C in ('wsl.exe -d Ubuntu -- docker exec campuslens-redis redis-cli PING 2^>nul') do set "REDIS_PONG=%%C"
)
if /I "%REDIS_PONG%"=="PONG" (
  echo [OK] Redis queue responded PONG.
  exit /b 0
)
echo [CampusLens] Redis queue verification failed: %REDIS_PONG%
exit /b 1

:wait_http
set "SERVICE_NAME=%~1"
set "SERVICE_URL=%~2"
set "MAX_TRIES=%~3"
echo [CampusLens] Waiting for %SERVICE_NAME%: %SERVICE_URL%
for /l %%I in (1,1,%MAX_TRIES%) do (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -UseBasicParsing -Uri '%SERVICE_URL%' -TimeoutSec 3; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { exit 0 } } catch { }; exit 1" >nul 2>nul
  if not errorlevel 1 (
    echo [OK] %SERVICE_NAME% responded.
    exit /b 0
  )
  powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul
)
echo [CampusLens] %SERVICE_NAME% did not respond in time: %SERVICE_URL%
exit /b 1

:verify_backend_landmarks
echo [CampusLens] Verifying backend can read MySQL landmark data...
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $data = Invoke-RestMethod -Uri 'http://localhost:8080/api/landmarks' -TimeoutSec 5; if ($data.Count -ge 10 -and $data[0].code -eq 'L01') { exit 0 } } catch { }; exit 1" >nul 2>nul
if not errorlevel 1 (
  echo [OK] backend returned L01-L10 landmark data.
  exit /b 0
)
echo [CampusLens] Backend did not return the expected landmark data.
exit /b 1
