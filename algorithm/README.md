# CampusLens AI 图像检索服务

基于 DINOv2 特征提取 + 马氏距离统计检索的校园地标智能检索微服务（**纯离线模式**）。

## ⚠️ 重要说明

**本服务仅支持离线模式，必须预先准备 DINOv2 模型文件。**

## 🎯 核心特性

- ✅ **纯离线运行**: 无需网络连接，所有资源本地加载
- ✅ **地标级别检索**: 返回 Top-5 地标类别而非单张图片
- ✅ **自适应评分**: 基于马氏距离和协方差分析的统计学方法
- ✅ **科学置信度**: 利用卡方分布提供有概率意义的置信度评估
- ✅ **CPU/GPU 双模式**: 自动检测硬件，GPU 加速提升 6-7 倍性能

---

## 📋 前置要求

### 1. 模型文件（必需）

- **模型名称**: DINOv2 ViT-B/14
- **文件格式**: `.pth` (PyTorch)
- **文件大小**: 约 330MB
- **存放位置**: `./models/dinov2_model.pth`

如未准备模型，可从以下途径获取：
- [Facebook DINOv2 官方仓库](https://github.com/facebookresearch/dinov2)
- [HuggingFace Model Hub](https://huggingface.co/facebook/dinov2-base)

### 2. 地标图片数据集（必需）

按规范放置在 `../datasets/landmarks/` 目录：
```
datasets/landmarks/
├── L01_library/
│   ├── L01_front_day_001.jpg
│   ├── L01_side_cloudy_002.jpg
│   └── ... (至少 20 张)
├── L02_academic_auditorium/
└── ...
```

每个地标至少 20 张图片，覆盖不同角度和光照条件。

---

## 🚀 快速开始

### 方式一：本地运行

#### Step 1: 安装依赖
```bash
cd algorithm
pip install -r requirements.txt
```

**注意**：默认安装 CPU 版本。如需 GPU 加速，见下方「GPU 加速」章节。

#### Step 2: 验证模型文件
```bash
python verify_model.py ./models/dinov2_model.pth
```

预期输出：
```
============================================================
DINOv2 离线模型验证工具
============================================================
✅ 模型文件存在: ./models/dinov2_model.pth
📦 文件大小: 330.33 MB
...
✅ 模型验证通过！
```

#### Step 3: 配置环境变量（可选）
```bash
cp .env.example .env
```

编辑 `.env` 文件可配置：
- `DEVICE`: 计算设备 (`auto`/`cpu`/`cuda`)，默认 `auto` 自动检测
- `IMAGE_SIZE`: 输入图片尺寸，默认 `518`
- `BATCH_SIZE`: 批处理大小，默认 `32`

#### Step 4: 启动服务
```bash
python app/main.py
```

首次启动会看到：
```
============================================================
初始化 DINOv2 特征提取器（离线模式）
============================================================
Loading DINOv2 model from: ./models/dinov2_model.pth
Using device: cpu (或 cuda 如果有 GPU)
📦 模型文件大小: 330.33 MB
✅ 加载完整的 DINOv2 模型对象
DINOv2 model loaded successfully. Feature dimension: 768
```

#### Step 5: 构建统计参数
```bash
curl.exe -X POST http://localhost:8000/api/v1/index/rebuild
```

#### Step 6: 测试检索
```bash
curl.exe -X POST http://localhost:8000/api/v1/search `
  -F "file=@.\datasets\landmarks\L01_library\编号1_图书馆_1.jpg" | python -m json.tool
```

---

## 📡 API 接口

### 1. 健康检查
```bash
GET /api/v1/health
```

响应：
```json
{
  "status": "healthy",
  "service": "CampusLens AI Search",
  "version": "1.0.0"
}
```

### 2. 地标检索（核心功能）
```bash
POST /api/v1/search
Content-Type: multipart/form-data

file: <image_file>
```

响应示例：
```json
{
  "results": [
    {
      "rank": 1,
      "landmarkCode": "L01",
      "landmarkName": "library",
      "score": 0.9879,
      "confidenceLevel": "high",
      "mahalanobisDistance": 4.3239
    },
    {
      "rank": 2,
      "landmarkCode": "L03",
      "landmarkName": "wenyong_square",
      "score": 0.2744,
      "confidenceLevel": "low",
      "mahalanobisDistance": 44.5702
    }
  ],
  "lowConfidence": false,
  "message": "Search successful"
}
```

**字段说明**：
- `score`: **基于马氏距离的置信度评分** [0, 1]，越高表示越匹配
- `confidenceLevel`: 置信度等级（high/medium/low）
- `mahalanobisDistance`: 马氏距离，越小表示查询点越接近地标分布中心

### 3. 重建统计参数
```bash
POST /api/v1/index/rebuild
```

响应：
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

### 4. 统计参数状态
```bash
GET /api/v1/index/stats
```

响应：
```json
{
  "status": "ready",
  "totalVectors": 10,
  "dimension": 768,
  "indexedLandmarks": 10
}
```

---

## 🧮 算法原理

### 核心思想

**不只是比较"有多像"，而是判断"是否属于同一分布"**

通过统计学方法分析每个地标的特征分布模式，自动判断查询图片是否符合该地标的正常变化范围。

### 技术流程

```
1. 特征提取: DINOv2 → 768维向量
2. 统计分析: 计算均值 μ 和协方差矩阵 Σ
3. 马氏距离: d = √((q-μ)ᵀΣ⁻¹(q-μ))
4. 置信度评分: score = exp(-d²/(2·χ²_critical))
```

### 数学基础

**马氏距离**考虑了特征间的协方差结构：
```
d_M = √((q - μ)ᵀ Σ⁻¹ (q - μ))
```

**置信度评分**基于卡方分布理论：
```
score = exp(-d² / (2 · χ²₀.₉₅(768)))
```

其中 χ²₀.₉₅(768) ≈ 830.5 是自由度为 768 的卡方分布 95% 分位数。

### 算法优势

| 特性 | 传统方法（余弦相似度） | 本算法（马氏距离） |
|------|---------------------|-------------------|
| **理论基础** | 向量夹角 | 多元正态分布 + 卡方检验 |
| **参数设置** | 需要手动调参 | 完全自动化 |
| **区分度** | 0.9974 vs 0.9949（差异<0.3%） | 0.9879 vs 0.2744（差异>70%） |
| **置信度判断** | 所有都是 high | 只有真正匹配是 high |
| **适应性** | 固定标准 | 自动适应分布 |

详细算法说明见：[algo.md](algo.md)

---

## 🏗️ 技术架构

```
┌─────────────┐
│  SpringBoot │ ──── 上传图片 ────► │ FastAPI Service │
│  (Port 8080)│ ◄─── 返回结果 ──── │  (Port 8000)    │
└─────────────┘                     └────────┬────────┘
                                             │
                                    ┌────────▼────────┐
                                    │  DINOv2 Extractor│
                                    │  (离线模型加载)   │
                                    └────────┬────────┘
                                             │
                                    ┌────────▼────────┐
                                    │  Statistical     │
                                    │  Analysis Engine │
                                    │  (马氏距离计算)   │
                                    └────────┬────────┘
                                             │
                                    ┌────────▼────────┐
                                    │   FAISS Index    │
                                    │  (地标中心索引)   │
                                    └─────────────────┘
```

### 核心技术栈

- **FastAPI**: 高性能异步 Web 框架
- **DINOv2**: Facebook 自监督视觉 Transformer（本地加载）
- **马氏距离统计检索**: 为每个地标估计均值向量、正则化协方差矩阵和协方差逆矩阵，按卡方置信度评分返回 Top-5
- **PyTorch**: 深度学习推理引擎（支持 CPU/GPU）
- **NumPy + SciPy**: 数值计算和统计分析
- **Pillow**: 图像处理库

---

## ⚡ GPU 加速

本服务支持 **CPU 和 GPU 双模式**，可根据硬件配置灵活切换。

### 默认：CPU 模式

```bash
# 安装 CPU 版本依赖
pip install -r requirements.txt

# 启动服务（自动使用 CPU）
python app/main.py
```

### 启用 GPU 加速

#### Windows

```bash
# 运行 GPU 安装脚本
install_gpu.bat

# 验证 GPU 是否可用
python -c "import torch; print(f'CUDA available: {torch.cuda.is_available()}')"

# 重启服务（自动检测并使用 GPU）
python app/main.py
```

#### Linux/Mac

```bash
# 卸载 CPU 版本
pip uninstall -y torch torchvision faiss-cpu

# 安装 GPU 版本（CUDA 11.8）
pip install torch==2.1.2+cu118 torchvision==0.16.2+cu118 --index-url https://download.pytorch.org/whl/cu118
pip install faiss-gpu==1.7.4

# 重启服务
python app/main.py
```

### 性能对比

| 操作 | CPU (i7) | GPU (RTX 3060) | 加速比 |
|------|----------|----------------|--------|
| 单图特征提取 | ~200ms | ~30ms | **6.7x** ⚡ |
| Top-5 检索 | ~5ms | ~2ms | 2.5x |
| 统计参数构建 (250张) | ~60s | ~15s | **4x** ⚡ |

**建议**：如果有 NVIDIA GPU，强烈建议启用 GPU 加速！

详细 GPU 配置见：[GPU_SUPPORT.md](GPU_SUPPORT.md)

---

## 🔧 与 SpringBoot 集成

SpringBoot 调用示例：

```java
@Service
public class AISearchClient {

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public SearchResponse searchLandmark(MultipartFile file) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

            ResponseEntity<SearchResponse> response = restTemplate.postForEntity(
                aiServiceUrl + "/api/v1/search",
                requestEntity,
                SearchResponse.class
            );

            return response.getBody();

        } catch (Exception e) {
            throw new ServiceException("AI service call failed: " + e.getMessage());
        }
    }
}
```

application.yml 配置：
```yaml
ai:
  service:
    url: http://localhost:8000
    timeout: 30000
