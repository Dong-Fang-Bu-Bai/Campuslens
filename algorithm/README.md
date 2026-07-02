# CampusLens AI 图像检索服务

基于 DINOv2 特征提取 + 马氏距离统计检索的校园地标智能检索微服务（**纯离线模式**）。

## ⚠️ 重要说明

**本服务仅支持离线模式，必须预先准备 DINOv2 模型文件。**

模型权重和索引产物不提交到 GitHub。`models/dinov2_model.pth` 需要按本 README 下载到本地；`data/faiss_index/landmark_index.faiss`、`metadata.pkl`、`landmark_stats.pkl` 由 `/api/v1/index/rebuild` 根据本地数据集自动生成。

---

## 🎯 核心特性

- ✅ **纯离线运行**: 无需网络连接，所有资源本地加载
- ✅ **地标级别检索**: 返回 Top-5 地标类别而非单张图片
- ✅ **经验匹配分**: 基于马氏距离和 sigmoid 归一化提供稳定区分度
- ✅ **匹配等级**: 返回 high/medium/low 等级辅助人工核验，不解释为概率置信度
- ✅ **CPU/GPU 双模式**: `DEVICE=auto` 优先 CUDA，启动时不可用则回退 CPU
- ✅ **有界 GPU 推理**: 单执行信号量、批量上限 2、CUDA OOM 自动拆为单图重试
- ✅ **SAR 在线适配**: 支持带可靠性门控、状态持久化和自动回退的测试时自适应实验
- ✅ **索引版本管理**: 支持索引重建、版本切换和回滚

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

本项目默认文件名为 `dinov2_model.pth`。如果下载的是官方 `dinov2_vitb14_pretrain.pth`，请保存或重命名为：

```text
algorithm/models/dinov2_model.pth
```

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

### 3. 系统要求

| 组件 | 最低要求 | 推荐配置 |
|------|---------|---------|
| CPU | Intel i5-8400 | Intel i7-12700 |
| 内存 | 16GB | 32GB |
| GPU | 无 | NVIDIA RTX 3060+ |
| CUDA | 无 | 12.1 |
| 存储 | 10GB | 20GB |

---

## 🚀 快速开始

### 方式一：本地运行（推荐）

#### Step 1: 首次安装与配置

进入算法目录并创建配置文件：

```powershell
cd algorithm
Copy-Item .env.example .env
```

**Windows + NVIDIA GPU（推荐）**：

```powershell
.\create_gpu_env.bat
```

该脚本使用 `D:\AnaConda\Scripts\conda.exe` 创建 `D:\AnaConda\envs\campuslens-gpu`，并安装：
- `requirements.txt`：FastAPI、FAISS CPU、Pillow、NumPy、SciPy 等公共依赖；
- `requirements-gpu.txt`：`torch 2.1.2+cu121`、`torchvision 0.16.2+cu121`；
- `requirements-test.txt`：pytest 与 httpx。

**已有 Python 环境**：

```powershell
$env:CAMPUSLENS_ALGORITHM_PYTHON = "D:\path\to\python.exe"
.\install_gpu.bat
```

**CPU 环境**：

```powershell
$env:CAMPUSLENS_ALGORITHM_PYTHON = "D:\path\to\python.exe"
.\install_cpu.bat
```

**依赖文件说明**：

| 文件 | 内容 |
| --- | --- |
| `requirements.txt` | FastAPI、FAISS CPU、图像与数据处理等公共依赖，不含 PyTorch |
| `requirements-gpu.txt` | 公共依赖 + PyTorch CUDA 12.1 |
| `requirements-cpu.txt` | 公共依赖 + CPU 版 PyTorch |
| `requirements-test.txt` | 公共依赖 + pytest、httpx |

> **注意**：不要只运行 `pip install -r requirements.txt`，否则算法服务缺少 PyTorch。

**依赖安装脚本说明**：

- `create_gpu_env.bat`：使用 `D:\AnaConda\Scripts\conda.exe` 创建 `D:\AnaConda\envs\campuslens-gpu`，并安装公共依赖、PyTorch CUDA 12.1 及测试依赖。Conda、pip 和临时文件缓存均写入 D 盘配置目录。
- `install_gpu.bat`：在已有 Python 3.10+ 环境中安装 GPU 版本依赖
- `install_cpu.bat`：在已有 Python 环境中安装 CPU 版本依赖

#### Step 2: 验证模型文件

