from __future__ import annotations

import csv
import json
import platform
import random
import tempfile
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from .metrics import build_summary
from .perturbations import apply_perturbation, load_rgb, read_conditions
from .sampling import read_base_manifest


CSV_FIELDS = (
    "runId", "mode", "seed", "orderIndex", "sampleId", "baseImageId",
    "landmarkCode", "landmarkName", "sourcePath", "perturbationType",
    "perturbationLabel", "severity", "parameterName", "parameterValue",
    "noiseSeed", "predictedCode", "trueRank", "top1Correct", "top5Correct",
    "reciprocalRank", "top1Score", "top2Score", "scoreMargin", "latencyMs",
    "sarApplied", "trustLevel", "top1Retention", "top5Jaccard",
    "baseModelVersion", "indexVersion", "sarStateVersion", "modelVersion",
    "topResultsJson",
)


@contextmanager
def isolated_sar_runtime(run_id: str):
    from app.config import Config

    original = {
        "SAR_STATE_DIR": Config.SAR_STATE_DIR,
        "SAR_EVENT_LOG": Config.SAR_EVENT_LOG,
        "SAR_ENABLED": Config.SAR_ENABLED,
        "INSTANCE_ID": Config.INSTANCE_ID,
        "INSTANCE_ROLE": Config.INSTANCE_ROLE,
    }
    with tempfile.TemporaryDirectory(prefix=f"campuslens-robustness-{run_id}-") as temporary:
        root = Path(temporary)
        Config.SAR_STATE_DIR = root / "sar_state"
        Config.SAR_EVENT_LOG = root / "sar_events.jsonl"
        Config.SAR_ENABLED = True
        Config.INSTANCE_ID = f"robustness-{run_id}"
        Config.INSTANCE_ROLE = "evaluation"
        try:
            yield root
        finally:
            for name, value in original.items():
                setattr(Config, name, value)


def _response_record(condition: dict, response: dict, *, run_id: str, mode: str, seed: int | None, order_index: int, latency_ms: float) -> dict:
    top_results = response.get("results") or []
    codes = [item.get("landmarkCode") for item in top_results]
    true_rank = codes.index(condition["landmarkCode"]) + 1 if condition["landmarkCode"] in codes else 0
    top1_score = float(top_results[0]["score"]) if top_results else 0.0
    top2_score = float(top_results[1]["score"]) if len(top_results) > 1 else 0.0
    return {
        **condition,
        "runId": run_id,
        "mode": mode,
        "seed": "" if seed is None else seed,
        "orderIndex": order_index,
        "predictedCode": codes[0] if codes else "",
        "trueRank": true_rank,
        "top1Correct": int(true_rank == 1),
        "top5Correct": int(true_rank > 0),
        "reciprocalRank": 1.0 / true_rank if true_rank else 0.0,
        "top1Score": top1_score,
        "top2Score": top2_score,
        "scoreMargin": top1_score - top2_score,
        "latencyMs": round(latency_ms, 4),
        "sarApplied": int(bool(response.get("sarApplied"))),
        "trustLevel": response.get("trustLevel") or "",
        "baseModelVersion": response.get("baseModelVersion") or "",
        "indexVersion": response.get("indexVersion") or "",
        "sarStateVersion": response.get("sarStateVersion") or "",
        "modelVersion": response.get("modelVersion") or "",
        "topResults": top_results,
        "topResultsJson": json.dumps(top_results, ensure_ascii=False, separators=(",", ":")),
    }


def _load_completed_run(path: Path) -> tuple[list[dict], dict] | None:
    if not path.is_file():
        return None
    payload = json.loads(path.read_text(encoding="utf-8"))
    records = payload.get("records") or []
    if len(records) != 800:
        raise RuntimeError(f"已完成运行文件记录数错误: {path}")
    return records, payload.get("diagnostic") or {}


