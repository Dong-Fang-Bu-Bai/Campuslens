from contextlib import nullcontext
from copy import deepcopy
from pathlib import Path
import threading

import numpy as np
from PIL import Image
import torch
import torch.nn.functional as F
from torchvision import transforms

from app.models.sar_adapter import SARAdapter


class DINOv2Extractor:
    def __init__(self, model_path: str, device: str = None, mixed_precision: bool = False, dual_track: bool = True):
        self.device = "cuda" if device in {None, "auto"} and torch.cuda.is_available() else ("cpu" if device in {None, "auto"} else device)
        if self.device == "cuda" and not torch.cuda.is_available():
            raise RuntimeError("DEVICE=cuda but CUDA is not available")
        self.mixed_precision = bool(mixed_precision and self.device == "cuda")
        self.base_model = self._load_local_model(model_path).to(self.device).eval()
        self.base_model.requires_grad_(False)
        self.sar_model = deepcopy(self.base_model).to(self.device).eval() if dual_track and isinstance(self.base_model, torch.nn.Module) else None
        self.sar_adapter = SARAdapter(self.sar_model) if self.sar_model is not None else None
        self.model = self.base_model
        self.model_lock = threading.RLock()
        self.transform = transforms.Compose([
            transforms.Resize(518), transforms.CenterCrop(518), transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
        ])
        self.feature_dim = self.get_feature_dimension()

    def _load_local_model(self, model_path):
        model_file = Path(model_path)
        if not model_file.exists():
            raise FileNotFoundError(f"model file not found: {model_path}")
        model = torch.load(model_path, map_location=self.device, weights_only=False)
        if hasattr(model, "forward_features"):
            return model
        if isinstance(model, dict):
            from app.models.dinov2_vit import create_dinov2_vitb14
            state = model.get("state_dict", model.get("model_state_dict", model))
            built = create_dinov2_vitb14()
            built.load_state_dict(state, strict=False)
            return built
        raise ValueError(f"unsupported model format: {type(model)}")

    def get_feature_dimension(self):
        dummy = torch.randn(1, 3, 518, 518, device=self.device)
        with torch.inference_mode(), self._autocast():
            return self._feature_tensor(self.base_model.forward_features(dummy)).shape[-1]

    def _feature_tensor(self, outputs):
        values = outputs["x_norm_clstoken"] if isinstance(outputs, dict) else outputs
        if values.ndim == 3:
            values = values[:, 0]
        return F.normalize(values, p=2, dim=-1)

    def _batch_tensor(self, images):
        tensors = []
        for image in images:
            if isinstance(image, (str, Path)):
                image = Image.open(image).convert("RGB")
            tensors.append(self.transform(image))
        return torch.stack(tensors).to(self.device)

    def extract_single(self, image):
        return self.extract_batch([image])[0]

    def extract_batch(self, images):
        tensor = self._batch_tensor(images)
        with self.model_lock, torch.inference_mode(), self._autocast():
            return self._feature_tensor(self.base_model.forward_features(tensor)).cpu().numpy()

    def adapt_sar_batch(self, images, landmark_stats):
        if self.sar_adapter is None:
            raise RuntimeError("SAR track is unavailable")
        tensor = self._batch_tensor(images)
        with self.model_lock, self._autocast():
            features, entropy, applied, updated = self.sar_adapter.adapt_batch(tensor, landmark_stats)
        return features.cpu().numpy(), entropy, applied, updated

    def extract_sar_batch(self, images):
        tensor = self._batch_tensor(images)
        with self.model_lock, self._autocast():
            return self.sar_adapter.extract(tensor).cpu().numpy()

    def reset_sar(self):
        with self.model_lock:
            self.sar_adapter.reset()

    def _autocast(self):
        return torch.autocast(device_type="cuda", dtype=torch.float16) if self.mixed_precision else nullcontext()