```powershell
D:\AnaConda\envs\campuslens-gpu\python.exe verify_model.py .\models\dinov2_model.pth
```

预期输出：
```
============================================================
DINOv2 离线模型验证工具
============================================================
[OK] 模型文件存在: ./models/dinov2_model.pth
[INFO] 文件大小: 330.33 MB
...
[OK] 模型验证通过
```

模型默认路径为 `algorithm/models/dinov2_model.pth`，可通过 `.env` 中的 `DINO_MODEL_PATH` 修改。索引和 SAR 运行数据位于 `algorithm/data`。首次没有可用索引时，需要准备 `datasets/landmarks` 数据集并由主实例触发索引重建。

#### Step 3: 配置环境变量

编辑 `.env` 文件可配置：
- `DEVICE`: 计算设备 (`auto`/`cpu`/`cuda`)，默认 `auto`
- `IMAGE_SIZE`: 输入图片尺寸，默认 `518`
- `BATCH_SIZE`: 批处理大小，默认 `32`
- `SEARCH_BATCH_SIZE`: 在线批量接口上限，默认 `2`
- `MIXED_PRECISION`: 混合精度开关，默认 `false`
- `SAR_ENABLED`: SAR 功能开关，默认 `true`

#### Step 4: 启动服务

**主实例**：

```powershell
$env:PORT = "8000"
$env:INSTANCE_ID = "algorithm-primary"
$env:INSTANCE_ROLE = "primary"
D:\AnaConda\envs\campuslens-gpu\python.exe app\main.py
```

**备用实例**（在另一个终端）：

```powershell
$env:PORT = "8001"
$env:INSTANCE_ID = "algorithm-secondary"
$env:INSTANCE_ROLE = "secondary"
D:\AnaConda\envs\campuslens-gpu\python.exe app\main.py
```

服务启动后会加载 DINOv2 模型并打印特征维度等信息。

#### Step 5: 构建统计参数

```powershell
curl.exe -X POST http://localhost:8000/api/v1/index/rebuild
```

#### Step 6: 测试检索

```powershell
curl.exe -X POST http://localhost:8000/api/v1/search `
  -F "file=@.\datasets\landmarks\L01_library\编号1_图书馆_1.jpg" | python -m json.tool
```

### 方式二：启动完整项目

使用项目根目录的统一脚本：

```powershell
cd ..
scripts\start.cmd
scripts\verify.cmd
```

统一脚本会启动：
- MySQL 数据库
- Redis 缓存
- 算法主实例（端口 8000）
- 算法备用实例（端口 8001）
- Spring Boot 后端（端口 8080）
- 前端（https://localhost:5173）

停止项目：
```powershell
scripts\stop.cmd
```

停止时保留 Docker 镜像和命名数据卷。

### 方式三：仅启动算法服务

**主实例**：
```powershell
$env:PORT = "8000"
$env:INSTANCE_ID = "algorithm-primary"
$env:INSTANCE_ROLE = "primary"
D:\AnaConda\envs\campuslens-gpu\python.exe app\main.py
```

**备用实例**（在另一个终端）：
```powershell
$env:PORT = "8001"
$env:INSTANCE_ID = "algorithm-secondary"
$env:INSTANCE_ROLE = "secondary"
D:\AnaConda\envs\campuslens-gpu\python.exe app\main.py
```

**健康检查**：
```powershell
curl.exe http://localhost:8000/api/v1/health
curl.exe http://localhost:8001/api/v1/health
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
  "version": "1.0.0",
  "device": "cuda",
  "gpuName": "NVIDIA GeForce RTX 4060",
  "cudaVersion": "12.1",
  "modelReady": true,
  "maxBatchSize": 2,
  "activeInference": 0,
  "instanceId": "algorithm-8000",
  "instanceRole": "primary"
}
```

### 2. 地标检索（核心功能）

```bash
POST /api/v1/search
Content-Type: multipart/form-data

