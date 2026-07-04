from __future__ import annotations

import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager
from matplotlib.lines import Line2D
import numpy as np
import pandas as pd
import seaborn as sns

from .perturbations import PERTURBATIONS


NAVY = "#103B66"
BLUE = "#1677B8"
CYAN = "#14B8A6"
INK = "#183247"
MUTED = "#5C7182"
GRID = "#DCE6ED"
MODE_COLORS = {"baseline": BLUE, "sar": CYAN}
MODE_LABELS = {"baseline": "普通模式", "sar": "SAR增强"}


def configure_style() -> None:
    preferred = ["Microsoft YaHei", "Noto Sans CJK SC", "SimHei", "Arial Unicode MS"]
    installed = {font.name for font in font_manager.fontManager.ttflist}
    selected = next((name for name in preferred if name in installed), "DejaVu Sans")
    sns.set_theme(style="whitegrid")
    plt.rcParams.update({
        "font.family": "sans-serif",
        "font.sans-serif": [selected, "DejaVu Sans"],
        "axes.unicode_minus": False,
        "axes.titleweight": "bold",
        "axes.titlesize": 13,
        "axes.labelsize": 10.5,
        "figure.facecolor": "white",
        "axes.facecolor": "white",
        "grid.color": GRID,
        "grid.linewidth": 0.8,
        "svg.fonttype": "none",
    })


def _load(results_dir: Path) -> tuple[pd.DataFrame, dict]:
    results_dir = Path(results_dir).resolve()
    frame = pd.read_csv(results_dir / "per_sample.csv", encoding="utf-8-sig")
    summary = json.loads((results_dir / "summary.json").read_text(encoding="utf-8"))
    if len(frame) != 3200 or int(summary.get("records", 0)) != 3200:
        raise ValueError("图表只接受完成的3200条真实实验记录")
    return frame, summary


