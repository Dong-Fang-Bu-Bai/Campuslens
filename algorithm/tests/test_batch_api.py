import io
import unittest
from unittest.mock import Mock, patch

import torch
from fastapi import FastAPI
from fastapi.testclient import TestClient
from PIL import Image

from app.api.routes import init_services, router
from app.config import Config
from app.models.dinov2_extractor import DINOv2Extractor


def image_bytes(color):
    buffer = io.BytesIO()
    Image.new("RGB", (128, 128), color).save(buffer, format="JPEG")
    return buffer.getvalue()


def result(code, score):
    return [{
        "rank": 1,
        "landmarkCode": code,
        "landmarkName": code,
        "score": score,
        "confidenceLevel": "high",
        "mahalanobisDistance": 1.0,
    }]


class FakeExtractor:
    device = "cpu"


class FakeFeatureService:
    extractor = FakeExtractor()


class FakeSearchService:
    def search_batch(self, images, top_k):
        return [result(f"L0{index + 1}", 0.9 - index * 0.1) for index in range(len(images))]

    def search_similar_landmarks(self, image, top_k):
        return result("L01", 0.9)


class OomSearchService(FakeSearchService):
    def search_batch(self, images, top_k):
        raise torch.cuda.OutOfMemoryError("test oom")


class PartialOomSearchService(OomSearchService):
    def search_similar_landmarks(self, image, top_k):
        if image.getpixel((0, 0))[2] > image.getpixel((0, 0))[0]:
            raise torch.cuda.OutOfMemoryError("single image oom")
        return result("L01", 0.9)


class BatchEndpointTests(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(router, prefix="/api/v1")
        self.client = TestClient(app)
        init_services(FakeFeatureService(), FakeSearchService())

    def test_batch_preserves_order_and_reports_invalid_item(self):
        response = self.client.post("/api/v1/search/batch", files=[
            ("files", ("first.jpg", image_bytes("red"), "image/jpeg")),
            ("files", ("bad.txt", b"not-an-image", "text/plain")),
        ])

        self.assertEqual(response.status_code, 200)
        items = response.json()["items"]
        self.assertTrue(items[0]["success"])
        self.assertEqual(items[0]["response"]["results"][0]["landmarkCode"], "L01")
        self.assertFalse(items[1]["success"])
        self.assertEqual(items[1]["errorCode"], "invalid_image")
        self.assertFalse(items[1]["retryable"])

    def test_cuda_oom_splits_batch_into_single_images(self):
        init_services(FakeFeatureService(), OomSearchService())
        response = self.client.post("/api/v1/search/batch", files=[
            ("files", ("first.jpg", image_bytes("red"), "image/jpeg")),
            ("files", ("second.jpg", image_bytes("blue"), "image/jpeg")),
        ])

        self.assertEqual(response.status_code, 200)
        items = response.json()["items"]
        self.assertEqual(len(items), 2)
        self.assertTrue(all(item["success"] for item in items))

    def test_cuda_oom_isolated_to_failing_single_image(self):
        init_services(FakeFeatureService(), PartialOomSearchService())
        response = self.client.post("/api/v1/search/batch", files=[
            ("files", ("first.jpg", image_bytes("red"), "image/jpeg")),
            ("files", ("second.jpg", image_bytes("blue"), "image/jpeg")),
        ])

        items = response.json()["items"]
        self.assertTrue(items[0]["success"])
        self.assertFalse(items[1]["success"])
        self.assertEqual(items[1]["errorCode"], "cuda_oom")
        self.assertTrue(items[1]["retryable"])

    @patch.object(Config, "MAX_FILE_SIZE", 32)
    def test_batch_rejects_oversized_image(self):
        response = self.client.post("/api/v1/search/batch", files=[
            ("files", ("large.jpg", image_bytes("red"), "image/jpeg")),
        ])

        self.assertEqual(response.status_code, 200)
        item = response.json()["items"][0]
        self.assertFalse(item["success"])
        self.assertEqual(item["errorCode"], "invalid_image")
        self.assertIn("图片大小不能超过", item["message"])

    @patch.object(Config, "MAX_FILE_SIZE", 32)
    def test_single_search_rejects_oversized_image(self):
        response = self.client.post("/api/v1/search", files={
            "file": ("large.jpg", image_bytes("red"), "image/jpeg"),
        })

        self.assertEqual(response.status_code, 400)
        self.assertIn("图片大小不能超过", response.json()["detail"])

    def test_health_exposes_device_and_batch_limit(self):
        response = self.client.get("/api/v1/health")
        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["device"], "cpu")
        self.assertTrue(body["modelReady"])
        self.assertGreaterEqual(body["maxBatchSize"], 1)
        self.assertFalse(body["mixedPrecision"])


class DeviceSelectionTests(unittest.TestCase):
    @patch.object(DINOv2Extractor, "get_feature_dimension", return_value=768)
    @patch.object(DINOv2Extractor, "_load_local_model")
    @patch("app.models.dinov2_extractor.torch.cuda.is_available", return_value=False)
    def test_auto_falls_back_to_cpu(self, cuda_available, load_model, feature_dimension):
        model = Mock()
        load_model.return_value = model
        extractor = DINOv2Extractor("unused.pth", "auto")
        self.assertEqual(extractor.device, "cpu")
        model.to.assert_called_once_with("cpu")

    @patch.object(DINOv2Extractor, "get_feature_dimension", return_value=768)
    @patch.object(DINOv2Extractor, "_load_local_model")
    @patch("app.models.dinov2_extractor.torch.cuda.is_available", return_value=False)
    def test_image_size_configures_preprocessing(self, cuda_available, load_model, feature_dimension):
        model = Mock()
        load_model.return_value = model
        extractor = DINOv2Extractor("unused.pth", "cpu", image_size=224)

        self.assertEqual(extractor.image_size, 224)
        self.assertEqual(extractor.transform.transforms[0].size, 224)
        self.assertEqual(extractor.transform.transforms[1].size, (224, 224))

    @patch("app.models.dinov2_extractor.torch.cuda.is_available", return_value=False)
    def test_explicit_cuda_fails_when_unavailable(self, cuda_available):
        with self.assertRaisesRegex(RuntimeError, "CUDA is not available"):
            DINOv2Extractor("unused.pth", "cuda")


if __name__ == "__main__":
    unittest.main()
