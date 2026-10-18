# CampusLens 数据库

MySQL 保存业务数据与任务状态，Redis 提供异步任务准入、分发和重试调度。表结构和基础种子数据由 Flyway 管理。

## 文件

| 路径 | 用途 |
| --- | --- |
| [migration](migration/) | Flyway 版本化迁移 |
| [schema.sql](schema.sql) | 基础表结构参考 |
| [seed_landmarks.sql](seed_landmarks.sql) | 地标种子数据参考 |

运行数据库以迁移脚本为准，不重复执行独立 schema 或 seed 文件。

## 启动

在项目根目录执行：

```powershell
docker compose up -d mysql redis
```

随后启动后端，由 Flyway 应用迁移。也可使用 `scripts/start.cmd` 启动完整服务。

| 参数 | 本地默认值 |
| --- | --- |
| MySQL 地址 | localhost:3306 |
| 数据库 | campuslens |
| 用户名 | campuslens |
| 密码 | campuslens123 |
| Redis 地址 | localhost:6379 |

Compose 参数见根目录 [环境示例](../.env.example)。修改数据库连接时同步设置后端的 `CAMPUSLENS_DATASOURCE_*` 环境变量。

MySQL 与 Redis 使用命名数据卷。`scripts/stop.cmd` 保留数据卷；`docker compose down -v` 会删除其中的数据。

字段与关系见[数据字典](../docs/03_data_dictionary.md)。