```

---

## 📊 性能指标

### CPU 模式

| 指标 | 配置 |
|------|------|
| 单图特征提取 | ~200ms (Intel i7) |
| Top-5 检索 | <10ms |
| 内存占用 | ~2.5GB |
| 索引构建 (250张图) | ~60s |

### GPU 模式（RTX 3060）

| 指标 | 配置 |
|------|------|
| 单图特征提取 | ~30ms |
| Top-5 检索 | <5ms |
| 显存占用 | ~1.5GB |
| 统计参数构建 (250张图) | ~15s |

**加速比**：GPU 比 CPU 快 **4-7 倍**！

**注意**：v2.1 优化后，检索时间略有增加（从 <5ms 到 <10ms），但召回率显著提升（>99%）。

---

## 🐛 故障排查

### 问题 1: 模型文件找不到
```
❌ 模型文件不存在: ./models/dinov2_model.pth
```

**解决方案**：
```bash
# 检查文件是否存在
ls -lh ./models/dinov2_model.pth

# 或设置自定义路径
export DINO_MODEL_PATH=/path/to/your/model.pth
```

### 问题 2: 模型格式不兼容
```
❌ 模型加载失败: Unsupported model format
```

**解决方案**：
```bash
# 运行验证脚本查看详细错误
python verify_model.py ./models/dinov2_model.pth

