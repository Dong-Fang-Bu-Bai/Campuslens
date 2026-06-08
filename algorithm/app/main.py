import sys
from pathlib import Path

# 确保项目根目录在 Python 路径中
project_root = Path(__file__).parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from app.config import Config
from app.services.feature_service import FeatureService
from app.services.search_service import SearchService
from app.api.routes import router, init_services
import json


Config.ensure_directories()

app = FastAPI(
    title=Config.APP_NAME,
    version=Config.APP_VERSION,
    description="CampusLens AI 图像检索服务 - 基于 DINOv2 + FAISS + SAR（纯离线模式）"
)

# 配置 JSON 响应格式化
def custom_json_response(content: dict, status_code: int = 200):
    return JSONResponse(
        content=content,
        status_code=status_code,
        media_type="application/json; charset=utf-8"
    )

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

feature_service = FeatureService(use_sar=True)
search_service = SearchService(feature_service)

# 尝试初始化 SAR 服务
sar_search_service = None
try:
    from app.services.sar_search_service import SARSearchService
    
    # 检查是否存在 SAR 配置或模型
    if hasattr(feature_service, 'extractor'):
        sar_search_service = SARSearchService(feature_service)
        print("[OK] SAR search service initialized successfully")
    else:
        print("[WARN] Feature service has no extractor, skipping SAR initialization")
except ImportError as e:
    print(f"[WARN] SAR service import failed: {e}")
except Exception as e:
    print(f"[WARN] SAR service initialization failed: {e}")

init_services(feature_service, search_service, sar_search_service)

app.include_router(router, prefix="/api/v1", tags=["AI Search"])


@app.on_event("startup")
async def startup_event():
    stats = feature_service.get_faiss_stats()
    sar_status = "available" if sar_search_service else "not available"
    print(f"AI Search Service started. Index status: {stats}, SAR: {sar_status}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app, 
        host=Config.HOST, 
        port=Config.PORT,
        log_level="info"
    )