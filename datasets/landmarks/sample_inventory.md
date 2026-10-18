# 地标样本目录

| 编号 | 地标 | 分类目录 |
| --- | --- | --- |
| L01 | 图书馆 | L01_library |
| L02 | 学术大讲堂 | L02_academic_auditorium |
| L03 | 文雍广场 | L03_wenyong_square |
| L04 | 博学桥 | L04_boxue_bridge |
| L05 | 琴湖及湖心岛 | L05_qin_lake_huxin_island |
| L06 | 体育馆 | L06_stadium |
| L07 | 游泳馆 | L07_natatorium |
| L08 | 第一饭堂 | L08_first_dining_hall |
| L09 | 第二饭堂 | L09_second_dining_hall |
| L10 | 中心酒店 | L10_hotel |

目录以 `datasets/landmarks/` 为根。有效图片数量取决于本地数据集；索引构建后的地标数、向量维度和样本统计通过 `GET /api/v1/index/stats` 查看。

新增地标时，在数据库中登记一致的编号与展示资料，再准备分类目录和代表图。图片处理与评测划分见[数据集规范](../../docs/04_dataset_rules.md)。
