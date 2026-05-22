@echo off
echo ========================================
echo Switching to CPU versions
echo ========================================
echo.

echo Step 1: Uninstalling GPU versions...
pip uninstall -y torch torchvision faiss-gpu

echo.
echo Step 2: Installing CPU versions...
pip install torch==2.1.2 torchvision==0.16.2 faiss-cpu==1.7.4

echo.
echo ========================================
echo ✅ CPU installation complete!
echo ========================================
echo.
echo Verifying...
python -c "import torch; print(f'CUDA available: {torch.cuda.is_available()}'); import faiss; print('FAISS CPU version installed')"
echo.
echo Restart the service to use CPU mode.
pause
