# CampusLens 后端

基于 Java 17 / Spring Boot 3.3.6，提供检索任务、地标、账号、反馈、社区和管理接口。

## 启动

完整服务使用根目录 `scripts/start.cmd`。单独启动后端前，准备 MySQL 与 Redis：

```powershell
cd backend
mvn spring-boot:run
```

默认监听 `8080`，健康检查为 `GET /api/health`。Flyway 从 `../database/migration` 应用数据库迁移。

## 服务职责

| 服务 | 内容 |
| --- | --- |
| 地标与检索 | 地标资料、上传校验、任务提交、轮询与历史 |
| 身份与账号 | 用户认证、游客编号、头像与密码找回 |
| 任务处理 | Redis 准入与调度、持久化重试、租约恢复及主备算法调用 |
| 反馈与索引 | 反馈处理、校正样本与索引重建 |
| 社区 | 关联检索记录的打卡、照片公开选项、回复与点赞 |

检索提交返回 HTTP `202`。MySQL 保存权威任务状态，Redis 管理运行队列。算法主实例优先，失败后回退备用实例。

## 配置

配置定义见 [application.yml](src/main/resources/application.yml)。

| 环境变量 | 用途 |
| --- | --- |
| CAMPUSLENS_DATASOURCE_URL | MySQL JDBC 地址 |
| CAMPUSLENS_DATASOURCE_USERNAME / PASSWORD | 数据库账号 |
| CAMPUSLENS_REDIS_HOST / PORT | Redis 地址 |
| CAMPUSLENS_ALGORITHM_URLS | 算法实例地址列表 |
| CAMPUSLENS_UPLOAD_DIR | 上传目录 |
| CAMPUSLENS_DATASET_DIR | 地标样本目录 |
| CAMPUSLENS_SEARCH_QUEUE_CAPACITY | 队列容量 |
| CAMPUSLENS_SEARCH_LEASE_SECONDS | 消费者租约 |
| CAMPUSLENS_SEARCH_MAX_ATTEMPTS | 最大尝试次数 |
| CAMPUSLENS_JOB_TOKEN_SECRET | 游客任务令牌密钥 |

本地默认数据库凭据见[数据库说明](../database/README.md)。Docker Compose 的 `.env` 不会自动导出为后端进程环境变量；修改连接参数时需同步设置上述变量。

## 密码找回邮件

邮件使用 SMTP + STARTTLS。设置以下环境变量：

```powershell
$env:CAMPUSLENS_MAIL_HOST = "smtp.example.com"
$env:CAMPUSLENS_MAIL_PORT = "587"
$env:CAMPUSLENS_MAIL_USERNAME = "sender@example.com"
$env:CAMPUSLENS_MAIL_PASSWORD = "your-smtp-authorization-code"
$env:CAMPUSLENS_MAIL_FROM = $env:CAMPUSLENS_MAIL_USERNAME
```

验证码有效期与发送冷却分别由 `CAMPUSLENS_PASSWORD_RESET_VALID_MINUTES` 和 `CAMPUSLENS_PASSWORD_RESET_COOLDOWN_SECONDS` 控制。

## 测试与接口

```powershell
mvn test
```

[接口契约](../docs/02_api_contract.md) · [OpenAPI](../api/openapi-campuslens.yaml) · [系统架构](../docs/architecture.md)
