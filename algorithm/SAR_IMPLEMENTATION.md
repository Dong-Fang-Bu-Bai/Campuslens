# SAR 算法实现详解

**版本**: v2.1  
**最后更新**: 2026-06-13

---

## 📋 目录

1. [概述](#概述)
2. [技术架构](#技术架构)
3. [熵的统一定义](#熵的统一定义)
4. [搜索阶段技术路线](#搜索阶段技术路线)
5. [模型侧 SAR 更新机制](#模型侧-sar-更新机制)
6. [反馈阶段技术路线](#反馈阶段技术路线)
7. [代码实现](#代码实现)
8. [验证与调试](#验证与调试)
9. [当前实现边界](#当前实现边界)
10. [总结](#总结)

---

## 概述

**SAR（Sharpness-Aware and Reliable Entropy Minimization）** 是一种**测试时自适应**算法，出自 ICLR 2023 Oral（Notable-Top-5%）论文 *Towards Stable Test-Time Adaptation in Dynamic Wild World*。

**原始论文定位**：在动态变化的真实世界场景中实现稳定的测试时自适应，解决混合域漂移、小批量样本、在线不平衡标签分布等挑战。

**当前 `algorithm/` 中的集成**：已完成从"分类器熵门控"到"检索式熵门控"的适配，实现完整的在线自适应闭环。

**核心目标**：
- 通过在线学习适应数据分布变化（如 corruptions、simulation-to-real 差异）
- 提高检索准确性，特别是对于噪声或未见过的样本
- 防止错误标签直接污染模型

**原始论文技术亮点**：
- **选择性熵最小化**：排除噪声梯度样本，只对可靠样本进行自适应
- **Sharpness-Aware Minimization**：同时优化熵和熵表面锐度，提高泛化能力
- **EMA 模型恢复**：跟踪熵的移动平均，当低于阈值时自动重置模型
- **参数更新策略**：仅更新归一化层参数（BN/LN/GN 的 weight 和 bias）

---

## 技术架构

### 原始论文架构

SAR 的核心是**双前向传播机制**，在单次前向中完成：
1. **第一次前向**：计算熵并过滤可靠样本，执行梯度下降第一步
2. **第二次前向**：在扰动参数上重新计算熵，执行梯度下降第二步

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      SAR 核心算法流程                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│   Input x ──▶ Model(θ) ──▶ Outputs ──▶ Entropy(H)                     │
│                                         │                              │
│                                         ▼                              │
│                         Filter: H < SAR_ENTROPY_THRESHOLD            │
│                                         │                              │
│                                         ▼                              │
│                              Loss = mean(filtered_entropy)             │
│                                         │                              │
│                                         ▼                              │
│                              loss.backward()                           │
│                                         │                              │
│                                         ▼                              │
│                              optimizer.first_step()  ◀── 第一步: θ+ε   │
│                                         │                              │
│                                         ▼                              │
│                              Model(θ+ε) ──▶ Outputs2 ──▶ Entropy(H2)  │
│                                                          │             │
│                                                          ▼             │
│                                      Filter: H2 < SAR_ENTROPY_THRESHOLD│
│                                                          │             │
│                                                          ▼             │
│                              loss2.backward()                          │
│                                         │                              │
│                                         ▼                              │
│                              optimizer.second_step() ◀── 第二步: 最终更新│
│                                                                        │
└─────────────────────────────────────────────────────────────────────────┘
```

### CampusLens 集成架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        FastAPI Web Service                            │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────┐      ┌───────────────┐      ┌───────────────────┐   │
│  │  Search API   │      │  Feedback API │      │   SAR API         │   │
│  └───────┬───────┘      └───────┬───────┘      └─────────┬─────────┘   │
│          │                      │                        │             │
│          ▼                      ▼                        ▼             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                    SAR Search Service                           │ │
│  │  - search_similar_landmarks()                                  │ │
│  │  - feedback_update()                                           │ │
│  │  - reset_sar()                                                 │ │
│  └──────────────────┬───────────────────────────────┬──────────────┘ │
│                     │                               │                 │
│                     ▼                               ▼                 │
│  ┌─────────────────────────┐         ┌───────────────────────────┐   │
│  │     SAR Adapter         │         │    DINOv2 Extractor       │   │
│  │  - 熵计算与门控          │         │  - 特征提取              │   │
│  │  - SAM 参数更新          │         │  - 归一化层              │   │
│  │  - EMA 与重置逻辑        │         │  - SAR 模式支持          │   │
│  └────────────────┬────────┘         └───────────┬───────────────┘   │
│                   │                               │                   │
│                   └───────────┬───────────────────┘                   │
│                               ▼                                       │
│                    ┌─────────────────────┐                           │
│                    │    FAISS Manager    │                           │
│                    │  - 地标统计信息      │                           │
│                    │  - 马氏距离计算      │                           │
│                    │  - 经验匹配分        │                           │
│                    └─────────────────────┘                           │
└─────────────────────────────────────────────────────────────────────────┘
```

### 技术路线

当前实现分为两条链路：

| 链路 | 流程 | 作用 |
|------|------|------|
| **搜索链路** | DINOv2 特征提取 → FAISS 地标召回 → 马氏距离经验匹配分 → Top-K 分数归一化熵 → 信任度门控 | 执行检索并计算可信度 |
| **模型链路** | DINOv2 归一化 CLS 特征 → 马氏距离熵构造 → SARAdapter → SAM 更新归一化层参数 | 对可信样本执行在线更新 |

### 关键参数（来自原始论文）

| 参数 | 配置项 | 默认值 | 说明 |
|------|--------|--------|------|
| 熵门限 | `SAR_ENTROPY_THRESHOLD` | 0.70 | 用于选择性熵最小化的阈值 |
| EMA 衰减系数 | `SAR_EMA_ALPHA` | 0.9 | 指数移动平均衰减因子 |
| 更新步数 | `SAR_STEPS` | 1 | 每次前向的更新步数 |
| 学习率 | `SAR_LEARNING_RATE` | 0.0001 | SAM 基础优化器学习率 |
| SAM rho | `SAR_RHO` | 0.05 | Sharpness-Aware 扰动半径 |
| 熵温度 | `SAR_ENTROPY_TEMPERATURE` | 0.15 | 计算 softmax 分布时的温度系数 |
| 崩溃 EMA 阈值 | `SAR_COLLAPSE_EMA_THRESHOLD` | 0.05 | EMA 低于此值时触发模型重置 |
| 锚点 Top1 保留率 | `SAR_ANCHOR_TOP1_RETENTION` | 0.90 | 锚点检查失败时触发 SAR 重置 |
| 特征漂移阈值 | `SAR_FEATURE_DRIFT_THRESHOLD` | 0.08 | 锚点特征漂移上限 |

---

### 组件职责

| 组件 | 文件 | 职责 |
|------|------|------|
| **DINOv2Extractor** | `app/models/dinov2_extractor.py` | 加载 DINOv2 模型，维护基准和 SAR 双轨道，提取 768 维 CLS 特征 |
| **FAISSManager** | `app/utils/faiss_manager.py` | 索引管理、统计计算、马氏距离评分 |
| **SARAdapter** | `app/models/sar_adapter.py` | 熵计算、门控决策、SAM 参数更新、EMA 管理 |
| **SearchService** | `app/services/search_service.py` | 搜索流程协调、结果格式化、信任等级判定 |
| **FeatureService** | `app/services/feature_service.py` | 特征提取调度、SAR 状态持久化与恢复、锚点检查 |
| **Scoring** | `app/utils/scoring.py` | 经验匹配分计算、归一化熵计算、信任等级判定 |

---

## 熵的统一定义

### 搜索侧熵

搜索侧使用**检索分数归一化熵**，而非分类 logits 熵。

**计算步骤**：

1. 取得 Top-K 候选的 `score`（经验匹配分）
2. 对 `score` 做温度 softmax：
   $$p_i = \frac{\exp(\text{score}_i / T)}{\sum_j \exp(\text{score}_j / T)}$$
3. 计算 Shannon entropy：
   $$H = -\sum_i p_i \log(p_i)$$
4. 使用 `log(K)` 归一化到 `[0, 1]`：
   $$\text{normalized\_entropy} = \frac{H}{\log(K)}$$

**解释**：
- 熵越低，候选分布越集中，样本越可信
- 熵越高，候选分布越分散，样本越不可信

**默认门控阈值**：

| 熵范围 | 信任等级 | 说明 |
|--------|---------|------|
| `entropy < 0.35` | `trusted` | 高可信度，允许 SAR 更新 |
| `0.35 <= entropy < 0.60` | `uncertain` | 不确定，谨慎更新 |
| `entropy >= 0.60` | `untrusted` | 不可信，禁止更新 |

---

### 模型侧熵

模型侧使用与搜索侧**一致**的熵计算逻辑：

1. 从模型输出中提取 CLS 特征
2. 对 CLS 特征执行 `L2 normalize`
3. 使用与搜索侧同源的地标统计量计算马氏距离
4. 将马氏距离映射为经验匹配分
5. 对 Top-K 匹配分做温度 softmax
6. 计算归一化熵并作为 SAR 门控依据

**关键点**：模型侧和搜索侧的熵建立在**同一特征空间和同一分数语义**上。

---

## 搜索阶段技术路线

`POST /api/v1/search` 带 `sarMode=true` 的执行流程：

```
1. 接收查询图片
   ↓
2. DINOv2 提取 CLS 特征（768维）
   ↓
3. L2 归一化特征向量
   ↓
4. FAISS 召回候选地标（扩大范围）
   ↓
5. 对每个候选计算马氏距离和经验匹配分
   ↓
6. 根据 Top-K 匹配分计算归一化熵
   ↓
7. 确定信任等级（trusted/uncertain/untrusted）
   ↓
8. 返回检索结果：
   - results: Top-K 地标列表
   - entropy: 归一化熵值
   - trustLevel: 信任等级
   - trustScore: 信任分数
   - sarApplied: 是否应用 SAR
   ↓
9. 若样本低熵且满足门控条件 → 触发 SAR 更新
```

**设计目标**：
- 让可信样本推动测试时自适应
- 让高风险样本只参与检索，不参与更新
- 保持搜索结果稳定且可解释

---

## 模型侧 SAR 更新机制

### SAR 核心算法（来自原始论文）

**选择性熵最小化**（公式 2）：
$$\mathcal{L}_{\text{SAR}} = \frac{1}{|\mathcal{R}|} \sum_{i \in \mathcal{R}} H(p_{\theta}(y|x_i))$$
其中 $\mathcal{R} = \{i \mid H(p_{\theta}(y|x_i)) < \epsilon_0\}$ 是可靠样本集合。

**Sharpness-Aware 梯度更新**（公式 4）：
$$\theta \leftarrow \theta - \eta \cdot \nabla_{\theta} \mathcal{L}(\theta + \hat{\epsilon}(\theta))$$
其中 $\hat{\epsilon}(\theta) = \rho \cdot \frac{\nabla_{\theta} \mathcal{L}(\theta)}{\|\nabla_{\theta} \mathcal{L}(\theta)\|}$

**模型恢复机制**：当 EMA(entropy) < 0.2 时自动重置模型

### 双前向传播流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SAR 双前向传播流程                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  输入: feature (768维), landmark_stats                             │
│                          │                                         │
│                          ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 第一次前向 (θ)                                              │   │
│  │  - 计算马氏距离 → 经验匹配分 → 熵 H1                        │   │
│  │  - 过滤: R1 = {i | H1 < threshold}                         │   │
│  │  - loss1 = mean(H1[R1])                                    │   │
│  │  - loss1.backward()                                         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                          │                                         │
│                          ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ SAM 第一步: θ → θ + ε (ε = ρ * grad / ||grad||)             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                          │                                         │
│                          ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 第二次前向 (θ + ε)                                          │   │
│  │  - 重新计算熵 H2                                            │   │
│  │  - 再次过滤: R2 = {i | H2 < threshold}                     │   │
│  │  - loss2 = mean(H2[R2])                                    │   │
│  │  - 更新 EMA = alpha * EMA + (1-alpha) * loss2             │   │
│  │  - loss2.backward()                                         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                          │                                         │
│                          ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ SAM 第二步: θ → θ - lr * grad(θ + ε)                        │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                          │                                         │
│                          ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 模型恢复检查                                                 │   │
│  │  - if EMA(entropy) < 0.2 → reset to baseline               │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                          │                                         │
│                          ▼                                         │
│  输出: 更新后的参数, sarApplied, trustLevel                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 参数更新策略

当前实现收集模型中所有**归一化层参数**进行更新：

| 归一化层类型 | 更新参数 |
|-------------|---------|
| `nn.LayerNorm` | weight, bias |
| `nn.BatchNorm2d` | weight, bias |
| `nn.GroupNorm` | weight, bias |

**实现方式**：
- 遍历模型的所有模块，筛选出归一化层实例
- 仅将这些层的 `weight` 和 `bias` 参数加入优化器
- 其余参数保持冻结，不参与 SAR 更新

**原因**：
- 归一化层参数（weight/bias）直接影响特征分布的尺度和偏移
- 仅更新归一化层可以在适应新分布的同时保持特征提取的泛化能力

### EMA 模型恢复机制

**EMA 更新公式**：
$$\text{EMA} = \alpha \times \text{EMA} + (1 - \alpha) \times \text{current\_loss}$$

其中 $\alpha$ 为 `SAR_EMA_ALPHA`（默认 0.9）。

**恢复条件**：当 $\text{EMA} \leq \text{SAR\_COLLAPSE\_EMA\_THRESHOLD}$（默认 0.05）时，执行模型重置。

**设计意图**：
- 持续跟踪模型输出损失的移动平均
- 当损失持续降低时，说明模型可能陷入局部最优或过拟合
- 自动重置防止模型退化

### SARAdapter 核心实现（简化版）

```python
class SARAdapter:
    def __init__(self, model):
        self.model = model
        self.named_parameters = collect_adaptation_parameters(model)
        self.parameters = [p for _, p in self.named_parameters]
        self.initial_state = self._parameter_state()
        self.optimizer = self._new_optimizer()
        self.ema = None
        self.update_count = 0
        self.generation = 1

    def _new_optimizer(self):
        return SAM(
            self.parameters,
            torch.optim.Adam,
            lr=Config.SAR_LEARNING_RATE,
            rho=Config.SAR_RHO,
        )

    def adapt_batch(self, input_tensor, landmark_stats):
        self.model.train()
        self.model.requires_grad_(False)
        for p in self.parameters:
            p.requires_grad_(True)

        with torch.enable_grad():
            first_outputs = self.model.forward_features(input_tensor)
            first_scores = self._scores(first_outputs, landmark_stats)
            first_entropy = self._entropy(first_scores)
            reliable = first_entropy < Config.SAR_ENTROPY_THRESHOLD
            applied = reliable.detach().cpu().tolist()
            updated = bool(reliable.any().item())

            if updated:
                for _ in range(max(1, Config.SAR_STEPS)):
                    outputs = self.model.forward_features(input_tensor)
                    entropy = self._entropy(self._scores(outputs, landmark_stats))
                    entropy[reliable].mean().backward()
                    self.optimizer.first_step(zero_grad=True)

                    perturbed = self.model.forward_features(input_tensor)
                    perturbed_entropy = self._entropy(self._scores(perturbed, landmark_stats))
                    second_loss = perturbed_entropy[reliable].mean()
                    second_loss.backward()
                    self.optimizer.second_step(zero_grad=True)

                loss_value = float(second_loss.detach().item())
                alpha = min(max(Config.SAR_EMA_ALPHA, 0.0), 1.0)
                self.ema = loss_value if self.ema is None else alpha * self.ema + (1.0 - alpha) * loss_value
                self.update_count += 1

        self.model.eval()
        with torch.inference_mode():
            final_outputs = self.model.forward_features(input_tensor)
            features = self._features(final_outputs)
        return features, first_entropy.detach().cpu().tolist(), applied, updated

    def reset(self):
        self._load_parameter_state(self.initial_state)
        self.optimizer = self._new_optimizer()
        self.ema = None
        self.update_count = 0
        self.generation += 1
        self.model.eval()
```

---

## 反馈阶段技术路线

`POST /api/v1/feedback` 的作用是**反馈可信度分流**，而非直接更新模型。

**处理步骤**：

```
1. 接收反馈请求（图片 + 用户标签）
   ↓
2. 用当前模型对图片重新检索
   ↓
3. 取得模型 Top-1 预测及其归一化熵
   ↓
4. 计算用户标签对应地标的马氏一致性
   ↓
5. 综合评估：
   - 模型预测 vs 用户标签
   - 模型熵（可信度）
   - 马氏距离（分布一致性）
   - 标签一致性得分
   ↓
6. 输出反馈状态：
   - accepted: 低冲突，可用于索引更新
   - review: 中等冲突，需要人工审核
   - pending: 高冲突，暂存等待处理
```

**当前策略**：
- 反馈不直接作为真值更新模型
- 高冲突反馈默认进入 `review/pending`
- 只有低风险反馈才允许进入索引更新路径

---

## 代码实现

### 原始 SAR 算法核心代码

### 相关核心代码位置

当前 SAR 实现分布在以下文件中：

- **`app/models/sar_adapter.py`**：`SARAdapter` 类，实现熵计算、SAM 双步更新、EMA 跟踪和状态导出/导入
- **`app/models/dinov2_extractor.py`**：`DINOv2Extractor` 类，维护基准模型和 SAR 模型的双轨道，提供特征提取和 SAR 适配接口
- **`app/services/feature_service.py`**：`FeatureService` 类，调度特征提取、SAR 状态持久化与恢复、锚点漂移检查
- **`app/services/search_service.py`**：`SearchService` 类，协调搜索流程并返回包含信任等级的结果
- **`app/utils/scoring.py`**：经验匹配分、归一化熵和信任等级的计算函数
- **`app/utils/sam_optimizer.py`**：`SAM` 优化器实现

---

## 验证与调试

### 已验证的行为

通过 API 调用和单元测试，当前实现已验证以下行为：

| 验证项 | 说明 |
|--------|------|
| `POST /api/v1/search` 带 `sarMode=true` | 返回包含 `trustLevel` 和 `sarApplied` 的响应 |
| 模型侧马氏距离对齐 | 与搜索侧同量级 |
| SAR 更新链路 | 低熵样本触发 SAM 双步更新 |
| EMA 更新 | 每次更新后根据 `SAR_EMA_ALPHA` 累积 |
| 锚点检查 | 定期运行，失败时自动重置 SAR |
| 状态持久化 | 服务重启后可恢复 SAR 状态 |

### 推荐调试流程

```powershell
1. 启动服务：
   python app/main.py

2. 检查健康状态：
   curl.exe http://localhost:8000/api/v1/health

3. 执行 SAR 搜索并观察响应：
   curl.exe -X POST http://localhost:8000/api/v1/search `
     -F "file=@test.jpg" -F "sarMode=true" | python -m json.tool

4. 重点观察：
   - `trustLevel`（trusted/uncertain/untrusted）
   - `sarApplied`（是否执行了 SAR 更新）
   - `sarStateVersion`（版本是否递增）

5. 查看运行时状态：
   curl.exe http://localhost:8000/api/v1/runtime/status | python -m json.tool
```

---

## 当前实现边界

当前实现仍然有明确边界：

| 边界 | 说明 |
|------|------|
| **参数更新范围** | SAR 仅更新归一化层参数，不做全模型训练 |
| **反馈持久化** | 校正样本写入本地 manifest 文件，由后端统一处理 |
| **参数标定** | 熵阈值与 score 参数依赖经验值，可通过环境变量调整 |
| **门限敏感性** | SAR 更新对门限敏感，不同场景下可能需要调整 `SAR_ENTROPY_THRESHOLD` 与 `SAR_ENTROPY_TEMPERATURE` |

**适合场景**：
- 检索场景下的测试时自适应验证
- 噪声图像鲁棒性增强实验
- 用户反馈防污染策略验证

**不适合场景**：
- 完整的生产级在线学习系统
- 需要大规模参数更新的场景

---

## 总结

### SAR 核心思想（来自原始论文）

SAR（Sharpness-Aware and Reliable Entropy Minimization）是 ICLR 2023 Oral（Notable-Top-5%）论文提出的测试时自适应算法，核心贡献包括：

1. **选择性熵最小化**：通过熵门限过滤掉噪声梯度样本，只对可靠样本进行自适应
2. **Sharpness-Aware 更新**：使用 SAM 优化器同时优化熵和熵表面锐度，提高泛化能力
3. **EMA 模型恢复**：跟踪熵的指数移动平均，当低于阈值时自动重置模型，防止退化

### CampusLens 集成方案

当前 `algorithm/` 中的 SAR 集成已完成从"分类器熵门控"到"检索式熵门控"的适配：

1. **检索式熵门控**：使用马氏距离计算的经验匹配分进行熵计算，而非直接使用分类器 logits
2. **双前向传播机制**：第一次前向确定可靠样本，第二次前向在扰动参数上更新
3. **归一化层参数更新**：仅更新 DINOv2 的 LayerNorm 层参数，跳过顶层 Transformer block
4. **EMA 漂移检测**：跟踪特征分布变化，自动回退异常更新

### 关键参数对比

| 参数 | 原始论文 | CampusLens 实现 |
|------|----------|-----------------|
| 熵门限 | `margin_e0 = 0.4 × log(C)` | `SAR_ENTROPY_THRESHOLD`（默认 0.70） |
| 重置阈值 | `reset_constant_em = 0.2` | `SAR_COLLAPSE_EMA_THRESHOLD`（默认 0.05） |
| EMA 衰减 | 0.9 | `SAR_EMA_ALPHA`（默认 0.9） |
| 更新范围 | BN/LN/GN 的 weight/bias | 所有归一化层（LN/BN/GN）的 weight/bias |

### 实验结果（来自原始论文）

在 ImageNet-C 上的对比结果：

| 方法 | ResNet-50 (GN) | ViT-Base (LN) |
|------|----------------|---------------|
| No adapt | 30.6 | 29.9 |
| Tent | 22.0 | 47.3 |
| EATA | 31.6 | 49.9 |
| **SAR (ours)** | **37.2 ± 0.6** | **58.0 ± 0.5** |

### 引用

```
@inproceedings{niu2023towards,
  title={Towards Stable Test-Time Adaptation in Dynamic Wild World},
  author={Niu, Shuaicheng and Wu, Jiaxiang and Zhang, Yifan and Wen, Zhiquan and Chen, Yaofo and Zhao, Peilin and Tan, Mingkui},
  booktitle = {International Conference on Learning Representations},
  year = {2023}
}
```

---

## 📚 相关文档

- [README.md](README.md) - 项目主文档
- [algo.md](algo.md) - 马氏距离检索算法详解
- [GPU_SUPPORT.md](GPU_SUPPORT.md) - GPU 加速配置指南
- [CHECKLIST.md](CHECKLIST.md) - 项目清单和状态