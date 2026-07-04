import csv
import json
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from robustness.metrics import build_summary
from robustness.perturbations import (
    PERTURBATIONS,
    apply_perturbation,
    build_conditions,
    deterministic_seed,
)
from robustness.sampling import LANDMARKS, read_base_manifest, select_base_images


class RobustnessFixtureMixin:
    def create_dataset(self, root: Path) -> Path:
        source = root / "datasets" / "landmarks"
        source.mkdir(parents=True)
        angles = ["front", "back", "entrance", "far", "side", "near"]
        for landmark_index, code in enumerate(LANDMARKS):
            folder = source / f"{code}_fixture"
            folder.mkdir()
            for image_index, angle in enumerate(angles):
                image = Image.new("RGB", (80, 64), (20 + landmark_index * 17, 30 + image_index * 23, 90))
                draw = ImageDraw.Draw(image)
                draw.rectangle((4 + image_index, 5 + landmark_index, 45 + image_index * 2, 42), outline="white", width=3)
                draw.line((0, image_index * 8, 79, 63 - landmark_index * 3), fill=(250, 140, 30), width=2)
                image.save(folder / f"{code}_{angle}_day_{image_index + 1:03d}.png")
        return source


class SamplingTests(unittest.TestCase, RobustnessFixtureMixin):
    def test_sampling_is_balanced_diverse_and_reproducible(self):
        with tempfile.TemporaryDirectory(prefix="campuslens-robust-sampling-") as temporary:
            root = Path(temporary)
            source = self.create_dataset(root)
            first = root / "first.csv"
            second = root / "second.csv"
            rows_first = select_base_images(source, first, seed=42)
            rows_second = select_base_images(source, second, seed=42)
            self.assertEqual(rows_first, rows_second)
            self.assertEqual(len(rows_first), 50)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            loaded = read_base_manifest(first, root)
            self.assertEqual(len(loaded), 50)
            for code in LANDMARKS:
                subset = [row for row in loaded if row["landmarkCode"] == code]
                self.assertEqual(len(subset), 5)
                self.assertGreaterEqual(len({row["angleTag"] for row in subset}), 5)


class PerturbationTests(unittest.TestCase):
    def setUp(self):
        grid = np.indices((96, 96)).sum(axis=0) % 2
        array = np.stack([grid * 255, (1 - grid) * 220, grid * 120 + 50], axis=2).astype(np.uint8)
        self.image = Image.fromarray(array, "RGB")

    def test_clean_is_pixel_identical_and_noise_is_reproducible(self):
        clean = apply_perturbation(self.image, "clean", 0)
        self.assertTrue(np.array_equal(np.asarray(clean), np.asarray(self.image)))
        seed = deterministic_seed(42, "BASE-L01-01", "gaussian_noise", 3)
        first = apply_perturbation(self.image, "gaussian_noise", 3, noise_seed=seed)
        second = apply_perturbation(self.image, "gaussian_noise", 3, noise_seed=seed)
        self.assertTrue(np.array_equal(np.asarray(first), np.asarray(second)))

    def test_severity_changes_are_monotonic(self):
        original = np.asarray(self.image, dtype=np.float32)
        brightness_means = []
        noise_errors = []
        blur_gradients = []
        for severity in range(1, 6):
            brightness = np.asarray(apply_perturbation(self.image, "low_brightness", severity), dtype=np.float32)
            brightness_means.append(brightness.mean())
            noisy = np.asarray(apply_perturbation(self.image, "gaussian_noise", severity, noise_seed=1234), dtype=np.float32)
            noise_errors.append(np.mean((noisy - original) ** 2))
            blurred = np.asarray(apply_perturbation(self.image, "gaussian_blur", severity), dtype=np.float32)
            blur_gradients.append(np.var(np.diff(blurred, axis=1)))
        self.assertEqual(brightness_means, sorted(brightness_means, reverse=True))
        self.assertEqual(noise_errors, sorted(noise_errors))
        self.assertEqual(
            list(PERTURBATIONS["gaussian_blur"]["levels"].values()),
            sorted(PERTURBATIONS["gaussian_blur"]["levels"].values()),
        )
        self.assertGreater(blur_gradients[0], blur_gradients[-1] * 100)

    def test_condition_manifest_has_800_rows(self):
        base_rows = [
            {
                "baseImageId": f"BASE-L{index // 5 + 1:02d}-{index % 5 + 1:02d}",
                "landmarkCode": f"L{index // 5 + 1:02d}",
                "landmarkName": LANDMARKS[f"L{index // 5 + 1:02d}"],
                "sourcePath": f"datasets/landmarks/image-{index}.jpg",
            }
            for index in range(50)
        ]
        with tempfile.TemporaryDirectory(prefix="campuslens-conditions-") as temporary:
            conditions = build_conditions(base_rows, Path(temporary) / "conditions.csv")
        self.assertEqual(len(conditions), 800)
        self.assertEqual(sum(row["perturbationType"] == "clean" for row in conditions), 50)


