# CampusLens Backend

Spring Boot 后端工程，用于第二周 M1 上传检索主流程、M2 地标信息补齐和 M5 反馈入口联调。

## 技术栈

- Java 17+
- Spring Boot 3.3.6
- Maven 3.9+

## 启动

正常启动流程默认使用 MySQL。推荐在项目根目录运行：

```powershell
scripts\2_start-dev.cmd
```

只启动后端时，脚本也会先检查/启动 MySQL：

```powershell
scripts\start-backend.cmd
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

当前版本服务于第二周细化阶段 V1 主流程联调。`LandmarkService` 已通过 Spring JDBC 从 MySQL `landmark` 表读取 L01-L10 地标基础数据，不再使用后端硬编码地标列表。`SearchService` 会优先调用 `campuslens.algorithm.base-url` 配置的算法服务，默认地址为 `http://localhost:8000/api/v1/search`，再按 `landmarkCode` 从数据库补齐后端地标名称、简介、代表图和地图坐标。若算法服务暂不可用，接口返回空候选结果、`lowConfidence=true` 和明确 `message`，不再伪造演示 Top-5。

当前 V1 真实使用数据库的范围仅限基础地标元数据读取。`searchRecordId`、`feedbackId` 仍为运行期临时编号，`search_record` 和 `feedback` 持久化留到后续迭代。`POST /api/feedback` 已固定 `correct`、`wrong`、`uncertain` 三类反馈口径，其中 `correct` 和 `wrong` 需要提供 `predictedLandmarkId`，`wrong` 还需要提供 `confirmedLandmarkId`。

上传图片会保存到 `uploads/yyyyMMdd/`，该目录已由 `.gitignore` 排除。

`demo` profile 的 H2 内存库仅用于临时脱离 MySQL 的单机调试。正常开发、联调和验收默认使用 `mysql` profile。

## 验证

```powershell
mvn test
```
