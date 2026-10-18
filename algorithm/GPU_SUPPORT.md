# CPU / GPU 环境配置

DINOv2 使用 PyTorch 在 NVIDIA GPU 或 CPU 上运行，FAISS 使用 CPU 索引。双算法实例是两个独立进程，各自加载模型。

## 安装依赖

在 Python 3.10 独立环境中，从算法目录执行：

```powershell
python -m pip install -r requirements-gpu.txt -r requirements-test.txt
```

GPU 依赖使用 PyTorch 2.1.2 / torchvision 0.16.2 的 CUDA 12.1 版本。无 NVIDIA GPU 时：

```powershell
python -m pip install -r requirements-cpu.txt -r requirements-test.txt
```

上述 python 应指向目标环境。完整 Windows 环境创建示例见[运行指南](../docs/09_interface_runbook.md)。

## 设备检查

```powershell
python -c "import torch, faiss; print('CUDA:', torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'); print('FAISS ready')"
nvidia-smi
```

CUDA 模式需同时满足驱动可用、CUDA 版 PyTorch 已安装及 `torch.cuda.is_available()` 返回 true。FAISS 的 GPU 数量不用于判断 DINOv2 的运行设备。

## 设备选择

在 `.env` 中配置：

```dotenv
DEVICE=auto
MIXED_PRECISION=false
BATCH_SIZE=32
SEARCH_BATCH_SIZE=2
```

| 配置 | 行为 |
| --- | --- |
| DEVICE=auto | CUDA 可用时使用 GPU，否则使用 CPU |
| DEVICE=cuda | 强制 CUDA，不可用时启动失败 |
| DEVICE=cpu | 强制 CPU |
| MIXED_PRECISION=true | CUDA 上使用混合精度 |
| BATCH_SIZE | 控制离线特征提取批次 |
| SEARCH_BATCH_SIZE | 控制在线检索批次上限 |

## 主备实例与资源

主实例监听 8000，备用实例监听 8001。两个进程各自维护基准与适配模型，显存占用需按进程及模型副本评估。

显存不足时，可降低批次、关闭其他 GPU 工作负载或使用 CPU 模式定位问题。CPU 模式适合功能检查，吞吐与延迟需要按实际数据集测量。

## 常见问题

| 问题 | 处理 |
| --- | --- |
| CUDA 返回 false | 检查解释器、PyTorch 版本、驱动和 NVIDIA 设备 |
| 缺少 torch | 安装 GPU 或 CPU requirements，公共 requirements 不包含 PyTorch |
| 模型无法加载 | 检查权重路径和 ViT-B/14 结构兼容性 |
| 备用实例未启动 | 检查 8001 端口、进程日志和可用显存 |
| CUDA OOM | 降低批次，检查两个实例的模型占用 |

运行设备与实例状态可通过 `/api/v1/health` 和 `/api/v1/runtime/status` 查看。
