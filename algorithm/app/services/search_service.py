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
        """
        基于地标类别的搜索
        利用地标统计特征进行自适应评判
        """
        print("Extracting features from query image...")
        query_vector = self.extractor.extract_single(image)
        
        print("Searching landmarks by category with adaptive scoring...")
        results = self.faiss_manager.search_landmarks_by_category(query_vector, top_k=top_k)
        
        if not results:
            raise ValueError("No similar landmarks found")
        
        return results

    def search_batch(self, images: List[Image.Image], top_k: int = 5) -> List[List[Dict]]:
        if not images:
            return []
        vectors = self.extractor.extract_batch(images)
        return [
            self.faiss_manager.search_landmarks_by_category(vector, top_k=top_k)
            for vector in vectors
        ]
