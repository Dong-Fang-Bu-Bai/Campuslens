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
- 图片与向量：本地目录、向量文件、地标统计参数文件
- 检索策略：DINOv2 提取图像特征，按地标样本特征估计均值和协方差，使用马氏距离与 sigmoid 经验匹配分返回 Top-5
- 地图能力：基于校园平面图做静态标注，不做实时导航和室内导航

## 本地演示启动

Windows 下可以直接使用 `scripts/` 目录中的脚本启动后端、前端和算法服务：

```powershell
scripts\1_check-env.cmd
scripts\2_start-dev.cmd
```

默认启动流程会使用本机 Docker Desktop 启动 MySQL，并以 `mysql` profile 启动后端。如果需要做完整联调验收，直接运行：

```powershell
scripts\4_verify-dev.cmd
```

启动后访问：

```text
前端页面：http://localhost:5173
后端健康检查：http://localhost:8080/api/health
算法健康检查：http://localhost:8000/api/v1/health
```

本地演示默认依赖 MySQL。算法服务需要本机准备模型文件和索引参数：

```text
algorithm/models/dinov2_model.pth
algorithm/data/faiss_index/landmark_index.faiss
algorithm/data/faiss_index/metadata.pkl
algorithm/data/faiss_index/landmark_stats.pkl
```

模型权重和 `algorithm/data/faiss_index/` 下的索引产物不提交到 GitHub。模型文件按 `algorithm/README.md` 下载到本地；索引和统计参数在算法服务启动后通过 `POST /api/v1/index/rebuild` 自动生成。

MySQL 是默认启动路径，不需要额外设置 `CAMPUSLENS_BACKEND_PROFILE`。`start-database.cmd` 会优先使用 Windows 原生 Docker Desktop，并内置识别本机推荐路径 `D:\Tools\Docker\Docker\resources\bin`；如果 Docker daemon 未就绪，脚本会尝试启动 `D:\Tools\Docker\Docker\Docker Desktop.exe` 并等待就绪。如果 Windows 侧没有 Docker，但 WSL 中存在 `Ubuntu` 发行版且已配置 Docker Engine，则会自动通过 WSL 执行 `docker compose up -d mysql`。数据库首次创建容器数据卷时会自动执行 `database/schema.sql` 和 `database/seed_landmarks.sql`，初始化基础表和 L01-L10 地标数据。账号和密码仅用于本地开发，可通过 `.env` 覆盖；仓库只提交 `.env.example`。

```powershell
scripts\2_start-dev.cmd
```

当前本机已验证 Docker Desktop 程序安装在 `D:\Tools\Docker\Docker`，Docker Desktop 的 WSL 数据盘通过目录联接放在 `D:\DockerData\wsl`。项目脚本不依赖这个路径必须存在；存在时会优先使用它。

如果使用 WSL Docker，需要先在 Ubuntu 中确认当前用户有 Docker daemon 权限：

```bash
sudo usermod -aG docker $USER
```

执行后在 Windows PowerShell 中重启 WSL，再重新运行启动脚本：

```powershell
wsl --shutdown
scripts\2_start-dev.cmd
```

停止由脚本启动的服务：

```powershell
scripts\3_stop-dev.cmd
```

`3_stop-dev.cmd` 只读取 `.run/` 中的 PID 文件并停止本项目脚本启动的窗口，不会扫描端口或强行结束其他 Java、Node 进程。若服务是手动通过 `mvn spring-boot:run` 或 `npm run dev` 启动的，请直接关闭对应命令行窗口。数据库可用 `docker compose stop mysql` 暂停，或用 `docker compose down` 停止容器；不要随意删除 volume，否则会清空本地数据库数据。

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
| 周子栋 | M3 图像特征提取与统计检索服务 | `feature/m3-vision-zhouzidong` | 周子栋 |
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
- [初始阶段 M1/M2/M4/M5 完成记录](docs/07_initial_stage_m1_m2_m4_m5.md)
- [前端界面原型说明](docs/08_frontend_prototype.md)
- [接口联调说明](docs/09_interface_runbook.md)

## 接口契约

- OpenAPI 草案：[api/openapi-campuslens.yaml](api/openapi-campuslens.yaml)
- REST Client 样例：[api/api-test.http](api/api-test.http)

接口字段以 `api/openapi-campuslens.yaml` 为准；中文说明以 `docs/02_api_contract.md` 为准。若两者冲突，先更新 OpenAPI，再同步中文说明。
