# database

数据库脚本目录。第二周 V1 已接入基础 MySQL 库，当前真实使用 `landmark` 表读取 L01-L10 地标元数据；`landmark_image`、`image_feature`、`search_record`、`feedback` 和 `admin_user` 表为后续迭代预留。

- `schema.sql`：建表脚本。
- `seed_landmarks.sql`：首批地标初始化数据。
- `migration/`：后续字段变更脚本。

字段口径以 `../docs/03_data_dictionary.md` 为准。

## Docker 启动

项目根目录提供 `docker-compose.yml`。首次启动时会自动执行：

1. `database/schema.sql`
2. `database/seed_landmarks.sql`

```powershell
cd ..
scripts\start-database.cmd
```

`scripts\start-database.cmd` 会优先使用 Windows 中的 Docker；如果 Windows 没有 `docker` 命令，但 WSL 的 `Ubuntu` 发行版中已经安装并启动 Docker Engine，则会自动在 WSL 中运行同一份 `docker-compose.yml`。这种方式不需要安装 Docker Desktop，后端和前端仍可继续在 Windows 中启动。

首次使用 WSL Docker 时，需要确保 Ubuntu 当前用户可以访问 Docker daemon：

```bash
sudo usermod -aG docker $USER
```

执行后在 Windows PowerShell 中运行 `wsl --shutdown`，重新打开终端后再执行 `scripts\start-database.cmd`。

默认连接信息：

```text
host: localhost
port: 3306
database: campuslens
username: campuslens
password: campuslens123
```

这些账号只用于本地开发。同学从 GitHub 拉取代码后，启动自己的 Docker MySQL 容器即可得到同样的基础库；不要把个人 `.env` 或真实服务器密码提交到仓库。
