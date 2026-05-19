import os
from dotenv import load_dotenv
from pathlib import Path

load_dotenv()

BASE_DIR = Path(__file__).resolve().parent.parent

class Config:
    APP_NAME = "CampusLens AI Search Service"
    APP_VERSION = "1.0.0"
    
    HOST = os.getenv("HOST", "0.0.0.0")
    PORT = int(os.getenv("PORT", "8000"))
    
    DATASETS_DIR = Path(os.getenv("DATASETS_DIR", BASE_DIR.parent / "datasets" / "landmarks"))
    FAISS_INDEX_DIR = Path(os.getenv("FAISS_INDEX_DIR", BASE_DIR / "data" / "faiss_index"))
    FEATURES_CACHE_DIR = Path(os.getenv("FEATURES_CACHE_DIR", BASE_DIR / "data" / "features"))
    
    DINO_MODEL_PATH = os.getenv("DINO_MODEL_PATH", str(BASE_DIR / "models" / "dinov2_model.pth"))
    IMAGE_SIZE = int(os.getenv("IMAGE_SIZE", "518"))  # DINOv2 默认 518x518
    BATCH_SIZE = int(os.getenv("BATCH_SIZE", "32"))
    
    TOP_K_RESULTS = int(os.getenv("TOP_K_RESULTS", "5"))
    CONFIDENCE_THRESHOLD = float(os.getenv("CONFIDENCE_THRESHOLD", "0.5"))
    
    ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png"}
    MAX_FILE_SIZE = int(os.getenv("MAX_FILE_SIZE", "10485760"))  # 10MB
    
    @classmethod
    def ensure_directories(cls):
        cls.FAISS_INDEX_DIR.mkdir(parents=True, exist_ok=True)
        cls.FEATURES_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    
    @classmethod
    def validate_config(cls):
        if not Path(cls.DINO_MODEL_PATH).exists():
            raise FileNotFoundError(
                f"❌ DINOv2 模型文件不存在: {cls.DINO_MODEL_PATH}\n"
                f"请将模型文件放置在此路径，或设置环境变量 DINO_MODEL_PATH"
            )
        print(f"✅ 模型文件验证通过: {cls.DINO_MODEL_PATH}")
