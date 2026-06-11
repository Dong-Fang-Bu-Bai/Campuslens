import math
from typing import Sequence

import numpy as np

SIGMOID_CENTER_DISTANCE = 900.0
SIGMOID_SLOPE = 3.0
HIGH_MATCH_THRESHOLD = 0.80
MEDIUM_MATCH_THRESHOLD = 0.40
DEFAULT_ENTROPY_TEMPERATURE = 0.2
DEFAULT_TRUST_LOW_THRESHOLD = 0.35
DEFAULT_TRUST_HIGH_THRESHOLD = 0.60


def mahalanobis_match_score(distance: float) -> float:
    """Convert Mahalanobis distance to an empirical match score in [0,
    1]."""
    if distance < 0:
        raise ValueError("Mahalanobis distance must be non-negative")

    log_dist = math.log(distance + 1.0)
    center = math.log(SIGMOID_CENTER_DISTANCE + 1.0)
    exponent = SIGMOID_SLOPE * (log_dist - center)
    if exponent > 700:
        return 0.0
    if exponent < -700:
        return 1.0
    return 1.0 / (1.0 + math.exp(exponent))


def match_level(score: float) -> str:
    """Map empirical match score to a coarse display level."""
    if score >= HIGH_MATCH_THRESHOLD:
        return "high"
    if score >= MEDIUM_MATCH_THRESHOLD:
        return "medium"
    return "low"


def normalized_entropy_from_scores(
        scores: Sequence[float],
        temperature: float = DEFAULT_ENTROPY_TEMPERATURE,
) -> float:
    """
    Convert Top-K scores into normalized entropy in [0, 1].

    说明:
    - 输入是检索结果的 score，不是分类 logits。
    - 先做温度 softmax，再算 Shannon entropy，并按 log(K) 归一化。
    - 熵越低，说明 Top-K 分布越集中，样本越可信。
    """
    scores_arr = np.asarray(list(scores), dtype=np.float64)
    if scores_arr.size == 0:
        return 1.0
    if scores_arr.size == 1:
        return 0.0

    temp = max(float(temperature), 1e-6)
    logits = scores_arr / temp
    logits = logits - np.max(logits)
    probs = np.exp(logits)
    probs_sum = float(np.sum(probs))
    if probs_sum <= 0.0 or not np.isfinite(probs_sum):
        return 1.0

    probs = probs / probs_sum
    entropy = -float(np.sum(probs * np.log(probs + 1e-12)))
    return float(entropy / math.log(scores_arr.size))


def trust_level_from_entropy(entropy: float) -> str:
    """Map normalized entropy to a coarse trust level."""
    if entropy < DEFAULT_TRUST_LOW_THRESHOLD:
        return "trusted"
    if entropy < DEFAULT_TRUST_HIGH_THRESHOLD:
        return "uncertain"
    return "untrusted"


def confidence_from_entropy(entropy: float) -> float:
    """Convert normalized entropy to a confidence-like trust score in
    [0, 1]."""
    entropy = max(0.0, min(1.0, float(entropy)))
    return 1.0 - entropy
