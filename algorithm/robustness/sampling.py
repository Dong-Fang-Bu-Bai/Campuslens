from __future__ import annotations

import csv
import hashlib
import random
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageOps


LANDMARKS = {
    "L01": "图书馆",
    "L02": "学术大讲堂",
    "L03": "文雍广场",
    "L04": "博学桥",
    "L05": "琴湖及湖心岛",
    "L06": "体育馆",
    "L07": "游泳馆",
    "L08": "第一饭堂",
    "L09": "第二饭堂",
    "L10": "中心酒店",
}

ANGLE_PRIORITY = ("front", "back", "entrance", "far", "side", "near")
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tif", ".tiff"}
BASE_MANIFEST_FIELDS = (
    "baseImageId",
    "landmarkCode",
    "landmarkName",
    "sourcePath",
    "angleTag",
    "lightTag",
    "sha256",
    "dHash",
    "selectionReason",
)


class SamplingError(ValueError):
    pass


@dataclass(frozen=True)
class Candidate:
    path: Path
    angle: str
    light: str
    sha256: str
    dhash: int


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def difference_hash(path: Path, hash_size: int = 8) -> int:
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("L").resize((hash_size + 1, hash_size))
        pixels = list(image.getdata())
    value = 0
    width = hash_size + 1
    for row in range(hash_size):
        for column in range(hash_size):
            value = (value << 1) | int(
                pixels[row * width + column] > pixels[row * width + column + 1]
            )
    return value


def hamming_distance(left: int, right: int) -> int:
    return (left ^ right).bit_count()


def _tags(path: Path) -> tuple[str, str]:
    tokens = path.stem.lower().split("_")
    return (
        tokens[1] if len(tokens) > 1 else "unknown",
        tokens[2] if len(tokens) > 2 else "unknown",
    )


def _candidate(path: Path) -> Candidate:
    angle, light = _tags(path)
    return Candidate(path, angle, light, sha256_file(path), difference_hash(path))


def _pick_most_different(
    options: list[Candidate],
    chosen: list[Candidate],
    rng: random.Random,
) -> Candidate:
    ordered = sorted(options, key=lambda item: item.path.name.lower())
    if not chosen:
        return ordered[rng.randrange(len(ordered))]
    distances = {
        option.path: min(hamming_distance(option.dhash, item.dhash) for item in chosen)
        for option in ordered
    }
    best_distance = max(distances.values())
    tied = [option for option in ordered if distances[option.path] == best_distance]
    return tied[rng.randrange(len(tied))]


def select_base_images(
    source_dir: Path,
    output_manifest: Path,
    *,
    count_per_landmark: int = 5,
    seed: int = 42,
) -> list[dict]:
    source_dir = Path(source_dir).resolve()
    output_manifest = Path(output_manifest).resolve()
    if not source_dir.is_dir():
        raise SamplingError(f"建库图片目录不存在: {source_dir}")
    project_dir = source_dir.parents[1]
    selected_rows: list[dict] = []

    folders = {
        folder.name.split("_")[0]: folder
        for folder in source_dir.iterdir()
        if folder.is_dir() and folder.name.split("_")[0] in LANDMARKS
    }
    if set(folders) != set(LANDMARKS):
        missing = sorted(set(LANDMARKS) - set(folders))
        raise SamplingError(f"缺少地标目录: {', '.join(missing)}")

    for code, name in LANDMARKS.items():
        files = sorted(
            path for path in folders[code].iterdir()
            if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
        )
        if len(files) < count_per_landmark:
            raise SamplingError(f"{code}只有{len(files)}张图片，无法抽取{count_per_landmark}张")
        candidates = [_candidate(path) for path in files]
        if len({item.sha256 for item in candidates}) != len(candidates):
            raise SamplingError(f"{code}建库目录存在完全重复图片，请先清洗")

        rng = random.Random(f"{seed}:{code}")
        chosen: list[Candidate] = []
        reasons: dict[Path, str] = {}
        for angle in ANGLE_PRIORITY:
            if len(chosen) >= count_per_landmark:
                break
            options = [item for item in candidates if item.angle == angle and item not in chosen]
            if options:
                picked = _pick_most_different(options, chosen, rng)
                chosen.append(picked)
                reasons[picked.path] = f"angle-coverage:{angle}"

        while len(chosen) < count_per_landmark:
            options = [item for item in candidates if item not in chosen]
            picked = _pick_most_different(options, chosen, rng)
            chosen.append(picked)
            reasons[picked.path] = "dhash-diversity-fill"

        for index, item in enumerate(chosen, start=1):
            selected_rows.append({
                "baseImageId": f"BASE-{code}-{index:02d}",
                "landmarkCode": code,
                "landmarkName": name,
                "sourcePath": item.path.relative_to(project_dir).as_posix(),
                "angleTag": item.angle,
                "lightTag": item.light,
                "sha256": item.sha256,
                "dHash": f"{item.dhash:016x}",
                "selectionReason": reasons[item.path],
            })

    if len(selected_rows) != len(LANDMARKS) * count_per_landmark:
        raise SamplingError("基础图片数量不符合预期")
    output_manifest.parent.mkdir(parents=True, exist_ok=True)
    with output_manifest.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=BASE_MANIFEST_FIELDS)
        writer.writeheader()
        writer.writerows(selected_rows)
    return selected_rows


def read_base_manifest(manifest_path: Path, project_dir: Path) -> list[dict]:
    manifest_path = Path(manifest_path).resolve()
    project_dir = Path(project_dir).resolve()
    with manifest_path.open("r", encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != 50:
        raise SamplingError(f"基础图片清单应有50条，实际为{len(rows)}条")
    seen_ids, seen_paths = set(), set()
    counts = {code: 0 for code in LANDMARKS}
    for row in rows:
        base_id = row["baseImageId"]
        code = row["landmarkCode"]
        path = (project_dir / row["sourcePath"]).resolve()
        if base_id in seen_ids or path in seen_paths:
            raise SamplingError("基础图片清单存在重复ID或重复路径")
        if code not in LANDMARKS or row["landmarkName"] != LANDMARKS[code]:
            raise SamplingError(f"基础图片标签无效: {base_id}")
        if not path.is_file():
            raise SamplingError(f"基础图片不存在: {path}")
        if sha256_file(path) != row["sha256"]:
            raise SamplingError(f"基础图片内容已变化: {path}")
        row["absolutePath"] = str(path)
        seen_ids.add(base_id)
        seen_paths.add(path)
        counts[code] += 1
    if any(count != 5 for count in counts.values()):
        raise SamplingError(f"每类必须恰好5张: {counts}")
    return rows
