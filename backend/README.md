# CampusLens Backend

Spring Boot 后端工程，负责认证、游客身份、异步检索任务、反馈闭环、社区数据、索引重建协调以及主备算法实例故障转移。

## 技术栈

- Java 17+
- Spring Boot 3.3.6
- Maven 3.9+

## 启动

正常启动流程默认使用 MySQL。推荐在项目根目录运行：

```powershell
scripts\start.cmd
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
| `POST /api/guests` | 按浏览器客户端令牌幂等分配递增游客编号 |
| `POST /api/search/upload` | 提交异步检索，返回 `202` 和任务标识 |
| `GET /api/search/jobs/{jobId}` | 查询任务状态、错误信息和 Top-5 终态结果 |
| `POST /api/feedback` | 提交正确、错误或不确定反馈 |
| `GET /api/admin/algorithm/runtime` | 管理员查看主算法实例运行、SAR 和索引状态 |
| `POST /api/admin/index/rebuild` | 管理员提交索引重建任务，返回 `202` |
| `GET /api/admin/index/rebuild/{jobId}` | 管理员查询索引重建任务状态 |

## 当前实现边界

- MySQL 保存地标、用户、游客身份、检索任务、反馈、校正样本和索引重建状态；Redis 负责异步任务准入、分发、领取和延迟重试。
- 检索任务提交后返回 `202`，客户端通过任务接口轮询终态。算法调用固定主实例 `8000` 优先，在连接失败、超时或 5xx 时回退备用实例 `8001`。
- `searchRecordId`、`feedbackId` 和 `guest#number` 均为数据库持久化编号。游客编号只能通过 `POST /api/guests` 分配。
- 管理员采纳反馈后生成校正样本，索引重建由主算法实例执行并通过版本文件原子切换。
- `demo` profile 的 H2 内存库仅用于临时调试；日常联调和验收使用 MySQL、Redis 与 Flyway。

对外接口使用 Java/JSON 小驼峰命名，例如 `jobId`、`searchRecordId`。算法服务内部 FastAPI 路径中的 `{job_id}` 只是在算法端生成文档时使用的路径参数名。

上传图片会保存到 `uploads/yyyyMMdd/`，该目录已由 `.gitignore` 排除。

## 验证

```powershell
mvn test
```
