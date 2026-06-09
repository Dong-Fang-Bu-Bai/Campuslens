from fastapi import APIRouter, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
import json
import math
from datetime import datetime, timezone
from PIL import Image
from app.utils.image_processor import ImageProcessor
from app.schemas.response import (
    CorrectionSampleRequest,
    CorrectionSampleResponse,
    SearchResponse,
    IndexStatsResponse,
)
from app.config import Config

router = APIRouter()

search_service = None
feature_service = None


def init_services(fs, ss):
    global feature_service, search_service
    feature_service = fs
    search_service = ss


@router.post("/search", response_model=SearchResponse)
async def search_landmark(file: UploadFile = File(...)):
    is_valid, error_msg = ImageProcessor.validate_file(file)
    if not is_valid:
        raise HTTPException(status_code=400, detail=error_msg)
    
    try:
        image = ImageProcessor.read_image(file)
        
        is_valid_size, size_error = ImageProcessor.validate_image_size(image)
        if not is_valid_size:
            raise HTTPException(status_code=400, detail=size_error)
        
        results = search_service.search_similar_landmarks(image, top_k=Config.TOP_K_RESULTS)
        
        if not results:
            raise HTTPException(status_code=404, detail="No similar landmarks found")
        
        low_confidence = all(r['confidenceLevel'] == 'low' for r in results)
        
        response_data = SearchResponse(
            results=results,
            lowConfidence=low_confidence,
            message="Low match score, manual verification recommended" if low_confidence else "Search successful"
        )
        
        return JSONResponse(
            content=response_data.dict(),
            status_code=200,
            media_type="application/json; charset=utf-8"
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")


@router.post("/index/rebuild")
async def rebuild_index():
    try:
        result = feature_service.extract_and_index_all_landmarks()
        response_data = {"status": "success", "message": "Index rebuilt", "data": result}
        return JSONResponse(
            content=response_data,
            status_code=200,
            media_type="application/json; charset=utf-8"
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Index rebuild failed: {str(e)}")


@router.get("/index/stats", response_model=IndexStatsResponse)
async def get_index_stats():
    try:
        stats = feature_service.get_faiss_stats()
        response_data = IndexStatsResponse(**stats)
        return JSONResponse(
            content=response_data.dict(),
            status_code=200,
            media_type="application/json; charset=utf-8"
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get stats: {str(e)}")


@router.get("/health")
async def health_check():
    response_data = {
        "status": "healthy",
        "service": "CampusLens AI Search",
        "version": Config.APP_VERSION
    }
    return JSONResponse(
        content=response_data,
        status_code=200,
        media_type="application/json; charset=utf-8"
    )


@router.post("/adaptation/correction-samples", response_model=CorrectionSampleResponse)
async def receive_correction_sample(payload: CorrectionSampleRequest):
    if not payload.topResults:
        raise HTTPException(status_code=400, detail="topResults must not be empty")

    confirmed = payload.confirmedLandmarkCode
    matched = next((item for item in payload.topResults if item.landmarkCode == confirmed), None)
    best = max(payload.topResults, key=lambda item: item.score)
    review_score = _review_score(payload.topResults, confirmed)
    sar_eligible = matched is not None and review_score >= 0.40
    suggest_accept = sar_eligible and payload.feedbackType in {"correct", "wrong"}
    next_action = "append_to_manifest" if suggest_accept else "manual_review"
    reason = (
        "confirmed landmark appears in Top results; store as weak-label correction sample"
        if matched is not None
        else "confirmed landmark is outside Top results; keep for manual review"
    )

    response_data = CorrectionSampleResponse(
        suggestAccept=suggest_accept,
        reviewScore=round(review_score, 6),
        reason=reason,
        sarEligible=sar_eligible,
        nextAction=next_action,
    )
    _append_correction_manifest(payload, response_data, best.landmarkCode)
    return JSONResponse(
        content=response_data.dict(),
        status_code=200,
        media_type="application/json; charset=utf-8"
    )


def _review_score(top_results, confirmed_code: str) -> float:
    # Pseudo probability over ranking scores. This uses a SAR-style reliability
    # gate idea without claiming a full SAR implementation.
    logits = [max(0.0, item.score) for item in top_results]
    max_logit = max(logits)
    exp_values = [math.exp(value - max_logit) for value in logits]
    total = sum(exp_values)
    for item, exp_value in zip(top_results, exp_values):
        if item.landmarkCode == confirmed_code:
            return exp_value / total if total else 0.0
    return 0.0


def _append_correction_manifest(
    payload: CorrectionSampleRequest,
    response: CorrectionSampleResponse,
    best_landmark_code: str,
):
    Config.CORRECTION_SAMPLES_MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    record = {
        "sampleId": payload.sampleId,
        "feedbackId": payload.feedbackId,
        "searchRecordId": payload.searchRecordId,
        "imageUrl": payload.imageUrl,
        "confirmedLandmarkCode": payload.confirmedLandmarkCode,
        "predictedLandmarkCode": payload.predictedLandmarkCode,
        "bestLandmarkCode": best_landmark_code,
        "feedbackType": payload.feedbackType,
        "comment": payload.comment,
        "topResults": [item.dict() for item in payload.topResults],
        "suggestAccept": response.suggestAccept,
        "reviewScore": response.reviewScore,
        "reason": response.reason,
        "sarEligible": response.sarEligible,
        "nextAction": response.nextAction,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }
    with Config.CORRECTION_SAMPLES_MANIFEST.open("a", encoding="utf-8") as fp:
        fp.write(json.dumps(record, ensure_ascii=False) + "\n")