file: <image_file>
sarMode: false (默认) / true (启用 SAR)
```

响应示例：
```json
{
  "results": [
    {
      "rank": 1,
      "landmarkCode": "L01",
      "landmarkName": "library",
      "score": 0.8533,
      "confidenceLevel": "high",
      "mahalanobisDistance": 500.0
    },
    {
      "rank": 2,
      "landmarkCode": "L03",
      "landmarkName": "wenyong_square",
      "score": 0.1113,
      "confidenceLevel": "low",
      "mahalanobisDistance": 1800.0
    }
  ],
  "lowConfidence": false,
  "message": "Search successful",
  "sarApplied": false,
  "trustLevel": "trusted",
  "modelVersion": "base-xxx@index-xxx@baseline"
}
```

**字段说明**：
- `score`: **基于马氏距离的经验归一化匹配分** [0, 1]，越高表示越匹配
- `confidenceLevel`: 匹配等级（high/medium/low）
- `mahalanobisDistance`: 马氏距离，越小表示查询点越接近地标分布中心
- `trustLevel`: 信任等级（trusted/uncertain/untrusted）

### 3. 批量地标检索

```bash
POST /api/v1/search/batch
Content-Type: multipart/form-data

files: <image_file_1>
files: <image_file_2>
sarMode: true/false
```

响应中的 `items` 与上传顺序一致。该接口不是轮询接口，而是一次请求提交多张图片，由算法服务在同一个受控推理批次中统一处理。当前 `SEARCH_BATCH_SIZE` 默认为 `2`；如果只有一张图片，应优先调用 `/api/v1/search`。当批量推理发生 CUDA OOM 时，服务会清理缓存并降级为逐张重试，因此批量接口的主要目的，是减少重复预处理和模型调用开销，并统一返回每张图片的成功或失败结果。

### 4. 重建统计参数

```bash
POST /api/v1/index/rebuild
```

仅允许 `instanceRole=primary` 的实例发起。返回 HTTP `202`：
```json
{
  "status": "accepted",
  "rebuildJobId": "<uuid>",
  "createdAt": "2026-06-13T10:00:00+00:00",
  "error": null
}
```

### 5. 重建任务状态

```bash
GET /api/v1/index/rebuild/{job_id}
```

状态依次为 `building` → `switching` → `completed` 或 `failed`。

### 6. 运行状态

```bash
GET /api/v1/runtime/status
```

返回实例身份、基准模型版本、活动索引版本、SAR 状态版本、漂移指标和最近重建任务。

### 7. 统计参数状态

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

### 8. 校正样本

```bash
POST /api/v1/adaptation/correction-samples
```

用户提交反馈后，后端调用该接口评估图片和原检索结果，计算 `suggestAccept`、`reviewScore` 与评估原因，并写入本地评估 manifest。该接口不会自动采纳反馈、复制待发布样本或更新 SAR 参数；样本只有在管理员采纳后才进入后续索引重建流程。

### 接口汇总

| 接口 | 方法 | 作用 |
| --- | --- | --- |
| `/api/v1/health` | GET | 健康检查与实例信息 |
| `/api/v1/search` | POST | 单图普通/SAR 检索 |
| `/api/v1/search/batch` | POST | 批量检索 |
| `/api/v1/runtime/status` | GET | 模型、SAR 与索引运行状态 |
| `/api/v1/index/stats` | GET | 活动索引统计 |
| `/api/v1/index/rebuild` | POST | 创建索引重建任务 |
| `/api/v1/index/rebuild/{job_id}` | GET | 查询重建状态 |
| `/api/v1/adaptation/correction-samples` | POST | 评估反馈校正样本并生成审核建议 |

---

## 🧮 算法原理

### 核心思想

**不只是比较"有多像"，而是判断"是否属于同一分布"**

通过每个地标的特征分布模式计算马氏距离，再映射为经验匹配分，用于 Top-5 排序和结果展示。

### 技术流程

```
1. 特征提取: DINOv2 → 768维向量
2. 统计分析: 计算均值 μ 和协方差矩阵 Σ
3. 马氏距离: d = √((q-μ)ᵀΣ⁻¹(q-μ))
4. 经验匹配分: score = sigmoid(log(d + 1), center=log(701), slope=5.0)
```

### 数学基础

**马氏距离**考虑了特征间的协方差结构：
```
d_M = √((q - μ)ᵀ Σ⁻¹ (q - μ))
```

**经验匹配分**基于马氏距离的 sigmoid 归一化：
```
score = 1 / (1 + exp(5.0 · (log(d + 1) - log(701))))
```

### SAR 在线适配

算法服务维护基准与持续 SAR 两条轨道：
- `sarMode=false`：使用不可变基准模型
- `sarMode=true`：使用 SAR 自适应模型，根据检索结果动态调整

SAR 状态在基准模型和索引版本一致时可跨重启恢复；低熵 EMA、锚点 Top-1 保持率或特征漂移越界时自动回退。当前验收只能说明双轨更新、恢复和回退链路可运行，不能据此直接宣称识别准确率必然提升。

### 设计差异

| 特性 | 余弦相似度 | 当前马氏距离检索 |
|------|---------------------|-------------------|
| **理论基础** | 向量夹角 | 马氏距离 + 经验 sigmoid 归一化 |
| **统计信息** | 不使用类别协方差 | 使用各地标均值和正则化协方差 |
| **展示分数** | 可直接使用相似度排序 | 将距离映射为经验匹配分，并划分 high/medium/low |
| **适用边界** | 适合快速向量近邻召回 | 依赖已有地标样本统计，参数需结合数据集标定 |

当前实现只保留马氏距离到经验匹配分的单一路径，旧自适应评分函数和 `confidence_threshold` 兼容参数已移除。`score` 仍只用于排序和展示区分度，不代表概率。

详细算法说明见：[algo.md](algo.md)

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                    FastAPI Web Service                      │
│                       (Port 8000)                          │
└─────────────────────────┬───────────────────────────────────┘
                         │
         ┌────────────────────────┼────────────────────────┐
         ▼                        ▼                        ▼
┌───────────────┐      ┌────────────────┐      ┌─────────────────┐
│  Feature      │      │  Search        │      │  SAR Adapter    │
│  Extraction   │      │  Service       │      │                 │
│  (DINOv2)     │      │  (Mahalanobis) │      │  (Online Adapt) │
└───────┬───────┘      └────────┬───────┘      └────────┬────────┘
        │                       │                       │
        └───────────┬───────────┴───────────┬───────────┘
                    ▼                       ▼
           ┌─────────────────┐    ┌─────────────────────┐
           │  FAISS Index    │    │  SAR State Manager  │
           │  Manager        │    │  (Versioning)       │
           └─────────────────┘    └─────────────────────┘
```

