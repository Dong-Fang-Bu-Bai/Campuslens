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
| `guestId` | String | 未登录用户统一记录为 `guest` |
| `createdAt` | DateTime | 检索时间 |

## Feedback 用户反馈

第三周 V2 已接入运行时持久化。提交反馈时会校验 `searchRecordId` 是否存在，并要求 `predictedLandmarkId` 来自本次检索结果，保证反馈和检索记录能闭环追踪。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `searchRecordId` | Long | 关联检索记录 |
| `predictedLandmarkId` | Long | 系统预测地标 |
| `confirmedLandmarkId` | Long | 用户确认地标，可为空 |
| `feedbackType` | String | 正确、错误、不确定 |
| `comment` | String | 用户说明 |
| `status` | String | 待处理、已采纳、已忽略 |
| `createdAt` | DateTime | 提交时间 |
| `updatedAt` | DateTime | 状态更新时间 |

`feedbackType` 对外接口取值为 `correct`、`wrong`、`uncertain`。数据库可先保存同名字符串，后续如需要中文展示由前端或后台管理页面转换。

## 地图坐标口径

`mapX` 和 `mapY` 当前采用校园平面图百分比坐标，取值范围为 0-100。前端展示时以图片左上角为原点，`mapX` 表示横向百分比，`mapY` 表示纵向百分比。该口径适合初始阶段静态标注，后续若更换底图或接入 GIS 数据，需要同步修订地标元数据。

## AdminUser 管理员

第三周 V2 管理员端为本地演示最小实现，账号体系可简化。当前种子账号为 `admin/admin`，`passwordHash` 字段暂存演示口令，后续迭代再替换为加密摘要和完整权限控制。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `username` | String | 管理员账号 |
| `passwordHash` | String | 密码哈希 |
| `role` | String | 角色 |
| `enabled` | Boolean | 是否启用 |
| `createdAt` | DateTime | 创建时间 |

## Flyway 迁移

第三周 V2 使用 `database/migration/` 管理表结构和基础种子数据。

| 脚本 | 说明 |
| --- | --- |
| `V1__baseline_schema.sql` | 创建地标、样本图、特征、检索记录、反馈和管理员表 |
| `V2__v2_record_feedback_admin_fields.sql` | 为既有库补齐 V2 检索记录、反馈更新时间和管理员创建时间字段 |
| `V3__seed_landmarks_and_admin.sql` | 写入 L01-L10 地标元数据和 `admin/admin` 演示账号 |
