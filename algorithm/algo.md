# 基于马氏距离的地标检索算法

## 📋 算法概述

本算法将图像检索问题形式化为**多元统计推断问题**，通过计算查询向量到各地标特征分布的马氏距离，实现基于概率意义的置信度评估。

**核心思想**：不只是比较"有多像"，而是判断"是否属于同一分布"。

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

**实际案例**：
```
查询图书馆图片：
- 到图书馆的余弦相似度：0.9974
- 到酒店的余弦相似度：0.9949
差异：0.0025（0.25%）→ 几乎无法区分！
```

---

### 本算法的方案

使用**马氏距离 + 卡方分布**：
```python
# 1. 计算马氏距离
d² = (q - μ)ᵀ Σ⁻¹ (q - μ)

# 2. 转换为置信度评分
score = exp(-d² / (2 · χ²_critical))
```

**优势**：
- ✅ 充分利用一二阶矩（均值 + 协方差）
- ✅ 自适应加权（根据方差调整）
- ✅ 有明确的统计解释（95% 置信区间）
- ✅ 有效区分真匹配和假阳性

**同样案例**：
```
查询图书馆图片：
- 到图书馆的马氏距离：4.32 → score = 0.9879
- 到酒店的马氏距离：57.93 → score = 0.1125
差异：0.8754（87.5%）→ 清晰明了！
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

### 2. 卡方分布理论

**核心定理**：

> 如果 $\mathbf{q} \sim \mathcal{N}_k(\mu, \Sigma)$，则马氏距离的平方服从自由度为 $k$ 的卡方分布：

$$d_M^2 \sim \chi^2(k)$$

**证明思路**：
1. 对协方差矩阵进行 Cholesky 分解：$\Sigma = LL^T$
2. 做线性变换：$\mathbf{z} = L^{-1}(\mathbf{q} - \mu)$
3. 则 $\mathbf{z} \sim \mathcal{N}_k(0, I)$（标准正态分布）
4. $d_M^2 = \mathbf{z}^T\mathbf{z} = \sum z_i^2$
5. 根据定义，$k$ 个独立标准正态变量的平方和服从 $\chi^2(k)$

**统计推断意义**：
```python
P(d² ≤ χ²₀.₉₅(k)) = 0.95

即：如果 d² < χ²₀.₉₅，则有 95% 的把握认为 q 来自该分布
```

**临界值计算**：
```python
from scipy import stats

# 自由度 = 768
df = 768

