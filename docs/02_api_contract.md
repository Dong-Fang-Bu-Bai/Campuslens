# 接口契约说明

正式接口结构以 `api/openapi-campuslens.yaml` 为准。本文件用于解释接口边界和调用顺序。

## 调用关系

用户端调用 Spring Boot 后端接口；后端负责鉴权、文件校验、业务记录和数据组装。图像特征提取与检索由后端调用 Python FastAPI 算法服务完成。

```text
Vue 前端 -> Spring Boot 后端 -> Python FastAPI 算法服务
                  |
                  v
                MySQL + 图片目录 + 向量/索引文件
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
| `POST /api/admin/index/rebuild` | 重建索引 | M3 |
| `GET /api/admin/feedback` | 查看反馈记录 | M5 |

## 算法服务接口

算法服务是后端内部调用的 FastAPI 服务，默认地址为 `http://localhost:8000`，统一使用 `/api/v1` 前缀。

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `POST /api/v1/search` | 接收上传图片文件，返回 Top-5 候选地标 | M3 周子栋 |
| `POST /api/v1/index/rebuild` | 根据样本库重建 FAISS 向量索引 | M3 周子栋 |
| `GET /api/v1/index/stats` | 查看当前索引状态、向量数量和维度 | M3 周子栋 |
| `GET /api/v1/health` | 算法服务健康检查 | M3 周子栋 |

## 字段命名规则

- JSON 字段使用小驼峰：`landmarkId`、`imageUrl`、`searchRecordId`。
- 数据库字段使用下划线：`landmark_id`、`image_url`、`search_record_id`。
- 地标编号使用 `L01` 至 `L10`。
- 时间字段统一使用 ISO 8601 字符串，例如 `2026-05-18T10:00:00`。

## Top-5 返回规则

- 返回的是 Top-5 地标，不是 Top-5 图片。
- 同一地标多张图片命中时，取最高相似度作为该地标得分。
- 后端对外返回字段至少包含：`rank`、`landmarkId`、`landmarkCode`、`name`、`score`、`coverImageUrl`、`summary`、`locationText`、`mapX`、`mapY`。
- 算法服务内部返回字段至少包含：`rank`、`landmarkCode`、`landmarkName`、`imagePath`、`imageFilename`、`score`，由 Spring Boot 后端补齐数据库中的 `landmarkId`、中文名称和简介等信息。
- 如果最高相似度低于阈值，可以提示“未找到高置信度结果”，但仍展示候选 Top-5。

## 图片上传规则

- 第一周最小工程限制上传图片类型为 JPG、PNG、WebP。
- 单张图片大小上限为 8MB。
- 后端保存上传图后返回 `uploadImageUrl`，运行目录下的 `uploads/` 不提交到 Git。
- 当前最小工程返回演示 Top-5 结果，第二周替换为调用算法服务并写入真实检索记录。

## 反馈规则

| feedbackType | 含义 | confirmedLandmarkId |
| --- | --- | --- |
| `correct` | 用户确认系统识别正确 | 可与 `predictedLandmarkId` 一致 |
| `wrong` | 用户确认系统识别错误 | 必填，用于记录正确地标 |
| `uncertain` | 用户无法判断或结果不明确 | 可为空 |

反馈提交后默认状态为 `pending`，第四周可扩展为后台审核、采纳、忽略和统计。

## 错误码口径

| HTTP 状态码 | 场景 |
| --- | --- |
| `400` | 参数错误、图片格式错误、图片过大 |
| `404` | 地标不存在、检索记录不存在 |
| `409` | 数据冲突，例如重复地标编号 |
| `500` | 后端内部错误 |
| `502` | 算法服务不可用或返回异常 |
