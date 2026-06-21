# 接口契约说明

正式接口结构以 `api/openapi-campuslens.yaml` 为准。本文件用于解释接口边界和调用顺序。

## 调用关系

用户端调用 Spring Boot 后端接口；后端负责文件校验、任务持久化、Redis 排队和结果组装。独立任务消费者按小批次调用 Python FastAPI 算法服务，MySQL 保存权威状态，Redis 只负责分发和容量准入。

```text
Vue 前端 -> Spring Boot 提交接口 -> MySQL + Redis ready/processing/delayed
                                   |
                                   v
                         后端任务消费者 -> FastAPI GPU
```

## 用户端核心接口

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `GET /api/health` | 后端健康检查 | M1 马启凡 |
| `POST /api/guests` | 携带浏览器持久化 `clientToken`，幂等获取数据库分配的 `guest#number` | M1 / M4 |
| `POST /api/search/upload` | 携带 `Idempotency-Key` 提交异步检索，返回 `202`、`jobId`、`jobToken` 和 `searchRecordId` | M1 马启凡，依赖 M3 |
| `GET /api/search/jobs/{jobId}` | 登录用户按 Bearer token、游客按 `X-Search-Job-Token` 查询任务状态和 Top-5 | M1 马启凡，依赖 M3 |
| `GET /api/landmarks` | 获取地标列表 | M2 叶炳良 |
| `GET /api/landmarks/{id}` | 获取地标详情 | M2 叶炳良，M4 洪传凯 |
| `POST /api/feedback` | 提交识别纠错反馈 | M5 庄子杰 |
| `POST /api/auth/register` | 普通用户注册，用户名唯一，密码至少 8 位，邮箱选填 | M1 / M4 |
| `POST /api/auth/login` | 用户登录；输入 `admin/admin` 时返回管理员身份并由前端自动进入后台 | M1 / M4 |
| `GET /api/me/account` | 登录用户或管理员查看自己的账号资料 | M1 / M4 |
| `PUT /api/me/account/email` | 登录用户或管理员更新或清除自己的邮箱 | M1 / M4 |
| `PUT /api/me/account/password` | 校验当前密码后更新密码，新密码至少 8 位且不能与当前密码相同 | M1 / M4 |
| `POST /api/me/account/avatar` | 上传裁剪后的 JPG/PNG 头像，最大 5 MB；服务端校验真实图片并替换旧头像 | M1 / M4 |
| `GET /api/me/search-records` | 登录用户查看自己的检索历史，返回上传图、最高候选、Top-5 快照和反馈状态 | M1 / M4 |
| `GET /api/check-ins` | 查询校园打卡留言，可按 `landmarkId` 过滤 | M2 / M4 / M5 |
| `POST /api/check-ins` | 从本人识图记录的 Top-5 候选发布打卡留言；每条检索记录限一次 | M2 / M4 / M5 |
| `GET /api/check-ins/{id}` | 查询单条校园动态及完整嵌套回复树 | M2 / M4 / M5 |
| `POST /api/check-ins/{id}/like` | 点赞或取消点赞打卡留言 | M4 / M5 |
| `POST /api/check-ins/{id}/replies` | 回复帖子或通过 `parentReplyId` 回复另一条评论 | M4 / M5 |
| `POST /api/check-in-replies/{id}/like` | 点赞或取消点赞回复 | M4 / M5 |

## 后台辅助接口

第三周 V2 提供后台入口。前端不再显示管理员专用登录框，统一通过右上角“登录/注册”进入认证页面。普通用户按注册/登录流程进入主页面；输入 `admin/admin` 时后端返回 `role=admin`、`token` 和管理员身份，前端自动进入后台。后台接口统一要求 `Authorization: Bearer <token>`，并在服务端校验管理员角色。密码字段以 PBKDF2 哈希形式落库，不再保存明文口令。

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `POST /api/admin/auth/login` | 管理员轻量登录兼容旧接口，前端不直接展示 | M1 / M4 |
| `GET /api/admin/search-records` | 查看最近检索记录，需管理员 token | M1 / M4 |
| `POST /api/admin/landmarks` | 新增地标，需管理员 token | M2 |
| `PUT /api/admin/landmarks/{id}` | 修改地标，需管理员 token | M2 |
| `POST /api/admin/landmarks/{id}/images` | 上传地标样本图片，需管理员 token | M2 |
| `GET /api/admin/algorithm/runtime` | 查看主算法实例的模型、SAR、索引和重建任务状态 | M3 / M4 |
| `POST /api/admin/index/rebuild` | 提交索引重建任务，返回 `202` 和 `rebuildJobId` | M3 |
| `GET /api/admin/index/rebuild/{jobId}` | 查询索引重建任务状态和发布版本 | M3 / M4 |
| `GET /api/admin/feedback` | 查看反馈记录，需管理员 token | M5 |
| `GET /api/admin/feedback/{id}` | 查看反馈详情，含上传图、Top-5 快照、算法建议和校正样本同步状态 | M5 / M4 |
| `POST /api/admin/feedback/{id}/status` | 将反馈状态更新为 `pending`、`accepted` 或 `ignored`，需管理员 token | M5 |

