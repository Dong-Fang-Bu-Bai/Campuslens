from pydantic import BaseModel
from typing import List, Optional


class SearchResult(BaseModel):
    rank: int
    landmarkCode: str
    landmarkName: str
    imagePath: str
    imageFilename: str
    score: float


class SearchResponse(BaseModel):
    results: List[SearchResult]
    lowConfidence: bool = False
    message: str = "检索成功"


class IndexStatsResponse(BaseModel):
    status: str
    totalVectors: Optional[int] = None
    dimension: Optional[int] = None
    indexedLandmarks: Optional[int] = None
