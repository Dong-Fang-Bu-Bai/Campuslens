from fastapi import APIRouter, File, Form, UploadFile, HTTPException
from fastapi.responses import JSONResponse
import json
import math
import asyncio
import threading
import torch
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
from app.utils.scoring import confidence_from_entropy, normalized_entropy_from_scores

router = APIRouter()

search_service = None
feature_service = None
inference_semaphore = asyncio.Semaphore(1)
active_inference = 0
active_lock = threading.Lock()


def init_services(fs, ss):
    global feature_service, search_service
    feature_service = fs
    search_service = ss


@router.post("/search", response_model=SearchResponse)
async def search_landmark(file: UploadFile = File(...), sarMode: bool = Form(False)):
    _ensure_available()
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
                result = await asyncio.to_thread(
                    _search_one, image, Config.TOP_K_RESULTS, sarMode
                )
            finally:
                _change_active(-1)
        
        if not result["results"]:
            raise HTTPException(status_code=404, detail="No similar landmarks found")

        results = result["results"]
        low_confidence = all(r['confidenceLevel'] == 'low' for r in results)
        
        response_data = SearchResponse(
            results=results,
            lowConfidence=low_confidence,
            message="Low match score, manual verification recommended" if low_confidence else "Search successful",
            sarApplied=result.get("sarApplied"),
            trustLevel=result.get("trustLevel"),
            modelVersion=result.get("modelVersion"),
            baseModelVersion=result.get("baseModelVersion"),
            indexVersion=result.get("indexVersion"),
            sarStateVersion=result.get("sarStateVersion"),
            instanceId=result.get("instanceId"),
            instanceRole=result.get("instanceRole"),
        )
        
        return JSONResponse(
            content=response_data.model_dump(),
            status_code=200,
            media_type="application/json; charset=utf-8"
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")


@router.post("/search/batch")
async def search_landmark_batch(files: list[UploadFile] = File(...), sarMode: bool = Form(False)):
    _ensure_available()
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
                        _search_batch, valid_images, Config.TOP_K_RESULTS, sarMode
                    )
                    if len(batch_results) != len(valid_images):
                        raise RuntimeError("batch result count does not match input count")
                    for original_index, result in zip(valid_indices, batch_results):
                        items[original_index] = _batch_success(result)
                except torch.cuda.OutOfMemoryError:
                    if torch.cuda.is_available():
                        torch.cuda.empty_cache()
                    for original_index, image in zip(valid_indices, valid_images):
                        try:
                            result = await asyncio.to_thread(
                                _search_one, image, Config.TOP_K_RESULTS, sarMode
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


@router.post("/index/rebuild")
async def rebuild_index():
    try:
        result = feature_service.start_rebuild()
        response_data = {"status": "accepted", **result}
        return JSONResponse(
            content=response_data,
            status_code=202,
            media_type="application/json; charset=utf-8"
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Index rebuild failed: {str(e)}")


@router.get("/index/rebuild/{job_id}")
async def rebuild_status(job_id: str):
    job = feature_service.rebuild_status(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="rebuild job not found")
    return job


@router.get("/runtime/status")
async def runtime_status():
    return feature_service.runtime_status()


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
    extractor = getattr(feature_service, "extractor", None)
    device = getattr(extractor, "device", "uninitialized")
    runtime = feature_service.runtime_status() if hasattr(feature_service, "runtime_status") else {
        "status": "running",
        "sarEnabled": Config.SAR_ENABLED,
    }
    response_data = {
        "status": "healthy",
        "service": "CampusLens AI Search",
        "version": Config.APP_VERSION,
        "device": device,
        "gpuName": torch.cuda.get_device_name(0) if device == "cuda" and torch.cuda.is_available() else None,
        "cudaVersion": torch.version.cuda,
        "modelReady": extractor is not None,
        "maxBatchSize": Config.SEARCH_BATCH_SIZE,
        "activeInference": active_inference,
        "mixedPrecision": bool(getattr(extractor, "mixed_precision", False)),
        **runtime,
    }
    return JSONResponse(
        content=response_data,
        status_code=200,
        media_type="application/json; charset=utf-8"
    )


def _batch_error(code: str, message: str, retryable: bool):
    return {
        "success": False,
        "response": None,
        "errorCode": code,
        "message": message,
        "retryable": retryable,
    }


def _search_one(image, top_k, sar_mode=False):
    if hasattr(search_service, "search_with_metadata"):
        return search_service.search_with_metadata(image, top_k, sar_mode)
    return {"results": search_service.search_similar_landmarks(image, top_k)}


def _search_batch(images, top_k, sar_mode=False):
    if hasattr(search_service, "search_batch_with_metadata"):
        return search_service.search_batch_with_metadata(images, top_k, sar_mode)
    return [{"results": results} for results in search_service.search_batch(images, top_k)]


def _batch_success(result):
    if isinstance(result, dict):
        results = result["results"]
    else:
        results = result
        result = {}
    low_confidence = all(r["confidenceLevel"] == "low" for r in results)
    return {
        "success": True,
        "response": {
            "results": results,
            "lowConfidence": low_confidence,
            "message": "Low match score, manual verification recommended"
            if low_confidence else "Search successful",
            "sarApplied": result.get("sarApplied"),
            "trustLevel": result.get("trustLevel"),
            "modelVersion": result.get("modelVersion"),
            "baseModelVersion": result.get("baseModelVersion"),
            "indexVersion": result.get("indexVersion"),
            "sarStateVersion": result.get("sarStateVersion"),
            "instanceId": result.get("instanceId"),
            "instanceRole": result.get("instanceRole"),
        },
        "errorCode": None,
        "message": None,
        "retryable": False,
    }


def _change_active(delta: int):
    global active_inference
    with active_lock:
        active_inference += delta


def _ensure_available():
    if getattr(feature_service, "maintenance", False):
        raise HTTPException(
            status_code=503,
            detail="index switch maintenance",
            headers={"Retry-After": "3"},
        )


@router.post("/adaptation/correction-samples", response_model=CorrectionSampleResponse)
async def receive_correction_sample(
    file: UploadFile = File(...),
    payload: str = Form(...),
):
    try:
        payload = CorrectionSampleRequest.model_validate_json(payload)
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"invalid correction payload: {exc}") from exc
    if not payload.topResults:
        raise HTTPException(status_code=400, detail="topResults must not be empty")

    is_valid, error_msg = ImageProcessor.validate_file(file)
    if not is_valid:
        raise HTTPException(status_code=400, detail=error_msg)
    image = ImageProcessor.read_image(file)

    confirmed = payload.confirmedLandmarkCode
    matched = next((item for item in payload.topResults if item.landmarkCode == confirmed), None)
    best = max(payload.topResults, key=lambda item: item.score)
    entropy = normalized_entropy_from_scores([item.score for item in payload.topResults])
    review_score = confidence_from_entropy(entropy)
    sar_eligible = (
        matched is not None
        and matched.score >= Config.CORRECTION_ACCEPT_CONFIDENCE
        and entropy < Config.SAR_ENTROPY_THRESHOLD
    )
    suggest_accept = sar_eligible and payload.feedbackType in {"correct", "wrong"}
    next_action = "rebuild_model_and_index" if suggest_accept else "manual_review"
    reason = (
        "confirmed landmark appears in Top results; store as weak-label correction sample"
        if matched is not None
        else "confirmed landmark is outside Top results; keep for manual review"
    )

    activation = {"activated": False}
    adaptation_error = None
    if suggest_accept:
        next_action = "pending_index"
        reason = "accepted samples are staged by the backend and published only by an index rebuild"

    response_data = CorrectionSampleResponse(
        suggestAccept=suggest_accept,
        reviewScore=round(review_score, 6),
        reason=reason,
        sarEligible=sar_eligible,
        nextAction=next_action,
        modelVersion=activation.get("version"),
        activated=bool(activation.get("activated")),
        adaptationError=adaptation_error,
    )
    _append_correction_manifest(payload, response_data, best.landmarkCode)
    return JSONResponse(
        content=response_data.model_dump(),
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
