import math
from typing import Sequence

import numpy as np

from app.config import Config
HIGH_MATCH_THRESHOLD = 0.80
MEDIUM_MATCH_THRESHOLD = 0.40


def mahalanobis_match_score(distance: float) -> float:
    """
    Convert Mahalanobis distance to an empirical match score in [0, 1].

    The score is intended for ranking and visual separation only. It is not a
    probability or statistical confidence value.
    """
    if distance < 0:
        raise ValueError("Mahalanobis distance must be non-negative")

    log_dist = math.log(distance + 1.0)
    center = math.log(Config.MATCH_SCORE_CENTER_DISTANCE + 1.0)
    exponent = Config.MATCH_SCORE_SLOPE * (log_dist - center)
    if exponent > Config.SIGMOID_STABILITY_THRESHOLD:
        return 0.0
    if exponent < -Config.SIGMOID_STABILITY_THRESHOLD:
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
    temperature: float | None = None,
) -> float:
    values = np.asarray(list(scores), dtype=np.float64)
    if values.size == 0:
        return 1.0
    if values.size == 1:
        return 0.0
    temp = max(float(temperature or Config.SAR_ENTROPY_TEMPERATURE), 1e-6)
    logits = values / temp
    logits -= np.max(logits)
    probabilities = np.exp(logits)
    total = float(np.sum(probabilities))
    if total <= 0.0 or not np.isfinite(total):
        return 1.0
    probabilities /= total
    entropy = -float(np.sum(probabilities * np.log(probabilities + 1e-12)))
    return max(0.0, min(1.0, entropy / math.log(values.size)))


def trust_level_from_entropy(entropy: float) -> str:
    if entropy < Config.TRUST_LOW_THRESHOLD:
        return "trusted"
    if entropy < Config.TRUST_HIGH_THRESHOLD:
        return "uncertain"
    return "untrusted"


def confidence_from_entropy(entropy: float) -> float:
    return 1.0 - max(0.0, min(1.0, float(entropy)))
