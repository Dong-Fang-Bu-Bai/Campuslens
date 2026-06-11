from pydantic import BaseModel
from typing import List, Optional


class SearchResult(BaseModel):
    """地标搜索结果"""
    rank: int
    landmarkCode: str
    landmarkName: str
    score: float  # 经验归一化匹配分（非概率值）
    confidenceLevel: str  # 兼容字段，表示匹配等级: high, medium, low
    mahalanobisDistance: float  # 马氏距离（越小越匹配）


class SearchResponse(BaseModel):
    results: List[SearchResult]
    lowConfidence: bool = False  # 兼容字段，表示所有候选均为低匹配等级
    message: str = "检索成功"


class IndexStatsResponse(BaseModel):
    status: str
    totalVectors: Optional[int] = None
    dimension: Optional[int] = None
    indexedLandmarks: Optional[int] = None


class AdaptationTopResult(BaseModel):
    rank: int
    landmarkCode: str
    score: float
    mahalanobisDistance: Optional[float] = None


class CorrectionSampleRequest(BaseModel):
    sampleId: int
    feedbackId: int
    searchRecordId: int
    imageUrl: str
    confirmedLandmarkCode: str
    predictedLandmarkCode: Optional[str] = None
    feedbackType: str
    comment: Optional[str] = None
    topResults: List[AdaptationTopResult]


class CorrectionSampleResponse(BaseModel):
    suggestAccept: bool
    reviewScore: float
    reason: str
    sarEligible: bool
    nextAction: str
