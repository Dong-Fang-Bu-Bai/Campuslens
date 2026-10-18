# 测试说明

## 自动化测试

前端构建，在 `frontend/` 执行：

```powershell
npm ci
npm run build
```

后端测试，在 `backend/` 执行：

```powershell
mvn test
```

算法测试，在 `algorithm/` 的 Python 环境执行：

```powershell
python -m pip install -r requirements-test.txt
python -m pytest
```

算法环境还需按设备安装 CPU 或 GPU requirements，见[环境配置](../algorithm/GPU_SUPPORT.md)。

| 测试入口 | 内容 |
| --- | --- |
| [ApiControllerTest](../backend/src/test/java/com/campuslens/ApiControllerTest.java) | 业务接口、认证、数据持久化与任务 |
| [test_batch_api.py](../algorithm/tests/test_batch_api.py) | 批量接口 |
| [test_scoring.py](../algorithm/tests/test_scoring.py) | 匹配分与熵 |
| [test_sar_core.py](../algorithm/tests/test_sar_core.py) | 可靠性门控、更新与回退 |
| [test_adaptation.py](../algorithm/tests/test_adaptation.py) | 校正样本与适配 |
| [test_faiss_persistence.py](../algorithm/tests/test_faiss_persistence.py) | FAISS 持久化 |
| [test_version_switch.py](../algorithm/tests/test_version_switch.py) | 模型与索引版本切换 |

## 服务检查

在项目根目录执行 `scripts/verify.cmd`，检查 MySQL、Redis、后端、主备算法实例和 HTTPS 前端。模型、数据集与索引需要提前准备。

## 业务场景

| 场景 | 预期行为 |
| --- | --- |
| 图片上传 | 返回 202、任务编号与轮询凭据 |
| 同所有者重复幂等提交 | 同键同文件返回原任务，同键不同文件返回 409 |
| 队列满或 Redis 不可用 | 分别返回 429 或 503 |
| 检索完成 | 返回候选及版本信息，终态成功或低匹配等级 |
| 访问他人任务 | 拒绝未授权访问 |
| 主算法不可用 | 备用实例接管；全部不可用时按策略重试 |
| 消费者租约过期 | 任务恢复，过期消费者结果无法覆盖新处理 |
| 反馈采纳 | 生成待发布校正样本，经重建发布进入索引 |
| 索引构建失败 | 保留旧活动版本 |
| SAR 状态不兼容 | 不加载模型或索引版本不匹配的状态 |
| 打卡与照片展示 | 校验检索归属及候选，尊重照片公开选项 |

## 并发测试

从项目根目录运行：

```powershell
python scripts/concurrency_test.py --image "C:\path\to\query.jpg" --profile low --output .run/concurrency-low.json
python scripts/concurrency_test.py --image "C:\path\to\query.jpg" --profile low --sar-mode --output .run/concurrency-sar-low.json
```

`--profile` 支持 low、medium、high。工具分别测量后端查询、算法检索、上传及异步任务完成，可通过 `--help` 查看并发与请求数量参数。

记录 CPU / GPU、模型、样本规模、批次、SAR 模式和主备数量，分别报告提交延迟与任务完成延迟、成功率、吞吐、平均值 / P95 / P99 及峰值显存。HTTP 成功不等于检索结果正确。

## 检索效果

使用不参与索引构建的标注图片，报告 Top-1、Top-5，并按光照、模糊、遮挡和视角分组。SAR 与基准使用相同查询顺序及数据划分，控制初始状态并记录回退次数。

修改模型、预处理、样本库或索引后重新评估。经验匹配分不作为识别准确率。
