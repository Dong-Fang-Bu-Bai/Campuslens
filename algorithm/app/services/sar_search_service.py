"""
SAR-enhanced search service.

Search path:
- Use entropy on Top-K retrieval scores as a trust gate.
- Low entropy samples can be used for SAR adaptation.
- High entropy samples are returned but not used for update.

Feedback path:
- User feedback is not treated as direct truth for immediate model update.
- Feedback is first assigned a trust level based on disagreement with model
  distribution and Mahalanobis consistency.
- High-risk feedback is marked pending and should not update the index/model
  immediately.
"""
from typing import List, Dict
from PIL import Image
import math
from app.config import Config
from app.utils.scoring import (
    confidence_from_entropy,
    normalized_entropy_from_scores,
    trust_level_from_entropy,
)
class SARSearchService:
    def __init__(self, feature_service):
        self.extractor = feature_service.extractor
        self.faiss_manager = feature_service.faiss_manager
        self.feature_service = feature_service
    def _attach_trust_fields(self, results: List[Dict]) -> Dict:
        scores = [float(r["score"]) for r in results]
        entropy = normalized_entropy_from_scores(scores)
        trust_level = trust_level_from_entropy(entropy)
        trust_score = confidence_from_entropy(entropy)
        return {
            "entropy": round(float(entropy), 6),
            "trustLevel": trust_level,
            "trustScore": round(float(trust_score), 6),
        }
    def search_similar_landmarks(
        self,
        image: Image.Image,
        top_k: int = 5,
        use_sar: bool = True,
    ) -> Dict:
        """
        Search for similar landmarks with optional SAR adaptation.
        Returns a dict:
        {
            "results": [...],
            "entropy": ...,
            "trustLevel": ...,
            "trustScore": ...,
            "sarEnabled": bool
        }
        """
        print(f"[INFO] Extracting features (SAR: {use_sar})...")
        query_vector = self.extractor.extract_single(image, use_sar=use_sar)
        print("[INFO] Searching landmarks...")
        results = self.faiss_manager.search_landmarks_by_category(query_vector, top_k=top_k)
        if not results:
            raise ValueError("No similar landmarks found")
        trust_fields = self._attach_trust_fields(results)
        for result in results:
            result["sar_enabled"] = use_sar
        return {
            "results": results,
            "sarEnabled": use_sar,
            **trust_fields,
        }
    def feedback_update(
        self,
        image: Image.Image,
        landmark_code: str,
        update_index: bool = True,
    ) -> Dict:
        """
        Process user feedback with trust gating.
        Policy:
        - Do not immediately trust the user's label as ground truth.
        - Compare feedback label with model prediction and Mahalanobis consistency.
        - High-risk feedback enters pending state.
        """
        print(f"[INFO] Processing feedback for {landmark_code}")
        query_vector = self.extractor.extract_single(image, use_sar=False)
        search_result = self.faiss_manager.search_landmarks_by_category(query_vector, top_k=5)
        top1 = search_result[0] if search_result else None
        landmark_stats = self.faiss_manager.landmark_stats
        if landmark_code in landmark_stats:
            mahal_dist = self.faiss_manager._compute_mahalanobis_distance(
                query_vector, landmark_stats[landmark_code]
            )
            label_confidence = math.exp(-math.log(10) / 1000 * mahal_dist)
            label_confidence = max(0.0, min(1.0, label_confidence))
        else:
            mahal_dist = float("inf")
            label_confidence = 0.0
        model_code = top1["landmarkCode"] if top1 else None
        model_score = float(top1["score"]) if top1 else 0.0
        model_entropy = normalized_entropy_from_scores(
            [r["score"] for r in search_result[:5]] if search_result else []
        )
        model_trust = trust_level_from_entropy(model_entropy)
        # 反馈可信度:
        # - 与 Top-1 一致且模型可信 -> 高可信
        # - 与 Top-1 不一致但模型本来就不确定 -> 中可信
        # - 与高置信预测冲突 -> 低可信，pending
        if model_code is None:
            feedback_trust = "pending"
            approved = False
        elif model_code == landmark_code and model_trust == "trusted":
            feedback_trust = "accepted"
            approved = True
        elif model_code != landmark_code and model_trust == "untrusted":
            feedback_trust = "pending"
            approved = False
        else:
            feedback_trust = "review"
            approved = False
        index_updated = False
        if update_index and approved and label_confidence > Config.FEEDBACK_ACCEPT_CONFIDENCE:
            try:
                feature = self.extractor.extract_single(
                    image,
                    use_sar=True,
                    label=None,
                    label_confidence=label_confidence,
                )
                self.faiss_manager.add_to_index(feature, landmark_code)
                index_updated = True
                print(f"[OK] Added feature to index for {landmark_code}")
            except Exception as exc:
                print(f"[WARN] Index update skipped: {exc}")
        sar_ema = self.extractor.get_sar_ema()
        return {
            "success": True,
            "message": "Feedback accepted" if approved else "Feedback stored for review",
            "landmarkCode": landmark_code,
            "modelPrediction": model_code,
            "modelScore": round(model_score, 4),
            "modelEntropy": round(float(model_entropy), 6),
            "modelTrust": model_trust,
            "mahalanobisDistance": round(mahal_dist, 4) if mahal_dist < float("inf") else None,
            "labelConfidence": round(label_confidence, 4),
            "feedbackTrust": feedback_trust,
            "pending": not approved,
            "index_updated": index_updated,
            "sar_ema": sar_ema,
        }
    def reset_sar(self):
        """Reset SAR adapter to initial state."""
        self.extractor.reset_sar()
        return {"success": True, "message": "SAR adapter reset"}