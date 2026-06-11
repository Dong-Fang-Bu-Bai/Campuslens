import argparse
import json
import math
import statistics
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path

import requests


_thread_local = threading.local()


def session():
    value = getattr(_thread_local, "session", None)
    if value is None:
        value = requests.Session()
        _thread_local.session = value
    return value


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def summarize(name, concurrency, expected, started, results):
    duration = time.perf_counter() - started
    latencies = [item["latency_ms"] for item in results]
    statuses = {}
    errors = {}
    for item in results:
        status = str(item.get("status", "transport_error"))
        statuses[status] = statuses.get(status, 0) + 1
        if item.get("error"):
            key = item["error"][:240]
            errors[key] = errors.get(key, 0) + 1
    http_success = sum(1 for item in results if item.get("http_success"))
    semantic_success = sum(1 for item in results if item.get("semantic_success"))
    return {
        "name": name,
        "concurrency": concurrency,
        "requests": expected,
        "completed": len(results),
        "httpSuccess": http_success,
        "semanticSuccess": semantic_success,
        "httpSuccessRate": round(http_success / expected, 6) if expected else 0,
        "semanticSuccessRate": round(semantic_success / expected, 6) if expected else 0,
        "statusCounts": statuses,
        "errors": errors,
        "durationSeconds": round(duration, 3),
        "throughputRps": round(len(results) / duration, 3) if duration else 0,
        "latencyMs": {
            "min": round(min(latencies), 3) if latencies else None,
            "avg": round(statistics.fmean(latencies), 3) if latencies else None,
            "p50": round(percentile(latencies, 0.50), 3) if latencies else None,
            "p95": round(percentile(latencies, 0.95), 3) if latencies else None,
            "p99": round(percentile(latencies, 0.99), 3) if latencies else None,
            "max": round(max(latencies), 3) if latencies else None,
        },
    }


def execute_stage(name, concurrency, count, operation):
    started = time.perf_counter()
    results = []
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(operation, index) for index in range(count)]
        for future in as_completed(futures):
            try:
                results.append(future.result())
            except Exception as exc:  # Keep the load run alive and report every failure.
                results.append({
                    "latency_ms": 0.0,
                    "http_success": False,
                    "semantic_success": False,
                    "error": f"worker_error: {type(exc).__name__}: {exc}",
                })
    summary = summarize(name, concurrency, count, started, results)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return summary, results


def timed_request(operation, semantic_check):
    started = time.perf_counter()
    try:
        response = operation()
        elapsed = (time.perf_counter() - started) * 1000
        http_success = 200 <= response.status_code < 300
        body = None
        try:
            body = response.json()
        except ValueError:
            pass
        semantic_success = http_success and semantic_check(body)
        error = None
        if not http_success:
            error = f"HTTP {response.status_code}: {response.text[:200]}"
        elif not semantic_success:
            error = "HTTP succeeded but response did not satisfy the stage contract"
        return {
            "status": response.status_code,
            "latency_ms": elapsed,
            "http_success": http_success,
            "semantic_success": semantic_success,
            "error": error,
            "body": body,
        }
    except requests.RequestException as exc:
        return {
            "latency_ms": (time.perf_counter() - started) * 1000,
            "http_success": False,
            "semantic_success": False,
            "error": f"{type(exc).__name__}: {exc}",
        }


