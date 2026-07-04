from __future__ import annotations

import json
import statistics
from collections import defaultdict
from datetime import datetime, timezone

import numpy as np

from .perturbations import PERTURBATIONS


METRICS = (
    "top1Correct",
    "top5Correct",
    "reciprocalRank",
    "scoreMargin",
    "top1Retention",
    "top5Jaccard",
)


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(float(value) for value in values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * fraction)))
    return ordered[index]


def _top_codes(record: dict) -> list[str]:
    results = record.get("topResults")
    if results is None:
        results = json.loads(record["topResultsJson"])
    return [str(item["landmarkCode"]) for item in results]


def add_stability_metrics(records: list[dict]) -> list[dict]:
    baseline_clean = {
        record["baseImageId"]: record
        for record in records
        if record["mode"] == "baseline" and record["perturbationType"] == "clean"
    }
    if len(baseline_clean) != 50:
        raise ValueError(f"需要50条Baseline原图参考，实际为{len(baseline_clean)}条")
    enriched = []
    for source in records:
        record = dict(source)
        reference = baseline_clean[record["baseImageId"]]
        reference_codes = set(_top_codes(reference))
        current_codes = set(_top_codes(record))
        union = reference_codes | current_codes
        record["top1Retention"] = int(record["predictedCode"] == reference["predictedCode"])
        record["top5Jaccard"] = len(reference_codes & current_codes) / len(union) if union else 1.0
        enriched.append(record)
    return enriched


def _cluster_bootstrap(
    baseline_by_base: dict[str, float],
    sar_by_base: dict[str, float],
    *,
    draws: int,
    seed: int,
) -> dict:
    base_ids = sorted(set(baseline_by_base) & set(sar_by_base))
    if len(base_ids) != 50:
        raise ValueError(f"配对统计必须包含50张基础图片，实际为{len(base_ids)}张")
    baseline = np.array([baseline_by_base[item] for item in base_ids], dtype=np.float64)
    sar = np.array([sar_by_base[item] for item in base_ids], dtype=np.float64)
    rng = np.random.default_rng(seed)
    indices = rng.integers(0, len(base_ids), size=(draws, len(base_ids)))
    baseline_draws = baseline[indices].mean(axis=1)
    sar_draws = sar[indices].mean(axis=1)
    delta_draws = sar_draws - baseline_draws
    return {
        "baseSamples": len(base_ids),
        "baseline": float(baseline.mean()),
        "baselineCi95": [float(np.quantile(baseline_draws, 0.025)), float(np.quantile(baseline_draws, 0.975))],
        "sar": float(sar.mean()),
        "sarCi95": [float(np.quantile(sar_draws, 0.025)), float(np.quantile(sar_draws, 0.975))],
        "delta": float((sar - baseline).mean()),
        "deltaCi95": [float(np.quantile(delta_draws, 0.025)), float(np.quantile(delta_draws, 0.975))],
    }


def _cell_records(records: list[dict], perturbation_type: str, severity: int) -> list[dict]:
    if severity == 0:
        return [record for record in records if record["perturbationType"] == "clean"]
    return [
        record for record in records
        if record["perturbationType"] == perturbation_type and int(record["severity"]) == severity
    ]


def _paired_values(cell: list[dict], metric: str) -> tuple[dict[str, float], dict[str, float]]:
    baseline = {
        record["baseImageId"]: float(record[metric])
        for record in cell if record["mode"] == "baseline"
    }
    sar_values: dict[str, list[float]] = defaultdict(list)
    for record in cell:
        if record["mode"] == "sar":
            sar_values[record["baseImageId"]].append(float(record[metric]))
    sar = {base_id: statistics.mean(values) for base_id, values in sar_values.items()}
    return baseline, sar


def _latency_block(records: list[dict]) -> dict:
    values = [float(record["latencyMs"]) for record in records]
    return {
        "requests": len(values),
        "averageLatencyMs": statistics.mean(values) if values else 0.0,
        "p50LatencyMs": percentile(values, 0.50),
        "p95LatencyMs": percentile(values, 0.95),
    }


def build_summary(
    records: list[dict],
    metadata: dict,
    *,
    bootstrap_draws: int = 2000,
    bootstrap_seed: int = 20260704,
) -> tuple[list[dict], dict]:
    enriched = add_stability_metrics(records)
    curve_data = []
    for perturbation_index, perturbation_type in enumerate(PERTURBATIONS):
        for severity in range(6):
            cell = _cell_records(enriched, perturbation_type, severity)
            for metric_index, metric in enumerate(METRICS):
                baseline, sar = _paired_values(cell, metric)
                stats = _cluster_bootstrap(
                    baseline,
                    sar,
                    draws=bootstrap_draws,
                    seed=bootstrap_seed + perturbation_index * 100 + severity * 10 + metric_index,
                )
                curve_data.append({
                    "perturbationType": perturbation_type,
                    "perturbationLabel": PERTURBATIONS[perturbation_type]["label"],
                    "severity": severity,
                    "metric": metric,
                    **stats,
                })

    baseline_records = [record for record in enriched if record["mode"] == "baseline"]
    sar_records = [record for record in enriched if record["mode"] == "sar"]
    sar_runs = {}
    for run_id in sorted({record["runId"] for record in sar_records}):
        run_records = [record for record in sar_records if record["runId"] == run_id]
        sar_runs[run_id] = {
            "requests": len(run_records),
            "top1": statistics.mean(float(record["top1Correct"]) for record in run_records),
            "top5": statistics.mean(float(record["top5Correct"]) for record in run_records),
            "sarAppliedRate": statistics.mean(float(record["sarApplied"]) for record in run_records),
        }

    stressed_top1 = [
        row for row in curve_data
        if row["metric"] == "top1Correct" and int(row["severity"]) > 0
    ]
    mean_delta = statistics.mean(row["delta"] for row in stressed_top1)
    baseline_latency = _latency_block(baseline_records)
    sar_latency = _latency_block(sar_records)
    p95_delta = sar_latency["p95LatencyMs"] - baseline_latency["p95LatencyMs"]
    if mean_delta > 0.001:
        direction = f"SAR在全部受控扰动条件上的平均Top-1提高{mean_delta * 100:.1f}个百分点"
    elif mean_delta < -0.001:
        direction = f"SAR在全部受控扰动条件上的平均Top-1下降{abs(mean_delta) * 100:.1f}个百分点"
    else:
        direction = "SAR与普通模式在全部受控扰动条件上的平均Top-1基本持平"

    summary = {
        "protocolVersion": "campuslens-sar-robustness-v1",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "boundary": "基于建库图片的受控扰动压力测试，不代表独立现场测试集泛化准确率。",
        "metadata": metadata,
        "baseImages": 50,
        "conditions": 800,
        "records": len(enriched),
        "bootstrap": {"unit": "baseImageId", "draws": bootstrap_draws, "seed": bootstrap_seed},
        "latency": {"baseline": baseline_latency, "sar": sar_latency},
        "sarRuns": sar_runs,
        "curveData": curve_data,
        "conclusion": f"{direction}；SAR的P95延迟变化为{p95_delta:+.0f} ms。",
    }
    return enriched, summary
