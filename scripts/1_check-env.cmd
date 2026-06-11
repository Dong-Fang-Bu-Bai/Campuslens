@echo off
setlocal
chcp 65001 >nul

if exist "D:\Tools\apache-maven-3.9.11\bin\mvn.cmd" set "PATH=D:\Tools\apache-maven-3.9.11\bin;%PATH%"
if exist "D:\Tools\Docker\Docker\resources\bin\docker.exe" set "PATH=D:\Tools\Docker\Docker\resources\bin;%PATH%"

echo [CampusLens] Checking local environment...
echo.

call :check java "-version"
call :check mvn "-version"
call :check node "-v"
call :check npm "-v"
call :check docker "--version"
if exist "D:\Tools\Docker\Docker\Docker Desktop.exe" (
  echo [OK] Docker Desktop install path: D:\Tools\Docker\Docker
)
if exist "D:\DockerData\wsl" (
  echo [OK] Docker Desktop data path: D:\DockerData\wsl
)

where py >nul 2>nul
if errorlevel 1 (
  echo [MISSING] py is not available in PATH.
) else (
  echo [OK] py
  py -3.10 --version
  echo.
)

echo [CampusLens] Checking algorithm local files...
set "ALGORITHM_PYTHON=%CAMPUSLENS_ALGORITHM_PYTHON%"
if "%ALGORITHM_PYTHON%"=="" if exist "D:\AnaConda\envs\campuslens-gpu\python.exe" set "ALGORITHM_PYTHON=D:\AnaConda\envs\campuslens-gpu\python.exe"
if "%ALGORITHM_PYTHON%"=="" set "ALGORITHM_PYTHON=%~dp0..\algorithm\.venv\Scripts\python.exe"

if exist "%ALGORITHM_PYTHON%" (
  echo [OK] Algorithm Python: %ALGORITHM_PYTHON%
  "%ALGORITHM_PYTHON%" --version
) else (
  echo [MISSING] Algorithm Python is not available: %ALGORITHM_PYTHON%
)

if exist "%~dp0..\algorithm\models\dinov2_model.pth" (
  echo [OK] algorithm\models\dinov2_model.pth
) else (
  echo [MISSING] algorithm\models\dinov2_model.pth is not available.
)

if exist "%~dp0..\algorithm\.env" (
  echo [OK] algorithm\.env
) else (
  echo [MISSING] algorithm\.env is not available.
)

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
