# 基于马氏距离的地标检索算法

**版本**: v2.1
**最后更新**: 2026-06-13

---

## 📋 目录

1. [算法概述](#算法概述)
2. [问题定义](#问题定义)
3. [理论基础](#理论基础)
4. [核心算法](#核心算法)
5. [系统架构](#系统架构)
6. [数据流程](#数据流程)
7. [代码实现](#代码实现)
8. [性能优化](#性能优化)
9. [复杂度分析](#复杂度分析)
10. [实际应用案例](#实际应用案例)
11. [总结](#总结)

---

## 🎯 算法概述

本算法将图像检索问题转化为**地标特征分布匹配问题**，通过计算查询向量到各地标特征分布的马氏距离，再映射为经验匹配分完成 Top-5 排序。

**核心思想**：不只是比较"有多像"，而是比较查询图与各地标样本分布的接近程度。

**文档定位**：本文从整体角度概括该服务算法，包含完整的实现细节和技术原理。详细实现代码说明见后续章节。

---

## 🎯 问题定义

### 传统方法的局限

传统图像检索使用**余弦相似度**：
```python
similarity = cos(q, μ) = qᵀμ / (||q|| · ||μ||)
```

**问题**：
- ❌ 只利用一阶矩（均值），忽略二阶矩（方差）
- ❌ 假设所有方向同等重要（各向同性）
- ❌ 没有统计学校准（0.99 是什么意思？）
- ❌ 无法区分"真匹配"和"偶然相似"

**说明性示例**（数值仅用于说明余弦分数接近时的排序困难）：
```
查询图书馆图片：
- 到图书馆的余弦相似度：0.997
- 到酒店的余弦相似度：0.995
差异：约 0.002 → 几乎无法区分！
```

---

### 本算法的方案

使用**马氏距离 + sigmoid 经验归一化**：
```python
# 1. 计算马氏距离
d² = (q - μ)ᵀ Σ⁻¹ (q - μ)

# 2. 转换为经验匹配分
score = 1 / (1 + exp(MULTIPLIER * (log(d + 1) - log(CENTER + 1))))
# 其中 MULTIPLIER = Config.MATCH_SCORE_SLOPE, CENTER = Config.MATCH_SCORE_CENTER_DISTANCE
```

**设计特点**：
- ✅ 充分利用一二阶矩（均值 + 协方差）
- ✅ 自适应加权（根据方差调整）
- ✅ 分数落在 [0, 1] 区间，便于展示和排序
- ✅ 可结合距离和匹配等级辅助分析候选差异

**同样案例**：
```
查询图书馆图片：
- 到图书馆的马氏距离：500 → score ≈ 0.84
- 到酒店的马氏距离：1200 → score ≈ 0.10
差异明显，便于排序和展示。
```

---

## 📐 理论基础

### 1. 多元正态分布假设

假设每个地标 $L$ 的特征向量服从 $k$ 维多元正态分布（$k=768$）：

$$X_L \sim \mathcal{N}_k(\mu, \Sigma)$$

其中：
- $\mu \in \mathbb{R}^k$：均值向量（地标中心）
- $\Sigma \in \mathbb{R}^{k \times k}$：协方差矩阵（特征变化模式）

**概率密度函数**：
$$p(x|\mu,\Sigma) = \frac{1}{(2\pi)^{k/2}|\Sigma|^{1/2}} \exp\left(-\frac{1}{2}(x-\mu)^T\Sigma^{-1}(x-\mu)\right)$$

**关键观察**：指数部分包含马氏距离的平方！

---

### 2. 参数估计

对地标 $L$ 的 $n$ 张样本图片 $\{\mathbf{x}_1, \ldots, \mathbf{x}_n\}$，计算充分统计量：

**样本均值**：
$$\hat{\mu} = \frac{1}{n}\sum_{i=1}^{n}\mathbf{x}_i$$

**样本协方差**：
$$\hat{\Sigma} = \frac{1}{n-1}\sum_{i=1}^{n}(\mathbf{x}_i - \hat{\mu})(\mathbf{x}_i - \hat{\mu})^T$$

**正则化处理**（避免矩阵奇异）：
$$\Sigma_{\text{reg}} = \hat{\Sigma} + \varepsilon\mathbf{I}, \quad \varepsilon=10^{-6}$$

**协方差逆矩阵**：
$$\Sigma^{-1} = (\Sigma_{\text{reg}})^{-1}$$

---

## 🔬 核心算法

### 1. 马氏距离计算

查询向量 $\mathbf{q}$ 到地标 $L$ 的马氏距离定义为：

$$d_M(\mathbf{q}, L) = \sqrt{(\mathbf{q} - \mu)^T \Sigma^{-1} (\mathbf{q} - \mu)}$$

**计算步骤**：
```python
# Step 1: 计算偏差向量
diff = q - μ                    # 形状: (768,)

# Step 2: 左乘协方差逆矩阵
temp = Σ⁻¹ · diff               # 形状: (768,)

# Step 3: 计算内积并开方
d² = diffᵀ · temp               # 标量
d = √d²
```

**几何意义**：
- 考虑了数据分布的形状（椭球体）
- 自动标准化不同维度的尺度
- 消除特征间的相关性影响

**与欧氏距离对比**：
```
欧氏距离: d_E = ||q - μ||₂
          → 假设所有方向同等重要

马氏距离: d_M = √((q-μ)ᵀΣ⁻¹(q-μ))
          → 根据数据分布调整各方向权重
```

---

### 2. 经验匹配分转换

**目标**：将马氏距离转换为 [0, 1] 范围的经验匹配分，增强展示区分度。

**公式**：
$$\text{score}(\mathbf{q}, L) = \frac{1}{1 + \exp(\text{MATCH\_SCORE\_SLOPE} \cdot (\log(d_M + 1) - \log(\text{MATCH\_SCORE\_CENTER\_DISTANCE} + 1)))}$$

其中参数从配置读取：
- `MATCH_SCORE_SLOPE`: sigmoid 曲线斜率（默认 5.0）
- `MATCH_SCORE_CENTER_DISTANCE`: sigmoid 中心点距离（默认 700）

该公式先用对数压缩马氏距离范围，再以配置的中心距离作为 sigmoid 中心点。`score` 越高表示查询图越接近候选地标分布中心，但它不是概率，也不具备统计置信度含义。

**评分特性**：

| 马氏距离 | score | 解释 |
|------|-------|------|
| 接近 0 | 接近 1.000 | 查询点接近地标分布中心 |
| 约 700 | 约 0.500 | 经验分界区域（MATCH_SCORE_CENTER_DISTANCE） |
| 明显大于 700 | 接近 0.000 | 查询点偏离地标分布 |

**实际应用中的阈值**：
- `score ≥ 0.8` → **high**（高匹配）
- `score 0.4-0.8` → **medium**（中匹配）
- `score < 0.4` → **low**（低匹配，建议人工审核）

---

### 3. 距离重排序框架

对每个候选地标计算查询特征到该地标特征分布中心的马氏距离，再用 sigmoid 归一化为经验匹配分：

**距离统计量**：

$$d_M = \sqrt{(\mathbf{q} - \boldsymbol{\mu}_L)^T \boldsymbol{\Sigma}_L^{-1}(\mathbf{q} - \boldsymbol{\mu}_L)}$$

**经验匹配分**：

$$score = \frac{1}{1 + e^{\text{MATCH\_SCORE\_SLOPE} \cdot (\log(d_M + 1) - \log(\text{MATCH\_SCORE\_CENTER\_DISTANCE} + 1))}}$$

**决策规则**：
- 马氏距离越小，表示查询图越接近候选地标的样本分布中心。
- `score` 越高，表示经验匹配程度越高，排序越靠前。
- `score` 不具备概率或统计置信度含义，只用于排序、展示区分度和辅助判断。

---

## 🏗️ 系统架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    FastAPI Web Service                       │
│                      (Port 8000)                             │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
    ┌──────────▼──────────┐      ┌───────────▼──────────┐
    │  Feature Extraction  │      │   Search Service     │
    │   (DINOv2 Model)     │      │  (Mahalanobis Score) │
    └──────────┬──────────┘      └───────────┬──────────┘
               │                              │
               │         ┌────────────────────▼────────────────┐
               │         │     FAISS Index Manager              │
               │         │  - Landmark Centroids                │
               │         │  - Covariance Matrices               │
               │         │  - Statistical Analysis              │
               │         └─────────────────────────────────────┘
               │
    ┌──────────▼──────────┐
    │   Image Processor   │
    │  (Pillow + NumPy)   │
    └─────────────────────┘
```

### 模块职责

| 模块 | 文件 | 职责 |
|------|------|------|
| **Feature Service** | `app/services/feature_service.py` | 管理 DINOv2 模型，提取特征向量 |
| **Search Service** | `app/services/search_service.py` | 协调搜索流程，调用 FAISS 管理器 |
| **FAISS Manager** | `app/utils/faiss_manager.py` | 索引管理、统计计算、马氏距离评分 |
| **Image Processor** | `app/utils/image_processor.py` | 图片加载、预处理、增强 |
| **SAR Adapter** | `app/models/sar_adapter.py` | SAR 自适应逻辑、参数更新 |

### 核心组件详解

#### 1. FAISSManager

**位置**: `app/utils/faiss_manager.py`

**主要功能**:
- 地标统计信息计算（均值、协方差矩阵、协方差逆矩阵）
- 马氏距离计算
- 经验匹配分转换
- FAISS 索引管理（构建、搜索、持久化）

**关键方法**:

```python
class FAISSManager:
    # 索引构建
    def rebuild_from_scratch(self, all_vectors, all_metadata)

    # 搜索检索
    def search_landmarks_by_category(self, query_vector, top_k=5)

    # 统计计算
    def _compute_mahalanobis_distance(self, query_vector, stats)
    def _get_confidence_level(self, score, stats)
```

#### 2. SearchService

**位置**: `app/services/search_service.py`

**主要功能**:
- 协调特征提取和搜索流程
- 错误处理和异常捕获
- 结果格式化和排名更新

**关键方法**:

```python
class SearchService:
    def search_similar_landmarks(self, image: Image.Image, top_k: int = 5)
```

#### 3. DINOv2Extractor

**位置**: `app/models/dinov2_extractor.py`

**主要功能**:
- 离线加载 DINOv2 模型（支持 CPU/GPU 自动适配）
- 单图/批量特征提取
- 图像预处理（resize、normalize）

**关键方法**:

```python
class DINOv2Extractor:
    def extract_single(self, image: Image.Image) -> np.ndarray
    def extract_batch(self, images: List[Image.Image]) -> np.ndarray
```

---

## 🔄 数据流程

### 索引构建流程

```
1. 扫描数据集目录
   ↓
2. 按地标分组图片
   ↓
3. 批量提取特征向量 (DINOv2)
   ↓
4. 对每个地标计算统计信息：
   - 均值向量 μ
   - 协方差矩阵 Σ
   - 协方差逆矩阵 Σ⁻¹
   ↓
5. 用地标中心向量构建 FAISS 索引
   ↓
6. 持久化存储（索引 + 统计信息）
```

### 搜索检索流程

```
1. 接收查询图片
   ↓
2. 预处理（resize, normalize）
   ↓
3. 提取特征向量 q (DINOv2)
   ↓
4. FAISS 粗筛 Top-K 候选地标
   ↓
5. 对每个候选地标：
   a. 计算马氏距离 d_M
   b. 转换为经验匹配分 score
   c. 确定匹配等级
   ↓
6. 按评分排序，返回 Top-K
```

---

## 💻 代码实现

### 索引构建详细实现

```python
def rebuild_from_scratch(self, all_vectors: np.ndarray, all_metadata: List[dict]):
    """
    重建索引，同时构建地标级别的统计信息

    Args:
        all_vectors: 所有图片的特征向量数组 (n, 768)
        all_metadata: 对应的元数据列表
    """
    self.create_index()
    self.metadata = []

    # Step 1: 按地标分组
    print("Computing landmark statistics...")
    landmark_vectors = {}
    for i, meta in enumerate(all_metadata):
        code = meta['landmark_code']
        if code not in landmark_vectors:
            landmark_vectors[code] = []
        landmark_vectors[code].append(all_vectors[i])

    # Step 2: 计算每个地标的统计特征
    self.landmark_stats = {}
    landmark_centroids = []
    self.landmark_codes = []

    for code, vecs in landmark_vectors.items():
        vecs_array = np.array(vecs).astype('float32')

        # 2.1 计算均值向量 μ
        mean_vec = np.mean(vecs_array, axis=0)

        # 2.2 计算标准差（辅助信息）
        std_vec = np.std(vecs_array, axis=0)

        # 2.3 计算协方差矩阵 Σ
        cov_matrix = np.cov(vecs_array.T)  # (768, 768)

        # 2.4 正则化（避免矩阵奇异）
        regularization = 1e-6 * np.eye(cov_matrix.shape[0])
        cov_matrix += regularization

        # 2.5 计算协方差逆矩阵 Σ⁻¹
        try:
            cov_inv = np.linalg.inv(cov_matrix)
        except np.linalg.LinAlgError:
            # 如果仍然奇异，使用伪逆
            cov_inv = np.linalg.pinv(cov_matrix)

        # 2.6 归一化均值向量（用于 FAISS 索引）
        mean_vec_normalized = mean_vec.copy()
        faiss.normalize_L2(mean_vec_normalized.reshape(1, -1))

        # 2.7 获取地标名称
        landmark_name = next(
            (m['landmark_name'] for m in all_metadata if m['landmark_code'] == code),
            code
        )

        # 2.8 保存统计信息
        self.landmark_stats[code] = {
            "mean": mean_vec,
            "mean_normalized": mean_vec_normalized,
            "std": std_vec,
            "cov_matrix": cov_matrix,
            "cov_inv": cov_inv,
            "count": len(vecs),
            "name": landmark_name
        }

        landmark_centroids.append(mean_vec_normalized)
        self.landmark_codes.append(code)

        print(f"  {code}: {len(vecs)} images, avg_std={np.mean(std_vec):.4f}")

    # Step 3: 用地标中心向量构建 FAISS 索引
    centroids_array = np.array(landmark_centroids).astype('float32')
    self.index.add(centroids_array)

    # Step 4: 保存原始图片向量到 metadata
    self.metadata = all_metadata

    # Step 5: 持久化存储
    self.save_index()

    print(f"[OK] Landmark index built: {len(self.landmark_codes)} landmarks")
```

---

### 马氏距离计算实现

```python
def _compute_mahalanobis_distance(self, query_vector: np.ndarray, stats: dict) -> float:
    """
    计算查询向量到地标分布的马氏距离

    公式: d = √((q - μ)ᵀ Σ⁻¹ (q - μ))

    Args:
        query_vector: 查询图片的特征向量 (768,)
        stats: 地标统计信息字典

    Returns:
        马氏距离（标量）
    """
    # Step 1: 计算偏差向量
    diff = query_vector.astype('float32') - stats["mean"]

    # Step 2: 计算 (q-μ)ᵀ Σ⁻¹ (q-μ)
    # np.dot(diff.T, stats["cov_inv"]) → (768,)
    # 再 dot diff → 标量
    distance = np.sqrt(np.dot(np.dot(diff.T, stats["cov_inv"]), diff))

    return float(distance)
```

---

### 经验匹配分实现

```python
def mahalanobis_match_score(distance: float) -> float:
    """
    基于马氏距离计算经验匹配分

    公式: score = 1 / (1 + exp(SLOPE * (log(d + 1) - log(CENTER + 1))))
    其中 SLOPE = Config.MATCH_SCORE_SLOPE, CENTER = Config.MATCH_SCORE_CENTER_DISTANCE

    Args:
        distance: 查询图片到候选地标分布的马氏距离

    Returns:
        经验匹配分 [0, 1]，不具备概率或统计置信度含义
    """
    log_dist = math.log(distance + 1.0)
    center = math.log(Config.MATCH_SCORE_CENTER_DISTANCE + 1.0)
    exponent = Config.MATCH_SCORE_SLOPE * (log_dist - center)
    if exponent > Config.SIGMOID_STABILITY_THRESHOLD:
        return 0.0
    if exponent < -Config.SIGMOID_STABILITY_THRESHOLD:
        return 1.0
    return 1.0 / (1.0 + math.exp(exponent))
```

---

### 搜索检索完整实现

```python
def search_landmarks_by_category(
    self,
    query_vector: np.ndarray,
    top_k: int = 5
) -> List[dict]:
    """
    基于地标类别的搜索

    Args:
        query_vector: 查询图片的特征向量
        top_k: 返回的地标数量

    Returns:
        地标列表，包含马氏距离和经验匹配分
    """
    if self.index is None or self.index.ntotal == 0:
        raise ValueError("FAISS index is empty, please rebuild index first")

    if not self.landmark_stats:
        raise ValueError("Landmark statistics not available, please rebuild index")

    # Step 1: 归一化查询向量
    query_norm = query_vector.copy().astype('float32')
    faiss.normalize_L2(query_norm.reshape(1, -1))

    # Step 2: FAISS 粗筛（扩大范围以便后续过滤）
    search_k = min(max(top_k * 5, 30), self.index.ntotal)
    _, indices = self.index.search(query_norm.reshape(1, -1), search_k)

    # Step 3: 对每个候选地标计算马氏距离和经验匹配分
    results = []
    for idx in indices[0]:
        if idx == -1:  # FAISS 返回 -1 表示无结果
            continue

        landmark_code = self.landmark_codes[idx]
        stats = self.landmark_stats[landmark_code]

        # 3.1 计算马氏距离
        mahalanobis_dist = self._compute_mahalanobis_distance(query_vector, stats)

        # 3.2 计算经验匹配分和匹配等级
        match_score = mahalanobis_match_score(mahalanobis_dist)
        confidence_level = self._get_confidence_level(match_score, stats)

        results.append({
            "rank": 0,  # 稍后排序
            "landmarkCode": landmark_code,
            "landmarkName": stats["name"],
            "score": round(float(match_score), 4),
            "confidenceLevel": confidence_level,
            "mahalanobisDistance": round(float(mahalanobis_dist), 4)
        })

    # Step 4: 按经验匹配分排序
    results.sort(key=lambda x: x["score"], reverse=True)

    # Step 5: 取 Top-K 并更新排名
    top_results = results[:top_k]
    for i, result in enumerate(top_results, 1):
        result["rank"] = i

    return top_results
```

---

## ⚡ 性能优化

### 1. 批量特征提取

```python
def extract_batch(self, images: List[Image.Image], batch_size: int = 32) -> np.ndarray:
    """
    批量提取特征向量

    Args:
        images: 图片列表
        batch_size: 批处理大小

    Returns:
        特征向量数组 (n, 768)
    """
    all_features = []

    for i in range(0, len(images), batch_size):
        batch = images[i:i+batch_size]

        # 预处理
        processed = [self.preprocess(img) for img in batch]
        batch_tensor = torch.stack(processed).to(self.device)

        # 批量推理
        with torch.no_grad():
            features = self.model(batch_tensor)

        all_features.append(features.cpu().numpy())

    return np.concatenate(all_features, axis=0)
```

---

### 2. 协方差矩阵缓存

协方差逆矩阵在索引构建时计算一次，之后缓存在内存中：

```python
# 索引构建时计算
self.landmark_stats[code] = {
    "cov_inv": cov_inv,  # 缓存逆矩阵
    ...
}

# 搜索时直接使用，无需重复计算
distance = np.sqrt(np.dot(np.dot(diff.T, stats["cov_inv"]), diff))
```

---

### 3. FAISS 索引优化

```python
# 使用内积索引（等价于余弦相似度，因为向量已归一化）
self.index = faiss.IndexFlatIP(self.dimension)

# 如果需要更快的搜索，可以使用 IVF 索引
# nlist = int(sqrt(num_landmarks))
# quantizer = faiss.IndexFlatIP(dimension)
# self.index = faiss.IndexIVFFlat(quantizer, dimension, nlist)
```

---

### 4. 内存优化

对于大规模部署，可以考虑：

1. **PCA 降维**：将 768 维降至 128 维，减少协方差矩阵存储
2. **稀疏矩阵**：如果协方差矩阵稀疏，使用稀疏存储格式
3. **延迟加载**：只加载常用地标的统计信息

---

## 📊 复杂度分析

- **空间复杂度**：$O(L \cdot k^2)$
  - $L$ 为地标数
  - $k=768$ 为特征维度
  - 主要存储协方差逆矩阵（每个地标 768×768）

- **时间复杂度**：
  - 索引构建：$O(n \cdot k^2)$，$n$ 为总图片数
  - 单次检索：$O(L_{\text{candidate}} \cdot k^2)$，$L_{\text{candidate}}$ 为候选地标数

- **性能说明**：索引构建和检索耗时与设备、图片数量、地标数量及并发模式直接相关。当前项目实测数据统一记录在 [完整系统验收记录](../docs/15_full_system_acceptance_test.md)，本文不维护脱离测试环境的固定性能值。

---

## 🔍 实际应用案例

### 测试场景

查询一张图书馆图片，返回 Top-5 地标（以下为说明性示例，实际数值取决于具体数据集）：

| 排名 | 地标 | 马氏距离 | 经验匹配分 | 匹配等级 |
|------|------|---------|-----------|-----------|
| 1 | L01 - library | 约 500 | **约 0.85** | high |
| 2 | L03 - wenyong_square | 约 700 | **约 0.50** | medium |
| 3 | L05 - qin_lake_huxin_island | 约 1000 | **约 0.15** | low |
| 4 | L04 - boxue_bridge | 约 1200 | **约 0.09** | low |
| 5 | L10 - hotel | 约 1500 | **约 0.05** | low |

### 示例说明

1. 马氏距离较小的候选会获得更高的经验匹配分。
2. 匹配等级可将候选划分为 high、medium 和 low，供前端展示及人工核验。
3. 该表不属于真实测试结果，不能用于证明准确率或鲁棒性提升。

---

## 🎯 算法优势

### 1. 分布特征利用
- ✅ 基于每个地标的样本特征分布进行重排序
- ✅ 充分利用一阶矩（均值）和二阶矩（协方差）信息
- ✅ 通过经验归一化增强不同候选之间的展示区分度

### 2. 自适应机制
- ✅ 自动考虑特征维度的方差差异（异方差性）
- ✅ 消除特征间的相关性影响（去冗余）
- ✅ 统计量由样本数据计算；正则化系数和匹配分映射参数仍需配置与标定

### 3. 区分能力
- ✅ 使用分布距离补充仅比较向量方向的信息
- ✅ 克服余弦相似度仅利用方向信息的局限性
- ✅ 提供高/中/低匹配等级辅助人工核验

### 4. 可解释性
- ✅ 马氏距离直接反映查询图与地标样本分布中心的距离
- ✅ 经验匹配分可稳定映射到 0-1 区间，便于前端展示和排序
- ✅ 保留原始马氏距离和匹配等级，便于追溯判断依据

---

## 🧪 测试与验证

### 单元测试示例

```python
import unittest
import numpy as np
from app.utils.faiss_manager import FAISSManager

class TestMahalanobisDistance(unittest.TestCase):

    def setUp(self):
        self.manager = FAISSManager(dimension=768)

    def test_mahalanobis_distance_center(self):
        """测试查询点在中心时的马氏距离"""
        mean = np.random.randn(768)
        cov = np.eye(768)
        cov_inv = np.linalg.inv(cov)

        stats = {
            "mean": mean,
            "cov_inv": cov_inv
        }

        # 查询点恰好在中心
        distance = self.manager._compute_mahalanobis_distance(mean, stats)

        self.assertAlmostEqual(distance, 0.0, places=5)

    def test_mahalanobis_score_range(self):
        """测试评分在 [0, 1] 范围内"""
        mean = np.random.randn(768)
        cov = np.eye(768)
        cov_inv = np.linalg.inv(cov)

        stats = {
            "mean": mean,
            "cov_inv": cov_inv
        }

        query = mean + np.random.randn(768) * 0.1
        score = mahalanobis_match_score(
            self.manager._compute_mahalanobis_distance(query, stats)
        )

        self.assertGreaterEqual(score, 0.0)
        self.assertLessEqual(score, 1.0)

if __name__ == '__main__':
    unittest.main()
```

---

## 🐛 调试技巧

### 1. 打印统计信息

```python
# 在索引构建时
print(f"Landmark {code}:")
print(f"  Mean norm: {np.linalg.norm(mean_vec):.4f}")
print(f"  Avg std: {np.mean(std_vec):.4f}")
print(f"  Cov condition number: {np.linalg.cond(cov_matrix):.2e}")
```

### 2. 可视化马氏距离分布

```python
import matplotlib.pyplot as plt

distances = [result['mahalanobisDistance'] for result in results]
plt.hist(distances, bins=20)
plt.xlabel('Mahalanobis Distance')
plt.ylabel('Frequency')
plt.title('Distribution of Mahalanobis Distances')
plt.show()
```

### 3. 对比不同地标的评分

```python
for result in results:
    print(f"{result['landmarkCode']:5s}: "
          f"score={result['score']:.4f}, "
          f"d_mahalanobis={result['mahalanobisDistance']:.2f}")
```

---

## ❓ 常见问题

### Q1: 协方差矩阵奇异怎么办？

**A**: 添加正则化项：
```python
cov_matrix += 1e-6 * np.eye(768)
```

如果仍然奇异，使用伪逆：
```python
cov_inv = np.linalg.pinv(cov_matrix)
```

### Q2: 马氏距离太大导致评分为 0？

**A**: 这是正常现象，说明查询点明显不属于该地标分布。可以：
- 检查数据集质量
- 增加该地标的训练样本
- 考虑使用 PCA 降维

### Q3: 索引构建太慢？

**A**:
- 使用 GPU 加速特征提取
- 减小 BATCH_SIZE 避免内存溢出
- 考虑并行处理不同地标

### Q4: 如何验证算法正确性？

**A**: 可以编写单元测试验证核心功能：

```python
import unittest
import numpy as np
from app.utils.faiss_manager import FAISSManager

class TestMahalanobisDistance(unittest.TestCase):

    def setUp(self):
        self.manager = FAISSManager(dimension=768)

    def test_mahalanobis_distance_center(self):
        """测试查询点在中心时的马氏距离"""
        mean = np.random.randn(768)
        cov = np.eye(768)
        cov_inv = np.linalg.inv(cov)

        stats = {
            "mean": mean,
            "cov_inv": cov_inv
        }

        # 查询点恰好在中心
        distance = self.manager._compute_mahalanobis_distance(mean, stats)

        self.assertAlmostEqual(distance, 0.0, places=5)

    def test_mahalanobis_score_range(self):
        """测试评分在 [0, 1] 范围内"""
        mean = np.random.randn(768)
        cov = np.eye(768)
        cov_inv = np.linalg.inv(cov)

        stats = {
            "mean": mean,
            "cov_inv": cov_inv
        }

        query = mean + np.random.randn(768) * 0.1
        score = mahalanobis_match_score(
            self.manager._compute_mahalanobis_distance(query, stats)
        )

        self.assertGreaterEqual(score, 0.0)
        self.assertLessEqual(score, 1.0)

if __name__ == '__main__':
    unittest.main()
```

---

## 🎓 总结

本算法将图像检索转化为地标特征分布匹配问题，利用马氏距离和 sigmoid 经验归一化生成 Top-5 检索排序。

**当前实现要点**：
1. 使用 FAISS 完成候选召回，并使用地标分布距离重排序；
2. 保存各地标均值和正则化协方差逆矩阵；
3. 将马氏距离映射为非概率性质的经验匹配分；
4. 通过配置阈值生成匹配等级，便于前端展示和人工核验。

相比只使用向量夹角的方案，该方法额外利用了各地标样本的一阶与二阶统计信息，并通过经验匹配分提供统一的结果展示口径。实际区分能力和鲁棒性仍应以具体数据集上的对照实验为准。

---

## 📚 相关文档

- [README.md](README.md) - 项目主文档，包含快速启动和 API 说明
- [SAR_IMPLEMENTATION.md](SAR_IMPLEMENTATION.md) - SAR 在线适配算法详解
- [GPU_SUPPORT.md](GPU_SUPPORT.md) - GPU 加速配置指南
- [CHECKLIST.md](CHECKLIST.md) - 项目清单和状态
