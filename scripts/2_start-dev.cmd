@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
if "%CAMPUSLENS_BACKEND_PROFILE%"=="" set "CAMPUSLENS_BACKEND_PROFILE=demo"

if /I "%CAMPUSLENS_BACKEND_PROFILE%"=="mysql" (
  call "%~dp0start-database.cmd"
  if errorlevel 1 (
    echo.
    echo [CampusLens] Database startup failed. Install Docker Desktop, configure WSL Docker, or start MySQL manually before launching backend.
    pause
    exit /b 1
  )
) else (
  echo [CampusLens] Using backend profile "%CAMPUSLENS_BACKEND_PROFILE%"; skipping MySQL startup.
)
echo.
call "%~dp0start-backend.cmd"
echo.
call "%~dp0start-frontend.cmd"
echo.
call "%~dp0start-algorithm.cmd"

echo.
echo [CampusLens] Development services are launching.
echo [CampusLens] Backend profile: %CAMPUSLENS_BACKEND_PROFILE%
echo [CampusLens] Backend:  http://localhost:8080/api/health
echo [CampusLens] Frontend: http://localhost:5173
echo [CampusLens] Algorithm: http://localhost:8000/api/v1/health
echo.
echo [CampusLens] Use scripts\3_stop-dev.cmd to stop windows started by these scripts.
pause
endlocal
