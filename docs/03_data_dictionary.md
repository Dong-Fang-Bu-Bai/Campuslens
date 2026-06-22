# 数据字典

## Landmark 地标

第二周 V1 已真实使用该表，通过 Spring JDBC 为 `GET /api/landmarks`、`GET /api/landmarks/{id}` 和上传检索结果补齐提供基础地标数据。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `code` | String | 地标编号，如 `L01` |
| `name` | String | 中文名称 |
| `englishName` | String | 英文或拼音名称 |
| `type` | String | 建筑、广场、桥梁、湖区、场馆等 |
| `summary` | String | 简介摘要 |
| `description` | Text | 详细介绍 |
| `locationText` | String | 位置描述 |
| `mapX` | Number | 平面图标注 X 坐标，可后续确定 |
| `mapY` | Number | 平面图标注 Y 坐标，可后续确定 |
| `coverImageUrl` | String | 代表图片路径 |
| `createdAt` | DateTime | 创建时间 |
| `updatedAt` | DateTime | 更新时间 |

## LandmarkImage 地标样本图

第二周 V1 已建表预留，但当前样本图片仍主要通过本地 `datasets/landmarks/` 目录和 `sample_inventory.md` 管理，尚未批量写入该表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `landmarkId` | Long | 所属地标 |
| `imageUrl` | String | 图片路径 |
| `angle` | String | 正面、侧面、远景、近景等 |
| `lightCondition` | String | 白天、阴天、傍晚等 |
| `isCover` | Boolean | 是否代表图 |
| `collector` | String | 采集人 |
| `createdAt` | DateTime | 上传时间 |

## ImageFeature 图片特征

第二周 V1 已建表预留，实际特征缓存和统计参数仍由算法服务本地文件管理。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `imageId` | Long | 样本图片 ID |
| `landmarkId` | Long | 地标 ID |
| `modelName` | String | 特征模型名称，当前算法服务采用 DINOv2 ViT-B/14 |
| `modelVersion` | String | 模型版本 |
| `dimension` | Integer | 向量维度 |
| `featurePath` | String | 向量文件路径 |
| `indexed` | Boolean | 是否已进入统计参数构建流程 |
| `createdAt` | DateTime | 生成时间 |

## SearchRecord 检索记录

第四周 V3 异步改造后，`search_record` 同时作为检索记录和任务权威状态表。提交接口先写入待准入记录，Redis 原子准入成功后进入 `queued`，并立即返回任务编号；Redis 只保存运行期调度结构。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `uploadImageUrl` | String | 用户上传图片路径 |
| `topResultsJson` | Text | Top-5 结果快照 |
| `bestLandmarkId` | Long | 最高分候选地标 |
| `bestScore` | Decimal | 最高经验匹配分 |
| `status` | String | 成功、失败、低匹配等级 |
| `lowConfidence` | Boolean | 是否需要人工核验 |
| `message` | String | 检索提示或异常说明 |
| `guestId` | String | 未登录用户由 `guest_identity` 表分配的 `guest#number`；登录用户记录为 `user-{userId}` |
| `userId` | Long | 登录用户 ID，未登录时为空 |
| `userType` | String | `guest` 或 `user` |
| `jobId` | String | 对外任务 UUID，唯一 |
| `jobTokenHash` | String | 游客任务令牌 SHA-256 哈希，不保存明文 |
| `idempotencyKey` | String | 所有者作用域与原始幂等键的 SHA-256 摘要，全局唯一且不暴露原始键 |
| `fileSha256` | String | 上传文件摘要，用于识别同键不同文件 |
| `queuedAt` | DateTime | Redis 容量准入确认时间；为空表示尚未完成准入 |
| `startedAt` | DateTime | 首次开始处理时间 |
| `finishedAt` | DateTime | 进入终态时间 |
| `updatedAt` | DateTime | 最近状态更新时间 |
| `attemptCount` | Integer | 已领取执行次数，最多 3 次 |
| `errorCode` | String | 最近错误代码，可为空 |
| `leaseUntil` | DateTime | 当前 worker 租约到期时间 |
| `workerId` | String | 当前领取任务的消费者标识 |
| `nextAttemptAt` | DateTime | 下次允许重试的时间；首次任务和终态为空 |
| `sarMode` | Boolean | 用户提交时是否请求持续 SAR 模式 |
| `sarApplied` | Boolean | 本次任务是否实际应用 SAR；任务未完成时可为空 |
| `trustLevel` | String | SAR 可靠性门控结果或信任等级 |
| `baseModelVersion` | String | 本次检索使用的基础模型版本 |
| `indexVersion` | String | 本次检索读取的活动索引版本 |
| `sarStateVersion` | String | 本次检索使用的 SAR 状态版本 |
| `algorithmInstanceId` | String | 实际处理任务的算法实例编号 |
| `algorithmInstanceRole` | String | 实际算法实例角色，当前为 `primary` 或 `secondary` |
| `createdAt` | DateTime | 检索时间 |

