@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
set "WSL_DISTRO=Ubuntu"
set "PID_DIR=%ROOT%\.run"
set "DB_ENV=%PID_DIR%\database-env.cmd"
if exist "D:\Tools\Docker\Docker\resources\bin\docker.exe" set "PATH=D:\Tools\Docker\Docker\resources\bin;%PATH%"
if "%CAMPUSLENS_DB_NAME%"=="" set "CAMPUSLENS_DB_NAME=campuslens"
if "%CAMPUSLENS_DB_USERNAME%"=="" set "CAMPUSLENS_DB_USERNAME=campuslens"
if "%CAMPUSLENS_DB_PASSWORD%"=="" set "CAMPUSLENS_DB_PASSWORD=campuslens123"
if "%CAMPUSLENS_DB_PORT%"=="" set "CAMPUSLENS_DB_PORT=3306"
cd /d "%ROOT%"

where docker >nul 2>nul
if not errorlevel 1 (
  call :ensure_windows_docker
  if errorlevel 1 exit /b 1
  echo [CampusLens] Starting MySQL and Redis with Windows Docker Compose...
  docker compose up -d mysql redis
  if errorlevel 1 exit /b 1
  call :wait_mysql_windows
  if errorlevel 1 exit /b 1
  call :wait_mysql_windows_query
  if errorlevel 1 exit /b 1
  call :wait_redis_windows
  if errorlevel 1 exit /b 1
  if exist "%DB_ENV%" del "%DB_ENV%" >nul 2>nul
  goto :started
)

echo [CampusLens] Docker is not available in Windows PATH. Trying WSL Docker...

where wsl.exe >nul 2>nul
if errorlevel 1 (
  echo [CampusLens] wsl.exe is not available. Install Docker Desktop, configure WSL Docker, or start MySQL manually.
  exit /b 1
)

wsl.exe -d %WSL_DISTRO% -- true >nul 2>nul
if errorlevel 1 (
  echo [CampusLens] WSL distro "%WSL_DISTRO%" is not available.
  echo [CampusLens] Install Docker Desktop, configure WSL Docker, or start MySQL manually.
  exit /b 1
)

wsl.exe -d %WSL_DISTRO% -- bash -lc "command -v docker >/dev/null 2>&1"
if errorlevel 1 (
  echo [CampusLens] Docker is not installed inside WSL distro "%WSL_DISTRO%".
  echo [CampusLens] Install Docker Engine in WSL, install Docker Desktop, or start MySQL manually.
  exit /b 1
)

wsl.exe -d %WSL_DISTRO% -- bash -lc "docker compose version >/dev/null 2>&1"
if errorlevel 1 (
  echo [CampusLens] Docker Compose is not available inside WSL distro "%WSL_DISTRO%".
  echo [CampusLens] Install the Docker Compose plugin in WSL, install Docker Desktop, or start MySQL manually.
  exit /b 1
)

wsl.exe -d %WSL_DISTRO% -- docker ps >nul 2>nul
if errorlevel 1 (
  echo [CampusLens] WSL Docker is installed, but the current WSL user cannot access the Docker daemon.
  echo [CampusLens] In Ubuntu, run: sudo usermod -aG docker $USER
  echo [CampusLens] Then in Windows PowerShell, run: wsl --shutdown
  echo [CampusLens] If the Docker service is stopped, run in Ubuntu: sudo systemctl start docker
  exit /b 1
)

echo [CampusLens] Starting MySQL and Redis with WSL Docker Compose...
wsl.exe -d %WSL_DISTRO% --cd "%ROOT%" -- docker compose up -d mysql redis
if errorlevel 1 exit /b 1
call :wait_mysql_wsl
if errorlevel 1 exit /b 1
call :wait_mysql_wsl_query
if errorlevel 1 exit /b 1
call :wait_redis_wsl
if errorlevel 1 exit /b 1

if not exist "%PID_DIR%" mkdir "%PID_DIR%"
for /f "tokens=1" %%I in ('wsl.exe -d %WSL_DISTRO% -- hostname -I') do (
  set "WSL_IP=%%I"
  goto :got_wsl_ip
)

:got_wsl_ip
if "%WSL_IP%"=="" (
  echo [CampusLens] Failed to resolve WSL IP address for backend database access.
  exit /b 1
)

call :wait_mysql_wsl_bridge
if errorlevel 1 exit /b 1
powershell -NoProfile -Command "Start-Sleep -Seconds 3" >nul

> "%DB_ENV%" echo @echo off
>> "%DB_ENV%" echo set "CAMPUSLENS_DATASOURCE_URL=jdbc:mysql://%WSL_IP%:%CAMPUSLENS_DB_PORT%/%CAMPUSLENS_DB_NAME%?allowPublicKeyRetrieval=true"
>> "%DB_ENV%" echo set "CAMPUSLENS_DATASOURCE_USERNAME=%CAMPUSLENS_DB_USERNAME%"
>> "%DB_ENV%" echo set "CAMPUSLENS_DATASOURCE_PASSWORD=%CAMPUSLENS_DB_PASSWORD%"
echo [CampusLens] WSL Docker detected. Backend database URL saved to %DB_ENV%.

