# 开发指南

## 环境与结构

安装和启动见[运行指南](docs/09_interface_runbook.md)。源码采用 UTF-8：

- `frontend/`：Vue 页面与组件。
- `backend/`：业务接口、鉴权、任务与数据服务。
- `algorithm/`：图像特征、检索与适配。
- `database/migration/`：Flyway 迁移。
- `api/`：接口契约和请求示例。

## 提交修改

从目标分支建立功能分支，例如 `feature/search-upload` 或 `fix/job-retry`。保持修改范围清晰，通过 Pull Request 说明具体问题、行为变化及验证结果。

提交信息使用 `feat`、`fix`、`docs`、`test`、`refactor` 或 `chore`，例如：

```text
fix(search): preserve task ownership during retry
docs(api): clarify job polling response
```

## 变更约定

- 接口变化同步更新 `api/openapi-campuslens.yaml`、请求示例与[接口说明](docs/02_api_contract.md)。
- 数据库变化新增 Flyway 迁移，避免修改已应用的迁移文件。
- 算法变化说明模型、索引兼容性与配置影响。
- 文档说明公开功能、接口约定和配置方式，与代码保持一致。
- 不提交密钥、本地配置、样本原图、模型权重、索引、日志及生成文件。

## 验证

根据修改范围执行[对应测试](docs/testing.md)。涉及多个服务的行为变化时，再检查上传、轮询、检索、反馈与索引发布等相关链路。