### 核心技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| Web 框架 | FastAPI | 0.100+ |
| 特征提取 | DINOv2 ViT-B/14 | - |
| 深度学习 | PyTorch | 2.1.2+cu121 |
| 向量检索 | FAISS | 1.7.4 |
| 数值计算 | NumPy + SciPy | - |
| 图像处理 | Pillow | - |

### 模块职责

| 模块 | 文件 | 职责 |
|------|------|------|
| **Feature Service** | `app/services/feature_service.py` | 管理 DINOv2 模型，提取特征向量 |
| **Search Service** | `app/services/search_service.py` | 协调搜索流程，调用 FAISS 管理器 |
| **FAISS Manager** | `app/utils/faiss_manager.py` | 索引管理、统计计算、马氏距离评分 |
| **SAR Adapter** | `app/models/sar_adapter.py` | SAR 自适应逻辑、参数更新 |
| **Image Processor** | `app/utils/image_processor.py` | 图片加载、预处理 |

---

## ⚡ GPU 加速

### Windows GPU 环境

```powershell
create_gpu_env.bat
D:\AnaConda\envs\campuslens-gpu\python.exe -c "import torch, faiss; print(torch.__version__); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'); print(faiss.__version__)"
```

预期为 PyTorch `2.1.2+cu121`、CUDA 可用、识别本机 NVIDIA GPU、FAISS `1.7.4`。

### Linux（NVIDIA GPU）

```bash
pip install -r requirements-gpu.txt -r requirements-test.txt
python app/main.py
```

### macOS

macOS 不支持 CUDA，使用 `requirements-cpu.txt`。

### 验证结果

当前 RTX 4060 环境使用 GPU 执行 DINOv2 特征提取，FAISS 继续在 CPU 上检索。

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

## 📊 性能验证口径

性能受模型设备、双实例数量、数据集规模、是否启用 SAR 和并发方式影响，不在 README 中给出脱离环境的固定承诺。当前完整系统验收使用 RTX 4060：213 张图片的索引重建约耗时 590.9 秒；高并发 SAR 场景的 P95 延迟约为 8.2 秒，双实例峰值显存约为 7.7 GiB。上述数据仅代表对应验收环境。

提交新的性能结论时，应同时记录硬件、数据量、请求模式、样本数、平均值/P95 和显存峰值。详细测试条件及结果见 [完整系统验收记录](../docs/15_full_system_acceptance_test.md) 和 [持续 SAR 双轨运行验证记录](../docs/14_sar_online_adaptation_test.md)。