## 算法服务接口

算法服务是后端内部调用的 FastAPI 服务，默认地址为 `http://localhost:8000`，统一使用 `/api/v1` 前缀。

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `POST /api/v1/search` | 接收上传图片文件，返回 Top-5 候选地标 | M3 周子栋 |
| `POST /api/v1/search/batch` | 按上传顺序接收最多 2 张图片，逐项返回结果或可重试错误 | M3 周子栋 |
| `POST /api/v1/index/rebuild` | 主实例异步创建索引重建任务，返回 `202` 和 `rebuildJobId` | M3 周子栋 |
| `GET /api/v1/index/rebuild/{job_id}` | 查询算法端重建任务的 building/switching/completed/failed 状态 | M3 周子栋 |
| `GET /api/v1/runtime/status` | 查看当前实例、模型版本、SAR 状态和活动索引版本 | M3 周子栋 |
| `GET /api/v1/index/stats` | 查看当前统计参数状态、样本数量和维度 | M3 周子栋 |
| `GET /api/v1/health` | 算法服务健康检查 | M3 周子栋 |
| `POST /api/v1/adaptation/correction-samples` | multipart 接收管理员采纳后的校正图片与 JSON 元数据，完成可靠性门控，并在通过时构建候选模型和完整索引版本 | M3 周子栋 |

## 字段命名规则

- JSON 字段使用小驼峰：`landmarkId`、`imageUrl`、`searchRecordId`。
- 数据库字段使用下划线：`landmark_id`、`image_url`、`search_record_id`。
- 地标编号使用 `L01` 至 `L10`。
- 时间字段统一使用 ISO 8601 字符串，例如 `2026-05-18T10:00:00`。
- 游客编号仅由 `guest_identity` 自增主键生成。前端不得自行拼接编号，检索和社区接口会拒绝数据库中不存在的 `guestId`。
- 浏览器和后端 JSON 使用 `jobId`、`rebuildJobId` 等小驼峰字段。FastAPI 路径模板中的 `{job_id}` 是算法端函数参数名，不改变 JSON 字段命名。

## Top-5 返回规则

- 返回的是 Top-5 地标，不是 Top-5 图片。
- 每个地标根据样本特征估计均值向量和协方差矩阵，查询时计算马氏距离并换算为经验归一化匹配分。
- Top-5 按 `score` 从高到低排序；`score` 越高，表示查询图越接近该地标特征分布。该分数用于排序和展示区分度，不具备概率或统计置信度含义。
- 后端对外返回字段至少包含：`rank`、`landmarkId`、`landmarkCode`、`name`、`score`、`confidenceLevel`、`mahalanobisDistance`、`coverImageUrl`、`summary`、`locationText`、`mapX`、`mapY`。
- 算法服务内部返回字段至少包含：`rank`、`landmarkCode`、`landmarkName`、`score`、`confidenceLevel`、`mahalanobisDistance`。用户任务响应仅新增 `sarApplied`、`trustLevel`、`modelVersion`；后端另行保存 `sarMode`、基准模型版本、索引版本和 SAR 状态版本用于追溯。
- 服务级 `SAR_ENABLED` 默认开启，但请求字段 `sarMode` 默认关闭。仅 `sarMode=true` 的请求进入 SAR 流程；批量中的不同用户图片不会共享本次适配梯度。
- 管理员采纳样本由后端从受控 `uploads/` 目录读取并通过 multipart 转发。只有校正样本通过熵、候选匹配分和标签存在性检查后，算法服务才会生成候选模型，并使用同一候选模型重建完整 FAISS 索引。模型和索引验证成功后才切换活动版本。
- 如果算法返回的候选均为低匹配等级，终态为 `low_confidence`；算法或任务处理失败时终态为 `failed`，并返回 `errorCode` 和明确 `message`，不伪造演示 Top-5。
- 状态统一为 `queued -> processing -> success|low_confidence|failed`。任务最多尝试 3 次，使用 90 秒 lease；重试时间写入 MySQL `next_attempt_at`，退避为 2、5、15 秒，不阻塞消费者线程。
- Redis 使用活跃任务 Set 做容量准入，ready List 分发任务，processing ZSet 保存带凭证的领取记录，delayed ZSet 保存等待重试任务。终态确认后才释放容量。
- 每次新任务先写入未确认准入的 `search_record`，Redis 原子准入成功后写入 `queued_at`。Redis 不可用时返回 `503`；队列满时返回 `429` 和 `Retry-After: 5`，未准入记录不会伪装成已接收任务。
- `Idempotency-Key` 按登录用户或游客标识划分作用域，并与文件 SHA-256 共同保证幂等：同一所有者同键同文件返回原任务，同键不同文件返回 `409`；不同所有者可使用相同键。
- worker 完成、失败和重试更新均校验 `worker_id + attempt_count`。过期 worker 的晚到结果不能覆盖已由新 worker 恢复的任务。

