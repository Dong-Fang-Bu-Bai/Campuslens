# 模块归属与协作边界

## M1 图片上传与地标检索主流程

负责人：马启凡

分支：`feature/m1-search-maqifan`

主要范围：

- 图片上传页面入口。
- `POST /api/search/upload`。
- 上传图片校验、保存和检索记录创建。
- 调用 M3 算法服务。
- 组装 Top-5 结果返回给前端。
- 项目计划、接口联调和文档整合。

依赖：

- M2 提供地标和样本图片数据。
- M3 提供算法服务接口。
- M4 展示检索结果。
- M5 关联检索记录提交反馈。

## M2 地标图像库与元数据管理

负责人：叶炳良

分支：`feature/m2-landmark-yebingliang`

主要范围：

- 地标资料维护。
- 样本图片目录和命名规则。
- `landmark`、`landmark_image` 表。
- 地标列表和详情接口。
- 基础后台维护接口或脚本。

## M3 图像特征提取与统计检索服务

负责人：周子栋

分支：`feature/m3-vision-zhouzidong`

主要范围：

- 预训练模型验证。
- 图片预处理和特征提取。
- 马氏距离统计检索。
- Top-5 地标聚合。
- 地标均值、正则化协方差和协方差逆矩阵的统计参数构建。
- Python FastAPI 算法服务。

## M4 检索结果展示与地图导览

负责人：洪传凯

分支：`feature/m4-result-map-hongchuankai`

主要范围：

- Top-5 结果页。
- 地标详情页。
- 代表图片和简介展示。
- 校园平面图静态标注。
- 反馈入口。

## M5 用户反馈纠错与检索记录统计

负责人：庄子杰

分支：`feature/m5-feedback-zhuangzijie`

主要范围：

- 用户反馈表单。
- `POST /api/feedback`。
- `feedback`、`search_record` 关联。
- 反馈记录查看。
- 简单统计和测试记录。

## 跨模块修改规则

- 修改接口字段前，先更新 `api/openapi-campuslens.yaml` 并通知受影响模块。
- 修改数据库字段前，先更新 `docs/03_data_dictionary.md` 和 `database/schema.sql`。
- 修改样本地标列表前，先更新 `docs/00_project_overview.md` 和 `docs/04_dataset_rules.md`。
- 个人分支可以修改多个技术目录，但只应修改自己模块相关文件。
