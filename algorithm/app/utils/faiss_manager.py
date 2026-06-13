import faiss
import numpy as np
from pathlib import Path
from typing import List, Tuple
import pickle
from app.config import Config
from app.utils.scoring import mahalanobis_match_score, match_level


class FAISSManager:
    def __init__(self, dimension: int, index_dir=None):
        self.dimension = dimension
        self.index = None
        self.metadata = []
        self.index_dir = Path(index_dir or Config.FAISS_INDEX_DIR)
        self.index_path = self.index_dir / "landmark_index.faiss"
        self.metadata_path = self.index_dir / "metadata.pkl"
        
        # 地标级别的统计信息
        self.landmark_stats = {}  # {landmark_code: {"mean", "std", "count", "name"}}
        self.landmark_codes = []  # 地标代码列表（与索引顺序对应）
        self.stats_path = self.index_dir / "landmark_stats.pkl"
    
    def create_index(self):
        self.index = faiss.IndexFlatIP(self.dimension)
        print(f"Created new FAISS index with dimension {self.dimension}")
    
    def load_index(self) -> bool:
        if self.index_path.exists() and self.metadata_path.exists():
            serialized = np.frombuffer(self.index_path.read_bytes(), dtype=np.uint8)
            self.index = faiss.deserialize_index(serialized)
            with open(self.metadata_path, 'rb') as f:
                self.metadata = pickle.load(f)
            
            # 加载地标统计信息（如果存在）
            if self.stats_path.exists():
                with open(self.stats_path, 'rb') as f:
                    stats_data = pickle.load(f)
                    self.landmark_stats = stats_data.get('landmark_stats', {})
                    self.landmark_codes = stats_data.get('landmark_codes', [])
                print(f"Loaded FAISS index with {len(self.metadata)} vectors and {len(self.landmark_stats)} landmarks")
            else:
                print(f"Loaded FAISS index with {len(self.metadata)} vectors (no landmark stats)")
            
            return True
        return False
    
    def save_index(self):
        self.index_dir.mkdir(parents=True, exist_ok=True)
        # FAISS FileIOWriter cannot reliably open Unicode Windows paths.
        self.index_path.write_bytes(faiss.serialize_index(self.index).tobytes())
        with open(self.metadata_path, 'wb') as f:
            pickle.dump(self.metadata, f)
        
        # 保存地标统计信息
        stats_data = {
            'landmark_stats': self.landmark_stats,
            'landmark_codes': self.landmark_codes
        }
        with open(self.stats_path, 'wb') as f:
            pickle.dump(stats_data, f)
        
        print(f"Saved FAISS index with {len(self.metadata)} vectors and {len(self.landmark_stats)} landmarks")
    
    def add_vectors(self, vectors: np.ndarray, metadata_list: List[dict]):
        if self.index is None:
            self.create_index()
        
        vectors = vectors.astype('float32')
        faiss.normalize_L2(vectors)
        
        self.index.add(vectors)
        self.metadata.extend(metadata_list)
    
    def search(self, query_vector: np.ndarray, top_k: int = 5) -> List[Tuple[dict, float]]:
        if self.index is None or self.index.ntotal == 0:
            raise ValueError("FAISS 索引为空，请先构建索引")
        
        query_vector = query_vector.astype('float32').reshape(1, -1)
        faiss.normalize_L2(query_vector)
        
        scores, indices = self.index.search(query_vector, top_k)
        
        results = []
        for score, idx in zip(scores[0], indices[0]):
            if idx != -1:
                results.append((self.metadata[idx], float(score)))
        
        return results
    
    def rebuild_from_scratch(self, all_vectors: np.ndarray, all_metadata: List[dict]):
        """
        重建索引，同时构建地标级别的统计信息
        """
        self.create_index()
        self.metadata = []
        
        # 按地标分组计算统计特征
        print("Computing landmark statistics...")
        landmark_vectors = {}
        for i, meta in enumerate(all_metadata):
            code = meta['landmark_code']
            if code not in landmark_vectors:
                landmark_vectors[code] = []
            landmark_vectors[code].append(all_vectors[i])
        
        # 计算每个地标的统计特征
        self.landmark_stats = {}
        landmark_centroids = []
        self.landmark_codes = []
        
        for code, vecs in landmark_vectors.items():
            vecs_array = np.array(vecs).astype('float32')
            
            # 计算均值（地标中心）
            mean_vec = np.mean(vecs_array, axis=0)
            
            # 计算标准差（地标内变化程度）
            std_vec = np.std(vecs_array, axis=0)
            
            # 计算协方差矩阵（用于马氏距离）
            # 添加正则化项避免矩阵奇异
            cov_matrix = np.cov(vecs_array.T)
            regularization = 1e-6 * np.eye(cov_matrix.shape[0])
            cov_matrix += regularization
            
            # 计算协方差矩阵的逆（用于马氏距离计算）
            try:
                cov_inv = np.linalg.inv(cov_matrix)
            except np.linalg.LinAlgError:
                # 如果仍然奇异，使用伪逆
                cov_inv = np.linalg.pinv(cov_matrix)
            
            # 归一化均值向量
            mean_vec_normalized = mean_vec.copy()
            faiss.normalize_L2(mean_vec_normalized.reshape(1, -1))
            
            # 获取地标名称
            landmark_name = next((m['landmark_name'] for m in all_metadata if m['landmark_code'] == code), code)
            
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
        
        # 用地标中心向量构建 FAISS 索引
        centroids_array = np.array(landmark_centroids).astype('float32')
        self.index.add(centroids_array)
        
        # 添加原始图片向量到 metadata（用于后续可能的详细查询）
        self.metadata = all_metadata
        
        # 保存索引和统计信息
        self.save_index()
        
        print(f"[OK] Landmark index built: {len(self.landmark_codes)} landmarks")
    
    def get_index_stats(self) -> dict:
        if self.index is None:
            return {"status": "not_initialized"}
        
        return {
            "status": "ready",
            "totalVectors": self.index.ntotal,
            "dimension": self.dimension,
            "indexedLandmarks": len(set(m.get('landmark_code') for m in self.metadata))
        }
    
    def search_landmarks_by_category(self, query_vector: np.ndarray, top_k: int = 5, confidence_threshold: float = 0.7) -> List[dict]:
        """
        基于地标类别的搜索
        利用地标统计特征计算经验匹配分
        
        Args:
            query_vector: 查询图片的特征向量
            top_k: 返回的地标数量
            confidence_threshold: 兼容旧调用，当前不参与匹配分计算
        
        Returns:
            地标列表，包含马氏距离和经验归一化匹配分
        """
        if self.index is None or self.index.ntotal == 0:
            raise ValueError("FAISS index is empty, please rebuild index first")
        
        if not self.landmark_stats:
            raise ValueError("Landmark statistics not available, please rebuild index")
        
        # 归一化查询向量
        query_norm = query_vector.copy().astype('float32')
        faiss.normalize_L2(query_norm.reshape(1, -1))
        
        # FAISS 快速召回候选地标（基于余弦相似度）
        # 扩大召回范围，确保不遗漏真正匹配的地标
        search_k = min(max(top_k * 5, 30), self.index.ntotal)
        _, indices = self.index.search(query_norm.reshape(1, -1), search_k)
        
        # 对每个候选地标计算马氏距离和经验匹配分（重排序）
        results = []
        for idx in indices[0]:
            if idx == -1:  # FAISS 返回 -1 表示无结果
                continue
            
            landmark_code = self.landmark_codes[idx]
            stats = self.landmark_stats[landmark_code]
            
            # 计算马氏距离
            mahalanobis_dist = self._compute_mahalanobis_distance(query_vector, stats)
            
            # 计算基于马氏距离的经验匹配分与等级
            score = mahalanobis_match_score(mahalanobis_dist)
            confidence_level = self._get_confidence_level(score, stats)
            
            results.append({
                "rank": 0,  # 稍后排序
                "landmarkCode": landmark_code,
                "landmarkName": stats["name"],
                "score": round(float(score), 4),
                "confidenceLevel": confidence_level,
                "mahalanobisDistance": round(float(mahalanobis_dist), 4)
            })
        
        # 按经验匹配分排序（降序）
        results.sort(key=lambda x: x["score"], reverse=True)
        
        # 取 Top-K 并更新排名
        top_results = results[:top_k]
        for i, result in enumerate(top_results, 1):
            result["rank"] = i
        
        return top_results
    
    def _compute_mahalanobis_distance(self, query_vector: np.ndarray, stats: dict) -> float:
        """
        计算查询向量到地标分布的马氏距离
        
        马氏距离考虑了数据的协方差结构，能够判断样本是否属于该分布
        公式：d = sqrt((x - μ)^T Σ^(-1) (x - μ))
        """
        diff = query_vector.astype('float32') - stats["mean"]
        distance = np.sqrt(np.dot(np.dot(diff.T, stats["cov_inv"]), diff))
        return float(distance)
    
    def _calculate_mahalanobis_score(self, query_vector: np.ndarray, stats: dict) -> float:
        """
        基于马氏距离计算经验匹配分（Sigmoid 归一化）
        
        核心思想：
        - 马氏距离越小，查询图越接近候选地标的特征分布中心
        - 使用对数变换压缩距离范围，再用 Sigmoid 映射到 [0, 1]
        - score 仅用于排序、展示区分度和辅助判断，不具备概率或统计置信度含义
        """
        mahalanobis_dist = self._compute_mahalanobis_distance(query_vector, stats)
        return mahalanobis_match_score(mahalanobis_dist)
    
    def _calculate_adaptive_score(self, raw_score: float, stats: dict) -> float:
        """
        【已废弃】旧的自适应评分方法
        当前检索使用基于马氏距离的经验匹配分
        """
        avg_std = np.mean(stats["std"])
        adjustment_factor = self._get_adjustment_factor(stats["std"])
        
        # 自适应调整：分散的地标给予宽松标准
        adaptive_score = raw_score * (1.0 + adjustment_factor * avg_std)
        
        # 限制在 [0, 1] 范围内
        return np.clip(adaptive_score, 0.0, 1.0)
    
    def _get_adjustment_factor(self, std_vector: np.ndarray) -> float:
        """
        计算调整因子
        
        方差越大，调整因子越大，评判越宽松
        """
        avg_std = np.mean(std_vector)
        
        # 使用 sigmoid 函数将标准差映射到 [0, 0.5] 范围
        # 这样调整幅度不会太大
        adjustment = 0.5 / (1.0 + np.exp(-10 * (avg_std - 0.1)))
        
        return adjustment
    
    def _calculate_compactness(self, std_vector: np.ndarray) -> str:
        """
        计算地标的紧凑程度
        """
        avg_std = np.mean(std_vector)
        
        if avg_std < 0.05:
            return "very_compact"  # 非常紧凑
        elif avg_std < 0.1:
            return "compact"  # 紧凑
        elif avg_std < 0.15:
            return "moderate"  # 中等
        elif avg_std < 0.2:
            return "dispersed"  # 分散
        else:
            return "very_dispersed"  # 非常分散
    
    def _get_confidence_level(self, score: float, stats: dict) -> str:
        """
        根据经验匹配分确定展示等级
        """
        return match_level(score)
