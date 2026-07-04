from __future__ import annotations

import csv
import hashlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps


PERTURBATIONS = {
    "gaussian_noise": {
        "label": "高斯噪声",
        "parameter": "sigma",
        "levels": {1: 5.0, 2: 10.0, 3: 15.0, 4: 25.0, 5: 35.0},
    },
    "low_brightness": {
        "label": "亮度降低",
        "parameter": "factor",
        "levels": {1: 0.90, 2: 0.78, 3: 0.65, 4: 0.52, 5: 0.40},
    },
    "gaussian_blur": {
        "label": "高斯模糊",
        "parameter": "radius",
        "levels": {1: 0.5, 2: 1.0, 3: 1.5, 4: 2.2, 5: 3.0},
    },
}

CONDITION_FIELDS = (
    "sampleId",
    "baseImageId",
    "landmarkCode",
    "landmarkName",
    "sourcePath",
    "perturbationType",
    "perturbationLabel",
    "severity",
    "parameterName",
    "parameterValue",
    "noiseSeed",
    "materializedPath",
)


def deterministic_seed(global_seed: int, base_image_id: str, perturbation_type: str, severity: int) -> int:
    payload = f"{global_seed}|{base_image_id}|{perturbation_type}|{severity}".encode("utf-8")
    return int.from_bytes(hashlib.sha256(payload).digest()[:8], "big") & 0x7FFFFFFF


def write_conditions(conditions: list[dict], output_manifest: Path) -> None:
    output_manifest = Path(output_manifest).resolve()
    output_manifest.parent.mkdir(parents=True, exist_ok=True)
    with output_manifest.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=CONDITION_FIELDS)
        writer.writeheader()
        writer.writerows(conditions)


def build_conditions(base_rows: list[dict], output_manifest: Path, *, seed: int = 42) -> list[dict]:
    conditions: list[dict] = []
    for base in base_rows:
        common = {
            "baseImageId": base["baseImageId"],
            "landmarkCode": base["landmarkCode"],
            "landmarkName": base["landmarkName"],
            "sourcePath": base["sourcePath"],
            "materializedPath": "",
        }
        conditions.append({
            **common,
            "sampleId": f"{base['baseImageId']}-clean-0",
            "perturbationType": "clean",
            "perturbationLabel": "原图",
            "severity": 0,
            "parameterName": "none",
            "parameterValue": 0.0,
            "noiseSeed": 0,
        })
        for perturbation_type, config in PERTURBATIONS.items():
            for severity, value in config["levels"].items():
                conditions.append({
                    **common,
                    "sampleId": f"{base['baseImageId']}-{perturbation_type}-{severity}",
                    "perturbationType": perturbation_type,
                    "perturbationLabel": config["label"],
                    "severity": severity,
                    "parameterName": config["parameter"],
                    "parameterValue": value,
                    "noiseSeed": deterministic_seed(seed, base["baseImageId"], perturbation_type, severity),
                })
    if len(conditions) != len(base_rows) * 16:
        raise ValueError(f"扰动条件应为{len(base_rows) * 16}条，实际为{len(conditions)}条")
    write_conditions(conditions, output_manifest)
    return conditions


def read_conditions(path: Path) -> list[dict]:
    with Path(path).open("r", encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != 800:
        raise ValueError(f"扰动清单应有800条，实际为{len(rows)}条")
    for row in rows:
        row["severity"] = int(row["severity"])
        row["parameterValue"] = float(row["parameterValue"])
        row["noiseSeed"] = int(row["noiseSeed"])
    return rows


def load_rgb(path: Path) -> Image.Image:
    with Image.open(path) as source:
        return ImageOps.exif_transpose(source).convert("RGB")


def apply_perturbation(
    image: Image.Image,
    perturbation_type: str,
    severity: int,
    *,
    noise_seed: int = 0,
) -> Image.Image:
    rgb = image.convert("RGB")
    if perturbation_type == "clean" or severity == 0:
        return rgb.copy()
    if perturbation_type not in PERTURBATIONS or severity not in PERTURBATIONS[perturbation_type]["levels"]:
        raise ValueError(f"无效扰动: {perturbation_type} level {severity}")
    value = PERTURBATIONS[perturbation_type]["levels"][severity]
    if perturbation_type == "gaussian_noise":
        array = np.asarray(rgb, dtype=np.float32)
        noise = np.random.default_rng(noise_seed).normal(0.0, value, size=array.shape)
        return Image.fromarray(np.clip(array + noise, 0, 255).astype(np.uint8), "RGB")
    if perturbation_type == "low_brightness":
        return ImageEnhance.Brightness(rgb).enhance(value)
    if perturbation_type == "gaussian_blur":
        return rgb.filter(ImageFilter.GaussianBlur(radius=value))
    raise AssertionError("unreachable")


def materialize_conditions(conditions: list[dict], project_dir: Path, output_dir: Path) -> None:
    project_dir = Path(project_dir).resolve()
    output_dir = Path(output_dir).resolve()
    cache: dict[str, Image.Image] = {}
    for row in conditions:
        base_id = row["baseImageId"]
        if base_id not in cache:
            cache[base_id] = load_rgb(project_dir / row["sourcePath"])
        image = apply_perturbation(
            cache[base_id], row["perturbationType"], int(row["severity"]), noise_seed=int(row["noiseSeed"])
        )
        relative = Path("perturbed_images") / row["perturbationType"] / f"{row['sampleId']}.jpg"
        destination = output_dir / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        image.save(destination, format="JPEG", quality=92, optimize=True)
        row["materializedPath"] = relative.as_posix()


def create_preview(base_rows: list[dict], project_dir: Path, output_path: Path) -> Path:
    if not base_rows:
        raise ValueError("基础图片清单为空")
    base = base_rows[0]
    original = load_rgb(Path(project_dir) / base["sourcePath"])
    cell_width, cell_height = 220, 170
    canvas = Image.new("RGB", (cell_width * 6, cell_height * 3), "white")
    draw = ImageDraw.Draw(canvas)
    for row_index, perturbation_type in enumerate(PERTURBATIONS):
        for severity in range(6):
            seed = deterministic_seed(42, base["baseImageId"], perturbation_type, severity)
            image = apply_perturbation(original, perturbation_type, severity, noise_seed=seed)
            thumb = ImageOps.fit(image, (cell_width, cell_height - 24), method=Image.Resampling.LANCZOS)
            x, y = severity * cell_width, row_index * cell_height
            canvas.paste(thumb, (x, y + 24))
            draw.text((x + 7, y + 5), f"{perturbation_type}  level {severity}", fill="#183247")
    output_path = Path(output_path).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(output_path, format="JPEG", quality=92)
    return output_path


def create_base_contact_sheet(base_rows: list[dict], project_dir: Path, output_path: Path) -> Path:
    if len(base_rows) != 50:
        raise ValueError("基础图片联系表需要恰好50张")
    cell_width, cell_height = 210, 145
    canvas = Image.new("RGB", (cell_width * 5, cell_height * 10), "white")
    draw = ImageDraw.Draw(canvas)
    for index, row in enumerate(base_rows):
        image = load_rgb(Path(project_dir) / row["sourcePath"])
        thumb = ImageOps.fit(image, (cell_width, cell_height - 24), method=Image.Resampling.LANCZOS)
        column, line = index % 5, index // 5
        x, y = column * cell_width, line * cell_height
        canvas.paste(thumb, (x, y + 24))
        draw.text((x + 6, y + 5), f"{row['baseImageId']}  {row.get('angleTag', '')}", fill="#183247")
    output_path = Path(output_path).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(output_path, format="JPEG", quality=92)
    return output_path