def _execute_run(
    conditions: list[dict],
    base_by_id: dict[str, dict],
    project_dir: Path,
    runs_dir: Path,
    *,
    run_id: str,
    mode: str,
    seed: int | None,
    expected_versions: tuple[str, str] | None,
) -> tuple[list[dict], dict, tuple[str, str]]:
    import torch
    from app.services.feature_service import FeatureService
    from app.services.search_service import SearchService

    final_path = runs_dir / f"{run_id}.json"
    completed = _load_completed_run(final_path)
    if completed is not None:
        records, diagnostic = completed
        versions = (diagnostic["baseModelVersion"], diagnostic["indexVersion"])
        if expected_versions is not None and versions != expected_versions:
            raise RuntimeError(f"已完成运行的模型/索引版本不一致: {run_id}")
        print(f"[RESUME] 复用已完成运行 {run_id}")
        return records, diagnostic, versions

    partial_path = runs_dir / f"{run_id}.partial.jsonl"
    partial_path.unlink(missing_ok=True)
    ordered = list(conditions)
    if seed is not None:
        random.Random(seed).shuffle(ordered)

    with isolated_sar_runtime(run_id):
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            torch.cuda.reset_peak_memory_stats()
        feature_service = FeatureService()
        search_service = SearchService(feature_service)
        runtime = feature_service.runtime_status()
        versions = (runtime["baseModelVersion"], runtime["indexVersion"])
        if expected_versions is not None and versions != expected_versions:
            raise RuntimeError(f"测试期间模型或索引版本变化: {expected_versions} -> {versions}")
        if feature_service.faiss_manager.index is None or len(feature_service.faiss_manager.landmark_stats) != 10:
            raise RuntimeError("正式FAISS索引不可用或地标统计不是10类")

        first_base = base_by_id[ordered[0]["baseImageId"]]
        warmup = load_rgb(project_dir / first_base["sourcePath"])
        search_service.search_with_metadata(warmup, 5, False)

        records: list[dict] = []
        started_run = time.perf_counter()
        with partial_path.open("w", encoding="utf-8") as partial:
            for order_index, condition in enumerate(ordered, start=1):
                base = base_by_id[condition["baseImageId"]]
                original = load_rgb(project_dir / base["sourcePath"])
                image = apply_perturbation(
                    original,
                    condition["perturbationType"],
                    int(condition["severity"]),
                    noise_seed=int(condition["noiseSeed"]),
                )
                started = time.perf_counter()
                response = search_service.search_with_metadata(image, 5, mode == "sar")
                latency_ms = (time.perf_counter() - started) * 1000
                if (response.get("baseModelVersion"), response.get("indexVersion")) != versions:
                    raise RuntimeError(f"{condition['sampleId']}执行期间模型或索引版本变化")
                record = _response_record(
                    condition,
                    response,
                    run_id=run_id,
                    mode=mode,
                    seed=seed,
                    order_index=order_index,
                    latency_ms=latency_ms,
                )
                records.append(record)
                partial.write(json.dumps({key: value for key, value in record.items() if key != "topResultsJson"}, ensure_ascii=False) + "\n")
                partial.flush()
                if order_index % 25 == 0 or order_index == len(ordered):
                    elapsed = time.perf_counter() - started_run
                    rate = order_index / elapsed if elapsed else 0.0
                    remaining = (len(ordered) - order_index) / rate if rate else 0.0
                    print(f"[{run_id}] {order_index}/{len(ordered)} | {rate:.2f} req/s | ETA {remaining / 60:.1f} min", flush=True)

        final_runtime = feature_service.runtime_status()
        diagnostic = {
            "runId": run_id,
            "mode": mode,
            "seed": seed,
            "requests": len(records),
            "baseModelVersion": versions[0],
            "indexVersion": versions[1],
            "initialSarStateVersion": runtime["sarStateVersion"],
            "finalSarStateVersion": final_runtime["sarStateVersion"],
            "elapsedSeconds": time.perf_counter() - started_run,
            "peakGpuMemoryMb": torch.cuda.max_memory_allocated() / 1024 / 1024 if torch.cuda.is_available() else 0.0,
        }
        final_path.write_text(
            json.dumps({"diagnostic": diagnostic, "records": records}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        partial_path.unlink(missing_ok=True)
        del search_service
        del feature_service
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
        return records, diagnostic, versions


def _write_consolidated(output_dir: Path, records: list[dict], summary: dict) -> None:
    with (output_dir / "per_sample.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=CSV_FIELDS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(records)
    json_records = [{key: value for key, value in record.items() if key != "topResultsJson"} for record in records]
    (output_dir / "per_sample.json").write_text(
        json.dumps(json_records, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def run_experiment(
    project_dir: Path,
    output_dir: Path,
    *,
    seeds: tuple[int, ...] = (42, 2026, 704),
    bootstrap_draws: int = 2000,
) -> dict:
    import torch

    project_dir = Path(project_dir).resolve()
    output_dir = Path(output_dir).resolve()
    base_rows = read_base_manifest(output_dir / "robustness_base_manifest.csv", project_dir)
    conditions = read_conditions(output_dir / "perturbation_manifest.csv")
    base_by_id = {row["baseImageId"]: row for row in base_rows}
    runs_dir = output_dir / "runs"
    runs_dir.mkdir(parents=True, exist_ok=True)

    all_records: list[dict] = []
    diagnostics: list[dict] = []
    baseline, diagnostic, versions = _execute_run(
        conditions, base_by_id, project_dir, runs_dir,
        run_id="baseline", mode="baseline", seed=None, expected_versions=None,
    )
    all_records.extend(baseline)
    diagnostics.append(diagnostic)
    for seed in seeds:
        records, diagnostic, _ = _execute_run(
            conditions, base_by_id, project_dir, runs_dir,
            run_id=f"sar-seed-{seed}", mode="sar", seed=seed, expected_versions=versions,
        )
        all_records.extend(records)
        diagnostics.append(diagnostic)

    if len(all_records) != 800 * (1 + len(seeds)):
        raise RuntimeError(f"完整实验应有{800 * (1 + len(seeds))}条记录，实际为{len(all_records)}条")
    metadata = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "projectDirectory": str(project_dir),
        "sourceImages": 212,
        "baseImages": 50,
        "conditions": 800,
        "baselineRuns": 1,
        "sarSeeds": list(seeds),
        "python": platform.python_version(),
        "platform": platform.platform(),
        "torch": torch.__version__,
        "cudaAvailable": torch.cuda.is_available(),
        "device": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "cpu",
        "baseModelVersion": versions[0],
        "indexVersion": versions[1],
        "runDiagnostics": diagnostics,
    }
    enriched, summary = build_summary(
        all_records,
        metadata,
        bootstrap_draws=bootstrap_draws,
    )
    _write_consolidated(output_dir, enriched, summary)
    return summary
