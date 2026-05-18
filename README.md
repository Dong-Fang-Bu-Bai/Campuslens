# CampusLens（校园慧眼）

校园地标智能检索与导览系统。项目面向新生、访客和校内用户，核心目标是通过 Web 页面上传校园照片，返回 Top-5 候选地标，并展示地标详情、静态平面图位置和用户反馈入口。

本仓库中的 `docs/` 和 `api/` 主要用于组内开发对齐，不替代课程提交版 Word 文档。

## 目录结构

```text
Campuslens/
  api/          接口契约、OpenAPI 草案、接口测试样例
  docs/         项目范围、分支流程、数据字典、采集规范、模块边界
  frontend/     Vue 用户端与简化后台页面
  backend/      Spring Boot 后端服务
  algorithm/    Python FastAPI 图像检索服务
  database/     MySQL 表结构、初始化脚本、种子数据
  datasets/     地标样本图片目录与采集说明
```

## 技术栈口径

- 前端：Vue
- 后端：Spring Boot + REST API
- 算法服务：Python FastAPI
- 数据库：MySQL
- 图片与向量：本地目录、向量文件或 FAISS index 文件
- 检索策略：V1 直接遍历 + 余弦相似度；V2 视进度接入 FAISS
- 地图能力：基于校园平面图做静态标注，不做实时导航和室内导航

## 分支模型

分支按“垂直业务模块”划分，目录按“技术工程”划分。也就是说，每个人的分支可以同时改 `frontend/`、`backend/`、`algorithm/`、`database/`、`docs/` 中属于自己模块的内容，但工程目录仍按技术栈组织。

长期分支：

| 分支 | 用途 | 创建人 | 权限建议 |
| --- | --- | --- | --- |
| `main` | 每周可提交、可演示的稳定版本 | 仓库初始化者或组长 | 保护分支，组长合并；请勿直接 push |
| `dev` | 日常集成与联调分支 | 组长从 `main` 创建 | 保护分支，成员通过合并请求，审核后合入 |

个人功能分支：

| 成员 | 模块 | 分支名 | 创建人 |
| --- | --- | --- | --- |
| 马启凡 | M1 图片上传与地标检索主流程 | `feature/m1-search-maqifan` | 马启凡 |
| 叶炳良 | M2 地标图像库与元数据管理 | `feature/m2-landmark-yebingliang` | 叶炳良 |
| 周子栋 | M3 图像特征提取与向量索引服务 | `feature/m3-vision-zhouzidong` | 周子栋 |
| 洪传凯 | M4 检索结果展示与地图导览 | `feature/m4-result-map-hongchuankai` | 洪传凯 |
| 庄子杰 | M5 用户反馈纠错与检索记录统计 | `feature/m5-feedback-zhuangzijie` | 庄子杰 |

## 分支创建

组长创建 `dev`：

```bash
git checkout main
git pull origin main
git checkout -b dev
git push -u origin dev
```

成员创建个人分支，以马启凡为例：

```bash
git checkout dev
git pull origin dev
git checkout -b feature/m1-search-maqifan
git push -u origin feature/m1-search-maqifan
```

其他成员只替换分支名即可。所有个人分支必须从最新 `dev` 创建，不从 `main` 创建。

## 日常开发流程

每天开始开发前，在个人分支同步最新 `dev`：

```bash
git checkout dev
git pull origin dev
git checkout feature/m1-search-maqifan
git merge dev
```

提交个人改动：

```bash
git status
git add <changed-files>
git commit -m "feat(search): implement image upload entry"
git push
```

功能完成准备合入 `dev` 前：

```bash
git checkout dev
git pull origin dev
git checkout feature/m1-search-maqifan
git merge dev
# 解决冲突并完成自测
git push
```

然后发起从个人分支到 `dev` 的合并请求，或由组长本地合并：

```bash
git checkout dev
git pull origin dev
git merge --no-ff feature/m1-search-maqifan
git push origin dev
```

每周验收前，组长从 `dev` 合并到 `main`：

```bash
git checkout main
git pull origin main
git merge --no-ff dev
git tag v1-week1
git push origin main --tags
```

## 合并规则

- 个人分支只能合入 `dev`，不要直接合入 `main`。
- `main` 只接收每周验收通过的 `dev`。
- 合并前必须先把最新 `dev` 合入个人分支并解决冲突。
- 合并到 `dev` 前至少保证自己模块能启动或能通过接口样例说明。
- 涉及接口字段、数据库字段、目录规范变化时，必须同步更新 `api/` 或 `docs/`。
- 不允许把临时文件、IDE 私有配置、模型大文件、样本原图批量提交到主仓库，除非组内已确认。

## 开发对齐文档

- [项目总览](docs/00_project_overview.md)
- [Git 分支与协作规则](docs/01_git_workflow.md)
- [接口契约说明](docs/02_api_contract.md)
- [数据字典](docs/03_data_dictionary.md)
- [图片采集与数据集规范](docs/04_dataset_rules.md)
- [模块归属与协作边界](docs/05_module_ownership.md)
- [开发文档归档](docs/06_development_notes.md)

## 接口契约

- OpenAPI 草案：[api/openapi-campuslens.yaml](api/openapi-campuslens.yaml)
- REST Client 样例：[api/api-test.http](api/api-test.http)

接口字段以 `api/openapi-campuslens.yaml` 为准；中文说明以 `docs/02_api_contract.md` 为准。若两者冲突，先更新 OpenAPI，再同步中文说明。