:started
echo [CampusLens] MySQL startup check finished.
echo [CampusLens] Database: campuslens, user: campuslens
echo [CampusLens] Redis queue: localhost:6379
endlocal
exit /b 0

:ensure_windows_docker
docker info >nul 2>nul
if not errorlevel 1 exit /b 0
echo [CampusLens] Windows Docker daemon is not ready. Trying to start Docker Desktop...
if exist "D:\Tools\Docker\Docker\Docker Desktop.exe" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath 'D:\Tools\Docker\Docker\Docker Desktop.exe' -WindowStyle Hidden" >nul 2>nul
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath 'Docker Desktop' -WindowStyle Hidden" >nul 2>nul
)
for /l %%I in (1,1,60) do (
  docker info >nul 2>nul
  if not errorlevel 1 exit /b 0
  powershell -NoProfile -Command "Start-Sleep -Seconds 3" >nul
)
echo [CampusLens] Docker Desktop did not become ready in time.
echo [CampusLens] Open Docker Desktop manually and retry this script.
exit /b 1

:wait_mysql_windows
echo [CampusLens] Waiting for MySQL health check...
for /l %%I in (1,1,30) do (
  for /f "delims=" %%H in ('docker inspect -f "{{.State.Health.Status}}" campuslens-mysql 2^>nul') do if "%%H"=="healthy" exit /b 0
  powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul
)
echo [CampusLens] MySQL did not become healthy in time.
exit /b 1

:wait_mysql_windows_query
echo [CampusLens] Verifying MySQL accepts queries...
for /l %%I in (1,1,20) do (
  docker exec campuslens-mysql mysql -u%CAMPUSLENS_DB_USERNAME% -p%CAMPUSLENS_DB_PASSWORD% "%CAMPUSLENS_DB_NAME%" -e "SELECT 1" >nul 2>nul
  if not errorlevel 1 exit /b 0
  powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul
)
echo [CampusLens] MySQL did not accept queries in time.
exit /b 1

:wait_redis_windows
echo [CampusLens] Waiting for Redis health check...
for /l %%I in (1,1,30) do (
  for /f "delims=" %%H in ('docker inspect -f "{{.State.Health.Status}}" campuslens-redis 2^>nul') do if "%%H"=="healthy" exit /b 0
  powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul
)
echo [CampusLens] Redis did not become healthy in time.
exit /b 1

:wait_mysql_wsl
echo [CampusLens] Waiting for WSL MySQL health check...
for /l %%I in (1,1,30) do (
  for /f "delims=" %%H in ('wsl.exe -d %WSL_DISTRO% -- docker inspect -f "{{.State.Health.Status}}" campuslens-mysql 2^>nul') do if "%%H"=="healthy" exit /b 0
  powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul
)
echo [CampusLens] WSL MySQL did not become healthy in time.
exit /b 1

:wait_mysql_wsl_query
echo [CampusLens] Verifying WSL MySQL accepts queries...
for /l %%I in (1,1,20) do (
  wsl.exe -d %WSL_DISTRO% -- docker exec campuslens-mysql mysql -u%CAMPUSLENS_DB_USERNAME% -p%CAMPUSLENS_DB_PASSWORD% "%CAMPUSLENS_DB_NAME%" -e "SELECT 1" >nul 2>nul
  if not errorlevel 1 exit /b 0
  powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul
)
echo [CampusLens] WSL MySQL did not accept queries in time.
exit /b 1

:wait_redis_wsl
echo [CampusLens] Waiting for WSL Redis health check...
for /l %%I in (1,1,30) do (
  for /f "delims=" %%H in ('wsl.exe -d %WSL_DISTRO% -- docker inspect -f "{{.State.Health.Status}}" campuslens-redis 2^>nul') do if "%%H"=="healthy" exit /b 0
  powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul
)
echo [CampusLens] WSL Redis did not become healthy in time.
exit /b 1

:wait_mysql_wsl_bridge
echo [CampusLens] Waiting for Windows to reach WSL MySQL at %WSL_IP%:%CAMPUSLENS_DB_PORT%...
for /l %%I in (1,1,30) do (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$client = New-Object Net.Sockets.TcpClient; $async = $client.BeginConnect('%WSL_IP%', [int]'%CAMPUSLENS_DB_PORT%', $null, $null); if ($async.AsyncWaitHandle.WaitOne(1000)) { try { $client.EndConnect($async); $client.Close(); exit 0 } catch { $client.Close(); exit 1 } } else { $client.Close(); exit 1 }" >nul 2>nul
  if not errorlevel 1 exit /b 0
  powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul
)
echo [CampusLens] Windows could not reach WSL MySQL at %WSL_IP%:%CAMPUSLENS_DB_PORT% in time.
exit /b 1