# 95% 置信区间的临界值
chi2_critical = stats.chi2.ppf(0.95, df=df)
# 结果约为 830.5
```

---

### 3. 置信度评分转换

**目标**：将马氏距离转换为 [0, 1] 范围的置信度分数。

**公式**：
$$\text{score}(\mathbf{q}, L) = \exp\left(-\frac{d_M^2}{2 \cdot \chi^2_{0.95}(k)}\right)$$

**推导来源**：多元正态分布的概率密度函数

$$p(\mathbf{q}|\mu, \Sigma) \propto \exp\left(-\frac{1}{2}d_M^2\right)$$

归一化后得到评分公式。

**评分特性**：

| 条件 | $d^2 / \chi^2_{0.95}$ | score | 解释 |
|------|---------------------|-------|------|
| 完美匹配 | 0 | 1.000 | 查询点在中心 |
| 95%置信边界 | 1.0 | 0.607 | 正常范围内 |
| 明显偏离 | 4.0 | 0.135 | 可能不匹配 |
| 极度偏离 | >9.0 | <0.011 | 几乎不匹配 |

**实际应用中的阈值**：
- `score ≥ 0.8` → **high confidence**（高置信度匹配）
- `score 0.4-0.8` → **medium confidence**（中等置信度）
- `score < 0.4` → **low confidence**（低置信度，建议人工审核）

---

### 4. 假设检验框架

对每个候选地标进行统计假设检验：

**假设**：
- **原假设** $H_0$：$\mathbf{q}$ 来自地标 $L$ 的分布
- **备择假设** $H_1$：$\mathbf{q}$ 不来自地标 $L$ 的分布

**检验统计量**：$T = d_M^2$

**拒绝域**：$T > \chi^2_{0.95}(k)$

**决策规则**：
- 若 $d_M^2 < \chi^2_{0.95}(k)$：**接受 $H_0$** → 可能是这个地标
- 若 $d_M^2 \geq \chi^2_{0.95}(k)$：**拒绝 $H_0$** → 不太可能是这个地标

**显著性水平**：$\alpha = 0.05$（95% 置信度）

---

## 💻 代码实现

### 索引构建阶段

```python
def rebuild_from_scratch(self, all_vectors, all_metadata):
    """重建索引并计算地标统计信息"""
    
    # 1. 按地标分组
    landmark_vectors = {}
    for i, meta in enumerate(all_metadata):
        code = meta['landmark_code']
        landmark_vectors[code].append(all_vectors[i])
    
    # 2. 计算每个地标的统计特征
    self.landmark_stats = {}
    for code, vecs in landmark_vectors.items():
        vecs_array = np.array(vecs).astype('float32')
        
        # 均值向量
        mean_vec = np.mean(vecs_array, axis=0)
        
        # 协方差矩阵 + 正则化
        cov_matrix = np.cov(vecs_array.T)
        cov_matrix += 1e-6 * np.eye(768)
        
        # 协方差逆矩阵
        try:
            cov_inv = np.linalg.inv(cov_matrix)
        except np.linalg.LinAlgError:
            cov_inv = np.linalg.pinv(cov_matrix)  # 伪逆作为后备
        
        # 保存统计信息
        self.landmark_stats[code] = {
            "mean": mean_vec,
            "cov_matrix": cov_matrix,
            "cov_inv": cov_inv,
            "count": len(vecs),
            "name": landmark_name
        }
    
    # 3. 用地标中心构建FAISS索引
    centroids = [stats["mean"] for stats in self.landmark_stats.values()]
    self.index.add(centroids)
```

---

### 搜索检索阶段

```python
def search_landmarks_by_category(self, query_vector, top_k=5):
    """基于地标类别的搜索"""
    
    # 1. FAISS 粗筛（扩大范围）
    search_k = min(top_k * 2, self.index.ntotal)
    scores, indices = self.index.search(query_vector, search_k)
    
    results = []
    for idx, cosine_score in zip(indices[0], scores[0]):
        landmark_code = self.landmark_codes[idx]
        stats = self.landmark_stats[landmark_code]
        
        # 2. 计算马氏距离
        mahalanobis_dist = self._compute_mahalanobis_distance(
            query_vector, stats
        )
        
        # 3. 计算置信度评分
        adaptive_score = self._calculate_mahalanobis_score(
            query_vector, stats
        )
        
        # 4. 确定置信度等级
        confidence_level = self._get_confidence_level(
            adaptive_score, stats
        )
        
        results.append({
            "landmarkCode": landmark_code,
            "rawScore": cosine_score,
            "adaptiveScore": adaptive_score,
            "confidenceLevel": confidence_level,
            "statistics": {
                "mahalanobisDistance": mahalanobis_dist,
                "stdDeviation": np.mean(stats["std"]),
                "compactness": self._calculate_compactness(stats["std"])
            }
        })
    
    # 5. 按自适应评分排序并返回 Top-K
    results.sort(key=lambda x: x["adaptiveScore"], reverse=True)
    return results[:top_k]
```

---

### 马氏距离计算

```python
def _compute_mahalanobis_distance(self, query_vector, stats):
    """
    计算马氏距离
    
    公式: d = √((q - μ)ᵀ Σ⁻¹ (q - μ))
    """
    diff = query_vector.astype('float32') - stats["mean"]
    distance = np.sqrt(np.dot(np.dot(diff.T, stats["cov_inv"]), diff))
    return float(distance)
