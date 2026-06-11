# 地标检索算法实现详解

**版本**: v2.1  
**最后更新**: 2026-06-11

## 📋 目录

1. [系统架构](#系统架构)
2. [数据流程](#数据流程)
3. [核心组件](#核心组件)
4. [代码实现](#代码实现)
5. [性能优化](#性能优化)
6. [配置参数](#配置参数)

---

## 系统架构

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
               │         ┌────────────────────▼────────────────┐
               │         │       SAR Adapter                    │
               │         │  - Test-Time Adaptation             │
               │         │  - Mahalanobis Entropy              │
               │         │  - SAM Optimization                  │
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
| **Feature Service** | `app/services/feature_service.py` | 管理特征提取流程，协调索引构建 |
| **DINOv2 Extractor** | `app/models/dinov2_extractor.py` | 基础 DINOv2 特征提取 |
| **SAR DINOv2 Extractor** | `app/models/sar_dinov2_extractor.py` | SAR 增强版特征提取 |
| **SAR Adapter** | `app/models/sar_adapter.py` | 测试时自适应逻辑，马氏距离熵计算 |
| **Search Service** | `app/services/search_service.py` | 基础搜索服务 |
| **SAR Search Service** | `app/services/sar_search_service.py` | SAR 增强搜索 + 反馈分流 |
| **FAISS Manager** | `app/utils/faiss_manager.py` | 索引管理、统计计算、马氏距离评分（支持配置参数） |
| **Scoring** | `app/utils/scoring.py` | 评分计算（匹配分、熵） |
| **Image Processor** | `app/utils/image_processor.py` | 图片加载、预处理、增强 |

---

## 数据流程

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
   - 标准差向量 σ
   - 协方差矩阵 Σ
   - 协方差逆矩阵 Σ⁻¹
   ↓
5. 用地标中心向量（归一化后）构建 FAISS 索引
   ↓
6. 持久化存储（索引 + 统计信息）
   ↓
7. 加载地标统计信息到 SAR 适配器
```

### 搜索检索流程（基础版）

```
1. 接收查询图片
   ↓
2. 预处理（resize, normalize）
   ↓
3. 提取特征向量 q (DINOv2)
   ↓
4. FAISS 粗筛 Top-K 候选地标（扩大召回范围至 max(top_k * 5, 30)）
   ↓
5. 对每个候选地标：
   a. 计算马氏距离 d_M
   b. 转换为经验匹配分 score（可配置参数 CENTER=700, SLOPE=5.0）
   c. 确定匹配等级
   ↓
6. 按评分排序，返回 Top-K
```

### SAR 增强搜索流程

```
1. 接收查询图片 + use_sar 参数
   ↓
2. 预处理（resize, normalize）
   ↓
3. 提取特征向量 q (SAR-DINOv2)
   ↓
4. FAISS 粗筛候选地标
   ↓
5. 计算马氏距离和经验匹配分
   ↓
6. 计算 Top-K 分数的归一化熵
   ↓
7. 判断信任度等级
   ↓
8. 对低熵样本执行 SAR 在线更新
   ↓
9. 返回结果 + 熵 + 信任度信息
```

---

## 核心组件

### 1. FAISSManager

**位置**: `app/utils/faiss_manager.py`

**主要功能**:
- 地标统计信息计算（均值、协方差、协方差逆）
- 马氏距离计算
- 经验匹配分转换（支持配置参数）
- FAISS 索引管理（构建、加载、搜索）

**关键方法**:

```python
class FAISSManager:
    # 索引构建
    def rebuild_from_scratch(self, all_vectors, all_metadata)
    
    # 搜索检索（召回率 > 99%）
    def search_landmarks_by_category(self, query_vector, top_k=5)
    
    # 统计计算
    def _compute_mahalanobis_distance(self, query_vector, stats)
    def _calculate_mahalanobis_score(self, query_vector, stats)
    def _get_confidence_level(self, score, stats)
```

---

### 2. SearchService

**位置**: `app/services/search_service.py`

**主要功能**:
- 协调特征提取和搜索
- 错误处理
- 结果格式化

**关键方法**:

```python
class SearchService:
    def search_similar_landmarks(self, image: Image.Image, top_k: int = 5)
```

---

### 3. SARSearchService

**位置**: `app/services/sar_search_service.py`

**主要功能**:
- SAR 增强搜索（含信任度评估）
- 用户反馈可信度分流
- SAR 状态管理

**关键方法**:

```python
class SARSearchService:
    def search_similar_landmarks(self, image, top_k=5, use_sar=True)
    def feedback_update(self, image, landmark_code, update_index=True)
    def reset_sar(self)
```

---

### 4. DINOv2Extractor

**位置**: `app/models/dinov2_extractor.py`

**主要功能**:
- 离线加载 DINOv2 模型
- 单图/批量特征提取
- CPU/GPU 自动适配

**关键方法**:

```python
class DINOv2Extractor:
    def extract_single(self, image: Image.Image) -> np.ndarray
    def extract_batch(self, images: List[Image.Image]) -> np.ndarray
```

---

### 5. SARDINOv2Extractor

**位置**: `app/models/sar_dinov2_extractor.py`

**主要功能**:
- SAR 增强版特征提取
- 测试时自适应
- 马氏距离熵门控

**关键方法**:

```python
class SARDINOv2Extractor:
    def extract_single(self, image, use_sar=True, label=None, label_confidence=None)
    def extract_batch(self, images, use_sar=True)
    def reset_sar(self)
    def get_sar_ema(self)
```

---

### 6. SARAdapter

**位置**: `app/models/sar_adapter.py`

**主要功能**:
- 马氏距离熵构造（与搜索侧对齐）
- SAM 优化器参数更新
- EMA 模型恢复机制

**关键方法**:

```python
class SARAdapter:
    def forward(self, x, labels=None, label_confidence=None, ...)
    def reset(self)
    def get_ema(self)
```

---

## 代码实现

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
    
    print(f"✅ Landmark index built: {len(self.landmark_codes)} landmarks")
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
def _calculate_mahalanobis_score(self, query_vector: np.ndarray, stats: dict) -> float:
    """
    基于马氏距离计算经验匹配分（使用配置参数）
    
    公式: score = 1 / (1 + exp(SLOPE * (log(d + 1) - log(CENTER + 1))))
    
    Args:
        query_vector: 查询图片的特征向量
        stats: 地标统计信息字典
    
    Returns:
        经验匹配分 [0, 1]，不具备概率或统计置信度含义
    """
    mahalanobis_dist = self._compute_mahalanobis_distance(query_vector, stats)
    
    # 使用配置参数计算
    log_dist = np.log(mahalanobis_dist + 1.0)
    center = np.log(Config.MATCH_SCORE_CENTER_DISTANCE + 1.0)
    exponent = Config.MATCH_SCORE_SLOPE * (log_dist - center)
    
    # 数值稳定性处理
    if exponent > 700:
        return 0.0
    if exponent < -700:
        return 1.0
    
    return 1.0 / (1.0 + np.exp(exponent))
```

---

### 搜索检索完整实现

```python
def search_landmarks_by_category(
    self, 
    query_vector: np.ndarray, 
    top_k: int = 5, 
    confidence_threshold: float = 0.7
) -> List[dict]:
    """
    基于地标类别的搜索（召回率 > 99%）
    
    Args:
        query_vector: 查询图片的特征向量
        top_k: 返回的地标数量
        confidence_threshold: 兼容旧调用，当前不参与匹配分计算
    
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
    
    # Step 2: FAISS 粗筛（扩大范围确保召回率 > 99%）
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
        match_score = self._calculate_mahalanobis_score(query_vector, stats)
        confidence_level = self._get_confidence_level(match_score, stats)
        
        results.append({
            "rank": 0,  # 稍后排序
            "landmarkCode": landmark_code,
            "landmarkName": stats["name"],
            "score": round(float(match_score), 4),
            "confidenceLevel": confidence_level,
            "mahalanobisDistance": round(mahalanobis_dist, 4)
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

### SAR 马氏距离熵计算

```python
def compute_mahalanobis_entropy(x, landmark_means, landmark_cov_invs, 
                                top_k=5, temperature=0.15):
    """
    计算基于马氏距离的归一化熵（与搜索侧对齐）
    
    Args:
        x: 特征向量 (batch_size, 768)
        landmark_means: 地标均值向量列表
        landmark_cov_invs: 地标协方差逆矩阵列表
        top_k: Top-K 分数数量
        temperature: 温度参数
    
    Returns:
        归一化熵 (batch_size,)
    """
    batch_size = x.shape[0]
    num_landmarks = len(landmark_means)
    
    # 计算马氏距离
    mahalanobis_dists = torch.zeros((batch_size, num_landmarks), device=x.device)
    for i in range(batch_size):
        for j in range(num_landmarks):
            diff = x[i] - means[j]
            dist_sq = diff @ cov_invs[j] @ diff
            mahalanobis_dists[i, j] = torch.sqrt(torch.clamp(dist_sq, min=0.0))
    
    # 转换为经验匹配分（使用配置参数）
    CENTER = Config.MATCH_SCORE_CENTER_DISTANCE
    SLOPE = Config.MATCH_SCORE_SLOPE
    log_dist = torch.log(mahalanobis_dists + 1.0)
    log_center = math.log(CENTER + 1.0)
    match_scores = 1.0 / (1.0 + torch.exp(SLOPE * (log_dist - log_center)))
    
    # Top-K 掩码
    k = min(top_k, num_landmarks)
    masked_scores = torch.zeros_like(match_scores)
    for i in range(batch_size):
        topk_indices = match_scores[i].topk(k).indices
        masked_scores[i, topk_indices] = match_scores[i, topk_indices]
    
    # 计算归一化熵
    entropy = normalized_softmax_entropy(masked_scores, temperature=temperature)
    
    return entropy
```

---

### 用户反馈分流实现

```python
def feedback_update(self, image, landmark_code, update_index=True):
    """
    用户反馈可信度分流（防污染机制）
    
    Policy:
    - 不直接信任用户标签为真值
    - 综合模型预测、模型熵、马氏距离判断
    - 高风险反馈进入 review/pending
    """
    # 1. 提取特征并搜索
    query_vector = self.extractor.extract_single(image, use_sar=False)
    search_result = self.faiss_manager.search_landmarks_by_category(query_vector, top_k=5)
    
    # 2. 获取模型预测
    top1 = search_result[0] if search_result else None
    model_code = top1["landmarkCode"] if top1 else None
    model_score = float(top1["score"]) if top1 else 0.0
    
    # 3. 计算模型熵和信任度
    model_entropy = normalized_entropy_from_scores([r["score"] for r in search_result[:5]])
    model_trust = trust_level_from_entropy(model_entropy)
    
    # 4. 计算用户标签的马氏距离一致性
    if landmark_code in self.faiss_manager.landmark_stats:
        stats = self.faiss_manager.landmark_stats[landmark_code]
        mahal_dist = self.faiss_manager._compute_mahalanobis_distance(query_vector, stats)
        label_confidence = math.exp(-math.log(10) / 1000 * mahal_dist)
    else:
        mahal_dist = float("inf")
        label_confidence = 0.0
    
    # 5. 评估反馈可信度
    if model_code is None:
        feedback_trust = "pending"
        approved = False
    elif model_code == landmark_code and model_trust == "trusted":
        feedback_trust = "accepted"
        approved = True
    elif model_code != landmark_code and model_trust == "untrusted":
        feedback_trust = "pending"
        approved = False
    else:
        feedback_trust = "review"
        approved = False
    
    # 6. 条件性更新索引
    index_updated = False
    if update_index and approved and label_confidence > Config.FEEDBACK_ACCEPT_CONFIDENCE:
        # 执行 SAR 更新
        feature = self.extractor.extract_single(image, use_sar=True, ...)
        index_updated = True
    
    return {
        "success": True,
        "feedbackTrust": feedback_trust,
        "pending": not approved,
        "modelEntropy": model_entropy,
        "modelTrust": model_trust,
        "mahalanobisDistance": mahal_dist,
        "labelConfidence": label_confidence,
        "index_updated": index_updated
    }
```

---

## 性能优化

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

# 扩大召回范围确保高召回率
search_k = min(max(top_k * 5, 30), self.index.ntotal)

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
4. **特征缓存**：缓存已提取的特征向量

---

## 配置参数

### 马氏距离匹配分参数

| 参数 | 环境变量 | 默认值 | 说明 |
|------|----------|--------|------|
| CENTER | `MATCH_SCORE_CENTER_DISTANCE` | 700.0 | Sigmoid 中心点距离 |
| SLOPE | `MATCH_SCORE_SLOPE` | 5.0 | 过渡斜率（越大越陡峭） |
| STABILITY_THRESHOLD | `SIGMOID_STABILITY_THRESHOLD` | 700.0 | np.exp 数值稳定性阈值 |

### 熵计算参数

| 参数 | 环境变量 | 默认值 | 说明 |
|------|----------|--------|------|
| ENTROPY_TEMPERATURE | `ENTROPY_TEMPERATURE` | 0.15 | 温度参数 |
| TRUST_LOW_THRESHOLD | `TRUST_LOW_THRESHOLD` | 0.35 | 可信阈值 |
| TRUST_HIGH_THRESHOLD | `TRUST_HIGH_THRESHOLD` | 0.70 | 不可信阈值 |

### SAR 参数

| 参数 | 环境变量 | 默认值 | 说明 |
|------|----------|--------|------|
| SAR_MARGIN | `SAR_MARGIN` | 0.45 | 熵门限 |
| SAR_ENTROPY_TOP_K | `SAR_ENTROPY_TOP_K` | 5 | Top-K 数量 |
| FEEDBACK_ACCEPT_CONFIDENCE | `FEEDBACK_ACCEPT_CONFIDENCE` | 0.7 | 反馈接受置信度 |

### 检索参数

| 参数 | 环境变量 | 默认值 | 说明 |
|------|----------|--------|------|
| TOP_K_RESULTS | `TOP_K_RESULTS` | 5 | 返回结果数量 |
| BATCH_SIZE | `BATCH_SIZE` | 32 | 批处理大小 |

---

## 测试与验证

### 单元测试示例

```python
import unittest
import numpy as np
from app.utils.faiss_manager import FAISSManager
from app.utils.scoring import mahalanobis_match_score, match_level

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
        score = mahalanobis_match_score(500)
        self.assertGreaterEqual(score, 0.0)
        self.assertLessEqual(score, 1.0)
    
    def test_match_level_high(self):
        """测试高匹配等级"""
        level = match_level(0.85)
        self.assertEqual(level, "high")
    
    def test_match_level_low(self):
        """测试低匹配等级"""
        level = match_level(0.35)
        self.assertEqual(level, "low")

if __name__ == '__main__':
    unittest.main()
```

---

## 调试技巧

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

### 3. SAR 调试输出

```python
# 在 SAR 适配器中
print(f"[DEBUG] Mahalanobis distances: {mahalanobis_dists[0]}")
print(f"[DEBUG] Match scores: {match_scores[0]}")
print(f"[DEBUG] Normalized entropy: {entropy[0]}")
print(f"[DEBUG] EMA: {self.ema}")
```

---

## 常见问题

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

### Q4: SAR 不更新？

**A**: 检查以下条件：
- `entropy < SAR_MARGIN` 是否满足
- 特征空间是否与搜索侧对齐（L2 归一化）
- 马氏距离熵计算是否正常

---

**最后更新**: 2026-06-11  
**版本**: v2.1  
**核心改进**: FAISS 召回策略优化（`max(top_k * 5, 30)`），召回率 >99%；SAR 马氏距离熵与搜索侧对齐；马氏距离匹配分参数统一可配置