# CampusLens 本地脚本说明

这些脚本面向 Windows 本地开发，统一放在项目根目录下运行。

| 脚本 | 使用时机 |
| --- | --- |
| `1_check-env.cmd` | 开发前检查 Java、Maven、Node、npm、Docker、算法 Python、模型文件和 `.env` 是否可用；算法 Python 按 `CAMPUSLENS_ALGORITHM_PYTHON`、D 盘 Conda 环境、项目 `.venv` 的顺序选择。 |
| `2_start-dev.cmd` | 一键启动本地联调环境：默认先用本机 Docker Desktop 启动 MySQL，再启动后端、前端、算法服务。页面访问 `http://localhost:5173`。 |
| `3_stop-dev.cmd` | 停止由本项目脚本启动的后端、前端和算法服务窗口；不会扫描端口或结束手动启动的进程。 |
| `4_verify-dev.cmd` | 使用 MySQL profile 启动/检查 MySQL、后端、前端和算法服务，并等待三个 HTTP 健康地址成功响应。 |
| `start-database.cmd` | 只启动 MySQL。优先使用 Windows Docker Desktop，内置识别 `D:\Tools\Docker\Docker\resources\bin`，daemon 未就绪时会尝试启动 Docker Desktop；找不到 Windows Docker 时再尝试 WSL Docker。脚本会等待容器 healthy、执行 `SELECT 1`，WSL 模式还会确认 Windows 能连到 WSL IP 的 3306 端口。 |
| `start-backend.cmd` | 只启动 Spring Boot 后端，默认使用 `mysql` profile，并会先自动检查/启动 MySQL。健康检查地址为 `http://localhost:8080/api/health`。 |
| `start-frontend.cmd` | 只启动 Vue 前端，默认地址为 `http://localhost:5173`。 |
| `start-algorithm.cmd` | 只启动 FastAPI 算法服务，健康检查地址为 `http://localhost:8000/api/v1/health`。 |
| `stop-algorithm.cmd` | 只停止由 `start-algorithm.cmd` 启动的算法服务。 |

常用流程：

```cmd
scripts\1_check-env.cmd
scripts\2_start-dev.cmd
```

MySQL 全链路验收：

```cmd
scripts\4_verify-dev.cmd
```

结束联调：

```cmd
scripts\3_stop-dev.cmd
```

算法服务依赖本地环境和文件：

- `CAMPUSLENS_ALGORITHM_PYTHON` 指定的解释器，未设置时默认使用 `D:\AnaConda\envs\campuslens-gpu\python.exe`
- `algorithm\.venv\Scripts\python.exe` 仅作为迁移期回退
- `algorithm\.env`
- `algorithm\models\dinov2_model.pth`
- `algorithm\data\faiss_index\landmark_index.faiss`
- `algorithm\data\faiss_index\metadata.pkl`
- `algorithm\data\faiss_index\landmark_stats.pkl`

这些算法运行文件只保留在本机，不提交到 GitHub。模型文件需要下载到本地；`faiss_index` 下的索引和统计参数由 rebuild 接口根据本地数据集生成。

如果 `landmark_stats.pkl` 不存在或索引状态不是 `ready`，先启动算法服务，再调用：

```cmd
curl.exe -X POST http://localhost:8000/api/v1/index/rebuild
curl.exe http://localhost:8000/api/v1/index/stats
```

如果数据库通过 WSL Docker 启动，`start-database.cmd` 会把 Windows 后端可访问的 WSL 数据库地址写入 `.run\database-env.cmd`，`start-backend.cmd` 会自动读取它。WSL 的 IP 可能在 `wsl --shutdown` 或重启后变化，所以使用 MySQL profile 前建议让脚本重新跑一遍，不要长期复用旧的 `.run\database-env.cmd`。

本机推荐使用 Windows 原生 Docker Desktop。当前已验证的本机路径为：

```text
Docker Desktop: D:\Tools\Docker\Docker
Docker CLI:     D:\Tools\Docker\Docker\resources\bin
Docker data:    D:\DockerData\wsl
```

如果新终端暂时找不到 `docker`，脚本会自动把上述 CLI 路径加入当前脚本进程的 `PATH`，不需要手工修改系统环境变量。

后端脚本默认使用 `mysql` profile，正常启动流程会包含 MySQL。默认流程：

```cmd
scripts\2_start-dev.cmd
```

只有在临时脱离 MySQL 调试时，才显式切到 `demo` profile：

```cmd
set CAMPUSLENS_BACKEND_PROFILE=demo
scripts\2_start-dev.cmd
```
