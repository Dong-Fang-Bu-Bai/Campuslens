# 接口契约说明

正式接口结构以 `api/openapi-campuslens.yaml` 为准。本文件用于解释接口边界和调用顺序。

## 调用关系

用户端调用 Spring Boot 后端接口；后端负责文件校验、业务记录和数据组装。图像特征提取与检索由后端调用 Python FastAPI 算法服务完成。第二周 V1 主流程已将上传接口接到算法服务，暂不连接 MySQL，地标补齐仍使用后端内存中的 L01-L10 基础数据。

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
| `POST /api/search/upload` | 上传图片并返回 Top-5 地标结果 | M1 马启凡，依赖 M3 |
| `GET /api/landmarks` | 获取地标列表 | M2 叶炳良 |
| `GET /api/landmarks/{id}` | 获取地标详情 | M2 叶炳良，M4 洪传凯 |
| `POST /api/feedback` | 提交识别纠错反馈 | M5 庄子杰 |

## 后台辅助接口

管理员端不是核心演示主线，后台操作可以通过简化页面、接口测试工具或后端代码维护。

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `POST /api/admin/landmarks` | 新增地标 | M2 |
| `PUT /api/admin/landmarks/{id}` | 修改地标 | M2 |
| `POST /api/admin/landmarks/{id}/images` | 上传地标样本图片 | M2 |
| `POST /api/admin/index/rebuild` | 重建地标统计参数 | M3 |
| `GET /api/admin/feedback` | 查看反馈记录 | M5 |

## 算法服务接口

算法服务是后端内部调用的 FastAPI 服务，默认地址为 `http://localhost:8000`，统一使用 `/api/v1` 前缀。

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `POST /api/v1/search` | 接收上传图片文件，返回 Top-5 候选地标 | M3 周子栋 |
| `POST /api/v1/index/rebuild` | 根据样本库重建地标统计参数 | M3 周子栋 |
| `GET /api/v1/index/stats` | 查看当前统计参数状态、样本数量和维度 | M3 周子栋 |
| `GET /api/v1/health` | 算法服务健康检查 | M3 周子栋 |

## 字段命名规则

- JSON 字段使用小驼峰：`landmarkId`、`imageUrl`、`searchRecordId`。
- 数据库字段使用下划线：`landmark_id`、`image_url`、`search_record_id`。
- 地标编号使用 `L01` 至 `L10`。
- 时间字段统一使用 ISO 8601 字符串，例如 `2026-05-18T10:00:00`。

## Top-5 返回规则

- 返回的是 Top-5 地标，不是 Top-5 图片。
- 每个地标根据样本特征估计均值向量和协方差矩阵，查询时计算马氏距离并换算为置信度评分。
- Top-5 按置信度从高到低排序；置信度越高，表示查询图越接近该地标特征分布。
- 后端对外返回字段至少包含：`rank`、`landmarkId`、`landmarkCode`、`name`、`score`、`confidenceLevel`、`mahalanobisDistance`、`coverImageUrl`、`summary`、`locationText`、`mapX`、`mapY`。
- 算法服务内部返回字段至少包含：`rank`、`landmarkCode`、`landmarkName`、`score`、`confidenceLevel`、`mahalanobisDistance`，由 Spring Boot 后端根据 `landmarkCode` 补齐数据库中的 `landmarkId`、中文名称、简介、代表图和地图坐标等信息。
- 如果算法返回低置信度结果，后端保留 `lowConfidence=true` 并由前端提示需要人工核验；如果算法服务暂不可用，后端返回低置信度本地候选结果，并在 `message` 中说明原因。

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

反馈提交后默认状态为 `pending`，第四周可扩展为后台审核、采纳、忽略和统计。

## 错误码口径

| HTTP 状态码 | 场景 |
| --- | --- |
| `400` | 参数错误、图片格式错误、图片过大 |
| `404` | 地标不存在、检索记录不存在 |
| `409` | 数据冲突，例如重复地标编号 |
| `500` | 后端内部错误 |
| `502` | 预留给强依赖算法服务时使用；当前 V1 默认降级为 `200` 低置信度候选结果并在 `message` 中说明 |
