# database

数据库脚本目录。MySQL 通过 Flyway 管理地标、检索、反馈、社区和游客身份数据；`guest_identity` 使用自增主键生成全局递增的 `guest#number`。

- `schema.sql`：建表脚本。
- `seed_landmarks.sql`：首批地标初始化数据。
- `migration/`：后续字段变更脚本。

字段口径以 `../docs/03_data_dictionary.md` 为准。

## Docker 启动

项目根目录提供 `docker-compose.yml`，数据库结构与种子数据由后端启动时的 Flyway migration 管理。

```powershell
cd ..
scripts\start.cmd
```

`scripts\start.cmd` 使用当前已验证的 Windows Docker Desktop，自动补充 D 盘 Docker CLI 路径、等待 daemon 就绪，并启动 MySQL 与 Redis。

当前本机推荐布局：

```text
Docker Desktop: D:\Tools\Docker\Docker
Docker data:    D:\DockerData\wsl
```

默认连接信息：

```text
host: localhost
port: 3306
database: campuslens
username: campuslens
password: campuslens123
```

验证数据库是否真正可用：

```powershell
scripts\verify.cmd
```

这些账号只用于本地开发。同学从 GitHub 拉取代码后，启动自己的 Docker MySQL 容器即可得到同样的基础库；不要把个人 `.env` 或真实服务器密码提交到仓库。
