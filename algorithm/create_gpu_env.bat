@echo off
setlocal
chcp 65001 >nul

set "CONDA_EXE=D:\AnaConda\Scripts\conda.exe"
set "ENV_DIR=D:\AnaConda\envs\campuslens-gpu"
set "CONDA_PKGS_DIRS=D:\Tools\conda-pkgs"
set "PIP_CACHE_DIR=D:\Tools\pip-cache"
set "TMP=D:\tmp"
set "TEMP=D:\tmp"

if not exist "%CONDA_EXE%" (
  echo Conda not found: %CONDA_EXE%
  exit /b 1
)

if not exist "D:\AnaConda\envs" mkdir "D:\AnaConda\envs"
if not exist "%CONDA_PKGS_DIRS%" mkdir "%CONDA_PKGS_DIRS%"
if not exist "%PIP_CACHE_DIR%" mkdir "%PIP_CACHE_DIR%"
if not exist "%TMP%" mkdir "%TMP%"

if not exist "%ENV_DIR%\python.exe" (
  "%CONDA_EXE%" create -p "%ENV_DIR%" python=3.10 pip -y
  if errorlevel 1 exit /b 1
)

set "CAMPUSLENS_ALGORITHM_PYTHON=%ENV_DIR%\python.exe"
call "%~dp0install_gpu.bat"
endlocal
