# CampusLens Backend

Spring Boot 后端工程，负责认证、游客身份、异步检索任务、反馈闭环、社区数据、索引重建协调以及主备算法实例故障转移。

## 技术栈

- Java 17+
- Spring Boot 3.3.6
- Maven 3.9+

## 启动

正常启动流程默认使用 MySQL。推荐在项目根目录运行：

```powershell
scripts\start.cmd
```

默认地址：

```text
http://localhost:8080
```

健康检查：

```text
GET http://localhost:8080/api/health
```

## 已提供接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/health` | 后端健康检查 |
| `GET /api/landmarks` | 获取首批 L01-L10 地标列表 |
| `GET /api/landmarks/{id}` | 获取地标详情与地图坐标 |
| `POST /api/guests` | 按浏览器客户端令牌幂等分配递增游客编号 |
| `POST /api/search/upload` | 提交异步检索，返回 `202` 和任务标识 |
| `GET /api/search/jobs/{jobId}` | 查询任务状态、错误信息和 Top-5 终态结果 |
| `POST /api/feedback` | 提交正确、错误或不确定反馈 |
| `POST /api/auth/register` | 使用唯一用户名、唯一邮箱和至少 8 位密码注册 |
| `POST /api/auth/login` | 用户或管理员登录 |
| `POST /api/auth/password-reset/code` | 输入绑定邮箱并向该邮箱发送密码重置验证码 |
| `POST /api/auth/password-reset/confirm` | 使用绑定邮箱校验验证码并设置新密码 |
| `GET /api/admin/algorithm/runtime` | 管理员查看主算法实例运行、SAR 和索引状态 |
| `POST /api/admin/index/rebuild` | 管理员提交索引重建任务，返回 `202` |
| `GET /api/admin/index/rebuild/{jobId}` | 管理员查询索引重建任务状态 |

## 密码找回邮件配置

默认使用标准 SMTP + STARTTLS，可向 QQ 邮箱、163、Outlook、学校邮箱等任意合法收件地址投递。当前 Windows 开发环境已使用 QQ 邮箱 SMTP 完成真实投递验证，配置示例：

```powershell
$env:CAMPUSLENS_MAIL_HOST = "smtp.qq.com"
$env:CAMPUSLENS_MAIL_PORT = "587"
$env:CAMPUSLENS_MAIL_USERNAME = "your-account@qq.com"
$env:CAMPUSLENS_MAIL_PASSWORD = "your-qq-mail-authorization-code"
$env:CAMPUSLENS_MAIL_FROM = $env:CAMPUSLENS_MAIL_USERNAME
```

密码必须使用邮件服务商提供的 SMTP 授权码，不要使用邮箱登录密码，也不要将真实密码或密钥提交到仓库。可通过 `CAMPUSLENS_PASSWORD_RESET_VALID_MINUTES` 和 `CAMPUSLENS_PASSWORD_RESET_COOLDOWN_SECONDS` 调整验证码有效期和发送冷却时间。

## 当前实现边界

- MySQL 保存地标、用户、游客身份、检索任务、反馈、校正样本和索引重建状态；Redis 负责异步任务准入、分发、领取和延迟重试。
- 检索任务提交后返回 `202`，客户端通过任务接口轮询终态。算法调用固定主实例 `8000` 优先，在连接失败、超时或 5xx 时回退备用实例 `8001`。
- `searchRecordId`、`feedbackId` 和 `guest#number` 均为数据库持久化编号。游客编号只能通过 `POST /api/guests` 分配。
- 管理员采纳反馈后生成校正样本，索引重建由主算法实例执行并通过版本文件原子切换。
- `demo` profile 的 H2 内存库仅用于临时调试；日常联调和验收使用 MySQL、Redis 与 Flyway。

对外接口使用 Java/JSON 小驼峰命名，例如 `jobId`、`searchRecordId`。算法服务内部 FastAPI 路径中的 `{job_id}` 只是在算法端生成文档时使用的路径参数名。

上传图片会保存到 `uploads/yyyyMMdd/`，该目录已由 `.gitignore` 排除。

## 验证

```powershell
mvn test
```