状态取值为 `queued`、`processing`、`success`、`low_confidence`、`failed`。Top-5 快照不重复保存地标 Base64 封面，避免运行记录膨胀；展示时根据地标编号补齐封面。

## Feedback 用户反馈

第三周 V2 已接入运行时持久化。提交反馈时要求任务状态为 `success` 或 `low_confidence`，校验提交者所有权，并要求 `predictedLandmarkId` 来自本次检索结果。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `searchRecordId` | Long | 关联检索记录 |
| `predictedLandmarkId` | Long | 系统预测地标 |
| `confirmedLandmarkId` | Long | 用户确认地标，可为空 |
| `userId` | Long | 登录用户 ID，未登录时为空 |
| `feedbackType` | String | 正确、错误、不确定 |
| `comment` | String | 用户说明 |
| `status` | String | 待处理、已采纳、已忽略 |
| `createdAt` | DateTime | 提交时间 |
| `updatedAt` | DateTime | 状态更新时间 |

`feedbackType` 对外接口取值为 `correct`、`wrong`、`uncertain`。数据库可先保存同名字符串，后续如需要中文展示由前端或后台管理页面转换。

## CheckIn 打卡留言

第四周 V3 新增，V16 起绑定真实识图记录。新打卡必须引用发布者本人状态为 `success` 或 `low_confidence` 的检索记录，地标必须来自该记录 Top-5；一条检索记录只能发布一次。V16 以前的历史记录允许不绑定检索记录。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `searchRecordId` | Long | 来源检索记录；历史兼容记录可为空，新打卡必填且唯一 |
| `landmarkId` | Long | 打卡地标 |
| `userId` | Long | 登录用户 ID，游客为空 |
| `guestId` | String | 游客编号 |
| `displayName` | String | 前端展示名，登录用户为用户名，游客为 `guest#number` |
| `message` | String | 留言内容，最长 500 字符 |
| `publishImage` | Boolean | 是否在公共留言板展示来源上传图片，默认 `false` |
| `sourceImageUrl` | String | 接口派生字段；仅 `publishImage=true` 时返回来源图片地址 |
| `likeCount` | Integer | 点赞数量缓存 |
| `replyCount` | Integer | 当前帖子全部可见回复数量缓存 |
| `status` | String | 当前为 `visible`，为后续后台删除/隐藏预留 |
| `createdAt` | DateTime | 发布时间 |
| `updatedAt` | DateTime | 更新时间 |

## CheckInLike 打卡点赞

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `checkInId` | Long | 关联打卡留言 |
| `parentReplyId` | Long | 父回复 ID；为空表示直接回复帖子 |
| `userId` | Long | 登录用户 ID，游客为空 |
| `guestId` | String | 游客编号 |
| `createdAt` | DateTime | 点赞时间 |

