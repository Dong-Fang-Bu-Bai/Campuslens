# 安装与运行

以下命令在项目根目录执行，算法依赖安装命令除外。

## 环境要求

| 组件 | 要求 |
| --- | --- |
| 操作系统 | 统一启动脚本面向 Windows |
| Java | JDK 17，`java` 和 `keytool` 可从 PATH 访问 |
| 构建工具 | Maven、Node.js、npm |
| 基础服务 | Docker Desktop，能够执行 `docker info` |
| 算法环境 | Python 3.10；GPU 模式需兼容 PyTorch CUDA 12.1 的 NVIDIA 驱动 |
| 外部资源 | DINOv2 ViT-B/14 权重和按地标分类的图片样本 |

启动 Docker Desktop 后再运行项目脚本。Java、Maven、Node.js 与 Docker CLI 需在 PATH 中可用。

## 获取与配置

```powershell
git clone https://github.com/Dong-Fang-Bu-Bai/Campuslens.git
cd Campuslens
Copy-Item .env.example .env
Copy-Item algorithm\.env.example algorithm\.env
```

根目录 `.env` 供 Docker Compose 读取；算法目录的 `.env` 供 FastAPI 读取。后端配置使用 `CAMPUSLENS_*` 环境变量，见[后端说明](../backend/README.md)。

## Python 环境

创建独立环境并指定解释器：

```powershell
python -m venv algorithm\.venv
$env:CAMPUSLENS_ALGORITHM_PYTHON = (Resolve-Path algorithm\.venv\Scripts\python.exe).Path
Push-Location algorithm
& $env:CAMPUSLENS_ALGORITHM_PYTHON -m pip install -r requirements-gpu.txt -r requirements-test.txt
Pop-Location
```

无 NVIDIA GPU 时，将 `requirements-gpu.txt` 替换为 `requirements-cpu.txt`。设备选择与驱动检查见[CPU / GPU 配置](../algorithm/GPU_SUPPORT.md)。启动脚本不会自动安装算法 Python 依赖。

`CAMPUSLENS_ALGORITHM_PYTHON` 的设置作用于当前 PowerShell 会话；重新打开终端后需再次设置，或使用项目 `algorithm/.venv` 的默认解释器。

## 模型与样本

从 [DINOv2 官方仓库](https://github.com/facebookresearch/dinov2)准备 **ViT-B/14 的 PyTorch 权重**，保存为：

```text
algorithm/models/dinov2_model.pth
```

使用其他位置时，设置 `algorithm/.env` 中的 `DINO_MODEL_PATH`。模型结构与权重需兼容本项目的 ViT-B/14 加载器。

将地标图片放入 `datasets/landmarks/` 的分类目录，详见[数据集规范](04_dataset_rules.md)。权重、样本原图和索引由本地资源提供，不随 Git 仓库分发。

## 启动

```powershell
scripts\start.cmd
```

脚本启动 MySQL、Redis、后端、主备算法实例和 HTTPS 前端。前端依赖缺失时自动执行 npm 安装，后端通过 Maven 解析依赖；数据库由 Flyway 应用迁移与基础种子数据。

| 服务 | 地址 |
| --- | --- |
| 前端 | https://localhost:5173 |
| 后端健康检查 | http://localhost:8080/api/health |
| 主算法实例 | http://localhost:8000/api/v1/health |
| 备用算法实例 | http://localhost:8001/api/v1/health |

前端使用本地开发证书，并同源代理 `/api` 与 `/uploads`。浏览器首次访问需信任该证书。局域网访问时，将 localhost 替换为服务器地址；IP 变化后重启脚本以更新证书。

## 索引初始化

1. 使用本地演示管理员账号 `admin/admin` 登录。
2. 在管理页面触发索引重建，或调用 `POST /api/admin/index/rebuild`。
3. 轮询 `GET /api/admin/index/rebuild/{jobId}`，等待 `completed`。
4. 返回检索页面，上传 JPG、PNG 或 WebP 图片。

检索按异步任务执行：提交返回 `202` 后轮询任务接口。算法不可用时按策略重试，最终返回失败原因。

## 检查与停止

```powershell
scripts\verify.cmd
scripts\stop.cmd
```

`verify.cmd` 启动服务并检查各端点。`stop.cmd` 停止项目固定端口和 PID 文件对应的服务及容器，保留镜像与命名数据卷。自动化与并发测试见[测试说明](testing.md)。

## 常见问题

| 问题 | 处理 |
| --- | --- |
| 缺少命令 | 检查 Java、Maven、Node.js、Docker 和 keytool 的 PATH |
| 数据库无法连接 | 确认 Docker Desktop 运行，MySQL 容器健康，连接参数一致 |
| 算法无法启动 | 检查 Python 环境、模型路径、依赖和终端日志 |
| 无候选结果 | 检查样本目录和索引重建状态 |
| CUDA 不可用 | 检查驱动与 PyTorch；也可设置 DEVICE=cpu |
| 前端接口失败 | 检查后端健康端点和 Vite 代理 |
| HTTPS 证书不匹配 | 重新运行启动脚本，使用证书包含的主机地址 |
| 密码找回邮件失败 | 配置 SMTP、发件账号及授权码，见后端说明 |

部署到共享环境前应设置自己的数据库凭据、任务令牌密钥及管理员密码。
