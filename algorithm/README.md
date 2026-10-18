# CampusLens 算法服务

基于 FastAPI / PyTorch，提供 DINOv2 图像特征提取、地标检索、SAR 测试时适配和版本化索引管理。模型与样本在本地加载，检索请求无需访问外部模型 API。

## 环境与资源

- Python 3.10。
- DINOv2 ViT-B/14 PyTorch 权重，默认 `models/dinov2_model.pth`。
- `../datasets/landmarks` 中按地标分类的图片。
- GPU 模式使用 PyTorch CUDA 12.1；FAISS 使用 CPU。

模型来源参见 [DINOv2 官方仓库](https://github.com/facebookresearch/dinov2)。加载器支持具有 `forward_features` 的模型对象及兼容的 ViT-B/14 state dict。官方权重可保存为本项目默认文件名；不兼容的模型结构不能仅通过重命名使用。

## 安装

在本目录创建配置与环境：

```powershell
Copy-Item .env.example .env
python -m venv .venv
$env:CAMPUSLENS_ALGORITHM_PYTHON = (Resolve-Path .venv\Scripts\python.exe).Path
& $env:CAMPUSLENS_ALGORITHM_PYTHON -m pip install -r requirements-gpu.txt -r requirements-test.txt
```

无 NVIDIA GPU 时使用 `requirements-cpu.txt`。公共 `requirements.txt` 不包含 PyTorch；设备和依赖细节见[CPU / GPU 配置](GPU_SUPPORT.md)。

完整系统启动见[运行指南](../docs/09_interface_runbook.md)。单独启动算法服务：

```powershell
& $env:CAMPUSLENS_ALGORITHM_PYTHON app\main.py
```

默认地址为 `http://localhost:8000`，交互式 API 文档为 `/docs`。第一次启动后准备样本并发起索引重建。

## 接口

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | /api/v1/health | 设备、实例与服务状态 |
| POST | /api/v1/search | 单图检索 |
| POST | /api/v1/search/batch | 批量检索，按输入顺序返回结果 |
| POST | /api/v1/index/rebuild | 创建索引重建任务 |
| GET | /api/v1/index/rebuild/{job_id} | 查询重建状态 |
| GET | /api/v1/index/stats | 索引、维度与地标统计 |
| GET | /api/v1/runtime/status | 模型、索引、SAR 与实例信息 |
| POST | /api/v1/adaptation/correction-samples | 处理经后端审核的校正样本 |

浏览器使用后端的任务接口；算法检索接口由后端调用。算法批量接口是同步推理请求，业务异步任务的准入、轮询和重试由 Spring Boot 管理。

返回候选包含 `rank`、`landmarkCode`、`landmarkName`、`score`、`confidenceLevel` 和 `mahalanobisDistance`。响应还携带 SAR、模型与索引版本信息。

## 检索流程

1. 将图片转换为 RGB，缩放并中心裁剪至 518×518，按 ImageNet 均值与标准差归一化。
2. 使用 DINOv2 提取 L2 归一化 CLS 特征。
3. 从 FAISS 地标中心索引召回候选。
4. 计算查询特征到各候选地标分布的马氏距离。
5. 映射为经验匹配分，降序返回 Top-K。

分数用于排序与展示，不是校准后的概率。匹配等级为 high / medium / low。公式与实现入口见[检索原理](algo.md)。

## SAR 模式

`SAR_ENABLED` 控制服务是否支持 SAR，请求 `sarMode` 默认为 false。基准模型与适配模型独立运行；SAR 通过可靠性门控更新归一化层参数，并保存版本绑定的状态。EMA、锚点保持率和特征漂移用于回退保护。

SAR 请求由后端独立调度，避免不同用户图片共享适配梯度。详见[SAR 说明](SAR_IMPLEMENTATION.md)。

## 索引与存储

| 路径 | 内容 |
| --- | --- |
| models/ | 离线模型 |
| data/faiss_index/ | FAISS 索引、元数据与地标统计 |
| data/features/ | 特征缓存 |
| data/model_versions/ | 模型、索引版本及 SAR 状态 |
| data/logs/ | 算法运行日志 |

索引重建生成候选版本，验证成功后原子切换活动指针。重建期间在线请求读取旧版本。后端在发布成功后更新校正样本状态，详见[系统架构](../docs/architecture.md)。

## 配置

配置定义见 [app/config.py](app/config.py)，示例见 [.env.example](.env.example)。

| 配置 | 默认值 | 用途 |
| --- | --- | --- |
| DEVICE | auto | 自动选择 CUDA 或 CPU |
| IMAGE_SIZE | 518 | 预处理图像尺寸配置 |
| BATCH_SIZE | 32 | 离线特征提取批次 |
| SEARCH_BATCH_SIZE | 2 | 单次检索批次上限 |
| TOP_K_RESULTS | 5 | 候选数量 |
| MIXED_PRECISION | false | CUDA 混合精度 |
| SAR_ENABLED | true | 服务级 SAR 开关 |
| DINO_MODEL_PATH | models/dinov2_model.pth | 模型位置 |

预处理实现位于 [dinov2_extractor.py](app/models/dinov2_extractor.py)，采用固定 518×518。调整图像尺寸时需同步修改实现并重建索引。

## 测试

```powershell
& $env:CAMPUSLENS_ALGORITHM_PYTHON -m pytest
```

测试场景与系统级命令见[测试说明](../docs/testing.md)。
