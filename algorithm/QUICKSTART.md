# CampusLens 算法服务快速启动

本文说明当前 `dev` 分支的算法环境安装与日常启动方式。完整项目优先使用根目录 `scripts` 下的统一脚本。

## 1. 首次安装

进入算法目录并创建配置文件：

```powershell
cd algorithm
Copy-Item .env.example .env
```

Windows + NVIDIA GPU 推荐直接创建项目专用环境：

```powershell
.\create_gpu_env.bat
```

该脚本使用 `D:\AnaConda\Scripts\conda.exe` 创建 `D:\AnaConda\envs\campuslens-gpu`，并安装公共依赖、PyTorch CUDA 12.1 及测试依赖。Conda、pip 和临时文件缓存均写入 D 盘配置目录。

已有 Python 3.10 环境时，可指定解释器后安装：

```powershell
$env:CAMPUSLENS_ALGORITHM_PYTHON = "D:\path\to\python.exe"
.\install_gpu.bat
```

无 NVIDIA GPU 时使用 CPU 依赖：

```powershell
$env:CAMPUSLENS_ALGORITHM_PYTHON = "D:\path\to\python.exe"
.\install_cpu.bat
```

依赖文件的职责如下：

| 文件 | 内容 |
| --- | --- |
| `requirements.txt` | FastAPI、FAISS CPU、图像与数据处理等公共依赖，不含 PyTorch |
| `requirements-gpu.txt` | 公共依赖 + PyTorch CUDA 12.1 |
| `requirements-cpu.txt` | 公共依赖 + CPU 版 PyTorch |
| `requirements-test.txt` | 公共依赖 + pytest、httpx |

不要只执行 `pip install -r requirements.txt`，否则算法服务缺少 PyTorch。

## 2. 准备模型与数据

模型默认路径：

```text
algorithm/models/dinov2_model.pth
```

可通过 `.env` 中的 `DINO_MODEL_PATH` 修改。验证模型：

```powershell
D:\AnaConda\envs\campuslens-gpu\python.exe verify_model.py .\models\dinov2_model.pth
```

索引和 SAR 运行数据位于 `algorithm/data`。首次没有可用索引时，需要准备 `datasets/landmarks` 数据集并由主实例触发索引重建。

## 3. 验证环境

```powershell
D:\AnaConda\envs\campuslens-gpu\python.exe -c "import torch, faiss; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'); print('FAISS CPU ready')"
```

当前设计是 DINOv2 使用 CUDA 推理，FAISS 使用 CPU 索引，因此不应以 `faiss.get_num_gpus()` 作为 GPU 环境验收条件。

## 4. 启动完整项目

回到项目根目录：

```powershell
scripts\start.cmd
scripts\verify.cmd
```

统一脚本会启动 MySQL、Redis、算法主实例 `8000`、算法备用实例 `8001`、Spring Boot 后端和 `https://localhost:5173` 前端。它不会自动安装算法 Python 依赖或下载模型。

停止项目：

```powershell
scripts\stop.cmd
```

停止时保留 Docker 镜像和命名数据卷。

## 5. 仅启动算法服务

主实例：

```powershell
$env:PORT = "8000"
$env:INSTANCE_ID = "algorithm-primary"
$env:INSTANCE_ROLE = "primary"
D:\AnaConda\envs\campuslens-gpu\python.exe app\main.py
```

备用实例需在另一个终端启动：

```powershell
$env:PORT = "8001"
$env:INSTANCE_ID = "algorithm-secondary"
$env:INSTANCE_ROLE = "secondary"
D:\AnaConda\envs\campuslens-gpu\python.exe app\main.py
```

健康检查：

```powershell
curl.exe http://localhost:8000/api/v1/health
curl.exe http://localhost:8001/api/v1/health
```

当前主要接口：

| 接口 | 作用 |
| --- | --- |
| `POST /api/v1/search` | 单图普通/SAR 检索 |
| `POST /api/v1/search/batch` | 批量检索 |
| `GET /api/v1/health` | 健康与实例信息 |
| `GET /api/v1/runtime/status` | 模型、SAR 与索引运行状态 |
| `GET /api/v1/index/stats` | 活动索引统计 |
| `POST /api/v1/index/rebuild` | 主实例创建索引重建任务 |
| `GET /api/v1/index/rebuild/{job_id}` | 查询重建状态 |
| `POST /api/v1/adaptation/correction-samples` | 写入已采纳校正样本 |

`SAR_ENABLED` 默认为 `true`，但单次请求是否启用 SAR 仍由请求字段 `sarMode` 控制，默认请求值为 `false`。

## 6. 常见问题

- 显示 `Using device: cpu`：检查 `nvidia-smi`、PyTorch CUDA 版本和 `.env` 的 `DEVICE`。
- 模型不存在：确认模型文件路径，或修改 `DINO_MODEL_PATH`。
- 端口被占用：先运行根目录 `scripts\stop.cmd`，再检查 8000、8001 端口。
- 显存不足：双实例会各自加载模型，可减小 `BATCH_SIZE`，测试时也可临时只启动一个实例。
- PowerShell 请求失败：使用 `curl.exe`，避免调用 PowerShell 的 `curl` 别名。

更多说明见 [README.md](README.md) 和 [GPU_SUPPORT.md](GPU_SUPPORT.md)。

**最后更新：2026-06-13**
