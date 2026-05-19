from typing import List, Dict
from PIL import Image
from app.models.dinov2_extractor import DINOv2Extractor
from app.utils.faiss_manager import FAISSManager
from app.config import Config


class SearchService:
    def __init__(self, feature_service):
        self.extractor = feature_service.extractor
        self.faiss_manager = feature_service.faiss_manager
    
    def search_similar_landmarks(self, image: Image.Image, top_k: int = 5) -> List[Dict]:
        print("Extracting features from query image...")
        query_vector = self.extractor.extract_single(image)
        
        print("Searching in FAISS index...")
        results = self.faiss_manager.search(query_vector, top_k=top_k)
        
        formatted_results = []
        for rank, (metadata, score) in enumerate(results, 1):
            # 确保路径使用正斜杠
            image_path = metadata['image_path'].replace("\\", "/") if "\\" in metadata['image_path'] else metadata['image_path']
            
            formatted_results.append({
                "rank": rank,
                "landmarkCode": metadata['landmark_code'],
                "landmarkName": metadata['landmark_name'],
                "imagePath": image_path,
                "imageFilename": metadata['image_filename'],
                "score": round(score, 4)
            })
        
        return formatted_results