## 图片上传规则

- 当前上传接口限制上传图片类型为 JPG、PNG、WebP。
- 单张图片大小上限为 8MB。
- 后端保存上传图后先返回任务编号，任务完成时由轮询接口返回 `uploadImageUrl` 和 Top-5。
- 正式任务消费者调用批量算法接口，并按 `landmarkCode` 补齐展示信息；单图算法接口保留用于诊断。
- 地标样本图片由成员手动采集并整理到本地数据目录；原图按 `.gitignore` 排除，不直接提交 Git。当前样本数量记录见 `datasets/landmarks/sample_inventory.md`。

## 反馈规则

| feedbackType | 含义 | confirmedLandmarkId |
| --- | --- | --- |
| `correct` | 用户确认系统识别正确 | 可与 `predictedLandmarkId` 一致 |
| `wrong` | 用户确认系统识别错误 | 必填，用于记录正确地标 |
| `uncertain` | 用户无法判断或结果不明确 | 可为空 |

`correct` 和 `wrong` 反馈必须带 `predictedLandmarkId`，用于关联本次 SearchResponse 中的预测地标；`wrong` 必须额外带 `confirmedLandmarkId`。

反馈提交前会校验任务已进入 `success` 或 `low_confidence` 终态，并校验 `predictedLandmarkId` 来自本次 Top-5。登录任务必须由同一 Bearer 用户提交反馈；游客任务必须携带与检索记录一致的 `guestId`。反馈写入后默认为 `pending`，后台可更新为 `accepted` 或 `ignored`。

第四周 V3 已扩展反馈采纳闭环：管理员将反馈更新为 `accepted` 后，后端校验上传原图位于受控 `uploads/` 目录，将图片复制到确认地标的 `pending_index/` 目录，并创建 `correction_sample` 记录，状态为 `pending_index`。管理员触发索引重建后，算法服务使用正式样本和待发布样本构建候选模型与完整索引；验证和原子发布成功后，后端将相关校正样本更新为 `published` 并记录 `publishedIndexVersion`。旧版 `sync_pending`、`synced`、`sync_failed` 仅作为历史数据兼容状态，不再表示当前主流程。

## 打卡留言规则

- 新打卡必须提交 `searchRecordId`、Top-5 内的 `landmarkId` 和留言；检索记录须归当前用户或游客所有，状态为 `success` 或 `low_confidence`，且每条记录只能发布一次。V16 前的无来源历史记录继续可读。
- `publishImage` 默认为 `false`；只有发布者主动开启时，列表响应才通过 `sourceImageUrl` 返回上传图片。
- 登录用户发布留言、点赞和回复时显示用户名；未登录游客使用由 `POST /api/guests` 分配且数据库中真实存在的 `guestId`。格式非法或不存在的游客编号会被拒绝。
- 留言和回复单条内容最长 500 字符。`parentReplyId` 为空时回复帖子，非空时必须指向当前帖子下可见的回复；服务端按父子关系返回任意层级回复树。
- `GET /api/check-ins` 只返回帖子摘要和计数，`replies` 为空；进入详情页后由 `GET /api/check-ins/{id}` 返回完整回复树，避免主列表加载全部讨论。
- 每条回复返回创建时间、点赞数、当前访问者点赞状态和直接子回复数；登录用户与持久化游客都可切换回复点赞。
- 后台内容删除能力作为后续扩展预留，当前实现用户端查询、发布、帖子/回复点赞和多级回复。

## 个人历史规则

`GET /api/me/search-records` 必须携带 Bearer token。服务端只按 token 解析出的 `userId` 查询 `search_record`，不会接受前端传入的用户编号。游客历史仅在前端本机当前会话中展示，不提供服务端跨设备查询。

## 数据库迁移口径

第三周 V2 引入 Flyway，迁移脚本位于 `database/migration/`。Spring Boot 启动时从该目录执行版本化迁移。Docker Compose 不再挂载 `schema.sql` 或独立种子脚本，避免与 Flyway 重复建表；这些文件仅保留作结构核对。Git 不同步本机 Docker MySQL volume 中的运行记录。

## 错误码口径

| HTTP 状态码 | 场景 |
| --- | --- |
| `400` | 参数错误、图片格式错误、图片过大 |
| `401` | 未登录或 token 缺失 |
| `403` | 已登录但不是管理员 |
| `404` | 地标不存在、检索记录不存在 |
| `409` | 数据冲突，例如幂等键对应不同图片 |
| `429` | Redis 队列达到容量上限，响应含 `Retry-After` |
| `503` | Redis 不可用，任务未被接受 |
| `500` | 后端内部错误 |
