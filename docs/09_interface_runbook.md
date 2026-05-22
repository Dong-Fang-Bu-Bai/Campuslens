# 接口联调说明

## 启动顺序

### 一键启动

Windows 下优先使用项目脚本：

```powershell
scripts\check-env.cmd
scripts\start-dev.cmd
```

`start-dev.cmd` 会分别打开后端和前端命令行窗口。前端首次运行时，如果 `frontend/node_modules` 不存在，脚本会自动执行 `npm install --registry=https://registry.npmmirror.com`。

停止服务：

```powershell
scripts\stop-dev.cmd
```

停止脚本只终止 `.run/backend.pid` 和 `.run/frontend.pid` 中记录的启动窗口，不会按端口误杀其他 Java 或 Node 进程。

### 手动启动

1. 启动后端：

```powershell
cd backend
mvn spring-boot:run
```

2. 启动前端：

```powershell
cd frontend
npm install
npm run dev
```

3. 浏览器访问：

```text
http://localhost:5173
```

## 后端接口

| 接口 | 验证方式 | 预期结果 |
| --- | --- | --- |
| `GET /api/health` | 浏览器或 REST Client | 返回 `status=ok` |
| `GET /api/landmarks` | REST Client | 返回 L01-L10 地标列表 |
| `GET /api/landmarks/1` | REST Client | 返回图书馆详情 |
| `POST /api/search/upload` | 上传 JPG、PNG 或 WebP | 返回 `searchRecordId` 和 5 条候选结果 |
| `POST /api/feedback` | 提交 JSON | 返回 `feedbackId` 和 `pending` 状态 |

## 当前边界

- 后端最小工程暂不连接 MySQL。
- 图片上传保存到运行目录的 `uploads/`，该目录不进入 Git。
- 检索结果为演示数据，第二周替换为算法服务真实结果。
- 前端 dev server 通过 Vite proxy 访问后端接口，默认端口为 5173。

## 常见问题

| 问题 | 处理方式 |
| --- | --- |
| `mvn` 不可用 | 先运行 `scripts\check-env.cmd`，确认 Maven 已进入系统 PATH |
| `npm` 不可用 | 安装 Node.js LTS，并重新打开命令行窗口 |
| 8080 或 5173 端口占用 | 先运行 `scripts\stop-dev.cmd`；若仍占用，说明可能是手动启动的服务，需要关闭对应窗口 |
| 前端页面打开但接口失败 | 确认后端窗口仍在运行，并访问 `http://localhost:8080/api/health` |
| 上传图片后返回演示结果 | 当前为第一周初始阶段最小工程，第二周接入算法服务后替换为真实检索结果 |
