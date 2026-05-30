# CampusLens 本地脚本说明

这些脚本面向 Windows 本地开发，统一放在项目根目录下运行。

| 脚本 | 使用时机 |
| --- | --- |
| `1_check-env.cmd` | 开发前检查 Java、Maven、Node、npm、Docker、Python 3.10、算法虚拟环境、模型文件和 `.env` 是否可用。 |
| `2_start-dev.cmd` | 一键启动本地联调环境：后端、前端、算法服务；默认后端使用 H2，不启动 MySQL。页面访问 `http://localhost:5173`。 |
| `3_stop-dev.cmd` | 停止由本项目脚本启动的后端、前端和算法服务窗口；不会扫描端口或结束手动启动的进程。 |
| `start-database.cmd` | 只启动 MySQL。优先使用 Windows Docker，找不到时尝试 WSL Docker；会等待容器 healthy、执行 `SELECT 1`，WSL 模式还会确认 Windows 能连到 WSL IP 的 3306 端口。 |
| `start-backend.cmd` | 只启动 Spring Boot 后端，默认使用 `demo` profile 的本地 H2 内存库；如果设置为 `mysql` profile，会先自动检查/启动 MySQL。健康检查地址为 `http://localhost:8080/api/health`。 |
| `start-frontend.cmd` | 只启动 Vue 前端，默认地址为 `http://localhost:5173`。 |
| `start-algorithm.cmd` | 只启动 FastAPI 算法服务，健康检查地址为 `http://localhost:8000/api/v1/health`。 |
| `stop-algorithm.cmd` | 只停止由 `start-algorithm.cmd` 启动的算法服务。 |

常用流程：

```cmd
scripts\1_check-env.cmd
scripts\2_start-dev.cmd
```

结束联调：

```cmd
scripts\3_stop-dev.cmd
```

算法服务依赖本地文件：

- `algorithm\.venv\Scripts\python.exe`
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

后端脚本默认使用 `demo` profile 的本地 H2 内存库，适合页面联调和演示。如果必须连接 MySQL，可在同一个命令行中执行：

```cmd
set CAMPUSLENS_BACKEND_PROFILE=mysql
scripts\start-backend.cmd
```

如果希望 `2_start-dev.cmd` 也使用 MySQL：

```cmd
set CAMPUSLENS_BACKEND_PROFILE=mysql
scripts\2_start-dev.cmd
```
