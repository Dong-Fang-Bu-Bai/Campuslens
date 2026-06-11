"""
SAR debug script for Windows.

This script has two layers:
1. API smoke checks: health, index stats, search, SAR search, feedback
2. Model-level checks: whether SAR adaptation changes parameters / EMA / features

Usage:
  python debug_sar.py --image C:\\path\\to\\image.jpg
  python debug_sar.py --image C:\\path\\to\\image.jpg --iterations 5

Optional:
  --base-url http://localhost:8000
  --skip-model-check
  --skip-api-check
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from typing import Any, Dict, Optional

import numpy as np
import requests
from PIL import Image

PROJECT_ROOT = Path(__file__).resolve().parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.config import Config
from app.models.sar_dinov2_extractor import SARDINOv2Extractor
from app.utils.faiss_manager import FAISSManager


DEFAULT_BASE_URL = "http://localhost:8000"
DEFAULT_LOW_THRESHOLD = 0.35
DEFAULT_HIGH_THRESHOLD = 0.60


def build_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + path


def print_section(title: str) -> None:
    print("\n" + "=" * 78)
    print(title)
    print("=" * 78)


def print_json_compact(title: str, data: Dict[str, Any]) -> None:
    print(f"\n{title}")
    for key in sorted(data.keys()):
        value = data[key]
        if key == "results" and isinstance(value, list):
            print(f"  {key}: {len(value)} items")
            if value:
                top1 = value[0]
                print(
                    "    top1: "
                    f"{top1.get('landmarkCode')} "
                    f"score={top1.get('score')} "
                    f"level={top1.get('confidenceLevel')} "
                    f"D={top1.get('mahalanobisDistance')}"
                )
            continue
        print(f"  {key}: {value}")


def request_json(method: str, url: str, **kwargs) -> Optional[Dict[str, Any]]:
    try:
        response = requests.request(method, url, timeout=kwargs.pop("timeout", 30), **kwargs)
    except requests.RequestException as exc:
        print(f"❌ {method} {url} failed: {exc}")
        return None

    print(f"{method} {url} -> {response.status_code}")
    try:
        payload = response.json()
    except ValueError:
        print(response.text)
        return None

    if response.status_code >= 400:
        print_json_compact("error payload", payload)
        return None

    return payload


def load_landmark_stats():
    faiss_manager = FAISSManager(dimension=768)
    if not faiss_manager.load_index():
        return None, None, 0

    if not faiss_manager.landmark_stats or not faiss_manager.landmark_codes:
        return None, None, 0

    landmark_means = []
    landmark_cov_invs = []
    for code in faiss_manager.landmark_codes:
        stats = faiss_manager.landmark_stats[code]
        landmark_means.append(stats["mean"])
        landmark_cov_invs.append(stats["cov_inv"])

    return landmark_means, landmark_cov_invs, len(landmark_means)


def api_smoke_check(base_url: str, image_path: Path, use_sar: bool, landmark_code: str) -> bool:
    ok = True

    print_section("API CHECK")

    health = request_json("GET", build_url(base_url, "/api/v1/health"), timeout=10)
    if health is None:
        ok = False
    else:
        print_json_compact("health", health)

    stats = request_json("GET", build_url(base_url, "/api/v1/index/stats"), timeout=10)
    if stats is None:
        ok = False
    else:
        print_json_compact("index stats", stats)

    with image_path.open("rb") as fh:
        normal = request_json(
            "POST",
            build_url(base_url, "/api/v1/search"),
            files={"file": (image_path.name, fh, "image/jpeg")},
        )
    if normal is None:
        ok = False
    else:
        print_json_compact("normal search", normal)

    with image_path.open("rb") as fh:
        sar = request_json(
            "POST",
            build_url(base_url, "/api/v1/search/sar"),
            files={"file": (image_path.name, fh, "image/jpeg")},
            params={"use_sar": str(use_sar).lower()},
        )
    if sar is None:
        ok = False
    else:
        print_json_compact("sar search", sar)
        entropy = sar.get("entropy")
        if entropy is not None:
            print(f"  entropy={entropy:.6f}")
            print(f"  thresholds: low={DEFAULT_LOW_THRESHOLD:.2f}, high={DEFAULT_HIGH_THRESHOLD:.2f}")
            if entropy < DEFAULT_LOW_THRESHOLD:
                print("  trust verdict: trusted")
            elif entropy < DEFAULT_HIGH_THRESHOLD:
                print("  trust verdict: uncertain")
            else:
                print("  trust verdict: untrusted")
        for field in ("entropy", "trustLevel", "trustScore"):
            if field not in sar:
                print(f"⚠️  missing SAR field: {field}")
                ok = False

    with image_path.open("rb") as fh:
        feedback = request_json(
            "POST",
            build_url(base_url, "/api/v1/feedback"),
            files={"file": (image_path.name, fh, "image/jpeg")},
            params={"landmark_code": landmark_code, "update_index": "true"},
        )
    if feedback is None:
        ok = False
    else:
        print_json_compact("feedback", feedback)
        for field in ("feedbackTrust", "pending", "modelEntropy", "modelTrust"):
            if field not in feedback:
                print(f"⚠️  missing feedback field: {field}")
                ok = False

    return ok


def build_extractor() -> SARDINOv2Extractor:
    landmark_means, landmark_cov_invs, num_landmarks = load_landmark_stats()
    return SARDINOv2Extractor(
        model_path=Config.DINO_MODEL_PATH,
        device=Config.DEVICE,
        sar_steps=1,
        landmark_means=landmark_means,
        landmark_cov_invs=landmark_cov_invs,
        num_classes=num_landmarks if num_landmarks > 0 else 1000,
    )


def model_debug_check(image_path: Path, iterations: int) -> bool:
    print_section("MODEL CHECK")
    print(f"image: {image_path}")
    print(f"iterations: {iterations}")

    extractor = build_extractor()
    image = Image.open(image_path).convert("RGB")

    baseline = extractor.extract_single(image, use_sar=False)
    print(f"baseline feature norm: {np.linalg.norm(baseline):.6f}")

    initial_params: Dict[str, np.ndarray] = {}
    for name, param in extractor.model.named_parameters():
        if param.requires_grad:
            initial_params[name] = param.detach().cpu().numpy().copy()

    features = []
    emas = []
    total_param_deltas = []

    for idx in range(iterations):
        print(f"\n--- iteration {idx + 1}/{iterations} ---")
        before = {
            name: param.detach().cpu().numpy().copy()
            for name, param in extractor.model.named_parameters()
            if param.requires_grad
        }

        feature = extractor.extract_single(image, use_sar=True)
        features.append(feature)
        ema = extractor.get_sar_ema()
        emas.append(ema)

        after = {
            name: param.detach().cpu().numpy().copy()
            for name, param in extractor.model.named_parameters()
            if param.requires_grad
        }

        deltas = []
        for name in before:
            delta = float(np.mean(np.abs(after[name] - before[name])))
            deltas.append(delta)
        total_delta = float(np.sum(deltas)) if deltas else 0.0
        total_param_deltas.append(total_delta)

        print(f"feature norm: {np.linalg.norm(feature):.6f}")
        print(f"ema: {ema}")
        print(f"param delta total: {total_delta:.10f}")
        if deltas:
            top_changes = sorted(
                ((name, float(np.mean(np.abs(after[name] - before[name])))) for name in before),
                key=lambda x: x[1],
                reverse=True,
            )[:3]
            print("top parameter changes:")
            for name, change in top_changes:
                print(f"  {name}: {change:.10f}")

        if idx > 0:
            diff = float(np.linalg.norm(features[idx] - features[idx - 1]))
            print(f"feature diff from previous: {diff:.10f}")

        time.sleep(0.1)

    print_section("MODEL SUMMARY")
    print(f"feature norms: {[round(float(np.linalg.norm(f)), 6) for f in features]}")
    print(f"ema history: {emas}")
    print(f"param delta history: {[round(v, 10) for v in total_param_deltas]}")

    final_params = {
        name: param.detach().cpu().numpy().copy()
        for name, param in extractor.model.named_parameters()
        if param.requires_grad
    }
    total_change = 0.0
    for name, init_value in initial_params.items():
        total_change += float(np.mean(np.abs(final_params[name] - init_value)))

    print(f"total change from initial params: {total_change:.10f}")

    if total_change < 1e-8:
        print("❌ SAR adaptation does not appear to update parameters")
        return False

    print("✅ SAR adaptation appears to update parameters")
    if len(features) > 1:
        drift = float(np.linalg.norm(features[-1] - features[0]))
        print(f"feature drift from first to last: {drift:.10f}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="CampusLens SAR debug script")
    parser.add_argument("--image", required=True, help="Path to a local test image")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Algorithm service base URL")
    parser.add_argument("--iterations", type=int, default=5, help="SAR model debug iterations")
    parser.add_argument("--landmark-code", default="L01", help="Feedback landmark code")
    parser.add_argument("--skip-api-check", action="store_true", help="Skip API checks")
    parser.add_argument("--skip-model-check", action="store_true", help="Skip model checks")
    parser.add_argument("--no-sar", action="store_true", help="Disable SAR on API search")
    args = parser.parse_args()

    image_path = Path(args.image)
    if not image_path.exists():
        print(f"❌ image not found: {image_path}")
        return 1

    print_section("SETUP")
    print(f"base_url: {args.base_url}")
    print(f"image: {image_path}")
    print(f"iterations: {args.iterations}")
    print(f"use_sar: {not args.no_sar}")

    ok = True
    if not args.skip_api_check:
        ok = api_smoke_check(args.base_url, image_path, use_sar=not args.no_sar, landmark_code=args.landmark_code) and ok
    if not args.skip_model_check:
        ok = model_debug_check(image_path, args.iterations) and ok

    print_section("RESULT")
    if ok:
        print("✅ debug finished")
        return 0

    print("❌ debug found issues")
    return 2


if __name__ == "__main__":
    sys.exit(main())