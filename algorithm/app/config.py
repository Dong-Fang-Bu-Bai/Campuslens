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
    INSTANCE_ID = os.getenv("INSTANCE_ID", f"algorithm-{PORT}")
    INSTANCE_ROLE = os.getenv("INSTANCE_ROLE", "primary")
    
    DATASETS_DIR = Path(os.getenv("DATASETS_DIR", BASE_DIR.parent / "datasets" / "landmarks"))
    FAISS_INDEX_DIR = Path(os.getenv("FAISS_INDEX_DIR", BASE_DIR / "data" / "faiss_index"))
    FEATURES_CACHE_DIR = Path(os.getenv("FEATURES_CACHE_DIR", BASE_DIR / "data" / "features"))
    CORRECTION_SAMPLES_MANIFEST = Path(os.getenv(
        "CORRECTION_SAMPLES_MANIFEST",
        BASE_DIR / "data" / "adaptation" / "correction_samples.jsonl"
    ))
    MODEL_VERSIONS_DIR = Path(os.getenv(
        "MODEL_VERSIONS_DIR",
        BASE_DIR / "data" / "model_versions"
    ))
    SAR_STATE_DIR = Path(os.getenv("SAR_STATE_DIR", MODEL_VERSIONS_DIR / "sar_runtime"))
    SAR_EVENT_LOG = Path(os.getenv("SAR_EVENT_LOG", BASE_DIR / "data" / "logs" / "sar_events.jsonl"))
    ANCHOR_DATASET_DIR = Path(os.getenv("ANCHOR_DATASET_DIR", BASE_DIR.parent / "datasets" / "anchors"))
    
    DINO_MODEL_PATH = os.getenv("DINO_MODEL_PATH", str(BASE_DIR / "models" / "dinov2_model.pth"))
    DEVICE = os.getenv("DEVICE", "auto")  # auto, cpu, or cuda
    IMAGE_SIZE = int(os.getenv("IMAGE_SIZE", "518"))  # DINOv2 默认 518x518
    BATCH_SIZE = int(os.getenv("BATCH_SIZE", "32"))
    SEARCH_BATCH_SIZE = int(os.getenv("SEARCH_BATCH_SIZE", "2"))
    MIXED_PRECISION = os.getenv("MIXED_PRECISION", "false").lower() == "true"
    
    TOP_K_RESULTS = int(os.getenv("TOP_K_RESULTS", "5"))
    # Legacy compatibility setting. Current FAISS scoring uses app.utils.scoring thresholds.
    CONFIDENCE_THRESHOLD = float(os.getenv("CONFIDENCE_THRESHOLD", "0.5"))

    # Global kill switch. Per-request sarMode remains false by default.
    SAR_ENABLED = os.getenv("SAR_ENABLED", "true").lower() == "true"
    SAR_STEPS = int(os.getenv("SAR_STEPS", "1"))
    SAR_ENTROPY_TEMPERATURE = float(os.getenv("SAR_ENTROPY_TEMPERATURE", "0.15"))
    SAR_ENTROPY_THRESHOLD = float(os.getenv("SAR_ENTROPY_THRESHOLD", "0.70"))
    SAR_LEARNING_RATE = float(os.getenv("SAR_LEARNING_RATE", "0.0001"))
    SAR_RHO = float(os.getenv("SAR_RHO", "0.05"))
    SAR_EMA_ALPHA = float(os.getenv("SAR_EMA_ALPHA", "0.9"))
    SAR_COLLAPSE_EMA_THRESHOLD = float(os.getenv("SAR_COLLAPSE_EMA_THRESHOLD", "0.05"))
    SAR_ANCHOR_CHECK_INTERVAL = int(os.getenv("SAR_ANCHOR_CHECK_INTERVAL", "10"))
    SAR_ANCHOR_TOP1_RETENTION = float(os.getenv("SAR_ANCHOR_TOP1_RETENTION", "0.90"))
    SAR_FEATURE_DRIFT_THRESHOLD = float(os.getenv("SAR_FEATURE_DRIFT_THRESHOLD", "0.08"))
    MATCH_SCORE_CENTER_DISTANCE = float(os.getenv("MATCH_SCORE_CENTER_DISTANCE", "700.0"))
    MATCH_SCORE_SLOPE = float(os.getenv("MATCH_SCORE_SLOPE", "5.0"))
    SIGMOID_STABILITY_THRESHOLD = float(os.getenv("SIGMOID_STABILITY_THRESHOLD", "700.0"))
    TRUST_LOW_THRESHOLD = float(os.getenv("TRUST_LOW_THRESHOLD", "0.35"))
    TRUST_HIGH_THRESHOLD = float(os.getenv("TRUST_HIGH_THRESHOLD", "0.70"))
    CORRECTION_ACCEPT_CONFIDENCE = float(os.getenv("CORRECTION_ACCEPT_CONFIDENCE", "0.70"))
    
    ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "webp"}
    MAX_FILE_SIZE = int(os.getenv("MAX_FILE_SIZE", "10485760"))  # 10MB
    
    @classmethod
    def ensure_directories(cls):
        cls.FAISS_INDEX_DIR.mkdir(parents=True, exist_ok=True)
        cls.FEATURES_CACHE_DIR.mkdir(parents=True, exist_ok=True)
        cls.CORRECTION_SAMPLES_MANIFEST.parent.mkdir(parents=True, exist_ok=True)
        cls.MODEL_VERSIONS_DIR.mkdir(parents=True, exist_ok=True)
        cls.SAR_STATE_DIR.mkdir(parents=True, exist_ok=True)
        cls.SAR_EVENT_LOG.parent.mkdir(parents=True, exist_ok=True)
    
    @classmethod
    def validate_config(cls):
        if not Path(cls.DINO_MODEL_PATH).exists():
            raise FileNotFoundError(
                f"[ERROR] DINOv2 模型文件不存在: {cls.DINO_MODEL_PATH}\n"
                f"请将模型文件放置在此路径，或设置环境变量 DINO_MODEL_PATH"
            )
        print(f"[OK] 模型文件验证通过: {cls.DINO_MODEL_PATH}")
