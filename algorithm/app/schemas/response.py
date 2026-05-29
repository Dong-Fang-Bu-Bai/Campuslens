from pydantic import BaseModel
from typing import List, Optional


class SearchResult(BaseModel):
    """地标搜索结果"""
    rank: int
    landmarkCode: str
    landmarkName: str
    score: float  # 置信度评分（基于马氏距离）
    confidenceLevel: str  # 置信度等级: high, medium, low
    mahalanobisDistance: float  # 马氏距离（越小越匹配）


class SearchResponse(BaseModel):
    results: List[SearchResult]
    lowConfidence: bool = False
    message: str = "检索成功"


class IndexStatsResponse(BaseModel):
    status: str
    totalVectors: Optional[int] = None
    dimension: Optional[int] = None
    indexedLandmarks: Optional[int] = None
