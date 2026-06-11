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
    DEVICE = os.getenv("DEVICE", "auto")
    IMAGE_SIZE = int(os.getenv("IMAGE_SIZE", "518"))
    BATCH_SIZE = int(os.getenv("BATCH_SIZE", "32"))

    TOP_K_RESULTS = int(os.getenv("TOP_K_RESULTS", "5"))
    CONFIDENCE_THRESHOLD = float(os.getenv("CONFIDENCE_THRESHOLD", "0.5"))

    ENTROPY_TEMPERATURE = float(os.getenv("ENTROPY_TEMPERATURE", "0.2"))
    TRUST_LOW_THRESHOLD = float(os.getenv("TRUST_LOW_THRESHOLD", "0.35"))
    TRUST_HIGH_THRESHOLD = float(os.getenv("TRUST_HIGH_THRESHOLD", "0.60"))

    MATCH_SCORE_CENTER_DISTANCE = float(os.getenv("MATCH_SCORE_CENTER_DISTANCE", "900.0"))
    MATCH_SCORE_SLOPE = float(os.getenv("MATCH_SCORE_SLOPE", "3.0"))

    SAR_MARGIN = float(os.getenv("SAR_MARGIN", "0.9210"))
    SAR_ENTROPY_TOP_K = int(os.getenv("SAR_ENTROPY_TOP_K", "3"))

    FEEDBACK_ACCEPT_CONFIDENCE = float(os.getenv("FEEDBACK_ACCEPT_CONFIDENCE", "0.7"))
    LABEL_GUIDANCE_MIN_CONFIDENCE = float(os.getenv("LABEL_GUIDANCE_MIN_CONFIDENCE", "0.3"))

    ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png"}
    MAX_FILE_SIZE = int(os.getenv("MAX_FILE_SIZE", "10485760"))

    
    @classmethod
    def ensure_directories(cls):
        cls.FAISS_INDEX_DIR.mkdir(parents=True, exist_ok=True)
        cls.FEATURES_CACHE_DIR.mkdir(parents=True, exist_ok=True)

    
    @classmethod
    def validate_config(cls):
        if not Path(cls.DINO_MODEL_PATH).exists():
            raise FileNotFoundError(
                f"[ERROR] DINOv2 model file not found: {cls.DINO_MODEL_PATH}\n"
                f"Please place the model file at this path or set DINO_MODEL_PATH environment variable"
            )
        print(f"[OK] Model file validated: {cls.DINO_MODEL_PATH}")
