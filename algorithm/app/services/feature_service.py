from pathlib import Path
from typing import List, Dict
import json
import numpy as np
from app.models.dinov2_extractor import DINOv2Extractor
from app.utils.faiss_manager import FAISSManager
from app.config import Config


class FeatureService:
    def __init__(self):
        Config.validate_config()
        
        print("=" * 60)
        print("初始化 DINOv2 特征提取器（离线模式）")
        print("=" * 60)
        
        self.extractor = DINOv2Extractor(
            model_path=Config.DINO_MODEL_PATH,
            device=Config.DEVICE,
            mixed_precision=Config.MIXED_PRECISION,
        )
        self.faiss_manager = FAISSManager(dimension=self.extractor.feature_dim)
        
        if not self.faiss_manager.load_index():
            print("⚠️  未找到现有索引，需要执行索引重建")
            print("   调用 POST /api/v1/index/rebuild 来构建索引")
    
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
                    feature_vector = self.extractor.extract_single(str(image_path))
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
