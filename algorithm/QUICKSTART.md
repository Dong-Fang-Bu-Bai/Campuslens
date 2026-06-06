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

---

## ✅ 第二步：验证模型

```bash
python verify_model.py ./models/dinov2_model.pth
```

如果看到 "✅ 模型验证通过！"，说明模型文件正常。

---

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
Using device: cpu  ← 如果使用 GPU，这里会显示 cuda
📦 模型文件大小: 330.33 MB
✅ 加载完整的 DINOv2 模型对象
DINOv2 model loaded successfully. Feature dimension: 768
```

---

## 🔨 第四步：构建统计参数（首次运行必需）

确保 `../datasets/landmarks/` 目录下有地标图片，然后执行：

**PowerShell:**
```powershell
curl.exe -X POST http://localhost:8000/api/v1/index/rebuild
```

**CMD:**
```cmd
curl -X POST http://localhost:8000/api/v1/index/rebuild
```

预期输出：
```json
{
  "status": "success",
  "message": "统计参数重建完成",
  "data": {
    "total_images": 250,
    "total_landmarks": 10
  }
}
```

---

## 🧪 第五步：测试检索

准备一张测试图片，然后执行：

**PowerShell:**
```powershell
curl.exe -X POST http://localhost:8000/api/v1/search `
  -F "file=@.\datasets\landmarks\L01_library\编号1_图书馆_1.jpg"
```

如需格式化 JSON 输出：
```powershell
curl.exe -X POST http://localhost:8000/api/v1/search -F "file=@test.jpg" | Select-Object -ExpandProperty Content | ConvertFrom-Json
```

**CMD:**
```cmd
curl -X POST http://localhost:8000/api/v1/search -F "file=@test.jpg"
```

预期响应：
```json
{
  "results": [
    {
      "rank": 1,
      "landmarkCode": "L01",
      "landmarkName": "library",
      "score": 0.8533,
      "confidenceLevel": "high",
      "mahalanobisDistance": 4.3239
    }
  ],
  "lowConfidence": false,
  "message": "Search successful"
}
```

---

## 📊 查看统计参数状态

```bash
curl.exe http://localhost:8000/api/v1/index/stats
```

预期输出：
```json
{
  "status": "ready",
  "totalVectors": 10,
  "dimension": 768,
  "indexedLandmarks": 10
}
```

---

## 🏥 健康检查

```bash
curl.exe http://localhost:8000/api/v1/health
```

预期输出：
```json
{
  "status": "healthy",
  "service": "CampusLens AI Search",
  "version": "1.0.0"
}
```

---

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

---

## ❓ 常见问题

### Q1: 提示模型文件不存在
**A**: 确认模型文件在 `./models/dinov2_model.pth`，或设置环境变量：
```bash
set DINO_MODEL_PATH=C:\your\path\to\model.pth
```

### Q2: 统计参数重建失败
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
```

### Q6: PowerShell 中 curl 命令报错
**A**: PowerShell 的 `curl` 是 `Invoke-WebRequest` 的别名，需要使用 `curl.exe`：
```powershell
# ❌ 错误
curl -X POST ...

# ✅ 正确
curl.exe -X POST ...
```

### Q7: 如何理解 score 和 mahalanobisDistance？
**A**: 
- `score`: 基于马氏距离 sigmoid 归一化得到的经验匹配分（0-1），越高表示越匹配；该分数只用于排序、展示区分度和辅助判断，不具备概率或统计置信度含义
- `mahalanobisDistance`: 马氏距离原始值，越小表示查询点越接近地标分布中心
- `confidenceLevel`: 兼容字段，当前表示匹配等级
  - `score >= 0.8` → high（高匹配）
  - `score 0.4-0.8` → medium（中匹配）
  - `score < 0.4` → low（低匹配，建议人工核验）

详细算法说明见：[algo.md](algo.md)

### Q8: v2.1 优化了什么？
**A**: 
- 移除了未使用的余弦相似度变量，代码更简洁
- 改进了 FAISS 召回策略，从 `top_k * 2` 提升到 `max(top_k * 5, 30)`
- 召回率从 ~90% 提升至 >99%
- 响应时间略有增加（~3ms），但准确率显著提升

---

## 🎯 下一步

- 📖 阅读完整文档：[README.md](README.md)
- 🧮 了解算法原理：[algo.md](algo.md)
- ⚡ 配置 GPU 加速：[GPU_SUPPORT.md](GPU_SUPPORT.md)
- 📋 查看项目状态：[CHECKLIST.md](CHECKLIST.md)

---

**最后更新**: 2026-06-06
