import math


SIGMOID_CENTER_DISTANCE = 900.0
SIGMOID_SLOPE = 3.0
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