---

## 🧪 测试方法

### 1. 环境验证

```powershell
# 验证 Python 环境和依赖
D:\AnaConda\envs\campuslens-gpu\python.exe -c "import torch, faiss; print(f'CUDA: {torch.cuda.is_available()}'); print(f'FAISS: {faiss.__version__}')"

# 验证模型文件
python verify_model.py ./models/dinov2_model.pth
```

预期输出：
```
============================================================
DINOv2 离线模型验证工具
============================================================
[OK] 模型文件存在: ./models/dinov2_model.pth
[INFO] 文件大小: 330.33 MB
...
[OK] 模型验证通过
```

### 2. 单元测试

项目提供核心模块的单元测试（位于 `tests/` 目录，如未创建可手动添加）：

```powershell
cd algorithm
D:\AnaConda\envs\campuslens-gpu\python.exe -m pytest tests/ -v
```

测试覆盖：
- 马氏距离计算正确性
- 经验匹配分范围验证
- FAISS 索引构建与搜索
- SAR 熵计算与门控逻辑

### 3. 基准测试

```powershell
# SAR 基准测试
python benchmark_sar.py --limit-per-landmark 1
```

### 4. API 测试

#### 健康检查
```powershell
curl.exe http://localhost:8000/api/v1/health
```

预期响应：
```json
{
  "status": "healthy",
  "service": "CampusLens AI Search",
  "version": "1.0.0",
  "device": "cuda",
  "modelReady": true,
  "instanceId": "algorithm-8000",
  "instanceRole": "primary"
}
```

#### 单图检索
```powershell
# 普通检索
curl.exe -X POST http://localhost:8000/api/v1/search -F "file=@test.jpg" | python -m json.tool

# SAR 检索（启用在线自适应）
curl.exe -X POST http://localhost:8000/api/v1/search -F "file=@test.jpg" -F "sarMode=true" | python -m json.tool
```

#### 批量检索
```powershell
curl.exe -X POST http://localhost:8000/api/v1/search/batch -F "files=@img1.jpg" -F "files=@img2.jpg" | python -m json.tool
```

#### 索引重建
```powershell
curl.exe -X POST http://localhost:8000/api/v1/index/rebuild
```

#### 运行状态查询
```powershell
curl.exe http://localhost:8000/api/v1/runtime/status
curl.exe http://localhost:8000/api/v1/index/stats
```

### 5. SAR 功能验证

```powershell
# 1. 启动服务
$env:PORT = "8000"
$env:INSTANCE_ID = "algorithm-primary"
$env:INSTANCE_ROLE = "primary"
D:\AnaConda\envs\campuslens-gpu\python.exe app\main.py

# 2. 重建索引
curl.exe -X POST http://localhost:8000/api/v1/index/rebuild

# 3. 使用高可信度样本测试 SAR
curl.exe -X POST http://localhost:8000/api/v1/search `
  -F "file=@../datasets/landmarks/L01_library/L01_front_day_001.jpg" `
  -F "sarMode=true" | python -m json.tool

# 4. 检查 SAR 状态
curl.exe http://localhost:8000/api/v1/runtime/status | python -m json.tool
```

验证要点：
- `sarApplied` 应为 `true`（对于低熵样本）
- `trustLevel` 应为 `trusted` 或 `uncertain`
- `sarStateVersion` 应随每次 SAR 更新递增

### 6. 双实例部署测试

```powershell
# 启动主实例（端口 8000）
$env:PORT = "8000"
$env:INSTANCE_ID = "algorithm-primary"
$env:INSTANCE_ROLE = "primary"
D:\AnaConda\envs\campuslens-gpu\python.exe app\main.py

# 在另一个终端启动备用实例（端口 8001）
$env:PORT = "8001"
$env:INSTANCE_ID = "algorithm-secondary"
$env:INSTANCE_ROLE = "secondary"
D:\AnaConda\envs\campuslens-gpu\python.exe app\main.py

# 验证两个实例状态
curl.exe http://localhost:8000/api/v1/health
curl.exe http://localhost:8001/api/v1/health

# 只有主实例可以重建索引
curl.exe -X POST http://localhost:8000/api/v1/index/rebuild  # 成功
curl.exe -X POST http://localhost:8001/api/v1/index/rebuild  # 失败（非主实例）
```

---

## 🐛 故障排查

### 问题 1: 模型文件找不到

