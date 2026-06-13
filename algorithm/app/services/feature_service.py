from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import threading
import uuid

import numpy as np
import torch

from app.config import Config
from app.models.dinov2_extractor import DINOv2Extractor
from app.utils.faiss_manager import FAISSManager
from app.utils.file_lock import exclusive_file_lock


class FeatureService:
    def __init__(self):
        Config.validate_config()
        self.version_lock = threading.RLock()
        self.maintenance = False
        self.last_error = None
        self.last_reset_reason = None
        self.last_checkpoint_at = None
        self.feature_drift = None
        self.anchor_top1_retention = None
        self.rebuild_jobs = {}
        self.shared_state_lock = Config.MODEL_VERSIONS_DIR / "runtime-state.lock"
        self.sar_state_mtime = 0
        self.base_model_version = self._file_version(Path(Config.DINO_MODEL_PATH), "base")
        self.index_version = self._load_index_version()
        self.extractor = DINOv2Extractor(
            Config.DINO_MODEL_PATH, Config.DEVICE, Config.MIXED_PRECISION, dual_track=True
        )
        self.faiss_manager = FAISSManager(self.extractor.feature_dim, self._active_index_dir())
        if not self.faiss_manager.load_index():
            print("No active FAISS index found; rebuild is required")
        self.anchor_images = self._anchor_paths()
        self.anchor_base_features = None
        self.anchor_base_top1 = []
        self._initialize_anchors()
        self._restore_sar_state()

    def _active_index_pointer(self):
        return Config.MODEL_VERSIONS_DIR / "ACTIVE_INDEX_VERSION"

    def _replace_active_index_pointer(self, version):
        pointer = self._active_index_pointer()
        pointer.parent.mkdir(parents=True, exist_ok=True)
        temporary = pointer.with_name(f"{pointer.name}.{Config.INSTANCE_ID}.tmp")
        temporary.write_text(version, encoding="utf-8")
        os.replace(temporary, pointer)

    def _restore_active_index_pointer(self, version):
        pointer = self._active_index_pointer()
        if version is None:
            pointer.unlink(missing_ok=True)
            return
        self._replace_active_index_pointer(version)

    def _load_index_version(self):
        pointer = self._active_index_pointer()
        if pointer.exists():
            value = pointer.read_text(encoding="utf-8").strip()
            if value and (Config.MODEL_VERSIONS_DIR / value / "index").exists():
                return value
        return self._directory_version(Config.FAISS_INDEX_DIR, "index-baseline")

    def _active_index_dir(self):
        version_dir = Config.MODEL_VERSIONS_DIR / self.index_version / "index"
        return version_dir if version_dir.exists() else Config.FAISS_INDEX_DIR

    def _file_version(self, path, prefix):
        digest = hashlib.sha256()
        if path.exists():
            with path.open("rb") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
        return f"{prefix}-{digest.hexdigest()[:12]}"

    def _directory_version(self, path, prefix):
        digest = hashlib.sha256()
        if path.exists():
            for item in sorted(p for p in path.rglob("*") if p.is_file()):
                digest.update(item.name.encode())
                digest.update(str(item.stat().st_size).encode())
        return f"{prefix}-{digest.hexdigest()[:12]}"

    def search_vectors(self, images, sar_mode=False):
        self._refresh_shared_state()
        if self.maintenance:
            raise RuntimeError("maintenance")
        with self.version_lock:
            if self.maintenance:
                raise RuntimeError("maintenance")
            if not sar_mode:
                vectors = self.extractor.extract_batch(images)
                return vectors, [False] * len(images), [None] * len(images)
            if not Config.SAR_ENABLED:
                raise RuntimeError("SAR mode is disabled by service configuration")
            vectors, entropy, applied, updated = self.extractor.adapt_sar_batch(
                images, self.faiss_manager.landmark_stats
            )
            if updated:
                adapter = self.extractor.sar_adapter
                if adapter.ema is not None and adapter.ema <= Config.SAR_COLLAPSE_EMA_THRESHOLD:
                    self._reset_sar("low_entropy_ema_collapse")
                    vectors = self.extractor.extract_sar_batch(images)
                    applied = [False] * len(images)
                else:
                    self._checkpoint_sar()
                    self._event("sar_update", reliableCount=sum(applied), batchSize=len(images))
                    if adapter.update_count % max(1, Config.SAR_ANCHOR_CHECK_INTERVAL) == 0:
                        if not self._check_anchors():
                            vectors = self.extractor.extract_sar_batch(images)
                            applied = [False] * len(images)
            else:
                self._event("sar_skip", batchSize=len(images), reason="no_reliable_sample")
            return vectors, applied, entropy

    def _state_version(self):
        adapter = self.extractor.sar_adapter
        return f"sar-g{adapter.generation}-u{adapter.update_count}"

    def combined_model_version(self, sar_mode):
        suffix = self._state_version() if sar_mode else "baseline"
        return f"{self.base_model_version}@{self.index_version}@{suffix}"

    def runtime_status(self):
        self._refresh_shared_state()
        adapter = self.extractor.sar_adapter
        return {
            "status": "maintenance" if self.maintenance else "running",
            "instanceId": Config.INSTANCE_ID,
            "instanceRole": Config.INSTANCE_ROLE,
            "sarEnabled": Config.SAR_ENABLED,
            "baseModelVersion": self.base_model_version,
            "indexVersion": self.index_version,
            "sarStateVersion": self._state_version(),
            "updateCount": adapter.update_count,
            "ema": adapter.ema,
            "featureDrift": self.feature_drift,
            "anchorTop1Retention": self.anchor_top1_retention,
            "anchorCount": len(self.anchor_images),
            "lastCheckpointAt": self.last_checkpoint_at,
            "lastResetReason": self.last_reset_reason,
            "lastError": self.last_error,
            "rebuildJobs": list(self.rebuild_jobs.values())[-10:],
        }

    def _checkpoint_paths(self):
        return Config.SAR_STATE_DIR / "sar_checkpoint.pt", Config.SAR_STATE_DIR / "sar_state.json"

    def _checkpoint_sar(self):
        checkpoint, state_file = self._checkpoint_paths()
        checkpoint.parent.mkdir(parents=True, exist_ok=True)
        checkpoint_tmp = checkpoint.with_name(f"{checkpoint.name}.{Config.INSTANCE_ID}.tmp")
        state_tmp = state_file.with_name(f"{state_file.name}.{Config.INSTANCE_ID}.tmp")
        with exclusive_file_lock(self.shared_state_lock):
            torch.save(self.extractor.sar_adapter.export_state(), checkpoint_tmp)
            now = datetime.now(timezone.utc).isoformat()
            state = {
                "baseModelVersion": self.base_model_version,
                "indexVersion": self.index_version,
                "sarStateVersion": self._state_version(),
                "lastCheckpointAt": now,
                "instanceId": Config.INSTANCE_ID,
            }
            state_tmp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
            os.replace(checkpoint_tmp, checkpoint)
            os.replace(state_tmp, state_file)
            self.sar_state_mtime = state_file.stat().st_mtime_ns
        self.last_checkpoint_at = now
        self._event("checkpoint_saved", sarStateVersion=state["sarStateVersion"])

    def _restore_sar_state(self):
        checkpoint, state_file = self._checkpoint_paths()
        if not checkpoint.exists() or not state_file.exists():
            return
        try:
            lock_path = getattr(self, "shared_state_lock", Config.MODEL_VERSIONS_DIR / "runtime-state.lock")
            with exclusive_file_lock(lock_path):
                metadata = json.loads(state_file.read_text(encoding="utf-8"))
                if metadata.get("baseModelVersion") != self.base_model_version or metadata.get("indexVersion") != self.index_version:
                    self._event("restart_restore_rejected", reason="version_mismatch")
                    return
                try:
                    state = torch.load(checkpoint, map_location=self.extractor.device, weights_only=True)
                except TypeError:
                    state = torch.load(checkpoint, map_location=self.extractor.device)
                self.extractor.sar_adapter.import_state(state)
                self.last_checkpoint_at = metadata.get("lastCheckpointAt")
                self.sar_state_mtime = state_file.stat().st_mtime_ns
            self._event("restart_restored", sarStateVersion=self._state_version())
        except Exception as exc:
            self.last_error = str(exc)
            self._event("restart_restore_failed", error=str(exc))

    def _reset_sar(self, reason):
        self.extractor.reset_sar()
        self.last_reset_reason = reason
        self.feature_drift = None
        self.anchor_top1_retention = None
        checkpoint, state_file = self._checkpoint_paths()
        checkpoint.unlink(missing_ok=True)
        state_file.unlink(missing_ok=True)
        self._checkpoint_sar()
        self._event("sar_reset", reason=reason, sarStateVersion=self._state_version())

    def _refresh_shared_state(self):
        pointer = self._active_index_pointer()
        if pointer.exists():
            shared_version = pointer.read_text(encoding="utf-8").strip()
            if shared_version and shared_version != self.index_version:
                final_dir = Config.MODEL_VERSIONS_DIR / shared_version / "index"
                candidate = FAISSManager(self.extractor.feature_dim, final_dir)
                if final_dir.exists() and candidate.load_index():
                    with self.version_lock:
                        self.index_version = shared_version
                        self.faiss_manager = candidate
                        self.extractor.reset_sar()
                        self.anchor_images = self._anchor_paths()
                        self.anchor_base_features = None
                        self.anchor_base_top1 = []
                        self._initialize_anchors()
                        self.sar_state_mtime = 0
                        self._restore_sar_state()
                        self._event("shared_index_reloaded", indexVersion=shared_version)
        _, state_file = self._checkpoint_paths()
        if state_file.exists() and state_file.stat().st_mtime_ns > self.sar_state_mtime:
            self._restore_sar_state()

    def _anchor_paths(self):
        if not Config.ANCHOR_DATASET_DIR.exists():
            return []
        paths = []
        for folder in sorted(path for path in Config.ANCHOR_DATASET_DIR.iterdir() if path.is_dir()):
            images = []
            for pattern in ("*.jpg", "*.jpeg", "*.png", "*.webp"):
                images.extend(folder.glob(pattern))
            paths.extend(sorted(images)[:3])
        return paths

    def _initialize_anchors(self):
        if not self.anchor_images or self.faiss_manager.index is None:
            return
        self.anchor_base_features = self.extractor.extract_batch(self.anchor_images)
        self.anchor_base_top1 = [self._top1(vector) for vector in self.anchor_base_features]

    def _top1(self, vector):
        results = self.faiss_manager.search_landmarks_by_category(vector, top_k=1)
        return results[0]["landmarkCode"] if results else None

    def _check_anchors(self):
        if self.anchor_base_features is None or not self.anchor_images:
            self._event("anchor_check_skipped", reason="anchor_set_unavailable")
            return True
        current = self.extractor.extract_sar_batch(self.anchor_images)
        current_top1 = [self._top1(vector) for vector in current]
        self.anchor_top1_retention = sum(a == b for a, b in zip(self.anchor_base_top1, current_top1)) / len(current_top1)
        cosine = np.sum(self.anchor_base_features * current, axis=1)
        self.feature_drift = float(np.mean(1.0 - np.clip(cosine, -1.0, 1.0)))
        self._event("anchor_check", retention=self.anchor_top1_retention, featureDrift=self.feature_drift)
        if self.anchor_top1_retention < Config.SAR_ANCHOR_TOP1_RETENTION:
            self._reset_sar("anchor_top1_retention")
            return False
        if self.feature_drift > Config.SAR_FEATURE_DRIFT_THRESHOLD:
            self._reset_sar("anchor_feature_drift")
            return False
        return True

    def _event(self, event, **fields):
        record = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "event": event,
            "baseModelVersion": self.base_model_version,
            "indexVersion": self.index_version,
            "sarStateVersion": self._state_version(),
            **fields,
        }
        with Config.SAR_EVENT_LOG.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(record, ensure_ascii=False) + "\n")

    def get_faiss_stats(self):
        return self.faiss_manager.get_index_stats()

    def extract_and_index_all_landmarks(self):
        job = self.start_rebuild()
        return job

    def start_rebuild(self):
        if Config.INSTANCE_ROLE != "primary":
            raise RuntimeError("index rebuild is only available on the primary instance")
        with self.version_lock:
            if any(job["status"] in {"building", "switching"} for job in self.rebuild_jobs.values()):
                raise RuntimeError("an index rebuild is already running")
            job_id = str(uuid.uuid4())
            job_dir = Config.MODEL_VERSIONS_DIR / f"rebuild-{job_id}"
            job_dir.mkdir(parents=True, exist_ok=False)
            job = {
                "rebuildJobId": job_id,
                "status": "building",
                "createdAt": datetime.now(timezone.utc).isoformat(),
                "error": None,
            }
            self.rebuild_jobs[job_id] = job
            env = os.environ.copy()
            env["DEVICE"] = "cpu"
            algorithm_dir = Path(__file__).resolve().parents[2]
            process = subprocess.Popen(
                [sys.executable, "-m", "app.rebuild_worker", str(job_dir), job_id, self.base_model_version],
                cwd=algorithm_dir,
                env=env,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
            )
            threading.Thread(target=self._monitor_rebuild, args=(job_id, job_dir, process), daemon=True).start()
            self._event("index_rebuild_started", rebuildJobId=job_id)
            return job.copy()

    def rebuild_status(self, job_id):
        return self.rebuild_jobs.get(job_id)

    def _monitor_rebuild(self, job_id, job_dir, process):
        _, stderr = process.communicate()
        job = self.rebuild_jobs[job_id]
        if process.returncode != 0:
            job.update(status="failed", error=(stderr or "candidate builder failed")[-1000:])
            self.last_error = job["error"]
            self._event("index_rebuild_failed", rebuildJobId=job_id, error=job["error"])
            shutil.rmtree(job_dir, ignore_errors=True)
            return
        try:
            manifest = json.loads((job_dir / "candidate" / "manifest.json").read_text(encoding="utf-8"))
            if manifest.get("baseModelVersion") != self.base_model_version:
                raise RuntimeError("candidate was built by a different base model")
            candidate = FAISSManager(self.extractor.feature_dim, job_dir / "candidate" / "index")
            if not candidate.load_index() or candidate.dimension != self.extractor.feature_dim:
                raise RuntimeError("candidate index validation failed")
            if self.anchor_base_features is not None and self.anchor_images:
                expected = [path.parent.name.split("_")[0] for path in self.anchor_images]
                actual = []
                for vector in self.anchor_base_features:
                    result = candidate.search_landmarks_by_category(vector, top_k=1)
                    actual.append(result[0]["landmarkCode"] if result else None)
                retention = sum(a == b for a, b in zip(expected, actual)) / len(expected)
                if retention < Config.SAR_ANCHOR_TOP1_RETENTION:
                    raise RuntimeError(f"candidate anchor retention {retention:.4f} is below threshold")
            job["status"] = "switching"
            self.maintenance = True
            old_version = self.index_version
            old_manager = self.faiss_manager
            pointer = self._active_index_pointer()
            old_pointer = pointer.read_text(encoding="utf-8") if pointer.exists() else None
            with self.version_lock:
                new_version = manifest["indexVersion"]
                final_dir = Config.MODEL_VERSIONS_DIR / new_version
                if final_dir.exists():
                    shutil.rmtree(final_dir)
                os.replace(job_dir / "candidate", final_dir)
                with exclusive_file_lock(self.shared_state_lock):
                    self._replace_active_index_pointer(new_version)
                self.index_version = new_version
                self.faiss_manager = FAISSManager(self.extractor.feature_dim, final_dir / "index")
                if not self.faiss_manager.load_index():
                    raise RuntimeError("published index could not be loaded")
                self._reset_sar("index_published")
                self.anchor_images = self._anchor_paths()
                self._initialize_anchors()
            job.update(status="completed", indexVersion=self.index_version, finishedAt=datetime.now(timezone.utc).isoformat())
            self._event("index_switched", rebuildJobId=job_id, indexVersion=self.index_version)
        except Exception as exc:
            if "old_version" in locals():
                self.index_version = old_version
                self.faiss_manager = old_manager
                with exclusive_file_lock(self.shared_state_lock):
                    self._restore_active_index_pointer(old_pointer)
            job.update(status="failed", error=str(exc))
            self.last_error = str(exc)
            self._event("index_switch_failed", rebuildJobId=job_id, error=str(exc))
        finally:
            self.maintenance = False
            shutil.rmtree(job_dir, ignore_errors=True)