## CheckInReply 打卡回复

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `checkInId` | Long | 关联打卡留言 |
| `userId` | Long | 登录用户 ID，游客为空 |
| `guestId` | String | 游客编号 |
| `displayName` | String | 展示名 |
| `message` | String | 回复内容，最长 500 字符 |
| `likeCount` | Integer | 回复点赞数量缓存 |
| `replyCount` | Integer | 直接子回复数量缓存 |
| `status` | String | 当前为 `visible` |
| `createdAt` | DateTime | 回复时间 |

## CheckInReplyLike 回复点赞

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `replyId` | Long | 关联回复 |
| `userId` | Long | 登录用户 ID，游客为空 |
| `guestId` | String | 持久化游客编号 |
| `createdAt` | DateTime | 点赞时间 |

## CorrectionSample 校正样本

第四周 V3 新增。管理员采纳反馈后先写入该表，不直接污染正式地标样本库。算法服务接收元数据后写入 JSONL manifest，用于后续自适应或人工复核。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `feedbackId` | Long | 来源反馈 |
| `searchRecordId` | Long | 来源检索记录 |
| `uploadImageUrl` | String | 用户上传图路径 |
| `predictedLandmarkId` | Long | 原预测地标 |
| `confirmedLandmarkId` | Long | 管理员采纳后的确认地标 |
| `confirmedLandmarkCode` | String | 确认地标编号，如 `L02` |
| `sourceFeedbackType` | String | 来源反馈类型 |
| `topResultsJson` | Text | 来源检索 Top-5 快照 |
| `syncStatus` | String | 当前主流程为 `pending_index` 或 `published`；`sync_pending`、`synced`、`sync_failed` 仅兼容旧记录 |
| `suggestAccept` | Boolean | 算法是否建议采纳 |
| `reviewScore` | Decimal | 基于 Top-5 分数构造的伪概率评估分 |
| `reason` | String | 算法建议或失败原因 |
| `sarEligible` | Boolean | 是否满足 SAR 思路下的可靠样本门槛 |
| `nextAction` | String | 建议动作，如 `append_to_manifest` 或 `manual_review` |
| `algorithmResponseJson` | Text | 算法接口原始响应 |
| `datasetPath` | String | 待发布样本在受控地标目录中的实际路径 |
| `publishedIndexVersion` | String | 样本正式发布时对应的索引版本 |
| `createdAt` | DateTime | 创建时间 |
| `updatedAt` | DateTime | 更新时间 |

## IndexRebuildJob 索引重建任务

第四周 V3 新增。管理员提交索引重建后，后端以该表保存重建任务的权威状态，并从主算法实例同步最终发布结果。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 数据库主键 |
| `rebuildJobId` | String | 对外重建任务 UUID，唯一 |
| `status` | String | `building`、`switching`、`completed` 或 `failed` |
| `indexVersion` | String | 发布成功后的活动索引版本 |
| `errorMessage` | String | 失败原因，可为空 |
| `createdAt` | DateTime | 创建时间 |
| `updatedAt` | DateTime | 最近状态更新时间 |

## GuestIdentity 持久化游客身份

第四周 V3 新增。浏览器首次访问时提交本地生成的 `clientToken`，后端只保存 SHA-256 哈希，并通过自增主键生成全局唯一的 `guest#number`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 自增游客编号，接口展示为 `guest#{id}` |
| `clientTokenHash` | String | 浏览器令牌 SHA-256 哈希，唯一，不保存明文 |
| `createdAt` | DateTime | 首次分配时间 |
| `updatedAt` | DateTime | 最近幂等获取时间 |

## 地图坐标口径

`mapX` 和 `mapY` 当前采用校园平面图百分比坐标，取值范围为 0-100。前端展示时以图片左上角为原点，`mapX` 表示横向百分比，`mapY` 表示纵向百分比。该口径适合初始阶段静态标注，后续若更换底图或接入 GIS 数据，需要同步修订地标元数据。

## AppUser 用户

