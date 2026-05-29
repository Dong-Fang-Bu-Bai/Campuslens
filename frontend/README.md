# CampusLens Frontend

Vue 3 + Vite 前端工程，用于第二周 M4 检索结果展示、地图导览和反馈入口联调。

## 启动

```powershell
npm install
npm run dev
```

默认地址：

```text
http://localhost:5173
```

## 页面结构

| 页面区域 | 对应模块 | 说明 |
| --- | --- | --- |
| 图片上传 | M1 | 选择图片并调用 `POST /api/search/upload` |
| Top-5 结果 | M1 / M4 | 展示地标名称、置信度评分、简介和反馈入口 |
| 地标详情 | M2 / M4 | 展示简介、位置、地图坐标和样本要求 |
| 静态地图 | M4 | 使用 `public/campus-map.png` 显示 L01-L10 标注点 |
| 反馈纠错 | M5 | 提交正确、错误或不确定反馈 |

## 当前实现边界

页面会优先请求后端接口；若后端暂未启动，地标列表使用前端内置演示数据，保证原型可单独预览。第二周上传检索结果读取后端 `SearchResponse`，展示 `searchRecordId`、低置信度提示、Top-5 候选地标、`confidenceLevel` 和 `mahalanobisDistance`。反馈页会沿用当前 `searchRecordId` 和候选地标 ID 提交 `correct`、`wrong`、`uncertain` 三类反馈，审核与统计留到后续迭代。

## 构建

```powershell
npm run build
```
