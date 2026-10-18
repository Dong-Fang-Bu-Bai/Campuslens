# 运行脚本

在项目根目录执行入口脚本。

| 文件 | 用途 |
| --- | --- |
| [start.cmd](start.cmd) | 检查环境，启动基础服务、后端、双算法实例和 HTTPS 前端 |
| [stop.cmd](stop.cmd) | 停止服务与容器，保留镜像和命名数据卷 |
| [verify.cmd](verify.cmd) | 启动服务并检查各端点 |
| [campuslens.ps1](campuslens.ps1) | 入口脚本共用实现 |
| [concurrency_test.py](concurrency_test.py) | 分层并发与任务完成测试 |

```powershell
scripts\start.cmd
scripts\verify.cmd
scripts\stop.cmd
```

运行前准备 Docker Desktop、Python 环境、算法配置、模型与数据集。Python 解释器优先使用 `CAMPUSLENS_ALGORITHM_PYTHON`；可将独立环境放置于 `algorithm/.venv`。详细依赖见[运行指南](../docs/09_interface_runbook.md)。

首次启动在前端依赖缺失时执行 npm 安装，Maven 自动解析后端依赖；模型和算法 Python 依赖需单独准备。

运行目录 `.run/` 保存 PID、日志等本机状态，不进入 Git。并发工具的参数和使用方式见[测试说明](../docs/testing.md)。
