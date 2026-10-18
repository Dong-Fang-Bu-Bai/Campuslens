# CampusLens 文档

| 文档 | 内容 |
| --- | --- |
| [项目概览](00_project_overview.md) | 功能、应用范围与地标目录 |
| [运行指南](09_interface_runbook.md) | 环境安装、服务启动和故障排查 |
| [系统架构](architecture.md) | 系统组件、异步任务与索引版本 |
| [接口契约](02_api_contract.md) | 请求流程、字段与业务约束 |
| [数据字典](03_data_dictionary.md) | 数据实体、字段和迁移 |
| [数据集规范](04_dataset_rules.md) | 样本组织、命名与质量要求 |
| [前端设计](frontend.md) | 页面结构、状态与交互 |
| [测试说明](testing.md) | 自动化测试、并发与故障场景 |

## 子模块

- [算法服务](../algorithm/README.md)
- [检索原理](../algorithm/algo.md)
- [SAR 测试时适配](../algorithm/SAR_IMPLEMENTATION.md)
- [CPU / GPU 环境](../algorithm/GPU_SUPPORT.md)
- [后端服务](../backend/README.md)
- [前端工程](../frontend/README.md)
- [数据库](../database/README.md)
- [运行脚本](../scripts/README.md)
- [开发指南](../CONTRIBUTING.md)

浏览器访问 Spring Boot API，算法服务由后端调用。接口结构参见 [OpenAPI](../api/openapi-campuslens.yaml)，请求示例参见 [REST Client 文件](../api/api-test.http)。
