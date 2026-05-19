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
    description="CampusLens AI 图像检索服务 - 基于 DINOv2 + FAISS（纯离线模式）"
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

feature_service = FeatureService()
search_service = SearchService(feature_service)

init_services(feature_service, search_service)

app.include_router(router, prefix="/api/v1", tags=["AI Search"])


@app.on_event("startup")
async def startup_event():
    stats = feature_service.get_faiss_stats()
    print(f"AI Search Service started. Index status: {stats}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app, 
        host=Config.HOST, 
        port=Config.PORT,
        log_level="info"
    )