def _save(fig, output_dir: Path, stem: str) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    png = output_dir / f"{stem}.png"
    svg = output_dir / f"{stem}.svg"
    fig.savefig(png, dpi=300, bbox_inches="tight", facecolor="white")
    fig.savefig(svg, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    return [png, svg]


def _curve_rows(summary: dict, perturbation_type: str, metric: str) -> list[dict]:
    return sorted(
        [
            row for row in summary["curveData"]
            if row["perturbationType"] == perturbation_type and row["metric"] == metric
        ],
        key=lambda row: int(row["severity"]),
    )


def plot_metric_curves(summary: dict, metric: str, title: str, ylabel: str, stem: str, output_dir: Path, *, percent: bool) -> list[Path]:
    fig, axes = plt.subplots(1, 3, figsize=(13.333, 5.25), sharey=True)
    fig.subplots_adjust(left=0.07, right=0.985, top=0.78, bottom=0.22, wspace=0.08)
    factor = 100.0 if percent else 1.0
    all_values = []
    for ax, perturbation_type in zip(axes, PERTURBATIONS):
        rows = _curve_rows(summary, perturbation_type, metric)
        x = np.array([int(row["severity"]) for row in rows])
        for mode in ("sar", "baseline"):
            values = np.array([float(row[mode]) for row in rows]) * factor
            all_values.extend(values.tolist())
            lows = np.array([float(row[f"{mode}Ci95"][0]) for row in rows]) * factor
            highs = np.array([float(row[f"{mode}Ci95"][1]) for row in rows]) * factor
            style = {
                "marker": "o",
                "lw": 2.4,
                "color": MODE_COLORS[mode],
                "label": MODE_LABELS[mode],
                "linestyle": "--" if mode == "baseline" else "-",
                "markerfacecolor": "white" if mode == "baseline" else MODE_COLORS[mode],
                "markeredgewidth": 1.8,
                "zorder": 4 if mode == "baseline" else 3,
            }
            ax.plot(x, values, **style)
            ax.fill_between(x, lows, highs, color=MODE_COLORS[mode], alpha=0.12)
        ax.set_title(PERTURBATIONS[perturbation_type]["label"], pad=10)
        ax.set_xlabel("扰动强度 level")
        ax.set_xticks(range(6))
        sns.despine(ax=ax)
    axes[0].set_ylabel(ylabel)
    if percent:
        axes[0].set_ylim(-3, 103)
    handles = [
        Line2D([0], [0], color=BLUE, marker="o", markerfacecolor="white", markeredgewidth=1.8, linestyle="--", lw=2.4, label="普通模式"),
        Line2D([0], [0], color=CYAN, marker="o", lw=2.4, label="SAR增强（3轮均值）"),
    ]
    fig.legend(handles=handles, loc="lower center", bbox_to_anchor=(0.5, 0.075), ncol=2, frameon=False)
    fig.suptitle(title, fontsize=18, weight="bold", color=INK, y=0.96)
    if percent and all(abs(value - 100.0) < 1e-9 for value in all_values):
        fig.text(0.5, 0.855, "本轮既定扰动范围内两模式均保持100%，曲线完全重合", ha="center", color=CYAN, fontsize=10.5, weight="bold")
    fig.text(0.985, 0.025, "阴影为按50张基础图片聚类Bootstrap得到的95%区间", ha="right", color=MUTED, fontsize=8.5)
    return _save(fig, output_dir, stem)


def plot_latency(frame: pd.DataFrame, summary: dict, output_dir: Path) -> list[Path]:
    data = frame.copy()
    data["模式"] = data["mode"].map(MODE_LABELS)
    order = [MODE_LABELS["baseline"], MODE_LABELS["sar"]]
    palette = [BLUE, CYAN]
    fig, ax = plt.subplots(figsize=(10.8, 6.4), constrained_layout=True)
    sns.violinplot(data=data, x="模式", y="latencyMs", hue="模式", order=order, palette=palette, inner=None, cut=0, linewidth=1, alpha=0.35, dodge=False, legend=False, ax=ax)
    sns.boxplot(data=data, x="模式", y="latencyMs", order=order, width=0.18, showfliers=False, color="white", boxprops={"zorder": 3, "alpha": 0.9}, ax=ax)
    sampled = pd.concat([
        data[data["mode"] == "baseline"].sample(n=min(250, sum(data["mode"] == "baseline")), random_state=42),
        data[data["mode"] == "sar"].sample(n=min(500, sum(data["mode"] == "sar")), random_state=42),
    ])
    sns.stripplot(data=sampled, x="模式", y="latencyMs", hue="模式", order=order, palette=palette, size=2.8, jitter=0.18, alpha=0.35, dodge=False, legend=False, ax=ax)
    baseline = summary["latency"]["baseline"]
    sar = summary["latency"]["sar"]
    ax.set_title("SAR带来的识别变化必须与额外延迟一起评估", loc="left", pad=14, fontsize=18, color=INK)
    ax.set_xlabel("")
    ax.set_ylabel("单次算法检索延迟（ms）")
    ax.text(0.99, 0.98, f"P95：普通 {baseline['p95LatencyMs']:.0f} ms｜SAR {sar['p95LatencyMs']:.0f} ms", transform=ax.transAxes, ha="right", va="top", color=MUTED, fontsize=9.5)
    sns.despine(ax=ax)
    return _save(fig, output_dir, "04_latency_raincloud")


def plot_stability(summary: dict, output_dir: Path) -> list[Path]:
    fig, axes = plt.subplots(2, 3, figsize=(13.333, 7.6), sharex=True)
    fig.subplots_adjust(left=0.07, right=0.985, top=0.86, bottom=0.16, hspace=0.12, wspace=0.12)
    for column, perturbation_type in enumerate(PERTURBATIONS):
        for row_index, (metric, ylabel) in enumerate((
            ("top1Retention", "Top-1预测保持率（%）"),
            ("top5Jaccard", "Top-5集合Jaccard（%）"),
        )):
            ax = axes[row_index, column]
            rows = _curve_rows(summary, perturbation_type, metric)
            x = [int(row["severity"]) for row in rows]
            for mode in ("baseline", "sar"):
                y = [float(row[mode]) * 100 for row in rows]
                ax.plot(x, y, marker="o", lw=2.1, color=MODE_COLORS[mode])
            if row_index == 0:
                ax.set_title(PERTURBATIONS[perturbation_type]["label"], pad=8)
            if column == 0:
                ax.set_ylabel(ylabel)
            ax.set_ylim(-3, 103)
            ax.set_xticks(range(6))
            if row_index == 1:
                ax.set_xlabel("扰动强度 level")
            sns.despine(ax=ax)
    fig.suptitle("稳定性需直接比较扰动前后的预测与候选集合", fontsize=18, weight="bold", color=INK, y=0.97)
    fig.legend(
        handles=[Line2D([0], [0], color=BLUE, marker="o", label="普通模式"), Line2D([0], [0], color=CYAN, marker="o", label="SAR增强")],
        loc="lower center", bbox_to_anchor=(0.5, 0.035), ncol=2, frameon=False,
    )
    return _save(fig, output_dir, "05_prediction_stability")


def generate_all_charts(results_dir: Path) -> list[Path]:
    configure_style()
    results_dir = Path(results_dir).resolve()
    frame, summary = _load(results_dir)
    output_dir = results_dir / "figures"
    paths = []
    paths += plot_metric_curves(summary, "top1Correct", "扰动增强时，Top-1识别能力如何变化", "Top-1（%）", "01_top1_vs_severity", output_dir, percent=True)
    paths += plot_metric_curves(summary, "top5Correct", "Top-5候选覆盖是否能抵抗图像退化", "Top-5（%）", "02_top5_vs_severity", output_dir, percent=True)
    paths += plot_metric_curves(summary, "scoreMargin", "候选分离度随扰动强度的变化", "D = Top-1分数 − Top-2分数", "03_margin_d_vs_severity", output_dir, percent=False)
    paths += plot_latency(frame, summary, output_dir)
    paths += plot_stability(summary, output_dir)
    return paths
