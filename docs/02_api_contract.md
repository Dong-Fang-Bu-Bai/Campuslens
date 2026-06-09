# 接口契约说明

正式接口结构以 `api/openapi-campuslens.yaml` 为准。本文件用于解释接口边界和调用顺序。

## 调用关系

用户端调用 Spring Boot 后端接口；后端负责文件校验、业务记录和数据组装。图像特征提取与检索由后端调用 Python FastAPI 算法服务完成。第三周 V2 主流程已将上传接口接到算法服务，并通过 MySQL `landmark`、`search_record` 和 `feedback` 表保存可追溯记录。

```text
Vue 前端 -> Spring Boot 后端 -> Python FastAPI 算法服务
                  |
                  v
                MySQL + 图片目录 + 特征/统计参数文件
```

## 用户端核心接口

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `GET /api/health` | 后端健康检查 | M1 马启凡 |
| `POST /api/search/upload` | 上传图片并返回 Top-5 地标结果；登录用户身份由 Bearer token 解析，未登录时携带 `guestId` | M1 马启凡，依赖 M3 |
| `GET /api/landmarks` | 获取地标列表 | M2 叶炳良 |
| `GET /api/landmarks/{id}` | 获取地标详情 | M2 叶炳良，M4 洪传凯 |
| `POST /api/feedback` | 提交识别纠错反馈 | M5 庄子杰 |
| `POST /api/auth/register` | 普通用户注册，用户名唯一，密码至少 8 位，邮箱选填 | M1 / M4 |
| `POST /api/auth/login` | 用户登录；输入 `admin/admin` 时返回管理员身份并由前端自动进入后台 | M1 / M4 |
| `GET /api/me/search-records` | 登录用户查看自己的检索历史，返回上传图、最高候选、Top-5 快照和反馈状态 | M1 / M4 |
| `GET /api/check-ins` | 查询校园打卡留言，可按 `landmarkId` 过滤 | M2 / M4 / M5 |
| `POST /api/check-ins` | 发布打卡留言，登录用户显示用户名，游客显示 `guest#number` | M2 / M4 / M5 |
| `POST /api/check-ins/{id}/like` | 点赞或取消点赞打卡留言 | M4 / M5 |
| `POST /api/check-ins/{id}/replies` | 发表一级回复 | M4 / M5 |

## 后台辅助接口

第三周 V2 提供后台入口。前端不再显示管理员专用登录框，统一通过右上角“登录/注册”进入认证页面。普通用户按注册/登录流程进入主页面；输入 `admin/admin` 时后端返回 `role=admin`、`token` 和管理员身份，前端自动进入后台。后台接口统一要求 `Authorization: Bearer <token>`，并在服务端校验管理员角色。密码字段以 PBKDF2 哈希形式落库，不再保存明文口令。

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `POST /api/admin/auth/login` | 管理员轻量登录兼容旧接口，前端不直接展示 | M1 / M4 |
| `GET /api/admin/search-records` | 查看最近检索记录，需管理员 token | M1 / M4 |
| `POST /api/admin/landmarks` | 新增地标，需管理员 token | M2 |
| `PUT /api/admin/landmarks/{id}` | 修改地标，需管理员 token | M2 |
| `POST /api/admin/landmarks/{id}/images` | 上传地标样本图片，需管理员 token | M2 |
| `POST /api/admin/index/rebuild` | 重建地标统计参数，仍归 M3 实现 | M3 |
| `GET /api/admin/feedback` | 查看反馈记录，需管理员 token | M5 |
| `GET /api/admin/feedback/{id}` | 查看反馈详情，含上传图、Top-5 快照、算法建议和校正样本同步状态 | M5 / M4 |
| `POST /api/admin/feedback/{id}/status` | 将反馈状态更新为 `pending`、`accepted` 或 `ignored`，需管理员 token | M5 |

## 算法服务接口

算法服务是后端内部调用的 FastAPI 服务，默认地址为 `http://localhost:8000`，统一使用 `/api/v1` 前缀。

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `POST /api/v1/search` | 接收上传图片文件，返回 Top-5 候选地标 | M3 周子栋 |
| `POST /api/v1/index/rebuild` | 根据样本库重建地标统计参数 | M3 周子栋 |
| `GET /api/v1/index/stats` | 查看当前统计参数状态、样本数量和维度 | M3 周子栋 |
| `GET /api/v1/health` | 算法服务健康检查 | M3 周子栋 |
| `POST /api/v1/adaptation/correction-samples` | 接收管理员采纳后的校正样本元数据，写入 JSONL manifest 并返回采纳建议 | M3 周子栋 |

## 字段命名规则

- JSON 字段使用小驼峰：`landmarkId`、`imageUrl`、`searchRecordId`。
- 数据库字段使用下划线：`landmark_id`、`image_url`、`search_record_id`。
- 地标编号使用 `L01` 至 `L10`。
- 时间字段统一使用 ISO 8601 字符串，例如 `2026-05-18T10:00:00`。

## Top-5 返回规则

