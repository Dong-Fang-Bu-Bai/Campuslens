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
- 算法服务：Python FastAPI + PyTorch CUDA，CUDA 不可用时可在启动阶段回退 CPU
- 数据与队列：MySQL 权威任务状态 + Redis 活跃任务集合、ready 列表、processing/delayed 有序集合
- 图片与向量：本地目录、向量文件、地标统计参数文件
- 检索策略：DINOv2 提取图像特征，按地标样本特征估计均值和协方差，使用马氏距离与 sigmoid 经验匹配分返回 Top-5
- 地图能力：基于校园平面图做静态标注，不做实时导航和室内导航

## 本地演示启动

### 首次安装

当前 Windows + NVIDIA GPU 的已验证安装方式为：

```powershell
Copy-Item algorithm\.env.example algorithm\.env
algorithm\create_gpu_env.bat
```

`create_gpu_env.bat` 会在 `D:\AnaConda\envs\campuslens-gpu` 创建 Python 3.10 环境，并通过 `requirements.txt`、`requirements-gpu.txt`、`requirements-test.txt` 安装公共依赖、PyTorch CUDA 12.1 和测试依赖。FAISS 在 Windows 下固定使用 CPU 版，DINOv2 推理由 CUDA 执行。

如果已经有其他 Python 环境，可设置 `CAMPUSLENS_ALGORITHM_PYTHON` 后运行 `algorithm\install_gpu.bat` 或 `algorithm\install_cpu.bat`。单独执行 `pip install -r algorithm\requirements.txt` 不包含 PyTorch，不能完成算法运行环境安装。

还需要准备离线模型：

```text
algorithm/models/dinov2_model.pth
```

前端依赖在首次执行 `start.cmd` 且 `node_modules` 不存在时自动安装；后端 Maven 依赖由 `mvn spring-boot:run` 自动解析。`start.cmd` 不会自动安装算法 Python 依赖或下载模型。

### 日常启动

Windows 下使用 `scripts/` 目录中的统一脚本启动后端、前端和算法服务：

```powershell
scripts\start.cmd
```

默认启动流程会使用本机 Docker Desktop 启动 MySQL 和 Redis，并启动后端异步任务消费者。算法脚本优先读取 `CAMPUSLENS_ALGORITHM_PYTHON`，未设置时使用 `D:\AnaConda\envs\campuslens-gpu\python.exe`，再回退项目 `.venv`。

如果需要做完整联调验收，直接运行：

```powershell
scripts\verify.cmd
```

启动后访问：

```text
前端 HTTPS：https://localhost:5173
后端健康检查：http://localhost:8080/api/health
算法主实例：http://localhost:8000/api/v1/health
算法备用实例：http://localhost:8001/api/v1/health
```

前端监听 `0.0.0.0`，可将 `localhost` 替换为本机局域网 IP。HTTPS 使用包含当前局域网 IP SAN 的自签名证书，首次访问需要在每台设备和浏览器中确认继续访问；IP 变化后启动脚本会自动重新生成证书。页面通过 Vite 同源代理访问 `/api` 和 `/uploads`，不会产生混合内容问题。

### 接口命名口径

- 浏览器提交检索：`POST /api/search/upload`，只负责返回 `202`、`jobId`、`jobToken` 和 `searchRecordId`。
- 浏览器轮询结果：`GET /api/search/jobs/{jobId}`，任务终态为 `success`、`low_confidence` 或 `failed`。
- 后端任务消费者：正式检索调用算法服务 `POST /api/v1/search/batch`；`POST /api/v1/search` 保留给单图诊断。
- 管理员运维：通过后端 `/api/admin/algorithm/runtime`、`/api/admin/index/rebuild` 和 `/api/admin/index/rebuild/{jobId}` 操作，不由前端直连算法服务。

后端路径参数使用 Java 风格 `{jobId}`；算法服务的重建状态路径在 FastAPI 文档中显示为 `{job_id}`。两者都是路径占位符，不代表 JSON 字段改名。

### 终端编码

项目源码与 Markdown 统一使用 UTF-8。运行代码不再输出 emoji 图标，状态前缀统一使用 `[OK]`、`[INFO]`、`[WARN]`、`[ERROR]`，避免 Windows 默认代码页在打印 `✅` 等字符时触发编码错误。统一启动脚本已设置 `PYTHONUTF8=1`；手工启动算法服务时也可先设置 `$env:PYTHONUTF8 = "1"`。

本地演示默认依赖 MySQL。算法服务需要本机准备模型文件和索引参数：

```text
algorithm/models/dinov2_model.pth
algorithm/data/faiss_index/landmark_index.faiss
algorithm/data/faiss_index/metadata.pkl
algorithm/data/faiss_index/landmark_stats.pkl
```

模型权重和 `algorithm/data/faiss_index/` 下的索引产物不提交到 GitHub。模型文件按 `algorithm/README.md` 下载到本地；索引和统计参数在算法服务启动后通过 `POST /api/v1/index/rebuild` 触发生成。

MySQL 和 Redis 是默认启动路径，不需要额外设置 `CAMPUSLENS_BACKEND_PROFILE`。`start.cmd` 会检查当前环境、启动 Windows Docker Desktop、复用 Compose 镜像和命名数据卷，再启动后端、双算法实例及单个 HTTPS 前端。数据库结构和基础种子数据统一由 Flyway 管理。

忘记密码邮件通过标准 SMTP + STARTTLS 发送，收件邮箱不限于发件邮箱服务商。当前开发环境使用 QQ 邮箱 SMTP，并已完成真实验证码投递验证。启动前设置 `CAMPUSLENS_MAIL_USERNAME`、`CAMPUSLENS_MAIL_PASSWORD`，并按发件服务商设置 `CAMPUSLENS_MAIL_HOST`、`CAMPUSLENS_MAIL_PORT` 和 `CAMPUSLENS_MAIL_FROM`；具体示例见 `backend/README.md`。发件密码应使用 SMTP 授权码，禁止提交到仓库。

```powershell
scripts\start.cmd
```

当前本机已验证 Docker Desktop 程序安装在 `D:\Tools\Docker\Docker`，Docker Desktop 的 Linux 后端数据盘位于 `D:\DockerData\wsl`。统一脚本只维护这套已验证的 Windows Docker Desktop 运行方式。

停止由脚本启动的服务：

```powershell
scripts\stop.cmd
```

`stop.cmd` 会清理后端、HTTPS 前端、双算法实例以及 MySQL/Redis 容器，但保留 Docker 镜像和命名数据卷。不要使用 `docker compose down -v`，否则会删除本地数据库和 Redis 数据卷。

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
- [第四周 V3 M1/M2/M4/M5 实施记录](docs/11_v3_m1_m2_m4_m5.md)
- [运行、回归与并发测试记录](docs/12_runtime_and_concurrency_test.md)
- [GPU 与 Redis 异步队列测试记录](docs/13_gpu_async_queue_test.md)
- [SAR 在线适配与模型索引版本测试记录](docs/14_sar_online_adaptation_test.md)
- [全链路修复与验收记录](docs/15_full_system_acceptance_test.md)

## 接口契约

- OpenAPI 草案：[api/openapi-campuslens.yaml](api/openapi-campuslens.yaml)
- REST Client 样例：[api/api-test.http](api/api-test.http)

接口字段以 `api/openapi-campuslens.yaml` 为准；中文说明以 `docs/02_api_contract.md` 为准。若两者冲突，先更新 OpenAPI，再同步中文说明。
