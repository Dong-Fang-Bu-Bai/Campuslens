@echo off
echo ========================================
echo Installing GPU versions of dependencies
echo ========================================
echo.

echo Step 1: Uninstalling CPU versions...
pip uninstall -y torch torchvision faiss-cpu

echo.
echo Step 2: Installing PyTorch with CUDA 11.8 support...
pip install torch==2.1.2+cu118 torchvision==0.16.2+cu118 --index-url https://download.pytorch.org/whl/cu118

echo.
echo Step 3: Installing FAISS GPU...
pip install faiss-gpu==1.7.4

echo.
echo ========================================
echo ✅ GPU installation complete!
echo ========================================
echo.
echo Verifying GPU availability...
python -c "import torch; print(f'CUDA available: {torch.cuda.is_available()}'); print(f'Device count: {torch.cuda.device_count()}'); import faiss; print(f'FAISS compiled with GPU: {faiss.get_num_gpus() > 0}')"
echo.
echo If CUDA is available, restart the service to use GPU acceleration.
pause