```
❌ 模型文件不存在: ./models/dinov2_model.pth
```

**解决方案**：
```powershell
# 检查文件是否存在
Get-ChildItem ./models/dinov2_model.pth

# 或设置自定义路径
$env:DINO_MODEL_PATH = "D:\path\to\dinov2_model.pth"
```

### 问题 2: 模型格式不兼容

```
❌ 模型加载失败: Unsupported model format
```

**解决方案**：
```powershell
python verify_model.py ./models/dinov2_model.pth
```

### 问题 3: GPU 不可用

```
Using device: cpu
```

**解决方案**：
```powershell
nvidia-smi
python -c "import torch; print(torch.cuda.is_available())"
```

### 问题 4: 内存不足

```
RuntimeError: CUDA out of memory
```

**解决方案**：
```powershell
$env:BATCH_SIZE=16
$env:DEVICE=cpu
```

### 问题 5: 统计参数为空

```
ValueError: 统计参数为空，请先构建地标统计参数
```

**解决方案**：
```powershell
curl.exe -X POST http://localhost:8000/api/v1/index/rebuild
```

### 问题 6: PowerShell curl 失败

使用 `curl.exe` 而不是 PowerShell 的 `curl` 别名：
```powershell
curl.exe http://localhost:8000/api/v1/health
```

### 问题 7: 环境验证失败

```powershell
# 正确验证方式
D:\AnaConda\envs\campuslens-gpu\python.exe -c "import torch, faiss; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'); print('FAISS CPU ready')"
```

当前设计是 DINOv2 使用 CUDA 推理，FAISS 使用 CPU 索引，因此不应以 `faiss.get_num_gpus()` 作为 GPU 环境验收条件。

### 问题 8: 端口被占用

```powershell
# 先停止服务
scripts\stop.cmd

# 检查端口
netstat -ano | findstr "8000"
```

### 问题 9: 显存不足（双实例）

双实例会各自加载模型，可减小 `BATCH_SIZE`，测试时也可临时只启动一个实例：
```powershell
$env:BATCH_SIZE=16
```

---

## 📝 注意事项

1. ✅ **纯离线运行**: 无需网络连接，所有资源本地加载
2. ⚠️ **模型路径**: 启动前必须确保模型文件存在
3. 📂 **数据集准备**: 统计参数构建前需准备好地标图片
4. 💾 **参数持久化**: 地标统计参数自动保存到算法数据目录
5. 🔒 **生产部署**: 建议添加认证、限流和监控
6. ⚡ **GPU 加速**: 如有 NVIDIA GPU，建议启用 GPU 模式
7. 📊 **统计信息**: 每次重建统计参数会计算协方差矩阵，需要一定时间
8. 🎯 **响应格式**: v2.0+ 使用精简的响应格式，只返回核心字段
9. 🔄 **双实例部署**: 主实例负责索引重建，备用实例提供检索服务

---

## 🔄 版本历史

### v2.1 (2026-06-13) - 当前版本
- ✅ 移除未使用的余弦相似度变量
- ✅ 移除旧自适应评分遗留函数和未生效的兼容参数
- ✅ 改进 FAISS 召回策略（`top_k * 2` → `max(top_k * 5, 30)`）
- ✅ 扩大 FAISS 候选召回范围，并保留马氏距离重排序
- ✅ 精简 API 响应格式
- ✅ 新增 SAR 在线适配功能
- ✅ 新增索引版本管理
- ✅ 新增双实例部署支持

### v2.0 (2026-05-19)
- ✅ 升级为地标类别级别检索
- ✅ 引入马氏距离评分算法
- ✅ 实现基于马氏距离 sigmoid 归一化的经验匹配分
- ✅ 添加协方差矩阵分析

### v1.0 (2026-05-18)
- ✅ 基础图片级别检索
- ✅ DINOv2 + FAISS 集成
- ✅ CPU/GPU 双模式支持

---

## 📚 相关文档

- [algo.md](algo.md) - 算法原理详细说明与实现细节
- [SAR_IMPLEMENTATION.md](SAR_IMPLEMENTATION.md) - SAR 算法实现详解
- [GPU_SUPPORT.md](GPU_SUPPORT.md) - GPU 加速配置指南
- [CHECKLIST.md](CHECKLIST.md) - 项目清单和状态

---

## 📄 License

本项目遵循项目根目录的 LICENSE 文件。

**最后更新：2026-06-13**
