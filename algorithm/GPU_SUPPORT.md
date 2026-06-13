# CampusLens CPU/GPU 环境说明

## 当前计算边界

- DINOv2 特征提取使用 PyTorch，可在 NVIDIA GPU 或 CPU 上运行。
- FAISS 固定使用 `faiss-cpu==1.7.4`。当前索引规模不需要 FAISS GPU，且 Windows 环境不再安装 `faiss-gpu`。
- 日常模式启动两个独立算法进程，主实例监听 `8000`，备用实例监听 `8001`。两个进程都会加载模型，因此显存占用不是单实例的数值。
- `.env` 默认 `DEVICE=auto`：CUDA 可用时选择 GPU，否则回退 CPU。设置 `DEVICE=cuda` 时若 CUDA 不可用，服务会明确报错。

## Windows GPU 安装

推荐方式：

```powershell
cd algorithm
Copy-Item .env.example .env
.\create_gpu_env.bat
```

脚本使用固定路径：

```text
Conda:  D:\AnaConda\Scripts\conda.exe
环境:   D:\AnaConda\envs\campuslens-gpu
缓存:   D:\Tools\conda-pkgs、D:\Tools\pip-cache、D:\tmp
```

如果本机环境路径不同，先创建自己的 Python 3.10 环境，再指定解释器：

```powershell
$env:CAMPUSLENS_ALGORITHM_PYTHON = "D:\path\to\python.exe"
.\install_gpu.bat
```

`install_gpu.bat` 安装 `requirements-gpu.txt` 和 `requirements-test.txt`。其中 GPU requirements 已引用公共 `requirements.txt`，无需再单独安装。

## CPU 安装

```powershell
$env:CAMPUSLENS_ALGORITHM_PYTHON = "D:\path\to\python.exe"
.\install_cpu.bat
```

该脚本会移除现有 `torch`、`torchvision`，再安装 CPU 版本及测试依赖。不要在仍需 CUDA 的共享环境中执行该脚本。

Linux NVIDIA 环境可在 Python 3.10 虚拟环境中执行：

```bash
python -m pip install -r requirements-gpu.txt -r requirements-test.txt
```

macOS 和无 NVIDIA GPU 的 Linux 环境使用 `requirements-cpu.txt`。

## 验证

Windows 推荐环境：

```powershell
D:\AnaConda\envs\campuslens-gpu\python.exe -c "import torch, faiss; print('CUDA:', torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'); print('FAISS CPU ready')"
```

同时检查驱动：

```powershell
nvidia-smi
```

验收标准是 `torch.cuda.is_available()` 返回 `True` 且能输出显卡名称。FAISS GPU 数量不是当前项目的验收指标。

服务启动后可检查两个实例：

```powershell
curl.exe http://localhost:8000/api/v1/health
curl.exe http://localhost:8001/api/v1/health
```

响应中的 `instanceId`、`instanceRole` 和设备信息应与主备配置一致。

## 配置

在 `algorithm/.env` 中选择设备：

```dotenv
DEVICE=auto
MIXED_PRECISION=true
BATCH_SIZE=32
SAR_ENABLED=true
```

- `DEVICE=auto`：自动选择 CUDA 或 CPU。
- `DEVICE=cuda`：要求 CUDA 必须可用。
- `DEVICE=cpu`：强制 CPU，适合兼容性验证。
- `MIXED_PRECISION=true`：GPU 推理使用混合精度以降低显存和延迟。
- `SAR_ENABLED=true`：允许请求使用 SAR；具体请求仍需传入 `sarMode=true`。

## 双实例资源说明

日常启动脚本会启动两个独立 Python 进程。这样可以在主实例连接失败、超时或返回 5xx 时由后端转向备用实例，但代价是两份模型显存。

显存不足时按以下顺序处理：

1. 关闭其他占用 GPU 的进程。
2. 减小 `.env` 中的 `BATCH_SIZE`。
3. 临时设置 `DEVICE=cpu` 验证功能。
4. 仅在开发诊断时手工启动单实例；日常完整验收仍使用双实例。

## 常见故障

### `torch.cuda.is_available()` 为 `False`

确认 NVIDIA 驱动正常，再确认实际启动解释器与安装依赖时使用的是同一个解释器：

```powershell
where.exe python
$env:CAMPUSLENS_ALGORITHM_PYTHON
```

项目启动脚本的解释器优先级为：`CAMPUSLENS_ALGORITHM_PYTHON`、固定 D 盘 Conda 环境、项目 `.venv`。

### 安装后仍缺少 PyTorch

`requirements.txt` 只包含公共依赖。应运行 `install_gpu.bat`、`install_cpu.bat`，或安装对应的 CPU/GPU requirements。

### 模型加载失败

确认 `algorithm/models/dinov2_model.pth` 存在，且 `.env` 中 `DINO_MODEL_PATH` 指向正确文件。统一启动脚本不会下载模型。

### 双实例中一个未启动

先查看 `scripts/runtime/logs`，再检查 8000、8001 端口。可用根目录 `scripts\stop.cmd` 清理旧进程后重新启动。

**最后更新：2026-06-13**
