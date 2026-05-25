# 地标检索算法实现详解

## 📋 目录

1. [系统架构](#系统架构)
2. [数据流程](#数据流程)
3. [核心组件](#核心组件)
4. [代码实现](#代码实现)
5. [性能优化](#性能优化)

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
   b. 转换为置信度评分 score
   c. 确定置信度等级
   ↓
6. 按评分排序，返回 Top-K
```

---

## 核心组件

### 1. FAISSManager

**位置**: `app/utils/faiss_manager.py`

**主要功能**:
- 地标统计信息计算
- 马氏距离计算
- 置信度评分转换
- FAISS 索引管理

**关键方法**:

```python
class FAISSManager:
    # 索引构建
    def rebuild_from_scratch(self, all_vectors, all_metadata)
    
    # 搜索检索
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

### 3. DINOv2Extractor

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

### 置信度评分实现

```python
def _calculate_mahalanobis_score(self, query_vector: np.ndarray, stats: dict) -> float:
    """
    基于马氏距离计算自适应评分
    
    公式: score = exp(-d² / (2 · χ²_critical))
    
    Args:
        query_vector: 查询图片的特征向量 (768,)
        stats: 地标统计信息字典
    
    Returns:
        置信度评分 [0, 1]
    """
    # Step 1: 计算马氏距离
    mahalanobis_dist = self._compute_mahalanobis_distance(query_vector, stats)
    
    # Step 2: 确定自由度（特征维度数）
    degrees_of_freedom = len(query_vector)  # 768
    
    # Step 3: 计算卡方分布的95%分位数
    if HAS_SCIPY:
        chi2_critical = scipy_stats.chi2.ppf(0.95, df=degrees_of_freedom)
        # 对于 df=768，结果约为 830.5
    else:
        # 近似：对于大自由度，χ²(df) ≈ df
        chi2_critical = degrees_of_freedom
    
    # Step 4: 将马氏距离的平方转换为概率分数
    squared_distance = mahalanobis_dist ** 2
    score = np.exp(-squared_distance / (2.0 * chi2_critical))
    
    return float(score)
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
    基于地标类别的搜索
    
    Args:
        query_vector: 查询图片的特征向量
        top_k: 返回的地标数量
        confidence_threshold: 置信度阈值
    
    Returns:
        地标列表，包含统计信息和自适应评分
    """
    if self.index is None or self.index.ntotal == 0:
        raise ValueError("FAISS index is empty, please rebuild index first")
    
    if not self.landmark_stats:
        raise ValueError("Landmark statistics not available, please rebuild index")
    
    # Step 1: 归一化查询向量
    query_norm = query_vector.copy().astype('float32')
    faiss.normalize_L2(query_norm.reshape(1, -1))
    
    # Step 2: FAISS 粗筛（扩大范围以便后续过滤）
    search_k = min(top_k * 2, self.index.ntotal)
    scores, indices = self.index.search(query_norm.reshape(1, -1), search_k)
    
    # Step 3: 对每个候选地标计算马氏距离评分
    results = []
    for idx, score in zip(indices[0], scores[0]):
        if idx == -1:  # FAISS 返回 -1 表示无结果
            continue
        
        landmark_code = self.landmark_codes[idx]
        stats = self.landmark_stats[landmark_code]
        
        # 3.1 计算基于马氏距离的自适应置信度
        adaptive_score = self._calculate_mahalanobis_score(query_vector, stats)
        
        # 3.2 计算原始余弦相似度（用于对比）
        raw_cosine = float(score)
        
        # 3.3 计算置信度等级
        confidence_level = self._get_confidence_level(adaptive_score, stats)
        
        # 3.4 计算马氏距离
        mahalanobis_dist = self._compute_mahalanobis_distance(query_vector, stats)
        
        results.append({
            "rank": 0,  # 稍后排序
            "landmarkCode": landmark_code,
            "landmarkName": stats["name"],
            "rawScore": round(raw_cosine, 4),
            "adaptiveScore": round(float(adaptive_score), 4),
            "confidence": round(float(adaptive_score), 4),
            "confidenceLevel": confidence_level,
            "imageCount": stats["count"],
            "statistics": {
                "avgSimilarity": round(raw_cosine, 4),
                "mahalanobisDistance": round(mahalanobis_dist, 4),
                "stdDeviation": round(float(np.mean(stats["std"])), 4),
                "compactness": self._calculate_compactness(stats["std"]),
            }
        })
    
    # Step 4: 按自适应评分排序
    results.sort(key=lambda x: x["adaptiveScore"], reverse=True)
    
    # Step 5: 取 Top-K 并更新排名
    top_results = results[:top_k]
    for i, result in enumerate(top_results, 1):
        result["rank"] = i
    
    return top_results
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
3. **延迟加载**：只加载常用 landmar 的统计信息

---

## 测试与验证

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
        score = self.manager._calculate_mahalanobis_score(query, stats)
        
        self.assertGreaterEqual(score, 0.0)
        self.assertLessEqual(score, 1.0)

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

distances = [result['statistics']['mahalanobisDistance'] for result in results]
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
          f"raw={result['rawScore']:.4f}, "
          f"adaptive={result['adaptiveScore']:.4f}, "
          f"d_mahalanobis={result['statistics']['mahalanobisDistance']:.2f}")
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

---

**最后更新**: 2026-05-19  
**版本**: v2.0
