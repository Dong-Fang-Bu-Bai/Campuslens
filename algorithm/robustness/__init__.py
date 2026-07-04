"""Controlled corruption evaluation for CampusLens baseline and SAR retrieval."""

from .metrics import build_summary
from .perturbations import PERTURBATIONS, apply_perturbation, build_conditions
from .sampling import LANDMARKS, read_base_manifest, select_base_images

__all__ = [
    "LANDMARKS",
    "PERTURBATIONS",
    "apply_perturbation",
    "build_conditions",
    "build_summary",
    "read_base_manifest",
    "select_base_images",
]
