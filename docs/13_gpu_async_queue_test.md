# CampusLens GPU 与 Redis 异步队列测试记录

日期：2026-06-11

## 实施环境

- GPU：NVIDIA GeForce RTX 4060 Laptop GPU，8188 MiB
- 验证环境：`D:\AnaConda\envs\campuslens-gpu`，Python 3.10.20
- 迁移源环境：`D:\Tools\conda-envs\campuslens-gpu`；新旧环境清单已导出到 `D:\Tools\python-env-backups`
- PyTorch：2.1.2+cu121；torchvision：0.16.2+cu121
- FAISS：`faiss-cpu==1.7.4`
- Redis：7.4-alpine，AOF 持久化，单节点
- MySQL：8.4，Flyway 已从 V11 升级到 V12
- 算法进程：单 Uvicorn worker，GPU 推理并发限制为 1，批量上限 2，FP32

算法健康接口返回 `device=cuda`、`gpuName=NVIDIA GeForce RTX 4060 Laptop GPU`、`cudaVersion=12.1`、`modelReady=true`、`maxBatchSize=2`、`mixedPrecision=false`。

## 队列机制复核

Redis 不再使用可能漂移的单一计数器，而是使用以下结构：

- active Set：按唯一 `jobId` 进行容量准入，终态确认后移除。
- ready List 和 ready Set：负责 FIFO 分发与防重复入队。
- processing ZSet：保存领取凭证及超时时间；回执只清理对应凭证。
- delayed ZSet：保存 2、5、15 秒退避任务，不阻塞消费者线程。

MySQL 保存权威状态及 `next_attempt_at`。worker 的完成、失败和重试更新必须同时匹配 `worker_id` 与 `attempt_count`，因此过期 worker 的晚到结果不能覆盖恢复后的新任务。`queued_at` 作为 Redis 准入确认时间，解决数据库写入与 Redis 准入之间的幂等并发窗口。

## 自动化回归

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| 后端 | `mvn test` | 18 项通过 |
| 算法 | `python -m pytest` | 15 项通过 |
| 前端 | `npm run build` | 通过 |

后端新增覆盖所有者作用域幂等、非终态禁止反馈和跨用户反馈拒绝。算法新增 CUDA OOM 拆为单图后单项失败隔离，并验证混合精度默认关闭。

## GPU 一致性

在目标 Conda 环境中，使用同一张 L01 图片分别执行 GPU 与 CPU FP32 本地推理，两侧 Top-5 均为：

```text
L01, L03, L08, L09, L06
```

对应分数均为 `1.0000, 0.8589, 0.5813, 0.5738, 0.5656`，最大分数差为 0，排序完全一致。单图接口与双图批量接口返回相同排序，批量输入输出顺序一致。

PyTorch CUDA 统计显示，模型加载完成后分配显存约 `338.68 MiB`、保留显存约 `588.00 MiB`；双图批量推理峰值分配显存约 `728.96 MiB`，相对模型常驻分配增加约 `390.28 MiB`。RTX 4060 总显存为 8188 MiB，默认单消费者和批量大小 2 留有充足余量。

## 环境迁移结果

- 目标环境已完成克隆，项目脚本默认使用 `D:\AnaConda\envs\campuslens-gpu\python.exe`，并保留 `CAMPUSLENS_ALGORITHM_PYTHON` 覆盖及项目 `.venv` 回退。
- 目标环境验证版本为 `torch 2.1.2+cu121`、`torchvision 0.16.2+cu121`、`faiss-cpu 1.7.4`，CUDA 12.1 可用并识别 RTX 4060。
- 目标环境执行 `python -m pytest` 共 15 项通过，真实模型健康检查、单图请求和双图批量请求均通过。
- 新环境、迁移源环境的 Conda YAML、显式包清单与 pip freeze 已保存到 `D:\Tools\python-env-backups`。
- Conda 注册表、用户环境目录及常见安装位置中均未发现 C 盘名为 `pytorch` 的环境，因此未执行删除，避免误删其他环境或仅作为缓存存在的 PyTorch 包。

## 并发结果

原始结果由 `scripts/concurrency_test.py` 写入临时文件 `.run/async-gpu-concurrency-results-v2.json`。

| 阶段 | 并发/请求 | 成功率 | 吞吐量 | 平均延迟 | P95 | P99 | 最大延迟 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 后端地标查询 | 100 / 200 | 100% | 45.051 req/s | 1894.491 ms | 2743.267 ms | 2805.737 ms | 2853.611 ms |
| GPU 算法直连 | 10 / 20 | 100% | 4.399 req/s | 2003.234 ms | 3263.350 ms | 3341.798 ms | 3361.410 ms |
| 异步提交 | 100 / 100 | 100%，均为 202 | 171.412 req/s | 352.464 ms | 415.419 ms | 419.618 ms | 420.222 ms |
| 任务终态抽查 | 20 / 50 | 100% | 5.037 task/s | 3227.988 ms | 4193.033 ms | 4205.583 ms | 4214.424 ms |

100 个并发提交生成 100 个唯一任务。抽查 50 个任务全部返回 5 个候选；等待队列排空后，数据库内本轮及故障测试共 107 条记录全部为 `success`，Redis active、ready、processing、delayed 均为 0。提交接口 P95 小于 1 秒。

## 故障与恢复

- 将容量设为 5 并关闭消费者后，前 5 个并发请求返回 202，第 6 个返回 429，响应包含 `Retry-After: 5`。
- 停止 Redis 后，提交接口返回 503；提交前后 MySQL 记录数均为 6，没有遗留伪成功任务。
- 将任务模拟为 `processing`、`attempt_count=1`、lease 已过期并保留旧领取凭证；恢复消费者后任务以第 2 次尝试进入 `success`。
- lease 恢复完成后，Redis active Set、processing ZSet 和领取凭证 Hash 均为空。
- 停止算法服务后，可重试任务依次进入第 1、2、3 次处理，使用 2 秒和 5 秒持久化退避，约 11 秒后以 `algorithm_unavailable` 终止；消费者线程没有执行长时间 sleep。
- 上传 MIME 为 JPEG 但内容损坏的文件后，任务以 `invalid_image` 失败，`attempt_count=1`，没有进入重试。
- 真实单任务链路完成后，Top-5 写入 MySQL，Redis 四类运行状态全部回到 0。
- 使用无数据卷的临时 MySQL 8.4 容器启动后端，Flyway 从空库完整应用 12 个迁移并通过健康检查，验证 Compose 不依赖 `schema.sql` 预灌入。

## 部署边界

当前实现支持多算法 URL 健康冷却与轮询、Redis 单节点 AOF、容量保护、带领取凭证的任务恢复、所有者作用域幂等、任务令牌和 durable 重试。单 RTX 4060 默认只运行一个算法消费者。Redis Cluster、多 GPU 调度和跨节点指标系统不在本轮范围内。

## 清理结果

测试完成后已删除 2026-06-11 10:30 之后生成的检索、反馈、校正样本和上传文件，清空 Redis 测试键，停止临时后端、算法、MySQL 与 Redis 进程，并删除 `.run`、`backend/target`、`frontend/dist`、`.pytest_cache` 和 `__pycache__`。MySQL/Redis 数据卷结构、地标基础数据、模型、FAISS 索引及 Flyway 历史均保留。
