# CampusLens AI 服务快速启动指南

## 📦 第一步：安装依赖

### CPU 模式（默认）

```bash
cd algorithm
pip install -r requirements.txt
```

### GPU 模式（可选，需要 NVIDIA GPU）

**Windows:**
```bash
install_gpu.bat
```

**Linux/Mac:**
```bash
pip uninstall -y torch torchvision faiss-cpu
pip install torch==2.1.2+cu118 torchvision==0.16.2+cu118 --index-url https://download.pytorch.org/whl/cu118
pip install faiss-gpu==1.7.4
```

验证 GPU 是否可用：
```bash
python -c "import torch; print(f'CUDA available: {torch.cuda.is_available()}')"
```

## ✅ 第二步：验证模型

```bash
python verify_model.py ./models/dinov2_model.pth
```

如果看到 "✅ 模型验证通过！"，说明模型文件正常。

## 🚀 第三步：启动服务

```bash
python app/main.py
```

启动成功后会看到：
```
============================================================
初始化 DINOv2 特征提取器（离线模式）
============================================================
Loading DINOv2 model from: .\models\dinov2_model.pth
Using device: cuda  ← 如果使用 GPU，这里会显示 cuda
📦 模型文件大小: 330.33 MB
✅ 加载完整的 DINOv2 模型对象
DINOv2 model loaded successfully. Feature dimension: 768
```

## 🔨 第四步：构建索引（首次运行必需）

确保 `../datasets/landmarks/` 目录下有地标图片，然后执行：

```bash
curl -X POST http://localhost:8000/api/v1/index/rebuild
```

或者使用 PowerShell：
```powershell
Invoke-RestMethod -Uri http://localhost:8000/api/v1/index/rebuild -Method Post
```

## 🧪 第五步：测试检索

准备一张测试图片 `test.jpg`，然后执行：

```bash
curl -X POST http://localhost:8000/api/v1/search -F "file=@test.jpg"
```

或者使用 PowerShell：
```powershell
$form = @{ file = Get-Item "test.jpg" }
Invoke-RestMethod -Uri http://localhost:8000/api/v1/search -Method Post -Form $form
```

## 📊 查看索引状态

```bash
curl http://localhost:8000/api/v1/index/stats
```

## 🏥 健康检查

```bash
curl http://localhost:8000/api/v1/health
```

## 🐳 使用 Docker（可选）

```bash
docker-compose up -d
```

查看日志：
```bash
docker-compose logs -f
```

停止服务：
```bash
docker-compose down
```

## ❓ 常见问题

### Q1: 提示模型文件不存在
**A**: 确认模型文件在 `./models/dinov2_model.pth`，或设置环境变量：
```bash
set DINO_MODEL_PATH=C:\your\path\to\model.pth
```

### Q2: 索引重建失败
**A**: 检查 `../datasets/landmarks/` 目录是否有图片文件，每个地标文件夹至少要有几张图片。

### Q3: 端口被占用
**A**: 修改 `.env` 文件中的 `PORT` 配置，或停止占用 8000 端口的程序。

### Q4: 内存不足
**A**: 减小 `BATCH_SIZE` 环境变量，或强制使用 CPU：
```bash
# 在 .env 文件中设置
DEVICE=cpu
BATCH_SIZE=16
```

### Q5: GPU 未启用
**A**: 检查是否有 NVIDIA GPU 并安装 GPU 版本依赖：
```bash
nvidia-smi  # 检查 GPU 状态
python -c "import torch; print(torch.cuda.is_available())"  # 验证 CUDA
# 如果返回 False，运行 install_gpu.bat (Windows) 或见 README GPU 章节

## 📞 需要帮助？

查看详细文档：[README.md](README.md)
