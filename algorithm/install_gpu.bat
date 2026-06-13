@echo off
echo ========================================
echo Installing GPU versions of dependencies
echo ========================================
echo.

set "PYTHON=%CAMPUSLENS_ALGORITHM_PYTHON%"
if "%PYTHON%"=="" set "PYTHON=D:\AnaConda\envs\campuslens-gpu\python.exe"
if not exist "%PYTHON%" (
  echo Python environment not found: %PYTHON%
  exit /b 1
)

echo.
echo Installing GPU requirements. FAISS remains on CPU on Windows.
"%PYTHON%" -m pip install -r requirements-gpu.txt -r requirements-test.txt

echo.
echo ========================================
echo [OK] GPU installation complete!
echo ========================================
echo.
echo Verifying GPU availability...
"%PYTHON%" -c "import torch, faiss; print(f'CUDA available: {torch.cuda.is_available()}'); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU fallback'); print('FAISS CPU index ready')"
echo.
echo If CUDA is available, restart the service to use GPU acceleration.
pause