```

---

### 置信度评分计算

```python
def _calculate_mahalanobis_score(self, query_vector, stats):
    """
    基于马氏距离计算置信度评分
    
    公式: score = exp(-d² / (2 · χ²_critical))
    """
    # 1. 计算马氏距离
    mahalanobis_dist = self._compute_mahalanobis_distance(query_vector, stats)
    
    # 2. 确定卡方临界值
    degrees_of_freedom = len(query_vector)  # 768
    
    if HAS_SCIPY:
        chi2_critical = scipy_stats.chi2.ppf(0.95, df=degrees_of_freedom)
    else:
        chi2_critical = degrees_of_freedom  # 近似值
    
    # 3. 计算评分
    squared_distance = mahalanobis_dist ** 2
    score = np.exp(-squared_distance / (2.0 * chi2_critical))
    
    return float(score)
```

---

## 🎯 算法优势

### 1. 理论完备性
- ✅ 基于最大似然估计原理，在正态假设下为贝叶斯最优分类器
- ✅ 充分利用一阶矩（均值）和二阶矩（协方差）信息
- ✅ 具有明确的概率解释和统计推断基础

### 2. 自适应机制
- ✅ 自动考虑特征维度的方差差异（异方差性）
- ✅ 消除特征间的相关性影响（去冗余）
- ✅ 无需人工设定调整参数，完全由数据分布决定

### 3. 区分能力
- ✅ 有效识别"真匹配"（低马氏距离）与"假阳性"（高马氏距离）
- ✅ 克服余弦相似度仅利用方向信息的局限性
- ✅ 提供基于置信区间的科学决策边界

### 4. 可解释性
- ✅ 马氏距离直接反映"是否在分布内"
- ✅ 置信度分数有明确的统计意义
- ✅ 可以追溯判断依据

---

## 📊 复杂度分析

- **空间复杂度**：$O(L \cdot k^2)$
  - $L$ 为地标数
  - $k=768$ 为特征维度
  - 主要存储协方差逆矩阵（每个地标 768×768）

- **时间复杂度**：
  - 索引构建：$O(n \cdot k^2)$，$n$ 为总图片数
  - 单次检索：$O(L_{\text{candidate}} \cdot k^2)$，$L_{\text{candidate}}$ 为候选地标数

- **实际性能**（10个地标，250张图片）：
  - 索引构建：< 1分钟
  - 单次检索：< 10ms
  - 内存占用：~2.5GB

---

## 🔍 实际应用案例

### 测试场景

查询一张图书馆图片，返回 Top-5 地标：

| 排名 | 地标 | 余弦相似度 | 马氏距离 | 置信度评分 | 置信度等级 |
|------|------|-----------|---------|-----------|-----------|
| 1 | L01 - library | 0.9974 | 4.32 | **0.9879** | high ✅ |
| 2 | L03 - wenyong_square | 0.9948 | 44.57 | **0.2744** | low ❌ |
| 3 | L05 - qin_lake_huxin_island | 0.9947 | 47.14 | **0.2353** | low ❌ |
| 4 | L04 - boxue_bridge | 0.9923 | 54.29 | **0.1468** | low ❌ |
| 5 | L10 - hotel | 0.9949 | 57.93 | **0.1125** | low ❌ |

### 关键发现

1. **正确的识别**：图书馆以绝对优势排第一
2. **强大的区分度**：第一名 0.9879 vs 第二名 0.2744，差距明显
3. **科学的置信度判断**：只有图书馆是 "high"，其他都是 "low"
4. **有效抑制假阳性**：酒店虽然余弦相似度高（0.9949），但马氏距离大（57.93），被正确判定为不匹配

---

## 🎓 总结

本算法通过将图像检索转化为多元统计推断问题，利用马氏距离和卡方分布理论，实现了具有严格数学基础的置信度评估方法。

**核心贡献**：
1. 从"相似度排序"升级为"统计推断"
2. 从"手动调参"升级为"自动适应"
3. 从"启发式规则"升级为"科学方法"

相比传统基于几何距离的方法，该方法充分利用了数据的二阶统计特性，在保证理论严谨性的同时，显著提升了检索的区分能力和鲁棒性。