class MetricsAndPlottingTests(unittest.TestCase):
    @staticmethod
    def make_record(run_id, mode, seed, base_index, perturbation_type, severity):
        code = f"L{base_index // 5 + 1:02d}"
        difficulty = severity + (0 if perturbation_type == "clean" else list(PERTURBATIONS).index(perturbation_type))
        baseline_correct = difficulty < 5 or base_index % 4 != 0
        sar_correct = difficulty < 4 or base_index % 5 != 0
        correct = baseline_correct if mode == "baseline" else sar_correct
        predicted = code if correct else f"L{int(code[1:]) % 10 + 1:02d}"
        other = f"L{(int(code[1:]) + 1) % 10 + 1:02d}"
        top_results = [
            {"landmarkCode": predicted, "score": 0.82 if correct else 0.61},
            {"landmarkCode": other if predicted == code else code, "score": 0.65 if correct else 0.59},
            {"landmarkCode": "L03", "score": 0.45},
            {"landmarkCode": "L04", "score": 0.35},
            {"landmarkCode": "L05", "score": 0.25},
        ]
        codes = [item["landmarkCode"] for item in top_results]
        rank = codes.index(code) + 1 if code in codes else 0
        return {
            "runId": run_id,
            "mode": mode,
            "seed": "" if seed is None else seed,
            "orderIndex": base_index,
            "sampleId": f"BASE-{base_index:02d}-{perturbation_type}-{severity}",
            "baseImageId": f"BASE-{base_index:02d}",
            "landmarkCode": code,
            "landmarkName": LANDMARKS[code],
            "sourcePath": f"datasets/landmarks/{base_index}.jpg",
            "perturbationType": perturbation_type,
            "perturbationLabel": "原图" if perturbation_type == "clean" else PERTURBATIONS[perturbation_type]["label"],
            "severity": severity,
            "parameterName": "fixture",
            "parameterValue": severity,
            "noiseSeed": 0,
            "predictedCode": predicted,
            "trueRank": rank,
            "top1Correct": int(rank == 1),
            "top5Correct": int(rank > 0),
            "reciprocalRank": 1 / rank if rank else 0,
            "top1Score": top_results[0]["score"],
            "top2Score": top_results[1]["score"],
            "scoreMargin": top_results[0]["score"] - top_results[1]["score"],
            "latencyMs": 120 + severity * 8 + (190 if mode == "sar" else 0),
            "sarApplied": int(mode == "sar"),
            "trustLevel": "high" if correct else "medium",
            "baseModelVersion": "base-fixture",
            "indexVersion": "index-fixture",
            "sarStateVersion": f"sar-{run_id}",
            "modelVersion": "fixture",
            "topResults": top_results,
            "topResultsJson": json.dumps(top_results, ensure_ascii=False),
        }

    def build_records(self):
        conditions = [("clean", 0)] + [
            (perturbation_type, severity)
            for perturbation_type in PERTURBATIONS
            for severity in range(1, 6)
        ]
        records = []
        for base_index in range(50):
            for perturbation_type, severity in conditions:
                records.append(self.make_record("baseline", "baseline", None, base_index, perturbation_type, severity))
        for seed in (42, 2026, 704):
            for base_index in range(50):
                for perturbation_type, severity in conditions:
                    records.append(self.make_record(f"sar-seed-{seed}", "sar", seed, base_index, perturbation_type, severity))
        return records

    def test_clustered_metrics_and_charts(self):
        records = self.build_records()
        enriched, summary = build_summary(records, {
            "device": "fixture GPU",
            "baseModelVersion": "base-fixture",
            "indexVersion": "index-fixture",
        }, bootstrap_draws=100)
        self.assertEqual(len(enriched), 3200)
        self.assertEqual(summary["bootstrap"]["unit"], "baseImageId")
        self.assertEqual(len(summary["curveData"]), 3 * 6 * 6)
        self.assertEqual(summary["records"], 3200)
        self.assertIn("Top-1", summary["conclusion"])

        try:
            from robustness.plotting import generate_all_charts
            from robustness.report import generate_preview_html
            from robustness.runner import CSV_FIELDS
        except ModuleNotFoundError:
            self.skipTest("plot dependencies unavailable")
        with tempfile.TemporaryDirectory(prefix="campuslens-robust-charts-") as temporary:
            root = Path(temporary)
            (root / "summary.json").write_text(json.dumps(summary, ensure_ascii=False), encoding="utf-8")
            with (root / "per_sample.csv").open("w", encoding="utf-8-sig", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=CSV_FIELDS, extrasaction="ignore")
                writer.writeheader()
                writer.writerows(enriched)
            paths = generate_all_charts(root)
            preview = generate_preview_html(root)
            self.assertEqual(len(paths), 10)
            self.assertTrue(all(path.stat().st_size > 1000 for path in paths))
            self.assertIn("受控扰动压力测试", preview.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
