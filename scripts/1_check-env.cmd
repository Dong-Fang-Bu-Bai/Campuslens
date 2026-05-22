@echo off
setlocal
chcp 65001 >nul

if exist "D:\Tools\apache-maven-3.9.11\bin\mvn.cmd" set "PATH=D:\Tools\apache-maven-3.9.11\bin;%PATH%"

echo [CampusLens] Checking local environment...
echo.

call :check java "-version"
call :check mvn "-version"
call :check node "-v"
call :check npm "-v"

echo.
echo [CampusLens] Environment check finished.
pause
exit /b 0

:check
set "CMD_NAME=%~1"
set "CMD_ARGS=%~2"
where %CMD_NAME% >nul 2>nul
if errorlevel 1 (
  echo [MISSING] %CMD_NAME% is not available in PATH.
) else (
  echo [OK] %CMD_NAME%
  %CMD_NAME% %CMD_ARGS%
  echo.
)
exit /b 0
