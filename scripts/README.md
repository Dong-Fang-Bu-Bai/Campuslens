# CampusLens 运行脚本

当前只保留 5 个入口，日常不要再使用旧的编号脚本或分服务启动脚本。

| 文件 | 用途 |
| --- | --- |
| `start.cmd` | 检查环境，启动 Docker Desktop、MySQL、Redis、双算法实例、后端和 HTTPS 前端 |
| `stop.cmd` | 停止上述服务，保留 Docker 镜像和命名数据卷 |
| `verify.cmd` | 启动完整环境并验证 MySQL、Redis、后端、双算法实例和 HTTPS 前端 |
| `campuslens.ps1` | 三个 CMD 入口共用的实现脚本 |
| `concurrency_test.py` | 分档并发测试工具 |

## 使用方式

```powershell
scripts\start.cmd
scripts\verify.cmd
scripts\stop.cmd
```

算法 Python 环境和模型必须提前准备。脚本不会安装 Python 依赖或下载模型；首次安装见项目根目录 `README.md`。前端 `node_modules` 缺失时会使用 npmmirror 自动安装，Maven 会自动解析后端依赖。

`stop.cmd` 只停止容器，不删除镜像和数据卷。除非明确要清空数据库，否则不要执行 `docker compose down -v`。
