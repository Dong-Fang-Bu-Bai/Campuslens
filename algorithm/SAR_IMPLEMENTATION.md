# SAR 算法集成说明

## 概述

当前 `algorithm/` 中的 SAR 集成已经完成从“检索侧熵门控”到“模型侧在线更新”的闭环验证，但它的定位仍然是**检索式测试时自适应**，不是标准分类器上的完整监督训练框架。

当前技术路线分为两条链路：

- 搜索链路：DINOv2 特征提取 -> FAISS 地标召回 -> 马氏距离经验匹配分 -> Top-K 分数归一化熵 -> 信任度门控
- 模型链路：DINOv2 归一化 CLS 特征 -> 与搜索侧一致的马氏距离熵构造 -> SARAdapter -> SAM 更新归一化层参数

本次实现的关键修正是：**模型侧熵计算所用特征，已经对齐到搜索侧的 L2 归一化 CLS 向量空间**。这一步解决了早期模型侧马氏距离量级失真、匹配分接近 0、熵恒为 1 的问题。

---

## 当前代码结构

### 特征提取器

- `app/models/dinov2_extractor.py`
- `app/models/sar_dinov2_extractor.py`

职责：

- 加载本地 DINOv2 模型
- 提取 768 维 CLS 特征
- 在 SAR 模式下执行测试时自适应
- 对外保持统一的特征提取接口

### 检索与统计

- `app/utils/faiss_manager.py`
- `app/utils/scoring.py`

职责：

- 构建地标中心 FAISS 索引
- 保存每个地标的均值、协方差和协方差逆矩阵
- 计算马氏距离与经验匹配分
- 根据 Top-K 分数构造归一化熵
- 输出 `entropy`、`trustLevel`、`trustScore`

### SAR 适配层

- `app/models/sar_adapter.py`

职责：

- 将模型输出特征转换为与搜索侧一致的归一化特征
- 根据马氏距离构造归一化熵
- 对低熵样本执行 SAR 更新
- 维护 EMA 与重置逻辑

### SAR 搜索服务

- `app/services/sar_search_service.py`

职责：

- `search_similar_landmarks()`：SAR 增强搜索
- `feedback_update()`：反馈可信度分流
- `reset_sar()`：重置适配器状态

### API 层

- `app/api/routes.py`

接口：

- `POST /api/v1/search`
- `POST /api/v1/search/sar`
- `POST /api/v1/feedback`
- `POST /api/v1/sar/reset`
- `POST /api/v1/index/rebuild`
- `GET /api/v1/index/stats`
- `GET /api/v1/health`

---

## 熵的统一定义

### 搜索侧熵

搜索侧不使用分类 logits 熵，而是使用**检索分数归一化熵**。

计算步骤：

1. 取得 Top-K 候选的 `score`
2. 对 `score` 做温度 softmax
3. 计算 Shannon entropy
4. 使用 `log(K)` 归一化到 `[0, 1]`

解释：

- 熵越低，候选分布越集中，样本越可信
- 熵越高，候选分布越分散，样本越不可信

默认门控阈值：

- `entropy < 0.35`：`trusted`
- `0.35 <= entropy < 0.60`：`uncertain`
- `entropy >= 0.60`：`untrusted`

### 模型侧熵

模型侧现在也使用与搜索侧一致的思路：

1. 从模型输出中提取 CLS 特征
2. 对 CLS 特征执行 `L2 normalize`
3. 使用与搜索侧同源的地标统计量计算马氏距离
4. 将马氏距离映射为经验匹配分
5. 对 Top-K 匹配分做温度 softmax
6. 计算归一化熵并作为 SAR 门控依据

这样做的目的，是让模型侧和搜索侧的熵都建立在同一特征空间和同一分数语义上。

---

## 搜索阶段技术路线

`POST /api/v1/search/sar` 的执行流程如下：

1. 输入图片经过 DINOv2 提取特征
2. 使用 FAISS 召回候选地标
3. 对候选地标计算马氏距离与经验匹配分
4. 根据 Top-K 匹配分计算归一化熵
5. 返回：
   - `results`
   - `entropy`
   - `trustLevel`
   - `trustScore`
6. 若样本低熵且满足门控条件，则允许 SAR 对模型参数做在线微调

设计目标：

- 让可信样本推动测试时自适应
- 让高风险样本只参与检索，不参与更新
- 保持搜索结果稳定且可解释

---

## 反馈阶段技术路线

`POST /api/v1/feedback` 的作用不是直接把用户标签写进模型，而是做**反馈可信度分流**。

处理步骤：

1. 用当前模型对图片重新检索
2. 取得模型 Top-1 预测及其归一化熵
3. 计算用户标签对应地标的马氏一致性
4. 综合：
   - 模型预测
   - 模型熵
   - 马氏距离
   - 标签一致性
5. 输出反馈状态：
   - `accepted`
   - `review`
   - `pending`

当前策略：

- 反馈不直接作为真值更新模型
- 高冲突反馈默认进入 `review/pending`
- 只有低风险反馈才允许进入索引更新路径

这一步的目的，是防止用户恶意反馈或误标直接污染索引与模型。

---

## 已验证的行为

通过 `debug_sar.py` 的 API 检查与模型检查，当前实现已经验证了以下结论：

- `search/sar` 能稳定返回 `entropy`、`trustLevel`、`trustScore`
- `feedback` 能返回 `feedbackTrust`、`pending`、`modelEntropy`、`modelTrust`
- 模型侧马氏距离已经与搜索侧量级对齐，不再出现数万级异常距离
- 模型侧 `match score` 不再全为 0
- 模型侧归一化熵不再恒为 1
- 在放宽门限后，SAR 更新链路可以稳定产生非零参数变化
- `ema` 可以正常更新，特征漂移也可观察到

一次典型验证中可以观察到：

- 模型侧马氏距离：与搜索侧同量级
- 归一化熵：约 0.34 左右
- `param delta total`：稳定为非零
- `ema`：持续更新
- `feature drift`：随迭代逐步累积

这说明当前 SARAdapter 已经不只是“有门控不更新”，而是真正进入了在线适应阶段。

---

## 当前实现边界

当前实现仍然有明确边界：

- SAR 主要更新归一化层参数，不做全模型训练
- 反馈分流已完成，但没有落地数据库持久化审核队列
- 熵阈值与 score 参数仍主要依赖经验值，后续应使用验证集重新标定
- SAR 更新对门限敏感，不同场景下需要重新选择 `margin` 与 `temperature`

因此，当前实现适合：

- 检索场景下的测试时自适应验证
- 噪声图像鲁棒性增强实验
- 用户反馈防污染策略验证

不应直接视为完整的生产级在线学习系统。

---

## 推荐调试流程

1. 启动服务：`python app/main.py`
2. 检查健康状态：`python check_service.py`
3. 运行 SAR 调试：`python debug_sar.py --image <path>`
4. 重点观察：
   - `search/sar` 的 `entropy` 与 `trustLevel`
   - `feedback` 的 `feedbackTrust` 与 `pending`
   - `MODEL CHECK` 中的 `Normalized entropy`
   - `param delta total`
   - `ema history`
   - `feature drift`

---

## 结论

当前 `algorithm/` 中的 SAR 技术路线已经明确：

- 用归一化检索熵做搜索阶段可信度门控
- 用与搜索侧一致的归一化 CLS 特征做模型侧熵构造
- 用 SAR + SAM 对可信样本执行测试时自适应
- 用反馈分流阻止错误标签直接污染索引

这套方案适合当前 CampusLens 的地标检索架构，也与当前代码和调试结果保持一致。
