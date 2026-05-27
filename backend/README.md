# CampusLens Backend

Spring Boot 后端工程，用于第二周 M1 上传检索主流程、M2 地标信息补齐和 M5 反馈入口联调。

## 技术栈

- Java 17+
- Spring Boot 3.3.6
- Maven 3.9+

## 启动

```powershell
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

健康检查：

```text
GET http://localhost:8080/api/health
```

## 已提供接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/health` | 后端健康检查 |
| `GET /api/landmarks` | 获取首批 L01-L10 地标列表 |
| `GET /api/landmarks/{id}` | 获取地标详情与地图坐标 |
| `POST /api/search/upload` | 上传图片，调用算法服务并返回 Top-5 地标结果 |
| `POST /api/feedback` | 提交正确、错误或不确定反馈 |

## 当前实现边界

当前版本服务于第二周细化阶段 V1 主流程联调。`SearchService` 会优先调用 `campuslens.algorithm.base-url` 配置的算法服务，默认地址为 `http://localhost:8000/api/v1/search`，再按 `landmarkCode` 补齐后端地标名称、简介、代表图和地图坐标。若算法服务暂不可用，接口返回低置信度本地候选结果，并在 `message` 中说明原因，便于前端保持可演示状态。

后端 MySQL 持久化暂不作为第二周重点，当前仍以内存数据完成地标补齐、检索记录编号和反馈 pending 响应。`POST /api/feedback` 已固定 `correct`、`wrong`、`uncertain` 三类反馈口径，其中 `correct` 和 `wrong` 需要提供 `predictedLandmarkId`，`wrong` 还需要提供 `confirmedLandmarkId`。

上传图片会保存到 `uploads/yyyyMMdd/`，该目录已由 `.gitignore` 排除。

## 验证

```powershell
mvn test
```
