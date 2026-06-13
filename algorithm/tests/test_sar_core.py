import unittest

import numpy as np
import torch
from torch import nn

from app.config import Config
from app.models.sar_adapter import SARAdapter
from app.services.search_service import SearchService
from app.utils.scoring import confidence_from_entropy, normalized_entropy_from_scores, trust_level_from_entropy


class TinyFeatureModel(nn.Module):
    def __init__(self):
        super().__init__()
        self.projection = nn.Linear(4, 4, bias=False)
        self.norm = nn.LayerNorm(4)

    def forward_features(self, inputs):
        values = inputs.reshape(inputs.shape[0], 4)
        return {"x_norm_clstoken": self.norm(self.projection(values))}


class SarCoreTests(unittest.TestCase):
    def test_entropy_boundaries_and_trust(self):
        self.assertEqual(normalized_entropy_from_scores([]), 1.0)
        self.assertEqual(normalized_entropy_from_scores([0.5]), 0.0)
        self.assertEqual(trust_level_from_entropy(0.0), "trusted")
        self.assertEqual(trust_level_from_entropy(1.0), "untrusted")
        self.assertAlmostEqual(confidence_from_entropy(0.25), 0.75)

    def test_persistent_adaptation_and_explicit_reset(self):
        model = TinyFeatureModel()
        adapter = SARAdapter(model)
        before = {name: value.clone() for name, value in adapter.initial_state.items()}
        stats = {
            "L01": {"mean": np.zeros(4), "cov_inv": np.eye(4)},
            "L02": {"mean": np.ones(4), "cov_inv": np.eye(4)},
        }
        old_threshold = Config.SAR_ENTROPY_THRESHOLD
        Config.SAR_ENTROPY_THRESHOLD = 1.1
        try:
            _, _, applied, updated = adapter.adapt_batch(torch.ones(2, 1, 2, 2), stats)
        finally:
            Config.SAR_ENTROPY_THRESHOLD = old_threshold
        self.assertTrue(updated)
        self.assertEqual(applied, [True, True])
        self.assertEqual(adapter.update_count, 1)
        adapter.reset()
        self.assertEqual(adapter.update_count, 0)
        for name, parameter in adapter.named_parameters:
            self.assertTrue(torch.equal(parameter.detach().cpu(), before[name]))

    def test_sar_batch_is_joint_and_reports_each_image(self):
        class Manager:
            def search_landmarks_by_category(self, vector, top_k=5):
                return [{"rank": 1, "landmarkCode": "L01", "landmarkName": "Library",
                         "score": 0.9, "confidenceLevel": "high", "mahalanobisDistance": 1.0}]

        class FeatureService:
            faiss_manager = Manager()
            calls = 0

            def search_vectors(self, images, sar_mode=False):
                self.calls += 1
                return np.zeros((len(images), 4)), [True, False], [0.1, 0.9]

            def runtime_status(self):
                return {"baseModelVersion": "base", "indexVersion": "index", "sarStateVersion": "sar-g1-u1"}

            def combined_model_version(self, sar_mode):
                return "base@index@sar-g1-u1"

        features = FeatureService()
        responses = SearchService(features).search_batch_with_metadata([object(), object()], 5, True)
        self.assertEqual(features.calls, 1)
        self.assertEqual([item["sarApplied"] for item in responses], [True, False])


if __name__ == "__main__":
    unittest.main()
