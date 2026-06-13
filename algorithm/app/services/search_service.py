from app.utils.scoring import normalized_entropy_from_scores, trust_level_from_entropy


class SearchService:
    def __init__(self, feature_service):
        self.feature_service = feature_service

    @property
    def faiss_manager(self):
        return self.feature_service.faiss_manager

    def search_with_metadata(self, image, top_k=5, sar_mode=False):
        return self.search_batch_with_metadata([image], top_k, sar_mode)[0]

    def search_batch_with_metadata(self, images, top_k=5, sar_mode=False):
        if not images:
            return []
        vectors, applied, _ = self.feature_service.search_vectors(images, sar_mode)
        output = []
        for vector, was_applied in zip(vectors, applied):
            results = self.faiss_manager.search_landmarks_by_category(vector, top_k=top_k)
            entropy = normalized_entropy_from_scores([item["score"] for item in results])
            runtime = self.feature_service.runtime_status()
            output.append({
                "results": results,
                "sarApplied": bool(was_applied),
                "trustLevel": trust_level_from_entropy(entropy),
                "modelVersion": self.feature_service.combined_model_version(sar_mode),
                "baseModelVersion": runtime["baseModelVersion"],
                "indexVersion": runtime["indexVersion"],
                "sarStateVersion": runtime["sarStateVersion"],
                "instanceId": runtime.get("instanceId", "algorithm-unknown"),
                "instanceRole": runtime.get("instanceRole", "unknown"),
            })
        return output

    def search_similar_landmarks(self, image, top_k=5):
        return self.search_with_metadata(image, top_k, False)["results"]

    def search_batch(self, images, top_k=5):
        return [item["results"] for item in self.search_batch_with_metadata(images, top_k, False)]
