# CampusLens Frontend

Vue 3 + Vite 前端工程，覆盖图片检索、SAR 模式、地图导览、反馈、检索历史、游客社区、登录注册和管理员后台。

## 启动

日常使用项目根目录的 `scripts\start.cmd`。单独调试前端时先确保 `frontend/certs/campuslens-dev.p12` 已由统一启动脚本生成，再运行：

```powershell
npm install --registry=https://registry.npmmirror.com
npm run dev
```

默认地址：

```text
https://localhost:5173
```

开发服务器固定使用 `frontend/certs/campuslens-dev.p12` 自签名证书。首次访问需要在浏览器中确认继续访问。

## 页面结构

| 页面区域 | 对应模块 | 说明 |
| --- | --- | --- |
| 图片上传 | M1 | 调用 `POST /api/search/upload` 提交任务，再轮询 `GET /api/search/jobs/{jobId}` |
| Top-5 结果 | M1 / M4 | 展示地标名称、经验匹配分、简介和反馈入口 |
| 地标详情 | M2 / M4 | 展示简介、位置、地图坐标和样本要求 |
| 静态地图 | M4 | 使用 `public/campus-map.png` 显示 L01-L10 标注点 |
| 反馈纠错 | M5 | 提交正确、错误或不确定反馈 |

## 当前实现边界

页面通过 HTTPS Vite 同源代理访问 `/api` 和 `/uploads`。游客浏览器先调用 `/api/guests` 获取持久编号；检索采用异步提交与轮询，普通和 SAR 模式共用任务流程。管理员页面可查看检索、反馈、算法运行状态并触发索引重建。

前端只调用 Spring Boot 的 `/api` 接口，不直接访问算法服务的 `8000/8001` 端口。管理员运行状态和索引重建分别使用 `/api/admin/algorithm/runtime`、`/api/admin/index/rebuild` 与 `/api/admin/index/rebuild/{jobId}`。

## 构建

```powershell
npm run build
```
