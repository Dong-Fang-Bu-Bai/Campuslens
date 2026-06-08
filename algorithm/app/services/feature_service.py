from pathlib import Path
from typing import List, Dict
import json
import numpy as np
from app.config import Config


class FeatureService:
    def __init__(self, use_sar: bool = False):
        Config.validate_config()
        
        print("=" * 60)
        if use_sar:
            print("初始化 SAR-DINOv2 特征提取器（离线模式 + 测试时自适应）")
        else:
            print("初始化 DINOv2 特征提取器（离线模式）")
        print("=" * 60)
        
        # First load FAISS index to get landmark statistics (for SAR)
        from app.utils.faiss_manager import FAISSManager
        # We'll set dimension later after loading extractor
        temp_faiss = FAISSManager(dimension=768)  # Temporary, will be replaced
        temp_faiss.load_index()
        
        # Extract landmark statistics if available
        landmark_means = None
        landmark_cov_invs = None
        num_landmarks = 0
        if hasattr(temp_faiss, 'landmark_stats') and temp_faiss.landmark_stats:
            # Sort by landmark_codes to ensure consistent order
            if hasattr(temp_faiss, 'landmark_codes') and temp_faiss.landmark_codes:
                landmark_means = []
                landmark_cov_invs = []
                for code in temp_faiss.landmark_codes:
                    stats = temp_faiss.landmark_stats[code]
                    landmark_means.append(stats['mean'])
                    landmark_cov_invs.append(stats['cov_inv'])
                num_landmarks = len(landmark_means)
                print(f"[INFO] Loaded landmark statistics for {num_landmarks} landmarks")
        
        # 根据配置选择提取器
        if use_sar:
            try:
                from app.models.sar_dinov2_extractor import SARDINOv2Extractor
                self.extractor = SARDINOv2Extractor(
                    model_path=Config.DINO_MODEL_PATH,
                    device=Config.DEVICE,
                    sar_steps=1,
                    landmark_means=landmark_means,
                    landmark_cov_invs=landmark_cov_invs,
                    num_classes=num_landmarks if num_landmarks > 0 else 1000
                )
                print("[OK] SAR-DINOv2 extractor initialized")
            except ImportError as e:
                print(f"[WARN] Failed to import SAR extractor: {e}")
                print("[WARN] Falling back to standard DINOv2 extractor")
                from app.models.dinov2_extractor import DINOv2Extractor
                self.extractor = DINOv2Extractor(
                    model_path=Config.DINO_MODEL_PATH,
                    device=Config.DEVICE
                )
        else:
            from app.models.dinov2_extractor import DINOv2Extractor
            self.extractor = DINOv2Extractor(
                model_path=Config.DINO_MODEL_PATH,
                device=Config.DEVICE
            )
        
        # Initialize proper FAISS manager with correct dimension
        self.faiss_manager = FAISSManager(dimension=self.extractor.feature_dim)
        
        if not self.faiss_manager.load_index():
            print("[WARN] No existing index found, need to rebuild")
            print("   Call POST /api/v1/index/rebuild to build the index")
        
        self.use_sar = use_sar
    
    def extract_and_index_all_landmarks(self) -> Dict:
        print("Starting to extract features for all landmarks...")
        
        all_vectors = []
        all_metadata = []
        
        landmarks_dir = Config.DATASETS_DIR
        
        if not landmarks_dir.exists():
            raise FileNotFoundError(f"Datasets directory not found: {landmarks_dir}")
        
        landmark_folders = [d for d in landmarks_dir.iterdir() if d.is_dir()]
        
        for landmark_folder in landmark_folders:
            landmark_code = landmark_folder.name.split('_')[0]
            landmark_name = '_'.join(landmark_folder.name.split('_')[1:])
            
            image_files = list(landmark_folder.glob("*.jpg")) + \
                         list(landmark_folder.glob("*.jpeg")) + \
                         list(landmark_folder.glob("*.png"))
            
            print(f"Processing {landmark_code}: {len(image_files)} images")
            
            for image_path in image_files:
                try:
                    # 使用非 SAR 模式提取特征（索引构建不需要自适应）
                    feature_vector = self.extractor.extract_single(str(image_path), use_sar=False)
                    all_vectors.append(feature_vector)
                    
                    metadata = {
                        "landmark_code": landmark_code,
                        "landmark_name": landmark_name,
                        "image_path": str(image_path.relative_to(Config.DATASETS_DIR.parent.parent)).replace("\\", "/"),
                        "image_filename": image_path.name
                    }
                    all_metadata.append(metadata)
                    
                except Exception as e:
                    print(f"Error processing {image_path}: {e}")
                    continue
        
        if all_vectors:
            all_vectors_np = np.array(all_vectors)
            self.faiss_manager.rebuild_from_scratch(all_vectors_np, all_metadata)
            
            return {
                "status": "success",
                "total_images": len(all_vectors),
                "total_landmarks": len(set(m['landmark_code'] for m in all_metadata))
            }
        else:
            raise ValueError("No valid images found to process")
    
    def get_faiss_stats(self) -> Dict:
        return self.faiss_manager.get_index_stats()