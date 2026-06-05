# 接口联调说明

## 启动顺序

### 一键启动

Windows 下优先使用项目脚本：

```powershell
scripts\1_check-env.cmd
scripts\2_start-dev.cmd
```

`2_start-dev.cmd` 会先调用 `start-database.cmd` 启动 Docker MySQL，再分别打开后端和前端命令行窗口。前端首次运行时，如果 `frontend/node_modules` 不存在，脚本会自动执行 `npm install --registry=https://registry.npmmirror.com`。

停止服务：

```powershell
scripts\3_stop-dev.cmd
```

停止脚本只终止 `.run/backend.pid` 和 `.run/frontend.pid` 中记录的启动窗口，不会按端口误杀其他 Java 或 Node 进程。

### 手动启动

1. 启动数据库：

```powershell
scripts\start-database.cmd
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
http://localhost:5173
```

5. 如需验证真实算法链路，另开窗口启动算法服务：

```powershell
cd algorithm
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

算法服务未启动时，后端上传接口会返回 `lowConfidence=true` 和空候选结果，`message` 中会说明原因，不再伪造演示 Top-5。

## 后端接口

| 接口 | 验证方式 | 预期结果 |
| --- | --- | --- |
| `GET /api/health` | 浏览器或 REST Client | 返回 `status=ok` |
| `GET /api/landmarks` | REST Client | 返回 L01-L10 地标列表 |
| `GET /api/landmarks/1` | REST Client | 返回图书馆详情 |
| `POST /api/search/upload` | 上传 JPG、PNG 或 WebP | 算法服务可用时返回 `searchRecordId` 和候选结果；算法服务不可用时返回空结果和明确提示 |
| `POST /api/auth/register` | 提交用户名、密码和选填邮箱 | 返回普通用户身份和 token，用户名唯一，密码至少 8 位，密码哈希入库 |
| `POST /api/auth/login` | 提交用户名和密码 | 普通用户返回主页面身份和 token；`admin/admin` 返回管理员身份并进入后台 |
| `POST /api/feedback` | 提交 JSON | 返回 `feedbackId` 和 `pending` 状态 |

## 当前边界

- 后端已连接 MySQL 基础库，当前真实使用 `landmark` 表读取 L01-L10 地标元数据。
- 图片上传保存到运行目录的 `uploads/`，该目录不进入 Git。
- 检索结果优先来自算法服务 `/api/v1/search`，后端按 `landmarkCode` 补齐 L01-L10 地标展示字段。
- `search_record` 和 `feedback` 表已接入持久化；登录用户的检索和反馈通过 Bearer token 解析 `userId`，未登录时记录本地持久化 `guestId`。
- 地标样本图片由成员按采集规范手动采集并整理到本地 `datasets/landmarks/`，原图仍按 `.gitignore` 排除，不直接提交 Git。
- 前端 dev server 通过 Vite proxy 访问后端接口，默认端口为 5173。

## 常见问题

| 问题 | 处理方式 |
| --- | --- |
| `mvn` 不可用 | 先运行 `scripts\1_check-env.cmd`，确认 Maven 已进入系统 PATH |
| `npm` 不可用 | 安装 Node.js LTS，并重新打开命令行窗口 |
| `docker` 不可用 | 安装 Docker Desktop，并确认 `docker --version` 能正常输出 |
| 后端启动时数据库连接失败 | 先运行 `scripts\start-database.cmd`，确认 MySQL 容器健康 |
| 8080 或 5173 端口占用 | 先运行 `scripts\3_stop-dev.cmd`；若仍占用，说明可能是手动启动的服务，需要关闭对应窗口 |
| 前端页面打开但接口失败 | 确认后端窗口仍在运行，并访问 `http://localhost:8080/api/health` |
| 上传图片后没有候选结果 | 检查算法服务是否已启动，或访问 `http://localhost:8000/api/v1/health`；样本不足也会影响真实候选结果 |
| 找不到样本图片 | 查看 `datasets/landmarks/sample_inventory.md`，缺样本的 L02、L06、L07 等地标需要继续补采 |
