# CampusLens 前端

基于 Vue 3 / Vite，提供地标检索、地图、反馈、检索历史、账号、社区及管理页面。

## 启动与构建

完整服务使用根目录 `scripts/start.cmd`。单独调试前端前，先通过统一启动脚本生成 `certs/campuslens-dev.p12`，然后在本目录执行：

```powershell
npm install
npm run dev
```

默认访问地址为 **https://localhost:5173**。构建静态资源：

```powershell
npm run build
```

## 页面与交互

| 页面 | 功能 |
| --- | --- |
| 识图 | 图片上传、普通 / SAR 模式、任务状态及 Top-5 候选 |
| 地标与地图 | 地标详情、代表图和静态地图定位 |
| 反馈 | 结果确认、正确地标与补充说明 |
| 历史 | 登录用户的检索记录和候选快照 |
| 校园社区 | 打卡、照片展示、回复与点赞 |
| 账号 | 登录注册、邮箱、头像与密码管理 |
| 管理 | 地标、反馈、算法运行状态及索引重建 |

## 数据访问

前端通过 Vite 同源代理访问 Spring Boot 的 `/api` 与 `/uploads`。算法服务由后端访问。

图片上传后获取任务编号，再轮询 `GET /api/search/jobs/{jobId}`。地标资料从 `GET /api/landmarks` 加载，候选来自检索终态；加载失败时展示错误或空态。游客身份由后端分配并在浏览器持久化。

详见[前端设计](../docs/frontend.md)与[接口契约](../docs/02_api_contract.md)。
