"""
SAR-enhanced search service.

This service integrates SAR (Sharpness-Aware and Reliable entropy minimization)
for test-time adaptation, improving robustness to noisy images.
"""

from typing import List, Dict, Optional
from PIL import Image
import numpy as np
import math


class SARSearchService:
    def __init__(self, feature_service):
        self.extractor = feature_service.extractor
        self.faiss_manager = feature_service.faiss_manager
        self.feature_service = feature_service
    
    def search_similar_landmarks(self, image: Image.Image, top_k: int = 5, 
                                use_sar: bool = True) -> List[Dict]:
        """
        Search for similar landmarks with optional SAR adaptation.
        
        Args:
            image: Query image
            top_k: Number of results to return
            use_sar: Whether to use SAR adaptation
        
        Returns:
            results: List of search results
        """
        print(f"[INFO] Extracting features (SAR: {use_sar})...")
        
        # Extract features with SAR adaptation
        query_vector = self.extractor.extract_single(image, use_sar=use_sar)
        
        print("[INFO] Searching landmarks...")
        results = self.faiss_manager.search_landmarks_by_category(query_vector, top_k=top_k)
        
        if not results:
            raise ValueError("No similar landmarks found")
        
        # Add SAR information to results
        for result in results:
            result['sar_enabled'] = use_sar
        
        return results
    
    def feedback_update(self, image: Image.Image, 
                       landmark_code: str, 
                       update_index: bool = True) -> Dict:
        """
        Process user feedback and update model/index.
        
        Args:
            image: Image from user feedback
            landmark_code: Confirmed landmark code
            update_index: Whether to update FAISS index
        
        Returns:
            Dictionary with update status
        """
        print(f"[INFO] Processing feedback for {landmark_code}")
        
        # Extract features without SAR adaptation
        query_vector = self.extractor.extract_single(image, use_sar=False)
        
        # Calculate mahalanobis distance to the user-specified landmark
        landmark_stats = self.faiss_manager.landmark_stats
        if landmark_code in landmark_stats:
            mahal_dist = self.faiss_manager._compute_mahalanobis_distance(
                query_vector, landmark_stats[landmark_code]
            )
        else:
            mahal_dist = float('inf')
        
        # Calculate label confidence based on mahalanobis distance
        # Formula: confidence = exp(-ln(10)/1000 * distance)
        # - distance = 0  -> confidence = 1.0 (100%)
        # - distance = 1000 -> confidence = 0.1 (10%)
        # - distance = inf -> confidence = 0
        if mahal_dist < float('inf'):
            label_confidence = math.exp(-math.log(10) / 1000 * mahal_dist)
            label_confidence = max(0.0, min(1.0, label_confidence))  # Clamp to [0, 1]
        else:
            label_confidence = 0.0
        
        print(f"[INFO] Mahalanobis distance to {landmark_code}: {mahal_dist:.2f}, Label confidence: {label_confidence:.4f}")
        
        # Convert landmark code to integer for SAR
        try:
            user_label_int = int(landmark_code.replace('L', '').replace('landmark_', '')) - 1
        except:
            user_label_int = None
        
        # Extract features with label-guided SAR
        feature = self.extractor.extract_single(
            image,
            use_sar=True,
            label=user_label_int,
            label_confidence=label_confidence
        )
        
        # Update FAISS index if requested (only with high confidence)
        index_updated = False
        if update_index and label_confidence > 0.7:
            self.faiss_manager.add_to_index(feature, landmark_code)
            index_updated = True
            print(f"[OK] Added feature to index for {landmark_code}")
        
        # Get SAR adaptation info
        sar_ema = self.extractor.get_sar_ema()
        
        return {
            "success": True,
            "message": "Feedback processed successfully",
            "landmarkCode": landmark_code,
            "mahalanobisDistance": round(mahal_dist, 4) if mahal_dist < float('inf') else None,
            "labelConfidence": round(label_confidence, 4),
            "index_updated": index_updated,
            "sar_ema": sar_ema
        }
    
    def reset_sar(self):
        """Reset SAR adapter to initial state."""
        self.extractor.reset_sar()
        return {"success": True, "message": "SAR adapter reset"}