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

| 接口 | 说明 | 负责人 |
| --- | --- | --- |
| `POST /search` | 接收图片路径或图片文件，返回 Top-5 候选地标 | M3 周子栋 |
| `POST /rebuild-index` | 根据样本库重建向量索引 | M3 周子栋 |
| `POST /extract` | 可选，提取单张图片特征 | M3 周子栋 |

## 字段命名规则

- JSON 字段使用小驼峰：`landmarkId`、`imageUrl`、`searchRecordId`。
- 数据库字段使用下划线：`landmark_id`、`image_url`、`search_record_id`。
- 地标编号使用 `L01` 至 `L10`。
- 时间字段统一使用 ISO 8601 字符串，例如 `2026-05-18T10:00:00`。

## Top-5 返回规则

- 返回的是 Top-5 地标，不是 Top-5 图片。
- 同一地标多张图片命中时，V1 取最高相似度作为该地标得分。
- 返回字段至少包含：`rank`、`landmarkId`、`landmarkCode`、`name`、`score`、`coverImageUrl`、`summary`。
- 如果最高相似度低于阈值，可以提示“未找到高置信度结果”，但仍展示候选 Top-5。

## 错误码口径

| HTTP 状态码 | 场景 |
| --- | --- |
| `400` | 参数错误、图片格式错误、图片过大 |
| `404` | 地标不存在、检索记录不存在 |
| `409` | 数据冲突，例如重复地标编号 |
| `500` | 后端内部错误 |
| `502` | 算法服务不可用或返回异常 |