def main():
    parser = argparse.ArgumentParser(description="CampusLens layered concurrency test")
    parser.add_argument("--image", required=True)
    parser.add_argument("--output", default=".run/concurrency-results.json")
    parser.add_argument("--backend-url", default="http://localhost:8080")
    parser.add_argument("--algorithm-url", default="http://localhost:8000")
    parser.add_argument("--landmark-concurrency", type=int, default=100)
    parser.add_argument("--landmark-requests", type=int, default=2000)
    parser.add_argument("--algorithm-concurrency", type=int, default=10)
    parser.add_argument("--algorithm-requests", type=int, default=50)
    parser.add_argument("--upload-concurrency", type=int, default=100)
    parser.add_argument("--upload-requests", type=int, default=100)
    parser.add_argument("--completion-concurrency", type=int, default=20)
    parser.add_argument("--completion-limit", type=int, default=50)
    args = parser.parse_args()

    image_path = Path(args.image).resolve()
    image_bytes = image_path.read_bytes()
    content_type = "image/png" if image_path.suffix.lower() == ".png" else "image/jpeg"
    backend = args.backend_url.rstrip("/")
    algorithm = args.algorithm_url.rstrip("/")

    def landmark_request(_):
        return timed_request(
            lambda: session().get(f"{backend}/api/landmarks", timeout=30),
            lambda body: isinstance(body, list) and len(body) >= 10,
        )

    def algorithm_request(_):
        return timed_request(
            lambda: session().post(
                f"{algorithm}/api/v1/search",
                files={"file": (image_path.name, image_bytes, content_type)},
                timeout=240,
            ),
            lambda body: isinstance(body, dict) and len(body.get("results") or []) == 5,
        )

    def upload_request(index):
        return timed_request(
            lambda: session().post(
                f"{backend}/api/search/upload",
                headers={"Idempotency-Key": f"load-{uuid.uuid4()}-{index}"},
                files={
                    "file": (image_path.name, image_bytes, content_type),
                    "guestId": (None, "guest#900000001"),
                },
                timeout=60,
            ),
            lambda body: (
                isinstance(body, dict)
                and isinstance(body.get("searchRecordId"), int)
                and body.get("status") == "queued"
                and isinstance(body.get("jobId"), str)
                and isinstance(body.get("jobToken"), str)
            ),
        )

    def job_completion(submission):
        started = time.perf_counter()
        body = submission.get("body") or {}
        job_id = body.get("jobId")
        token = body.get("jobToken")
        if not job_id or not token:
            return {
                "latency_ms": 0.0,
                "http_success": False,
                "semantic_success": False,
                "error": "submission did not return job credentials",
            }
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            try:
                response = session().get(
                    f"{backend}/api/search/jobs/{job_id}",
                    headers={"X-Search-Job-Token": token},
                    timeout=30,
                )
                status_body = response.json()
                state = status_body.get("status")
                if state in {"success", "low_confidence", "failed"}:
                    semantic_success = (
                        response.status_code == 200
                        and state in {"success", "low_confidence"}
                        and len(status_body.get("results") or []) == 5
                    )
                    return {
                        "status": response.status_code,
                        "latency_ms": (time.perf_counter() - started) * 1000,
                        "http_success": response.status_code == 200,
                        "semantic_success": semantic_success,
                        "error": None if semantic_success else json.dumps(status_body, ensure_ascii=False)[:240],
                        "body": status_body,
                    }
            except requests.RequestException as exc:
                return {
                    "latency_ms": (time.perf_counter() - started) * 1000,
                    "http_success": False,
                    "semantic_success": False,
                    "error": f"{type(exc).__name__}: {exc}",
                }
            time.sleep(0.5)
        return {
            "latency_ms": (time.perf_counter() - started) * 1000,
            "http_success": False,
            "semantic_success": False,
            "error": "job polling timed out",
        }

    warmup_algorithm = algorithm_request(-1)
    warmup_upload = upload_request(-1)
    report = {
        "startedAt": datetime.now(timezone.utc).astimezone().isoformat(),
        "image": str(image_path),
        "warmup": {"algorithm": warmup_algorithm, "upload": warmup_upload},
        "stages": [],
    }
    stage, _ = execute_stage(
        "backend_landmarks", args.landmark_concurrency, args.landmark_requests, landmark_request)
    report["stages"].append(stage)
    stage, _ = execute_stage(
        "algorithm_search", args.algorithm_concurrency, args.algorithm_requests, algorithm_request)
    report["stages"].append(stage)
    submit_stage, submissions = execute_stage(
        "backend_upload_submit", args.upload_concurrency, args.upload_requests, upload_request)
    report["stages"].append(submit_stage)
    accepted = [item for item in submissions if item.get("semantic_success")]
    completion_count = min(args.completion_limit, len(accepted))
    completion_stage, completions = execute_stage(
        "backend_job_completion",
        args.completion_concurrency,
        completion_count,
        lambda index: job_completion(accepted[index]),
    )
    report["stages"].append(completion_stage)
    report["jobs"] = {
        "accepted": len(accepted),
        "uniqueJobIds": len({item["body"]["jobId"] for item in accepted}),
        "completionChecked": completion_count,
        "terminalSuccess": sum(1 for item in completions if item.get("semantic_success")),
    }
    report["finishedAt"] = datetime.now(timezone.utc).astimezone().isoformat()

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Saved results to {output_path.resolve()}")


if __name__ == "__main__":
    main()
