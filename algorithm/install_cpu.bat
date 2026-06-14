@echo off
echo ========================================
echo Switching to CPU versions
echo ========================================
echo.

set "PYTHON=%CAMPUSLENS_ALGORITHM_PYTHON%"
if "%PYTHON%"=="" if exist "D:\AnaConda\envs\campuslens-gpu\python.exe" set "PYTHON=D:\AnaConda\envs\campuslens-gpu\python.exe"
if "%PYTHON%"=="" set "PYTHON=.venv\Scripts\python.exe"

echo Step 1: Removing current PyTorch packages...
"%PYTHON%" -m pip uninstall -y torch torchvision

echo.
echo Step 2: Installing CPU versions...
"%PYTHON%" -m pip install -r requirements-cpu.txt -r requirements-test.txt

echo.
echo ========================================
echo [OK] CPU installation complete!
echo ========================================
echo.
echo Verifying...
"%PYTHON%" -c "import torch, faiss; print(f'CUDA available: {torch.cuda.is_available()}'); print('FAISS CPU version installed')"
echo.
echo Restart the service to use CPU mode.
pause