# 重新下载官方模型
wget https://dl.fbaipublicfiles.com/dinov2/dinov2_vitb14/dinov2_vitb14_pretrain.pth
```

### 问题 3: GPU 不可用
```
Using device: cpu
```

**解决方案**：
```bash
# 检查是否有 NVIDIA GPU
nvidia-smi

# 验证 CUDA 是否可用
python -c "import torch; print(f'CUDA available: {torch.cuda.is_available()}')"

# 如果返回 False，需要安装 GPU 版本
# Windows: 运行 install_gpu.bat
# Linux/Mac: 见上方「启用 GPU 加速」章节
```

### 问题 4: 内存不足
```
RuntimeError: CUDA out of memory
```

**解决方案**：
```bash
# 减小批处理大小
export BATCH_SIZE=16

# 或强制使用 CPU
DEVICE=cpu
```

### 问题 5: 统计参数为空
```
ValueError: 统计参数为空，请先构建地标统计参数
```

**解决方案**：
```bash
# 重建统计参数
curl -X POST http://localhost:8000/api/v1/index/rebuild

# 检查数据集目录
ls -R ../datasets/landmarks/
```

---

## 📝 注意事项

1. ✅ **纯离线运行**: 无需网络连接，所有资源本地加载
2. ⚠️ **模型路径**: 启动前必须确保模型文件存在
3. 📂 **数据集准备**: 统计参数构建前需准备好地标图片
4. 💾 **参数持久化**: 地标统计参数自动保存到算法数据目录
5. 🔒 **生产部署**: 建议添加认证、限流和监控
6. ⚡ **GPU 加速**: 如有 NVIDIA GPU，建议启用 GPU 模式
7. 📊 **统计信息**: 每次重建索引会计算协方差矩阵，需要一定时间
8. 🎯 **响应格式**: v2.0+ 使用精简的响应格式，只返回核心字段

---

## 🔄 版本历史

### v2.1 (2026-05-19) - 当前版本
- ✅ 移除未使用的余弦相似度变量
- ✅ 改进 FAISS 召回策略（`top_k * 2` → `max(top_k * 5, 30)`）
- ✅ 提升召回率至 >99%
- ✅ 精简 API 响应格式

### v2.0 (2026-05-19)
- ✅ 升级为地标类别级别检索
- ✅ 引入马氏距离评分算法
- ✅ 实现基于卡方分布的置信度评估
- ✅ 添加协方差矩阵分析

### v1.0 (2026-05-18)
- ✅ 基础图片级别检索
- ✅ DINOv2 + FAISS 集成
- ✅ CPU/GPU 双模式支持

---

## 📚 相关文档

- [algo.md](algo.md) - 算法原理详细说明
- [GPU_SUPPORT.md](GPU_SUPPORT.md) - GPU 加速配置指南
- [QUICKSTART.md](QUICKSTART.md) - 快速启动指南
- [CHECKLIST.md](CHECKLIST.md) - 项目清单和状态

---

## 📄 License

本项目遵循项目根目录的 LICENSE 文件。
