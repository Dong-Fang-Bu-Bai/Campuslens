import json
import tempfile
import unittest
import io
from pathlib import Path
from PIL import Image

try:
    from fastapi import FastAPI
    from fastapi.testclient import TestClient
except ModuleNotFoundError:  # pragma: no cover - local lightweight env fallback
    FastAPI = None
    TestClient = None

try:
    from app.config import Config
except ModuleNotFoundError:  # pragma: no cover - local lightweight env fallback
    Config = None


class AdaptationEndpointTests(unittest.TestCase):
    def setUp(self):
        if FastAPI is None or TestClient is None or Config is None:
            self.skipTest("algorithm web dependencies are not installed")
        from app.api.routes import router

        app = FastAPI()
        app.include_router(router, prefix="/api/v1")
        self.client = TestClient(app)
        self.original_manifest = Config.CORRECTION_SAMPLES_MANIFEST
        self.tmpdir = Path(tempfile.mkdtemp(prefix="campuslens-adaptation-"))
        Config.CORRECTION_SAMPLES_MANIFEST = self.tmpdir / "correction_samples.jsonl"

    def post_sample(self, payload):
        buffer = io.BytesIO()
        Image.new("RGB", (32, 32), "blue").save(buffer, format="JPEG")
        return self.client.post(
            "/api/v1/adaptation/correction-samples",
            data={"payload": json.dumps(payload)},
            files={"file": ("sample.jpg", buffer.getvalue(), "image/jpeg")},
        )

    def tearDown(self):
        Config.CORRECTION_SAMPLES_MANIFEST = self.original_manifest
        for child in self.tmpdir.glob("*"):
            child.unlink()
        self.tmpdir.rmdir()

    def test_valid_correction_sample_is_written_to_manifest(self):
        response = self.post_sample({
            "sampleId": 12,
            "feedbackId": 8,
            "searchRecordId": 99,
            "imageUrl": "/uploads/sample.jpg",
            "confirmedLandmarkCode": "L02",
            "predictedLandmarkCode": "L01",
            "feedbackType": "wrong",
            "comment": "confirmed by user",
            "topResults": [
                {"rank": 1, "landmarkCode": "L02", "score": 0.95, "mahalanobisDistance": 3.1},
                {"rank": 2, "landmarkCode": "L01", "score": 0.05, "mahalanobisDistance": 6.4},
            ],
        })

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertTrue(body["suggestAccept"])
        self.assertEqual(body["nextAction"], "pending_index")
        self.assertFalse(body["activated"])

        lines = Config.CORRECTION_SAMPLES_MANIFEST.read_text(encoding="utf-8").splitlines()
        self.assertEqual(len(lines), 1)
        record = json.loads(lines[0])
        self.assertEqual(record["sampleId"], 12)
        self.assertEqual(record["confirmedLandmarkCode"], "L02")

    def test_empty_top_results_is_rejected(self):
        response = self.post_sample({
            "sampleId": 12,
            "feedbackId": 8,
            "searchRecordId": 99,
            "imageUrl": "/uploads/sample.jpg",
            "confirmedLandmarkCode": "L02",
            "feedbackType": "wrong",
            "topResults": [],
        })
        self.assertEqual(response.status_code, 400)

    def test_missing_required_field_is_rejected(self):
        response = self.post_sample({
            "sampleId": 12,
            "feedbackId": 8,
            "imageUrl": "/uploads/sample.jpg",
            "confirmedLandmarkCode": "L02",
            "feedbackType": "wrong",
            "topResults": [{"rank": 1, "landmarkCode": "L01", "score": 0.91}],
        })
        self.assertEqual(response.status_code, 422)


if __name__ == "__main__":
    unittest.main()