第三周 V2 使用 `app_user` 保存普通用户和管理员演示账号。普通用户通过右上角“登录/注册”入口注册，用户名和邮箱唯一，密码至少 8 位，邮箱必填并用于密码找回。历史上未绑定邮箱的账号仍可登录并在个人中心补充邮箱，但补充前无法使用找回功能。`admin/admin` 作为演示管理员账号写入同一张表，密码以 PBKDF2 哈希存储；登录后后端返回服务端会话 token，前端携带 token 访问需要登录或管理员权限的接口。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `username` | String | 用户名，唯一 |
| `passwordHash` | String | PBKDF2 哈希摘要，格式为 `pbkdf2$迭代次数$salt$hash` |
| `email` | String | 唯一绑定邮箱；新注册账号必填，历史账号可暂为空 |
| `avatarUrl` | String | 选填头像地址；为空时前端按用户编号和用户名生成固定像素头像 |
| `role` | String | `user` 或 `admin` |
| `enabled` | Boolean | 是否启用 |
| `createdAt` | DateTime | 创建时间 |
| `updatedAt` | DateTime | 更新时间 |

## PasswordResetCode 密码重置验证码

`password_reset_code` 保存密码找回请求。用户通过唯一绑定邮箱发起找回，服务端按规范化邮箱定位账号。数据库只保存 PBKDF2 加盐哈希，不保存验证码明文；每次发送新验证码会使旧验证码失效。验证码默认 10 分钟有效、60 秒发送冷却、最多校验 5 次且只能成功使用一次。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `userId` | Long | 关联 `app_user.id` |
| `codeHash` | String | 验证码 PBKDF2 加盐哈希 |
| `expiresAt` | DateTime | 过期时间 |
| `attemptCount` | Integer | 验证失败次数 |
| `usedAt` | DateTime | 作废或成功使用时间 |
| `createdAt` | DateTime | 创建时间，用于发送冷却判断 |

## AdminUser 管理员兼容表

`admin_user` 保留为旧后台登录接口的兼容表，密码同样以 PBKDF2 哈希存储。前端当前不直接调用 `POST /api/admin/auth/login`，统一调用 `POST /api/auth/login`。

## Flyway 迁移

第三周 V2 使用 `database/migration/` 管理表结构和基础种子数据。

| 脚本 | 说明 |
| --- | --- |
| `V1__baseline_schema.sql` | 创建地标、样本图、特征、检索记录、反馈和管理员表 |
| `V2__v2_record_feedback_admin_fields.sql` | 兼容占位迁移，保留版本序列 |
| `V3__seed_landmarks_and_admin.sql` | 写入 L01-L10 地标元数据和 `admin/admin` 演示账号 |
| `V4__app_user_auth_and_record_owner.sql` | 创建 `app_user`，为检索记录和反馈记录补充登录用户归属字段 |
| `V5__hash_admin_passwords.sql` | 将管理员演示账号密码升级为 PBKDF2 哈希 |
| `V10__v3_check_in_and_correction_sample.sql` | 创建打卡留言、点赞、回复和校正样本表 |
| `V11__async_search_jobs.sql` | 为检索记录增加异步任务、幂等、lease 和错误字段 |
| `V12__harden_async_search_queue.sql` | 增加持久化重试时间和队列到期索引 |
| `V13__persistent_sar_and_index_rebuild.sql` | 增加 SAR、模型/索引版本、待发布样本路径，并创建索引重建任务表 |
| `V16__link_check_in_to_search_record.sql` | 为打卡增加检索记录唯一关联和照片公开开关，保留旧记录兼容性 |
| `V17__nested_check_in_replies.sql` | 为回复增加父子关系、点赞和直接回复计数，创建回复点赞表 |
| `V18__user_avatar.sql` | 为用户增加可持久化的自定义头像地址 |
| `V19__password_reset.sql` | 约束邮箱唯一，创建密码重置验证码表和查询索引 |
| `V14__algorithm_instance_tracking.sql` | 保存实际处理任务的算法实例编号与主备角色 |
| `V15__persistent_guest_identity.sql` | 创建持久化游客身份表，使用令牌哈希幂等分配游客编号 |
