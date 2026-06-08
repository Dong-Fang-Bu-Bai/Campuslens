from fastapi import APIRouter, File, UploadFile, HTTPException, Query
from fastapi.responses import JSONResponse
from typing import Dict, Optional
from PIL import Image
from app.utils.image_processor import ImageProcessor
from app.schemas.response import SearchResponse, IndexStatsResponse
from app.config import Config

router = APIRouter()

search_service = None
feature_service = None
sar_search_service = None


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


@router.post("/search/sar")
async def search_sar(file: UploadFile = File(...), use_sar: bool = Query(True)):
    """
    SAR-enhanced image search.
    
    Args:
        file: Query image file
        use_sar: Enable SAR test-time adaptation (default: True)
    
    Returns:
        Search results with SAR adaptation information
    """
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
        
        results = sar_search_service.search_similar_landmarks(
            image, 
            top_k=Config.TOP_K_RESULTS,
            use_sar=use_sar
        )
        
        if not results:
            raise HTTPException(status_code=404, detail="No similar landmarks found")
        
        low_confidence = all(r['confidenceLevel'] == 'low' for r in results)
        
        response_data = {
            "results": results,
            "lowConfidence": low_confidence,
            "sarEnabled": use_sar,
            "message": "Low match score, manual verification recommended" if low_confidence else "Search successful"
        }
        
        return JSONResponse(
            content=response_data,
            status_code=200,
            media_type="application/json; charset=utf-8"
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"SAR search failed: {str(e)}")


@router.post("/feedback")
async def feedback(
    file: UploadFile = File(...),
    landmark_code: str = Query(..., description="Confirmed landmark code"),
    update_index: bool = Query(True, description="Whether to update the FAISS index")
):
    """
    Process user feedback for model adaptation and index update.
    Label confidence is automatically calculated based on model prediction.
    
    Args:
        file: Image from user feedback
        landmark_code: Confirmed landmark code (e.g., "L01")
        update_index: Whether to add the feature to FAISS index
    
    Returns:
        Feedback processing status
    """
    is_valid, error_msg = ImageProcessor.validate_file(file)
    if not is_valid:
        raise HTTPException(status_code=400, detail=error_msg)
    
    if sar_search_service is None:
        raise HTTPException(status_code=503, detail="SAR search service not available")
    
    try:
        image = ImageProcessor.read_image(file)
        
        result = sar_search_service.feedback_update(
            image,
            landmark_code=landmark_code,
            update_index=update_index
        )
        
        return JSONResponse(
            content=result,
            status_code=200,
            media_type="application/json; charset=utf-8"
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Feedback processing failed: {str(e)}")


@router.post("/sar/reset")
async def reset_sar():
    """
    Reset SAR adapter to initial state.
    
    Returns:
        Reset status
    """
    if sar_search_service is None:
        raise HTTPException(status_code=503, detail="SAR search service not available")
    
    try:
        result = sar_search_service.reset_sar()
        
        return JSONResponse(
            content=result,
            status_code=200,
            media_type="application/json; charset=utf-8"
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
        "version": Config.APP_VERSION,
        "sarAvailable": sar_search_service is not None
    }
    return JSONResponse(
        content=response_data,
        status_code=200,
        media_type="application/json; charset=utf-8"
    )