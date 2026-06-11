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

第三周 V2 已接入运行时持久化。`POST /api/search/upload` 保存上传图后调用算法服务，并将检索状态、Top-5 快照和游客身份写入该表，接口返回的 `searchRecordId` 来自数据库主键。

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
| `guestId` | String | 未登录用户的前端持久化游客标识；登录用户记录为 `user-{userId}` |
| `userId` | Long | 登录用户 ID，未登录时为空 |
| `userType` | String | `guest` 或 `user` |
| `createdAt` | DateTime | 检索时间 |

## Feedback 用户反馈

第三周 V2 已接入运行时持久化。提交反馈时会校验 `searchRecordId` 是否存在，并要求 `predictedLandmarkId` 来自本次检索结果，保证反馈和检索记录能闭环追踪。

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

第四周 V3 新增。用于校园留言板，复用 `landmark` 作为打卡地点来源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `landmarkId` | Long | 打卡地标 |
| `userId` | Long | 登录用户 ID，游客为空 |
| `guestId` | String | 游客编号 |
| `displayName` | String | 前端展示名，登录用户为用户名，游客为 `guest#number` |
| `message` | String | 留言内容，最长 500 字符 |
| `likeCount` | Integer | 点赞数量缓存 |
| `replyCount` | Integer | 一级回复数量缓存 |
| `status` | String | 当前为 `visible`，为后续后台删除/隐藏预留 |
| `createdAt` | DateTime | 发布时间 |
| `updatedAt` | DateTime | 更新时间 |

## CheckInLike 打卡点赞

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `checkInId` | Long | 关联打卡留言 |
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
| `status` | String | 当前为 `visible` |
| `createdAt` | DateTime | 回复时间 |

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
| `syncStatus` | String | `sync_pending`、`synced` 或 `sync_failed` |
| `suggestAccept` | Boolean | 算法是否建议采纳 |
| `reviewScore` | Decimal | 基于 Top-5 分数构造的伪概率评估分 |
| `reason` | String | 算法建议或失败原因 |
| `sarEligible` | Boolean | 是否满足 SAR 思路下的可靠样本门槛 |
| `nextAction` | String | 建议动作，如 `append_to_manifest` 或 `manual_review` |
| `algorithmResponseJson` | Text | 算法接口原始响应 |
| `createdAt` | DateTime | 创建时间 |
| `updatedAt` | DateTime | 更新时间 |

## 地图坐标口径

`mapX` 和 `mapY` 当前采用校园平面图百分比坐标，取值范围为 0-100。前端展示时以图片左上角为原点，`mapX` 表示横向百分比，`mapY` 表示纵向百分比。该口径适合初始阶段静态标注，后续若更换底图或接入 GIS 数据，需要同步修订地标元数据。

## AppUser 用户

第三周 V2 使用 `app_user` 保存普通用户和管理员演示账号。普通用户通过右上角“登录/注册”入口注册，用户名为普通字符串且唯一，密码至少 8 位，邮箱选填。`admin/admin` 作为演示管理员账号写入同一张表，密码以 PBKDF2 哈希存储；登录后后端返回服务端会话 token，前端携带 token 访问需要登录或管理员权限的接口。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `username` | String | 用户名，唯一 |
| `passwordHash` | String | PBKDF2 哈希摘要，格式为 `pbkdf2$迭代次数$salt$hash` |
| `email` | String | 选填邮箱 |
| `role` | String | `user` 或 `admin` |
| `enabled` | Boolean | 是否启用 |
| `createdAt` | DateTime | 创建时间 |
| `updatedAt` | DateTime | 更新时间 |

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