- 返回的是 Top-5 地标，不是 Top-5 图片。
- 每个地标根据样本特征估计均值向量和协方差矩阵，查询时计算马氏距离并换算为经验归一化匹配分。
- Top-5 按 `score` 从高到低排序；`score` 越高，表示查询图越接近该地标特征分布。该分数用于排序和展示区分度，不具备概率或统计置信度含义。
- 后端对外返回字段至少包含：`rank`、`landmarkId`、`landmarkCode`、`name`、`score`、`confidenceLevel`、`mahalanobisDistance`、`coverImageUrl`、`summary`、`locationText`、`mapX`、`mapY`。
- 算法服务内部返回字段至少包含：`rank`、`landmarkCode`、`landmarkName`、`score`、`confidenceLevel`、`mahalanobisDistance`，由 Spring Boot 后端根据 `landmarkCode` 补齐数据库中的 `landmarkId`、中文名称、简介、代表图和地图坐标等信息。
- 如果算法返回的候选均为低匹配等级，后端保留兼容字段 `lowConfidence=true` 并由前端提示需要人工核验；如果算法服务暂不可用，后端返回空候选结果、`lowConfidence=true` 和明确 `message`，不再伪造演示 Top-5。
- 每次上传都会写入 `search_record`。记录状态取值为 `success`、`low_confidence`、`empty_result`、`algorithm_unavailable`，并保存上传图路径、Top-5 快照、最高分候选、最高分、提示信息和用户归属。未登录时记录前端持久化生成的 `guestId`；登录用户通过 Bearer token 解析 `userId` 并关联 `app_user`。

## 图片上传规则

- 当前上传接口限制上传图片类型为 JPG、PNG、WebP。
- 单张图片大小上限为 8MB。
- 后端保存上传图后返回 `uploadImageUrl`，运行目录下的 `uploads/` 不提交到 Git。
- 当前版本优先调用算法服务返回 Top-5 地标，并按 `landmarkCode` 补齐展示信息。
- 地标样本图片由成员手动采集并整理到本地数据目录；原图按 `.gitignore` 排除，不直接提交 Git。当前样本数量记录见 `datasets/landmarks/sample_inventory.md`。

## 反馈规则

| feedbackType | 含义 | confirmedLandmarkId |
| --- | --- | --- |
| `correct` | 用户确认系统识别正确 | 可与 `predictedLandmarkId` 一致 |
| `wrong` | 用户确认系统识别错误 | 必填，用于记录正确地标 |
| `uncertain` | 用户无法判断或结果不明确 | 可为空 |

`correct` 和 `wrong` 反馈必须带 `predictedLandmarkId`，用于关联本次 SearchResponse 中的预测地标；`wrong` 必须额外带 `confirmedLandmarkId`。

反馈提交前会校验 `searchRecordId` 是否存在，并校验 `predictedLandmarkId` 是否来自本次 Top-5 结果。登录用户提交反馈时由 Bearer token 解析当前用户，后端不再信任前端传入的 `userId`；未登录用户仍可按游客身份反馈，并可携带 `guestId` 与检索记录保持一致。反馈提交后写入 `feedback` 表，默认状态为 `pending`。后台可将状态更新为 `accepted` 或 `ignored`，第四周再扩展采纳后的样本更新、统计和审核记录。

第四周 V3 已扩展反馈采纳闭环：管理员将反馈更新为 `accepted` 后，后端会创建 `correction_sample` 记录，并通过异步任务通知算法服务 `/api/v1/adaptation/correction-samples`。算法服务返回 `suggestAccept`、`reviewScore`、`reason`、`sarEligible` 和 `nextAction` 后，后端将校正样本标记为 `synced`；若算法服务不可用或超时，校正样本保留为 `sync_failed`，管理员采纳动作不回滚。

## 打卡留言规则

- 打卡留言复用 L01-L10 地标 ID 和地图坐标，不新增独立地点字典。
- 登录用户发布留言、点赞和回复时显示用户名；未登录游客使用 `guestId`，无效游客编号由后端规范化为 `guest#number`。
- 留言和回复当前只支持一级文本互动，单条内容最长 500 字符。
- 后台内容删除能力作为后续扩展预留，本周先实现用户端查询、发布、点赞和一级回复。

## 个人历史规则

`GET /api/me/search-records` 必须携带 Bearer token。服务端只按 token 解析出的 `userId` 查询 `search_record`，不会接受前端传入的用户编号。游客历史仅在前端本机当前会话中展示，不提供服务端跨设备查询。

## 数据库迁移口径

第三周 V2 引入 Flyway，迁移脚本位于 `database/migration/`。Spring Boot 启动时从该目录执行版本化迁移，`schema.sql` 和 `seed_landmarks.sql` 保留为手工初始化和文档核对用脚本。Git 同步迁移脚本和基础种子数据，不同步本机 Docker MySQL volume 中的运行记录。

## 错误码口径

| HTTP 状态码 | 场景 |
| --- | --- |
| `400` | 参数错误、图片格式错误、图片过大 |
| `401` | 未登录或 token 缺失 |
| `403` | 已登录但不是管理员 |
| `404` | 地标不存在、检索记录不存在 |
| `409` | 数据冲突，例如重复地标编号 |
| `500` | 后端内部错误 |
| `502` | 预留给强依赖算法服务时使用；当前 V1 默认返回 `200`、空候选结果、`lowConfidence=true` 和明确 `message` |
