@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0.."
if "%CAMPUSLENS_BACKEND_PROFILE%"=="" set "CAMPUSLENS_BACKEND_PROFILE=mysql"

if /I "%CAMPUSLENS_BACKEND_PROFILE%"=="mysql" (
  echo [CampusLens] Using default backend profile "mysql"; backend startup will ensure MySQL is ready.
) else (
  echo [CampusLens] Using explicit backend profile "%CAMPUSLENS_BACKEND_PROFILE%"; skipping MySQL startup.
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
