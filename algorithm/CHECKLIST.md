# CampusLens 算法服务验收清单

本文用于核对当前算法服务是否具备完整运行条件，不再记录容易失真的文件数量、代码行数或历史性能估算。

## 首次安装

- [ ] `algorithm/.env` 已由 `.env.example` 创建，并按本机路径检查。
- [ ] Python 3.10 环境已通过 `create_gpu_env.bat`、`install_gpu.bat` 或 `install_cpu.bat` 安装完整依赖。
- [ ] 未把仅含公共包的 `requirements.txt` 误当成完整运行环境。
- [ ] `models/dinov2_model.pth` 存在，`verify_model.py` 验证通过。
- [ ] GPU 模式下 `torch.cuda.is_available()` 为 `True`；FAISS CPU 可以正常导入。

## 配置与数据

- [ ] `DEVICE`、`DINO_MODEL_PATH`、`DATASET_PATH` 和数据目录指向有效位置。
- [ ] `data/faiss_index/active_index.json` 指向可加载的活动索引版本。
- [ ] SAR checkpoint、状态和事件日志目录可写。
- [ ] `SAR_ENABLED` 运维开关符合运行要求；普通请求默认仍使用 `sarMode=false`。

## 双实例运行

- [ ] 主实例 `8000` 返回健康状态，`instanceRole=primary`。
- [ ] 备用实例 `8001` 返回健康状态，`instanceRole=secondary`。
- [ ] 两个实例加载相同的活动索引版本。
- [ ] 活动索引指针变化后，两个实例均能在请求前同步新版本。
- [ ] 停止主实例后，后端检索可在同一请求内回退到备用实例。
- [ ] 主实例恢复并经过冷却探测后，后端重新优先使用主实例。

## 接口

- [ ] `POST /api/v1/search` 支持 `sarMode=false/true`。
- [ ] `POST /api/v1/search/batch` 可处理批量请求。
- [ ] `GET /api/v1/health` 返回实例身份和健康信息。
- [ ] `GET /api/v1/runtime/status` 返回模型、SAR、索引与实例状态。
- [ ] `GET /api/v1/index/stats` 返回活动索引统计。
- [ ] `POST /api/v1/index/rebuild` 仅由主实例执行发布流程。
- [ ] `GET /api/v1/index/rebuild/{job_id}` 可查询重建任务。
- [ ] `POST /api/v1/adaptation/correction-samples` 可接收已采纳校正样本。

## SAR 与索引

- [ ] 普通检索不修改基准模型。
- [ ] SAR 检索响应包含是否应用、信任度和相关版本信息。
- [ ] SAR 状态使用文件锁与临时文件原子替换持久化。
- [ ] 反馈采纳后形成校正样本和待发布数据。
- [ ] 索引重建先生成候选版本，校验通过后再原子替换活动指针。
- [ ] 重建失败时旧活动指针和内存 manager 保持可用。
- [ ] 新索引发布后创建新的 SAR generation，并完成主备同步。

## 自动化验证

在算法目录执行：

```powershell
D:\AnaConda\envs\campuslens-gpu\python.exe -m pytest
```

在项目根目录执行完整环境验证：

```powershell
scripts\verify.cmd
```

- [ ] 算法测试通过。
- [ ] 后端测试通过。
- [ ] 前端构建通过。
- [ ] MySQL、Redis、后端、双算法实例和 HTTPS 前端健康。
- [ ] 普通与 SAR 真实检索均成功。
- [ ] 管理员反馈采纳、索引重建和故障转移完成实测。

## 当前边界

- 算法容器不是日常启动路径；根目录 Compose 主要提供 MySQL 与 Redis。
- 模型文件不随依赖安装或启动脚本下载。
- FAISS 使用 CPU，GPU 主要用于 DINOv2 和 SAR 计算。
- 压测结论应以实际运行记录为准，不在本文维护固定吞吐量或显存数字。

详细启动方式见 [QUICKSTART.md](QUICKSTART.md)，GPU 配置见 [GPU_SUPPORT.md](GPU_SUPPORT.md)，全链路验收记录见 [../docs/15_full_system_acceptance_test.md](../docs/15_full_system_acceptance_test.md)。

**最后更新：2026-06-13**
