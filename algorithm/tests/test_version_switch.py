import json
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

from app.config import Config
from app.services.feature_service import FeatureService


class FakeAdapter:
    def __init__(self):
        self.imported = False
        self.generation = 1
        self.update_count = 0

    def import_state(self, state):
        self.imported = True


class VersionSwitchTests(unittest.TestCase):
    def test_restart_rejects_sar_checkpoint_with_mismatched_versions(self):
        original = Config.SAR_STATE_DIR
        with tempfile.TemporaryDirectory(prefix="campuslens-sar-state-") as tmp:
            Config.SAR_STATE_DIR = Path(tmp)
            (Config.SAR_STATE_DIR / "sar_checkpoint.pt").write_bytes(b"invalid")
            (Config.SAR_STATE_DIR / "sar_state.json").write_text(json.dumps({
                "baseModelVersion": "old-base", "indexVersion": "old-index"
            }), encoding="utf-8")
            service = FeatureService.__new__(FeatureService)
            service.base_model_version = "new-base"
            service.index_version = "new-index"
            service.extractor = type("Extractor", (), {"sar_adapter": FakeAdapter(), "device": "cpu"})()
            service.last_error = None
            service.last_checkpoint_at = None
            service._event = lambda *args, **kwargs: None
            service._restore_sar_state()
            self.assertFalse(service.extractor.sar_adapter.imported)
        Config.SAR_STATE_DIR = original

    def test_shared_index_load_failure_keeps_current_manager(self):
        original_versions = Config.MODEL_VERSIONS_DIR
        original_sar = Config.SAR_STATE_DIR
        with tempfile.TemporaryDirectory(prefix="campuslens-version-switch-") as tmp:
            root = Path(tmp)
            Config.MODEL_VERSIONS_DIR = root / "versions"
            Config.SAR_STATE_DIR = root / "sar"
            failed_index = Config.MODEL_VERSIONS_DIR / "index-failed" / "index"
            failed_index.mkdir(parents=True)
            (Config.MODEL_VERSIONS_DIR / "ACTIVE_INDEX_VERSION").write_text(
                "index-failed", encoding="utf-8"
            )

            current_manager = object()
            service = FeatureService.__new__(FeatureService)
            service.index_version = "index-current"
            service.faiss_manager = current_manager
            service.extractor = type("Extractor", (), {"feature_dim": 8})()
            service.version_lock = threading.RLock()
            service.sar_state_mtime = 0
            service._checkpoint_paths = lambda: (
                Config.SAR_STATE_DIR / "sar_checkpoint.pt",
                Config.SAR_STATE_DIR / "sar_state.json",
            )

            candidate = type("Candidate", (), {"load_index": lambda self: False})()
            with patch("app.services.feature_service.FAISSManager", return_value=candidate):
                service._refresh_shared_state()

            self.assertEqual("index-current", service.index_version)
            self.assertIs(current_manager, service.faiss_manager)
        Config.MODEL_VERSIONS_DIR = original_versions
        Config.SAR_STATE_DIR = original_sar


if __name__ == "__main__":
    unittest.main()
