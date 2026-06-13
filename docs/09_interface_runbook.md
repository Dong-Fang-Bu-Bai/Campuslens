# 接口联调说明

## 首次依赖安装

算法依赖不会由 `start.cmd` 自动安装。Windows + NVIDIA GPU 推荐执行：

```powershell
Copy-Item algorithm\.env.example algorithm\.env
algorithm\create_gpu_env.bat
```

已有 Python 环境时，设置 `CAMPUSLENS_ALGORITHM_PYTHON` 后执行 `algorithm\install_gpu.bat` 或 `algorithm\install_cpu.bat`。`requirements.txt` 仅包含公共依赖，必须与 CPU/GPU requirements 和测试 requirements 组合使用。

同时准备 `algorithm/models/dinov2_model.pth`。前端依赖在首次统一启动时自动安装，Maven 依赖由后端启动命令自动解析。

## 启动顺序

### 一键启动

Windows 下优先使用项目脚本：

```powershell
scripts\start.cmd
```

`start.cmd` 会完成环境检查，启动 Docker Desktop、MySQL、Redis、后端、双算法实例以及单个 HTTPS 前端。前端首次运行时，如果 `frontend/node_modules` 不存在，脚本会自动执行 `npm install --registry=https://registry.npmmirror.com`。

停止服务：

```powershell
scripts\stop.cmd
```

停止脚本按本项目固定端口与 PID 文件清理服务，并停止 MySQL/Redis 容器；Docker 镜像和命名数据卷会保留。

### 手动启动

1. 启动数据库：

```powershell
docker compose up -d mysql redis
```

2. 启动后端：

```powershell
cd backend
mvn spring-boot:run
```

3. 启动前端：

```powershell
cd frontend
npm install
npm run dev
```

4. 浏览器访问：

```text
https://localhost:5173
```

5. 如需手动验证真实算法链路，分别启动主备算法实例：

```powershell
cd algorithm
$env:PORT = "8000"
$env:INSTANCE_ID = "algorithm-primary"
$env:INSTANCE_ROLE = "primary"
& $env:CAMPUSLENS_ALGORITHM_PYTHON app\main.py

# 另一个 PowerShell 窗口
$env:PORT = "8001"
$env:INSTANCE_ID = "algorithm-secondary"
$env:INSTANCE_ROLE = "secondary"
& $env:CAMPUSLENS_ALGORITHM_PYTHON app\main.py
```

算法服务全部不可用时，上传提交仍返回 `202` 和检索任务编号；任务按退避策略重试，最终进入 `failed` 并返回 `errorCode=algorithm_unavailable` 和明确错误信息，不会伪造 Top-5。

## 后端接口

| 接口 | 验证方式 | 预期结果 |
| --- | --- | --- |
| `GET /api/health` | 浏览器或 REST Client | 返回 `status=ok` |
| `GET /api/landmarks` | REST Client | 返回 L01-L10 地标列表 |
| `GET /api/landmarks/1` | REST Client | 返回图书馆详情 |
| `POST /api/guests` | 提交浏览器持久化 `clientToken` | 同一令牌重复返回同一 `guestId`，不同令牌按 `guest#1` 起递增 |
| `POST /api/search/upload` | 上传 JPG、PNG 或 WebP | 返回 `202`、`searchRecordId` 和任务状态，客户端继续轮询结果接口 |
| `GET /api/search/jobs/{jobId}` | 携带 Bearer token 或 `X-Search-Job-Token` | 返回 queued/processing 或包含 Top-5 的终态结果 |
| `POST /api/auth/register` | 提交用户名、密码和选填邮箱 | 返回普通用户身份和 token，用户名唯一，密码至少 8 位，密码哈希入库 |
| `POST /api/auth/login` | 提交用户名和密码 | 普通用户返回主页面身份和 token；`admin/admin` 返回管理员身份并进入后台 |
| `POST /api/feedback` | 提交 JSON | 返回 `feedbackId` 和 `pending` 状态 |
| `GET /api/admin/algorithm/runtime` | 管理员 Bearer token | 返回主实例、SAR、活动索引和最近重建任务 |
| `POST /api/admin/index/rebuild` | 管理员 Bearer token | 返回 `202` 和 `rebuildJobId` |
| `GET /api/admin/index/rebuild/{jobId}` | 管理员 Bearer token | 返回 building/switching/completed/failed 状态 |

## 当前边界

- 后端已连接 MySQL 基础库，当前真实使用 `landmark` 表读取 L01-L10 地标元数据。
- 图片上传保存到运行目录的 `uploads/`，该目录不进入 Git。
- 正式异步消费者调用算法服务 `/api/v1/search/batch`，单图 `/api/v1/search` 仅用于诊断；后端按 `landmarkCode` 补齐 L01-L10 地标展示字段。
- `search_record` 和 `feedback` 表已接入持久化；登录用户通过 Bearer token 解析 `userId`，未登录浏览器通过 `/api/guests` 获取并持久化数据库分配的 `guestId`。
- 地标样本图片由成员按采集规范手动采集并整理到本地 `datasets/landmarks/`，原图仍按 `.gitignore` 排除，不直接提交 Git。
- 前端 dev server 通过 Vite proxy 访问后端接口，默认端口为 5173。

## 常见问题

| 问题 | 处理方式 |
| --- | --- |
| `mvn` 不可用 | 运行 `scripts\start.cmd` 查看统一环境检查结果 |
| `npm` 不可用 | 安装 Node.js LTS，并重新打开命令行窗口 |
| `docker` 不可用 | 安装 Docker Desktop，并确认 `docker --version` 能正常输出 |
| 后端启动时数据库连接失败 | 运行 `scripts\verify.cmd`，确认 MySQL 与 Redis 容器健康 |
| 8080、5173、8000 或 8001 端口占用 | 先运行 `scripts\stop.cmd`，再重新启动 |
| 前端页面打开但接口失败 | 确认后端窗口仍在运行，并访问 `http://localhost:8080/api/health` |
| 上传图片后没有候选结果 | 检查算法服务是否已启动，或访问 `http://localhost:8000/api/v1/health`；样本不足也会影响真实候选结果 |
| Windows 终端打印状态时出现编码错误 | 使用统一启动脚本，或手工设置 `$env:PYTHONUTF8 = "1"`；当前运行代码已用 `[OK]` 等 ASCII 前缀替代 emoji |
| 找不到样本图片 | 查看 `datasets/landmarks/sample_inventory.md`，缺样本的 L02、L06、L07 等地标需要继续补采 |
