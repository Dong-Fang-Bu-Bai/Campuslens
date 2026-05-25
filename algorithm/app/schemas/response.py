from pydantic import BaseModel
from typing import List, Optional


class LandmarkStatistics(BaseModel):
    """地标统计信息"""
    avgSimilarity: float  # 平均相似度（余弦相似度）
    mahalanobisDistance: float  # 马氏距离
    stdDeviation: float  # 标准差
    compactness: str  # 紧凑程度: very_compact, compact, moderate, dispersed, very_dispersed


class SearchResult(BaseModel):
    """地标搜索结果"""
    rank: int
    landmarkCode: str
    landmarkName: str
    rawScore: float  # 原始相似度
    adaptiveScore: float  # 自适应评分
    confidence: float  # 置信度
    confidenceLevel: str  # 置信度等级: high, medium, low
    imageCount: int  # 该地标的图片数量
    statistics: LandmarkStatistics  # 统计信息


class SearchResponse(BaseModel):
    results: List[SearchResult]
    lowConfidence: bool = False
    message: str = "检索成功"


class IndexStatsResponse(BaseModel):
    status: str
    totalVectors: Optional[int] = None
    dimension: Optional[int] = None
    indexedLandmarks: Optional[int] = None
