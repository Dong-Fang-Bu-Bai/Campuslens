"""
Windows-friendly SAR integration smoke test.
Usage:
  python test_windows_sar_check.py --image C:\\path\\to\\sample.jpg
  python test_windows_sar_check.py --image C:\\path\\to\\sample.jpg --base-url http://localhost:8000
The script checks:
- health endpoint
- index stats
- standard search
- SAR search with entropy/trust fields
- feedback endpoint
"""
from __future__ import annotations
import argparse
import sys
from pathlib import Path
from typing import Any, Dict, Optional
import requests
def build_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + path
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
def upload_search(base_url: str, endpoint: str, image_path: Path, params: Optional[Dict[str, Any]] = None) ->Optional[Dict[str, Any]]:
    url = build_url(base_url, endpoint)
    with image_path.open("rb") as fh:
        files = {"file": (image_path.name, fh, "image/jpeg")}
        return request_json("POST", url, files=files, params=params or {})
def main() -> int:
    parser = argparse.ArgumentParser(description="Windows SAR smoke test for CampusLens algorithm service")
    parser.add_argument("--base-url", default="http://localhost:8000", help="Algorithm service base URL")
    parser.add_argument("--image", required=True, help="Path to a local test image")
    parser.add_argument("--use-sar", action="store_true", default=True, help="Enable SAR search (default on)")
    parser.add_argument("--no-sar", dest="use_sar", action="store_false", help="Disable SAR search")
    parser.add_argument("--landmark-code", default="L01", help="Landmark code for feedback test")
    parser.add_argument("--skip-feedback", action="store_true", help="Skip feedback endpoint test")
    args = parser.parse_args()
    image_path = Path(args.image)
    if not image_path.exists():
        print(f"❌ image not found: {image_path}")
        return 1
    print("=" * 72)
    print("CampusLens SAR smoke test")
    print(f"base_url: {args.base_url}")
    print(f"image: {image_path}")
    print(f"use_sar: {args.use_sar}")
    print("=" * 72)
    ok = True
    health = request_json("GET", build_url(args.base_url, "/api/v1/health"), timeout=10)
    if health is None:
        ok = False
    else:
        print_json_compact("health", health)
        if not health.get("sarAvailable", False):
            print("⚠️  sarAvailable is false")
    stats = request_json("GET", build_url(args.base_url, "/api/v1/index/stats"), timeout=10)
    if stats is None:
        ok = False
    else:
        print_json_compact("index stats", stats)
    normal = upload_search(args.base_url, "/api/v1/search", image_path)
    if normal is None:
        ok = False
    else:
        print_json_compact("normal search", normal)
    sar = upload_search(
        args.base_url,
        "/api/v1/search/sar",
        image_path,
        params={"use_sar": str(args.use_sar).lower()},
    )
    if sar is None:
        ok = False
    else:
        print_json_compact("sar search", sar)
        for field in ("entropy", "trustLevel", "trustScore"):
            if field not in sar:
                print(f"⚠️  missing field in SAR response: {field}")
                ok = False
        if sar.get("results"):
            top1 = sar["results"][0]
            for field in ("rank", "landmarkCode", "score", "confidenceLevel", "mahalanobisDistance"):
                if field not in top1:
                    print(f"⚠️  missing top1 field: {field}")
                    ok = False
    if not args.skip_feedback:
        feedback = upload_search(
            args.base_url,
            "/api/v1/feedback",
            image_path,
            params={
                "landmark_code": args.landmark_code,
                "update_index": "true",
            },
        )
        if feedback is None:
            ok = False
        else:
            print_json_compact("feedback", feedback)
            for field in ("feedbackTrust", "pending", "modelEntropy", "modelTrust"):
                if field not in feedback:
                    print(f"⚠️  missing feedback field: {field}")
                    ok = False
    print("\n" + "=" * 72)
    if ok:
        print("✅ smoke test finished")
        return 0
    print("❌ smoke test found issues")
    return 2
if __name__ == "__main__":
    sys.exit(main())
