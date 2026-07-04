from __future__ import annotations

import argparse
from datetime import datetime
from pathlib import Path

from robustness.perturbations import (
    build_conditions,
    create_base_contact_sheet,
    create_preview,
    materialize_conditions,
    write_conditions,
)
from robustness.sampling import select_base_images


ALGORITHM_DIR = Path(__file__).resolve().parent
PROJECT_DIR = ALGORITHM_DIR.parent
WORK_ROOT = PROJECT_DIR.parent
DEFAULT_SOURCE_DIR = PROJECT_DIR / "datasets" / "landmarks"
DEFAULT_OUTPUT_ROOT = WORK_ROOT / "软件开发实训文档汇总" / "图片" / "测试"


def new_output_dir() -> Path:
    return DEFAULT_OUTPUT_ROOT / f"SAR鲁棒性实测_{datetime.now().strftime('%Y%m%d_%H%M%S')}"


def prepare(source_dir: Path, output_dir: Path, *, materialize: bool = False) -> None:
    output_dir = Path(output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=False)
    base_manifest = output_dir / "robustness_base_manifest.csv"
    condition_manifest = output_dir / "perturbation_manifest.csv"
    base_rows = select_base_images(source_dir, base_manifest, seed=42)
    conditions = build_conditions(base_rows, condition_manifest, seed=42)
    create_base_contact_sheet(base_rows, PROJECT_DIR, output_dir / "base_selection_contact_sheet.jpg")
    create_preview(base_rows, PROJECT_DIR, output_dir / "perturbation_preview.jpg")
    if materialize:
        materialize_conditions(conditions, PROJECT_DIR, output_dir)
        write_conditions(conditions, condition_manifest)
    print(f"[OK] 基础图片: {len(base_rows)}")
    print(f"[OK] 扰动条件: {len(conditions)}")
    print(f"[OK] 输出目录: {output_dir}")


def parse_args():
    parser = argparse.ArgumentParser(description="CampusLens SAR受控扰动压力测试")
    subparsers = parser.add_subparsers(dest="command", required=True)

    sample = subparsers.add_parser("sample", help="抽取50张基础图片并生成800条扰动清单")
    sample.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    sample.add_argument("--output-dir", type=Path, default=None)
    sample.add_argument("--materialize", action="store_true")

    run = subparsers.add_parser("run", help="运行Baseline和SAR三轮并生成结果")
    run.add_argument("--output-dir", type=Path, required=True)
    run.add_argument("--seeds", type=int, nargs="+", default=[42, 2026, 704])
    run.add_argument("--bootstrap-draws", type=int, default=2000)

    charts = subparsers.add_parser("charts", help="从已完成结果重绘图表和HTML")
    charts.add_argument("--output-dir", type=Path, required=True)

    all_command = subparsers.add_parser("all", help="抽样、运行、绘图和HTML一键完成")
    all_command.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    all_command.add_argument("--output-dir", type=Path, default=None)
    all_command.add_argument("--materialize", action="store_true")
    all_command.add_argument("--seeds", type=int, nargs="+", default=[42, 2026, 704])
    all_command.add_argument("--bootstrap-draws", type=int, default=2000)
    return parser.parse_args()


def generate_outputs(output_dir: Path) -> None:
    from robustness.plotting import generate_all_charts
    from robustness.report import generate_preview_html

    paths = generate_all_charts(output_dir)
    preview = generate_preview_html(output_dir)
    print(f"[OK] 图表: {len(paths)}个PNG/SVG文件")
    print(f"[OK] 预览: {preview}")


def main():
    args = parse_args()
    if args.command == "sample":
        prepare(args.source_dir, args.output_dir or new_output_dir(), materialize=args.materialize)
        return
    if args.command == "charts":
        generate_outputs(args.output_dir)
        return

    from robustness.runner import run_experiment

    if args.command == "run":
        summary = run_experiment(PROJECT_DIR, args.output_dir, seeds=tuple(args.seeds), bootstrap_draws=args.bootstrap_draws)
        print(summary["conclusion"])
        return

    output_dir = args.output_dir or new_output_dir()
    prepare(args.source_dir, output_dir, materialize=args.materialize)
    summary = run_experiment(PROJECT_DIR, output_dir, seeds=tuple(args.seeds), bootstrap_draws=args.bootstrap_draws)
    generate_outputs(output_dir)
    print(summary["conclusion"])


if __name__ == "__main__":
    main()
