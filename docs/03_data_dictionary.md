# 数据字典

## Landmark 地标

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

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `imageId` | Long | 样本图片 ID |
| `landmarkId` | Long | 地标 ID |
| `modelName` | String | 特征模型名称，当前算法服务采用 DINOv2 ViT-B/14 |
| `modelVersion` | String | 模型版本 |
| `dimension` | Integer | 向量维度 |
| `featurePath` | String | 向量文件路径 |
| `indexed` | Boolean | 是否已进入索引 |
| `createdAt` | DateTime | 生成时间 |

## SearchRecord 检索记录

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `uploadImageUrl` | String | 用户上传图片路径 |
| `topResultsJson` | Text | Top-5 结果快照 |
| `bestLandmarkId` | Long | 最高分候选地标 |
| `bestScore` | Decimal | 最高相似度 |
| `status` | String | 成功、失败、低置信度 |
| `createdAt` | DateTime | 检索时间 |

## Feedback 用户反馈

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

## AdminUser 管理员

管理员端不是核心功能，账号体系可简化。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 主键 |
| `username` | String | 管理员账号 |
| `passwordHash` | String | 密码哈希 |
| `role` | String | 角色 |
| `enabled` | Boolean | 是否启用 |
