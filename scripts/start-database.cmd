@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
set "WSL_DISTRO=Ubuntu"
cd /d "%ROOT%"

where docker >nul 2>nul
if not errorlevel 1 (
  echo [CampusLens] Starting MySQL database with Windows Docker Compose...
  docker compose up -d mysql
  if errorlevel 1 exit /b 1
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

echo [CampusLens] Starting MySQL database with WSL Docker Compose...
wsl.exe -d %WSL_DISTRO% --cd "%ROOT%" -- docker compose up -d mysql
if errorlevel 1 exit /b 1

:started
echo [CampusLens] MySQL is starting on localhost:3306.
echo [CampusLens] Database: campuslens, user: campuslens
endlocal
