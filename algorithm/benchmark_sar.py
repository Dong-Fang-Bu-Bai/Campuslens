from __future__ import annotations

import argparse
import json
import statistics
import tempfile
import time
from pathlib import Path

import torch
from PIL import Image

from app.config import Config
from app.services.feature_service import FeatureService
from app.services.search_service import SearchService


def percentile(values, fraction):
    ordered = sorted(values)
    if not ordered:
        return 0.0
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * fraction)))
    return ordered[index]


def collect_images(limit_per_landmark):
    samples = []
    for folder in sorted(path for path in Config.DATASETS_DIR.iterdir() if path.is_dir()):
        code = folder.name.split("_")[0]
        files = []
        for pattern in ("*.jpg", "*.jpeg", "*.png", "*.webp"):
            files.extend(folder.glob(pattern))
        for path in sorted(files)[:limit_per_landmark]:
            samples.append((code, path))
    return samples


def run_mode(service, samples, sar_mode):
    latencies = []
    top1 = 0
    top5 = 0
    if torch.cuda.is_available():
        torch.cuda.reset_peak_memory_stats()
    for expected, path in samples:
        image = Image.open(path).convert("RGB")
        started = time.perf_counter()
        response = service.search_with_metadata(image, Config.TOP_K_RESULTS, sar_mode)
        latencies.append((time.perf_counter() - started) * 1000)
        codes = [item["landmarkCode"] for item in response["results"]]
        top1 += int(bool(codes) and codes[0] == expected)
        top5 += int(expected in codes)
    total = len(samples)
    return {
        "samples": total,
        "top1": top1 / total if total else 0.0,
        "top5": top5 / total if total else 0.0,
        "averageLatencyMs": statistics.mean(latencies) if latencies else 0.0,
        "p95LatencyMs": percentile(latencies, 0.95),
        "peakGpuMemoryMb": torch.cuda.max_memory_allocated() / 1024 / 1024 if torch.cuda.is_available() else 0.0,
    }


def main():
    parser = argparse.ArgumentParser(description="Compare CampusLens baseline and persistent SAR")
    parser.add_argument("--limit-per-landmark", type=int, default=2)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    original_state_dir = Config.SAR_STATE_DIR
    original_event_log = Config.SAR_EVENT_LOG
    original_enabled = Config.SAR_ENABLED
    with tempfile.TemporaryDirectory(prefix="campuslens-sar-benchmark-") as tmp:
        Config.SAR_STATE_DIR = Path(tmp) / "state"
        Config.SAR_EVENT_LOG = Path(tmp) / "sar_events.jsonl"
        Config.SAR_ENABLED = True
        feature_service = FeatureService()
        search_service = SearchService(feature_service)
        samples = collect_images(args.limit_per_landmark)
        if not samples:
            raise SystemExit("no evaluation images found")
        report = {
            "device": feature_service.extractor.device,
            "modelVersion": feature_service.combined_model_version(False),
            "baseline": run_mode(search_service, samples, False),
            "sar": run_mode(search_service, samples, True),
        }
    Config.SAR_STATE_DIR = original_state_dir
    Config.SAR_EVENT_LOG = original_event_log
    Config.SAR_ENABLED = original_enabled
    report["accepted"] = (
        report["sar"]["top1"] >= report["baseline"]["top1"]
        and report["sar"]["top5"] >= report["baseline"]["top5"]
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
