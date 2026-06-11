from fastapi import APIRouter, File, UploadFile, HTTPException, Query
from fastapi.responses import JSONResponse
import json
import math
import asyncio
import threading
import torch
from datetime import datetime, timezone
from PIL import Image
from app.utils.image_processor import ImageProcessor
from app.schemas.response import SearchResponse, IndexStatsResponse, CorrectionSampleRequest, CorrectionSampleResponse
from app.config import Config

router = APIRouter()

search_service = None
feature_service = None
sar_search_service = None
inference_semaphore = asyncio.Semaphore(1)
active_inference = 0
active_lock = threading.Lock()


def init_services(fs, ss, sar_ss=None):
    global feature_service, search_service, sar_search_service
    feature_service = fs
    search_service = ss
    sar_search_service = sar_ss


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

        async with inference_semaphore:
            _change_active(1)
            try:
                results = await asyncio.to_thread(
                    search_service.search_similar_landmarks, image, Config.TOP_K_RESULTS
                )
            finally:
                _change_active(-1)

        if not results:
            raise HTTPException(status_code=404, detail="No similar landmarks found")

        low_confidence = all(r["confidenceLevel"] == "low" for r in results)
        response_data = SearchResponse(
            results=results,
            lowConfidence=low_confidence,
            message="Low match score, manual verification recommended" if low_confidence else "Search successful",
        )

        return JSONResponse(
            content=response_data.model_dump(),
            status_code=200,
            media_type="application/json; charset=utf-8",
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")


@router.post("/search/sar")
async def search_sar(file: UploadFile = File(...), use_sar: bool = Query(True)):
    is_valid, error_msg = ImageProcessor.validate_file(file)
    if not is_valid:
        raise HTTPException(status_code=400, detail=error_msg)

    if sar_search_service is None:
        raise HTTPException(status_code=503, detail="SAR search service not available")

    try:
        image = ImageProcessor.read_image(file)

        is_valid_size, size_error = ImageProcessor.validate_image_size(image)
        if not is_valid_size:
            raise HTTPException(status_code=400, detail=size_error)

        async with inference_semaphore:
            _change_active(1)
            try:
                result = await asyncio.to_thread(
                    sar_search_service.search_similar_landmarks,
                    image,
                    top_k=Config.TOP_K_RESULTS,
                    use_sar=use_sar,
                )
            finally:
                _change_active(-1)

        results = result["results"]
        low_confidence = all(r["confidenceLevel"] == "low" for r in results)

        response_data = {
            "results": results,
            "lowConfidence": low_confidence,
            "sarEnabled": result["sarEnabled"],
            "entropy": result["entropy"],
            "trustLevel": result["trustLevel"],
            "trustScore": result["trustScore"],
            "message": "Low match score, manual verification recommended" if low_confidence else "Search successful",
        }

        return JSONResponse(
            content=response_data,
            status_code=200,
            media_type="application/json; charset=utf-8",
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"SAR search failed: {str(e)}")


@router.post("/search/batch")
async def search_landmark_batch(files: list[UploadFile] = File(...)):
    if not files:
        raise HTTPException(status_code=400, detail="files must not be empty")
    if len(files) > Config.SEARCH_BATCH_SIZE:
        raise HTTPException(
            status_code=400,
            detail=f"batch size exceeds {Config.SEARCH_BATCH_SIZE}",
        )

    items = [None] * len(files)
    valid_images = []
    valid_indices = []
    for index, file in enumerate(files):
        is_valid, error_msg = ImageProcessor.validate_file(file)
        if not is_valid:
            items[index] = _batch_error("invalid_image", error_msg, False)
            continue
        try:
            image = ImageProcessor.read_image(file)
            valid_size, size_error = ImageProcessor.validate_image_size(image)
            if not valid_size:
                items[index] = _batch_error("invalid_image", size_error, False)
                continue
            valid_images.append(image)
            valid_indices.append(index)
        except Exception as exc:
            items[index] = _batch_error("invalid_image", str(exc), False)

    if valid_images:
        async with inference_semaphore:
            _change_active(1)
            try:
                try:
                    batch_results = await asyncio.to_thread(
                        search_service.search_batch, valid_images, Config.TOP_K_RESULTS
                    )
                    if len(batch_results) != len(valid_images):
                        raise RuntimeError("batch result count does not match input count")
                    for original_index, results in zip(valid_indices, batch_results):
                        items[original_index] = _batch_success(results)
                except torch.cuda.OutOfMemoryError:
                    if torch.cuda.is_available():
                        torch.cuda.empty_cache()
                    for original_index, image in zip(valid_indices, valid_images):
                        try:
                            result = await asyncio.to_thread(
                                search_service.search_similar_landmarks, image, Config.TOP_K_RESULTS
                            )
                            items[original_index] = _batch_success(result)
                        except torch.cuda.OutOfMemoryError as exc:
                            if torch.cuda.is_available():
                                torch.cuda.empty_cache()
                            items[original_index] = _batch_error("cuda_oom", str(exc), True)
                        except Exception as exc:
                            items[original_index] = _batch_error("inference_failed", str(exc), True)
            except Exception as exc:
                for original_index in valid_indices:
                    if items[original_index] is None:
                        items[original_index] = _batch_error("inference_failed", str(exc), True)
            finally:
                _change_active(-1)

    return {"items": items}


@router.post("/feedback")
async def feedback(
        file: UploadFile = File(...),
        landmark_code: str = Query(..., description="Confirmed landmark code"),
        update_index: bool = Query(True, description="Whether to update the FAISS index"),
):
    is_valid, error_msg = ImageProcessor.validate_file(file)
    if not is_valid:
        raise HTTPException(status_code=400, detail=error_msg)

    if sar_search_service is None:
        raise HTTPException(status_code=503, detail="SAR search service not available")

    try:
        image = ImageProcessor.read_image(file)

        async with inference_semaphore:
            _change_active(1)
            try:
                result = await asyncio.to_thread(
                    sar_search_service.feedback_update,
                    image,
                    landmark_code=landmark_code,
                    update_index=update_index,
                )
            finally:
                _change_active(-1)

        return JSONResponse(
            content=result,
            status_code=200,
            media_type="application/json; charset=utf-8",
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Feedback processing failed: {str(e)}")


@router.post("/sar/reset")
async def reset_sar():
    if sar_search_service is None:
        raise HTTPException(status_code=503, detail="SAR search service not available")

    try:
        result = sar_search_service.reset_sar()
        return JSONResponse(
            content=result,
            status_code=200,
            media_type="application/json; charset=utf-8",
        )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"SAR reset failed: {str(e)}")


@router.post("/index/rebuild")
async def rebuild_index():
    try:
        result = feature_service.extract_and_index_all_landmarks()
        response_data = {"status": "success", "message": "Index rebuilt", "data": result}
        return JSONResponse(
            content=response_data,
            status_code=200,
            media_type="application/json; charset=utf-8",
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Index rebuild failed: {str(e)}")


@router.get("/index/stats", response_model=IndexStatsResponse)
async def get_index_stats():
    try:
        stats = feature_service.get_faiss_stats()
        response_data = IndexStatsResponse(**stats)
        return JSONResponse(
            content=response_data.model_dump(),
            status_code=200,
            media_type="application/json; charset=utf-8",
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get stats: {str(e)}")


@router.get("/health")
async def health_check():
    extractor = getattr(feature_service, "extractor", None)
    device = getattr(extractor, "device", "uninitialized")
    response_data = {
        "status": "healthy",
        "service": "CampusLens AI Search",
        "version": Config.APP_VERSION,
        "sarAvailable": sar_search_service is not None,
        "device": device,
        "gpuName": torch.cuda.get_device_name(0) if device == "cuda" and torch.cuda.is_available() else None,
        "cudaVersion": torch.version.cuda,
        "modelReady": extractor is not None,
        "maxBatchSize": Config.SEARCH_BATCH_SIZE,
        "activeInference": active_inference,
        "mixedPrecision": bool(getattr(extractor, "mixed_precision", False)),
    }
    return JSONResponse(
        content=response_data,
        status_code=200,
        media_type="application/json; charset=utf-8",
    )


def _batch_error(code: str, message: str, retryable: bool):
    return {
        "success": False,
        "response": None,
        "errorCode": code,
        "message": message,
        "retryable": retryable,
    }


def _batch_success(results):
    low_confidence = all(r["confidenceLevel"] == "low" for r in results)
    return {
        "success": True,
        "response": {
            "results": results,
            "lowConfidence": low_confidence,
            "message": "Low match score, manual verification recommended"
            if low_confidence else "Search successful",
        },
        "errorCode": None,
        "message": None,
        "retryable": False,
    }


def _change_active(delta: int):
    global active_inference
    with active_lock:
        active_inference += delta


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
        content=response_data.model_dump(),
        status_code=200,
        media_type="application/json; charset=utf-8"
    )


def _review_score(top_results, confirmed_code: str) -> float:
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
        "topResults": [item.model_dump() for item in payload.topResults],
        "suggestAccept": response.suggestAccept,
        "reviewScore": response.reviewScore,
        "reason": response.reason,
        "sarEligible": response.sarEligible,
        "nextAction": response.nextAction,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }
    with Config.CORRECTION_SAMPLES_MANIFEST.open("a", encoding="utf-8") as fp:
        fp.write(json.dumps(record, ensure_ascii=False) + "\n")
