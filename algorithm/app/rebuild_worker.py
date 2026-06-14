import json
import os
from pathlib import Path
import sys

import numpy as np

from app.config import Config
from app.models.dinov2_extractor import DINOv2Extractor
from app.utils.faiss_manager import FAISSManager


def main(job_dir, job_id, base_model_version):
    job_dir = Path(job_dir)
    temporary = job_dir / "candidate.tmp"
    candidate = job_dir / "candidate"
    temporary.mkdir(parents=True, exist_ok=False)
    extractor = DINOv2Extractor(Config.DINO_MODEL_PATH, "cpu", False, dual_track=False)
    image_paths = []
    metadata = []
    for folder in sorted(path for path in Config.DATASETS_DIR.iterdir() if path.is_dir()):
        code = folder.name.split("_")[0]
        name = "_".join(folder.name.split("_")[1:])
        images = []
        for pattern in ("*.jpg", "*.jpeg", "*.png", "*.webp"):
            images.extend(folder.rglob(pattern))
        for image_path in sorted(set(images)):
            image_paths.append(image_path)
            metadata.append({
                "landmark_code": code,
                "landmark_name": name,
                "image_path": str(image_path).replace("\\", "/"),
                "image_filename": image_path.name,
            })
    if not image_paths:
        raise RuntimeError("dataset contains no valid images")
    batch_size = max(1, int(os.getenv("REBUILD_BATCH_SIZE", "4")))
    batches = []
    for start in range(0, len(image_paths), batch_size):
        batches.append(extractor.extract_batch(image_paths[start:start + batch_size]))
    vectors = np.concatenate(batches, axis=0)
    manager = FAISSManager(extractor.feature_dim, temporary / "index")
    manager.rebuild_from_scratch(vectors, metadata)
    version = f"index-{job_id}"
    manifest = {
        "indexVersion": version,
        "baseModelVersion": base_model_version,
        "baseModelPath": str(Config.DINO_MODEL_PATH),
        "dimension": extractor.feature_dim,
        "totalImages": len(image_paths),
        "totalLandmarks": len(set(item["landmark_code"] for item in metadata)),
    }
    (temporary / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(temporary, candidate)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
