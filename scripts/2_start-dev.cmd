@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."

call "%~dp0start-database.cmd"
if errorlevel 1 (
  echo.
  echo [CampusLens] Database startup failed. Install Docker Desktop, configure WSL Docker, or start MySQL manually before launching backend.
  pause
  exit /b 1
)
echo.
call "%~dp0start-backend.cmd"
echo.
call "%~dp0start-frontend.cmd"

echo.
echo [CampusLens] Development services are launching.
echo [CampusLens] Backend:  http://localhost:8080/api/health
echo [CampusLens] Frontend: http://localhost:5173
echo.
echo [CampusLens] Use scripts\stop-dev.cmd to stop windows started by these scripts.
pause
endlocal
