from __future__ import annotations

import html
import json
from pathlib import Path


def generate_preview_html(results_dir: Path) -> Path:
    results_dir = Path(results_dir).resolve()
    summary = json.loads((results_dir / "summary.json").read_text(encoding="utf-8"))
    metadata = summary["metadata"]
    baseline = summary["latency"]["baseline"]
    sar = summary["latency"]["sar"]
    figures = [
        ("Top-1与扰动强度", "figures/01_top1_vs_severity.png"),
        ("Top-5与扰动强度", "figures/02_top5_vs_severity.png"),
        ("候选分离度D", "figures/03_margin_d_vs_severity.png"),
        ("检索延迟分布", "figures/04_latency_raincloud.png"),
        ("预测与候选稳定性", "figures/05_prediction_stability.png"),
    ]
    cards = "".join(
        f'<article><h2>{html.escape(title)}</h2><img src="{path}" alt="{html.escape(title)}"></article>'
        for title, path in figures
    )
    document = f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>CampusLens SAR受控扰动实验</title>
<style>
body{{margin:0;background:#eef4f7;color:#183247;font-family:"Microsoft YaHei",sans-serif}}main{{max-width:1380px;margin:auto;padding:34px}}
header,article,.metrics{{background:white;border:1px solid #dce6ed;border-radius:18px;box-shadow:0 8px 30px #103b6612}}header{{padding:30px 34px;margin-bottom:22px}}
h1{{margin:0 0 10px;font-size:32px}}.boundary{{background:#fff4d8;border-left:5px solid #e4a11b;padding:14px 18px;margin:20px 0;font-weight:700}}
.metrics{{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;overflow:hidden;margin-bottom:22px}}.metric{{padding:18px 22px;background:white}}.metric b{{display:block;font-size:25px;color:#1677b8;margin-top:6px}}
article{{padding:22px;margin:20px 0}}article h2{{margin:0 0 14px}}img{{display:block;width:100%;height:auto}}footer{{color:#5c7182;padding:15px 3px 40px}}
code{{font-family:Consolas,monospace}}@media(max-width:800px){{.metrics{{grid-template-columns:1fr 1fr}}main{{padding:15px}}}}
</style></head><body><main>
<header><h1>CampusLens SAR增强检索受控扰动实验</h1>
<p>50张基础图片 × 800种输入条件 × 普通模式1轮 / SAR固定3轮</p>
<div class="boundary">{html.escape(summary['boundary'])}</div>
<p><strong>自动结论：</strong>{html.escape(summary['conclusion'])}</p></header>
<section class="metrics">
<div class="metric">实验记录<b>{summary['records']}</b></div>
<div class="metric">基础图片<b>{summary['baseImages']}</b></div>
<div class="metric">普通模式 P95<b>{baseline['p95LatencyMs']:.0f} ms</b></div>
<div class="metric">SAR P95<b>{sar['p95LatencyMs']:.0f} ms</b></div>
</section>
{cards}
<article><h2>可追溯环境</h2><p>GPU：{html.escape(str(metadata['device']))}</p><p>基础模型：<code>{html.escape(metadata['baseModelVersion'])}</code></p><p>索引：<code>{html.escape(metadata['indexVersion'])}</code></p><p>聚类Bootstrap：按50个<code>baseImageId</code>重采样2000次。</p></article>
<footer>所有图表均由本目录的 per_sample.csv 与 summary.json 生成；匹配分和候选分离度不解释为概率。</footer>
</main></body></html>"""
    output = results_dir / "preview.html"
    output.write_text(document, encoding="utf-8")
    return output
