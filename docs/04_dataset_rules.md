# 图片采集与数据集规范

## 目录结构

```text
datasets/
  landmarks/
    L01_library/
    L02_academic_auditorium/
    L03_wenyong_square/
    L04_boxue_bridge/
    L05_qin_lake_huxin_island/
    L06_stadium/
    L07_natatorium/
    L08_first_dining_hall/
    L09_second_dining_hall/
    L10_hotel/
```

仓库中可以先保留目录说明，不建议直接提交大量原图。样本图片可放在本地、网盘或压缩包中，交付前再按课程要求整理。

## 图片命名

格式：

```text
<landmarkCode>_<angle>_<light>_<index>.jpg
```

示例：

```text
L01_front_day_001.jpg
L01_side_cloudy_002.jpg
L04_far_day_001.jpg
```

推荐取值：

| 字段 | 说明 | 示例 |
| --- | --- | --- |
| `angle` | 拍摄角度 | `front`、`side`、`far`、`near`、`entrance` |
| `light` | 光照条件 | `day`、`cloudy`、`evening`、`backlight` |
| `index` | 三位序号 | `001`、`002` |

## 采集要求

- 每个地标不少于 20 张图片。
- 覆盖正面、侧面、远景、近景和典型入口或标志物。
- 尽量覆盖不同光照和距离。
- 删除严重模糊、主体过小、遮挡过多、重复度过高的图片。
- 每个地标至少选择 1 张代表图用于前端展示。

## 元数据

每个地标至少维护：

- 地标编号。
- 中文名称。
- 英文或拼音名称。
- 类型。
- 位置描述。
- 简介。
- 代表图片路径。
- 平面图静态标注位置。

## 首批地标

首批地标以 `docs/00_project_overview.md` 中 L01-L10 为准。新增或替换地标时，必须同步更新：

- `docs/00_project_overview.md`
- `docs/03_data_dictionary.md` 如字段变化
- `database/seed_landmarks.sql`
- `api/openapi-campuslens.yaml` 如接口字段变化
